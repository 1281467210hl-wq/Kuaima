package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kuaima.app.admin.dto.AdminLoginDto;
import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.admin.service.AdminLogService;
import com.kuaima.app.security.util.JwtUtil;

class AdminAuthControllerAllowLoginTests {

    @Test
    void loginShouldRejectAdminWithAllowLoginDisabled() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AdminUser admin = new AdminUser();
        admin.setId(5L);
        admin.setUsername("editor");
        admin.setPassword("encoded");
        admin.setStatus("启用");
        admin.setAllowLogin(false);
        when(repo.findByUsername("editor")).thenReturn(Optional.of(admin));
        when(encoder.matches("password", "encoded")).thenReturn(true);

        AdminLoginDto dto = new AdminLoginDto();
        dto.setUsername("editor");
        dto.setPassword("password");
        var result = new AdminAuthController(repo, encoder, mock(JwtUtil.class), mock(AdminLogService.class))
                .login(dto, new MockHttpServletRequest());

        assertEquals(403, result.getCode());
        assertEquals("该账号已被禁止登录", result.getMessage());
    }

    @Test
    void loginShouldStillAllowSuperAdminWithAllowLoginDisabled() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        AdminUser superAdmin = new AdminUser();
        superAdmin.setId(1L);
        superAdmin.setUsername("admin");
        superAdmin.setPassword("encoded");
        superAdmin.setRole("SUPER_ADMIN");
        superAdmin.setStatus("启用");
        superAdmin.setAllowLogin(false);
        when(repo.findByUsername("admin")).thenReturn(java.util.Optional.of(superAdmin));
        when(repo.save(superAdmin)).thenReturn(superAdmin);
        when(encoder.matches("admin123", "encoded")).thenReturn(true);
        when(jwtUtil.generateAccessToken("admin", "ADMIN_SUPER_ADMIN", 1L)).thenReturn("token");

        AdminLoginDto dto = new AdminLoginDto();
        dto.setUsername("admin");
        dto.setPassword("admin123");
        var result = new AdminAuthController(repo, encoder, jwtUtil, mock(com.kuaima.app.admin.service.AdminLogService.class))
                .login(dto, new org.springframework.mock.web.MockHttpServletRequest());

        assertEquals(200, result.getCode());
        assertEquals("token", result.getData().get("accessToken"));
    }
}
