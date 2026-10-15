package com.kuaima.app.domain.wallet.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient;
import com.kuaima.app.domain.wallet.constant.WithDrawStatus;
import com.kuaima.app.domain.wallet.repository.WithDrawRespository;

@Service
public class WorkerWalletWithdrawalSyncService {
    private final WithDrawRespository withdrawals;
    private final WalletService wallets;
    private final WechatMerchantTransferClient transfers;

    public WorkerWalletWithdrawalSyncService(WithDrawRespository withdrawals, WalletService wallets,
            WechatMerchantTransferClient transfers) {
        this.withdrawals = withdrawals; this.wallets = wallets; this.transfers = transfers;
    }

    public int syncPending() {
        int changed = 0;
        var pending = withdrawals.findTop20ByStatusAndApplyTimeBeforeAndMerchantBatchNoIsNotNullOrderByApplyTimeAsc(
                WithDrawStatus.PENDING, LocalDateTime.now().minusMinutes(1));
        for (var draw : pending) {
            WechatMerchantTransferClient.MerchantTransferStatus status;
            boolean legacyTransfer = StringUtils.hasText(draw.getTransferResponse())
                    && draw.getTransferResponse().contains("batch_id")
                    && !draw.getTransferResponse().contains("transfer_bill_no");
            try {
                status = legacyTransfer
                        ? transfers.queryLegacy(draw.getMerchantBatchNo())
                        : transfers.query(draw.getMerchantBatchNo());
            }
            catch (RuntimeException e) { continue; }
            boolean terminalSuccess = legacyTransfer
                    ? "FINISHED".equals(status.state()) && "SUCCESS".equals(status.detailStatus())
                    : "SUCCESS".equals(status.state());
            boolean terminalFailure = legacyTransfer
                    ? "CLOSED".equals(status.state()) || "FAIL".equals(status.detailStatus())
                    : "FAIL".equals(status.state()) || "CANCELLED".equals(status.state());
            if (terminalSuccess) {
                wallets.succeedWechatWithdraw(draw.getId(), status.transferBillNo(), status.rawResponse()); changed++;
            } else if (terminalFailure) {
                String reason = StringUtils.hasText(status.failReason()) ? status.failReason() : "微信提现失败";
                wallets.failWechatWithdraw(draw.getId(), reason, status.transferBillNo(), status.rawResponse()); changed++;
            }
        }
        return changed;
    }
}
