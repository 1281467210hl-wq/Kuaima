package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

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

    @Test
    void passwordRuleShouldRequireLettersDigitsAndLength() {
        assertEquals("密码至少8位", AdminUserCrudController.passwordProblem("abc123"));
        assertEquals("密码必须同时包含字母和数字", AdminUserCrudController.passwordProblem("abcdefgh"));
        assertEquals("密码必须同时包含字母和数字", AdminUserCrudController.passwordProblem("12345678"));
        assertNull(AdminUserCrudController.passwordProblem("Kuaima2026"));
    }

    @Test
    void changeOwnPasswordShouldVerifyOldPasswordAndSaveNewHash() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AdminUser me = adminWithHash(3L, "old-hash");
        when(repo.findById(3L)).thenReturn(Optional.of(me));
        when(encoder.matches("Old12345", "old-hash")).thenReturn(true);
        when(encoder.encode("New12345")).thenReturn("new-hash");
        loginAs(3L);

        var result = new AdminUserCrudController(repo, encoder)
                .changeOwnPassword(Map.of("oldPassword", "Old12345", "newPassword", "New12345"));

        assertEquals(200, result.getCode());
        assertEquals("new-hash", me.getPassword());
        verify(repo).save(me);
    }

    @Test
    void changeOwnPasswordShouldRejectWrongOldPassword() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.findById(3L)).thenReturn(Optional.of(adminWithHash(3L, "old-hash")));
        when(encoder.matches("wrong123", "old-hash")).thenReturn(false);
        loginAs(3L);

        var result = new AdminUserCrudController(repo, encoder)
                .changeOwnPassword(Map.of("oldPassword", "wrong123", "newPassword", "New12345"));

        assertEquals(400, result.getCode());
        assertEquals("原密码不正确", result.getMessage());
        verify(repo, never()).save(any());
    }

    @Test
    void resetPasswordShouldRequireExplicitStrongPassword() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        AdminUser superAdmin = adminWithHash(1L, "hash");
        superAdmin.setRole("SUPER_ADMIN");
        when(repo.findById(1L)).thenReturn(Optional.of(superAdmin));
        when(repo.findById(9L)).thenReturn(Optional.of(adminWithHash(9L, "hash")));
        loginAs(1L);
        AdminUserCrudController controller = new AdminUserCrudController(repo, mock(PasswordEncoder.class));

        // 不传 newPassword 时不再默认重置为 123456
        assertEquals(400, controller.resetPassword(9L, Map.of()).getCode());
        assertEquals(400, controller.resetPassword(9L, Map.of("newPassword", "123456")).getCode());
        verify(repo, never()).save(any());
    }

    private AdminUser adminWithHash(Long id, String passwordHash) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setUsername("admin" + id);
        admin.setRole("ADMIN");
        admin.setPassword(passwordHash);
        return admin;
    }

    private void loginAs(Long adminId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(adminId, "operator", "ADMIN_ADMIN"), null));
    }
}
