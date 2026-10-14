package com.kuaima.app.controller.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.OssUploadResult;
import com.kuaima.app.domain.boss.service.BossOrderService;
import com.kuaima.app.domain.jobcategory.service.JobCategoryService;
import com.kuaima.app.domain.user.service.CertificationService;
import com.kuaima.app.security.model.LoginUser;

class BossOrderMediaControllerTests {

    @Test
    void uploadOrderMedia_shouldSaveRecruitmentObjectKeyAndReturnSignedPreview() {
        OssStorageService storage = mock(OssStorageService.class);
        String objectKey = "insurance/recruitment/2026/10/14/work.png";
        when(storage.uploadResult(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("insurance/recruitment"), org.mockito.ArgumentMatchers.eq(".png")))
                .thenReturn(new OssUploadResult(objectKey, "https://oss.example.com/raw.png", "work.png"));
        when(storage.playableUrl(objectKey)).thenReturn("https://oss.example.com/signed.png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "work.png", "application/octet-stream", new byte[] {1});

        var result = controller(storage)
                .uploadOrderMedia(file, "image", bossAuthentication()).getData();

        assertEquals(objectKey, result.objectKey());
        assertEquals("https://oss.example.com/signed.png", result.url());
    }

    @Test
    void uploadOrderMedia_shouldRejectUnsupportedVideo() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "work.avi", "video/x-msvideo", new byte[] {1});

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> controller(mock(OssStorageService.class))
                        .uploadOrderMedia(file, "video", bossAuthentication()));

        assertEquals("仅支持 MP4 视频", error.getMessage());
    }

    private BossController controller(OssStorageService storage) {
        return new BossController(mock(BossOrderService.class), mock(JobCategoryService.class),
                mock(CertificationService.class), null, null, null, null, null, null, null,
                null, storage);
    }

    private Authentication bossAuthentication() {
        return new UsernamePasswordAuthenticationToken(new LoginUser(7L, "boss", "BOSS"), null);
    }
}
