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
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.repository.UserRepository;
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
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, AdminUserRepository adminUserRepository) {
        this(jwtUtil, adminUserRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil, AdminUserRepository adminUserRepository,
                                   UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.adminUserRepository = adminUserRepository;
        this.userRepository = userRepository;
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
                if (UserRole.isValid(role) && isBlockedAppUser(uid)) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"账号已注销或被冻结，如有疑问请联系客服\"}");
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

    /**
     * 小程序 / App token 同样核对账号实时状态：注销、冻结（含老板删除的授权员工）、禁用后立即失效。
     * 否则注销后旧 token 仍可继续使用，注销形同虚设。
     */
    private boolean isBlockedAppUser(Long userId) {
        if (userRepository == null) return false;
        if (userId == null) return true;
        return userRepository.findStatusById(userId)
                .map(status -> status != null && !status.isBlank() && !"正常".equals(status))
                .orElse(true);
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
