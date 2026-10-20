package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.entity.Agreement;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.admin.repository.AgreementRepository;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.OssUploadResult;
import com.kuaima.app.security.model.LoginUser;

class AdminAgreementControllerTests {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listShouldReturnAgreementViews() {
        AgreementRepository agreements = mock(AgreementRepository.class);
        Agreement agreement = agreement(1L, "INPUT");
        when(agreements.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(agreement), PageRequest.of(0, 10), 1));

        var data = controller(agreements, mock(AdminUserRepository.class)).list(null, null, 0, 10).getData();

        assertEquals(1, ((List<?>) data.get("records")).size());
        assertEquals(1L, data.get("total"));
    }

    @Test
    void createShouldSaveUploadAccount() {
        AgreementRepository agreements = mock(AgreementRepository.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        AdminUser admin = new AdminUser();
        admin.setId(9L);
        admin.setUsername("admin_zhangwei");
        admin.setName("张伟");
        when(admins.findById(9L)).thenReturn(Optional.of(admin));
        when(agreements.save(any())).thenAnswer(invocation -> {
            Agreement saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        loginAs(9L);
        var request = new AdminAgreementController.AgreementRequest(
                " 快马日结用户服务协议 ", "用户服务协议", "INPUT", "协议正文", null, null, null, true);

        var data = controller(agreements, admins).create(request, null).getData();

        assertEquals("快马日结用户服务协议", data.getString("name"));
        assertEquals("AGR001", data.getString("agreementNo"));
        assertEquals("张伟", data.getString("uploadAccount"));
    }

    @Test
    void uploadShouldRejectUnsupportedFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("agreement.exe");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller(mock(AgreementRepository.class), mock(AdminUserRepository.class)).upload(file));

        assertEquals("仅支持 PDF / DOC / DOCX / TXT 格式文件", error.getMessage());
    }

    @Test
    void uploadShouldReturnObjectKeyAndMetadata() {
        AgreementRepository agreements = mock(AgreementRepository.class);
        AdminUserRepository admins = mock(AdminUserRepository.class);
        OssStorageService storage = mock(OssStorageService.class);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(2048L);
        when(file.getOriginalFilename()).thenReturn("privacy.pdf");
        when(storage.uploadResult(file, "insurance/agreement", ".pdf"))
                .thenReturn(new OssUploadResult("insurance/agreement/a.pdf", "http://oss/a.pdf", "a.pdf"));

        var data = new AdminAgreementController(agreements, admins, storage).upload(file).getData();

        assertEquals("insurance/agreement/a.pdf", data.get("objectKey"));
        assertEquals("privacy.pdf", data.get("fileName"));
        assertEquals(2048L, data.get("fileSize"));
    }

    @Test
    void downloadUrlShouldUseAgreementFileName() {
        AgreementRepository agreements = mock(AgreementRepository.class);
        Agreement agreement = agreement(5L, "FILE");
        agreement.setFileObjectKey("insurance/agreement/uuid.pdf");
        agreement.setFileName("快马日结隐私协议.pdf");
        when(agreements.findById(5L)).thenReturn(Optional.of(agreement));
        OssStorageService storage = mock(OssStorageService.class);
        when(storage.downloadUrl("insurance/agreement/uuid.pdf", "快马日结隐私协议.pdf"))
                .thenReturn("https://oss.example.com/signed.pdf");

        var data = new AdminAgreementController(agreements, mock(AdminUserRepository.class), storage)
                .downloadUrl(5L).getData();

        assertEquals("https://oss.example.com/signed.pdf", data.get("url"));
        assertEquals("快马日结隐私协议.pdf", data.get("fileName"));
    }

    @Test
    void publicLatestShouldReturnEnabledInputAgreement() {
        AgreementRepository agreements = mock(AgreementRepository.class);
        Agreement agreement = agreement(2L, "INPUT");
        agreement.setName("用户服务协议");
        when(agreements.findFirstByTypeAndEnabledOrderByUpdatedAtDescIdDesc("用户服务协议", true))
                .thenReturn(Optional.of(agreement));

        var data = new PublicAgreementController(agreements, mock(OssStorageService.class))
                .latest("用户服务协议").getData();

        assertEquals("用户服务协议", data.name());
        assertEquals("INPUT", data.contentMode());
        assertEquals("协议正文", data.content());
    }

    private AdminAgreementController controller(AgreementRepository agreements, AdminUserRepository admins) {
        return new AdminAgreementController(agreements, admins, mock(OssStorageService.class));
    }

    private Agreement agreement(Long id, String mode) {
        Agreement agreement = new Agreement();
        agreement.setId(id);
        agreement.setName("快马日结用户服务协议");
        agreement.setType("用户服务协议");
        agreement.setContentMode(mode);
        agreement.setContent("INPUT".equals(mode) ? "协议正文" : null);
        agreement.setFileObjectKey("FILE".equals(mode) ? "insurance/agreement/a.pdf" : null);
        agreement.setFileName("FILE".equals(mode) ? "a.pdf" : null);
        agreement.setFileSize("FILE".equals(mode) ? 1024L : null);
        agreement.setEnabled(true);
        agreement.setUploadAccount("admin_zhangwei");
        return agreement;
    }

    private void loginAs(Long adminId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(adminId, "admin_zhangwei", "ADMIN"), null));
    }
}
