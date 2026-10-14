package com.kuaima.app.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

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
}
