package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kuaima.app.admin.entity.AdminLog;
import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminLogRepository;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.security.model.LoginUser;

class AdminLogControllerTests {

    @AfterEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldDisplayAdminNameAndKeepAccountAsSupplementaryField() {
        AdminLogRepository logs = mock(AdminLogRepository.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        AdminLog log = new AdminLog();
        log.setId(1L);
        log.setOperator("admin");
        log.setOperatorId(5L);
        log.setType("登录");
        log.setCreateTime(LocalDateTime.of(2026, 10, 13, 22, 10));
        AdminUser admin = new AdminUser();
        admin.setId(5L);
        admin.setUsername("admin");
        admin.setName("张管理员");
        loginAsSuperAdmin(5L);
        when(logs.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(admins.findAllById(any())).thenReturn(List.of(admin));

        var row = new AdminLogController(logs, admins).list(null, null, null, null, 0, 20)
                .getData().getContent().get(0);

        assertEquals("张管理员", row.getString("operator"));
        assertEquals("admin", row.getString("operatorAccount"));
    }

    @Test
    void list_shouldFallbackToStoredOperatorWhenAdminIsMissing() {
        AdminLogRepository logs = mock(AdminLogRepository.class);
        AdminLog log = new AdminLog();
        log.setId(2L);
        log.setOperator("unknown");
        log.setOperatorId(99L);
        loginAsSuperAdmin(1L);
        when(logs.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        var row = new AdminLogController(logs, mock(AdminUserRepository.class))
                .list(null, null, null, null, 0, 20).getData().getContent().get(0);

        assertEquals("unknown", row.getString("operator"));
        assertEquals("unknown", row.getString("operatorAccount"));
    }

    @Test
    void list_shouldMapLegacyWriteTypesToEdit() {
        AdminLogRepository logs = mock(AdminLogRepository.class);
        AdminLog log = new AdminLog();
        log.setId(3L);
        log.setOperator("admin");
        log.setType("审核");
        loginAsSuperAdmin(1L);
        when(logs.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        var row = new AdminLogController(logs, mock(AdminUserRepository.class))
                .list(null, null, null, null, 0, 20).getData().getContent().get(0);

        assertEquals("编辑", row.getString("type"));
        assertEquals("审核", row.getString("originalType"));
    }

    @Test
    void list_shouldPushKeywordAndFullDateRangeToDatabase() {
        AdminLogRepository logs = mock(AdminLogRepository.class);
        when(logs.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        loginAsSuperAdmin(1L);

        new AdminLogController(logs, mock(AdminUserRepository.class))
                .list(null, "张管理员", LocalDate.of(2026, 10, 13),
                        LocalDate.of(2026, 10, 13), 0, 20);

        ArgumentCaptor<LocalDateTime> startTime = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endTime = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(logs).search(isNull(), isNull(), eq("张管理员"), startTime.capture(),
                endTime.capture(), any(Pageable.class));
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 0), startTime.getValue());
        assertEquals(LocalDateTime.of(2026, 10, 14, 0, 0), endTime.getValue());
    }

    private void loginAsSuperAdmin(Long id) {
        LoginUser user = new LoginUser(id, "admin", "ADMIN_SUPER_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null));
    }
}
