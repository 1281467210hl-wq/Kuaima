package com.kuaima.app.domain.points.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.entity.PointsWithdrawal;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.points.repository.PointsWithdrawalRepository;
import com.kuaima.app.domain.points.model.PointsWithdrawalModels.WithdrawalView;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class WorkerPointsWithdrawalService {
    public static final String USER = "USER";
    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String CANCELED = "CANCELED";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> VALID_TODAY = Set.of(PENDING, PROCESSING, SUCCESS);

    private final UserRepository users;
    private final PointsAccountRepository accounts;
    private final PointsFlowRepository flows;
    private final PointsWithdrawalRepository withdrawals;

    public WorkerPointsWithdrawalService(UserRepository users, PointsAccountRepository accounts,
                                         PointsFlowRepository flows, PointsWithdrawalRepository withdrawals) {
        this.users = users; this.accounts = accounts; this.flows = flows; this.withdrawals = withdrawals;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> config(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
        int balance = accounts.findFirstByUserIdAndRoleOrderByIdDesc(userId, USER)
                .map(a -> value(a.getBalance())).orElse(0);
        LocalDate today = LocalDate.now(ZONE);
        boolean applied = withdrawals.countValidToday(userId, VALID_TODAY, today.atStartOfDay(), today.plusDays(1).atStartOfDay()) > 0;
        Map<String, Object> wechat = new LinkedHashMap<>();
        wechat.put("code", "WECHAT"); wechat.put("name", "微信零钱");
        wechat.put("available", StringUtils.hasText(user.getOpenid()));
        wechat.put("accountMasked", StringUtils.hasText(user.getOpenid()) ? "已实名账户" : null);
        Map<String, Object> alipay = new LinkedHashMap<>();
        alipay.put("code", "ALIPAY"); alipay.put("name", "支付宝"); alipay.put("available", false); alipay.put("accountMasked", null);
        return Map.of("balance", balance, "exchangeRate", 100, "cashRate", 1, "minPoints", 1000,
                "multiple", 100, "fee", BigDecimal.ZERO.setScale(2), "dailyLimit", 1,
                "todayApplied", applied, "channels", List.of(wechat, alipay));
    }

    @Transactional
    public PointsWithdrawal apply(Long userId, Integer points, String channel, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) throw new IllegalArgumentException("Idempotency-Key 不能为空");
        String key = idempotencyKey.trim();
        PointsWithdrawal old = withdrawals.findByIdempotencyKey(key).orElse(null);
        if (old != null) {
            if (!userId.equals(old.getUserId()) || !USER.equals(old.getRole())) throw new ForbiddenBusinessException("幂等键已被其他用户使用");
            return old;
        }
        if (points == null || points < 1000) throw new IllegalArgumentException("提现积分最低1000分");
        if (points % 100 != 0) throw new IllegalArgumentException("提现积分必须是100的整数倍");
        if (!"WECHAT".equals(channel)) throw new IllegalArgumentException("当前仅支持已绑定的微信零钱");
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
        if (!StringUtils.hasText(user.getOpenid())) throw new IllegalArgumentException("请先绑定微信账户");
        List<PointsAccount> accountsForUpdate = accounts.findAllByUserIdAndRoleForUpdate(userId, USER);
        PointsAccount account = accountsForUpdate.isEmpty()
                ? accounts.findFirstByUserIdAndRoleOrderByIdDesc(userId, USER)
                        .orElseThrow(() -> new IllegalArgumentException("零工积分账户不存在"))
                : accountsForUpdate.get(0);
        old = withdrawals.findByIdempotencyKey(key).orElse(null);
        if (old != null) return old;
        LocalDate today = LocalDate.now(ZONE);
        if (withdrawals.countValidToday(userId, VALID_TODAY, today.atStartOfDay(), today.plusDays(1).atStartOfDay()) > 0) {
            throw new IllegalStateException("每天最多提交1次积分提现");
        }
        int before = value(account.getBalance());
        if (points > before) throw new IllegalArgumentException("积分余额不足");
        int after = before - points;
        account.setBalance(after); accounts.save(account);
        String no = "PWD" + LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        PointsWithdrawal w = new PointsWithdrawal();
        w.setWithdrawNo(no); w.setUserId(userId); w.setRole(USER); w.setPoints(points); w.setAmount(BigDecimal.valueOf(points, 2));
        w.setFee(BigDecimal.ZERO.setScale(2)); w.setChannel(channel); w.setChannelAccount(user.getOpenid()); w.setStatus(PENDING); w.setIdempotencyKey(key); w.setAppliedAt(LocalDateTime.now(ZONE));
        w = withdrawals.save(w);
        flow(userId, -points, after, "POINTS_WITHDRAW", no, "积分提现申请");
        return w;
    }

    @Transactional(readOnly = true)
    public Page<PointsWithdrawal> list(Long userId, String status, Pageable pageable) { return "ALL".equals(status) ? withdrawals.findByUserIdAndRoleOrderByAppliedAtDescIdDesc(userId, USER, pageable) : withdrawals.findByUserIdAndRoleAndStatusOrderByAppliedAtDescIdDesc(userId, USER, status, pageable); }

    @Transactional(readOnly = true)
    public Page<PointsWithdrawal> adminList(String status, Pageable pageable) { return "ALL".equals(status) ? withdrawals.findAllByOrderByAppliedAtDescIdDesc(pageable) : withdrawals.findAllByStatusOrderByAppliedAtDescIdDesc(status, pageable); }

    @Transactional(readOnly = true)
    public PointsWithdrawal detail(Long userId, Long id) { return withdrawals.findById(id).filter(w -> userId.equals(w.getUserId()) && USER.equals(w.getRole())).orElseThrow(() -> new EntityNotFoundException("提现记录不存在")); }

    public WithdrawalView view(PointsWithdrawal w) {
        java.time.OffsetDateTime expected = w.getAppliedAt() == null ? null
                : w.getAppliedAt().toLocalDate().plusDays(1).atTime(23, 59, 59).atZone(ZONE).toOffsetDateTime();
        return new WithdrawalView(w.getId(), w.getWithdrawNo(), w.getPoints(), w.getAmount(), w.getChannel(),
                w.getStatus(), w.getFee(), w.getFailureReason(), w.getAppliedAt(), expected,
                w.getProcessingAt(), w.getPaidAt());
    }

    @Transactional
    public PointsWithdrawal approve(Long id, Long adminId) {
        PointsWithdrawal w = locked(id); if (!PENDING.equals(w.getStatus()) && !PROCESSING.equals(w.getStatus())) throw new IllegalStateException("当前状态不能审核通过");
        w.setStatus(SUCCESS); w.setProcessingAt(w.getProcessingAt() == null ? LocalDateTime.now(ZONE) : w.getProcessingAt()); w.setPaidAt(LocalDateTime.now(ZONE)); w.setReviewedBy(adminId); w.setReviewedAt(LocalDateTime.now(ZONE)); return withdrawals.save(w);
    }

    @Transactional
    public PointsWithdrawal reject(Long id, Long adminId, String reason) {
        PointsWithdrawal w = locked(id); if (FAILED.equals(w.getStatus())) return w;
        if (SUCCESS.equals(w.getStatus()) || CANCELED.equals(w.getStatus())) throw new IllegalStateException("当前状态不能驳回");
        PointsAccount account = lockedAccount(w.getUserId()).orElseThrow(() -> new IllegalStateException("零工积分账户不存在"));
        int after = value(account.getBalance()) + w.getPoints(); account.setBalance(after); accounts.save(account);
        flow(w.getUserId(), w.getPoints(), after, "POINTS_WITHDRAW_REFUND", w.getWithdrawNo(), "积分提现失败退回");
        w.setStatus(FAILED); w.setFailureReason(StringUtils.hasText(reason) ? reason.trim() : "审核驳回"); w.setReviewedBy(adminId); w.setReviewedAt(LocalDateTime.now(ZONE)); return withdrawals.save(w);
    }

    @Transactional
    public PointsWithdrawal retry(Long id, Long adminId) {
        PointsWithdrawal w = locked(id); if (!FAILED.equals(w.getStatus())) throw new IllegalStateException("仅失败记录可以重试");
        PointsAccount account = lockedAccount(w.getUserId()).orElseThrow(() -> new IllegalStateException("零工积分账户不存在"));
        int before = value(account.getBalance()); if (before < w.getPoints()) throw new IllegalStateException("用户当前积分不足，不能重试");
        int after = before - w.getPoints(); account.setBalance(after); accounts.save(account);
        flow(w.getUserId(), -w.getPoints(), after, "POINTS_WITHDRAW", w.getWithdrawNo(), "积分提现重新处理");
        w.setStatus(PROCESSING); w.setFailureReason(null); w.setProcessingAt(LocalDateTime.now(ZONE)); w.setReviewedBy(adminId); w.setReviewedAt(LocalDateTime.now(ZONE)); return withdrawals.save(w);
    }

    private PointsWithdrawal locked(Long id) { return withdrawals.findByIdForUpdate(id).orElseThrow(() -> new EntityNotFoundException("提现记录不存在: " + id)); }
    private Optional<PointsAccount> lockedAccount(Long userId) {
        List<PointsAccount> matches = accounts.findAllByUserIdAndRoleForUpdate(userId, USER);
        return matches.isEmpty() ? accounts.findFirstByUserIdAndRoleOrderByIdDesc(userId, USER) : Optional.of(matches.get(0));
    }
    private void flow(Long userId, int delta, int after, String type, String no, String remark) { PointsFlow f = new PointsFlow(); f.setUserId(userId); f.setRole(USER); f.setDelta(delta); f.setBalanceAfter(after); f.setBizType(type); f.setBizNo(no); f.setRemark(remark); flows.save(f); }
    private int value(Integer v) { return v == null ? 0 : v; }
}
