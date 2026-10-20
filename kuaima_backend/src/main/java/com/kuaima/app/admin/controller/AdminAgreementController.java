package com.kuaima.app.admin.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson2.JSONObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.entity.Agreement;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.admin.repository.AgreementRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.service.OssUploadResult;
import com.kuaima.app.security.model.LoginUser;

@RestController
@RequestMapping("/admin/agreements")
@Tag(name = "后台-协议管理", description = "平台协议文本与附件管理")
public class AdminAgreementController {

    private static final Set<String> TYPES = Set.of(
            "用户服务协议",
            "隐私协议",
            "知识产权协议",
            "实名认证&人脸验证服务协议",
            "信息发布协议");
    private static final Set<String> FILE_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");

    private final AgreementRepository agreements;
    private final AdminUserRepository adminUsers;
    private final OssStorageService ossStorageService;

    public AdminAgreementController(AgreementRepository agreements,
                                    AdminUserRepository adminUsers,
                                    OssStorageService ossStorageService) {
        this.agreements = agreements;
        this.adminUsers = adminUsers;
        this.ossStorageService = ossStorageService;
    }

    @GetMapping
    @Operation(summary = "协议列表", description = "按协议类型与名称关键词分页查询")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String type,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        validatePage(page, size);
        Page<Agreement> result = agreements.search(
                StringUtils.hasText(type) ? type.trim() : null,
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return Result.success(Map.of(
                "records", result.getContent().stream().map(this::view).toList(),
                "total", result.getTotalElements(),
                "page", result.getNumber()), page, result.getTotalElements());
    }

    @GetMapping("/{id}")
    @Operation(summary = "协议详情", description = "返回协议基础信息、正文或附件元数据")
    public Result<JSONObject> get(@PathVariable Long id) {
        return Result.success(view(requireAgreement(id)));
    }

    @PostMapping
    @Operation(summary = "新建协议", description = "支持手动输入或上传附件两种内容形式")
    public Result<JSONObject> create(@RequestBody AgreementRequest request, Authentication authentication) {
        validate(request);
        Agreement agreement = new Agreement();
        apply(agreement, request);
        Long adminId = currentAdminId(authentication);
        agreement.setUploadAdminId(adminId);
        agreement.setUploadAccount(adminAccount(adminId));
        agreement.setCreatedAt(LocalDateTime.now());
        agreement.setUpdatedAt(LocalDateTime.now());
        return Result.success(view(agreements.save(agreement)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑协议", description = "更新协议基础信息与内容；上传账户保持不变")
    public Result<JSONObject> update(@PathVariable Long id, @RequestBody AgreementRequest request) {
        validate(request);
        Agreement agreement = requireAgreement(id);
        apply(agreement, request);
        return Result.success(view(agreements.save(agreement)));
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "启停协议", description = "切换协议启用状态")
    public Result<JSONObject> toggle(@PathVariable Long id) {
        Agreement agreement = requireAgreement(id);
        agreement.setEnabled(!Boolean.TRUE.equals(agreement.getEnabled()));
        return Result.success(view(agreements.save(agreement)));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传协议附件", description = "支持 PDF / DOC / DOCX / TXT，单文件不超过 5MB")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("文件大小不能超过5MB");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extension(original);
        if (!FILE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF / DOC / DOCX / TXT 格式文件");
        }
        if (ossStorageService == null) throw new IllegalStateException("附件存储服务未配置");
        OssUploadResult uploaded = ossStorageService.uploadResult(
                file, "insurance/agreement", "." + extension);
        return Result.success(Map.of(
                "objectKey", uploaded.objectKey(),
                "fileName", file.getOriginalFilename() == null ? uploaded.fileName() : file.getOriginalFilename(),
                "fileSize", file.getSize()));
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "获取协议附件下载地址", description = "私有 Bucket 附件按需生成限时签名地址")
    public Result<Map<String, Object>> downloadUrl(@PathVariable Long id) {
        Agreement agreement = requireAgreement(id);
        if (!"FILE".equals(agreement.getContentMode()) || !StringUtils.hasText(agreement.getFileObjectKey())) {
            throw new IllegalArgumentException("该协议未上传附件");
        }
        return Result.success(Map.of(
                "url", ossStorageService.downloadUrl(agreement.getFileObjectKey(), agreement.getFileName()),
                "fileName", agreement.getFileName()));
    }

    private void apply(Agreement agreement, AgreementRequest request) {
        agreement.setName(request.name().trim());
        agreement.setType(request.type().trim());
        agreement.setContentMode(request.contentMode().trim().toUpperCase());
        agreement.setContent("INPUT".equals(agreement.getContentMode()) ? request.content().trim() : null);
        agreement.setFileObjectKey("FILE".equals(agreement.getContentMode()) ? request.fileObjectKey().trim() : null);
        agreement.setFileName("FILE".equals(agreement.getContentMode()) ? request.fileName().trim() : null);
        agreement.setFileSize("FILE".equals(agreement.getContentMode()) ? request.fileSize() : null);
        agreement.setEnabled(!Boolean.FALSE.equals(request.enabled()));
    }

    private void validate(AgreementRequest request) {
        if (request == null) throw new IllegalArgumentException("协议内容不能为空");
        if (!StringUtils.hasText(request.name())) throw new IllegalArgumentException("协议名称不能为空");
        if (request.name().trim().length() > 120) throw new IllegalArgumentException("协议名称不能超过120字");
        if (!StringUtils.hasText(request.type()) || !TYPES.contains(request.type().trim())) {
            throw new IllegalArgumentException("协议类型无效");
        }
        String mode = StringUtils.hasText(request.contentMode()) ? request.contentMode().trim().toUpperCase() : "";
        if (!Set.of("INPUT", "FILE").contains(mode)) throw new IllegalArgumentException("协议内容形式无效");
        if ("INPUT".equals(mode) && !StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("请输入协议正文内容");
        }
        if ("FILE".equals(mode)
                && (!StringUtils.hasText(request.fileObjectKey())
                || !StringUtils.hasText(request.fileName())
                || request.fileSize() == null)) {
            throw new IllegalArgumentException("请上传协议文件");
        }
    }

    private Agreement requireAgreement(Long id) {
        return agreements.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("协议不存在: " + id));
    }

    private JSONObject view(Agreement agreement) {
        JSONObject result = new JSONObject(new LinkedHashMap<>());
        result.put("id", agreement.getId());
        result.put("agreementNo", "AGR" + String.format("%03d", agreement.getId()));
        result.put("name", agreement.getName());
        result.put("type", agreement.getType());
        result.put("contentMode", agreement.getContentMode());
        result.put("content", agreement.getContent());
        result.put("fileObjectKey", agreement.getFileObjectKey());
        result.put("fileName", agreement.getFileName());
        result.put("fileSize", agreement.getFileSize());
        result.put("enabled", Boolean.TRUE.equals(agreement.getEnabled()));
        result.put("uploadAccount", agreement.getUploadAccount());
        result.put("createdAt", agreement.getCreatedAt());
        result.put("updatedAt", agreement.getUpdatedAt());
        return result;
    }

    private Long currentAdminId(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof LoginUser user && user.id() != null) return user.id();
        Object contextPrincipal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return contextPrincipal instanceof LoginUser user ? user.id() : null;
    }

    private String adminAccount(Long adminId) {
        if (adminId == null) return "系统";
        return adminUsers.findById(adminId)
                .map(admin -> StringUtils.hasText(admin.getName()) ? admin.getName() : admin.getUsername())
                .orElse("管理员" + adminId);
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page 必须大于等于0，size范围为1-100");
        }
    }

    public record AgreementRequest(String name, String type, String contentMode, String content,
                                   String fileObjectKey, String fileName, Long fileSize, Boolean enabled) {}
}
