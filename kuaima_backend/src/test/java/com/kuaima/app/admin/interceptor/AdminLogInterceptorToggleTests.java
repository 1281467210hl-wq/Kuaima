package com.kuaima.app.admin.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.kuaima.app.admin.repository.MessageTemplateRepository;
import com.kuaima.app.admin.service.AdminLogService;
import com.kuaima.app.domain.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AdminLogInterceptorToggleTests {

    @Test
    void academyToggleShouldBeRecordedAsEditWithoutQueryingMessageTemplate() {
        AdminLogService logs = mock(AdminLogService.class);
        MessageTemplateRepository templates = mock(MessageTemplateRepository.class);
        var interceptor = new AdminLogInterceptor(logs, templates, mock(UserRepository.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/admin/academy/simulate-videos/2/toggle");
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(templates, never()).findById(any());
        verify(logs).recordAsync(eq("system"), eq(null), eq("编辑"),
                eq("后台操作 #2"), eq("127.0.0.1"), eq("成功"), any());
        verify(logs, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void webOperationTypesShouldFollowButtonSemantics() {
        assertType("POST", "/admin/notices", "新建");
        assertType("POST", "/admin/join-applies/2/approve", "编辑");
        assertType("DELETE", "/admin/notices/2", "删除");
        assertType("GET", "/admin/jobs/2", "查看");
    }

    @Test
    void loadingEditFormDataShouldNotBeRecordedAsView() {
        AdminLogService logs = mock(AdminLogService.class);
        var interceptor = new AdminLogInterceptor(logs, mock(MessageTemplateRepository.class), mock(UserRepository.class));
        HttpServletRequest request = request("GET", "/admin/admin-users/5");
        when(request.getHeader("Referer")).thenReturn("http://localhost:5173/admin/admin-user/form?mode=edit&id=5");
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(logs, never()).recordAsync(any(), any(), any(), any(), any(), any(), any());
    }

    private void assertType(String method, String uri, String expectedType) {
        AdminLogService logs = mock(AdminLogService.class);
        var interceptor = new AdminLogInterceptor(logs, mock(MessageTemplateRepository.class), mock(UserRepository.class));
        HttpServletRequest request = request(method, uri);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(logs).recordAsync(eq("system"), eq(null), eq(expectedType),
                any(), any(), eq("成功"), any());
    }

    private HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn(method);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }
}
