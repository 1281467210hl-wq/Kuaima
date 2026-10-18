package com.kuaima.app.controller.payroll;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.payroll.constant.PayrollConstants;
import com.kuaima.app.domain.payroll.entity.PayrollDetail;
import com.kuaima.app.domain.payroll.entity.PayrollEmployee;
import com.kuaima.app.domain.payroll.entity.PayrollOrder;
import com.kuaima.app.domain.payroll.model.BossPayrollCreateRequest;
import com.kuaima.app.domain.payroll.model.BossPayrollMembersRequest;
import com.kuaima.app.domain.payroll.repository.PayrollDetailRepository;
import com.kuaima.app.domain.payroll.repository.PayrollEmployeeRepository;
import com.kuaima.app.domain.payroll.repository.PayrollOrderRepository;
import com.kuaima.app.domain.payroll.service.PayrollService;
import com.kuaima.app.domain.project.constant.ProjectConstants;
import com.kuaima.app.domain.project.entity.AttendanceRecord;
import com.kuaima.app.domain.project.entity.ProjectMember;
import com.kuaima.app.domain.project.repository.AttendanceRepository;
import com.kuaima.app.domain.project.service.ProjectMemberService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/boss/payroll")
@Tag(name = "老板-批量发薪", description = "发薪单创建/列表、发薪员工管理、待审批、已审批记录、审批操作")
@RequiredArgsConstructor
public class BossPayrollController {

    private final PayrollService payrollService;
    private final PayrollOrderRepository orderRepository;
    private final PayrollDetailRepository detailRepository;
    private final PayrollEmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final ProjectMemberService memberService;

    // ==================== 发薪单 ====================

    @Operation(summary = "我的发薪单列表", description = "当前老板创建的发薪单（不含已撤回），按关键字（标题）筛选")
    @GetMapping("/orders")
    public Result<List<PayrollOrder>> listOrders(@RequestParam(required = false) String keyword,
                                                 Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> orders = orderRepository.findByCreatorIdOrderByIdDesc(bossId);
        List<PayrollOrder> filtered = new ArrayList<>();
        String kw = normalize(keyword);
        for (PayrollOrder order : orders) {
            if (PayrollConstants.ORDER_WITHDRAWN.equals(order.getStatus())) {
                continue;
            }
            if (kw != null && (order.getTitle() == null || !order.getTitle().contains(kw))) {
                continue;
            }
            filtered.add(order);
        }
        return Result.success(filtered);
    }

    @Operation(summary = "创建发薪单", description = "body：{title, projectId, projectName, type, details?[]}；details 为空则创建为「待提交审核」草稿，后续再添加人员与设置薪资；details 非空则直接创建为待审批")
    @PostMapping("/orders")
    public Result<PayrollOrder> createOrder(@RequestBody BossPayrollCreateRequest request,
                                            Authentication authentication) {
        Long bossId = requireBossId(authentication);
        if (!StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("转账标题不能为空");
        }
        if (request.getProjectId() == null) {
            throw new IllegalArgumentException("请选择关联项目");
        }
        User boss = userRepository.findById(bossId).orElse(null);
        String creator = boss != null && StringUtils.hasText(boss.getCompanyName())
                ? boss.getCompanyName()
                : (boss != null && StringUtils.hasText(boss.getNickname()) ? boss.getNickname() : "老板");
        PayrollOrder order = new PayrollOrder();
        order.setTitle(request.getTitle());
        order.setProjectId(request.getProjectId());
        order.setProjectName(request.getProjectName());
        String type = request.getType();
        order.setType(StringUtils.hasText(type) ? type : "wage");
        boolean draft = request.getDetails() == null || request.getDetails().isEmpty();
        if (draft) {
            return Result.success(payrollService.createDraft(order, bossId, creator));
        }
        order.setSubmitTime(new Date());
        return Result.success(payrollService.createOrder(order, request.getDetails(), bossId, creator));
    }

    @Operation(summary = "发薪单详情（含人员明细）", description = "草稿/待审批单的人员与薪资回显")
    @GetMapping("/orders/{orderId}")
    public Result<Map<String, Object>> orderDetail(@PathVariable Long orderId,
                                                   Authentication authentication) {
        Long bossId = requireBossId(authentication);
        PayrollOrder order = ownedOrder(orderId, bossId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", order);
        data.put("details", detailRepository.findByPayrollId(orderId));
        return Result.success(data);
    }

    @Operation(summary = "草稿-添加/替换发薪人员", description = "body：{members:[{userId,name,phone,job,dailyWage?,attendDays?}]}；整体替换草稿人员明细，保留已设置薪资。仅草稿态可用")
    @PutMapping("/orders/{orderId}/members")
    public Result<PayrollOrder> setOrderMembers(@PathVariable Long orderId,
                                                @RequestBody BossPayrollMembersRequest body,
                                                Authentication authentication) {
        Long bossId = requireBossId(authentication);
        ownedOrder(orderId, bossId);
        List<PayrollDetail> members = body == null || body.getMembers() == null ? List.of() : body.getMembers();
        return Result.success(payrollService.replaceMembers(orderId, members));
    }

    @Operation(summary = "草稿-设置薪资", description = "body：{salaries:[{userId,phone,dailyWage(分),attendDays}]}；按人回填日薪与出勤天数并重算金额。仅草稿态可用")
    @PutMapping("/orders/{orderId}/salary")
    public Result<PayrollOrder> setOrderSalary(@PathVariable Long orderId,
                                               @RequestBody BossPayrollMembersRequest body,
                                               Authentication authentication) {
        Long bossId = requireBossId(authentication);
        ownedOrder(orderId, bossId);
        List<PayrollDetail> salaries = body == null || body.getSalaries() == null ? List.of() : body.getSalaries();
        return Result.success(payrollService.setSalaries(orderId, salaries));
    }

    @Operation(summary = "草稿-提交审核", description = "校验已有人员且金额>0，草稿转待审批")
    @PostMapping("/orders/{orderId}/submit")
    public Result<PayrollOrder> submitOrder(@PathVariable Long orderId,
                                            Authentication authentication) {
        Long bossId = requireBossId(authentication);
        ownedOrder(orderId, bossId);
        return Result.success(payrollService.submitDraft(orderId));
    }

    private PayrollOrder ownedOrder(Long orderId, Long bossId) {
        PayrollOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("发薪单不存在: " + orderId));
        if (order.getCreatorId() == null || !order.getCreatorId().equals(bossId)) {
            throw new ForbiddenBusinessException("无权操作其他老板的发薪单");
        }
        return order;
    }

    @Operation(summary = "项目在职成员（批量发薪用）", description = "返回该项目在职/临时成员，含姓名/手机号/岗位/userId/日薪（从发薪员工库匹配，未匹配为 0 由老板填）")
    @GetMapping("/orders/members")
    public Result<List<Map<String, Object>>> payrollMembers(@RequestParam Long projectId,
                                                            Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<ProjectMember> members = memberService.listMembers(projectId, null).stream()
                .filter(m -> !ProjectConstants.MEMBER_LEFT.equals(m.getStatus()))
                .toList();

        // 发薪员工库：按 老板 + 手机号 预填日薪默认值
        Map<String, Long> wageByPhone = new LinkedHashMap<>();
        for (PayrollEmployee emp : employeeRepository.findByBossIdOrderByIdDesc(bossId)) {
            if (StringUtils.hasText(emp.getPhone())) {
                wageByPhone.putIfAbsent(emp.getPhone().trim(), emp.getDailyWage());
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (ProjectMember m : members) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", m.getUserId());
            item.put("name", m.getName());
            item.put("phone", m.getPhone());
            item.put("job", m.getRole());
            Long wage = wageByPhone.get(m.getPhone() == null ? "" : m.getPhone().trim());
            item.put("dailyWage", wage != null ? wage : 0L);
            list.add(item);
        }
        return Result.success(list);
    }

    @Operation(summary = "已审批的发薪记录", description = "当前老板审批通过的发薪单，按关键字（标题）筛选")
    @GetMapping("/orders/approved")
    public Result<List<PayrollOrder>> listApprovedOrders(@RequestParam(required = false) String keyword,
                                                         Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> orders = orderRepository.findByCreatorIdAndStatusOrderByIdDesc(
                bossId, PayrollConstants.ORDER_APPROVED);
        String kw = normalize(keyword);
        if (kw == null) {
            return Result.success(orders);
        }
        List<PayrollOrder> filtered = new ArrayList<>();
        for (PayrollOrder order : orders) {
            if (order.getTitle() != null && order.getTitle().contains(kw)) {
                filtered.add(order);
            }
        }
        return Result.success(filtered);
    }

    // ==================== 待审批 / 审批操作 ====================

    @Operation(summary = "待我审批的发薪单", description = "当前老板待审批的发薪单，按关键字（标题）筛选")
    @GetMapping("/orders/pending")
    public Result<List<PayrollOrder>> listPendingOrders(@RequestParam(required = false) String keyword,
                                                        Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> orders = orderRepository.findByCreatorIdAndStatusOrderByIdDesc(
                bossId, PayrollConstants.ORDER_PENDING);
        String kw = normalize(keyword);
        if (kw == null) {
            return Result.success(orders);
        }
        List<PayrollOrder> filtered = new ArrayList<>();
        for (PayrollOrder order : orders) {
            if (order.getTitle() != null && order.getTitle().contains(kw)) {
                filtered.add(order);
            }
        }
        return Result.success(filtered);
    }

    @Operation(summary = "已驳回的发薪记录", description = "当前老板被驳回的发薪单，按关键字（标题）筛选")
    @GetMapping("/orders/rejected")
    public Result<List<PayrollOrder>> listRejectedOrders(@RequestParam(required = false) String keyword,
                                                         Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> orders = orderRepository.findByCreatorIdAndStatusOrderByIdDesc(
                bossId, PayrollConstants.ORDER_REJECTED);
        String kw = normalize(keyword);
        if (kw == null) {
            return Result.success(orders);
        }
        List<PayrollOrder> filtered = new ArrayList<>();
        for (PayrollOrder order : orders) {
            if (order.getTitle() != null && order.getTitle().contains(kw)) {
                filtered.add(order);
            }
        }
        return Result.success(filtered);
    }

    @Operation(summary = "已审批记录（含已通过和已驳回）", description = "当前老板所有已处理的发薪单，按关键字（标题）筛选")
    @GetMapping("/orders/reviewed")
    public Result<List<PayrollOrder>> listReviewedOrders(@RequestParam(required = false) String keyword,
                                                         Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollOrder> approved = orderRepository.findByCreatorIdAndStatusOrderByIdDesc(
                bossId, PayrollConstants.ORDER_APPROVED);
        List<PayrollOrder> rejected = orderRepository.findByCreatorIdAndStatusOrderByIdDesc(
                bossId, PayrollConstants.ORDER_REJECTED);
        List<PayrollOrder> all = new ArrayList<>();
        all.addAll(approved);
        all.addAll(rejected);
        all.sort((a, b) -> {
            if (a.getId() == null && b.getId() == null) return 0;
            if (a.getId() == null) return 1;
            if (b.getId() == null) return -1;
            return b.getId().compareTo(a.getId());
        });
        String kw = normalize(keyword);
        if (kw == null) {
            return Result.success(all);
        }
        List<PayrollOrder> filtered = new ArrayList<>();
        for (PayrollOrder order : all) {
            if (order.getTitle() != null && order.getTitle().contains(kw)) {
                filtered.add(order);
            }
        }
        return Result.success(filtered);
    }

    @Operation(summary = "审批通过发薪单", description = "将指定发薪单状态置为审批通过，通过后触发批量发薪流程")
    @PutMapping("/orders/{orderId}/approve")
    public Result<PayrollOrder> approveOrder(@PathVariable Long orderId,
                                             Authentication authentication) {
        Long bossId = requireBossId(authentication);
        PayrollOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("发薪单不存在: " + orderId));
        if (!order.getCreatorId().equals(bossId)) {
            throw new ForbiddenBusinessException("无权操作其他老板的发薪单");
        }
        if (!PayrollConstants.ORDER_PENDING.equals(order.getStatus())) {
            throw new IllegalArgumentException("只能审批待审批的发薪单");
        }
        User reviewer = userRepository.findById(bossId).orElse(null);
        String reviewerName = reviewer != null && StringUtils.hasText(reviewer.getNickname())
                ? reviewer.getNickname() : "老板";
        return Result.success(payrollService.approve(orderId, reviewerName));
    }

    @Operation(summary = "驳回发薪单", description = "将指定发薪单状态置为已驳回，需填写驳回原因")
    @PutMapping("/orders/{orderId}/reject")
    public Result<PayrollOrder> rejectOrder(@PathVariable Long orderId,
                                            @RequestBody Map<String, Object> body,
                                            Authentication authentication) {
        Long bossId = requireBossId(authentication);
        PayrollOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("发薪单不存在: " + orderId));
        if (!order.getCreatorId().equals(bossId)) {
            throw new ForbiddenBusinessException("无权操作其他老板的发薪单");
        }
        if (!PayrollConstants.ORDER_PENDING.equals(order.getStatus())) {
            throw new IllegalArgumentException("只能审批待审批的发薪单");
        }
        User reviewer = userRepository.findById(bossId).orElse(null);
        String reviewerName = reviewer != null && StringUtils.hasText(reviewer.getNickname())
                ? reviewer.getNickname() : "老板";
        PayrollOrder rejected = payrollService.reject(orderId, reviewerName);
        String reason = body == null ? null : str(body.get("reason"));
        if (StringUtils.hasText(reason)) {
            rejected.setRejectReason(reason);
            rejected = orderRepository.save(rejected);
        }
        return Result.success(rejected);
    }

    // ==================== 发薪员工 ====================

    @Operation(summary = "发薪员工列表与统计", description = "按姓名/手机号搜索员工，返回列表与统计（总人数/本月新增/今日新增）")
    @GetMapping("/employees")
    public Result<Map<String, Object>> listEmployees(@RequestParam(required = false) String keyword,
                                                     Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<PayrollEmployee> employees = employeeRepository.searchByBoss(bossId, normalize(keyword));

        LocalDate today = LocalDate.now();
        Date dayStart = toDate(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = toDate(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date monthStart = toDate(today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employees", employees);
        data.put("total", employeeRepository.countByBossId(bossId));
        data.put("monthNew", employeeRepository.countByBossIdAndAddTimeAfter(bossId, monthStart));
        data.put("todayNew", employeeRepository.countByBossIdAndAddTimeBetween(bossId, dayStart, dayEnd));
        return Result.success(data);
    }

    @Operation(summary = "发薪员工详情", description = "返回员工信息、最近发薪记录（按手机号匹配发薪明细）与最近考勤记录")
    @GetMapping("/employees/{id}")
    public Result<Map<String, Object>> getEmployeeDetail(@PathVariable Long id,
                                                          Authentication authentication) {
        Long bossId = requireBossId(authentication);
        PayrollEmployee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("员工不存在: " + id));
        if (!employee.getBossId().equals(bossId)) {
            throw new ForbiddenBusinessException("无权查看其他老板的员工");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employee", employee);
        if (StringUtils.hasText(employee.getPhone())) {
            data.put("payRecords", detailRepository.findByPhoneOrderByIdDesc(employee.getPhone()));
        } else {
            data.put("payRecords", List.of());
        }
        // 最近考勤：按姓名匹配考勤打卡记录（最近10条）
        if (StringUtils.hasText(employee.getName())) {
            data.put("attendances", attendanceRepository.findTop10ByNameOrderByAttendDateDesc(employee.getName()));
        } else {
            data.put("attendances", List.of());
        }
        return Result.success(data);
    }

    @Operation(summary = "添加发薪员工", description = "手动添加员工到发薪员工库")
    @PostMapping("/employees")
    public Result<PayrollEmployee> addEmployee(@RequestBody PayrollEmployee employee,
                                               Authentication authentication) {
        Long bossId = requireBossId(authentication);
        employee.setBossId(bossId);
        if (employee.getStatus() == null) {
            employee.setStatus("active");
        }
        if (employee.getCertified() == null) {
            employee.setCertified(false);
        }
        if (employee.getTotalAttendDays() == null) {
            employee.setTotalAttendDays(0);
        }
        if (employee.getTotalPaid() == null) {
            employee.setTotalPaid(0L);
        }
        if (employee.getWorkDays() == null) {
            employee.setWorkDays(0);
        }
        if (employee.getAddTime() == null) {
            employee.setAddTime(new Date());
        }
        return Result.success(employeeRepository.save(employee));
    }

    private Long requireBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.BOSS.equals(loginUser.role()) && loginUser.id() != null) {
            return loginUser.id();
        }
        throw new ForbiddenBusinessException("当前登录账号不是有效的老板账号");
    }

    private String normalize(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private String str(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private Date toDate(java.time.Instant instant) {
        return Date.from(instant);
    }
}
