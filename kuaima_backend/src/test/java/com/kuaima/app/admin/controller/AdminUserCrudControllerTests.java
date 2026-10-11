package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.security.model.LoginUser;

class AdminUserCrudControllerTests {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateShouldPersistAllowLoginAndReceiveNotifications() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        AdminUser operator = admin(1L, "SUPER_ADMIN");
        AdminUser target = admin(5L, "ADMIN");
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(operator));
        when(repo.findById(5L)).thenReturn(java.util.Optional.of(target));
        when(repo.save(target)).thenReturn(target);
        loginAs(1L);

        AdminUser patch = new AdminUser();
        patch.setAllowLogin(false);
        patch.setReceiveNotifications(false);

        AdminUser saved = new AdminUserCrudController(repo, mock(PasswordEncoder.class))
                .update(5L, patch).getData();

        assertFalse(saved.getAllowLogin());
        assertFalse(saved.getReceiveNotifications());
    }

    @Test
    void updateShouldRejectDisablingSuperAdminLogin() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        AdminUser operator = admin(1L, "SUPER_ADMIN");
        AdminUser target = admin(5L, "SUPER_ADMIN");
        when(repo.findById(1L)).thenReturn(java.util.Optional.of(operator));
        when(repo.findById(5L)).thenReturn(java.util.Optional.of(target));
        loginAs(1L);

        AdminUser patch = new AdminUser();
        patch.setAllowLogin(false);

        var result = new AdminUserCrudController(repo, mock(PasswordEncoder.class))
                .update(5L, patch);

        assertEquals(403, result.getCode());
        assertEquals("超级管理员账号不可禁止登录", result.getMessage());
        assertTrue(target.getAllowLogin());
    }

    private AdminUser admin(Long id, String role) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setRole(role);
        admin.setStatus("启用");
        admin.setAllowLogin(true);
        admin.setReceiveNotifications(true);
        return admin;
    }

    private void loginAs(Long adminId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(adminId, "operator", "ADMIN_ADMIN"), null));
    }
}
