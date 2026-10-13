package com.kuaima.app.admin.controller;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson2.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.admin.dto.AdminLoginDto;
import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.interceptor.AdminLogInterceptor;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.admin.service.AdminLogService;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.security.util.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

/**
 * 后台管理员认证接口（账号密码登录）
 */
@RestController
@RequestMapping("/admin/auth")
@Tag(name = "后台-登录", description = "管理员登录鉴权")
public class AdminAuthController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AdminLogService logService;

    public AdminAuthController(AdminUserRepository adminUserRepository,
                               PasswordEncoder passwordEncoder,
                               JwtUtil jwtUtil,
                               AdminLogService logService) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.logService = logService;
    }

    @Operation(summary = "管理员登录", description = "账号密码登录，返回 accessToken（JWT role 前缀 ADMIN_）、adminId、username、name、role（SUPER_ADMIN/ADMIN/EDITOR/VIEWER）、permissions（权限树 JSON）；登录成功自动更新 lastLoginTime")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody AdminLoginDto dto, HttpServletRequest request) {
        String ip = AdminLogInterceptor.clientIp(request);
        String username = dto == null ? null : dto.getUsername();
        String password = dto == null ? null : dto.getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            logService.record(username, null, "登录", "后台登录", ip, "失败",
                    loginDetail(username, "用户名或密码为空"));
            return Result.error(400, "用户名和密码不能为空");
        }
        AdminUser admin = adminUserRepository.findByUsername(username).orElse(null);
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            logService.record(username, null, "登录", "后台登录", ip, "失败",
                    loginDetail(username, "用户名或密码错误"));
            return Result.error(400, "用户名或密码错误");
        }
        if ("禁用".equals(admin.getStatus())) {
            logService.record(username, admin.getId(), "登录", "后台登录", ip, "失败",
                    loginDetail(username, "账号已被禁用"));
            return Result.error(403, "账号已被禁用");
        }
        if (!"SUPER_ADMIN".equals(admin.getRole()) && Boolean.FALSE.equals(admin.getAllowLogin())) {
            logService.record(username, admin.getId(), "登录", "后台登录", ip, "失败",
                    loginDetail(username, "账号已被禁止登录"));
            return Result.error(403, "该账号已被禁止登录");
        }
        admin.setLastLoginTime(LocalDateTime.now());
        adminUserRepository.save(admin);

        // 生成 JWT，role 前缀加 ADMIN_ 区分
        String token = jwtUtil.generateAccessToken(admin.getUsername(), "ADMIN_" + admin.getRole(), admin.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", token);
        data.put("adminId", admin.getId());
        data.put("username", admin.getUsername());
        data.put("name", admin.getName());
        data.put("role", admin.getRole());
        data.put("permissions", admin.getPermissions());

        logService.record(admin.getUsername(), admin.getId(), "登录", "后台登录", ip, "成功",
                loginDetail(username, "登录成功"));
        return Result.success(data);
    }

    @Operation(summary = "获取当前管理员", description = "返回当前登录管理员信息（含角色、权限树 permissions、创建者 createdBy 等），供前端路由守卫/菜单过滤/按钮鉴权使用")
    private String loginDetail(String username, String result) {
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", "***");
        return "POST /admin/auth/login | body=" + body.toJSONString() + " | ex=" + result;
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof LoginUser lu)) {
            return Result.error(401, "未登录或登录已过期");
        }
        AdminUser admin = adminUserRepository.findById(lu.id()).orElse(null);
        if (admin == null) {
            return Result.error(404, "管理员账号不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("adminId", admin.getId());
        data.put("username", admin.getUsername());
        data.put("name", admin.getName());
        data.put("role", admin.getRole());
        data.put("permissions", admin.getPermissions());
        data.put("createdBy", admin.getCreatedBy());
        data.put("email", admin.getEmail());
        return Result.success(data);
    }
}
