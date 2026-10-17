package com.kuaima.app.common.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.kuaima.app.common.service.OssStorageService.ObjectStream;

/** 生成招工视频短期同域代理地址，避免 OSS 默认域名强制附件下载。 */
@Service
public class RecruitmentMediaPreviewService {
    private static final Pattern RECRUITMENT_VIDEO_KEY =
            Pattern.compile("^insurance/recruitment/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.mp4$");

    private final OssStorageService storage;
    private final String signingSecret;
    private final String publicBaseUrl;
    private final long expiresSeconds;

    public RecruitmentMediaPreviewService(
            OssStorageService storage,
            @Value("${aliyun.oss.access-key-secret:}") String signingSecret,
            @Value("${kuaima.media.public-base-url:}") String publicBaseUrl,
            @Value("${kuaima.media.proxy-url-expiration-seconds:3600}") long expiresSeconds) {
        this.storage = storage;
        this.signingSecret = signingSecret;
        this.publicBaseUrl = publicBaseUrl;
        this.expiresSeconds = Math.max(60, expiresSeconds);
    }

    public String previewUrl(String objectKey) {
        requireRecruitmentVideo(objectKey);
        if (!storage.isOssConfigured()) return storage.playableUrl(objectKey);
        long expires = Instant.now().plusSeconds(expiresSeconds).getEpochSecond();
        String signature = signature(objectKey, expires);
        return UriComponentsBuilder.fromUriString(resolveBaseUrl())
                .path("/public/recruitment-media")
                .queryParam("objectKey", objectKey)
                .queryParam("expires", expires)
                .queryParam("signature", signature)
                .build()
                .toUriString();
    }

    public ObjectStream open(String objectKey, long expires, String signature) {
        requireRecruitmentVideo(objectKey);
        if (!storage.isOssConfigured()) throw new IllegalStateException("OSS 存储未配置");
        if (expires < Instant.now().getEpochSecond() || !signature(signature(objectKey, expires), signature)) {
            throw new IllegalArgumentException("视频预览地址无效或已过期");
        }
        return storage.openObject(objectKey, null, null);
    }

    public ObjectStream openRange(String objectKey, long expires, String signature, long start, long end) {
        requireRecruitmentVideo(objectKey);
        if (!storage.isOssConfigured()) throw new IllegalStateException("OSS 存储未配置");
        if (expires < Instant.now().getEpochSecond() || !signature(signature(objectKey, expires), signature)) {
            throw new IllegalArgumentException("视频预览地址无效或已过期");
        }
        return storage.openObject(objectKey, start, end);
    }

    private void requireRecruitmentVideo(String objectKey) {
        if (!StringUtils.hasText(objectKey) || !RECRUITMENT_VIDEO_KEY.matcher(objectKey).matches()) {
            throw new IllegalArgumentException("招工视频地址无效");
        }
    }

    private String signature(String objectKey, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal((objectKey + "\n" + expires).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("视频预览地址签名失败", e);
        }
    }

    private boolean signature(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveBaseUrl() {
        if (StringUtils.hasText(publicBaseUrl)) return forceHttpsForPublicHost(publicBaseUrl.replaceAll("/$", ""));
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            String scheme = header(request, "X-Forwarded-Proto", request.getScheme());
            String host = header(request, "X-Forwarded-Host", request.getHeader("Host"));
            String prefix = header(request, "X-Forwarded-Prefix", "");
            if (!StringUtils.hasText(host)) return "";
            if (!StringUtils.hasText(prefix) && !isLocalHost(host) && !request.getRequestURI().startsWith("/api/")) {
                prefix = "/api";
            }
            return forceHttpsForPublicHost(scheme + "://" + host + prefix);
        }
        return "";
    }

    private String forceHttpsForPublicHost(String baseUrl) {
        if (!StringUtils.hasText(baseUrl) || !baseUrl.toLowerCase().startsWith("http://")) return baseUrl;
        String host = baseUrl.substring("http://".length()).split("/")[0];
        return isLocalHost(host) ? baseUrl : "https://" + baseUrl.substring("http://".length());
    }

    private String header(jakarta.servlet.http.HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean isLocalHost(String host) {
        String value = host.split(":")[0];
        return "localhost".equalsIgnoreCase(value) || "127.0.0.1".equals(value) || "0.0.0.0".equals(value);
    }

    private static final class MessageDigest {
        private static boolean isEqual(byte[] left, byte[] right) {
            return java.security.MessageDigest.isEqual(left, right);
        }
    }
}
