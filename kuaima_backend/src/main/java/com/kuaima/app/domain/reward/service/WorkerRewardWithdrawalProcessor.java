package com.kuaima.app.domain.reward.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.reward.entity.RewardAccount;
import com.kuaima.app.domain.reward.entity.RewardFlow;
import com.kuaima.app.domain.reward.entity.RewardWithdrawal;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardFlowRepository;
import com.kuaima.app.domain.reward.repository.RewardWithdrawalRepository;
import com.kuaima.app.domain.reward.service.RewardWithdrawSettingsService.RewardWithdrawSettings;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferResult;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

/** 负责奖励金提现的数据库事务边界；外部微信调用不放在余额扣减事务内。 */
@Service
public class WorkerRewardWithdrawalProcessor {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ORDER_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final UserRepository users;
    private final RewardAccountRepository accounts;
    private final RewardFlowRepository flows;
    private final RewardWithdrawalRepository withdrawals;

    public WorkerRewardWithdrawalProcessor(UserRepository users, RewardAccountRepository accounts,
            RewardFlowRepository flows, RewardWithdrawalRepository withdrawals) {
        this.users = users;
        this.accounts = accounts;
        this.flows = flows;
        this.withdrawals = withdrawals;
    }

    @Transactional(readOnly = true)
    public Optional<RewardWithdrawal> existing(Long userId, long amount, String channel, String normalizedKey) {
        return existing(userId, amount, channel, normalizedKey, "USER");
    }

    public Optional<RewardWithdrawal> existing(Long userId, long amount, String channel, String normalizedKey, String role) {
        return withdrawals.findByIdempotencyKey(normalizedKey)
                .map(old -> requireSameRequest(old, userId, amount, channel, role));
    }

    @Transactional
    public RewardWithdrawal submit(Long userId, long amount, String channel, RewardWithdrawSettings settings,
            String normalizedKey) {
        return submit(userId, amount, channel, settings, normalizedKey, "USER");
    }

    @Transactional
    public RewardWithdrawal submit(Long userId, long amount, String channel, RewardWithdrawSettings settings,
            String normalizedKey, String role) {
        Optional<RewardWithdrawal> old = existing(userId, amount, channel, normalizedKey, role);
        if (old.isPresent()) return old.get();

        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
        if (!"WECHAT".equals(settings.channel()) || !"WECHAT".equals(channel)) {
            throw new IllegalArgumentException("当前仅支持微信提现渠道");
        }
        if (!settings.enabled()) {
            throw new IllegalArgumentException(StringUtils.hasText(settings.disabledReason())
                    ? settings.disabledReason() : "奖励金提现暂未开放");
        }
        requireRealname(user);
        requireWechatBinding(user);
        if (amount < settings.minimumAmount()) {
            throw new IllegalArgumentException("最低提现金额为" + settings.minimumAmount() + "分");
        }

        RewardAccount account = lockedAccount(userId, role);
        BigDecimal amountYuan = yuan(amount);
        BigDecimal balance = value(account.getBalance());
        BigDecimal frozen = value(account.getFrozenAmount());
        if (balance.subtract(frozen).compareTo(amountYuan) < 0) {
            throw new IllegalArgumentException("奖励金可提现余额不足");
        }

        RewardWithdrawal withdrawal = new RewardWithdrawal();
        withdrawal.setUserId(userId);
        withdrawal.setRole(role);
        withdrawal.setAmount(amountYuan);
        withdrawal.setChannel(channel);
        withdrawal.setStatus("PENDING");
        withdrawal.setIdempotencyKey(normalizedKey);
        String suffix = LocalDateTime.now(ZONE).format(ORDER_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        withdrawal.setMerchantBatchNo("RWB" + suffix);
        withdrawal.setMerchantDetailNo("RWD" + suffix);
        withdrawal.setAppliedAt(LocalDateTime.now(ZONE));
        withdrawal = withdrawals.saveAndFlush(withdrawal);

        account.setBalance(balance.subtract(amountYuan));
        accounts.saveAndFlush(account);
        flow(withdrawal, amountYuan, account.getBalance(), "EXPENSE", "WECHAT_WITHDRAW",
                "提现至微信零钱", "奖励金提现申请", "PENDING", normalizedKey);
        return withdrawal;
    }

    @Transactional
    public RewardWithdrawal transferAccepted(RewardWithdrawal withdrawal, MerchantTransferResult result) {
        RewardWithdrawal locked = lockedWithdrawal(withdrawal.getId());
        locked.setWechatTransferNo(result.batchId());
        locked.setTransferResponse(result.rawResponse());
        locked.setUpdatedAt(LocalDateTime.now(ZONE));
        RewardWithdrawal saved = withdrawals.saveAndFlush(locked);
        flows.findByUserIdAndRoleAndBizTypeAndBizId(saved.getUserId(), saved.getRole(), "WECHAT_WITHDRAW", saved.getId())
                .ifPresent(flow -> flow.setStatus("PENDING"));
        return saved;
    }

    @Transactional
    public RewardWithdrawal transferSucceeded(Long withdrawalId, String wechatTransferNo, String rawResponse) {
        RewardWithdrawal withdrawal = lockedWithdrawal(withdrawalId);
        if ("SUCCESS".equals(withdrawal.getStatus())) return withdrawal;
        if (!"PENDING".equals(withdrawal.getStatus())) {
            throw new IllegalStateException("当前提现单状态不能标记成功");
        }
        withdrawal.setStatus("SUCCESS");
        withdrawal.setWechatTransferNo(wechatTransferNo);
        withdrawal.setTransferResponse(rawResponse);
        withdrawal.setPaidAt(LocalDateTime.now(ZONE));
        withdrawal.setUpdatedAt(LocalDateTime.now(ZONE));
        RewardWithdrawal saved = withdrawals.saveAndFlush(withdrawal);
        flows.findByUserIdAndRoleAndBizTypeAndBizId(saved.getUserId(), saved.getRole(), "WECHAT_WITHDRAW", saved.getId())
                .ifPresent(flow -> flow.setStatus("SUCCESS"));
        return saved;
    }

    @Transactional
    public RewardWithdrawal transferFailed(Long withdrawalId, String reason) {
        return transferFailed(withdrawalId, reason, null, null);
    }

    @Transactional
    public RewardWithdrawal transferFailed(Long withdrawalId, String reason, String wechatTransferNo, String rawResponse) {
        RewardWithdrawal withdrawal = lockedWithdrawal(withdrawalId);
        if ("FAILED".equals(withdrawal.getStatus())) return withdrawal;
        if (!"PENDING".equals(withdrawal.getStatus())) {
            throw new IllegalStateException("当前提现单状态不能标记失败");
        }
        RewardAccount account = accounts.findByUserIdAndRoleForUpdate(withdrawal.getUserId(), withdrawal.getRole())
                .or(() -> "USER".equals(withdrawal.getRole()) ? accounts.findByUserIdForUpdate(withdrawal.getUserId()) : java.util.Optional.empty())
                .orElseThrow(() -> new IllegalStateException("奖励金账户不存在"));
        BigDecimal amount = withdrawal.getAmount();
        account.setBalance(value(account.getBalance()).add(amount));
        accounts.saveAndFlush(account);

        RewardFlow withdrawFlow = flows.findByUserIdAndRoleAndBizTypeAndBizId(withdrawal.getUserId(), withdrawal.getRole(), "WECHAT_WITHDRAW", withdrawalId)
                .orElse(null);
        if (withdrawFlow != null) withdrawFlow.setStatus("FAILED");
        RewardFlow refund = new RewardFlow();
        refund.setUserId(withdrawal.getUserId());
        refund.setRole(withdrawal.getRole());
        refund.setType("INCOME");
        refund.setAmount(amount);
        refund.setBalanceAfter(account.getBalance());
        refund.setTitle("提现失败退回");
        refund.setRemark("微信商家转账发起失败，奖励金已退回");
        refund.setDescription("微信商家转账发起失败，奖励金已退回");
        refund.setBizType("WITHDRAW_REFUND");
        refund.setBizId(withdrawalId);
        refund.setSourceKey("REWARD_WITHDRAW_REFUND:" + withdrawalId);
        refund.setIdempotencyKey(withdrawal.getIdempotencyKey() + ":refund");
        refund.setStatus("SUCCESS");
        refund.setCreatedAt(LocalDateTime.now(ZONE));
        flows.saveAndFlush(refund);

        withdrawal.setStatus("FAILED");
        withdrawal.setFailureReason(failureReason(reason));
        withdrawal.setWechatTransferNo(StringUtils.hasText(wechatTransferNo) ? wechatTransferNo : withdrawal.getWechatTransferNo());
        withdrawal.setTransferResponse(StringUtils.hasText(rawResponse) ? rawResponse : withdrawal.getTransferResponse());
        withdrawal.setUpdatedAt(LocalDateTime.now(ZONE));
        return withdrawals.saveAndFlush(withdrawal);
    }

    private RewardWithdrawal lockedWithdrawal(Long id) {
        return withdrawals.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("奖励金提现单不存在: " + id));
    }

    private RewardAccount lockedAccount(Long userId, String role) {
        return accounts.findByUserIdAndRoleForUpdate(userId, role)
                .or(() -> "USER".equals(role) ? accounts.findByUserIdForUpdate(userId) : java.util.Optional.empty()).orElseGet(() -> {
            RewardAccount created = new RewardAccount();
            created.setUserId(userId);
            created.setRole(role);
            created.setBalance(BigDecimal.ZERO);
            created.setFrozenAmount(BigDecimal.ZERO);
            return accounts.saveAndFlush(created);
        });
    }

    private void flow(RewardWithdrawal withdrawal, BigDecimal amount, BigDecimal balanceAfter, String type,
            String bizType, String title, String description, String status, String idempotencyKey) {
        RewardFlow flow = new RewardFlow();
        flow.setUserId(withdrawal.getUserId());
        flow.setRole(withdrawal.getRole());
        flow.setType(type);
        flow.setAmount(amount);
        flow.setBalanceAfter(balanceAfter);
        flow.setTitle(title);
        flow.setRemark(description);
        flow.setDescription(description);
        flow.setBizType(bizType);
        flow.setBizId(withdrawal.getId());
        flow.setSourceKey("REWARD_WITHDRAW:" + withdrawal.getId());
        flow.setIdempotencyKey(idempotencyKey);
        flow.setStatus(status);
        flow.setCreatedAt(LocalDateTime.now(ZONE));
        flows.saveAndFlush(flow);
    }

    private RewardWithdrawal requireSameRequest(RewardWithdrawal withdrawal, Long userId, long amount, String channel, String role) {
        if (!Objects.equals(withdrawal.getUserId(), userId)) {
            throw new ForbiddenBusinessException("幂等键已被其他用户使用");
        }
        if (!Objects.equals(withdrawal.getRole(), role)) throw new ForbiddenBusinessException("幂等键已被其他身份使用");
        if (withdrawal.getAmount().compareTo(yuan(amount)) != 0) {
            throw new IllegalArgumentException("同一Idempotency-Key不能用于不同提现金额");
        }
        if (!Objects.equals(withdrawal.getChannel(), channel)) {
            throw new IllegalArgumentException("同一Idempotency-Key不能用于不同提现渠道");
        }
        return withdrawal;
    }

    private void requireRealname(User user) {
        boolean approved = "APPROVED".equals(user.getRealnameStatus())
                || ("REALNAME".equalsIgnoreCase(user.getCertType()) && "已通过".equals(user.getCertStatus()));
        if (!approved) throw new IllegalArgumentException("请先完成实名认证");
    }

    private void requireWechatBinding(User user) {
        if (!StringUtils.hasText(user.getOpenid())) throw new IllegalArgumentException("请先绑定微信账户");
    }

    private String failureReason(String reason) {
        String text = StringUtils.hasText(reason) ? reason.trim() : "微信商家转账发起失败";
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private BigDecimal yuan(long amount) {
        return BigDecimal.valueOf(amount, 2).setScale(2, RoundingMode.UNNECESSARY);
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
}
