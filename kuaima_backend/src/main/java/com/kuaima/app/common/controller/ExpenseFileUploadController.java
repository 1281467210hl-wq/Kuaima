package com.kuaima.app.common.controller;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.OssUploadResult;
import com.kuaima.app.common.service.OssStorageService;

@RestController
@RequestMapping("/files")
public class ExpenseFileUploadController {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final OssStorageService ossStorageService;

    public ExpenseFileUploadController(OssStorageService ossStorageService) {
        this.ossStorageService = ossStorageService;
    }

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("图片大小不能超过10MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extension(original);
        boolean unknownOrImageMime = contentType.isBlank() || "application/octet-stream".equals(contentType);
        if (!EXTENSIONS.contains(extension) || !(unknownOrImageMime || CONTENT_TYPES.contains(contentType))) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png 图片");
        }
        OssUploadResult uploaded = ossStorageService.uploadResult(file, "insurance/files", "." + extension);
        java.util.Map<String, String> result = uploaded.toMap();
        result.put("url", ossStorageService.inlinePreviewUrl(uploaded.objectKey()));
        return Result.success(result);
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
