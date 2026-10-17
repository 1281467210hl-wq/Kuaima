package com.kuaima.app.common.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.kuaima.app.common.service.OssStorageService.ObjectStream;
import com.kuaima.app.common.service.RecruitmentMediaPreviewService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
public class PublicRecruitmentMediaController {
    private static final Pattern RANGE_PATTERN = Pattern.compile("^bytes=(\\d+)-(\\d*)$");
    private final RecruitmentMediaPreviewService previewService;

    public PublicRecruitmentMediaController(RecruitmentMediaPreviewService previewService) {
        this.previewService = previewService;
    }

    @GetMapping("/public/recruitment-media")
    @Operation(summary = "播放招工视频", description = "短期签名地址，同域流式播放私有 OSS 招工视频")
    public ResponseEntity<StreamingResponseBody> preview(
            @RequestParam String objectKey,
            @RequestParam long expires,
            @RequestParam String signature,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        ObjectStream stream;
        try {
            long[] range = parseRange(rangeHeader);
            stream = range == null
                    ? previewService.open(objectKey, expires, signature)
                    : previewService.openRange(objectKey, expires, signature, range[0], range[1]);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(e.getMessage().contains("范围") ? HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE
                    : HttpStatus.FORBIDDEN).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                stream.contentType() == null ? "video/mp4" : stream.contentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"recruitment-video.mp4\"");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentLength(stream.responseLength());
        headers.set("X-Accel-Buffering", "no");
        HttpStatus status = HttpStatus.OK;
        if (stream.start() != null || stream.end() != null) {
            long start = stream.start() == null ? 0 : stream.start();
            long end = stream.end() == null ? stream.objectLength() - 1 : stream.end();
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + stream.objectLength());
            status = HttpStatus.PARTIAL_CONTENT;
        }
        InputStream content = stream.content();
        StreamingResponseBody body = output -> {
            try (InputStream input = content) {
                input.transferTo(output);
            } catch (IOException e) {
                // 客户端拖动或断开时会产生 expected EOF，流由 OSS 客户端关闭。
            }
        };
        return new ResponseEntity<>(body, headers, status);
    }

    private long[] parseRange(String rangeHeader) {
        if (!StringUtils.hasText(rangeHeader)) return null;
        Matcher matcher = RANGE_PATTERN.matcher(rangeHeader.trim());
        if (!matcher.matches()) return null;
        long start = Long.parseLong(matcher.group(1));
        return new long[] {start, matcher.group(2).isEmpty() ? Long.MAX_VALUE : Long.parseLong(matcher.group(2))};
    }
}
