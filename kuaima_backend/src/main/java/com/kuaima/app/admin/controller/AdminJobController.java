package com.kuaima.app.admin.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.kuaima.app.admin.dto.BossOrderView;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.service.BossOrderService;
import com.kuaima.app.domain.insurance.entity.Insurance;
import com.kuaima.app.domain.insurance.repository.InsuranceRepository;
import com.kuaima.app.domain.jobcategory.entity.JobCategory;
import com.kuaima.app.domain.jobcategory.repository.JobCategoryRepository;
import com.kuaima.app.domain.jobcategory.repository.JobIndustryRepository;
import com.kuaima.app.domain.jobcategory.entity.JobIndustry;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.admin.repository.ReportRepository;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.security.model.LoginUser;

/**
 * 后台招工管理 + 招工审核
 */
@RestController
@RequestMapping("/admin/jobs")
@Tag(name = "后台-招工审核", description = "招工订单审核与管理")
public class AdminJobController {

    private final BossOrderRespository orderRepository;
    private final BossOrderService bossOrderService;
    private final UserRepository userRepository;
    private final BaseOrderItemRespository orderItemRepository;
    private final JobCategoryRepository jobCategoryRepository;
    private final JobIndustryRepository jobIndustryRepository;
    private final ReportRepository reportRepository;
    private final InsuranceRepository insuranceRepository;

    public AdminJobController(BossOrderRespository orderRepository,
                              BossOrderService bossOrderService,
                              UserRepository userRepository,
                              BaseOrderItemRespository orderItemRepository,
                              JobCategoryRepository jobCategoryRepository,
                              JobIndustryRepository jobIndustryRepository,
                              ReportRepository reportRepository,
                              InsuranceRepository insuranceRepository) {
        this.orderRepository = orderRepository;
        this.bossOrderService = bossOrderService;
        this.userRepository = userRepository;
        this.orderItemRepository = orderItemRepository;
        this.jobCategoryRepository = jobCategoryRepository;
        this.jobIndustryRepository = jobIndustryRepository;
        this.reportRepository = reportRepository;
        this.insuranceRepository = insuranceRepository;
    }

    /** 招工列表（全部状态），批量填充雇主名称 */
    @Operation(summary = "招工列表分页", description = "按 id 倒序分页。参数：type(招工类型)、status(订单状态)、title(标题模糊)、page、size。返回 BossOrderView，扩展 employerName（雇主企业名，为空取昵称，再取用户名）")
    @GetMapping
    public Result<Page<BossOrderView>> list(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String title,
                                            @RequestParam(required = false) LocalDate startDate,
                                            @RequestParam(required = false) LocalDate endDate,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<BossOrder> orders = orderRepository.search(type, status, title, startDate, endDate, pageable);

        // 批量查询雇主名称（createBy = 雇主 userId）
        Set<Long> employerIds = orders.stream()
                .map(BossOrder::getCreateBy)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> employerNames = new HashMap<>();
        if (!employerIds.isEmpty()) {
            userRepository.findAllById(employerIds).forEach(u -> {
                String name = (u.getCompanyName() != null && !u.getCompanyName().isBlank())
                        ? u.getCompanyName()
                        : (u.getNickname() != null ? u.getNickname() : u.getUsername());
                employerNames.put(u.getId(), name);
            });
        }

        // 批量查询报名人数
        List<Long> orderIds = orders.stream().map(BossOrder::getId).toList();
        Map<Long, Long> applyCounts = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<String> validStatuses = List.of("已报名", "已录用", "已到岗", "待结算", "已完成");
            orderItemRepository.countByOrderIdsAndStatuses(orderIds, validStatuses)
                    .forEach(row -> applyCounts.put((Long) row[0], (Long) row[1]));
        }

        // 批量查询工种中文名称
        Set<Long> categoryIds = orders.stream()
                .map(BossOrder::getJobCategoryId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNames = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            jobCategoryRepository.findAllById(categoryIds).forEach(c ->
                    categoryNames.put(c.getId(), c.getName()));
        }
        Set<Long> industryIds = orders.stream().map(BossOrder::getIndustryId).filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Map<Long, String> industryNames = new HashMap<>();
        if (!industryIds.isEmpty()) jobIndustryRepository.findAllById(industryIds).forEach(i -> industryNames.put(i.getId(), i.getName()));

        Page<BossOrderView> views = orders.map(order -> new BossOrderView(
                order.getId(),
                order.getOrderTitle(),
                order.getOrderContent(),
                null,
                order.getOrderNum(),
                order.getOrderStatus(),
                order.getType(),
                order.getPostion(),
                order.getDuration(),
                order.getSalary(),
                order.getAddress(),
                order.getLongitude(),
                order.getLatitude(),
                order.getTags(),
                order.getTrialDuration(),
                order.getTimestamp(),
                order.getStartTime(),
                order.getEndTime(),
                order.getCreateBy() != null ? employerNames.getOrDefault(order.getCreateBy(), "未知雇主") : "未知雇主",
                null,
                applyCounts.getOrDefault(order.getId(), 0L),
                order.getAuditBy(),
                order.getAuditTime(),
                formatIndustryJob(industryNames.get(order.getIndustryId()), order.getJobCategoryId() != null ? categoryNames.get(order.getJobCategoryId()) : null)
        ));

        return Result.success(views, page, orders.getTotalElements());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String title,
                                         @RequestParam(required = false) LocalDate startDate,
                                         @RequestParam(required = false) LocalDate endDate) {
        Page<BossOrder> orders = orderRepository.search(type, status, title, startDate, endDate,
                PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "id")));

        Set<Long> employerIds = orders.stream().map(BossOrder::getCreateBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> employerNames = new HashMap<>();
        userRepository.findAllById(employerIds).forEach(user -> {
            String name = user.getCompanyName();
            if (name == null || name.isBlank()) name = user.getNickname();
            if (name == null || name.isBlank()) name = user.getUsername();
            employerNames.put(user.getId(), name);
        });

        Set<Long> categoryIds = orders.stream().map(BossOrder::getJobCategoryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> categoryNames = new HashMap<>();
        jobCategoryRepository.findAllById(categoryIds).forEach(category -> categoryNames.put(category.getId(), category.getName()));
        Set<Long> industryIds = orders.stream().map(BossOrder::getIndustryId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> industryNames = new HashMap<>();
        jobIndustryRepository.findAllById(industryIds).forEach(industry -> industryNames.put(industry.getId(), industry.getName()));

        StringBuilder csv = new StringBuilder("审核ID,行业/工种,雇主,工价,招聘人数,提交时间,审核人,审核时间,状态\n");
        for (BossOrder order : orders) {
            String industryJob = formatIndustryJob(industryNames.get(order.getIndustryId()), categoryNames.get(order.getJobCategoryId()));
            if (industryJob == null || industryJob.isBlank()) industryJob = order.getPostion();
            csv.append(csvCell(order.getId())).append(',').append(csvCell(industryJob)).append(',')
                    .append(csvCell(employerNames.getOrDefault(order.getCreateBy(), "未知雇主"))).append(',').append(csvCell(order.getSalary())).append(',')
                    .append(csvCell(order.getOrderNum())).append(',').append(csvCell(order.getTimestamp())).append(',')
                    .append(csvCell(order.getAuditBy())).append(',').append(csvCell(order.getAuditTime())).append(',')
                    .append(csvCell(order.getOrderStatus())).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=job-audit.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    private String formatIndustryJob(String industry, String job) {
        if (industry == null || industry.isBlank()) return job;
        if (job == null || job.isBlank()) return industry;
        return industry + " / " + job;
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    /** 招工详情 */
    @Operation(summary = "招工详情", description = "返回完整 BossOrder 订单对象")
    @GetMapping("/{id}")
    public Result<JSONObject> get(@PathVariable Long id) {
        BossOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("订单不存在: " + id));
        JSONObject result = (JSONObject) JSON.toJSON(order);
        if (order.getJobCategoryId() != null) {
            jobCategoryRepository.findById(order.getJobCategoryId())
                    .ifPresent(category -> result.put("jobCategoryName", category.getName()));
        }
        if (order.getIndustryId() != null) {
            jobIndustryRepository.findById(order.getIndustryId())
                    .ifPresent(industry -> result.put("industryName", industry.getName()));
        }
        if (order.getCreateBy() != null) {
            User employer = userRepository.findById(order.getCreateBy()).orElse(null);
            if (employer != null) {
                result.put("employerCreditScore", employer.getCreditScore() == null ? 0 : employer.getCreditScore());
                result.put("complaintCount", reportRepository.countByTargetId(employer.getId()));
                List<BossOrder> history = orderRepository.findByCreateByOrderByIdDesc(employer.getId());
                long passed = history.stream().filter(item -> !Set.of("待审核", "审核拒绝").contains(item.getOrderStatus())).count();
                result.put("historyJobs", history.size());
                result.put("passRate", history.isEmpty() ? null : Math.round(passed * 10000.0 / history.size()) / 100.0);
                if (employer.getLicenseNo() != null || employer.getLegalRep() != null) {
                    List<Map<String, Object>> materials = new java.util.ArrayList<>();
                    if (employer.getLicenseNo() != null && !employer.getLicenseNo().isBlank()) {
                        materials.add(Map.of("name", "营业执照.pdf"));
                    }
                    if (employer.getLegalRep() != null && !employer.getLegalRep().isBlank()) {
                        materials.add(Map.of("name", "法人身份证.jpg"));
                    }
                    result.put("submittedMaterials", materials);
                }
            }
        }
        // 保险区域：同一订单可能有多名零工的保单，取首条展示险种与保额，并附保单数
        List<Insurance> insurances = insuranceRepository.findByOrderId(order.getId());
        if (!insurances.isEmpty()) {
            Insurance insurance = insurances.get(0);
            Map<String, Object> insuranceInfo = new HashMap<>();
            insuranceInfo.put("type", insurance.getType());
            insuranceInfo.put("amount", insurance.getAmount());
            insuranceInfo.put("premium", insurance.getPremium());
            insuranceInfo.put("status", insurance.getStatus());
            insuranceInfo.put("count", insurances.size());
            result.put("insurance", insuranceInfo);
        }

        result.put("benefits", extractBenefits(order.getOrderRemark()));
        result.put("submittedMaterials", result.getOrDefault("submittedMaterials", null));
        return Result.success(result);
    }

    private List<String> extractBenefits(String remark) {
        if (remark == null || remark.isBlank()) return List.of();
        Set<String> benefitNames = Set.of("包吃住", "包餐", "包工作餐", "中午包饭", "晚上包饭", "交通补贴", "全勤奖",
                "保险", "加班补助", "提供饮水", "免费培训", "工作轻松", "环境舒适", "有空调", "有风扇", "室内工作", "当日结清", "月结");
        return java.util.Arrays.stream(remark.split("[,，、;；]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> benefitNames.stream().anyMatch(name -> value.equals(name) || value.contains(name)))
                .distinct()
                .toList();
    }

    /** 审核通过 */
    @Operation(summary = "审核通过", description = "「待审核 → 招工中」，并向全部员工广播 ORDER_PUBLISH 消息。仅待审核的订单可以审核通过")
    @PutMapping("/{id}/audit/pass")
    public Result<BossOrder> pass(@PathVariable Long id, Authentication authentication) {
        BossOrder order = bossOrderService.auditPass(id);
        return Result.success(saveAuditInfo(order, authentication));
    }

    /** 审核拒绝 */
    @Operation(summary = "审核拒绝", description = "「待审核 → 审核拒绝」，原因追加到 orderRemark。仅待审核的订单可以审核拒绝")
    @PutMapping("/{id}/audit/reject")
    public Result<BossOrder> reject(@PathVariable Long id, @RequestParam(required = false) String reason,
                                    Authentication authentication) {
        BossOrder order = bossOrderService.auditReject(id, reason);
        return Result.success(saveAuditInfo(order, authentication));
    }

    private BossOrder saveAuditInfo(BossOrder order, Authentication authentication) {
        String operator = "管理员";
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                && user.username() != null && !user.username().isBlank()) {
            operator = user.username();
        }
        order.setAuditBy(operator);
        order.setAuditTime(new java.util.Date());
        return orderRepository.save(order);
    }

    /** 订单状态流转（admin 端也可手动推进） */
    @Operation(summary = "订单状态流转", description = "同 /boss/order/{id}/status，admin 端手动推进订单状态。target 取值：招工结束 / 待结算 / 已完成 / 取消招工")
    @PutMapping("/{id}/status")
    public Result<BossOrder> changeStatus(@PathVariable Long id, @RequestParam String target) {
        return Result.success(bossOrderService.changeOrderStatus(id, target));
    }

    /** 关闭招工（取消招工） */
    @Operation(summary = "关闭招工", description = "将订单状态置为「取消招工」")
    @PutMapping("/{id}/close")
    public Result<BossOrder> close(@PathVariable Long id) {
        return Result.success(bossOrderService.changeOrderStatus(id, "取消招工"));
    }

    /** 重新开启招工 */
    @Operation(summary = "开启招工", description = "将订单状态置为「招工中」")
    @PutMapping("/{id}/open")
    public Result<BossOrder> open(@PathVariable Long id) {
        BossOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("订单不存在: " + id));
        order.setOrderStatus("招工中");
        return Result.success(orderRepository.save(order));
    }

    // ==================== 报名人员管理 ====================

    /** 某订单的报名人员列表（带零工用户信息） */
    @Operation(summary = "报名人员列表", description = "返回该订单下所有报名记录，附带零工用户名、手机号、认证状态")
    @GetMapping("/{id}/applicants")
    public Result<List<Map<String, Object>>> applicants(@PathVariable Long id) {
        List<BaseOrderItem> items = orderItemRepository.findByOrderId(id);
        Set<Long> userIds = items.stream().map(BaseOrderItem::getUserId).filter(uid -> uid != null && uid > 0).collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        List<Map<String, Object>> result = items.stream().map(item -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", item.getId());
            m.put("orderId", item.getOrderId());
            m.put("userId", item.getUserId());
            m.put("status", item.getStatus());
            m.put("remark", item.getRemark());
            m.put("applyDate", item.getApplyDate());
            m.put("hireDate", item.getHireDate());
            m.put("cancelDate", item.getCancelDate());
            m.put("cancelReason", item.getCancelReason());
            User u = userMap.get(item.getUserId());
            if (u != null) {
                m.put("username", u.getUsername());
                m.put("nickname", u.getNickname());
                m.put("avatar", u.getAvatar());
                m.put("phone", u.getPhone());
                m.put("idCard", maskIdCard(u.getIdCard()));
                m.put("gender", u.getGender());
                m.put("age", u.getAge());
                m.put("certStatus", u.getCertStatus());
            }
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    private String maskIdCard(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() <= 8) return value;
        return value.substring(0, 4) + "********" + value.substring(value.length() - 4);
    }

    /** 管理员录用报名人员 */
    @Operation(summary = "管理员录用", description = "将已报名的记录状态变为「已录用」并记录录用时间")
    @PutMapping("/item/{itemId}/hire")
    public Result<BaseOrderItem> hireItem(@PathVariable Long itemId) {
        return Result.success(bossOrderService.hireItem(itemId));
    }

    /** 管理员拒绝报名人员 */
    @Operation(summary = "管理员拒绝报名", description = "将已报名的记录状态变为「取消报名」并记录拒绝原因")
    @PutMapping("/item/{itemId}/reject")
    public Result<BaseOrderItem> rejectItem(@PathVariable Long itemId, @RequestParam(required = false) String reason) {
        BaseOrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("报名记录不存在: " + itemId));
        if (!"已报名".equals(item.getStatus())) {
            throw new IllegalStateException("仅已报名的记录可以拒绝");
        }
        item.setStatus("取消报名");
        item.setCancelReason(reason);
        item.setCancelDate(java.sql.Date.valueOf(java.time.LocalDate.now()));
        return Result.success(orderItemRepository.save(item));
    }
}
