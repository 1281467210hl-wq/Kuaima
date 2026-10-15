package com.kuaima.app.domain.reward.service;

import com.kuaima.app.alipay.service.AlipayFundTransferClient;
import com.kuaima.app.domain.reward.entity.*;
import com.kuaima.app.domain.reward.repository.*;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.data.domain.*;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class BossRewardService {
    public static final BigDecimal MIN_WITHDRAW_AMOUNT = new BigDecimal("10.00");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final RewardAccountRepository accounts;
    private final RewardFlowRepository flows;
    private final RewardWithdrawalRepository withdrawals;
    private final UserRepository users;
    private final RewardWithdrawSettingsService settingsService;
    private final WorkerRewardWithdrawalProcessor withdrawalProcessor;
    private final WechatMerchantTransferClient transferClient;
    private final AlipayFundTransferClient alipayTransferClient;

    public BossRewardService(RewardAccountRepository accounts, RewardFlowRepository flows,
            RewardWithdrawalRepository withdrawals, UserRepository users,
            RewardWithdrawSettingsService settingsService, WorkerRewardWithdrawalProcessor withdrawalProcessor,
            WechatMerchantTransferClient transferClient) {
        this(accounts, flows, withdrawals, users, settingsService, withdrawalProcessor, transferClient, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BossRewardService(RewardAccountRepository accounts, RewardFlowRepository flows,
            RewardWithdrawalRepository withdrawals, UserRepository users,
            RewardWithdrawSettingsService settingsService, WorkerRewardWithdrawalProcessor withdrawalProcessor,
            WechatMerchantTransferClient transferClient, AlipayFundTransferClient alipayTransferClient) {
        this.accounts = accounts; this.flows = flows; this.withdrawals = withdrawals; this.users = users;
        this.settingsService = settingsService; this.withdrawalProcessor = withdrawalProcessor;
        this.transferClient = transferClient; this.alipayTransferClient = alipayTransferClient;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long bossId) {
        requireUserExists(bossId);
        BigDecimal balance = accounts.findFirstByUserIdAndRoleOrderByIdDesc(bossId, UserRole.BOSS)
                .map(a -> value(a.getBalance())).orElse(BigDecimal.ZERO);
        BigDecimal income = value(flows.sumByUserIdAndRoleAndType(bossId, UserRole.BOSS, "INCOME"));
        BigDecimal expense = value(flows.sumByUserIdAndRoleAndType(bossId, UserRole.BOSS, "EXPENSE"));
        return Map.of("balance", balance, "totalIncome", income, "totalExpense", expense,
                "withdrawableAmount", balance,
                "alipayWithdrawEnabled", alipayTransferClient != null && alipayTransferClient.transferEnabled());
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> records(Long bossId, String type, Pageable pageable) {
        requireUserExists(bossId);
        String normalized = type == null ? "ALL" : type.trim().toUpperCase();
        if (!Set.of("ALL", "INCOME", "EXPENSE").contains(normalized))
            throw new IllegalArgumentException("type 只能是 ALL、INCOME 或 EXPENSE");
        Page<RewardFlow> page = "ALL".equals(normalized)
                ? flows.findByUserIdAndRoleOrderByCreatedAtDescIdDesc(bossId, UserRole.BOSS, pageable)
                : flows.findByUserIdAndRoleAndTypeOrderByCreatedAtDescIdDesc(bossId, UserRole.BOSS, normalized, pageable);
        return page.map(this::flowView);
    }

    public Map<String, Object> withdraw(Long bossId, BigDecimal amount, String idempotencyKey) {
        return withdraw(bossId, amount, "WECHAT", null, idempotencyKey);
    }

    public Map<String, Object> withdraw(Long bossId, BigDecimal amount, String channel, String payeeAccount,
            String idempotencyKey) {
        if (amount == null) throw new IllegalArgumentException("amount 不能为空");
        String normalizedChannel = normalizeChannel(channel);
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        long amountFen = normalized.movePointRight(2).longValueExact();
        if (amountFen <= 0) throw new IllegalArgumentException("amount 必须大于0");
        String key = normalizeKey(bossId, idempotencyKey);
        users.findById(bossId).filter(UserRole::isBossIdentity)
                .orElseThrow(() -> new com.kuaima.app.common.ForbiddenBusinessException("当前账号尚未完成企业认证，暂不可提现奖励金"));
        String normalizedAccount = "ALIPAY".equals(normalizedChannel)
                ? normalizeAlipayAccount(payeeAccount)
                : null;
        if ("ALIPAY".equals(normalizedChannel)
                && (alipayTransferClient == null || !alipayTransferClient.transferEnabled())) {
            throw new IllegalStateException("支付宝提现暂未开通");
        }

        RewardWithdrawal existing = withdrawalProcessor.existing(bossId, amountFen, normalizedChannel, key, UserRole.BOSS).orElse(null);
        if (existing != null) return withdrawalView(existing);
        RewardWithdrawal submitted;
        try {
            submitted = withdrawalProcessor.submit(bossId, amountFen, normalizedChannel, settingsService.settings(),
                    key, UserRole.BOSS, normalizedAccount);
        } catch (DataIntegrityViolationException e) {
            RewardWithdrawal concurrent = withdrawalProcessor.existing(bossId, amountFen, normalizedChannel, key, UserRole.BOSS).orElse(null);
            if (concurrent != null) return withdrawalView(concurrent);
            throw e;
        }
        if ("ALIPAY".equals(normalizedChannel)) return alipayWithdraw(submitted, amountFen);

        String openid = users.findById(bossId).map(com.kuaima.app.domain.user.entity.User::getOpenid).orElse(null);
        WechatMerchantTransferClient.MerchantTransferResult result;
        try {
            result = transferClient.transfer(new WechatMerchantTransferClient.MerchantTransferRequest(
                    submitted.getMerchantBatchNo(), submitted.getMerchantDetailNo(), amountFen, openid, "快马日结奖励金提现"));
        } catch (RuntimeException e) {
            String reason = e.getMessage();
            withdrawalProcessor.transferFailed(submitted.getId(), reason);
            throw new WorkerRewardWithdrawalFailedException("微信商家转账发起失败，奖励金已退回：" + reason);
        }
        return withdrawalView(withdrawalProcessor.transferAccepted(submitted, result));
    }

    private Map<String, Object> alipayWithdraw(RewardWithdrawal submitted, long amountFen) {
        AlipayFundTransferClient.TransferResult result;
        try {
            result = alipayTransferClient.transfer(
                    submitted.getMerchantDetailNo(),
                    submitted.getPayeeAccount(),
                    BigDecimal.valueOf(amountFen, 2),
                    "快马日结奖励金提现");
        } catch (RuntimeException e) {
            withdrawalProcessor.transferFailed(submitted.getId(), e.getMessage());
            throw new WorkerRewardWithdrawalFailedException("支付宝转账发起失败，奖励金已退回");
        }
        RewardWithdrawal accepted = withdrawalProcessor.transferAccepted(submitted, result);
        if ("SUCCESS".equals(result.status())) {
            accepted = withdrawalProcessor.transferSucceeded(
                    accepted.getId(), result.orderId(), result.rawResponse());
        }
        return withdrawalView(accepted);
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> withdrawals(Long bossId, Pageable pageable) {
        requireUserExists(bossId);
        return withdrawals.findByUserIdAndRoleOrderByAppliedAtDescIdDesc(bossId, UserRole.BOSS, pageable).map(this::withdrawalView);
    }

    private void requireUserExists(Long userId) {
        users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
    }
    private Map<String, Object> flowView(RewardFlow flow) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", flow.getId()); result.put("type", flow.getType());
        result.put("amount", flow.getAmount()); result.put("title", flow.getTitle()); result.put("remark", flow.getRemark());
        result.put("bizType", flow.getBizType()); result.put("bizId", flow.getBizId()); result.put("createdAt", flow.getCreatedAt());
        result.put("status", flow.getStatus());
        result.put("description", flow.getDescription());
        result.put("channel", flow.getBizType() == null ? null
                : flow.getBizType().startsWith("ALIPAY") ? "ALIPAY"
                : flow.getBizType().startsWith("WECHAT") ? "WECHAT" : null);
        return result;
    }
    private Map<String, Object> withdrawalView(RewardWithdrawal withdrawal) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", withdrawal.getId());
        result.put("amount", withdrawal.getAmount()); result.put("status", withdrawal.getStatus());
        result.put("channel", withdrawal.getChannel());
        result.put("payeeAccount", withdrawal.getPayeeAccount());
        result.put("appliedAt", withdrawal.getAppliedAt()); result.put("paidAt", withdrawal.getPaidAt());
        result.put("failureReason", withdrawal.getFailureReason());
        JSONObject rawResponse = StringUtils.hasText(withdrawal.getTransferResponse())
                ? JSONObject.parseObject(withdrawal.getTransferResponse()) : null;
        result.put("state", rawResponse == null ? null : rawResponse.getString("state"));
        result.put("appId", rawResponse == null ? null : rawResponse.getString("app_id"));
        result.put("mchId", rawResponse == null ? null : rawResponse.getString("mch_id"));
        result.put("packageInfo", rawResponse == null ? null : rawResponse.getString("package_info"));
        return result;
    }
    private String normalizeKey(Long bossId, String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Idempotency-Key 不能为空");
        String trimmed = key.trim();
        if (trimmed.length() > 100) throw new IllegalArgumentException("Idempotency-Key 长度不能超过100");
        return "boss-reward:" + bossId + ":" + trimmed;
    }
    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel) || "WECHAT".equalsIgnoreCase(channel.trim())) return "WECHAT";
        if ("ALIPAY".equalsIgnoreCase(channel.trim())) return "ALIPAY";
        throw new IllegalArgumentException("提现渠道只能是 WECHAT 或 ALIPAY");
    }

    private String normalizeAlipayAccount(String account) {
        if (!StringUtils.hasText(account)) throw new IllegalArgumentException("支付宝账户不能为空");
        String normalized = account.trim();
        if (!normalized.matches("^1\\d{10}$") && !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("支付宝账户必须是手机号或邮箱");
        }
        return normalized;
    }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
