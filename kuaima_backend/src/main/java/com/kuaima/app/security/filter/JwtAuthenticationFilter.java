package com.kuaima.app.security.filter;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.security.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AdminUserRepository adminUserRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AdminUserRepository adminUserRepository) {
        this.jwtUtil = jwtUtil;
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.isAccessToken(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                String username = jwtUtil.getUsername(token);
                String role = jwtUtil.getRole(token);
                Long uid = jwtUtil.getUserId(token);
                if (role != null && role.startsWith("ADMIN_") && isBlockedAdmin(uid)) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"账号已被禁用，请重新登录\"}");
                    return;
                }
                LoginUser principal = new LoginUser(uid, username, role,
                        jwtUtil.getAccountGroupOwnerId(token), jwtUtil.getEnterpriseId(token),
                        jwtUtil.getMemberRole(token));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 后台 token 每次请求核对账号实时状态：禁用或关闭允许登录后立即失效，实现踢下线。 */
    private boolean isBlockedAdmin(Long adminId) {
        if (adminId == null) return true;
        AdminUser admin = adminUserRepository.findById(adminId).orElse(null);
        // 超级管理员是系统兜底账号，永不因 allow_login 被拦截，避免整站后台锁死。
        if (admin != null && "SUPER_ADMIN".equals(admin.getRole())) return false;
        return admin == null || "禁用".equals(admin.getStatus()) || Boolean.FALSE.equals(admin.getAllowLogin());
    }
}
