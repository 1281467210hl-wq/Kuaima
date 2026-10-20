package com.kuaima.app.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.security.util.JwtUtil;

class JwtAuthenticationFilterTests {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectDisabledAdminTokenImmediately() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        AdminUser disabled = new AdminUser();
        disabled.setId(5L);
        disabled.setStatus("禁用");
        disabled.setAllowLogin(true);
        stubToken(jwtUtil, "token", 5L, "ADMIN_EDITOR");
        when(admins.findById(5L)).thenReturn(Optional.of(disabled));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(jwtUtil, admins).doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("{\"code\":401,\"message\":\"账号已被禁用，请重新登录\"}", response.getContentAsString());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
    }

    @Test
    void shouldAcceptEnabledAdminToken() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        AdminUser enabled = new AdminUser();
        enabled.setId(5L);
        enabled.setStatus("启用");
        enabled.setAllowLogin(true);
        stubToken(jwtUtil, "token", 5L, "ADMIN_EDITOR");
        when(admins.findById(5L)).thenReturn(Optional.of(enabled));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(jwtUtil, admins).doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(5L, ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).id());
    }

    @Test
    void shouldNeverBlockSuperAdminEvenIfAllowLoginDisabled() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        AdminUser superAdmin = new AdminUser();
        superAdmin.setId(1L);
        superAdmin.setRole("SUPER_ADMIN");
        superAdmin.setStatus("启用");
        superAdmin.setAllowLogin(false);
        stubToken(jwtUtil, "token", 1L, "ADMIN_SUPER_ADMIN");
        when(admins.findById(1L)).thenReturn(Optional.of(superAdmin));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(jwtUtil, admins).doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldRejectCancelledAppUserTokenImmediately() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository users = mock(UserRepository.class);
        stubToken(jwtUtil, "token", 9L, "USER");
        when(users.findStatusById(9L)).thenReturn(Optional.of("注销"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(jwtUtil, mock(AdminUserRepository.class), users).doFilter(request, response, chain);

        // 注销后旧 token 立即失效，不能继续访问业务接口
        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
    }

    @Test
    void shouldAllowNormalAppUserToken() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository users = mock(UserRepository.class);
        stubToken(jwtUtil, "token", 10L, "BOSS");
        when(users.findStatusById(10L)).thenReturn(Optional.of("正常"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockFilterChain chain = new MockFilterChain();

        new JwtAuthenticationFilter(jwtUtil, mock(AdminUserRepository.class), users)
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldRejectFrozenAppUserToken() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserRepository users = mock(UserRepository.class);
        stubToken(jwtUtil, "token", 11L, "USER");
        // 后台冻结、老板删除授权员工都会把账号置为「冻结」
        when(users.findStatusById(11L)).thenReturn(Optional.of("冻结"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwtUtil, mock(AdminUserRepository.class), users)
                .doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    private void stubToken(JwtUtil jwtUtil, String token, Long adminId, String role) {
        when(jwtUtil.isAccessToken(token)).thenReturn(true);
        when(jwtUtil.getUsername(token)).thenReturn("editor");
        when(jwtUtil.getRole(token)).thenReturn(role);
        when(jwtUtil.getUserId(token)).thenReturn(adminId);
        when(jwtUtil.getAccountGroupOwnerId(token)).thenReturn(adminId);
        when(jwtUtil.getEnterpriseId(token)).thenReturn(null);
        when(jwtUtil.getMemberRole(token)).thenReturn(null);
    }
}
