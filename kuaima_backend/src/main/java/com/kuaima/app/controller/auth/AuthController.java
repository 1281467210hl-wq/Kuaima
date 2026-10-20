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
        if (user != null && !isActiveAccount(user)) {
            // 与短信登录一致：注销 / 冻结 / 禁用的账号不能再签发登录态
            return Result.error(403, "该账号已注销或被冻结，如有疑问请联系客服");
        }
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
     * 注销账号（软删除）：POST /auth/cancel?reason={reason}
     * 只能注销当前登录账号；仅将 User.status 置为「注销」，不物理删除，保留历史数据。
     * 注销后旧 token 由 JwtAuthenticationFilter 拦截，微信 / 短信登录也会拒绝。
     */
    @Operation(summary = "账号注销", description = "只能注销当前登录账号（JWT 识别）；userId 参数仅为兼容旧客户端，与当前登录账号不一致时拒绝。软删除：status 置为「注销」并记录原因")
    @PostMapping("/cancel")
    @Transactional
    public Result<Map<String, Object>> cancel(@RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String reason,
                                              Authentication authentication) {
        Long currentId = currentUserId(authentication);
        if (currentId == null) return Result.error(401, "未登录或登录已过期");
        // 原接口直接按请求参数 userId 注销，任何已登录用户都能注销他人账号
        if (userId != null && !userId.equals(currentId)) {
            return Result.error(403, "只能注销当前登录的账号");
        }
        User user = userRepository.findById(currentId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + currentId));
        user.setStatus("注销");
        if (StringUtils.hasText(reason)) {
            String trimmed = reason.trim();
            String safeReason = trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
            user.setRemark(StringUtils.hasText(user.getRemark())
                    ? user.getRemark() + "；注销原因：" + safeReason
                    : "注销原因：" + safeReason);
        }
        userRepository.save(user);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", currentId);
        data.put("status", "注销");
        return Result.success(data);
    }

    // ==================== App 短信验证码登录 ====================

    /** 匿名发送登录验证码的单 IP 每日上限：手机号维度已有 60 秒重发和每日上限，这里再防止换号刷短信。 */
    static final int SMS_LOGIN_SEND_LIMIT_PER_IP = 30;
    private final java.util.concurrent.ConcurrentHashMap<String, int[]> smsLoginSendByIp = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String smsLoginSendDay = java.time.LocalDate.now().toString();

    public record SmsLoginRequest(String phone, String code, String role) {}

    @Operation(summary = "发送登录验证码", description = "无需登录；App 短信登录用。手机号 60 秒重发、每日上限，并按 IP 限制每日发送次数；固定验证码模式下仅测试手机号可用")
    @PostMapping("/sms/login/send")
    public Result<Void> sendSmsLoginCode(@RequestParam String phone, jakarta.servlet.http.HttpServletRequest request) {
        String normalized = phone == null ? "" : phone.trim();
        if (!smsService.isLoginAllowed(normalized)) {
            return Result.error(403, "当前为测试阶段，仅允许测试手机号登录");
        }
        if (!allowSmsLoginSend(com.kuaima.app.admin.interceptor.AdminLogInterceptor.clientIp(request))) {
            return Result.error(429, "验证码发送过于频繁，请稍后再试");
        }
        String err = smsService.sendCode(normalized);
        return err == null ? Result.success() : Result.error(400, err);
    }

    /**
     * 短信验证码登录：账号匹配与令牌签发和微信登录保持一致。
     * 注意：短信校验只证明手机号归属，不能视为实名认证，不修改实名状态。
     */
    @Operation(summary = "短信验证码登录", description = "body：{phone, code, role(BOSS/USER)}；同一手机号已有账号则登录该账号，否则按零工身份注册新账号")
    @PostMapping("/sms/login")
    @Transactional
    public Result<Map<String, Object>> smsLogin(@RequestBody SmsLoginRequest body) {
        String phone = body == null || body.phone() == null ? "" : body.phone().trim();
        String code = body == null || body.code() == null ? "" : body.code().trim();
        String role = body != null && StringUtils.hasText(body.role()) ? body.role().trim() : UserRole.USER;
        if (!UserRole.isValid(role)) {
            return Result.error(400, "身份不合法：仅支持 BOSS(老板) / USER(员工)");
        }
        if (!phone.matches("^1[3-9]\\d{9}$")) return Result.error(400, "手机号格式不正确");
        if (!StringUtils.hasText(code)) return Result.error(400, "请输入验证码");
        if (!smsService.isLoginAllowed(phone)) {
            return Result.error(403, "当前为测试阶段，仅允许测试手机号登录");
        }
        SmsService.VerifyResult verify = smsService.verifyCodeResult(phone, code);
        if (verify != SmsService.VerifyResult.OK) {
            return Result.error(400, verify == SmsService.VerifyResult.INVALID
                    ? "验证码错误" : "验证码已失效，请重新获取");
        }

        List<User> samePhone = userRepository.findByPhone(phone);
        List<User> activeMain = samePhone.stream()
                .filter(u -> u.getParentUserId() == null && isActiveAccount(u)).toList();
        if (activeMain.size() > 1) {
            return Result.error(409, "该手机号关联多个账号，请联系客服处理");
        }
        User user = activeMain.isEmpty() ? null : activeMain.get(0);
        if (user == null && UserRole.BOSS.equals(role)) {
            // 老板端：手机号只对应老板授权的员工子账号时，登录该子账号（与微信登录绑定子账号的规则一致）
            List<User> subAccounts = samePhone.stream()
                    .filter(u -> u.getParentUserId() != null && isActiveAccount(u)).toList();
            if (subAccounts.size() > 1) {
                return Result.error(409, "该手机号关联多个老板授权账号，请联系老板确认后重新授权");
            }
            user = subAccounts.isEmpty() ? null : subAccounts.get(0);
        }
        if (user == null && samePhone.stream().anyMatch(u -> !isActiveAccount(u))) {
            return Result.error(403, "该手机号对应的账号已注销或被禁用，请联系客服");
        }
        if (user == null) {
            User newUser = new User();
            newUser.setUsername(phone);
            newUser.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            // 与微信注册一致：持久化身份从零工开始，role 只代表本次登录端
            newUser.setRole(UserRole.USER);
            newUser.setNickname(nextDefaultNickname());
            newUser.setPhone(phone);
            newUser.setStatus("正常");
            user = userRepository.save(newUser);
        }
        return Result.success(buildTokenResponse(user, role));
    }

    private static boolean isActiveAccount(User user) {
        return !StringUtils.hasText(user.getStatus()) || "正常".equals(user.getStatus());
    }

    private synchronized boolean allowSmsLoginSend(String ip) {
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(smsLoginSendDay)) {
            smsLoginSendByIp.clear();
            smsLoginSendDay = today;
        }
        int[] count = smsLoginSendByIp.computeIfAbsent(ip == null ? "unknown" : ip, k -> new int[1]);
        if (count[0] >= SMS_LOGIN_SEND_LIMIT_PER_IP) return false;
        count[0]++;
        return true;
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
