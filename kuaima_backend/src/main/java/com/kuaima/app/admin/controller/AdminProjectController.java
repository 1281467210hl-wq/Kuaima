package com.kuaima.app.admin.controller;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.common.util.QrCodeUtils;
import com.kuaima.app.domain.project.constant.ProjectConstants;
import com.kuaima.app.domain.project.entity.AttendanceRecord;
import com.kuaima.app.domain.project.entity.OnboardApply;
import com.kuaima.app.domain.project.entity.Project;
import com.kuaima.app.domain.project.entity.ProjectAttendanceCode;
import com.kuaima.app.domain.project.entity.ProjectMember;
import com.kuaima.app.domain.project.entity.ProjectOnsiteStaff;
import com.kuaima.app.domain.project.repository.OnboardRepository;
import com.kuaima.app.domain.project.repository.ProjectMemberRepository;
import com.kuaima.app.domain.project.repository.ProjectRepository;
import com.kuaima.app.domain.project.service.AttendanceService;
import com.kuaima.app.domain.project.service.OnboardService;
import com.kuaima.app.domain.project.service.OnsiteService;
import com.kuaima.app.domain.project.service.ProjectAttendanceCodeService;
import com.kuaima.app.domain.project.service.ProjectMemberService;
import com.kuaima.app.domain.project.service.ProjectService;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 后台-项目管理。复用老板端 domain/project 各 Service，去掉 bossId 作用域（平台级全量视角）。
 * 返回结构与 admin-web ProjectMgmt/ProjectDetail 页面字段对齐。
 */
@RestController
@RequestMapping("/admin/projects")
@Tag(name = "后台-项目管理", description = "项目列表/详情/增改/归档/删除/统计 + 成员/驻场/考勤/入职 + 签到码")
public class AdminProjectController {

    private final ProjectService projectService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final OnboardRepository onboardRepository;
    private final ProjectMemberService memberService;
    private final OnsiteService onsiteService;
    private final AttendanceService attendanceService;
    private final OnboardService onboardService;
    private final ProjectAttendanceCodeService attendanceCodeService;

    public AdminProjectController(ProjectService projectService, ProjectRepository projectRepository,
            ProjectMemberRepository memberRepository, OnboardRepository onboardRepository,
            ProjectMemberService memberService, OnsiteService onsiteService,
            AttendanceService attendanceService, OnboardService onboardService,
            ProjectAttendanceCodeService attendanceCodeService) {
        this.projectService = projectService;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.onboardRepository = onboardRepository;
        this.memberService = memberService;
        this.onsiteService = onsiteService;
        this.attendanceService = attendanceService;
        this.onboardService = onboardService;
        this.attendanceCodeService = attendanceCodeService;
    }

    /* ==================== 项目 CRUD ==================== */

    @GetMapping
    @Operation(summary = "项目列表（关键字/状态/所属企业筛选 + 分页）")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long enterpriseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Project> result;
        if (enterpriseId == null) {
            result = projectService.listProjects(keyword, status, PageRequest.of(page, size));
        } else {
            // Service 未提供企业维度筛选，这里按同一规则补一条 Specification（不改共享 Service）
            Specification<Project> spec = (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
                ps.add(cb.notEqual(root.get("status"), ProjectConstants.PROJECT_DELETED));
                ps.add(cb.equal(root.get("enterpriseId"), enterpriseId));
                if (StringUtils.hasText(status)) {
                    ps.add(cb.equal(root.get("status"), status));
                }
                if (StringUtils.hasText(keyword)) {
                    String like = "%" + keyword + "%";
                    ps.add(cb.or(cb.like(root.get("name"), like), cb.like(root.get("companyName"), like)));
                }
                return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };
            result = projectRepository.findAll(spec, PageRequest.of(page, size));
        }
        List<Map<String, Object>> rows = result.getContent().stream()
                .map(this::projectView).collect(Collectors.toList());
        return Result.success(rows, page, result.getTotalElements());
    }

    @GetMapping("/stats")
    @Operation(summary = "项目统计（进行中/已归档/今日签到码数）")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("active", projectService.countByStatus(ProjectConstants.PROJECT_ACTIVE));
        map.put("archived", projectService.countByStatus(ProjectConstants.PROJECT_ARCHIVED));
        map.put("total", projectRepository.count());
        return Result.success(map);
    }

    @GetMapping("/{id}")
    @Operation(summary = "项目详情（基础信息 + stats/members/checkins/onsite/onboard 聚合）")
    public Result<Map<String, Object>> detail(@PathVariable Long id,
            @RequestParam(required = false) String date) {
        Project project = projectService.getOrThrow(id);
        LocalDate day = date != null ? LocalDate.parse(date) : LocalDate.now();

        List<ProjectMember> members = memberService.listMembers(id, null);
        Map<String, Long> memberStats = memberService.stats(id);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", memberStats.get("all"));
        stats.put("active", memberStats.get("active"));
        stats.put("left", memberStats.get("left"));
        stats.put("temp", memberStats.get("temp"));

        Map<String, Object> data = projectView(project);
        data.put("coord", null);                       // 实体无经纬度字段，前端可降级展示
        data.put("signCode", attendanceCodeService.current(project, false).getCode());
        data.put("stats", stats);
        data.put("attendanceStats", attendanceService.statsForDate(id, day));
        data.put("onboardStats", onboardService.stats(id));
        data.put("members", members.stream().map(this::memberView).collect(Collectors.toList()));
        data.put("checkins", attendanceService.listByDate(id, day).stream()
                .map(r -> checkinView(r, project, members)).collect(Collectors.toList()));
        data.put("onsite", onsiteService.list(id).stream().map(this::onsiteView).collect(Collectors.toList()));
        data.put("onboard", onboardService.list(id, null).stream()
                .map(this::onboardView).collect(Collectors.toList()));
        return Result.success(data);
    }

    @PostMapping
    @Operation(summary = "创建项目")
    public Result<Map<String, Object>> create(@RequestBody Project project) {
        Project created = projectService.create(project, currentAdminId());
        // Service 未覆盖的字段在此补写（同 update 的处理方式）
        if (project.getCheckinTime() != null || project.getCheckoutTime() != null) {
            created.setCheckinTime(project.getCheckinTime());
            created.setCheckoutTime(project.getCheckoutTime());
            created = projectRepository.save(created);
        }
        return Result.success(projectView(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新项目（含所属企业 enterpriseId、规定签到/签退时间，Service 未覆盖字段在此补写）")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Project body) {
        Project saved = projectService.update(id, body);
        boolean dirty = false;
        if (body.getEnterpriseId() != null && !body.getEnterpriseId().equals(saved.getEnterpriseId())) {
            saved.setEnterpriseId(body.getEnterpriseId());
            dirty = true;
        }
        if (body.getCheckinTime() != null && !body.getCheckinTime().equals(saved.getCheckinTime())) {
            saved.setCheckinTime(body.getCheckinTime());
            dirty = true;
        }
        if (body.getCheckoutTime() != null && !body.getCheckoutTime().equals(saved.getCheckoutTime())) {
            saved.setCheckoutTime(body.getCheckoutTime());
            dirty = true;
        }
        if (dirty) {
            saved = projectRepository.save(saved);
        }
        return Result.success(projectView(saved));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档项目")
    public Result<Void> archive(@PathVariable Long id) {
        projectService.archive(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除项目（软删除）")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success();
    }

    /* ==================== 子资源：成员 / 驻场 / 考勤 / 入职 ==================== */

    @GetMapping("/{id}/members")
    @Operation(summary = "项目成员列表（status 可选：active/temp/left）")
    public Result<List<Map<String, Object>>> members(@PathVariable Long id,
            @RequestParam(required = false) String status) {
        return Result.success(memberService.listMembers(id, status).stream()
                .map(this::memberView).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "新增项目成员（body: name/phone/role/userId/status/joinDate），同步补一条已通过入职记录")
    public Result<Map<String, Object>> addMember(@PathVariable Long id, @RequestBody ProjectMember member) {
        projectService.getOrThrow(id);
        member.setProjectId(id);
        ProjectMember saved = memberService.add(member, currentAdminId());
        syncPassedOnboard(saved);
        return Result.success(memberView(saved));
    }

    /**
     * 成员新增成功后补一条「已通过」入职申请，让手机端入职记录单与项目成员同一数据源。
     * 同一人（手机号优先，其次姓名）在本项目已有 pending/passed 记录时不重复创建；
     * OnboardApply 无 userId 列，故按手机号/姓名判重。
     */
    private void syncPassedOnboard(ProjectMember member) {
        boolean exists = onboardRepository.findByProjectId(member.getProjectId()).stream()
                .anyMatch(a -> !ProjectConstants.ONBOARD_REJECTED.equals(a.getStatus())
                        && samePerson(a, member));
        if (exists) {
            return;
        }
        OnboardApply apply = new OnboardApply();
        apply.setProjectId(member.getProjectId());
        apply.setName(member.getName());
        apply.setPhone(member.getPhone());
        apply.setJob(member.getRole());
        apply.setApplyRole(member.getRole());
        apply.setStatus(ProjectConstants.ONBOARD_PASSED);
        apply.setJoinDate(member.getJoinDate() != null ? member.getJoinDate() : LocalDate.now());
        apply.setApplyTime(new java.util.Date());
        apply.setReviewTime(new java.util.Date());
        apply.setCreateBy(member.getCreateBy());
        onboardRepository.save(apply);
    }

    /** 同一人判定：手机号都存在时按手机号，否则按姓名。 */
    private static boolean samePerson(OnboardApply apply, ProjectMember member) {
        if (StringUtils.hasText(member.getPhone()) && StringUtils.hasText(apply.getPhone())) {
            return apply.getPhone().equals(member.getPhone());
        }
        return StringUtils.hasText(member.getName()) && member.getName().equals(apply.getName());
    }

    @PutMapping("/{id}/members/{memberId}/status")
    @Operation(summary = "编辑成员状态（status 支持中文：在职/临时/离职，或 active/temp/left；body 可选 leaveDate=yyyy-MM-dd，仅离职时写入）")
    public Result<Map<String, Object>> updateMemberStatus(@PathVariable Long id, @PathVariable Long memberId,
            @RequestBody Map<String, String> body) {
        String status = normalizeMemberStatus(body.get("status"));
        ProjectMember member = memberService.getOrThrow(memberId);
        member.setStatus(status);
        // 置离职写入离职日期（未传按今日），恢复在职清空，便于列表/详情展示 leaveTime
        if (ProjectConstants.MEMBER_LEFT.equals(status)) {
            String leaveDate = body.get("leaveDate");
            member.setLeaveDate(StringUtils.hasText(leaveDate)
                    ? LocalDate.parse(leaveDate.trim()) : LocalDate.now());
        } else if (ProjectConstants.MEMBER_ACTIVE.equals(status)) {
            member.setLeaveDate(null);
        }
        return Result.success(memberView(memberRepository.save(member)));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @Operation(summary = "移除项目成员")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long memberId) {
        memberService.remove(memberId);
        return Result.success();
    }

    @GetMapping("/{id}/onsite")
    @Operation(summary = "驻场人员列表")
    public Result<List<Map<String, Object>>> onsite(@PathVariable Long id) {
        return Result.success(onsiteService.list(id).stream().map(this::onsiteView).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/onsite")
    @Operation(summary = "新建驻场人员（body: name/phone/company/onsiteRole）")
    public Result<Map<String, Object>> addOnsite(@PathVariable Long id, @RequestBody ProjectOnsiteStaff staff) {
        projectService.getOrThrow(id);
        staff.setProjectId(id);
        return Result.success(onsiteView(onsiteService.add(staff, currentAdminId())));
    }

    @DeleteMapping("/{id}/onsite/{staffId}")
    @Operation(summary = "移除驻场人员")
    public Result<Void> removeOnsite(@PathVariable Long id, @PathVariable Long staffId) {
        onsiteService.remove(staffId);
        return Result.success();
    }

    @GetMapping("/{id}/attendance")
    @Operation(summary = "考勤打卡列表（date 缺省为今日，附当日统计）")
    public Result<Map<String, Object>> attendance(@PathVariable Long id,
            @RequestParam(required = false) String date) {
        Project project = projectService.getOrThrow(id);
        LocalDate day = date != null ? LocalDate.parse(date) : LocalDate.now();
        List<ProjectMember> members = memberService.listMembers(id, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", attendanceService.listByDate(id, day).stream()
                .map(r -> checkinView(r, project, members)).collect(Collectors.toList()));
        data.put("stats", attendanceService.statsForDate(id, day));
        return Result.success(data);
    }

    @GetMapping("/{id}/onboard")
    @Operation(summary = "入职申请列表（status 可选：pending/passed/rejected）")
    public Result<List<Map<String, Object>>> onboard(@PathVariable Long id,
            @RequestParam(required = false) String status) {
        return Result.success(onboardService.list(id, status).stream()
                .map(this::onboardView).collect(Collectors.toList()));
    }

    /* ==================== 签到码 ==================== */

    @GetMapping("/{id}/attendance-code")
    @Operation(summary = "项目签到码（含 base64 二维码图；refresh=true 重新生成 code）")
    public Result<Map<String, Object>> attendanceCode(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        Project project = projectService.getOrThrow(id);
        ProjectAttendanceCode code = attendanceCodeService.current(project, refresh);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectId", project.getId());
        data.put("projectName", project.getName());
        data.put("code", code.getCode());
        data.put("date", code.getAttendDate().toString());
        data.put("qrContent", code.getQrContent());
        data.put("expireMinutes", code.getExpireMinutes());
        data.put("expireTime", code.getExpireTime());
        data.put("status", code.getStatus());
        data.put("qrImage", QrCodeUtils.generateBase64Png(code.getQrContent(), 300));
        return Result.success(data);
    }

    /* ==================== 视图映射 ==================== */

    /** 项目 → 前端列表/详情基础信息字段（code 为展示编号，实体暂无该列，用 PRJ-{id} 兜底）。 */
    private Map<String, Object> projectView(Project p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode() != null && !p.getCode().isEmpty() ? p.getCode() : "PRJ-" + p.getId());
        m.put("name", p.getName());
        m.put("client", p.getCompanyName());
        m.put("manager", p.getLeaderName());
        m.put("managerPhone", p.getLeaderPhone());
        m.put("date", p.getEstablishDate() != null ? p.getEstablishDate().toString() : null);
        m.put("address", p.getLocation());
        m.put("checkinTime", p.getCheckinTime());
        m.put("checkoutTime", p.getCheckoutTime());
        m.put("enterpriseId", p.getEnterpriseId());
        m.put("status", p.getStatus());
        m.put("statusText", ProjectConstants.PROJECT_ACTIVE.equals(p.getStatus()) ? "进行中" : "已归档");
        m.put("count", (long) memberService.listMembers(p.getId(), null).size());
        return m;
    }

    private Map<String, Object> memberView(ProjectMember e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("phone", e.getPhone());
        m.put("job", e.getRole());
        m.put("salary", e.getSalary());
        // 用工类型：优先取实体列，缺省时按成员状态推断（temp=临时工，其余=正式工）
        m.put("type", employmentTypeText(e));
        m.put("joinTime", e.getJoinDate() != null ? e.getJoinDate().toString() : null);
        m.put("leaveTime", memberLeaveTimeText(e));
        m.put("status", memberStatusText(e.getStatus()));
        m.put("statusCode", e.getStatus());
        return m;
    }

    /** 用工类型文案：实体列优先（fulltime/temp），缺省按状态推断。 */
    private String employmentTypeText(ProjectMember e) {
        if (e.getEmploymentType() != null && !e.getEmploymentType().isEmpty()) {
            return "temp".equals(e.getEmploymentType()) ? "临时工" : "正式工";
        }
        return ProjectConstants.MEMBER_TEMP.equals(e.getStatus()) ? "临时工" : "正式工";
    }

    /** 离职时间：优先取离职日期列，否则离职成员回退到更新时间戳。 */
    private String memberLeaveTimeText(ProjectMember e) {
        if (e.getLeaveDate() != null) {
            return e.getLeaveDate().toString();
        }
        return ProjectConstants.MEMBER_LEFT.equals(e.getStatus()) && e.getTimestamp() != null
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(e.getTimestamp()) : null;
    }

    private Map<String, Object> checkinView(AttendanceRecord r, Project project, List<ProjectMember> members) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("worker", r.getName());
        m.put("job", members.stream()
                .filter(mb -> mb.getId().equals(r.getMemberId()))
                .findFirst().map(ProjectMember::getRole).orElse(null));
        m.put("time", r.getSignInTime() != null
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(r.getSignInTime()) : "—");
        // 逐条签到地点：优先取记录列，缺省回退项目地点
        m.put("loc", r.getSignInLocation() != null && !r.getSignInLocation().isEmpty()
                ? r.getSignInLocation() : project.getLocation());
        m.put("statusCode", r.getStatus());
        m.put("status", attendStatusText(r.getStatus()));
        m.put("lateMinutes", r.getLateMinutes());
        return m;
    }

    private Map<String, Object> onsiteView(ProjectOnsiteStaff s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("phone", s.getPhone());
        m.put("company", s.getCompany());
        // 岗位：优先取实体 position 列，缺省回退驻场角色文案
        m.put("job", s.getPosition() != null && !s.getPosition().isEmpty()
                ? s.getPosition()
                : (ProjectConstants.ONSITE_LEADER.equals(s.getOnsiteRole()) ? "项目负责人" : "项目助理"));
        m.put("onsiteRole", s.getOnsiteRole());
        m.put("startDate", s.getStartDate() != null ? s.getStartDate().toString() : null);
        m.put("endDate", s.getEndDate() != null ? s.getEndDate().toString() : null);
        m.put("onsiteDays", s.getOnsiteDays());
        String onsiteStatus = onsiteStatusOf(s);
        m.put("status", "ended".equals(onsiteStatus) ? "已结束" : "驻场中");
        m.put("onsiteStatus", onsiteStatus);
        return m;
    }

    /** 驻场状态：实体列优先；为空时按驻场结束日期推导（endDate 早于今日视为已结束），不改表结构。 */
    private static String onsiteStatusOf(ProjectOnsiteStaff s) {
        if (StringUtils.hasText(s.getOnsiteStatus())) {
            return s.getOnsiteStatus();
        }
        return s.getEndDate() != null && s.getEndDate().isBefore(LocalDate.now()) ? "ended" : "ongoing";
    }

    private Map<String, Object> onboardView(OnboardApply a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("worker", a.getName());
        m.put("job", a.getJob());
        m.put("phone", a.getPhone());
        m.put("role", a.getApplyRole());
        m.put("time", a.getJoinDate() != null ? a.getJoinDate() + " 入职"
                : a.getApplyTime() != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(a.getApplyTime()) : null);
        m.put("statusCode", a.getStatus());
        m.put("status", onboardStatusText(a.getStatus()));
        return m;
    }

    /* ==================== 状态文案映射 ==================== */

    private static String memberStatusText(String s) {
        switch (s == null ? "" : s) {
            case ProjectConstants.MEMBER_ACTIVE: return "在职";
            case ProjectConstants.MEMBER_TEMP: return "临时";
            case ProjectConstants.MEMBER_LEFT: return "离职";
            default: return s;
        }
    }

    /** 前端编辑成员状态可能直接回传中文，做一次归一化。 */
    private static String normalizeMemberStatus(String s) {
        if (s == null) {
            return ProjectConstants.MEMBER_TEMP;
        }
        switch (s) {
            case "在职": return ProjectConstants.MEMBER_ACTIVE;
            case "临时": return ProjectConstants.MEMBER_TEMP;
            case "离职": return ProjectConstants.MEMBER_LEFT;
            default: return s;
        }
    }

    private static String attendStatusText(String s) {
        switch (s == null ? "" : s) {
            case ProjectConstants.ATTEND_ON: return "出勤";
            case ProjectConstants.ATTEND_LATE: return "迟到";
            case ProjectConstants.ATTEND_ABSENT: return "缺卡";
            case ProjectConstants.ATTEND_LEAVE: return "请假";
            default: return s;
        }
    }

    private static String onboardStatusText(String s) {
        switch (s == null ? "" : s) {
            case ProjectConstants.ONBOARD_PENDING: return "审核中";
            case ProjectConstants.ONBOARD_PASSED: return "已通过";
            case ProjectConstants.ONBOARD_REJECTED: return "已拒绝";
            default: return s;
        }
    }

    /** 当前登录管理员 id（审计字段 createBy 用）。 */
    private Long currentAdminId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof LoginUser user ? user.id() : null;
    }
}
