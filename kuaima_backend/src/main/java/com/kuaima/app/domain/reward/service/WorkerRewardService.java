package com.kuaima.app.domain.reward.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.reward.entity.RewardAccount;
import com.kuaima.app.domain.reward.entity.RewardEarningRule;
import com.kuaima.app.domain.reward.entity.RewardFlow;
import com.kuaima.app.domain.reward.entity.RewardWithdrawal;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardEarningRuleRepository;
import com.kuaima.app.domain.reward.repository.RewardFlowRepository;
import com.kuaima.app.domain.reward.service.RewardWithdrawSettingsService.RewardWithdrawSettings;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferRequest;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferResult;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

/** 零工端奖励金查询与提现应用服务。对外金额单位统一为“分”；复用现有奖励金账户存储。 */
@Service
public class WorkerRewardService {
    private static final Set<String> RECORD_TYPES = Set.of("ALL", "INCOME", "WITHDRAW");

    private final UserRepository users;
    private final RewardAccountRepository accounts;
    private final RewardFlowRepository flows;
    private final RewardEarningRuleRepository rules;
    private final RewardWithdrawSettingsService settingsService;
    private final WorkerRewardWithdrawalProcessor withdrawalProcessor;
    private final WechatMerchantTransferClient transferClient;

    public WorkerRewardService(UserRepository users, RewardAccountRepository accounts,
            RewardFlowRepository flows, RewardEarningRuleRepository rules,
            RewardWithdrawSettingsService settingsService, WorkerRewardWithdrawalProcessor withdrawalProcessor,
            WechatMerchantTransferClient transferClient) {
        this.users = users;
        this.accounts = accounts;
        this.flows = flows;
        this.rules = rules;
        this.settingsService = settingsService;
        this.withdrawalProcessor = withdrawalProcessor;
        this.transferClient = transferClient;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long userId) {
        User user = currentUser(userId);
        RewardWithdrawSettings settings = settingsService.settings();
        RewardAccount account = accounts.findByUserIdAndRole(userId, "USER").orElseGet(() -> accounts.findByUserId(userId).orElse(null));
        long balance = fen(account == null ? null : account.getBalance());
        long frozen = fen(account == null ? null : account.getFrozenAmount());
        long withdrawable = Math.max(0, balance - frozen);

        String disabledReason = disabledReason(user, settings, withdrawable);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", balance);
        result.put("withdrawableBalance", withdrawable);
        result.put("minimumWithdrawAmount", settings.minimumAmount());
        result.put("withdrawEnabled", disabledReason == null);
        result.put("withdrawDisabledReason", disabledReason);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> records(Long userId, String type, Pageable pageable) {
        String normalized = type == null ? "ALL" : type.trim().toUpperCase();
        if (!RECORD_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("type 只能是 ALL、INCOME 或 WITHDRAW");
        }
        Page<RewardFlow> page = "ALL".equals(normalized)
                ? Optional.ofNullable(flows.findByUserIdAndRoleOrderByCreatedAtDescIdDesc(userId, "USER", pageable)).orElseGet(() -> flows.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable))
                : Optional.ofNullable(flows.findByUserIdAndRoleAndTypeOrderByCreatedAtDescIdDesc(userId, "USER", storageType(normalized), pageable)).orElseGet(() -> flows.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(userId, storageType(normalized), pageable));
        return Map.of(
                "records", page.map(this::recordView).getContent(),
                "total", page.getTotalElements(),
                "page", page.getNumber(),
                "size", page.getSize(),
                "hasMore", page.getNumber() + 1 < page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> rules() {
        RewardWithdrawSettings settings = settingsService.settings();
        List<Map<String, Object>> methods = rules.findByEnabledTrueOrderBySortAscIdAsc().stream()
                .map(this::ruleView)
                .toList();
        return Map.of(
                "minimumWithdrawAmount", settings.minimumAmount(),
                "withdrawChannel", settings.channel(),
                "earningMethods", methods);
    }

    public Map<String, Object> withdraw(Long userId, Long amount, String channel, String idempotencyKey) {
        if (amount == null || amount <= 0) throw new IllegalArgumentException("amount 必须是大于0的分数金额");
        String normalizedChannel = channel == null ? "" : channel.trim().toUpperCase();
        if (!"WECHAT".equals(normalizedChannel)) throw new IllegalArgumentException("当前仅支持 WECHAT 提现渠道");
        String key = normalizeKey(userId, idempotencyKey);
        RewardWithdrawSettings settings = settingsService.settings();
        RewardWithdrawal existing = withdrawalProcessor.existing(userId, amount, normalizedChannel, key).orElse(null);
        if (existing != null) return withdrawalView(existing);

        RewardWithdrawal submitted;
        try {
            submitted = withdrawalProcessor.submit(userId, amount, normalizedChannel, settings, key, "USER");
            if (submitted == null) submitted = withdrawalProcessor.submit(userId, amount, normalizedChannel, settings, key);
        } catch (DataIntegrityViolationException e) {
            RewardWithdrawal concurrent = withdrawalProcessor.existing(userId, amount, normalizedChannel, key).orElse(null);
            if (concurrent != null) return withdrawalView(concurrent);
            throw e;
        }
        User user = currentUser(userId);
        MerchantTransferResult result;
        try {
            result = transferClient.transfer(new MerchantTransferRequest(
                    submitted.getMerchantBatchNo(), submitted.getMerchantDetailNo(), amount,
                    user.getOpenid(), "快马日结奖励金提现"));
        } catch (RuntimeException e) {
            String reason = transferFailureReason(e.getMessage());
            withdrawalProcessor.transferFailed(submitted.getId(), reason);
            throw new WorkerRewardWithdrawalFailedException("微信商家转账发起失败，奖励金已退回：" + reason);
        }
        RewardWithdrawal accepted = withdrawalProcessor.transferAccepted(submitted, result);
        return withdrawalView(accepted);
    }

    private String normalizeKey(Long userId, String key) {
        if (!StringUtils.hasText(key)) throw new IllegalArgumentException("Idempotency-Key 不能为空");
        String trimmed = key.trim();
        if (trimmed.length() > 100) throw new IllegalArgumentException("Idempotency-Key 长度不能超过100");
        return "worker-reward:" + userId + ":" + trimmed;
    }

    private String transferFailureReason(String reason) {
        String text = StringUtils.hasText(reason) ? reason.trim() : "未知错误";
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    private User currentUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
    }

    private String storageType(String type) {
        return "WITHDRAW".equals(type) ? "EXPENSE" : type;
    }

    private String disabledReason(User user, RewardWithdrawSettings settings, long withdrawable) {
        if (!settings.enabled()) {
            return StringUtils.hasText(settings.disabledReason()) ? settings.disabledReason() : "奖励金提现暂未开放";
        }
        if (!isRealnameApproved(user)) return "请先完成实名认证";
        if (!StringUtils.hasText(user.getOpenid())) return "请先绑定微信账户";
        if (withdrawable < settings.minimumAmount()) return "奖励金余额低于最低提现金额";
        return null;
    }

    private boolean isRealnameApproved(User user) {
        return "APPROVED".equals(user.getRealnameStatus())
                || ("REALNAME".equalsIgnoreCase(user.getCertType()) && "已通过".equals(user.getCertStatus()));
    }

    private Map<String, Object> recordView(RewardFlow flow) {
        boolean income = "INCOME".equals(flow.getType());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", flow.getId());
        result.put("type", income ? "INCOME" : "WITHDRAW");
        result.put("bizType", flow.getBizType());
        result.put("title", flow.getTitle());
        result.put("description", StringUtils.hasText(flow.getDescription()) ? flow.getDescription() : flow.getRemark());
        result.put("amount", income ? fen(flow.getAmount()) : -fen(flow.getAmount()));
        result.put("balanceAfter", fen(flow.getBalanceAfter()));
        result.put("status", StringUtils.hasText(flow.getStatus()) ? flow.getStatus() : "SUCCESS");
        result.put("createdAt", flow.getCreatedAt());
        return result;
    }

    private Map<String, Object> ruleView(RewardEarningRule rule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", rule.getCode());
        result.put("name", rule.getName());
        result.put("description", rule.getDescription());
        result.put("rewardAmount", rule.getRewardAmount());
        result.put("actionType", rule.getActionType());
        result.put("actionPath", rule.getActionPath());
        result.put("enabled", Boolean.TRUE.equals(rule.getEnabled()));
        return result;
    }

    private Map<String, Object> withdrawalView(RewardWithdrawal withdrawal) {
        RewardAccount account = accounts.findByUserIdAndRole(withdrawal.getUserId(), "USER").orElseGet(() -> accounts.findByUserId(withdrawal.getUserId()).orElse(null));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("withdrawId", withdrawal.getId());
        result.put("amount", fen(withdrawal.getAmount()));
        result.put("status", withdrawal.getStatus());
        result.put("balance", fen(account == null ? null : account.getBalance()));
        result.put("createdAt", withdrawal.getCreatedAt() == null ? withdrawal.getAppliedAt() : withdrawal.getCreatedAt());
        return result;
    }

    private long fen(BigDecimal yuan) {
        return yuan == null ? 0L : yuan.movePointRight(2).longValueExact();
    }
}
