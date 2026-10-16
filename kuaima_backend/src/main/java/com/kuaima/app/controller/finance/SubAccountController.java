package com.kuaima.app.controller.finance;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
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

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.repository.EnterpriseMemberRepository;
import com.kuaima.app.domain.enterprise.service.EnterpriseContextService;
import com.kuaima.app.domain.subaccount.entity.SubAccount;
import com.kuaima.app.domain.subaccount.repository.SubAccountRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/boss/sub-accounts")
@Tag(name = "老板-授权员工", description = "基于当前JWT老板的数据隔离、创建、删除和角色维护")
public class SubAccountController {
    private static final Set<String> ROLES = Set.of("ADMIN", "FINANCE", "OPERATOR");

    private final SubAccountRepository subAccountRepository;
    private final UserRepository userRepository;
    private final EnterpriseMemberRepository enterpriseMemberRepository;
    private final EnterpriseContextService enterpriseContextService;
    private final Environment environment;
    private final String devVerificationCode;

    public SubAccountController(SubAccountRepository subAccountRepository,
                                UserRepository userRepository,
                                EnterpriseMemberRepository enterpriseMemberRepository,
                                EnterpriseContextService enterpriseContextService,
                                Environment environment,
                                @Value("${aliyun.sms.mock-code:}") String devVerificationCode) {
        this.subAccountRepository = subAccountRepository;
        this.userRepository = userRepository;
        this.enterpriseMemberRepository = enterpriseMemberRepository;
        this.enterpriseContextService = enterpriseContextService;
        this.environment = environment;
        this.devVerificationCode = devVerificationCode;
    }

    @GetMapping
    @Operation(summary = "授权员工列表", description = "parentId仅兼容旧调用，实际只查询当前JWT老板；传入其他老板ID返回403")
    public Result<List<SubAccount>> listSubAccounts(@RequestParam(required = false) Long parentId,
                                                     Authentication authentication) {
        Long bossId = requireBossId(authentication);
        rejectOtherParent(parentId, bossId);
        List<SubAccount> accounts = subAccountRepository.findByParentId(bossId);
        Map<Long, User> users = userRepository.findAllById(accounts.stream().map(SubAccount::getUserId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        accounts.forEach(account -> account.setPhone(users.containsKey(account.getUserId())
                ? users.get(account.getUserId()).getPhone() : null));
        return Result.success(accounts);
    }

    @PostMapping
    @Transactional
    @Operation(summary = "开通授权员工", description = "请求memberId、code、role；只绑定当前企业已有成员，不创建新用户；老板和企业身份均取当前JWT")
    public Result<SubAccount> createSubAccount(@RequestBody Map<String, Object> body,
                                               Authentication authentication) {
        Long bossId = requireBossId(authentication);
        rejectOtherParent(toLong(body.get("parentId")), bossId);
        Long memberId = toLong(body.get("memberId"));
        String code = text(body.get("code"));
        String role = normalizeRole(text(body.get("role")));
        if (memberId == null) throw new IllegalArgumentException("memberId不能为空");
        verifyCode(code);

        EnterpriseContextService.Context context = enterpriseContextService.require(authentication, "MEMBER_WRITE");
        EnterpriseMember member = enterpriseMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("企业成员不存在"));
        if (!member.getEnterpriseId().equals(context.enterprise().getId())
                || !"ACTIVE".equals(member.getStatus())) {
            throw new ForbiddenBusinessException("无权授权该企业成员");
        }
        if (bossId.equals(member.getUserId())) {
            throw new IllegalArgumentException("不能授权当前登录老板自己");
        }
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("企业成员对应的系统用户不存在"));
        if (subAccountRepository.existsByParentIdAndUserId(bossId, user.getId())) {
            throw new IllegalStateException("该员工已授权");
        }
        user.setSubRole(role);
        userRepository.save(user);
        List<String> permissionCodes = permissionCodes(body.get("permissionCodes"), body.get("menuCodes"));
        member.setPermissions(com.alibaba.fastjson2.JSON.toJSONString(permissionCodes));
        member.setPortalEnabled(true);
        member.setAuthorizedBy(bossId);
        member.setAuthorizedAt(java.time.LocalDateTime.now());
        member.setPermissionVersion(member.getPermissionVersion() == null ? 1 : member.getPermissionVersion() + 1);
        enterpriseMemberRepository.save(member);
        SubAccount sub = new SubAccount();
        sub.setParentId(bossId);
        sub.setUserId(user.getId());
        sub.setPhone(user.getPhone());
        sub.setRole(role);
        sub.setStatus("ACTIVE");
        return Result.success(subAccountRepository.save(sub));
    }

    @PutMapping("/{id}/permissions")
    @Transactional
    @Operation(summary = "修改员工菜单权限", description = "只修改当前企业已有成员权限，不创建系统用户")
    public Result<EnterpriseMember> updatePermissions(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body,
                                                       Authentication authentication) {
        Long bossId = requireBossId(authentication);
        EnterpriseContextService.Context context = enterpriseContextService.require(authentication, "MEMBER_WRITE");
        EnterpriseMember member = enterpriseMemberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("企业成员不存在"));
        if (bossId.equals(member.getUserId())) {
            throw new ForbiddenBusinessException("不能修改当前登录老板自己的权限");
        }
        if (!context.enterprise().getId().equals(member.getEnterpriseId())) {
            throw new ForbiddenBusinessException("该成员不属于当前企业，请从当前企业成员列表中选择");
        }
        member.setPermissions(com.alibaba.fastjson2.JSON.toJSONString(permissionCodes(body.get("permissionCodes"), body.get("menuCodes"))));
        member.setPortalEnabled(true);
        member.setAuthorizedBy(bossId);
        member.setAuthorizedAt(java.time.LocalDateTime.now());
        member.setPermissionVersion(member.getPermissionVersion() == null ? 1 : member.getPermissionVersion() + 1);
        return Result.success(enterpriseMemberRepository.save(member));
    }

    @DeleteMapping("/{id}/permissions")
    @Transactional
    @Operation(summary = "撤销员工企业端权限")
    public Result<Void> revokePermissions(@PathVariable Long id, Authentication authentication) {
        Long bossId = requireBossId(authentication);
        EnterpriseContextService.Context context = enterpriseContextService.require(authentication, "MEMBER_WRITE");
        EnterpriseMember member = enterpriseMemberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("企业成员不存在"));
        if (bossId.equals(member.getUserId())) {
            throw new ForbiddenBusinessException("不能撤销当前登录老板自己的权限");
        }
        if (!context.enterprise().getId().equals(member.getEnterpriseId())) {
            throw new ForbiddenBusinessException("该成员不属于当前企业，请从当前企业成员列表中选择");
        }
        member.setPortalEnabled(false);
        member.setPermissions("[]");
        member.setPermissionVersion(member.getPermissionVersion() == null ? 1 : member.getPermissionVersion() + 1);
        enterpriseMemberRepository.save(member);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "删除授权员工", description = "仅允许当前JWT老板删除自己的子账号；越权返回403")
    public Result<Void> deleteSubAccount(@PathVariable Long id, Authentication authentication) {
        Long bossId = requireBossId(authentication);
        SubAccount sub = owned(id, bossId);
        userRepository.findById(sub.getUserId()).ifPresent(user -> {
            user.setStatus("冻结");
            userRepository.save(user);
        });
        subAccountRepository.delete(sub);
        return Result.success();
    }

    @PutMapping("/{id}/role")
    @Transactional
    @Operation(summary = "修改授权员工角色", description = "仅允许当前JWT老板修改自己的子账号；role支持ADMIN、FINANCE、OPERATOR")
    public Result<SubAccount> updateRole(@PathVariable Long id, @RequestParam String role,
                                         Authentication authentication) {
        Long bossId = requireBossId(authentication);
        SubAccount sub = owned(id, bossId);
        String normalizedRole = normalizeRole(role);
        sub.setRole(normalizedRole);
        userRepository.findById(sub.getUserId()).ifPresent(user -> {
            user.setSubRole(normalizedRole);
            userRepository.save(user);
        });
        return Result.success(subAccountRepository.save(sub));
    }

    private void verifyCode(String code) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        boolean mockEnabled = Boolean.parseBoolean(environment.getProperty("aliyun.sms.mock-enabled", "false"));
        if (!dev || !mockEnabled || !StringUtils.hasText(devVerificationCode)) {
            throw new IllegalStateException("短信验证码服务尚未配置");
        }
        if (!devVerificationCode.equals(code)) throw new IllegalArgumentException("验证码不正确");
    }

    private SubAccount owned(Long id, Long bossId) {
        return subAccountRepository.findByIdAndParentId(id, bossId).orElseThrow(() ->
                subAccountRepository.existsById(id)
                        ? new ForbiddenBusinessException("无权操作其他老板的子账号")
                        : new IllegalArgumentException("子账号不存在: " + id));
    }

    private Long requireBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                && user.id() != null && UserRole.BOSS.equals(user.role())) return user.id();
        throw new ForbiddenBusinessException("当前登录账号不是有效的老板账号");
    }

    private void rejectOtherParent(Long parentId, Long bossId) {
        if (parentId != null && !bossId.equals(parentId)) throw new ForbiddenBusinessException("无权访问其他老板的子账号");
    }

    private String normalizeRole(String role) {
        String normalized = StringUtils.hasText(role) ? role.trim().toUpperCase() : "OPERATOR";
        if (!ROLES.contains(normalized)) throw new IllegalArgumentException("不支持的子账号角色: " + normalized);
        return normalized;
    }

    private Long toLong(Object value) { return value == null ? null : Long.valueOf(value.toString()); }
    private String text(Object value) { return value == null ? "" : value.toString().trim(); }

    private List<String> permissionCodes(Object permissions, Object menus) {
        Object source = permissions != null ? permissions : menus;
        if (!(source instanceof List<?> list)) return List.of("HOME_VIEW");
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String code = text(item).toUpperCase();
            code = switch (code) {
                case "HOME" -> "HOME_VIEW";
                case "ORDER", "RECRUIT_ORDER" -> "ORDER_VIEW";
                case "MESSAGE" -> "MESSAGE_VIEW";
                case "WORKBENCH" -> "WORKBENCH_VIEW";
                case "ATTENDANCE" -> "ATTENDANCE_VIEW";
                case "SETTLEMENT" -> "SETTLEMENT_VIEW";
                case "REPORT" -> "REPORT_VIEW";
                default -> code;
            };
            if (!code.isBlank() && code.length() <= 60 && code.matches("[A-Z0-9_]+")) result.add(code);
        }
        return result.stream().distinct().toList();
    }
}
