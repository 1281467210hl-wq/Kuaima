package com.kuaima.app.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import com.kuaima.app.common.service.OssStorageService.ObjectStream;

class RecruitmentMediaPreviewServiceTests {
    private static final String OBJECT_KEY =
            "insurance/recruitment/2026/10/15/e9b4d56a626c4376b3bcf9d8bad8289e.mp4";

    @Test
    void previewUrlUsesSameDomainSignedProxyAddress() {
        OssStorageService storage = mock(OssStorageService.class);
        when(storage.isOssConfigured()).thenReturn(true);
        RecruitmentMediaPreviewService service = new RecruitmentMediaPreviewService(
                storage, "signing-secret", "https://ke.taifang.xyz/api", 3600);

        String url = service.previewUrl(OBJECT_KEY);

        assertTrue(url.startsWith("https://ke.taifang.xyz/api/public/recruitment-media?objectKey="));
        assertTrue(url.contains("expires="));
        assertTrue(url.contains("signature="));
    }

    @Test
    void previewUrlForcesPublicHttpBaseUrlToHttps() {
        OssStorageService storage = mock(OssStorageService.class);
        when(storage.isOssConfigured()).thenReturn(true);
        RecruitmentMediaPreviewService service = new RecruitmentMediaPreviewService(
                storage, "signing-secret", "http://ke.taifang.xyz/api", 3600);

        String url = service.previewUrl(OBJECT_KEY);

        assertTrue(url.startsWith("https://ke.taifang.xyz/api/public/recruitment-media?"));
    }

    @Test
    void openRangeValidatesSignatureAndReturnsOssStream() {
        OssStorageService storage = mock(OssStorageService.class);
        when(storage.isOssConfigured()).thenReturn(true);
        RecruitmentMediaPreviewService service = new RecruitmentMediaPreviewService(
                storage, "signing-secret", "https://ke.taifang.xyz/api", 3600);
        String url = service.previewUrl(OBJECT_KEY);
        long expires = Long.parseLong(url.split("expires=")[1].split("&")[0]);
        String signature = url.split("signature=")[1];
        ObjectStream expected = new ObjectStream(
                new ByteArrayInputStream(new byte[] {1}), "video/mp4", 1, 1, 0L, 0L);
        when(storage.openObject(OBJECT_KEY, 0L, 1023L)).thenReturn(expected);

        ObjectStream actual = service.openRange(OBJECT_KEY, expires, signature, 0, 1023);

        assertEquals(expected, actual);
    }

    @Test
    void rejectInvalidObjectKeyToPreventOpenProxy() {
        OssStorageService storage = mock(OssStorageService.class);
        RecruitmentMediaPreviewService service = new RecruitmentMediaPreviewService(
                storage, "signing-secret", "https://ke.taifang.xyz/api", 3600);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.previewUrl("insurance/realname/2026/10/15/a.mp4"));

        assertEquals("招工视频地址无效", error.getMessage());
    }
}
