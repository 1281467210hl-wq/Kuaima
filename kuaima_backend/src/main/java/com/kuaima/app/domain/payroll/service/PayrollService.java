package com.kuaima.app.domain.payroll.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.boss.entity.BossMerchantAccount;
import com.kuaima.app.domain.boss.repository.BossMerchantAccountRepository;
import com.kuaima.app.domain.payroll.constant.PayrollConstants;
import com.kuaima.app.domain.payroll.entity.PayrollDetail;
import com.kuaima.app.domain.payroll.entity.PayrollOrder;
import com.kuaima.app.domain.payroll.repository.PayrollDetailRepository;
import com.kuaima.app.domain.payroll.repository.PayrollOrderRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class PayrollService {

    private final PayrollOrderRepository orderRepository;
    private final PayrollDetailRepository detailRepository;
    private final PayrollPayService payService;
    private final BossMerchantAccountRepository accountRepository;

    public PayrollService(PayrollOrderRepository orderRepository,
                          PayrollDetailRepository detailRepository,
                          PayrollPayService payService,
                          BossMerchantAccountRepository accountRepository) {
        this.orderRepository = orderRepository;
        this.detailRepository = detailRepository;
        this.payService = payService;
        this.accountRepository = accountRepository;
    }

    public Page<PayrollOrder> listOrders(String tab, String status, Long projectId, String keyword, Pageable pageable) {
        Specification<PayrollOrder> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if ("reviewed".equals(tab)) {
                predicates.add(cb.in(root.get("status")).value(PayrollConstants.ORDER_APPROVED).value(PayrollConstants.ORDER_REJECTED));
            } else {
                // submitted：除已撤回外都展示
                predicates.add(cb.notEqual(root.get("status"), PayrollConstants.ORDER_WITHDRAWN));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("title"), like),
                        cb.like(root.get("creator"), like),
                        cb.like(root.get("reviewBy"), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return orderRepository.findAll(spec, pageable);
    }

    public PayrollOrder getOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("发薪单不存在: " + id));
    }

    public List<PayrollDetail> details(Long id) {
        return detailRepository.findByPayrollId(id);
    }

    /** 创建发薪单并写入明细，自动汇总应发金额与人数。金额单位统一为「分」。 */
    @Transactional(rollbackFor = Exception.class)
    public PayrollOrder createOrder(PayrollOrder order, List<PayrollDetail> details, Long creatorId, String creator) {
        if (order.getStatus() == null) {
            order.setStatus(PayrollConstants.ORDER_PENDING);
        }
        // 管理后台创建薪单即视为已提交（待审批），submitTime 若未显式传入则默认为当前时间
        if (order.getSubmitTime() == null) {
            order.setSubmitTime(new java.util.Date());
        }
        if (order.getOrderNo() == null) {
            order.setOrderNo("TR" + System.currentTimeMillis());
        }
        if (creator != null) {
            order.setCreator(creator);
        }
        if (creatorId != null) {
            order.setCreatorId(creatorId);
        }
        order.setAmount(0L);
        order.setPeopleCount(0);
        PayrollOrder saved = orderRepository.save(order);

        long totalAmount = 0L;
        if (details != null) {
            for (PayrollDetail d : details) {
                d.setPayrollId(saved.getId());
                if (d.getStatus() == null) {
                    d.setStatus(PayrollConstants.DETAIL_PENDING);
                }
                // 应发金额 = 日薪（分）× 出勤天数
                // 只要日薪或出勤天数有值，就以日薪×天数为准（覆盖前端显式传的 amount）
                long dailyWage = d.getDailyWage() == null ? 0L : d.getDailyWage();
                int attendDays = d.getAttendDays() == null ? 0 : d.getAttendDays();
                if (dailyWage > 0 || attendDays > 0) {
                    d.setAmount(dailyWage * attendDays);
                } else if (d.getAmount() == null) {
                    d.setAmount(0L);
                }
                totalAmount += d.getAmount() == null ? 0L : d.getAmount();
                detailRepository.save(d);
            }
        }
        saved.setAmount(totalAmount);
        saved.setPeopleCount(details == null ? 0 : details.size());
        return orderRepository.save(saved);
    }

    @Transactional
    public PayrollOrder submit(Long id) {
        PayrollOrder order = getOrThrow(id);
        order.setSubmitTime(new java.util.Date());
        order.setStatus(PayrollConstants.ORDER_PENDING);
        return orderRepository.save(order);
    }

    /* ==================== 草稿流程（1016 原型：draft→添加人员→设置薪资→提交审核） ==================== */

    /** 创建草稿发薪单（无明细，状态 draft）。 */
    @Transactional(rollbackFor = Exception.class)
    public PayrollOrder createDraft(PayrollOrder order, Long creatorId, String creator) {
        order.setStatus(PayrollConstants.ORDER_DRAFT);
        order.setSubmitTime(null);
        order.setAmount(0L);
        order.setPeopleCount(0);
        if (order.getOrderNo() == null) {
            order.setOrderNo("TR" + System.currentTimeMillis());
        }
        if (creator != null) {
            order.setCreator(creator);
        }
        if (creatorId != null) {
            order.setCreatorId(creatorId);
        }
        return orderRepository.save(order);
    }

    /** 草稿态：整体替换人员明细；按 userId/手机号 保留此前已设置的日薪与出勤天数。 */
    @Transactional(rollbackFor = Exception.class)
    public PayrollOrder replaceMembers(Long id, List<PayrollDetail> people) {
        PayrollOrder order = requireDraft(id);
        List<PayrollDetail> existing = detailRepository.findByPayrollId(id);
        Map<String, PayrollDetail> oldByKey = new java.util.HashMap<>();
        for (PayrollDetail d : existing) {
            oldByKey.put(personKey(d.getUserId(), d.getPhone()), d);
        }
        detailRepository.deleteAll(existing);
        List<PayrollDetail> rebuilt = new ArrayList<>();
        if (people != null) {
            for (PayrollDetail p : people) {
                PayrollDetail d = oldByKey.get(personKey(p.getUserId(), p.getPhone()));
                if (d == null) {
                    d = new PayrollDetail();
                }
                d.setPayrollId(order.getId());
                d.setUserId(p.getUserId());
                d.setName(p.getName());
                d.setPhone(p.getPhone());
                d.setJob(p.getJob());
                // 保留已设置薪资；新人员若带默认日薪则采用
                if (p.getDailyWage() != null && p.getDailyWage() > 0 && (d.getDailyWage() == null || d.getDailyWage() == 0L)) {
                    d.setDailyWage(p.getDailyWage());
                }
                if (p.getAttendDays() != null && p.getAttendDays() > 0 && (d.getAttendDays() == null || d.getAttendDays() == 0)) {
                    d.setAttendDays(p.getAttendDays());
                }
                d.setStatus(PayrollConstants.DETAIL_PENDING);
                rebuilt.add(d);
            }
        }
        recomputeAmounts(rebuilt);
        detailRepository.saveAll(rebuilt);
        return recomputeOrder(order, rebuilt);
    }

    /** 草稿态：设置每人薪资（dailyWage 分 / attendDays），重算金额。 */
    @Transactional(rollbackFor = Exception.class)
    public PayrollOrder setSalaries(Long id, List<PayrollDetail> salaries) {
        PayrollOrder order = requireDraft(id);
        List<PayrollDetail> existing = detailRepository.findByPayrollId(id);
        Map<String, PayrollDetail> byKey = new java.util.HashMap<>();
        for (PayrollDetail d : existing) {
            byKey.put(personKey(d.getUserId(), d.getPhone()), d);
        }
        if (salaries != null) {
            for (PayrollDetail s : salaries) {
                PayrollDetail d = byKey.get(personKey(s.getUserId(), s.getPhone()));
                if (d == null) {
                    continue;
                }
                if (s.getDailyWage() != null) {
                    d.setDailyWage(s.getDailyWage());
                }
                if (s.getAttendDays() != null) {
                    d.setAttendDays(s.getAttendDays());
                }
            }
        }
        recomputeAmounts(existing);
        detailRepository.saveAll(existing);
        return recomputeOrder(order, existing);
    }

    /** 提交草稿 → 待审批；校验有人员且金额>0。 */
    @Transactional(rollbackFor = Exception.class)
    public PayrollOrder submitDraft(Long id) {
        PayrollOrder order = requireDraft(id);
        List<PayrollDetail> details = detailRepository.findByPayrollId(id);
        if (details.isEmpty()) {
            throw new IllegalArgumentException("请先添加发薪人员");
        }
        recomputeAmounts(details);
        long sum = details.stream().mapToLong(d -> d.getAmount() == null ? 0L : d.getAmount()).sum();
        if (sum <= 0) {
            throw new IllegalArgumentException("请先设置薪资");
        }
        detailRepository.saveAll(details);
        order.setStatus(PayrollConstants.ORDER_PENDING);
        order.setSubmitTime(new java.util.Date());
        order.setAmount(sum);
        order.setPeopleCount(details.size());
        return orderRepository.save(order);
    }

    private PayrollOrder requireDraft(Long id) {
        PayrollOrder order = getOrThrow(id);
        if (!PayrollConstants.ORDER_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有待提交审核（草稿）的发薪单可编辑人员与薪资");
        }
        return order;
    }

    private String personKey(Long userId, String phone) {
        if (userId != null) {
            return "u:" + userId;
        }
        return "p:" + (phone == null ? "" : phone.trim());
    }

    /** 应发金额 = 日薪（分）× 出勤天数。 */
    private void recomputeAmounts(List<PayrollDetail> details) {
        for (PayrollDetail d : details) {
            long dailyWage = d.getDailyWage() == null ? 0L : d.getDailyWage();
            int attendDays = d.getAttendDays() == null ? 0 : d.getAttendDays();
            d.setAmount(dailyWage * attendDays);
        }
    }

    private PayrollOrder recomputeOrder(PayrollOrder order, List<PayrollDetail> details) {
        long sum = details.stream().mapToLong(d -> d.getAmount() == null ? 0L : d.getAmount()).sum();
        order.setAmount(sum);
        order.setPeopleCount(details.size());
        return orderRepository.save(order);
    }

    /**
     * 审批通过发薪单：自动从老板账户余额扣款，并逐人打入零工钱包。
     *
     * <p>加锁顺序固定为「发薪单 → 明细 → 老板账户」，所有审批入口共用本方法，防并发重复扣款。
     * 任一步失败整体回滚，绝不允许「部分付款」。
     */
    @Transactional(rollbackFor = Exception.class)
    public PayrollOrder approve(Long id, String reviewer) {
        // 1) 锁发薪单行，校验 pending（主幂等防线）
        PayrollOrder order = orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("发薪单不存在: " + id));
        if (!PayrollConstants.ORDER_PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("只能审批待审批的发薪单");
        }

        // 2) 锁明细（同事务）
        List<PayrollDetail> details = detailRepository.findByPayrollIdInForUpdate(List.of(id));

        // 3) 整单级校验：明细非空、金额/人数与单一致（防篡改）
        validateOrderIntegrity(order, details);

        // 4) 收款人解析（白名单校验：非本项目成员 → 整单拒付；无收款账号 → 逐笔 failed）
        PayrollPayService.PayeeResolution resolution = payService.resolvePayees(order.getProjectId(), details);
        List<PayrollDetail> payable = resolution.payable();
        List<PayrollDetail> unpayable = resolution.unpayable();
        long totalFen = payable.stream()
                .mapToLong(d -> d.getAmount() == null ? 0L : d.getAmount())
                .sum();
        if (totalFen <= 0) {
            throw new IllegalArgumentException("没有可支付的发薪明细，请先添加人员");
        }

        // 5) 锁老板账户并校验余额
        if (order.getCreatorId() == null) {
            throw new IllegalArgumentException("该发薪单未关联老板账号，无法自动扣款，请先补充制单老板");
        }
        BossMerchantAccount account = lockBossAccount(order.getCreatorId());
        long available = account.getBalance() == null ? 0L : account.getBalance();
        if (available < totalFen) {
            throw new IllegalArgumentException(buildShortageMessage(account, totalFen, available, unpayable));
        }

        // 6) 同事务扣款 + 入账 + 回填
        String batchNo = "PB" + order.getId() + "_" + System.currentTimeMillis();
        account.setBalance(available - totalFen);
        accountRepository.save(account);
        for (PayrollDetail d : payable) {
            payService.pay(d, order);
        }
        for (PayrollDetail d : unpayable) {
            d.setStatus(PayrollConstants.DETAIL_FAILED);
        }
        detailRepository.saveAll(details);

        order.setStatus(PayrollConstants.ORDER_APPROVED);
        order.setReviewBy(reviewer);
        order.setReviewTime(new java.util.Date());
        order.setPayTime(new java.util.Date());
        order.setPayAccount(StringUtils.hasText(account.getAccountName()) ? account.getAccountName() : "老板账户");
        order.setPayBatchNo(batchNo);
        return orderRepository.save(order);
    }

    @Transactional
    public PayrollOrder reject(Long id, String reviewer) {
        PayrollOrder order = getOrThrow(id);
        order.setStatus(PayrollConstants.ORDER_REJECTED);
        order.setReviewBy(reviewer);
        order.setReviewTime(new java.util.Date());
        return orderRepository.save(order);
    }

    @Transactional
    public PayrollOrder withdraw(Long id) {
        PayrollOrder order = getOrThrow(id);
        order.setStatus(PayrollConstants.ORDER_WITHDRAWN);
        return orderRepository.save(order);
    }

    /** 发薪管理统计：项目总数 / 本月发薪总额 / 本月发薪笔数 / 待我审批。 */
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<PayrollOrder> all = orderRepository.findAll();
        // 后台建单只填项目名、不传 projectId，若仅按 projectId 去重会恒为 0
        long projectTotal = all.stream()
                .map(PayrollService::projectKey)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        LocalDate now = LocalDate.now();
        long monthAmount = all.stream()
                .filter(o -> PayrollConstants.ORDER_APPROVED.equals(o.getStatus()) && isThisMonth(o.getSubmitTime(), now))
                .mapToLong(o -> o.getAmount() == null ? 0L : o.getAmount()).sum();
        long monthCount = all.stream()
                .filter(o -> PayrollConstants.ORDER_APPROVED.equals(o.getStatus()) && isThisMonth(o.getSubmitTime(), now))
                .count();
        long pendingCount = all.stream().filter(o -> PayrollConstants.ORDER_PENDING.equals(o.getStatus())).count();

        result.put("projectTotal", projectTotal);
        result.put("monthAmount", monthAmount);
        result.put("monthCount", monthCount);
        result.put("pendingCount", pendingCount);
        return result;
    }

    /** 整单级校验：明细非空，且金额/人数与发薪单一致（防止创建后被绕过或手改）。 */
    private void validateOrderIntegrity(PayrollOrder order, List<PayrollDetail> details) {
        if (details == null || details.isEmpty()) {
            throw new IllegalArgumentException("发薪单没有人员明细，无法发薪");
        }
        long sum = details.stream().mapToLong(d -> d.getAmount() == null ? 0L : d.getAmount()).sum();
        long orderAmount = order.getAmount() == null ? 0L : order.getAmount();
        if (sum != orderAmount) {
            throw new IllegalArgumentException("发薪单金额与明细汇总不一致（单 " + orderAmount + " 分 / 明细 " + sum + " 分），拒绝审批");
        }
        int counted = order.getPeopleCount() == null ? 0 : order.getPeopleCount();
        if (counted != details.size()) {
            throw new IllegalArgumentException("发薪单人数与明细条数不一致（单 " + counted + " / 明细 " + details.size() + "），拒绝审批");
        }
    }

    /** 锁定老板账户：默认账户优先，无默认则取最新一张；都没有则拒绝（不自动开户）。 */
    private BossMerchantAccount lockBossAccount(Long bossId) {
        List<BossMerchantAccount> defaults = accountRepository.findByBossIdAndIsDefaultTrueForUpdate(bossId);
        if (!defaults.isEmpty()) {
            return defaults.get(0);
        }
        List<BossMerchantAccount> any = accountRepository.findByBossIdForUpdate(bossId);
        if (any.isEmpty()) {
            throw new IllegalArgumentException("该账号没有可用账户，无法自动扣款，请先开户并充值");
        }
        return any.get(0);
    }

    /** 余额不足提示：写明应付 / 可用 / 还差，并附带无收款账号的明细。 */
    private String buildShortageMessage(BossMerchantAccount account, long requiredFen, long availableFen,
                                        List<PayrollDetail> unpayable) {
        StringBuilder sb = new StringBuilder("审批失败：老板账户余额不足。本单应付 ¥")
                .append(yuan(requiredFen))
                .append("，当前可用 ¥").append(yuan(availableFen))
                .append("，还差 ¥").append(yuan(requiredFen - availableFen))
                .append("。请先充值再审批。");
        if (unpayable != null && !unpayable.isEmpty()) {
            sb.append("（另有 ").append(unpayable.size()).append(" 笔明细无收款账号，将被标记为失败：");
            sb.append(unpayable.stream()
                    .map(d -> (StringUtils.hasText(d.getName()) ? d.getName() : "未填写姓名")
                            + (StringUtils.hasText(d.getPhone()) ? " " + d.getPhone() : ""))
                    .collect(java.util.stream.Collectors.joining("、")));
            sb.append("）");
        }
        return sb.toString();
    }

    /** 分 → 元 展示串。 */
    private String yuan(long fen) {
        return java.math.BigDecimal.valueOf(fen, 2).toPlainString();
    }

    /** 项目去重键：优先用 projectId，缺省回退 projectName。两者都为空表示未关联项目。 */
    private static String projectKey(PayrollOrder order) {
        if (order.getProjectId() != null) {
            return "id:" + order.getProjectId();
        }
        String name = order.getProjectName();
        return (name == null || name.isBlank()) ? null : "name:" + name.trim();
    }

    private boolean isThisMonth(java.util.Date date, LocalDate now) {
        if (date == null) {
            return false;
        }
        LocalDate d = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return d.getYear() == now.getYear() && d.getMonth() == now.getMonth();
    }
}
