package com.kuaima.app.controller.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;

import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.OssUploadResult;
import com.kuaima.app.common.service.FfmpegVideoTranscoder;
import com.kuaima.app.common.service.RecruitmentMediaPreviewService;
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

    @Test
    void uploadOrderMedia_shouldReturnPlayableMp4Preview() {
        OssStorageService storage = mock(OssStorageService.class);
        FfmpegVideoTranscoder transcoder = mock(FfmpegVideoTranscoder.class);
        String objectKey = "insurance/recruitment/2026/10/15/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.mp4";
        when(storage.uploadResult(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("insurance/recruitment"), org.mockito.ArgumentMatchers.eq(".mp4")))
                .thenReturn(new OssUploadResult(objectKey, "https://oss.example.com/raw.mp4", "work.mp4"));
        RecruitmentMediaPreviewService previewService = mock(RecruitmentMediaPreviewService.class);
        when(previewService.previewUrl(objectKey))
                .thenReturn("https://ke.taifang.xyz/api/public/recruitment-media?objectKey=x");
        MockMultipartFile file = new MockMultipartFile(
                "file", "work.mp4", "application/octet-stream", new byte[] {1});

        var result = controller(storage, transcoder, previewService)
                .uploadOrderMedia(file, "video", bossAuthentication()).getData();

        assertEquals(objectKey, result.objectKey());
        assertEquals("https://ke.taifang.xyz/api/public/recruitment-media?objectKey=x", result.url());
        verify(previewService).previewUrl(objectKey);
    }

    @Test
    void uploadOrderMedia_shouldTranscodeHevcToWechatCompatibleH264() throws Exception {
        OssStorageService storage = mock(OssStorageService.class);
        FfmpegVideoTranscoder transcoder = mock(FfmpegVideoTranscoder.class);
        String objectKey = "insurance/recruitment/2026/10/15/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.mp4";
        Path source = java.nio.file.Files.createTempFile("hevc-source", ".mp4");
        Path transcoded = java.nio.file.Files.createTempFile("hevc-target", ".mp4");
        java.nio.file.Files.writeString(source, "prefix hvc1 suffix");
        when(transcoder.isHevc(any(Path.class))).thenReturn(true);
        when(transcoder.transcodeToH264(any(Path.class))).thenReturn(transcoded);
        when(storage.uploadResult(transcoded, "video/mp4", "insurance/recruitment", ".mp4"))
                .thenReturn(new OssUploadResult(objectKey, "https://oss.example.com/raw.mp4", "work-h264.mp4"));
        RecruitmentMediaPreviewService previewService = mock(RecruitmentMediaPreviewService.class);
        when(previewService.previewUrl(objectKey))
                .thenReturn("https://ke.taifang.xyz/api/public/recruitment-media?objectKey=x");
        MockMultipartFile file = new MockMultipartFile(
                "file", "work.mp4", "video/mp4", java.nio.file.Files.readAllBytes(source));

        var result = controller(storage, transcoder, previewService)
                .uploadOrderMedia(file, "video", bossAuthentication()).getData();

        assertEquals(objectKey, result.objectKey());
        assertEquals("https://ke.taifang.xyz/api/public/recruitment-media?objectKey=x", result.url());
        verify(transcoder).transcodeToH264(any(Path.class));
        java.nio.file.Files.deleteIfExists(source);
        java.nio.file.Files.deleteIfExists(transcoded);
    }

    private BossController controller(OssStorageService storage) {
        return controller(storage, null);
    }

    private BossController controller(OssStorageService storage, FfmpegVideoTranscoder transcoder) {
        return controller(storage, transcoder, null);
    }

    private BossController controller(OssStorageService storage, FfmpegVideoTranscoder transcoder,
            RecruitmentMediaPreviewService previewService) {
        return new BossController(mock(BossOrderService.class), mock(JobCategoryService.class),
                mock(CertificationService.class), null, null, null, null, null, null, null,
                null, storage, transcoder, previewService);
    }

    private Authentication bossAuthentication() {
        return new UsernamePasswordAuthenticationToken(new LoginUser(7L, "boss", "BOSS"), null);
    }
}
