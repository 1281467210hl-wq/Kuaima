package com.kuaima.app.admin.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.admin.dto.AdminTransferQueryDto;
import com.kuaima.app.admin.repository.AdminBossRechargeOrderRepository;
import com.kuaima.app.admin.repository.AdminBossMerchantAccountRepository;
import com.kuaima.app.admin.repository.AdminPayrollDetailRepository;
import com.kuaima.app.admin.util.AdminMoneyUtils;
import com.kuaima.app.domain.boss.entity.BossBalanceRechargeOrder;
import com.kuaima.app.domain.boss.entity.BossMerchantAccount;
import com.kuaima.app.domain.payroll.constant.PayrollConstants;
import com.kuaima.app.domain.payroll.entity.PayrollDetail;
import com.kuaima.app.domain.payroll.entity.PayrollOrder;
import com.kuaima.app.domain.payroll.repository.PayrollOrderRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * 后台「闪电发薪-转账记录」查询服务：平台全局视角，去掉老板身份强绑，老板/项目改为可选筛选。
 * <p>数据来源两类，字段名与实体单位保持一致：
 * <ul>
 *   <li>出账（direction=out）：已审批通过（approved）的 payroll_order + payroll_detail，金额以「分」存储；
 *       转账状态由明细状态派生：有 pending → processing，否则有 failed → failed，否则有明细 → success，无明细 → processing</li>
 *   <li>入账（direction=in）：boss_balance_recharge_order 充值单，金额以「元」存储，出参统一换算为「分」</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminTransferService {

    public static final String DIRECTION_OUT = "out";
    public static final String DIRECTION_IN = "in";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_FAILED = "failed";

    /** 合并方向下单侧最大扫描条数：先各取 (page+1)*size 条再归并，保证分页顺序正确 */
    private static final int MERGE_SCAN_LIMIT = 2000;
    /** 汇总/明细状态聚合的单次扫描上限 */
    private static final int SUMMARY_SCAN_LIMIT = 20000;
    /** IN 条件分片大小，避免超长 SQL */
    private static final int AGGREGATE_CHUNK_SIZE = 500;
    /** 导出最大条数 */
    private static final int EXPORT_LIMIT = 10000;
    private static final Set<String> RECHARGE_PAID = Set.of("paid", "manual_confirmed");
    private static final Set<String> RECHARGE_PENDING = Set.of("pending", "manual_pending");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PayrollOrderRepository payrollOrderRepository;
    private final AdminPayrollDetailRepository payrollDetailRepository;
    private final AdminBossRechargeOrderRepository rechargeOrderRepository;
    private final AdminBossMerchantAccountRepository merchantAccountRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    // ==================== 列表 + 统计 ====================

    /** 转账记录列表：按方向/状态/关键词/老板/项目筛选并分页，同时返回筛选维度的统计。 */
    @Transactional(readOnly = true)
    public Map<String, Object> list(AdminTransferQueryDto query) {
        int page = Math.max(query.getPage(), 0);
        int size = normalizeSize(query.getSize());
        boolean needOut = needOut(query.getDirection());
        boolean needIn = needIn(query.getDirection());
        // 各源先取当前页所需前缀，再归并排序截取，避免跨源分页错位
        int prefix = (int) Math.min((long) (page + 1) * size, MERGE_SCAN_LIMIT);

        List<Map<String, Object>> rows = new ArrayList<>();
        if (needOut) {
            rows.addAll(outRows(query, prefix));
        }
        if (needIn) {
            rows.addAll(inRows(query, prefix));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Date) r.get("payTime"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        long total = (needOut ? countOut(query) : 0L) + (needIn ? countIn(query) : 0L);
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        List<Map<String, Object>> slice = new ArrayList<>(rows.subList(from, to));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", slice);
        data.put("stats", stats(query, needOut, needIn));
        data.put("direction", normalizeDirection(query.getDirection()));
        data.put("range", rangeText(rows));
        data.put("page", page);
        data.put("size", size);
        data.put("total", total);
        return data;
    }

    /** 单笔转账（发薪单）的人员明细。 */
    @Transactional(readOnly = true)
    public Map<String, Object> details(Long payrollOrderId) {
        PayrollOrder order = payrollOrderRepository.findById(payrollOrderId)
                .orElseThrow(() -> new EntityNotFoundException("转账记录不存在: " + payrollOrderId));
        List<PayrollDetail> details = payrollDetailRepository.findByPayrollIdOrderByIdAsc(payrollOrderId);

        Map<String, Object> head = new LinkedHashMap<>();
        head.put("id", order.getId());
        head.put("orderNo", order.getOrderNo());
        head.put("title", order.getTitle());
        head.put("company", order.getCompany());
        head.put("bossId", order.getCreatorId());
        head.put("bossName", order.getCreator());
        head.put("projectId", order.getProjectId());
        head.put("projectName", order.getProjectName());
        head.put("type", order.getType());
        head.put("payAccount", order.getPayAccount());
        head.put("amountFen", nvl(order.getAmount()));
        head.put("amountYuan", AdminMoneyUtils.fenToYuan(order.getAmount()));
        head.put("peopleCount", nvl(order.getPeopleCount()));
        head.put("payTime", order.getPayTime());
        head.put("status", derivedStatus(statusAggregate(details)));
        head.put("orderStatus", order.getStatus());

        List<Map<String, Object>> people = details.stream().map(this::detailRow).collect(Collectors.toList());
        StatusCount count = statusAggregate(details);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", head);
        data.put("details", people);
        data.put("total", people.size());
        data.put("successCount", count.success);
        data.put("processingCount", count.pending);
        data.put("failedCount", count.failed);
        return data;
    }

    /** 汇总：成功/处理中/失败笔数与金额（分），出账按人员明细与转账单两个维度，入账按充值单状态。 */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(AdminTransferQueryDto query) {
        boolean needOut = needOut(query.getDirection());
        boolean needIn = needIn(query.getDirection());

        Map<String, Object> data = new LinkedHashMap<>();
        if (needOut) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", outAggregate(query, null));
            out.put(STATUS_SUCCESS, outAggregate(query, STATUS_SUCCESS));
            out.put(STATUS_PROCESSING, outAggregate(query, STATUS_PROCESSING));
            out.put(STATUS_FAILED, outAggregate(query, STATUS_FAILED));
            out.put("detailStatus", outDetailStatusAggregates(query));
            data.put("out", out);
        }
        if (needIn) {
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("total", inAggregate(query, null));
            in.put(STATUS_SUCCESS, inAggregate(query, STATUS_SUCCESS));
            in.put(STATUS_PROCESSING, inAggregate(query, STATUS_PROCESSING));
            in.put(STATUS_FAILED, inAggregate(query, STATUS_FAILED));
            data.put("in", in);
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        long count = 0L;
        long amountFen = 0L;
        for (String key : List.of(STATUS_SUCCESS, STATUS_PROCESSING, STATUS_FAILED)) {
            long c = 0L;
            long a = 0L;
            for (String dir : List.of("out", "in")) {
                if (data.get(dir) instanceof Map<?, ?> source && source.get(key) instanceof Map<?, ?> bucket) {
                    c += toLong(bucket.get("count"));
                    a += toLong(bucket.get("amountFen"));
                }
            }
            count += c;
            amountFen += a;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", key);
            item.put("statusText", statusText(key));
            item.put("count", c);
            item.put("amountFen", a);
            item.put("amountYuan", AdminMoneyUtils.fenToYuan(a));
            merged.put(key, item);
        }
        merged.put("totalCount", count);
        merged.put("totalAmountFen", amountFen);
        merged.put("totalAmountYuan", AdminMoneyUtils.fenToYuan(amountFen));
        data.put("merged", merged);
        data.put("direction", normalizeDirection(query.getDirection()));
        return data;
    }

    // ==================== 导出 ====================

    /** 导出转账记录 CSV（含 UTF-8 BOM，Excel 直接打开不乱码）。 */
    @Transactional(readOnly = true)
    public byte[] exportCsv(AdminTransferQueryDto query) {
        boolean needOut = needOut(query.getDirection());
        boolean needIn = needIn(query.getDirection());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (needOut) {
            rows.addAll(outRows(query, EXPORT_LIMIT));
        }
        if (needIn) {
            rows.addAll(inRows(query, EXPORT_LIMIT));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Date) r.get("payTime"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        StringBuilder csv = new StringBuilder(
                "转账单号,方向,所属公司,老板,项目,类型,付款账号,收款方,收款账号,金额(元),转账人数,状态,转账时间,备注\n");
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (Map<String, Object> row : rows) {
            csv.append(cell(row.get("no"))).append(',')
                    .append(cell(row.get("directionText"))).append(',')
                    .append(cell(row.get("company"))).append(',')
                    .append(cell(row.get("bossName"))).append(',')
                    .append(cell(row.get("projectName"))).append(',')
                    .append(cell(row.get("typeText"))).append(',')
                    .append(cell(row.get("payAccount"))).append(',')
                    .append(cell(row.get("payee"))).append(',')
                    .append(cell(row.get("recvAccount"))).append(',')
                    .append(cell(row.get("amountYuan"))).append(',')
                    .append(cell(row.get("peopleCount"))).append(',')
                    .append(cell(row.get("statusText"))).append(',')
                    .append(cell(row.get("payTime") == null ? null : timeFormat.format((Date) row.get("payTime")))).append(',')
                    .append(cell(row.get("remark"))).append('\n');
        }
        return ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 出账（发薪转账） ====================

    private List<Map<String, Object>> outRows(AdminTransferQueryDto query, int limit) {
        List<PayrollOrder> orders = payrollOrderRepository
                .findAll(outSpec(query), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "payTime", "id")))
                .getContent();
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> ids = orders.stream().map(PayrollOrder::getId).filter(Objects::nonNull).toList();
        Map<Long, StatusCount> aggregates = outAggregates(ids);
        Map<Long, List<PayrollDetail>> pageDetails = payrollDetailRepository.findByPayrollIdIn(ids).stream()
                .collect(Collectors.groupingBy(PayrollDetail::getPayrollId));
        Map<Long, String> bossNames = bossNames(orders.stream().map(PayrollOrder::getCreatorId).collect(Collectors.toSet()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PayrollOrder order : orders) {
            StatusCount sc = aggregates.getOrDefault(order.getId(), StatusCount.empty());
            String status = derivedStatus(sc);
            if (!matchesStatus(query.getStatus(), status)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", order.getId());
            row.put("direction", DIRECTION_OUT);
            row.put("directionText", "出账(发薪)");
            row.put("bizType", "payroll_order");
            row.put("no", order.getOrderNo());
            row.put("title", order.getTitle());
            row.put("company", order.getCompany());
            row.put("bossId", order.getCreatorId());
            row.put("bossName", lookup(bossNames, order.getCreatorId(), order.getCreator()));
            row.put("projectId", order.getProjectId());
            row.put("projectName", order.getProjectName());
            row.put("type", order.getType());
            row.put("typeText", typeText(order.getType()));
            row.put("payAccount", order.getPayAccount());
            row.put("payee", payeeText(order, pageDetails.get(order.getId())));
            row.put("recvAccount", recvAccountText(order, pageDetails.get(order.getId())));
            row.put("amountFen", nvl(order.getAmount()));
            row.put("amountYuan", AdminMoneyUtils.fenToYuan(order.getAmount()));
            row.put("peopleCount", nvl(order.getPeopleCount()));
            row.put("status", status);
            row.put("statusText", statusText(status));
            row.put("successCount", sc.success);
            row.put("processingCount", sc.pending);
            row.put("failedCount", sc.failed);
            row.put("payTime", order.getPayTime());
            row.put("reviewTime", order.getReviewTime());
            row.put("remark", order.getRejectReason());
            rows.add(row);
        }
        return rows;
    }

    /** 明细状态聚合：payrollId → 各状态笔数。 */
    private Map<Long, StatusCount> outAggregates(List<Long> orderIds) {
        Map<Long, StatusCount> result = new LinkedHashMap<>();
        for (int i = 0; i < orderIds.size(); i += 500) {
            List<Long> chunk = orderIds.subList(i, Math.min(i + 500, orderIds.size()));
            for (AdminPayrollDetailRepository.PayrollStatusAggregate agg
                    : payrollDetailRepository.aggregateStatusByPayrollIds(chunk)) {
                StatusCount sc = result.computeIfAbsent(agg.getPayrollId(), k -> StatusCount.empty());
                long count = agg.getTotalCount() == null ? 0L : agg.getTotalCount();
                sc.add(agg.getStatus(), count);
            }
        }
        return result;
    }

    /** 人员维度状态汇总（金额单位：分）：先取命中转账单 id，再按明细状态聚合。 */
    private List<Map<String, Object>> outDetailStatusAggregates(AdminTransferQueryDto query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<PayrollOrder> root = idQuery.from(PayrollOrder.class);
        idQuery.select(root.get("id"));
        idQuery.where(outPredicates(root, idQuery, cb, query, query.getStatus()).toArray(new Predicate[0]));
        List<Long> ids = entityManager.createQuery(idQuery).setMaxResults(SUMMARY_SCAN_LIMIT).getResultList();

        Map<String, long[]> byStatus = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i += AGGREGATE_CHUNK_SIZE) {
            List<Long> chunk = ids.subList(i, Math.min(i + AGGREGATE_CHUNK_SIZE, ids.size()));
            for (AdminPayrollDetailRepository.PayrollStatusAggregate agg
                    : payrollDetailRepository.aggregateStatusByPayrollIds(chunk)) {
                String mapped = mapDetailStatus(agg.getStatus());
                long[] acc = byStatus.computeIfAbsent(mapped, k -> new long[2]);
                acc[0] += agg.getTotalCount() == null ? 0L : agg.getTotalCount();
                acc[1] += agg.getTotalAmount() == null ? 0L : agg.getTotalAmount();
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (String status : List.of(STATUS_SUCCESS, STATUS_PROCESSING, STATUS_FAILED)) {
            long[] acc = byStatus.getOrDefault(status, new long[2]);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status);
            item.put("statusText", statusText(status));
            item.put("count", acc[0]);
            item.put("amountFen", acc[1]);
            item.put("amountYuan", AdminMoneyUtils.fenToYuan(acc[1]));
            list.add(item);
        }
        return list;
    }

    /** 出账维度统计：笔数/金额（分）/人数。statusOverride 为 null 时沿用查询条件。 */
    private Map<String, Object> outAggregate(AdminTransferQueryDto query, String statusOverride) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<PayrollOrder> root = cq.from(PayrollOrder.class);
        cq.multiselect(cb.count(root), cb.sum(root.<Long>get("amount")), cb.sum(root.<Integer>get("peopleCount")));
        String status = statusOverride != null ? statusOverride : query.getStatus();
        cq.where(outPredicates(root, cq, cb, query, status).toArray(new Predicate[0]));
        Tuple tuple = entityManager.createQuery(cq).getSingleResult();
        return aggregateMap(toLong(tuple.get(0)), toLong(tuple.get(1)), toLong(tuple.get(2)));
    }

    private long countOut(AdminTransferQueryDto query) {
        // 派生状态过滤已落到 SQL，可直接用 count 得到准确总数
        return payrollOrderRepository.count(outSpec(query));
    }

    private Specification<PayrollOrder> outSpec(AdminTransferQueryDto query) {
        return (root, cq, cb) -> cb.and(outPredicates(root, cq, cb, query, query.getStatus()).toArray(new Predicate[0]));
    }

    private List<Predicate> outPredicates(Root<PayrollOrder> root, AbstractQuery<?> cq, CriteriaBuilder cb,
                                         AdminTransferQueryDto query, String status) {
        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.equal(root.get("status"), PayrollConstants.ORDER_APPROVED));
        if (query.getBossId() != null) {
            ps.add(cb.equal(root.get("creatorId"), query.getBossId()));
        }
        if (query.getProjectId() != null) {
            ps.add(cb.equal(root.get("projectId"), query.getProjectId()));
        }
        if (StringUtils.hasText(query.getType())) {
            ps.add(cb.equal(root.get("type"), query.getType().trim()));
        }
        String keyword = trim(query.getKeyword());
        if (keyword != null) {
            String like = "%" + keyword + "%";
            ps.add(cb.or(
                    cb.like(root.get("orderNo"), like),
                    cb.like(root.get("title"), like),
                    cb.like(root.get("company"), like),
                    cb.like(root.get("projectName"), like),
                    cb.like(root.get("payAccount"), like),
                    payeeExists(root, cq, cb, like)));
        }
        String bossKeyword = trim(query.getBossKeyword());
        if (bossKeyword != null) {
            List<Long> bossIds = searchBossIds(bossKeyword);
            ps.add(bossIds.isEmpty() ? cb.disjunction() : root.get("creatorId").in(bossIds));
        }
        addTimeRange(ps, root, cb, query, "payTime", "reviewTime");
        addStatusPredicate(ps, root, cq, cb, status);
        return ps;
    }

    /** 转账单派生状态过滤：用明细子查询计数落到 SQL，保证分页与统计一致。 */
    private void addStatusPredicate(List<Predicate> ps, Root<PayrollOrder> root, AbstractQuery<?> cq,
                                    CriteriaBuilder cb, String status) {
        String normalized = normalizeStatus(status);
        if (normalized == null) {
            return;
        }
        Expression<Long> pending = detailCount(root, cq, cb, PayrollConstants.DETAIL_PENDING);
        Expression<Long> failed = detailCount(root, cq, cb, PayrollConstants.DETAIL_FAILED);
        Expression<Long> total = detailCount(root, cq, cb, null);
        switch (normalized) {
            case STATUS_SUCCESS -> ps.add(cb.and(cb.equal(pending, 0L), cb.equal(failed, 0L),
                    cb.greaterThan(total, 0L)));
            case STATUS_FAILED -> ps.add(cb.and(cb.equal(pending, 0L), cb.greaterThan(failed, 0L)));
            default -> ps.add(cb.or(cb.greaterThan(pending, 0L), cb.equal(total, 0L)));
        }
    }

    private Expression<Long> detailCount(Root<PayrollOrder> root, AbstractQuery<?> cq, CriteriaBuilder cb,
                                        String detailStatus) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<PayrollDetail> d = sub.from(PayrollDetail.class);
        sub.select(cb.count(d));
        List<Predicate> conds = new ArrayList<>();
        conds.add(cb.equal(d.get("payrollId"), root.get("id")));
        if (detailStatus != null) {
            conds.add(cb.equal(d.get("status"), detailStatus));
        }
        sub.where(conds.toArray(new Predicate[0]));
        return sub;
    }

    /** 关键词命中收款人姓名/手机号（明细维度）。 */
    private Predicate payeeExists(Root<PayrollOrder> root, AbstractQuery<?> cq, CriteriaBuilder cb, String like) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<PayrollDetail> d = sub.from(PayrollDetail.class);
        sub.select(d.get("id"));
        sub.where(cb.equal(d.get("payrollId"), root.get("id")),
                cb.or(cb.like(d.get("name"), like), cb.like(d.get("phone"), like)));
        return cb.exists(sub);
    }

    // ==================== 入账（商户账户充值） ====================

    private List<Map<String, Object>> inRows(AdminTransferQueryDto query, int limit) {
        List<BossBalanceRechargeOrder> orders = rechargeOrderRepository
                .findAll(inSpec(query), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "payTime", "id")))
                .getContent();
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> accountIds = orders.stream().map(BossBalanceRechargeOrder::getAccountId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BossMerchantAccount> accounts = accountIds.isEmpty() ? Map.of()
                : merchantAccountRepository.findAllById(accountIds).stream()
                        .collect(Collectors.toMap(BossMerchantAccount::getId, a -> a, (x, y) -> x));
        Map<Long, String> bossNames = bossNames(orders.stream().map(BossBalanceRechargeOrder::getBossId)
                .collect(Collectors.toSet()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (BossBalanceRechargeOrder order : orders) {
            String status = mapRechargeStatus(order.getStatus());
            if (!matchesStatus(query.getStatus(), status)) {
                continue;
            }
            BossMerchantAccount account = order.getAccountId() == null ? null
                    : accounts.get(order.getAccountId());
            long amountFen = AdminMoneyUtils.yuanToFen(order.getAmount());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", order.getId());
            row.put("direction", DIRECTION_IN);
            row.put("directionText", "入账(充值)");
            row.put("bizType", "recharge_order");
            row.put("no", order.getOrderNo());
            row.put("title", "商户账户充值");
            row.put("company", order.getCompanyName());
            row.put("bossId", order.getBossId());
            row.put("bossName", StringUtils.hasText(order.getBossName()) ? order.getBossName()
                    : lookup(bossNames, order.getBossId(), null));
            row.put("projectId", null);
            row.put("projectName", null);
            row.put("type", "recharge");
            row.put("typeText", "充值");
            row.put("payAccount", payMethodText(order.getPayMethod()));
            row.put("payee", account != null ? account.getAccountName() : order.getCompanyName());
            row.put("recvAccount", account != null ? account.getMerchantNo() : null);
            row.put("amountFen", amountFen);
            row.put("amountYuan", order.getAmount() == null ? "0.00" : order.getAmount().toPlainString());
            row.put("peopleCount", 1);
            row.put("status", status);
            row.put("statusText", statusText(status));
            row.put("successCount", null);
            row.put("processingCount", null);
            row.put("failedCount", null);
            row.put("payTime", order.getPayTime());
            row.put("reviewTime", order.getTimestamp());
            row.put("remark", order.getRemark());
            row.put("transactionId", order.getTransactionId());
            rows.add(row);
        }
        return rows;
    }

    private Specification<BossBalanceRechargeOrder> inSpec(AdminTransferQueryDto query) {
        return (root, cq, cb) -> cb.and(
                inPredicates(root, cb, query, normalizeStatus(query.getStatus())).toArray(new Predicate[0]));
    }

    private List<Predicate> inPredicates(Root<BossBalanceRechargeOrder> root, CriteriaBuilder cb,
                                         AdminTransferQueryDto query, String status) {
        List<Predicate> ps = new ArrayList<>();
        // 指定项目筛选时入账记录不适用（充值单无项目归属）
        if (query.getProjectId() != null) {
            ps.add(cb.disjunction());
            return ps;
        }
        if (query.getBossId() != null) {
            ps.add(cb.equal(root.get("bossId"), query.getBossId()));
        }
        String bossKeyword = trim(query.getBossKeyword());
        if (bossKeyword != null) {
            List<Long> bossIds = searchBossIds(bossKeyword);
            ps.add(bossIds.isEmpty() ? cb.disjunction() : root.get("bossId").in(bossIds));
        }
        String keyword = trim(query.getKeyword());
        if (keyword != null) {
            String like = "%" + keyword + "%";
            ps.add(cb.or(cb.like(root.get("orderNo"), like), cb.like(root.get("companyName"), like),
                    cb.like(root.get("bossName"), like), cb.like(root.get("transactionId"), like)));
        }
        String payMethod = trim(query.getPayMethod());
        if (payMethod != null) {
            ps.add(cb.equal(root.get("payMethod"), payMethod));
        }
        if (status != null) {
            ps.add(root.get("status").in(rechargeStatuses(status)));
        }
        addTimeRange(ps, root, cb, query, "payTime", "timestamp");
        return ps;
    }

    /** 入账状态 → 充值单原始状态集合。 */
    private Set<String> rechargeStatuses(String status) {
        return switch (status) {
            case STATUS_SUCCESS -> RECHARGE_PAID;
            case STATUS_PROCESSING -> RECHARGE_PENDING;
            default -> Set.of("failed", "canceled");
        };
    }

    private Map<String, Object> inAggregate(AdminTransferQueryDto query, String statusOverride) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<BossBalanceRechargeOrder> root = cq.from(BossBalanceRechargeOrder.class);
        cq.multiselect(cb.count(root), cb.sum(root.<BigDecimal>get("amount")));
        String status = statusOverride != null ? statusOverride : normalizeStatus(query.getStatus());
        List<Predicate> ps = inPredicates(root, cb, query, status);
        cq.where(ps.toArray(new Predicate[0]));
        Tuple tuple = entityManager.createQuery(cq).getSingleResult();
        // 充值单金额以「元」存储，汇总时换算为「分」与其他来源保持同一单位
        BigDecimal amountYuan = tuple.get(1) == null ? BigDecimal.ZERO : tuple.get(1, BigDecimal.class);
        return aggregateMap(toLong(tuple.get(0)), AdminMoneyUtils.yuanToFen(amountYuan), 0L);
    }

    private long countIn(AdminTransferQueryDto query) {
        return rechargeOrderRepository.count(inSpec(query));
    }

    private Map<String, Object> stats(AdminTransferQueryDto query, boolean needOut, boolean needIn) {
        Map<String, Object> stats = new LinkedHashMap<>();
        long count = 0L;
        long amountFen = 0L;
        long people = 0L;
        if (needOut) {
            Map<String, Object> out = outAggregate(query, null);
            stats.put("out", out);
            count += toLong(out.get("count"));
            amountFen += toLong(out.get("amountFen"));
            people += toLong(out.get("peopleCount"));
        }
        if (needIn) {
            Map<String, Object> in = inAggregate(query, null);
            stats.put("in", in);
            count += toLong(in.get("count"));
            amountFen += toLong(in.get("amountFen"));
            people += toLong(in.get("peopleCount"));
        }
        stats.put("count", count);
        stats.put("amountFen", amountFen);
        stats.put("amountYuan", AdminMoneyUtils.fenToYuan(amountFen));
        stats.put("peopleCount", people);
        return stats;
    }

    // ==================== 通用工具 ====================

    private void addTimeRange(List<Predicate> ps, Root<?> root, CriteriaBuilder cb, AdminTransferQueryDto query,
                              String timeField, String fallbackField) {
        if (query.getStartDate() == null && query.getEndDate() == null) {
            return;
        }
        Expression<Date> time = cb.coalesce(root.<Date>get(timeField), root.<Date>get(fallbackField));
        if (query.getStartDate() != null) {
            ps.add(cb.greaterThanOrEqualTo(time, Date.from(query.getStartDate().atStartOfDay(ZONE).toInstant())));
        }
        if (query.getEndDate() != null) {
            ps.add(cb.lessThan(time, Date.from(query.getEndDate().plusDays(1).atStartOfDay(ZONE).toInstant())));
        }
    }

    private Map<String, Object> aggregateMap(long count, long amountFen, long peopleCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("count", count);
        map.put("amountFen", amountFen);
        map.put("amountYuan", AdminMoneyUtils.fenToYuan(amountFen));
        map.put("peopleCount", peopleCount);
        return map;
    }

    private Map<String, Object> detailRow(PayrollDetail d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("payrollId", d.getPayrollId());
        row.put("userId", d.getUserId());
        row.put("name", d.getName());
        row.put("phone", d.getPhone());
        row.put("job", d.getJob());
        row.put("attendDays", d.getAttendDays());
        row.put("dailyWageFen", nvl(d.getDailyWage()));
        row.put("dailyWageYuan", AdminMoneyUtils.fenToYuan(d.getDailyWage()));
        row.put("amountFen", nvl(d.getAmount()));
        row.put("amountYuan", AdminMoneyUtils.fenToYuan(d.getAmount()));
        row.put("status", mapDetailStatus(d.getStatus()));
        row.put("statusText", statusText(mapDetailStatus(d.getStatus())));
        row.put("orderNo", d.getOrderNo());
        row.put("payAccount", d.getAccount());
        row.put("payTime", d.getPayTime());
        row.put("remark", d.getRemark());
        return row;
    }

    private StatusCount statusAggregate(List<PayrollDetail> details) {
        StatusCount sc = StatusCount.empty();
        for (PayrollDetail d : details) {
            sc.add(d.getStatus(), 1L);
        }
        return sc;
    }

    /** 转账单派生状态：有处理中→processing，否则有失败→failed，全部成功→success，无明细→processing。 */
    private String derivedStatus(StatusCount sc) {
        if (sc.pending > 0 || sc.total == 0) {
            return STATUS_PROCESSING;
        }
        return sc.failed > 0 ? STATUS_FAILED : STATUS_SUCCESS;
    }

    private String payeeText(PayrollOrder order, List<PayrollDetail> details) {
        int people = order.getPeopleCount() == null ? 0 : order.getPeopleCount();
        if (details == null || details.isEmpty()) {
            return people <= 0 ? "—" : people + " 名零工";
        }
        String first = details.get(0).getName();
        return people <= 1 ? first : first + " 等 " + people + " 人";
    }

    private String recvAccountText(PayrollOrder order, List<PayrollDetail> details) {
        int people = order.getPeopleCount() == null ? 0 : order.getPeopleCount();
        if (people > 1 || details == null || details.isEmpty()) {
            return "多人收款（见转账明细）";
        }
        return maskPhone(details.get(0).getPhone());
    }

    private Map<Long, String> bossNames(Set<Long> bossIds) {
        List<Long> ids = bossIds.stream().filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            map.put(user.getId(), displayName(user));
        }
        return map;
    }

    /** null 安全取值：Map.of() 等不可变 Map 不允许 null 键查询。 */
    private String lookup(Map<Long, String> map, Long key, String fallback) {
        if (key == null) {
            return fallback;
        }
        String value = map.get(key);
        return value == null ? fallback : value;
    }

    /** 老板关键词 → 老板 user.id 集合（DB 层按账号/昵称/手机号/企业名匹配，最多 200 个）。 */
    private List<Long> searchBossIds(String keyword) {
        return userRepository.searchByRole(UserRole.BOSS, null, keyword, PageRequest.of(0, 200))
                .map(User::getId)
                .getContent();
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

    private String normalizeDirection(String direction) {
        String value = trim(direction);
        if (DIRECTION_OUT.equals(value) || DIRECTION_IN.equals(value)) {
            return value;
        }
        return "all";
    }

    private boolean needOut(String direction) {
        String value = normalizeDirection(direction);
        return !"all".equals(value) ? DIRECTION_OUT.equals(value) : true;
    }

    private boolean needIn(String direction) {
        String value = normalizeDirection(direction);
        return !"all".equals(value) ? DIRECTION_IN.equals(value) : true;
    }

    private String normalizeStatus(String status) {
        String value = trim(status);
        if (value == null || "all".equals(value)) {
            return null;
        }
        return switch (value) {
            case STATUS_SUCCESS, STATUS_PROCESSING, STATUS_FAILED -> value;
            default -> null;
        };
    }

    private boolean matchesStatus(String filter, String actual) {
        String normalized = normalizeStatus(filter);
        return normalized == null || normalized.equals(actual);
    }

    private String mapDetailStatus(String status) {
        String value = trim(status);
        if (PayrollConstants.DETAIL_SUCCESS.equals(value)) {
            return STATUS_SUCCESS;
        }
        if (PayrollConstants.DETAIL_FAILED.equals(value)) {
            return STATUS_FAILED;
        }
        return STATUS_PROCESSING;
    }

    private String mapRechargeStatus(String status) {
        String value = trim(status);
        if (value != null && RECHARGE_PAID.contains(value)) {
            return STATUS_SUCCESS;
        }
        if (value != null && RECHARGE_PENDING.contains(value)) {
            return STATUS_PROCESSING;
        }
        return STATUS_FAILED;
    }

    private String statusText(String status) {
        return switch (String.valueOf(status)) {
            case STATUS_SUCCESS -> "转账成功";
            case STATUS_FAILED -> "转账失败";
            default -> "处理中";
        };
    }

    private String typeText(String type) {
        return switch (String.valueOf(type)) {
            case PayrollConstants.TYPE_ADVANCE -> "预支";
            case PayrollConstants.TYPE_OTHER -> "其他";
            case "recharge" -> "充值";
            default -> "工资";
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

    /** 筛选结果的时间范围文本，如 2026-09-01 ~ 2026-09-30。 */
    private String rangeText(List<Map<String, Object>> rows) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date min = null;
        Date max = null;
        for (Map<String, Object> row : rows) {
            Date time = (Date) row.get("payTime");
            if (time == null) {
                continue;
            }
            if (min == null || time.before(min)) {
                min = time;
            }
            if (max == null || time.after(max)) {
                max = time;
            }
        }
        return min == null ? "" : format.format(min) + " ~ " + format.format(max);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String cell(Object value) {
        String text = value == null ? "" : String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizeSize(int size) {
        return size <= 0 ? 10 : Math.min(size, 200);
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    /** 明细状态计数累加器。 */
    private static final class StatusCount {
        private long total;
        private long success;
        private long pending;
        private long failed;

        static StatusCount empty() {
            return new StatusCount();
        }

        void add(String detailStatus, long count) {
            total += count;
            switch (String.valueOf(detailStatus)) {
                case PayrollConstants.DETAIL_SUCCESS -> success += count;
                case PayrollConstants.DETAIL_FAILED -> failed += count;
                default -> pending += count;
            }
        }
    }
}
