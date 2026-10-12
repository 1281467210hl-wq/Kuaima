package com.kuaima.app.domain.reward.service;

import com.kuaima.app.domain.reward.entity.*;
import com.kuaima.app.domain.reward.repository.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RewardLedgerService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final RewardAccountRepository accounts;
    private final RewardFlowRepository flows;

    public RewardLedgerService(RewardAccountRepository accounts, RewardFlowRepository flows) {
        this.accounts = accounts; this.flows = flows;
    }

    @Transactional
    public void credit(Long userId, BigDecimal amount, String bizType, Long bizId, String title, String remark, String sourceKey) {
        credit(userId, "BOSS", amount, bizType, bizId, title, remark, sourceKey);
    }

    public void credit(Long userId, String role, BigDecimal amount, String bizType, Long bizId, String title, String remark, String sourceKey) {
        amount = normalize(amount);
        if (amount.signum() <= 0 || !flows.findAllBySourceKeyOrderByIdDesc(sourceKey).isEmpty()) return;
        RewardAccount account = lockedAccount(userId, role);
        account.setBalance(value(account.getBalance()).add(amount));
        accounts.save(account);
        saveFlow(userId, role, "INCOME", amount, account.getBalance(), title, remark, bizType, bizId, sourceKey);
    }

    public RewardAccount lockedAccount(Long userId) {
        return lockedAccount(userId, "BOSS");
    }

    public RewardAccount lockedAccount(Long userId, String role) {
        List<RewardAccount> matches = accounts.findAllByUserIdAndRoleForUpdate(userId, role);
        if (!matches.isEmpty()) return matches.get(0);
        return accounts.findFirstByUserIdAndRoleOrderByIdDesc(userId, role).orElseGet(() -> {
            RewardAccount account = new RewardAccount(); account.setUserId(userId); account.setBalance(BigDecimal.ZERO);
            account.setRole(role);
            return accounts.save(account);
        });
    }

    public void saveFlow(Long userId, String type, BigDecimal amount, BigDecimal balanceAfter, String title,
                         String remark, String bizType, Long bizId, String sourceKey) {
        saveFlow(userId, "BOSS", type, amount, balanceAfter, title, remark, bizType, bizId, sourceKey);
    }
    public void saveFlow(Long userId, String role, String type, BigDecimal amount, BigDecimal balanceAfter, String title,
                         String remark, String bizType, Long bizId, String sourceKey) {
        RewardFlow flow = new RewardFlow(); flow.setUserId(userId); flow.setType(type); flow.setAmount(amount);
        flow.setRole(role);
        flow.setBalanceAfter(balanceAfter); flow.setTitle(title); flow.setRemark(remark); flow.setBizType(bizType);
        flow.setBizId(bizId); flow.setSourceKey(sourceKey); flow.setCreatedAt(LocalDateTime.now(ZONE)); flows.save(flow);
    }
    public void saveFlow(Long userId, String type, long amount, long balanceAfter, String title,
                         String remark, String bizType, Long bizId, String sourceKey) {
        saveFlow(userId, type, BigDecimal.valueOf(amount), BigDecimal.valueOf(balanceAfter), title, remark, bizType, bizId, sourceKey);
    }

    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal normalize(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.setScale(2, java.math.RoundingMode.HALF_UP); }
}
