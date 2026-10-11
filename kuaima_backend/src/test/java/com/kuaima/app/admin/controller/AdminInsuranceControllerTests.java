package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.domain.insurance.entity.InsuranceProduct;
import com.kuaima.app.domain.insurance.repository.InsuranceProductRepository;
import com.kuaima.app.security.model.LoginUser;

class AdminInsuranceControllerTests {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listShouldAttachTierCountAndCreatorName() {
        InsuranceProductRepository products = mock(InsuranceProductRepository.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        InsuranceProduct product = product(1L, "众安保险", "ZA-33W", true,
                "[{\"amount\":11},{\"amount\":33},{\"amount\":55},{\"amount\":88}]");
        product.setCreateBy(9L);
        when(products.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 10), 1));
        AdminUser creator = new AdminUser();
        creator.setId(9L);
        creator.setName("张伟");
        when(admins.findById(9L)).thenReturn(Optional.of(creator));

        var row = new AdminInsuranceController(products, admins, mock(com.kuaima.app.common.service.OssStorageService.class)).list("众安", "已启用", 0, 10)
                .getData().getContent().get(0);

        assertEquals("众安保险", row.getString("carrier"));
        assertEquals(4, row.getIntValue("tierCount"));
        assertEquals("张伟", row.getString("creatorName"));
    }

    @Test
    void toggleShouldFlipEnabledState() {
        InsuranceProductRepository products = mock(InsuranceProductRepository.class);
        InsuranceProduct product = product(3L, "太平洋保险", "TP-88W", true, "[]");
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(products.save(product)).thenReturn(product);

        var data = new AdminInsuranceController(products, mock(AdminUserRepository.class), mock(com.kuaima.app.common.service.OssStorageService.class))
                .toggle(3L).getData();

        assertFalse(data.getBooleanValue("enabled"));
    }

    @Test
    void createShouldRejectDuplicatedCode() {
        InsuranceProductRepository products = mock(InsuranceProductRepository.class);
        when(products.existsByCode("ZA-33W")).thenReturn(true);
        var request = new AdminInsuranceController.InsuranceProductRequest(
                "众安保险", "ZA-33W", true, List.of(Map.of("amount", 33)), null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AdminInsuranceController(products, mock(AdminUserRepository.class), mock(com.kuaima.app.common.service.OssStorageService.class)).create(request));

        assertEquals("产品编码已存在: ZA-33W", error.getMessage());
    }

    @Test
    void createShouldSaveTiersAndCreator() {
        InsuranceProductRepository products = mock(InsuranceProductRepository.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        when(products.existsByCode("ZA-11W")).thenReturn(false);
        when(products.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        loginAs(9L);
        var request = new AdminInsuranceController.InsuranceProductRequest(
                "众安保险", " ZA-11W ", true, List.of(Map.of("amount", 11, "death", 10, "medical", 1, "points", 1)), null);

        var data = new AdminInsuranceController(products, admins, mock(com.kuaima.app.common.service.OssStorageService.class)).create(request).getData();

        assertEquals("ZA-11W", data.getString("code"));
        assertEquals(9L, data.getLongValue("createBy"));
        assertEquals(1, data.getJSONArray("tiers").size());
        assertTrue(data.getBooleanValue("enabled"));
    }

    private InsuranceProduct product(Long id, String carrier, String code, Boolean enabled, String tiers) {
        InsuranceProduct product = new InsuranceProduct();
        product.setId(id);
        product.setCarrier(carrier);
        product.setCode(code);
        product.setEnabled(enabled);
        product.setTiers(tiers);
        return product;
    }

    private void loginAs(Long adminId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(adminId, "operator", "ADMIN_ADMIN"), null));
    }
}
