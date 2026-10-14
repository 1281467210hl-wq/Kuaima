package com.kuaima.app.admin.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

import com.alibaba.fastjson2.JSON;
import com.kuaima.app.common.BusinessHttpException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.employee.entity.EmployeeRole;
import com.kuaima.app.domain.employee.repository.EmployeeRoleRepository;
import com.kuaima.app.domain.enterprise.constant.WorkbenchPermCodes;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.domain.enterprise.entity.EnterpriseInvite;
import com.kuaima.app.domain.enterprise.entity.EnterpriseJoinApply;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMemberProject;
import com.kuaima.app.domain.enterprise.repository.AdminEnterpriseMemberRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseJoinApplyRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseMemberProjectRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseRepository;
import com.kuaima.app.domain.enterprise.service.EnterpriseMemberService;
import com.kuaima.app.domain.project.entity.Project;
import com.kuaima.app.domain.project.repository.ProjectRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 后台-企业成员管理（admin-web EnterpriseMember.vue 数据形状对齐）。
 * 列表字段：id/name/phone/avatar/dept/position/role/roleName/roleId/joinTime/status/statusName/proj/wbPerms；
 * 权限统一按 {@link WorkbenchPermCodes} 大写 code 存取，中文权限名与前端小写 key 读取时自动归一化。
 */
@RestController
@RequestMapping("/admin/enterprises/{enterpriseId}/members")
@Tag(name = "后台-企业成员", description = "成员列表/详情/角色/权限/工作台开关/移除/参与项目 + 入企申请 + 手机号邀请")
public class AdminEnterpriseMemberController {

    /** 前端头像底色 class，按成员 id 轮询取色。 */
    private static final String[] AVATAR_PALETTE = {"orange", "blue", "green", "purple"};

    private final AdminEnterpriseMemberRepository memberRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;
    private final EnterpriseJoinApplyRepository applyRepository;
    private final EnterpriseMemberProjectRepository memberProjectRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRoleRepository employeeRoleRepository;
    private final EnterpriseMemberService memberService;

    public AdminEnterpriseMemberController(AdminEnterpriseMemberRepository memberRepository,
            EnterpriseRepository enterpriseRepository, UserRepository userRepository,
            EnterpriseJoinApplyRepository applyRepository,
            EnterpriseMemberProjectRepository memberProjectRepository,
            ProjectRepository projectRepository, EmployeeRoleRepository employeeRoleRepository,
            EnterpriseMemberService memberService) {
        this.memberRepository = memberRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.userRepository = userRepository;
        this.applyRepository = applyRepository;
        this.memberProjectRepository = memberProjectRepository;
        this.projectRepository = projectRepository;
        this.employeeRoleRepository = employeeRoleRepository;
        this.memberService = memberService;
    }

    /* ==================== 成员列表 / 详情 ==================== */

    @GetMapping
    @Operation(summary = "成员列表（keyword/status/company/role 筛选 + 分页，关联 User 补姓名/手机号；role 传角色 id 时按 role_id 过滤）")
    public Result<List<Map<String, Object>>> list(@PathVariable Long enterpriseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Enterprise enterprise = requireEnterprise(enterpriseId);
        // role 为纯数字时按自定义角色 enterprise_member.role_id 过滤，'all'/super/admin/staff 走 memberRole
        Long roleId = toLong(role);
        Page<EnterpriseMember> result = memberRepository.search(enterpriseId,
                normalizeStatus(status), roleId == null ? normalizeRoleParam(role) : null, roleId,
                blankToNull(company), blankToNull(keyword), PageRequest.of(page, size));
        List<EnterpriseMember> members = result.getContent();

        Map<Long, User> users = members.isEmpty() ? Map.of()
                : userRepository.findAllById(members.stream().map(EnterpriseMember::getUserId)
                        .distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, List<EnterpriseMemberProject>> links = memberProjectIds(members);

        List<Map<String, Object>> rows = members.stream()
                .map(m -> rowView(m, enterprise, users.get(m.getUserId()),
                        links.getOrDefault(m.getId(), List.of())))
                .collect(Collectors.toList());
        return Result.success(rows, page, result.getTotalElements());
    }

    @GetMapping("/{memberId}")
    @Operation(summary = "成员详情（含邀请人、参与项目、工作台权限明细）")
    public Result<Map<String, Object>> detail(@PathVariable Long enterpriseId, @PathVariable Long memberId) {
        Enterprise enterprise = requireEnterprise(enterpriseId);
        EnterpriseMember member = requireMember(enterpriseId, memberId);
        User user = userRepository.findById(member.getUserId()).orElse(null);

        Map<String, Object> row = rowView(member, enterprise, user,
                memberProjectRepository.findByMemberId(memberId));
        row.put("invitedByName", invitedByName(member));
        row.put("permissionVersion", member.getPermissionVersion());
        row.put("permIndexes", WorkbenchPermCodes.permIndexes(member.getMemberRole()));
        row.put("projects", projectViews(memberProjectRepository.findByMemberId(memberId)));
        return Result.success(row);
    }

    /* ==================== 角色 / 权限 / 工作台开关 / 移除 ==================== */

    @PutMapping("/{memberId}/role")
    @Operation(summary = "编辑成员角色：member_role（super/admin/staff 或 OWNER/ADMIN/STAFF）与 role_id 绑定 employee_role 二选一或同时提交")
    public Result<Map<String, Object>> updateRole(@PathVariable Long enterpriseId, @PathVariable Long memberId,
            @RequestBody Map<String, Object> body) {
        Enterprise enterprise = requireEnterprise(enterpriseId);
        EnterpriseMember member = requireMember(enterpriseId, memberId);

        boolean touched = false;
        if (body.containsKey("roleId")) {
            Long roleId = toLong(body.get("roleId"));
            if (roleId == null || roleId == 0L) {
                member.setRoleId(null);                      // 传 null/0 解除角色绑定
            } else {
                EmployeeRole role = employeeRoleRepository.findById(roleId)
                        .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "绑定角色不存在"));
                member.setRoleId(role.getId());
            }
            touched = true;
        }
        String normalizedRole = normalizeRoleParam(string(body.get("role")));
        if (StringUtils.hasText(string(body.get("title")))) {
            member.setTitle(string(body.get("title")).trim());
            touched = true;
        }
        EnterpriseMember saved = StringUtils.hasText(normalizedRole)
                ? memberService.updateRole(member, normalizedRole)
                : (touched ? memberRepository.save(member) : member);

        User user = userRepository.findById(saved.getUserId()).orElse(null);
        return Result.success(rowView(saved, enterprise, user,
                memberProjectRepository.findByMemberId(saved.getId())));
    }

    @PutMapping("/{memberId}/permissions")
    @Operation(summary = "编辑成员工作台权限（permissions 支持 code/中文名/前端 key，写入 JSON 并自增 permission_version）")
    public Result<Map<String, Object>> updatePermissions(@PathVariable Long enterpriseId,
            @PathVariable Long memberId, @RequestBody Map<String, Object> body) {
        Enterprise enterprise = requireEnterprise(enterpriseId);
        EnterpriseMember member = requireMember(enterpriseId, memberId);
        applyPermissions(member, WorkbenchPermCodes.normalize(body.get("permissions")));
        User user = userRepository.findById(member.getUserId()).orElse(null);
        return Result.success(rowView(member, enterprise, user,
                memberProjectRepository.findByMemberId(memberId)));
    }

    @PutMapping("/permissions/batch")
    @Operation(summary = "批量修改成员权限（body: memberIds[] + permissions[]；仅作用于本企业成员）")
    public Result<Map<String, Object>> batchPermissions(@PathVariable Long enterpriseId,
            @RequestBody Map<String, Object> body) {
        requireEnterprise(enterpriseId);
        List<Long> memberIds = toLongList(body.get("memberIds"));
        List<String> codes = WorkbenchPermCodes.normalize(body.get("permissions"));
        List<EnterpriseMember> targets = memberRepository.findAllById(memberIds).stream()
                .filter(m -> enterpriseId.equals(m.getEnterpriseId()))
                .collect(Collectors.toList());
        targets.forEach(m -> applyPermissions(m, codes));
        return Result.success(Map.of("updated", targets.size(), "permissions", codes));
    }

    @PutMapping("/{memberId}/status")
    @Operation(summary = "成员在职状态修改（body: status=active|left / ACTIVE|LEFT / 在职|离职；OWNER 不允许置为离职）")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long enterpriseId,
            @PathVariable Long memberId, @RequestBody Map<String, String> body) {
        EnterpriseMember member = requireMember(enterpriseId, memberId);
        String raw = body.get("status");
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("status 必填（active/left）");
        }
        String normalized = raw.trim();
        if ("离职".equals(normalized) || "LEFT".equalsIgnoreCase(normalized) || "left".equals(normalized)) {
            if ("OWNER".equals(member.getMemberRole())) {
                throw new IllegalArgumentException("超级管理员不支持置为离职，请使用移除功能");
            }
            member.setStatus("LEFT");
        } else if ("在职".equals(normalized) || "ACTIVE".equalsIgnoreCase(normalized) || "active".equals(normalized)) {
            member.setStatus("ACTIVE");
        } else {
            throw new IllegalArgumentException("非法状态，仅支持 active/left");
        }
        memberRepository.save(member);
        Enterprise enterprise = requireEnterprise(enterpriseId);
        User user = userRepository.findById(member.getUserId()).orElse(null);
        return Result.success(rowView(member, enterprise, user,
                memberProjectRepository.findByMemberId(memberId)));
    }

    @PutMapping("/{memberId}/portal")
    @Operation(summary = "企业工作台访问开关（body: enabled / portalEnabled=true|false）")
    public Result<Map<String, Object>> updatePortal(@PathVariable Long enterpriseId,
            @PathVariable Long memberId, @RequestBody Map<String, Object> body) {
        Enterprise enterprise = requireEnterprise(enterpriseId);
        EnterpriseMember member = requireMember(enterpriseId, memberId);
        Object enabled = body.containsKey("enabled") ? body.get("enabled") : body.get("portalEnabled");
        member.setPortalEnabled(toBoolean(enabled));
        memberRepository.save(member);
        User user = userRepository.findById(member.getUserId()).orElse(null);
        return Result.success(rowView(member, enterprise, user,
                memberProjectRepository.findByMemberId(memberId)));
    }

    @DeleteMapping("/{memberId}")
    @Operation(summary = "移除成员（软删：status=LEFT；OWNER 不可移除）")
    public Result<Void> remove(@PathVariable Long enterpriseId, @PathVariable Long memberId) {
        EnterpriseMember member = requireMember(enterpriseId, memberId);
        memberService.removeMember(member);
        return Result.success();
    }

    /* ==================== 参与项目 / 申请 / 邀请 ==================== */

    @GetMapping("/{memberId}/projects")
    @Operation(summary = "成员参与项目列表（查 enterprise_member_project，关联 project 补名称）")
    public Result<List<Map<String, Object>>> projects(@PathVariable Long enterpriseId,
            @PathVariable Long memberId) {
        requireMember(enterpriseId, memberId);
        return Result.success(projectViews(memberProjectRepository.findByMemberId(memberId)));
    }

    @GetMapping("/applies")
    @Operation(summary = "入企申请列表（复用 EnterpriseJoinApplyRepository；status: pending/approved/rejected）")
    public Result<List<Map<String, Object>>> applies(@PathVariable Long enterpriseId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireEnterprise(enterpriseId);
        Page<EnterpriseJoinApply> result = applyRepository.search(
                enterpriseId, normalizeApplyStatus(status), PageRequest.of(page, size));
        List<Map<String, Object>> rows = result.getContent().stream()
                .map(this::applyView).collect(Collectors.toList());
        return Result.success(rows, page, result.getTotalElements());
    }

    @PostMapping("/invite")
    @Operation(summary = "手机号邀请成员（复用 EnterpriseMemberService/EnterpriseInvite 链路）")
    public Result<Map<String, Object>> invite(@PathVariable Long enterpriseId,
            @RequestBody Map<String, Object> body) {
        Enterprise enterprise = requireEnterprise(enterpriseId);
        String phone = string(body.get("phone"));
        if (phone == null || !phone.trim().matches("1\\d{10}")) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "请输入正确的11位手机号");
        }
        String inviteRole = normalizeRoleParam(string(body.get("role")));
        if (!StringUtils.hasText(inviteRole)) {
            inviteRole = "STAFF";
        }
        Long inviterId = currentAdminId();
        if (inviterId == null) {
            inviterId = enterprise.getCreateBy() != null ? enterprise.getCreateBy() : 0L;
        }
        EnterpriseInvite invite = memberService.createInvite(enterprise, inviterId, phone.trim(), inviteRole);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", invite.getId());
        data.put("phone", maskPhone(phone.trim()));
        data.put("name", invite.getName());
        data.put("role", invite.getInviteRole());
        data.put("roleName", roleNameCn(invite.getInviteRole()));
        data.put("inviteCode", invite.getInviteCode());
        data.put("link", "https://kuaima.com/invite?code=" + invite.getInviteCode());
        data.put("status", invite.getStatus());
        return Result.success(data);
    }

    /* ==================== 视图映射 ==================== */

    /** 成员 → 前端列表/详情行；字段与 EnterpriseMember.vue seedMembers 对齐。 */
    private Map<String, Object> rowView(EnterpriseMember m, Enterprise enterprise, User user,
            List<EnterpriseMemberProject> links) {
        List<String> codes = WorkbenchPermCodes.normalize(parsePermissions(m.getPermissions()));
        boolean active = !"LEFT".equals(m.getStatus());
        List<String> projectNames = projectNames(links);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("memberId", m.getId());
        row.put("userId", m.getUserId());
        row.put("name", displayName(user, m));
        row.put("phone", user != null ? maskPhone(user.getPhone()) : "");
        row.put("avatar", AVATAR_PALETTE[(int) (m.getId() % AVATAR_PALETTE.length)]);
        row.put("dept", enterprise.getCompanyName());
        row.put("position", m.getTitle() != null ? m.getTitle() : "");
        row.put("memberRole", m.getMemberRole());
        row.put("role", roleKey(m.getMemberRole()));
        row.put("roleName", roleNameCn(m.getMemberRole()));
        row.put("roleId", m.getRoleId());
        row.put("joinTime", joinTime(m));
        row.put("statusCode", m.getStatus());
        row.put("status", active ? "active" : "resigned");
        row.put("statusName", active ? "在职" : "离职");
        row.put("proj", projectNames.isEmpty() ? "全部项目" : String.join("、", projectNames));
        row.put("projects", projectViews(links));
        row.put("permissions", codes);
        row.put("wbPerms", codes.stream().map(WorkbenchPermCodes::label).collect(Collectors.toList()));
        row.put("portalEnabled", "OWNER".equals(m.getMemberRole())
                || !Boolean.FALSE.equals(m.getPortalEnabled()));
        return row;
    }

    /** 申请 → 前端申请列表行（seedApplies 字段）；实体无「申请职位/意向项目」列，job 回退申请角色文案。 */
    private Map<String, Object> applyView(EnterpriseJoinApply a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("userId", a.getUserId());
        row.put("name", a.getName());
        row.put("phone", maskPhone(a.getPhone()));
        row.put("avatar", AVATAR_PALETTE[(int) (a.getId() % AVATAR_PALETTE.length)]);
        row.put("job", roleNameCn(a.getApplyRole()));
        row.put("roleName", roleNameCn(a.getApplyRole()));
        row.put("applyRole", a.getApplyRole());
        row.put("proj", null);
        row.put("note", a.getNote());
        row.put("source", a.getSource());
        row.put("time", a.getTimestamp() != null
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(a.getTimestamp()) : "");
        row.put("statusCode", a.getStatus());
        row.put("status", applyStatusKey(a.getStatus()));
        row.put("statusName", applyStatusCn(a.getStatus()));
        return row;
    }

    private List<Map<String, Object>> projectViews(List<EnterpriseMemberProject> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = projectNameMap(links);
        List<Map<String, Object>> views = new ArrayList<>();
        for (EnterpriseMemberProject link : links) {
            boolean active = "ACTIVE".equals(link.getStatus());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", link.getId());
            item.put("projectId", link.getProjectId());
            item.put("name", names.getOrDefault(link.getProjectId(), "项目" + link.getProjectId()));
            item.put("role", link.getRoleInProject());
            item.put("joined", link.getJoinedDate() != null ? link.getJoinedDate().toString() : "");
            item.put("statusCode", link.getStatus());
            item.put("status", active ? "进行中" : "已退出");
            views.add(item);
        }
        return views;
    }

    private Map<Long, String> projectNameMap(List<EnterpriseMemberProject> links) {
        List<Long> projectIds = links.stream().map(EnterpriseMemberProject::getProjectId)
                .distinct().collect(Collectors.toList());
        return projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));
    }

    private List<String> projectNames(List<EnterpriseMemberProject> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = projectNameMap(links);
        return links.stream().map(link -> names.getOrDefault(link.getProjectId(),
                "项目" + link.getProjectId())).collect(Collectors.toList());
    }

    /* ==================== 归一化 / 工具 ==================== */

    /** ACTIVE 成员按前端筛选值映射：all→不过滤，active→ACTIVE，resigned/inactive/离职→LEFT，其余原样大写。 */
    private static String normalizeStatus(String status) {
        String value = blankToNull(status);
        if (value == null || "all".equalsIgnoreCase(value)) {
            return null;
        }
        switch (value.trim().toLowerCase()) {
            case "active": return "ACTIVE";
            case "resigned": case "inactive": case "left": return "LEFT";
            default: return value.trim().toUpperCase();
        }
    }

    /** 角色入参兼容前端 key（super/admin/staff/member）与后端 raw 值（OWNER/ADMIN/STAFF/VIEWER/...）。 */
    private static String normalizeRoleParam(String role) {
        String value = blankToNull(role);
        if (value == null || "all".equalsIgnoreCase(value)) {
            return null;
        }
        switch (value.trim().toLowerCase()) {
            case "super": case "owner": case "超级管理员": return "OWNER";
            case "admin": case "管理员": return "ADMIN";
            case "staff": case "member": case "普通成员": return "STAFF";
            default: return value.trim().toUpperCase();
        }
    }

    private static String normalizeApplyStatus(String status) {
        String value = blankToNull(status);
        if (value == null || "all".equalsIgnoreCase(value)) {
            return null;
        }
        switch (value.trim().toLowerCase()) {
            case "pending": return "PENDING";
            case "approved": case "agreed": return "AGREED";
            case "rejected": case "refused": return "REFUSED";
            default: return value.trim().toUpperCase();
        }
    }

    private static String applyStatusKey(String status) {
        switch (status == null ? "" : status) {
            case "PENDING": return "pending";
            case "AGREED": return "approved";
            case "REFUSED": return "rejected";
            default: return status == null ? "" : status.toLowerCase();
        }
    }

    private static String applyStatusCn(String status) {
        switch (status == null ? "" : status) {
            case "PENDING": return "待审批";
            case "AGREED": return "已同意";
            case "REFUSED": return "已拒绝";
            default: return status;
        }
    }

    private static String roleKey(String memberRole) {
        switch (memberRole == null ? "" : memberRole) {
            case "OWNER": return "super";
            case "ADMIN": return "admin";
            default: return "staff";
        }
    }

    private static String roleNameCn(String memberRole) {
        switch (memberRole == null ? "" : memberRole) {
            case "OWNER": return "超级管理员";
            case "ADMIN": return "管理员";
            case "VIEWER": return "查看者";
            case "RECRUITER": return "招聘专员";
            case "FINANCE": return "财务";
            default: return "普通成员";
        }
    }

    private static String displayName(User user, EnterpriseMember member) {
        if (user == null) {
            return "成员" + member.getUserId();
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname()
                : (StringUtils.hasText(user.getUsername()) ? user.getUsername()
                        : "成员" + member.getUserId());
    }

    private String invitedByName(EnterpriseMember member) {
        if (member.getInvitedBy() == null) {
            return "";
        }
        return memberRepository.findById(member.getInvitedBy())
                .map(inviter -> userRepository.findById(inviter.getUserId())
                        .map(u -> displayName(u, inviter)).orElse(""))
                .orElse("");
    }

    private static String joinTime(EnterpriseMember member) {
        if (member.getTimestamp() != null) {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(member.getTimestamp());
        }
        return member.getDate() != null ? member.getDate().toString() : "";
    }

    private static List<String> parsePermissions(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            String trimmed = raw.trim();
            if (trimmed.startsWith("[")) {
                return JSON.parseArray(trimmed, String.class);
            }
            return List.of(trimmed.split(","));
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /** 写权限 JSON 并自增 permission_version（batch/单条共用）。 */
    private void applyPermissions(EnterpriseMember member, List<String> codes) {
        member.setPermissions(JSON.toJSONString(codes));
        Integer version = member.getPermissionVersion();
        member.setPermissionVersion(version == null ? 1 : version + 1);
        memberRepository.save(member);
    }

    private Map<Long, List<EnterpriseMemberProject>> memberProjectIds(List<EnterpriseMember> members) {
        if (members.isEmpty()) {
            return Map.of();
        }
        return memberProjectRepository.findByMemberIdIn(
                        members.stream().map(EnterpriseMember::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.groupingBy(EnterpriseMemberProject::getMemberId));
    }

    private Enterprise requireEnterprise(Long enterpriseId) {
        return enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.NOT_FOUND, "企业不存在"));
    }

    private EnterpriseMember requireMember(Long enterpriseId, Long memberId) {
        EnterpriseMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "成员不存在"));
        if (!enterpriseId.equals(member.getEnterpriseId())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "该成员不属于当前企业");
        }
        return member;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<Long> toLongList(Object value) {
        List<Long> ids = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Long id = toLong(item);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 当前登录管理员 id（邀请人审计字段用）。 */
    private Long currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof LoginUser user ? user.id() : null;
    }
}
