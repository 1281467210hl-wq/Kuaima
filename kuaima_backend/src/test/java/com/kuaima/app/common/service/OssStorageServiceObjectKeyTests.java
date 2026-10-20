package com.kuaima.app.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class OssStorageServiceObjectKeyTests {

    @Test
    void objectKeyShouldKeepRelativeKeyIntact() throws Exception {
        OssStorageService service = configured();
        assertEquals("insurance/realname/2026/10/11/abc.png",
                objectKey(service, "insurance/realname/2026/10/11/abc.png"));
    }

    @Test
    void objectKeyShouldExtractKeyFromBucketUrl() throws Exception {
        OssStorageService service = configured();
        assertEquals("insurance/realname/2026/10/11/abc.png",
                objectKey(service,
                        "https://resume-storage-20260923.oss-cn-beijing.aliyuncs.com/insurance/realname/2026/10/11/abc.png"));
    }

    @Test
    void objectKeyShouldRejectForeignHost() throws Exception {
        OssStorageService service = configured();
        assertNull(objectKey(service, "https://evil.example.com/insurance/realname/a.png"));
    }

    @Test
    void previewContentTypeShouldSupportRecruitmentImagesAndVideos() throws Exception {
        OssStorageService service = new OssStorageService();
        assertEquals("image/png", previewContentType(service, "insurance/recruitment/a.png"));
        assertEquals("image/jpeg", previewContentType(service, "insurance/recruitment/a.JPG"));
        assertEquals("image/webp", previewContentType(service, "insurance/recruitment/a.webp"));
        assertEquals("video/mp4", previewContentType(service, "insurance/recruitment/a.mp4"));
        assertEquals("application/octet-stream", previewContentType(service, "insurance/recruitment/a.unknown"));
    }

    @Test
    void uploadContentTypeShouldCorrectMp4WhenClientSendsOctetStream() throws Exception {
        OssStorageService service = new OssStorageService();
        Method method = OssStorageService.class.getDeclaredMethod(
                "resolveContentType", String.class, String.class);
        method.setAccessible(true);

        assertEquals("video/mp4", method.invoke(service, "application/octet-stream", ".mp4"));
        assertEquals("video/mp4", method.invoke(service, null, ".MP4"));
    }

    @Test
    void playableUrlShouldSignWithConfiguredCustomDomain() throws Exception {
        OssStorageService service = configured();
        field(service, "accessKeyId", "test-key");
        field(service, "accessKeySecret", "test-secret");
        objectField(service, "signedUrlExpirationSeconds", 3600L);
        field(service, "domain", "https://media.example.com");
        OSS previewClient = mock(OSS.class);
        objectField(service, "previewClient", previewClient);
        when(previewClient.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("http://media.example.com/insurance/recruitment/a.mp4?Expires=1"));

        String url = service.playableUrl("insurance/recruitment/a.mp4");

        org.junit.jupiter.api.Assertions.assertTrue(url.startsWith("https://media.example.com/"));
        ArgumentCaptor<GeneratePresignedUrlRequest> captor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(previewClient).generatePresignedUrl(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "resume-storage-20260923", captor.getValue().getBucketName());
        org.junit.jupiter.api.Assertions.assertEquals(
                "insurance/recruitment/a.mp4", captor.getValue().getKey());
        assertEquals("inline", captor.getValue().getResponseHeaders().getContentDisposition());
        assertNull(captor.getValue().getResponseHeaders().getContentType());
    }

    @Test
    void downloadUrlShouldSignAttachmentWithOriginalFileName() throws Exception {
        OssStorageService service = configured();
        field(service, "accessKeyId", "test-key");
        field(service, "accessKeySecret", "test-secret");
        objectField(service, "signedUrlExpirationSeconds", 3600L);
        field(service, "domain", "https://files.example.com");
        OSS previewClient = mock(OSS.class);
        objectField(service, "previewClient", previewClient);
        when(previewClient.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://resume-storage.example.com/insurance/agreement/a.pdf?Expires=1"));

        service.downloadUrl("insurance/agreement/a.pdf", "快马日结隐私协议.pdf");

        ArgumentCaptor<GeneratePresignedUrlRequest> captor =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(previewClient).generatePresignedUrl(captor.capture());
        assertEquals("attachment; filename=\"________.pdf\"; filename*=UTF-8''%E5%BF%AB%E9%A9%AC%E6%97%A5%E7%BB%93%E9%9A%90%E7%A7%81%E5%8D%8F%E8%AE%AE.pdf",
                captor.getValue().getResponseHeaders().getContentDisposition());
    }

    private OssStorageService configured() throws Exception {
        OssStorageService service = new OssStorageService();
        field(service, "endpoint", "oss-cn-beijing.aliyuncs.com");
        field(service, "bucketName", "resume-storage-20260923");
        return service;
    }

    private Object objectKey(OssStorageService service, String value) throws Exception {
        Method method = OssStorageService.class.getDeclaredMethod("objectKey", String.class);
        method.setAccessible(true);
        return method.invoke(service, value);
    }

    private String previewContentType(OssStorageService service, String value) throws Exception {
        Method method = OssStorageService.class.getDeclaredMethod("previewContentType", String.class);
        method.setAccessible(true);
        return String.valueOf(method.invoke(service, value));
    }

    private void field(OssStorageService service, String name, String value) throws Exception {
        Field field = OssStorageService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private void objectField(OssStorageService service, String name, Object value) throws Exception {
        Field field = OssStorageService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }
}
