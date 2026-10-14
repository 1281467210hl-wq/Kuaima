package com.kuaima.app.common.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.kuaima.app.common.ForbiddenBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PreDestroy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OssStorageService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    /** 本地兜底存储时允许的扩展名形态（防路径穿越）：仅 . + 1~8 位字母数字。 */
    private static final java.util.regex.Pattern SAFE_EXT =
            java.util.regex.Pattern.compile("^\\.[a-z0-9]{1,8}$");

    @Value("${aliyun.oss.endpoint:}") private String endpoint;
    @Value("${aliyun.oss.bucket-name:}") private String bucketName;
    @Value("${aliyun.oss.access-key-id:}") private String accessKeyId;
    @Value("${aliyun.oss.access-key-secret:}") private String accessKeySecret;
    @Value("${aliyun.oss.domain:}") private String domain;
    @Value("${aliyun.oss.signed-url-expiration-seconds:3600}") private long signedUrlExpirationSeconds;
    @Value("${kuaima.upload.dir:./uploads/}") private String uploadDir;
    @Value("${kuaima.upload.public-base-url:}") private String uploadBaseUrl;
    private volatile OSS client;

    /** OSS 四项必填配置齐全才走云端，否则本地磁盘兜底（开发/测试环境无 OSS 也能上传成功）。 */
    private boolean ossConfigured() {
        return StringUtils.hasText(endpoint) && StringUtils.hasText(bucketName)
                && StringUtils.hasText(accessKeyId) && StringUtils.hasText(accessKeySecret);
    }

    /** 写入 kuaima.upload.dir 并返回 /uploads/** 静态映射可访问的结果。 */
    private OssUploadResult saveLocal(byte[] content, String folder, String extension) {
        String suffix = extension == null ? "" : extension.toLowerCase();
        if (!SAFE_EXT.matcher(suffix).matches()) {
            suffix = "";
        }
        String relative = folder.replaceAll("^/+", "") + "/" + LocalDate.now().format(DATE_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + suffix;
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法上传路径");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("本地存储写入失败", e);
        }
        String url = uploadBaseUrl.replaceAll("/$", "") + "/uploads/" + relative;
        return new OssUploadResult("uploads/" + relative, url,
                relative.substring(relative.lastIndexOf('/') + 1));
    }

    public Map<String, String> upload(MultipartFile file, String folder, String extension) {
        return uploadResult(file, folder, extension).toMap();
    }

    public OssUploadResult uploadResult(MultipartFile file, String folder, String extension) {
        if (!ossConfigured()) {
            try {
                return saveLocal(file.getBytes(), folder, extension);
            } catch (IOException e) {
                throw new IllegalStateException("读取上传文件失败", e);
            }
        }
        String suffix = extension == null ? "" : extension.toLowerCase();
        String objectKey = folder + "/" + LocalDate.now().format(DATE_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + suffix;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(resolveContentType(file.getContentType(), suffix));
        metadata.setContentLength(file.getSize());
        try {
            getClient().putObject(bucketName, objectKey, file.getInputStream(), metadata);
            return result(objectKey);
        } catch (OSSException e) {
            if ("AccessDenied".equalsIgnoreCase(e.getErrorCode())) {
                throw new ForbiddenBusinessException("OSS 上传权限不足，请为当前 RAM 用户授予目标目录的 oss:PutObject 权限");
            }
            throw new IllegalStateException("上传 OSS 失败，请检查存储配置和服务状态");
        } catch (IOException e) {
            throw new IllegalStateException("上传 OSS 失败", e);
        }
    }

    public Map<String, String> upload(Path file, String contentType, String folder, String extension) {
        return uploadResult(file, contentType, folder, extension).toMap();
    }

    public OssUploadResult uploadResult(Path file, String contentType, String folder, String extension) {
        if (!ossConfigured()) {
            try {
                return saveLocal(Files.readAllBytes(file), folder, extension);
            } catch (IOException e) {
                throw new IllegalStateException("读取上传文件失败", e);
            }
        }
        String suffix = extension == null ? "" : extension.toLowerCase();
        String objectKey = folder + "/" + LocalDate.now().format(DATE_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + suffix;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        try { metadata.setContentLength(Files.size(file)); }
        catch (IOException e) { throw new IllegalStateException("读取上传文件失败", e); }
        try (var input = Files.newInputStream(file)) {
            getClient().putObject(bucketName, objectKey, input, metadata);
            return result(objectKey);
        } catch (OSSException e) {
            if ("AccessDenied".equalsIgnoreCase(e.getErrorCode())) {
                throw new ForbiddenBusinessException("OSS 上传权限不足，请为当前 RAM 用户授予目标目录的 oss:PutObject 权限");
            }
            throw new IllegalStateException("上传 OSS 失败，请检查存储配置和服务状态");
        } catch (IOException e) {
            throw new IllegalStateException("上传 OSS 失败", e);
        }
    }

    /**
     * 上传内存中的字节内容（如 PDF 渲染出的预览图），返回 {objectKey, url, fileName}。
     */
    public Map<String, String> upload(byte[] content, String contentType, String folder, String extension) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("上传内容为空");
        }
        if (!ossConfigured()) {
            return saveLocal(content, folder, extension).toMap();
        }
        String suffix = extension == null ? "" : extension.toLowerCase();
        String objectKey = folder + "/" + LocalDate.now().format(DATE_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + suffix;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(content.length);
        OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            client.putObject(bucketName, objectKey, new ByteArrayInputStream(content), metadata);
            Map<String, String> result = new LinkedHashMap<>();
            result.put("objectKey", objectKey);
            result.put("url", objectUrl(objectKey));
            result.put("fileName", objectKey.substring(objectKey.lastIndexOf('/') + 1));
            return result;
        } catch (OSSException e) {
            if ("AccessDenied".equalsIgnoreCase(e.getErrorCode())) {
                throw new ForbiddenBusinessException("OSS 上传权限不足，请为当前 RAM 用户授予目标目录的 oss:PutObject 权限");
            }
            throw new IllegalStateException("上传 OSS 失败，请检查存储配置和服务状态");
        } finally {
            client.shutdown();
        }
    }

    /**
     * 纠正上传对象的 Content-Type。
     *
     * <p>小程序 uni.uploadFile 发出的分片通常不带具体 Content-Type，Spring 解析出来是
     * application/octet-stream 或 null；直接透传给 OSS 会导致图片/PDF 等对象类型错误
     * （小程序 &lt;image&gt; 渲染、浏览器内联预览都会受影响）。这里按扩展名兜底推断。
     */
    private static String resolveContentType(String clientType, String suffix) {
        if (StringUtils.hasText(clientType) && !"application/octet-stream".equalsIgnoreCase(clientType)) {
            return clientType;
        }
        switch (suffix == null ? "" : suffix) {
            case ".jpg":
            case ".jpeg":
            case ".jpe":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".webp":
                return "image/webp";
            case ".bmp":
                return "image/bmp";
            case ".pdf":
                return "application/pdf";
            case ".doc":
                return "application/msword";
            case ".docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xls":
                return "application/vnd.ms-excel";
            case ".xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                return StringUtils.hasText(clientType) ? clientType : "application/octet-stream";
        }
    }

    private String objectUrl(String objectKey) {
        String base = StringUtils.hasText(domain) ? domain.replaceAll("/$", "") : "https://" + bucketName + "." + endpoint;
        return base + "/" + URLEncoder.encode(objectKey, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    /**
     * 私有 Bucket 中对象的临时播放地址。数据库可以保存普通对象 URL，接口返回时再签名，避免
     * 把 academy 目录或整个 Bucket 改成公共读。
     */
    public String playableUrl(String value) {
        return playableUrl(value, signedUrlExpirationSeconds);
    }

    /** 生成自定义有效期的临时地址，单位为秒。 */
    public String playableUrl(String value, long expirationSeconds) {
        if (expirationSeconds <= 0) throw new IllegalArgumentException("签名有效期必须大于 0 秒");
        if (!StringUtils.hasText(value) || !StringUtils.hasText(endpoint)
                || !StringUtils.hasText(bucketName) || !StringUtils.hasText(accessKeyId)
                || !StringUtils.hasText(accessKeySecret)) {
            return value;
        }
        String objectKey = objectKey(value);
        if (!StringUtils.hasText(objectKey)) return value;
        Date expires = new Date(System.currentTimeMillis() + expirationMillis(expirationSeconds));
        return getClient().generatePresignedUrl(bucketName, objectKey, expires).toString()
                .replaceFirst("^http://", "https://");
    }

    /**
     * 浏览器内联预览地址：强制 Content-Disposition: inline，并按扩展名纠正 Content-Type，
     * 避免对象元数据为 octet-stream 时浏览器直接触发下载。
     */
    public String inlinePreviewUrl(String value) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(endpoint)
                || !StringUtils.hasText(bucketName) || !StringUtils.hasText(accessKeyId)
                || !StringUtils.hasText(accessKeySecret)) {
            return value;
        }
        String objectKey = objectKey(value);
        if (!StringUtils.hasText(objectKey)) return value;
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(bucketName, objectKey, HttpMethod.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + expirationMillis(signedUrlExpirationSeconds)));
        ResponseHeaderOverrides overrides = new ResponseHeaderOverrides();
        overrides.setContentDisposition("inline");
        overrides.setContentType(previewContentType(objectKey));
        request.setResponseHeaders(overrides);
        return getClient().generatePresignedUrl(request).toString()
                .replaceFirst("^http://", "https://");
    }

    private String previewContentType(String objectKey) {
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".jpe")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private OssUploadResult result(String objectKey) {
        return new OssUploadResult(objectKey, objectUrl(objectKey), objectKey.substring(objectKey.lastIndexOf('/') + 1));
    }

    private long expirationMillis(long expirationSeconds) {
        return TimeUnit.SECONDS.toMillis(expirationSeconds);
    }

    private OSS getClient() {
        OSS current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) client = current = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            }
        }
        return current;
    }

    @PreDestroy
    void shutdown() {
        OSS current = client;
        if (current != null) current.shutdown();
    }

    private String objectKey(String value) {
        try {
            URI uri = URI.create(value);
            // 相对对象键（如 insurance/realname/a.png）：没有 scheme/host，本身就是 key，
            // 不能走 URL 的 path.substring(1)，否则会截掉首字符。
            if (uri.getScheme() == null && uri.getHost() == null) return value;
            String host = uri.getHost();
            String expectedHost = bucketName + "." + endpoint;
            if (host == null || !expectedHost.equalsIgnoreCase(host)) return null;
            String path = uri.getPath();
            if (path == null || path.length() <= 1) return null;
            return URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
