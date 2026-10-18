package com.kuaima.app.controller.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.repository.EnterpriseMemberRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseRepository;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.security.dto.WechatLoginDto;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.security.util.JwtUtil;
import com.kuaima.app.service.SmsService;
import com.kuaima.app.wechat.service.WechatService;
import com.kuaima.app.wechat.service.WechatService.WechatUserInfo;

import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "微信登录、JWT 鉴权、短信验证码")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WechatService wechatService;
    private final SmsService smsService;
    private final EnterpriseMemberRepository enterpriseMemberRepository;
    private final EnterpriseRepository enterpriseRepository;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          WechatService wechatService,
                          SmsService smsService) {
        this(userRepository, passwordEncoder, jwtUtil, wechatService, smsService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          WechatService wechatService,
                          SmsService smsService,
                          EnterpriseMemberRepository enterpriseMemberRepository,
                          EnterpriseRepository enterpriseRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.wechatService = wechatService;
        this.smsService = smsService;
        this.enterpriseMemberRepository = enterpriseMemberRepository;
        this.enterpriseRepository = enterpriseRepository;
    }

    @GetMapping("/enterprise-contexts")
    @Operation(summary = "查询当前用户可进入的企业")
    public Result<List<Map<String, Object>>> enterpriseContexts(Authentication authentication) {
        Long uid = currentUserId(authentication);
        if (uid == null || enterpriseMemberRepository == null || enterpriseRepository == null) {
            return Result.success(List.of());
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (EnterpriseMember member : enterpriseMemberRepository.findByUserIdAndStatus(uid, "ACTIVE")) {
            if (!"OWNER".equals(member.getMemberRole()) && Boolean.FALSE.equals(member.getPortalEnabled())) continue;
            Enterprise enterprise = enterpriseRepository.findById(member.getEnterpriseId()).orElse(null);
            if (enterprise == null || !"ACTIVE".equals(enterprise.getStatus())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("enterpriseId", enterprise.getId());
            item.put("enterpriseName", enterprise.getCompanyName());
            item.put("memberRole", member.getMemberRole());
            item.put("permissions", member.getPermissions() == null ? List.of()
                    : com.alibaba.fastjson2.JSON.parseArray(member.getPermissions()));
            result.add(item);
        }
        return Result.success(result);
    }

    public record EnterpriseContextRequest(Long enterpriseId) {}

    @PostMapping("/enterprise-context/switch")
    @Transactional(readOnly = true)
    @Operation(summary = "切换进入指定企业菜单")
    public Result<Map<String, Object>> switchEnterpriseContext(@RequestBody EnterpriseContextRequest request,
                                                                Authentication authentication) {
        Long uid = currentUserId(authentication);
        if (uid == null || request == null || request.enterpriseId() == null) {
            throw new IllegalArgumentException("enterpriseId 不能为空");
        }
        EnterpriseMember member = enterpriseMemberRepository.findByEnterpriseIdAndUserId(request.enterpriseId(), uid)
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .filter(m -> "OWNER".equals(m.getMemberRole()) || !Boolean.FALSE.equals(m.getPortalEnabled()))
                .orElseThrow(() -> new com.kuaima.app.common.ForbiddenBusinessException("当前用户未获准进入该企业"));
        Enterprise enterprise = enterpriseRepository.findById(member.getEnterpriseId())
                .filter(e -> "ACTIVE".equals(e.getStatus()))
                .orElseThrow(() -> new com.kuaima.app.common.ForbiddenBusinessException("企业不存在或已停用"));
        User user = userRepository.findById(uid).orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        String token = jwtUtil.generateAccessToken(user.getUsername(), UserRole.BOSS, uid, uid,
                enterprise.getId(), member.getMemberRole());
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", token);
        data.put("enterpriseId", enterprise.getId());
        data.put("enterpriseName", enterprise.getCompanyName());
        data.put("memberRole", member.getMemberRole());
        data.put("permissions", member.getPermissions() == null ? List.of()
                : com.alibaba.fastjson2.JSON.parseArray(member.getPermissions()));
        return Result.success(data);
    }

    private Map<String, Object> buildTokenResponse(User user) {
        return buildTokenResponse(user, user.getRole());
    }

    private Map<String, Object> buildTokenResponse(User user, String sessionRole) {
        Long enterpriseId = null;
        String memberRole = null;
        if (enterpriseMemberRepository != null) {
            java.util.Optional<EnterpriseMember> membership = enterpriseMemberRepository
                    .findFirstByUserIdAndStatusOrderByIdAsc(user.getId(), "ACTIVE");
            if (membership != null && membership.isPresent()) {
                enterpriseId = membership.get().getEnterpriseId();
                memberRole = membership.get().getMemberRole();
            }
        }
        String accessToken = enterpriseMemberRepository == null
                ? jwtUtil.generateAccessToken(user.getUsername(), sessionRole, user.getId())
                : jwtUtil.generateAccessToken(user.getUsername(), sessionRole, user.getId(),
                        null, enterpriseId, memberRole);
        Map<String, Object> data = new HashMap<>();
        data.put("needPhoneNumber", false);
        data.put("accessToken", accessToken);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("role", sessionRole);
        data.put("phone", user.getPhone());
        data.put("certStatus", user.getCertStatus());
        data.put("enterpriseId", enterpriseId);
        data.put("memberRole", memberRole);
        return data;
    }

    @Operation(summary = "微信小程序登录", description = "首次用 code 换取 openid；新用户返回短期 registrationToken，之后用 registrationToken + phoneCode 完成注册，避免重复使用微信 code")
    @PostMapping("/wechat/login")
    @Transactional
    public Result<Map<String, Object>> wechatLogin(@RequestBody WechatLoginDto dto) {
        if (dto == null || (!StringUtils.hasText(dto.getCode())
                && !StringUtils.hasText(dto.getRegistrationToken()))) {
            return Result.error(400, "微信 code 或注册凭证不能为空");
        }
        // 身份：缺省按员工(USER)处理，非法值报错
        String role = StringUtils.hasText(dto.getRole()) ? dto.getRole() : UserRole.USER;
        if (!UserRole.isValid(role)) {
            return Result.error(400, "身份不合法：仅支持 BOSS(老板) / USER(员工)");
        }
        boolean registrationContinuation = StringUtils.hasText(dto.getRegistrationToken());
        final WechatUserInfo info;
        if (registrationContinuation) {
            if (!StringUtils.hasText(dto.getPhoneCode())) {
                return Result.error(400, "手机号授权 code 不能为空");
            }
            try {
                String openid = jwtUtil.getWechatRegistrationOpenid(dto.getRegistrationToken());
                info = new WechatUserInfo(openid, null, null, null);
            } catch (RuntimeException e) {
                return Result.error(400, "注册凭证无效或已过期，请重新登录");
            }
        } else {
            info = wechatService.loginByCode(dto.getCode());
        }
        if (info.openid() == null) {
            return Result.error(401, "微信登录失败：未获取到 openid");
        }

        // 新老用户只能由 openid 查询结果判断，不能用手机号是否为空判断。
        User user = userRepository.findByOpenid(info.openid()).orElse(null);
        if (user == null) {
            if (!StringUtils.hasText(dto.getPhoneCode())) {
                Map<String, Object> data = new HashMap<>();
                data.put("needPhoneNumber", true);
                data.put("registrationToken", jwtUtil.generateWechatRegistrationToken(info.openid()));
                return Result.success(data);
            }

            final String phone;
            try {
                phone = wechatService.getPhoneNumber(dto.getPhoneCode());
            } catch (RuntimeException e) {
                return Result.error(400, "手机号授权失败");
            }
            if (!StringUtils.hasText(phone)) {
                return Result.error(400, "手机号授权失败");
            }

            User authorizedEmployee = findUnboundAuthorizedEmployee(phone, role);
            if (authorizedEmployee != null) {
                // 老板先创建授权员工，员工之后首次用自己微信手机号登录时绑定既有子账号。
                authorizedEmployee.setOpenid(info.openid());
                if (!StringUtils.hasText(authorizedEmployee.getAvatar()) && StringUtils.hasText(info.avatar())) {
                    authorizedEmployee.setAvatar(info.avatar());
                }
                user = userRepository.save(authorizedEmployee);
                return Result.success(buildTokenResponse(user, role));
            }

            User newUser = new User();
            newUser.setUsername(phone);
            newUser.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            // role 是本次登录端；用户持久化身份始终从零工开始，企业认证通过后再额外获得老板资格。
            newUser.setRole(UserRole.USER);
            newUser.setOpenid(info.openid());
            String rawNickname = info.nickname() != null ? info.nickname() : dto.getNickname();
            // 新用户尚未完成企业认证，业务身份统一按零工处理；登录端 role 只代表本次会话端。
            newUser.setNickname(StringUtils.hasText(rawNickname) ? rawNickname : nextDefaultNickname());
            newUser.setAvatar(info.avatar() != null ? info.avatar() : dto.getAvatar());
            newUser.setPhone(phone);
            // 新用户状态默认「正常」；User 实体无默认值，漏设会导致后台按状态筛选时查不到该用户。
            newUser.setStatus("正常");
            user = userRepository.save(newUser);
        } else if (registrationContinuation) {
            // 注册流程的第二步必须真实校验手机号授权 code，不能只凭注册凭证登录已有账号。
            try {
                wechatService.getPhoneNumber(dto.getPhoneCode());
            } catch (RuntimeException e) {
                return Result.error(400, "手机号授权失败");
            }
        }

        // role 只表示本次登录端，不再覆盖用户的历史业务身份字段。
        return Result.success(buildTokenResponse(user, role));
    }

    /**
     * 手机号授权登录时优先匹配老板端授权员工子账号。
     * 仅允许绑定未绑定过微信且状态正常的子账号，避免复用他人微信或已冻结账号。
     */
    private User findUnboundAuthorizedEmployee(String phone, String sessionRole) {
        if (!UserRole.BOSS.equals(sessionRole)) return null;
        List<User> candidates = userRepository.findUnboundActiveSubAccountsByPhone(phone);
        if (candidates.size() > 1) {
            throw new IllegalStateException("该手机号关联多个老板授权账号，请联系老板确认后重新授权");
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /** 为尚未完成企业认证的新用户生成零工默认昵称，不能按登录端 role 生成老板昵称。 */
    private synchronized String nextDefaultNickname() {
        String prefix = "零工";
        int max = userRepository.findByRole(UserRole.USER).stream()
                .map(User::getNickname)
                .filter(StringUtils::hasText)
                .filter(n -> n.startsWith(prefix))
                .map(n -> n.substring(prefix.length()))
                .filter(s -> s.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max().orElse(0);
        return prefix + String.format("%02d", max + 1);
    }

    /**
     * 退出登录：POST /auth/logout
     * 当前使用无状态 JWT，服务端确认请求已通过鉴权；客户端收到响应后清除本地登录态。
     */
    @Operation(summary = "退出登录", description = "需携带有效 accessToken；服务端确认退出，客户端应清除本地 Token 和用户缓存")
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    /**
     * 当前登录用户完整资料：GET /auth/me
     * 从 JWT 解析出 uid 后反查 User 实体返回完整 profile
     */
    @Operation(summary = "获取当前用户", description = "从 JWT 解析出 uid 后反查 User 实体，返回当前登录用户的完整资料")
    @GetMapping("/me")
    public Result<User> me(Authentication authentication) {
        Long uid = currentUserId(authentication);
        if (uid == null) {
            return Result.success(null);
        }
        return Result.success(userRepository.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + uid)));
    }

    /**
     * 切换身份：POST /auth/switch-role?role=BOSS|USER
     * 更新当前用户 role，并签发包含新 role 的新 JWT
     */
    @Operation(summary = "切换用户身份", description = "更新当前用户 role，并签发包含新 role 的新 JWT accessToken")
    @PostMapping("/switch-role")
    @Transactional
    public Result<Map<String, Object>> switchRole(@RequestParam String role,
                                                    Authentication authentication) {
        if (!UserRole.isValid(role)) {
            return Result.error(400, "身份不合法：仅支持 BOSS(老板) / USER(员工)");
        }
        Long uid = currentUserId(authentication);
        if (uid == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + uid));
        return Result.success(buildTokenResponse(user, role));
    }

    /**
     * 注销账号（软删除）：POST /auth/cancel?userId={id}&reason={reason}
     * 仅将 User.status 置为「注销」，不物理删除，保留历史数据
     */
    @Operation(summary = "账号注销", description = "软删除：将 User.status 置为「注销」并记录注销原因，不物理删除，保留历史数据")
    @PostMapping("/cancel")
    @Transactional
    public Result<User> cancel(@RequestParam Long userId,
                               @RequestParam(required = false) String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
        user.setStatus("注销");
        if (StringUtils.hasText(reason)) {
            user.setRemark(StringUtils.hasText(user.getRemark())
                    ? user.getRemark() + "；注销原因：" + reason
                    : "注销原因：" + reason);
        }
        return Result.success(userRepository.save(user));
    }

    /**
     * 发送短信验证码：GET|POST /auth/sms/send?phone=13800000000
     * 需登录，60秒重发和每日上限限制，验证码5分钟有效
     */
    @Operation(summary = "发送短信验证码", description = "需登录，向指定手机号发送6位验证码；60秒重发、每日上限限制，5分钟有效")
    @GetMapping("/sms/send")
    @PostMapping("/sms/send")
    public Result<Void> sendSmsCode(@RequestParam String phone) {
        String err = smsService.sendCode(phone);
        if (err != null) {
            return Result.error(400, err);
        }
        return Result.success();
    }

    /**
     * 校验短信验证码：POST /auth/sms/verify?phone=13800000000&code=123456
     * 无需登录，校验通过后删除验证码
     */
    @Operation(summary = "校验短信验证码", description = "无需登录，校验手机号+验证码，通过后验证码自动失效")
    @PostMapping("/sms/verify")
    public Result<Map<String, Object>> verifySmsCode(@RequestParam String phone,
                                                      @RequestParam String code) {
        boolean ok = smsService.verifyCode(phone, code);
        if (!ok) {
            return Result.error(400, "验证码错误或已过期");
        }
        // 当前业务规则：手机号验证通过即视为个人认证通过。
        var users = userRepository.findByPhone(phone);
        for (var user : users) {
            boolean changed = false;
            if (!CertificationStatus.APPROVED.equals(user.getRealnameStatus())) {
                user.setRealnameStatus(CertificationStatus.APPROVED);
                changed = true;
            }
            if (!"已通过".equals(user.getCertStatus())) {
                user.setCertStatus("已通过");
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("phone", phone);
        data.put("verified", true);
        return Result.success(data);
    }

    /** 从 SecurityContext 提取当前登录用户 ID，缺失返回 null */
    private Long currentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return loginUser.id();
        }
        return null;
    }
}
