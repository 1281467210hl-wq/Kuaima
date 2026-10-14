package com.kuaima.app.admin.service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.admin.dto.AdminMerchantAccountDto;
import com.kuaima.app.admin.entity.MerchantAccountBank;
import com.kuaima.app.admin.repository.AdminBossMerchantAccountRepository;
import com.kuaima.app.admin.repository.AdminBossRechargeOrderRepository;
import com.kuaima.app.admin.repository.MerchantAccountBankRepository;
import com.kuaima.app.admin.util.AdminMoneyUtils;
import com.kuaima.app.domain.boss.entity.BossBalanceRechargeOrder;
import com.kuaima.app.domain.boss.entity.BossMerchantAccount;
import com.kuaima.app.domain.payroll.constant.PayrollConstants;
import com.kuaima.app.domain.payroll.entity.PayrollOrder;
import com.kuaima.app.domain.payroll.repository.PayrollOrderRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * 后台「商户账户余额」服务：跨老板的商户账户查询与维护 + 充值订单查询。
 * <p>单位约定：boss_merchant_account.balance 以「分」存储，接口同时返回 xxxFen 与 xxxYuan；
 * boss_balance_recharge_order.amount 以「元」存储，出参换算为「分」；
 * 开户银行/对公账号/冻结金额落在扩展表 boss_merchant_account_bank（boss_merchant_account 无这些列）。
 * <p>平台全局视角：不再绑定登录老板身份，bossId/bossKeyword 为可选筛选。
 */
@Service
@RequiredArgsConstructor
public class AdminMerchantAccountService {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FROZEN = "frozen";
    /** 软删除标记：删除只改状态，保留余额与流水可追溯 */
    public static final String STATUS_DELETED = "deleted";

    private static final Set<String> RECHARGE_PAID = Set.of("paid", "manual_confirmed");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AdminBossMerchantAccountRepository accountRepository;
    private final MerchantAccountBankRepository bankRepository;
    private final AdminBossRechargeOrderRepository rechargeOrderRepository;
    private final PayrollOrderRepository payrollOrderRepository;
    private final UserRepository userRepository;

    // ==================== 账户列表 / 详情 ====================

    /** 跨老板的商户账户列表：关键词/老板/状态/默认标记筛选 + 分页，附总余额统计。 */
    @Transactional(readOnly = true)
    public Map<String, Object> list(AdminMerchantAccountDto.Query query) {
        int page = Math.max(query.getPage(), 0);
        int size = normalizeSize(query.getSize());
        Page<BossMerchantAccount> accounts = accountRepository.findAll(accountSpec(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<Map<String, Object>> records = rows(accounts.getContent());

        long totalBalanceFen = accounts.getContent().stream()
                .mapToLong(a -> a.getBalance() == null ? 0L : a.getBalance()).sum();
        long globalBalanceFen = nvl(accountRepository.sumBalanceExcludingDeleted());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("page", page);
        data.put("size", size);
        data.put("total", accounts.getTotalElements());
        data.put("pageBalanceFen", totalBalanceFen);
        data.put("pageBalanceYuan", AdminMoneyUtils.fenToYuan(totalBalanceFen));
        data.put("globalBalanceFen", globalBalanceFen);
        data.put("globalBalanceYuan", AdminMoneyUtils.fenToYuan(globalBalanceFen));
        return data;
    }

    /** 余额详情：账户信息 + 银行扩展信息 + 累计充值/累计发薪支出 + 最近转账。 */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        BossMerchantAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("商户账户不存在: " + id));
        MerchantAccountBank bank = bankRepository.findByAccountId(id).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("account", row(account, bank));
        data.put("stats", statsOf(account.getBossId()));
        data.put("recentRechargeOrders", recentRechargeOrders(id));
        data.put("recentTransfers", recentTransfers(account.getBossId()));
        return data;
    }

    // ==================== 新增 / 编辑 / 软删除 ====================

    /** 新增商户账户：bossId/公司简称/主体全称/商户号必填，商户号在未删除账户内唯一。 */
    @Transactional
    public Map<String, Object> create(AdminMerchantAccountDto.SaveRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (req.getBossId() == null) {
            throw new IllegalArgumentException("所属老板 bossId 必填");
        }
        userRepository.findById(req.getBossId())
                .orElseThrow(() -> new IllegalArgumentException("老板账号不存在: " + req.getBossId()));
        String accountName = require(req.getAccountName(), "公司简称/账户名称").trim();
        String subjectName = require(req.getSubjectName(), "主体全称").trim();
        String merchantNo = require(req.getMerchantNo(), "商户号").trim();
        assertMerchantNoAvailable(merchantNo, null);

        BossMerchantAccount account = new BossMerchantAccount();
        account.setBossId(req.getBossId());
        account.setAccountName(accountName);
        account.setSubjectName(subjectName);
        account.setMerchantNo(merchantNo);
        account.setBalance(resolveBalanceFen(req, 0L));
        // 老板名下首个账户自动置为默认
        boolean firstAccount = accountRepository
                .findByBossIdAndStatusNotOrderByIdDesc(req.getBossId(), STATUS_DELETED).isEmpty();
        account.setIsDefault(Boolean.TRUE.equals(req.getIsDefault())
                || (req.getIsDefault() == null && firstAccount));
        account.setStatus(defaultStatus(req.getStatus()));
        account = accountRepository.save(account);

        if (Boolean.TRUE.equals(account.getIsDefault())) {
            clearOtherDefaults(account.getBossId(), account.getId());
        }
        saveBank(account, req, null);
        return detail(account.getId());
    }

    @Transactional
    public Map<String, Object> update(Long id, AdminMerchantAccountDto.SaveRequest req) {
        BossMerchantAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("商户账户不存在: " + id));
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (StringUtils.hasText(req.getMerchantNo())) {
            String merchantNo = req.getMerchantNo().trim();
            assertMerchantNoAvailable(merchantNo, id);
            account.setMerchantNo(merchantNo);
        }
        if (StringUtils.hasText(req.getAccountName())) {
            account.setAccountName(req.getAccountName().trim());
        }
        if (StringUtils.hasText(req.getSubjectName())) {
            account.setSubjectName(req.getSubjectName().trim());
        }
        if (req.getBalanceFen() != null || req.getBalanceYuan() != null) {
            account.setBalance(resolveBalanceFen(req, account.getBalance()));
        }
        if (StringUtils.hasText(req.getStatus())) {
            account.setStatus(normalizeStatus(req.getStatus()));
        }
        // 软删除状态的账户不允许被重新设为默认
        if (Boolean.TRUE.equals(req.getIsDefault()) && !STATUS_DELETED.equals(account.getStatus())) {
            account.setIsDefault(true);
        } else if (Boolean.FALSE.equals(req.getIsDefault())) {
            account.setIsDefault(false);
        }
        if (req.getBossId() != null && !req.getBossId().equals(account.getBossId())) {
            throw new IllegalArgumentException("商户账户所属老板不可变更");
        }
        account = accountRepository.save(account);
        if (Boolean.TRUE.equals(account.getIsDefault())) {
            clearOtherDefaults(account.getBossId(), account.getId());
        }
        MerchantAccountBank existing = bankRepository.findByAccountId(account.getId()).orElse(null);
        saveBank(account, req, existing);
        return detail(account.getId());
    }

    /** 软删除：状态置 deleted、取消默认标记，余额与流水保留。 */
    @Transactional
    public Map<String, Object> delete(Long id) {
        BossMerchantAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("商户账户不存在: " + id));
        account.setStatus(STATUS_DELETED);
        account.setIsDefault(false);
        accountRepository.save(account);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", account.getId());
        data.put("status", account.getStatus());
        data.put("isDefault", account.getIsDefault());
        data.put("message", "商户账户已软删除（保留余额与流水，可用 status=deleted 查询）");
        return data;
    }

    // ==================== 充值订单 ====================

    /** 跨老板充值订单查询：老板/状态/单号/支付方式/日期筛选 + 分页。amount 单位为「元」。 */
    @Transactional(readOnly = true)
    public Map<String, Object> rechargeOrders(AdminMerchantAccountDto.Query query) {
        int page = Math.max(query.getPage(), 0);
        int size = normalizeSize(query.getSize());
        Page<BossBalanceRechargeOrder> orders = rechargeOrderRepository.findAll(rechargeSpec(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));

        Set<Long> accountIds = orders.getContent().stream().map(BossBalanceRechargeOrder::getAccountId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BossMerchantAccount> accounts = accountIds.isEmpty() ? Map.of()
                : accountRepository.findAllById(accountIds).stream()
                        .collect(Collectors.toMap(BossMerchantAccount::getId, a -> a, (x, y) -> x));

        List<Map<String, Object>> records = orders.getContent().stream().map(order -> {
            BossMerchantAccount account = order.getAccountId() == null ? null : accounts.get(order.getAccountId());
            BigDecimal amount = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
            long amountFen = AdminMoneyUtils.yuanToFen(amount);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", order.getId());
            row.put("orderNo", order.getOrderNo());
            row.put("bossId", order.getBossId());
            row.put("bossName", order.getBossName());
            row.put("companyName", order.getCompanyName());
            row.put("accountId", order.getAccountId());
            row.put("accountName", account == null ? null : account.getAccountName());
            row.put("subjectName", account == null ? null : account.getSubjectName());
            row.put("merchantNo", account == null ? null : account.getMerchantNo());
            row.put("amountYuan", amount);
            row.put("amountFen", amountFen);
            row.put("payMethod", order.getPayMethod());
            row.put("payMethodText", payMethodText(order.getPayMethod()));
            row.put("status", order.getStatus());
            row.put("statusText", rechargeStatusText(order.getStatus()));
            row.put("transactionId", order.getTransactionId());
            row.put("payTime", order.getPayTime());
            row.put("createdAt", order.getTimestamp());
            row.put("remark", order.getRemark());
            row.put("operatorName", order.getOperatorName());
            return row;
        }).collect(Collectors.toList());

        long paidFen = orders.getContent().stream()
                .filter(o -> o.getStatus() != null && RECHARGE_PAID.contains(o.getStatus()))
                .mapToLong(o -> AdminMoneyUtils.yuanToFen(o.getAmount())).sum();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("page", page);
        data.put("size", size);
        data.put("total", orders.getTotalElements());
        data.put("pagePaidAmountFen", paidFen);
        data.put("pagePaidAmountYuan", AdminMoneyUtils.fenToYuan(paidFen));
        return data;
    }

    // ==================== 查询条件装配 ====================

    private Specification<BossMerchantAccount> accountSpec(AdminMerchantAccountDto.Query query) {
        return (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            String status = trim(query.getStatus());
            if (status == null) {
                ps.add(cb.notEqual(root.get("status"), STATUS_DELETED));
            } else {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (query.getBossId() != null) {
                ps.add(cb.equal(root.get("bossId"), query.getBossId()));
            }
            if (query.getIsDefault() != null) {
                ps.add(cb.equal(root.get("isDefault"), query.getIsDefault()));
            }
            List<Long> bossIds = bossIds(query.getBossKeyword());
            if (bossIds != null) {
                ps.add(root.get("bossId").in(bossIds));
            }
            String keyword = trim(query.getKeyword());
            if (keyword != null) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(cb.like(root.get("accountName"), like), cb.like(root.get("subjectName"), like),
                        cb.like(root.get("merchantNo"), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private Specification<BossBalanceRechargeOrder> rechargeSpec(AdminMerchantAccountDto.Query query) {
        return (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            String status = trim(query.getStatus());
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (query.getBossId() != null) {
                ps.add(cb.equal(root.get("bossId"), query.getBossId()));
            }
            if (query.getAccountId() != null) {
                ps.add(cb.equal(root.get("accountId"), query.getAccountId()));
            }
            List<Long> bossIds = bossIds(query.getBossKeyword());
            if (bossIds != null) {
                ps.add(root.get("bossId").in(bossIds));
            }
            String payMethod = trim(query.getPayMethod());
            if (payMethod != null) {
                ps.add(cb.equal(root.get("payMethod"), payMethod));
            }
            String orderNo = trim(query.getOrderNo());
            if (orderNo != null) {
                ps.add(cb.equal(root.get("orderNo"), orderNo));
            }
            String keyword = trim(query.getKeyword());
            if (keyword != null) {
                String like = "%" + keyword + "%";
                ps.add(cb.or(cb.like(root.get("orderNo"), like), cb.like(root.get("companyName"), like),
                        cb.like(root.get("bossName"), like), cb.like(root.get("transactionId"), like)));
            }
            if (query.getStartDate() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("timestamp"),
                        Date.from(query.getStartDate().atStartOfDay(ZONE).toInstant())));
            }
            if (query.getEndDate() != null) {
                ps.add(cb.lessThan(root.get("timestamp"),
                        Date.from(query.getEndDate().plusDays(1).atStartOfDay(ZONE).toInstant())));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** 老板关键词命中则返回 id 集合（空集合会让条件恒假），未传关键词返回 null 表示不加该条件。 */
    private List<Long> bossIds(String bossKeyword) {
        if (!StringUtils.hasText(bossKeyword)) {
            return null;
        }
        return userRepository.searchByRole(UserRole.BOSS, null, bossKeyword.trim(), PageRequest.of(0, 200))
                .map(User::getId).getContent();
    }

    // ==================== 视图组装 ====================

    private List<Map<String, Object>> rows(List<BossMerchantAccount> accounts) {
        if (accounts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = accounts.stream().map(BossMerchantAccount::getId).filter(Objects::nonNull).toList();
        Map<Long, MerchantAccountBank> banks = bankRepository.findByAccountIdIn(ids).stream()
                .collect(Collectors.toMap(MerchantAccountBank::getAccountId, b -> b, (x, y) -> x));
        List<Long> bossIds = accounts.stream().map(BossMerchantAccount::getBossId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> bossNames = new LinkedHashMap<>();
        userRepository.findAllById(bossIds).forEach(user -> bossNames.put(user.getId(), displayName(user)));
        return accounts.stream()
                .map(account -> row(account, banks.get(account.getId()), bossNames.get(account.getBossId())))
                .collect(Collectors.toList());
    }

    private Map<String, Object> row(BossMerchantAccount account, MerchantAccountBank bank) {
        String bossName = userRepository.findById(account.getBossId()).map(this::displayName).orElse(null);
        return row(account, bank, bossName);
    }

    private Map<String, Object> row(BossMerchantAccount account, MerchantAccountBank bank, String bossName) {
        long balanceFen = account.getBalance() == null ? 0L : account.getBalance();
        long frozenFen = bank == null || bank.getFrozenAmount() == null ? 0L : bank.getFrozenAmount();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", account.getId());
        row.put("bossId", account.getBossId());
        row.put("bossName", bossName);
        row.put("accountName", account.getAccountName());
        row.put("companyShort", account.getAccountName());
        row.put("subjectName", account.getSubjectName());
        row.put("merchantNo", account.getMerchantNo());
        row.put("balanceFen", balanceFen);
        row.put("balanceYuan", AdminMoneyUtils.fenToYuan(balanceFen));
        row.put("frozenFen", frozenFen);
        row.put("frozenYuan", AdminMoneyUtils.fenToYuan(frozenFen));
        row.put("availableFen", balanceFen - frozenFen);
        row.put("availableYuan", AdminMoneyUtils.fenToYuan(balanceFen - frozenFen));
        row.put("bankName", bank == null ? null : bank.getBankName());
        row.put("bankBranch", bank == null ? null : bank.getBankBranch());
        row.put("bankAccountNo", bank == null ? null : bank.getBankAccountNo());
        row.put("remark", bank == null ? null : bank.getRemark());
        row.put("isDefault", Boolean.TRUE.equals(account.getIsDefault()));
        row.put("status", account.getStatus());
        row.put("statusText", accountStatusText(account.getStatus()));
        row.put("createdAt", account.getDate());
        row.put("updatedAt", account.getTimestamp());
        return row;
    }

    /** 老板维度的资金概览：累计充值成功（元→分）、累计发薪支出（分）、账户余额。 */
    private Map<String, Object> statsOf(Long bossId) {
        BigDecimal rechargeYuan = rechargeOrderRepository.sumPaidAmountByBossId(bossId);
        long rechargeFen = AdminMoneyUtils.yuanToFen(rechargeYuan);
        List<PayrollOrder> paidOrders = payrollOrderRepository
                .findByCreatorIdAndStatusOrderByIdDesc(bossId, PayrollConstants.ORDER_APPROVED);
        long payoutFen = paidOrders.stream().mapToLong(o -> o.getAmount() == null ? 0L : o.getAmount()).sum();
        long people = paidOrders.stream().mapToLong(o -> o.getPeopleCount() == null ? 0 : o.getPeopleCount()).sum();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("rechargeAmountFen", rechargeFen);
        stats.put("rechargeAmountYuan", AdminMoneyUtils.fenToYuan(rechargeFen));
        stats.put("payoutAmountFen", payoutFen);
        stats.put("payoutAmountYuan", AdminMoneyUtils.fenToYuan(payoutFen));
        stats.put("payoutOrderCount", paidOrders.size());
        stats.put("payoutPeopleCount", people);
        return stats;
    }

    private List<Map<String, Object>> recentRechargeOrders(Long accountId) {
        return rechargeOrderRepository
                .findAll((Specification<BossBalanceRechargeOrder>) (root, cq, cb) ->
                        cb.equal(root.get("accountId"), accountId),
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream().map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("orderNo", order.getOrderNo());
                    row.put("amountYuan", order.getAmount());
                    row.put("amountFen", AdminMoneyUtils.yuanToFen(order.getAmount()));
                    row.put("payMethod", order.getPayMethod());
                    row.put("status", order.getStatus());
                    row.put("statusText", rechargeStatusText(order.getStatus()));
                    row.put("payTime", order.getPayTime());
                    return row;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> recentTransfers(Long bossId) {
        return payrollOrderRepository.findByCreatorIdAndStatusOrderByIdDesc(bossId, PayrollConstants.ORDER_APPROVED)
                .stream().limit(5).map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", order.getId());
                    row.put("orderNo", order.getOrderNo());
                    row.put("title", order.getTitle());
                    row.put("projectName", order.getProjectName());
                    row.put("amountFen", order.getAmount());
                    row.put("amountYuan", AdminMoneyUtils.fenToYuan(order.getAmount()));
                    row.put("peopleCount", order.getPeopleCount());
                    row.put("payAccount", order.getPayAccount());
                    row.put("payTime", order.getPayTime());
                    return row;
                }).collect(Collectors.toList());
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    // ==================== 写操作辅助 ====================

    private void saveBank(BossMerchantAccount account, AdminMerchantAccountDto.SaveRequest req,
                          MerchantAccountBank existing) {
        boolean hasBankInfo = StringUtils.hasText(req.getBankName()) || StringUtils.hasText(req.getBankBranch())
                || StringUtils.hasText(req.getBankAccountNo())
                || req.getFrozenFen() != null || req.getFrozenYuan() != null || StringUtils.hasText(req.getRemark());
        if (!hasBankInfo && existing == null) {
            return;
        }
        MerchantAccountBank bank = existing == null ? new MerchantAccountBank() : existing;
        bank.setAccountId(account.getId());
        bank.setBossId(account.getBossId());
        if (StringUtils.hasText(req.getBankName())) {
            bank.setBankName(req.getBankName().trim());
        }
        if (StringUtils.hasText(req.getBankBranch())) {
            bank.setBankBranch(req.getBankBranch().trim());
        }
        if (StringUtils.hasText(req.getBankAccountNo())) {
            bank.setBankAccountNo(req.getBankAccountNo().trim());
        }
        if (StringUtils.hasText(req.getRemark())) {
            bank.setRemark(req.getRemark().trim());
        }
        if (req.getFrozenFen() != null) {
            bank.setFrozenAmount(req.getFrozenFen());
        } else if (req.getFrozenYuan() != null) {
            bank.setFrozenAmount(AdminMoneyUtils.yuanToFen(req.getFrozenYuan()));
        } else if (bank.getFrozenAmount() == null) {
            bank.setFrozenAmount(0L);
        }
        bankRepository.save(bank);
    }

    /** 同一老板只保留一个默认账户。 */
    private void clearOtherDefaults(Long bossId, Long keepAccountId) {
        for (BossMerchantAccount other : accountRepository
                .findByBossIdAndStatusNotOrderByIdDesc(bossId, STATUS_DELETED)) {
            if (!Objects.equals(other.getId(), keepAccountId) && Boolean.TRUE.equals(other.getIsDefault())) {
                other.setIsDefault(false);
                accountRepository.save(other);
            }
        }
    }

    private void assertMerchantNoAvailable(String merchantNo, Long selfId) {
        List<BossMerchantAccount> exists = accountRepository.findByMerchantNoAndStatusNot(merchantNo, STATUS_DELETED);
        for (BossMerchantAccount account : exists) {
            if (!Objects.equals(account.getId(), selfId)) {
                throw new IllegalArgumentException("商户号已存在: " + merchantNo);
            }
        }
    }

    private long resolveBalanceFen(AdminMerchantAccountDto.SaveRequest req, Long fallback) {
        if (req.getBalanceFen() != null) {
            return req.getBalanceFen();
        }
        if (req.getBalanceYuan() != null) {
            return AdminMoneyUtils.yuanToFen(req.getBalanceYuan());
        }
        return fallback == null ? 0L : fallback;
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? normalizeStatus(status) : STATUS_ACTIVE;
    }

    private String normalizeStatus(String status) {
        String value = trim(status);
        if (STATUS_FROZEN.equals(value) || STATUS_DELETED.equals(value)) {
            return value;
        }
        return STATUS_ACTIVE;
    }

    private String accountStatusText(String status) {
        return switch (String.valueOf(status)) {
            case STATUS_FROZEN -> "已冻结";
            case STATUS_DELETED -> "已删除";
            default -> "正常";
        };
    }

    private String rechargeStatusText(String status) {
        return switch (String.valueOf(status)) {
            case "paid" -> "已支付";
            case "manual_confirmed" -> "人工确认入账";
            case "manual_pending" -> "待人工确认";
            case "canceled" -> "已取消";
            case "failed" -> "支付失败";
            default -> "待支付";
        };
    }

    private String payMethodText(String payMethod) {
        if ("wechat".equalsIgnoreCase(String.valueOf(payMethod))) {
            return "微信支付";
        }
        if ("alipay".equalsIgnoreCase(String.valueOf(payMethod))) {
            return "支付宝支付";
        }
        return payMethod == null ? "" : payMethod;
    }

    private String displayName(User user) {
        if (StringUtils.hasText(user.getCompanyName())) {
            return user.getCompanyName();
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        return user.getNickname();
    }

    private String require(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " 必填");
        }
        return value;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizeSize(int size) {
        return size <= 0 ? 10 : Math.min(size, 200);
    }
}
