package com.kuaima.app.common.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.OssUploadResult;

class ExpenseFileUploadControllerTests {

    @Test
    void upload_shouldStoreInAuthorizedPrefixAndReturnSignedPreview() {
        OssStorageService storage = mock(OssStorageService.class);
        String objectKey = "insurance/files/2026/10/14/qr.png";
        when(storage.uploadResult(any(), eq("insurance/files"), eq(".png")))
                .thenReturn(new OssUploadResult(objectKey, "https://oss.example.com/raw.png", "qr.png"));
        when(storage.inlinePreviewUrl(objectKey)).thenReturn("https://oss.example.com/signed.png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "qr.png", "application/octet-stream", new byte[] {1});

        var result = new ExpenseFileUploadController(storage).upload(file).getData();

        assertEquals(objectKey, result.get("objectKey"));
        assertEquals("https://oss.example.com/signed.png", result.get("url"));
    }

    @Test
    void upload_shouldRejectUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "qr.gif", "image/gif", new byte[] {1});

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ExpenseFileUploadController(mock(OssStorageService.class)).upload(file));

        assertEquals("仅支持 jpg、jpeg、png 图片", error.getMessage());
    }
}
