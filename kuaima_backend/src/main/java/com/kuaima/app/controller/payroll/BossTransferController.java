package com.kuaima.app.controller.payroll;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.payroll.constant.PayrollConstants;
import com.kuaima.app.domain.payroll.entity.PayrollDetail;
import com.kuaima.app.domain.payroll.entity.PayrollOrder;
import com.kuaima.app.domain.payroll.repository.PayrollDetailRepository;
import com.kuaima.app.domain.payroll.repository.PayrollOrderRepository;
import com.kuaima.app.domain.project.entity.Project;
import com.kuaima.app.domain.project.repository.ProjectRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 老板端「转账记录」模块：转账记录列表/统计、转账明细、明细汇总与导出。
 * 转账数据来源于已审批通过（approved）的发薪单及其明细。
 * 金额单位统一为「分」，前端展示时除 100 转元。
 */
@RestController
@RequestMapping("/boss/transfers")
@Tag(name = "老板-转账记录", description = "转账记录列表与统计、转账明细、明细汇总、导出")
@RequiredArgsConstructor
public class BossTransferController {

    private final PayrollOrderRepository orderRepository;
    private final PayrollDetailRepository detailRepository;
    private final ProjectRepository projectRepository;

    // ==================== 转账记录（发薪单维度） ====================

    @Operation(summary = "转账记录列表与统计", description = "已审批通过的发薪单列表（按支付日期倒序），附带转账统计（笔数/人数/金额）；projectName 按项目名模糊筛选")
    @GetMapping
    public Result<Map<String, Object>> listRecords(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String type,
                                                   @RequestParam(required = false) Long projectId,
                                                   @RequestParam(required = false) String projectName,
                                                   @RequestParam(required = false) String creator,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                   Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> orders = filterOrders(bossId, keyword, type, projectId, projectName, creator,
                startDate, endDate);
        orders.sort((a, b) -> {
            Date ta = a.getPayTime() != null ? a.getPayTime() : a.getReviewTime();
            Date tb = b.getPayTime() != null ? b.getPayTime() : b.getReviewTime();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        long count = orders.size();
        long people = orders.stream().mapToLong(o -> o.getPeopleCount() == null ? 0 : o.getPeopleCount()).sum();
        long amount = orders.stream().mapToLong(o -> o.getAmount() == null ? 0L : o.getAmount()).sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", orders);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", count);
        stats.put("people", people);
        stats.put("amount", amount);
        data.put("stats", stats);
        data.put("range", rangeText(orders));
        return Result.success(data);
    }

    // ==================== 转账明细（人员维度） ====================

    @Operation(summary = "转账明细列表", description = "已审批发薪单下的人员转账明细，按类型/时间筛选，附成功/失败统计")
    @GetMapping("/details")
    public Result<Map<String, Object>> listDetails(@RequestParam(required = false) String type,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                   Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> orders = filterOrders(bossId, null, type, null, null, null, startDate, endDate);
        List<Long> orderIds = orders.stream().map(PayrollOrder::getId).toList();
        List<PayrollDetail> details = orderIds.isEmpty()
                ? List.of()
                : detailRepository.findByPayrollIdIn(orderIds);
        details = new ArrayList<>(details);
        details.sort((a, b) -> {
            if (a.getId() == null || b.getId() == null) return 0;
            return b.getId().compareTo(a.getId());
        });

        long success = details.stream().filter(d -> PayrollConstants.DETAIL_SUCCESS.equals(d.getStatus())).count();
        long failed = details.stream().filter(d -> PayrollConstants.DETAIL_FAILED.equals(d.getStatus())).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("details", details);
        data.put("total", details.size());
        data.put("successCount", success);
        data.put("failedCount", failed);
        return Result.success(data);
    }

    // ==================== 明细汇总 ====================

    @Operation(summary = "转账明细汇总", description = "按周期汇总：总额/笔数/覆盖人数/涉及项目，以及项目维度汇总（金额、笔数、人数、成功率）")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                               Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> orders = filterOrders(bossId, null, null, null, null, null, startDate, endDate);

        long totalAmount = 0L;
        long peopleSum = 0;
        Map<Long, List<PayrollOrder>> byProject = new LinkedHashMap<>();
        for (PayrollOrder order : orders) {
            totalAmount += order.getAmount() == null ? 0L : order.getAmount();
            peopleSum += order.getPeopleCount() == null ? 0 : order.getPeopleCount();
            byProject.computeIfAbsent(order.getProjectId(), k -> new ArrayList<>()).add(order);
        }

        List<PayrollDetail> allDetails = detailRepository.findByPayrollIdIn(
                orders.stream().map(PayrollOrder::getId).toList());

        long avgPerPeople = peopleSum == 0 ? 0L : totalAmount / peopleSum;

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalAmount", totalAmount);
        overview.put("totalCount", orders.size());
        overview.put("successCount", allDetails.stream().filter(d -> PayrollConstants.DETAIL_SUCCESS.equals(d.getStatus())).count());
        overview.put("failedCount", allDetails.stream().filter(d -> PayrollConstants.DETAIL_FAILED.equals(d.getStatus())).count());
        overview.put("peopleCount", peopleSum);
        overview.put("avgPerPeople", avgPerPeople);
        overview.put("projectCount", byProject.size());
        overview.put("activeProjects", byProject.keySet().stream()
                .map(projectRepository::findById)
                .filter(p -> p.isPresent() && "active".equals(p.get().getStatus()))
                .count());

        List<Map<String, Object>> projects = new ArrayList<>();
        for (Map.Entry<Long, List<PayrollOrder>> entry : byProject.entrySet()) {
            List<PayrollOrder> projectOrders = entry.getValue();
            long projectAmount = projectOrders.stream().mapToLong(o -> o.getAmount() == null ? 0L : o.getAmount()).sum();
            long projectPeople = projectOrders.stream().mapToLong(o -> o.getPeopleCount() == null ? 0 : o.getPeopleCount()).sum();
            List<Long> ids = projectOrders.stream().map(PayrollOrder::getId).toList();
            List<PayrollDetail> projectDetails = detailRepository.findByPayrollIdIn(ids);
            long projectSuccess = projectDetails.stream().filter(d -> PayrollConstants.DETAIL_SUCCESS.equals(d.getStatus())).count();
            int successRate = projectDetails.isEmpty() ? 100 : (int) (projectSuccess * 100 / projectDetails.size());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("projectId", entry.getKey());
            item.put("projectName", projectOrders.get(0).getProjectName());
            Project project = entry.getKey() == null ? null
                    : projectRepository.findById(entry.getKey()).orElse(null);
            item.put("projectDesc", project != null && "active".equals(project.getStatus())
                    ? "进行中" : "已完成");
            item.put("totalAmount", projectAmount);
            item.put("orderCount", projectOrders.size());
            item.put("peopleCount", projectPeople);
            item.put("successRate", successRate);
            item.put("percent", totalAmount == 0 ? 0 : (int) (projectAmount * 100 / totalAmount));
            projects.add(item);
        }
        projects.sort((a, b) -> Long.compare((Long) b.get("totalAmount"), (Long) a.get("totalAmount")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", overview);
        data.put("projects", projects);
        data.put("range", rangeText(orders));
        return Result.success(data);
    }

    // ==================== 导出（原型占位） ====================

    @Operation(summary = "导出转账记录", description = "按筛选条件导出转账记录，返回导出任务提示（原型占位）")
    @PostMapping("/export")
    public Result<Map<String, Object>> exportRecords(@RequestBody(required = false) Map<String, Object> body,
                                                     Authentication authentication) {
        requireBossId(authentication);
        return Result.success(exportResult("转账记录"));
    }

    @Operation(summary = "导出转账明细", description = "按筛选条件导出转账明细，返回导出任务提示（原型占位）")
    @PostMapping("/details/export")
    public Result<Map<String, Object>> exportDetails(@RequestBody(required = false) Map<String, Object> body,
                                                     Authentication authentication) {
        requireBossId(authentication);
        return Result.success(exportResult("转账明细"));
    }

    @Operation(summary = "导出转账汇总报告", description = "按周期导出汇总报告，返回导出任务提示（原型占位）")
    @PostMapping("/summary/export")
    public Result<Map<String, Object>> exportSummary(@RequestBody(required = false) Map<String, Object> body,
                                                     Authentication authentication) {
        requireBossId(authentication);
        return Result.success(exportResult("汇总报告"));
    }

    // ==================== 私有方法 ====================

    /** 按当前老板 + 筛选条件过滤已审批通过的发薪单（即转账记录）。 */
    private List<PayrollOrder> filterOrders(Long bossId, String keyword, String type, Long projectId,
                                            String projectName, String creator,
                                            LocalDate startDate, LocalDate endDate) {
        List<PayrollOrder> orders = orderRepository.findByCreatorIdAndStatusOrderByIdDesc(
                bossId, PayrollConstants.ORDER_APPROVED);
        Date start = startDate == null ? null
                : Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date end = endDate == null ? null
                : Date.from(endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String pn = StringUtils.hasText(projectName) ? projectName.trim() : null;
        String cr = StringUtils.hasText(creator) ? creator.trim() : null;

        List<PayrollOrder> filtered = new ArrayList<>();
        for (PayrollOrder order : orders) {
            if (kw != null && (order.getTitle() == null || !order.getTitle().contains(kw))) {
                continue;
            }
            if (StringUtils.hasText(type) && !type.equals(order.getType())) {
                continue;
            }
            if (projectId != null && !projectId.equals(order.getProjectId())) {
                continue;
            }
            if (pn != null && (order.getProjectName() == null || !order.getProjectName().contains(pn))) {
                continue;
            }
            if (cr != null && (order.getCreator() == null || !order.getCreator().contains(cr))) {
                continue;
            }
            Date payTime = order.getPayTime() != null ? order.getPayTime() : order.getReviewTime();
            if (start != null && (payTime == null || payTime.before(start))) {
                continue;
            }
            if (end != null && (payTime == null || !payTime.before(end))) {
                continue;
            }
            filtered.add(order);
        }
        return filtered;
    }

    private String rangeText(List<PayrollOrder> orders) {
        LocalDate min = null;
        LocalDate max = null;
        for (PayrollOrder order : orders) {
            Date t = order.getPayTime() != null ? order.getPayTime() : order.getReviewTime();
            if (t == null) continue;
            LocalDate d = t.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (min == null || d.isBefore(min)) min = d;
            if (max == null || d.isAfter(max)) max = d;
        }
        if (min == null) return "";
        return min + " ~ " + Objects.requireNonNull(max);
    }

    private Map<String, Object> exportResult(String what) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "processing");
        data.put("message", what + "导出任务已创建，请稍后到下载列表查看");
        return data;
    }

    private Long requireBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.BOSS.equals(loginUser.role()) && loginUser.id() != null) {
            return loginUser.id();
        }
        throw new ForbiddenBusinessException("当前登录账号不是有效的老板账号");
    }
}
