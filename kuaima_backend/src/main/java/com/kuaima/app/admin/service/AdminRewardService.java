package com.kuaima.app.admin.service;

import com.alibaba.fastjson2.JSON;
import com.kuaima.app.admin.entity.*;
import com.kuaima.app.admin.repository.*;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.entity.*;
import com.kuaima.app.domain.wallet.repository.*;
import com.kuaima.app.domain.reward.service.RewardLedgerService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AdminRewardService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final RewardCampaignRepository campaigns;
    private final RewardGrantRepository grants;
    private final RewardFundAccountRepository funds;
    private final RewardFundFlowRepository fundFlows;
    private final UserRepository users;
    private final WalletRespository wallets;
    private final WalletFlowRespository walletFlows;
    private final RewardLedgerService rewardLedger;
    /**
     * 奖励发放默认不受平台预存预算拦截。余额仍会按实际支出记账，便于审计；
     * 需要财务预存控制时可通过配置显式开启。
     */
    @Value("${kuaima.reward.fund-control-enabled:false}")
    private boolean fundControlEnabled;

    public AdminRewardService(RewardCampaignRepository campaigns, RewardGrantRepository grants,
                              RewardFundAccountRepository funds, RewardFundFlowRepository fundFlows,
                              UserRepository users, WalletRespository wallets, WalletFlowRespository walletFlows,
                              RewardLedgerService rewardLedger) {
        this.campaigns = campaigns; this.grants = grants; this.funds = funds;
        this.fundFlows = fundFlows; this.users = users; this.wallets = wallets; this.walletFlows = walletFlows;
        this.rewardLedger = rewardLedger;
    }

    @Transactional
    public RewardCampaign create(Map<String, Object> body, Long operatorId, String operatorName) {
        String name = text(body, "name"), target = text(body, "target"), scope = text(body, "scope");
        String amountMode = text(body, "amountMode"), sendMode = text(body, "sendMode");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("奖励名称不能为空");
        if (!Set.of("老板", "零工", "全部用户").contains(target)) throw new IllegalArgumentException("target 参数无效");
        if (!Set.of("指定", "全部").contains(scope)) throw new IllegalArgumentException("scope 参数无效");
        if (!Set.of("fixed", "random").contains(amountMode)) throw new IllegalArgumentException("amountMode 参数无效");
        if (!Set.of("now", "timing").contains(sendMode)) throw new IllegalArgumentException("sendMode 参数无效");

        List<User> recipients = resolveUsers(body, target, scope);
        int plannedCount = recipients.size();
        if ("全部".equals(scope)) {
            plannedCount = integer(body, "count");
            if (plannedCount <= 0) throw new IllegalArgumentException("count 必须大于0");
            if (plannedCount < recipients.size()) recipients = new ArrayList<>(recipients.subList(0, plannedCount));
            else plannedCount = recipients.size();
        }
        if (recipients.isEmpty()) throw new IllegalArgumentException("没有符合条件的发放用户");

        BigDecimal amount = BigDecimal.ZERO, min = BigDecimal.ZERO, max = BigDecimal.ZERO;
        if ("fixed".equals(amountMode)) {
            amount = money(body, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("amount 必须大于0");
        } else {
            min = money(body, "min"); max = money(body, "max");
            if (min.signum() <= 0 || max.compareTo(min) <= 0) throw new IllegalArgumentException("random 金额区间不合法");
        }
        LocalDateTime sendAt = "now".equals(sendMode) ? LocalDateTime.now(ZONE) : dateTime(body.get("sendAt"));
        if (sendAt == null) throw new IllegalArgumentException("定时发放时间不能为空");
        if ("timing".equals(sendMode) && !sendAt.isAfter(LocalDateTime.now(ZONE)))
            throw new IllegalArgumentException("定时发放时间必须晚于当前时间");

        RewardCampaign c = new RewardCampaign();
        c.setRewardNo("RW" + LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 5).toUpperCase());
        c.setName(name); c.setTarget(target); c.setScope(scope);
        c.setUsers(JSON.toJSONString(recipients.stream().map(User::getId).distinct().toList()));
        c.setPlannedCount(plannedCount); c.setAmountMode(amountMode);
        c.setAmount("fixed".equals(amountMode) ? amount : null); c.setMinAmount(min); c.setMaxAmount(max);
        c.setTotalAmount("fixed".equals(amountMode) ? amount.multiply(BigDecimal.valueOf(plannedCount))
                : min.add(max).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(plannedCount)));
        c.setStatus("待发放"); c.setSendMode(sendMode); c.setSendAt(sendAt); c.setCreatedAt(LocalDateTime.now(ZONE));
        c.setRemark(text(body, "remark")); c.setOperatorId(operatorId); c.setOperatorName(operatorName);
        c = campaigns.save(c);
        if ("now".equals(sendMode)) execute(c.getId());
        return get(c.getId());
    }

    private List<User> resolveUsers(Map<String, Object> body, String target, String scope) {
        List<User> all;
        if ("指定".equals(scope)) {
            Object raw = body.get("users");
            List<String> identifiers = raw instanceof Collection<?> values
                    ? values.stream().map(String::valueOf).filter(s -> !s.isBlank()).distinct().toList()
                    : Arrays.stream(String.valueOf(raw == null ? "" : raw).split("[,，\\s]+"))
                            .filter(s -> !s.isBlank()).distinct().toList();
            if (identifiers.isEmpty()) throw new IllegalArgumentException("指定用户不能为空");
            all = new ArrayList<>();
            for (String identifier : identifiers) {
                User user = null;
                try { user = users.findById(Long.valueOf(identifier)).orElse(null); }
                catch (NumberFormatException ignored) { }
                if (user == null) user = users.findByPhone(identifier).stream().findFirst().orElse(null);
                if (user == null) throw new EntityNotFoundException("用户不存在: " + identifier);
                all.add(user);
            }
        } else all = users.findAll();
        List<User> matched = all.stream().filter(user -> "全部用户".equals(target)
                || ("老板".equals(target) && UserRole.isBossIdentity(user))
                || ("零工".equals(target) && UserRole.isWorkerIdentity(user))).distinct().toList();
        if ("指定".equals(scope) && matched.size() != all.stream().distinct().count())
            throw new IllegalArgumentException("指定用户类型与 target 不匹配");
        return matched;
    }

    @Transactional
    public RewardCampaign execute(Long id) {
        RewardCampaign c = get(id);
        if ("已撤销".equals(c.getStatus()) || "已发放".equals(c.getStatus())) return c;
        List<Long> ids = c.getUsers() == null ? List.of() : JSON.parseArray(c.getUsers(), Long.class);
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        for (Long userId : ids) if (!grants.existsByCampaignIdAndUserId(id, userId))
            amounts.put(userId, "fixed".equals(c.getAmountMode()) ? c.getAmount()
                    : randomMoney(c.getMinAmount(), c.getMaxAmount()));
        BigDecimal required = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        RewardFundAccount fund = funds.findForUpdate(1L)
                .orElseThrow(() -> new IllegalStateException("平台奖励预算账户未配置"));
        if (fundControlEnabled && value(fund.getBalance()).compareTo(required) < 0)
            throw new IllegalStateException("平台奖励预算不足");
        int success = 0; BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : amounts.entrySet()) {
            Long userId = entry.getKey(); BigDecimal value = entry.getValue();
            User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("发放用户不存在: " + userId));
            String walletRole = UserRole.isBossIdentity(user) ? UserRole.BOSS : UserRole.USER;
            walletCredit(userId, value, id, c.getRemark(), walletRole);
            RewardGrant grant = new RewardGrant(); grant.setCampaignId(id); grant.setUserId(userId); grant.setAmount(value);
            grant.setUserRole(UserRole.isBossIdentity(user) ? UserRole.BOSS : UserRole.USER); grant.setStatus("SUCCESS");
            grant.setGrantedAt(LocalDateTime.now(ZONE)); grants.save(grant);
            if (rewardLedger != null) rewardLedger.credit(userId, walletRole, value, "ADMIN_REWARD", id,
                    "平台奖励金", c.getRemark(), "ADMIN_REWARD:" + id + ":" + userId);
            success++; sum = sum.add(value);
        }
        if (required.signum() > 0) {
            fund.setBalance(value(fund.getBalance()).subtract(required)); funds.save(fund);
            RewardFundFlow flow = new RewardFundFlow(); flow.setFundAccountId(fund.getId()); flow.setDirection("outcome");
            flow.setAmount(required); flow.setBalanceAfter(fund.getBalance()); flow.setCampaignId(id);
            flow.setRemark("奖励金发放"); flow.setCreatedAt(LocalDateTime.now(ZONE)); fundFlows.save(flow);
        }
        c.setActualCount((c.getActualCount() == null ? 0 : c.getActualCount()) + success);
        c.setActualAmount(value(c.getActualAmount()).add(sum));
        c.setFailedCount(0); c.setErrorMessage(null); c.setStatus("已发放");
        return campaigns.save(c);
    }

    @Transactional
    public void recordFailure(Long id, String error) {
        campaigns.findById(id).ifPresent(c -> {
            int actual = c.getActualCount() == null ? 0 : c.getActualCount();
            c.setFailedCount(Math.max(0, (c.getPlannedCount() == null ? 0 : c.getPlannedCount()) - actual));
            c.setErrorMessage(error == null || error.isBlank() ? "执行失败" : error); campaigns.save(c);
        });
    }

    private void walletCredit(Long userId, BigDecimal amount, Long campaignId, String remark, String role) {
        List<Wallet> matches = wallets.findAllByUserIdAndRoleForUpdate(userId, role);
        Wallet wallet = matches.isEmpty() ? null : matches.get(0);
        if (wallet == null) {
            Wallet created = new Wallet(); created.setUserId(userId); created.setRole(role); created.setBalance(BigDecimal.ZERO);
            wallet = wallets.save(created);
        }
        wallet.setBalance(value(wallet.getBalance()).add(amount)); wallets.save(wallet);
        WalletFlow flow = new WalletFlow(); flow.setUserId(userId); flow.setDirection("income"); flow.setBizType("REWARD");
        flow.setAmount(amount); flow.setBalanceAfter(wallet.getBalance()); flow.setBizId(campaignId); flow.setRemark(remark);
        walletFlows.save(flow);
    }

    @Transactional
    public RewardCampaign cancel(Long id) {
        RewardCampaign c = get(id);
        if (!"待发放".equals(c.getStatus())) throw new IllegalArgumentException("仅待发放活动可撤销");
        c.setStatus("已撤销"); return campaigns.save(c);
    }

    public RewardCampaign get(Long id) {
        return campaigns.findById(id).orElseThrow(() -> new EntityNotFoundException("奖励活动不存在: " + id));
    }
    public Page<RewardCampaign> list(String status, Pageable pageable) { return campaigns.search(status, pageable); }
    public Map<String, Object> stats() {
        long total = campaigns.count(), pending = campaigns.countByStatus("待发放");
        long distributed = campaigns.countByStatus("已发放"), canceled = campaigns.countByStatus("已撤销");
        long coverage = grants.countByStatus("SUCCESS"), boss = grants.countByUserRoleAndStatus("BOSS", "SUCCESS");
        long worker = grants.countByUserRoleAndStatus("USER", "SUCCESS");
        LocalDate month = LocalDate.now(ZONE).withDayOfMonth(1);
        BigDecimal monthAmount = Optional.ofNullable(grants.sumSuccessAmount(month.atStartOfDay(), month.plusMonths(1).atStartOfDay())).orElse(BigDecimal.ZERO);
        long monthCoverage = grants.countSuccess(month.atStartOfDay(), month.plusMonths(1).atStartOfDay());
        BigDecimal totalAmount = campaigns.findAll().stream().map(c -> value(c.getActualAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate quarter = LocalDate.now(ZONE).withMonth(((LocalDate.now(ZONE).getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
        BigDecimal quarterAmount = Optional.ofNullable(grants.sumSuccessAmount(quarter.atStartOfDay(), quarter.plusMonths(3).atStartOfDay())).orElse(BigDecimal.ZERO);
        BigDecimal previousQuarterAmount = Optional.ofNullable(grants.sumSuccessAmount(quarter.minusMonths(3).atStartOfDay(), quarter.atStartOfDay())).orElse(BigDecimal.ZERO);
        BigDecimal totalRate = previousQuarterAmount.signum() == 0 ? BigDecimal.ZERO
                : quarterAmount.subtract(previousQuarterAmount).multiply(BigDecimal.valueOf(100))
                        .divide(previousQuarterAmount, 1, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAmount", totalAmount); result.put("monthAmount", monthAmount);
        result.put("pendingCount", pending); result.put("distributedCount", distributed);
        result.put("canceledCount", canceled); result.put("totalCount", total); result.put("coverage", coverage);
        result.put("bossCoverage", boss); result.put("workerCoverage", worker); result.put("totalRate", totalRate);
        result.put("monthCoverage", monthCoverage);
        result.put("fundControlEnabled", fundControlEnabled);
        funds.findById(1L).ifPresent(fund -> result.put("fundBalance", value(fund.getBalance())));
        return result;
    }
    private String text(Map<String, Object> body, String key) { return body.get(key) == null ? null : String.valueOf(body.get(key)); }
    private int integer(Map<String, Object> body, String key) {
        try { return Integer.parseInt(String.valueOf(body.get(key))); }
        catch (Exception e) { throw new IllegalArgumentException(key + " 必须是整数"); }
    }
    private BigDecimal money(Map<String, Object> body, String key) {
        try { return new BigDecimal(String.valueOf(body.get(key))).setScale(2, RoundingMode.HALF_UP); }
        catch (Exception e) { throw new IllegalArgumentException(key + " 必须是金额"); }
    }
    private BigDecimal randomMoney(BigDecimal min, BigDecimal max) {
        long cents = ThreadLocalRandom.current().nextLong(min.movePointRight(2).longValueExact(), max.movePointRight(2).longValueExact() + 1);
        return BigDecimal.valueOf(cents, 2);
    }
    private LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        try { return LocalDateTime.parse(String.valueOf(value).replace('T', ' '), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) { throw new IllegalArgumentException("sendAt 时间格式无效"); }
    }
    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
