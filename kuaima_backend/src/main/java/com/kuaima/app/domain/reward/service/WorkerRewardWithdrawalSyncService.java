package com.kuaima.app.domain.reward.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.reward.entity.RewardWithdrawal;
import com.kuaima.app.domain.reward.repository.RewardWithdrawalRepository;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferStatus;
import com.kuaima.app.alipay.service.AlipayFundTransferClient;

/** 同步微信商家转账终态；查询失败不视为转账失败，避免查询异常导致误退款。 */
@Service
public class WorkerRewardWithdrawalSyncService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(WorkerRewardWithdrawalSyncService.class);

    private final RewardWithdrawalRepository withdrawals;
    private final WorkerRewardWithdrawalProcessor processor;
    private final WechatMerchantTransferClient transferClient;
    private final AlipayFundTransferClient alipayTransferClient;

    public WorkerRewardWithdrawalSyncService(RewardWithdrawalRepository withdrawals,
            WorkerRewardWithdrawalProcessor processor, WechatMerchantTransferClient transferClient) {
        this(withdrawals, processor, transferClient, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorkerRewardWithdrawalSyncService(RewardWithdrawalRepository withdrawals,
            WorkerRewardWithdrawalProcessor processor, WechatMerchantTransferClient transferClient,
            AlipayFundTransferClient alipayTransferClient) {
        this.withdrawals = withdrawals;
        this.processor = processor;
        this.transferClient = transferClient;
        this.alipayTransferClient = alipayTransferClient;
    }

    public int syncPending() {
        LocalDateTime due = LocalDateTime.now(ZONE).minusMinutes(1);
        List<RewardWithdrawal> pending = withdrawals
                .findTop20ByStatusAndAppliedAtBeforeOrderByAppliedAtAscIdAsc("PENDING", due);
        int changed = 0;
        for (RewardWithdrawal withdrawal : pending) {
            if (!StringUtils.hasText(withdrawal.getMerchantBatchNo())) continue;
            if ("ALIPAY".equals(withdrawal.getChannel())) {
                changed += syncAlipay(withdrawal);
                continue;
            }
            boolean legacyTransfer = isLegacyTransfer(withdrawal.getTransferResponse());
            MerchantTransferStatus status;
            try {
                status = legacyTransfer
                        ? transferClient.queryLegacy(withdrawal.getMerchantBatchNo())
                        : transferClient.query(withdrawal.getMerchantBatchNo());
            } catch (RuntimeException e) {
                // 查询失败不代表转账失败；保留 PENDING，等待下一轮查询。
                log.warn("奖励金提现单 {} 微信状态查询失败: {}", withdrawal.getId(), e.getMessage());
                continue;
            }
            boolean terminalSuccess = legacyTransfer
                    ? "FINISHED".equals(status.state()) && "SUCCESS".equals(status.detailStatus())
                    : "SUCCESS".equals(status.state());
            boolean terminalFailure = legacyTransfer
                    ? "CLOSED".equals(status.state()) || "FAIL".equals(status.detailStatus())
                    : "FAIL".equals(status.state()) || "CANCELLED".equals(status.state());
            if (terminalSuccess) {
                processor.transferSucceeded(withdrawal.getId(), status.transferBillNo(), status.rawResponse());
                changed++;
            } else if (terminalFailure) {
                String reason = StringUtils.hasText(status.failReason()) ? status.failReason() : "微信商家转账失败";
                processor.transferFailed(withdrawal.getId(), reason,
                        status.transferBillNo(), status.rawResponse());
                changed++;
            }
        }
        return changed;
    }

    private boolean isLegacyTransfer(String rawResponse) {
        return StringUtils.hasText(rawResponse) && rawResponse.contains("batch_id")
                && !rawResponse.contains("transfer_bill_no");
    }

    private int syncAlipay(RewardWithdrawal withdrawal) {
        if (alipayTransferClient == null || !alipayTransferClient.transferEnabled()) return 0;
        AlipayFundTransferClient.QueryResult status;
        try {
            status = alipayTransferClient.query(withdrawal.getMerchantDetailNo());
        } catch (RuntimeException e) {
            log.warn("奖励金提现单 {} 支付宝状态查询失败: {}", withdrawal.getId(), e.getMessage());
            return 0;
        }
        if ("SUCCESS".equals(status.status())) {
            processor.transferSucceeded(withdrawal.getId(), withdrawal.getWechatTransferNo(), status.rawResponse());
            return 1;
        }
        if ("FAIL".equals(status.status())) {
            processor.transferFailed(withdrawal.getId(),
                    StringUtils.hasText(status.failReason()) ? status.failReason() : "支付宝转账失败");
            return 1;
        }
        return 0;
    }
}
