package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.kuaima.app.admin.entity.AdminSetting;
import com.kuaima.app.admin.repository.AdminSettingRepository;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.service.SmsService;

class AdminSettingControllerTests {

    @Test
    void byCategory_shouldSignPlatformLogoWithoutChangingStoredValue() {
        AdminSettingRepository repository = mock(AdminSettingRepository.class);
        OssStorageService storage = mock(OssStorageService.class);
        AdminSetting setting = new AdminSetting();
        setting.setSettingKey("platform.logo");
        setting.setSettingValue("insurance/admin/2026/10/13/logo.png");
        when(repository.findByCategory("platform")).thenReturn(List.of(setting));
        when(storage.inlinePreviewUrl(setting.getSettingValue())).thenReturn("https://example.com/signed.png");
        AdminSettingController controller = controller(repository, storage);

        var result = controller.byCategory("platform").getData().get(0);

        assertEquals("insurance/admin/2026/10/13/logo.png", result.getSettingValue());
        assertEquals("https://example.com/signed.png", result.getDisplayValue());
    }

    @Test
    void byCategory_shouldKeepExternalLogoUnchanged() {
        AdminSettingRepository repository = mock(AdminSettingRepository.class);
        AdminSetting setting = new AdminSetting();
        setting.setSettingKey("platform.logo");
        setting.setSettingValue("https://cdn.example.com/logo.png");
        when(repository.findByCategory("platform")).thenReturn(List.of(setting));

        var result = controller(repository, mock(OssStorageService.class))
                .byCategory("platform").getData().get(0);

        assertEquals("https://cdn.example.com/logo.png", result.getSettingValue());
        assertNull(result.getDisplayValue());
    }

    private AdminSettingController controller(AdminSettingRepository repository, OssStorageService storage) {
        return new AdminSettingController(repository, mock(SmsService.class), mock(MessageService.class),
                mock(UserRepository.class), storage);
    }
}
