package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

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

    @Test
    void save_shouldDeriveCategoryFromKeySoLogoReloadsAfterRefresh() {
        AdminSettingRepository repository = mock(AdminSettingRepository.class);
        OssStorageService storage = mock(OssStorageService.class);
        when(repository.findById("platform.logo")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.inlinePreviewUrl("insurance/admin/2026/10/17/logo.png")).thenReturn("https://example.com/signed.png");
        AdminSetting request = new AdminSetting();
        request.setSettingValue("insurance/admin/2026/10/17/logo.png");
        request.setDescription("平台Logo");

        AdminSetting saved = controller(repository, storage).save("platform.logo", request).getData();

        assertEquals("platform", saved.getCategory());
        assertEquals("https://example.com/signed.png", saved.getDisplayValue());
    }

    @Test
    void save_shouldKeepExistingCategoryAndDescription() {
        AdminSettingRepository repository = mock(AdminSettingRepository.class);
        AdminSetting existing = new AdminSetting();
        existing.setSettingKey("rules.feeRate");
        existing.setCategory("security");
        existing.setDescription("平台服务费率");
        when(repository.findById("rules.feeRate")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AdminSetting request = new AdminSetting();
        request.setSettingValue("5");

        AdminSetting saved = controller(repository, mock(OssStorageService.class)).save("rules.feeRate", request).getData();

        assertEquals("security", saved.getCategory());
        assertEquals("平台服务费率", saved.getDescription());
    }

    @Test
    void byCategory_shouldIncludeLegacyRowsSavedWithoutCategory() {
        AdminSettingRepository repository = mock(AdminSettingRepository.class);
        OssStorageService storage = mock(OssStorageService.class);
        AdminSetting name = new AdminSetting();
        name.setSettingKey("platform.name");
        name.setCategory("platform");
        AdminSetting legacyLogo = new AdminSetting();
        legacyLogo.setSettingKey("platform.logo");
        legacyLogo.setSettingValue("insurance/admin/2026/10/13/logo.png");
        when(repository.findByCategory("platform")).thenReturn(List.of(name));
        when(repository.findByCategoryIsNullAndSettingKeyStartingWith("platform.")).thenReturn(List.of(legacyLogo));
        when(storage.inlinePreviewUrl(legacyLogo.getSettingValue())).thenReturn("https://example.com/signed.png");

        var result = controller(repository, storage).byCategory("platform").getData();

        assertEquals(2, result.size());
        assertEquals("https://example.com/signed.png", result.get(1).getDisplayValue());
    }

    private AdminSettingController controller(AdminSettingRepository repository, OssStorageService storage) {
        return new AdminSettingController(repository, mock(SmsService.class), mock(MessageService.class),
                mock(UserRepository.class), storage);
    }
}
