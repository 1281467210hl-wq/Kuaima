package com.kuaima.app.common.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.kuaima.app.common.service.OssStorageService.ObjectStream;
import com.kuaima.app.common.service.RecruitmentMediaPreviewService;

class PublicRecruitmentMediaControllerTests {
    @Test
    void previewReturnsInlineHeadersAndSupportsRange() {
        RecruitmentMediaPreviewService service = mock(RecruitmentMediaPreviewService.class);
        ObjectStream stream = new ObjectStream(
                new ByteArrayInputStream(new byte[] {1, 2, 3}), "video/mp4", 3, 2, 0L, 1L);
        when(service.openRange("key", 200L, "signature", 0, 1023)).thenReturn(stream);
        PublicRecruitmentMediaController controller = new PublicRecruitmentMediaController(service);

        var response = controller.preview("key", 200, "signature", "bytes=0-1023");

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("video/mp4", response.getHeaders().getContentType().toString());
        assertEquals("inline; filename=\"recruitment-video.mp4\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertEquals("bytes 0-1/3", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertEquals(2, response.getHeaders().getContentLength());
        assertEquals("no", response.getHeaders().getFirst("X-Accel-Buffering"));
    }

    @Test
    void invalidSignatureReturnsForbidden() {
        RecruitmentMediaPreviewService service = mock(RecruitmentMediaPreviewService.class);
        when(service.open("key", 100L, "bad"))
                .thenThrow(new IllegalArgumentException("视频预览地址无效或已过期"));
        PublicRecruitmentMediaController controller = new PublicRecruitmentMediaController(service);

        var response = controller.preview("key", 100, "bad", null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
