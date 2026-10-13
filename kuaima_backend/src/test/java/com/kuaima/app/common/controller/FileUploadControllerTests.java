package com.kuaima.app.common.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.OssUploadResult;

class FileUploadControllerTests {

    @Test
    void upload_shouldReturnObjectKeyAndSignedPreviewUrl() {
        OssStorageService storage = mock(OssStorageService.class);
        FileUploadController controller = new FileUploadController(storage);
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1});
        OssUploadResult uploaded = new OssUploadResult(
                "insurance/admin/2026/10/13/logo.png",
                "https://resume-storage-20260923.oss-cn-beijing.aliyuncs.com/insurance/admin/2026/10/13/logo.png",
                "logo.png");
        when(storage.uploadResult(file, "insurance/admin", ".png")).thenReturn(uploaded);
        when(storage.inlinePreviewUrl(uploaded.objectKey()))
                .thenReturn("https://example.com/signed-logo.png");

        var result = controller.upload(file);

        assertEquals(uploaded.objectKey(), result.getData().get("objectKey"));
        assertEquals("https://example.com/signed-logo.png", result.getData().get("url"));
        verify(storage).inlinePreviewUrl(uploaded.objectKey());
    }
}
