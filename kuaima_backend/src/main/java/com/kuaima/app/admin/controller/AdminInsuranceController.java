package com.kuaima.app.admin.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.domain.insurance.entity.InsuranceProduct;
import com.kuaima.app.domain.insurance.repository.InsuranceProductRepository;
import com.kuaima.app.security.model.LoginUser;

/** 后台保险管理：保险公司意外险产品配置 */
@RestController
@RequestMapping("/admin/insurances")
@Tag(name = "后台-保险管理", description = "保险产品配置与管理")
public class AdminInsuranceController {

    private final InsuranceProductRepository products;
    private final AdminUserRepository adminUsers;
    private final OssStorageService ossStorageService;

    public AdminInsuranceController(InsuranceProductRepository products, AdminUserRepository adminUsers,
                                    OssStorageService ossStorageService) {
        this.products = products;
        this.adminUsers = adminUsers;
        this.ossStorageService = ossStorageService;
    }

    @Operation(summary = "保险产品列表", description = "按保险公司/产品编码关键词和启用状态分页查询，按 id 倒序")
    @GetMapping
    public Result<Page<JSONObject>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        Boolean enabled = switch (status == null ? "" : status) {
            case "已启用" -> true;
            case "已停用" -> false;
            default -> null;
        };
        Page<JSONObject> views = products.search(
                        keyword == null || keyword.isBlank() ? null : keyword.trim(),
                        enabled,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(this::view);
        return Result.success(views, page, views.getTotalElements());
    }

    @Operation(summary = "保险产品详情", description = "返回产品完整信息，tiers 解析为档位数组")
    @GetMapping("/{id}")
    public Result<JSONObject> get(@PathVariable Long id) {
        InsuranceProduct product = products.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("保险产品不存在: " + id));
        return Result.success(view(product));
    }

    @Operation(summary = "客户告知书临时查看地址", description = "私有 Bucket 对象按需生成带签名的临时 URL，避免附件被公开访问")
    @GetMapping("/{id}/notice-url")
    public Result<Map<String, Object>> noticeUrl(@PathVariable Long id) {
        InsuranceProduct product = products.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("保险产品不存在: " + id));
        JSONArray tiers = parseTiers(product.getTiers());
        String objectUrl = null;
        for (int i = 0; i < tiers.size(); i++) {
            String url = tiers.getJSONObject(i).getString("noticeFileUrl");
            if (StringUtils.hasText(url)) {
                objectUrl = url;
                break;
            }
        }
        if (objectUrl == null) throw new IllegalArgumentException("该保险产品未上传客户告知书");
        Map<String, Object> data = new HashMap<>();
        data.put("url", ossStorageService.inlinePreviewUrl(objectUrl));
        return Result.success(data);
    }

    @Operation(summary = "新建保险产品", description = "配置保险公司、产品编码、启用状态与档位 JSON")
    @PostMapping
    public Result<JSONObject> create(@RequestBody InsuranceProductRequest request) {
        validate(request, null);
        InsuranceProduct product = new InsuranceProduct();
        apply(product, request);
        product.setCreateBy(currentAdminId());
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        return Result.success(view(products.save(product)));
    }

    @Operation(summary = "编辑保险产品", description = "更新保险公司、产品编码、启用状态与档位")
    @PutMapping("/{id}")
    public Result<JSONObject> update(@PathVariable Long id, @RequestBody InsuranceProductRequest request) {
        InsuranceProduct product = products.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("保险产品不存在: " + id));
        validate(request, id);
        apply(product, request);
        product.setUpdateTime(LocalDateTime.now());
        return Result.success(view(products.save(product)));
    }

    @Operation(summary = "启用/停用保险产品", description = "切换启用状态")
    @PutMapping("/{id}/toggle")
    public Result<JSONObject> toggle(@PathVariable Long id) {
        InsuranceProduct product = products.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("保险产品不存在: " + id));
        product.setEnabled(!Boolean.TRUE.equals(product.getEnabled()));
        product.setUpdateTime(LocalDateTime.now());
        return Result.success(view(products.save(product)));
    }

    @Operation(summary = "上传客户告知书附件", description = "仅支持 PDF，单文件不超过 5MB")
    @org.springframework.web.bind.annotation.PostMapping("/upload-notice")
    public Result<Map<String, String>> uploadNotice(@org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > 5L * 1024 * 1024) throw new IllegalArgumentException("附件大小不能超过5MB");
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = extension(original);
        if (!Set.of("pdf").contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF 格式附件");
        }
        return Result.success(ossStorageService.upload(file, "insurance/notice", "." + extension));
    }

    private void validate(InsuranceProductRequest request, Long excludeId) {
        if (request.carrier() == null || request.carrier().isBlank()) {
            throw new IllegalArgumentException("保险公司不能为空");
        }
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("产品编码不能为空");
        }
        String code = request.code().trim();
        boolean duplicated = excludeId == null ? products.existsByCode(code) : products.existsByCodeAndIdNot(code, excludeId);
        if (duplicated) {
            throw new IllegalArgumentException("产品编码已存在: " + code);
        }
    }

    private void apply(InsuranceProduct product, InsuranceProductRequest request) {
        product.setCarrier(request.carrier().trim());
        product.setCode(request.code().trim());
        product.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        product.setRemark(request.remark());
        product.setTiers(request.tiers() == null ? "[]" : JSON.toJSONString(request.tiers()));
    }

    private JSONObject view(InsuranceProduct product) {
        JSONObject obj = (JSONObject) JSON.toJSON(product);
        JSONArray tiers = parseTiers(product.getTiers());
        obj.put("tiers", tiers);
        obj.put("tierCount", tiers.size());
        obj.put("creatorName", creatorName(product.getCreateBy()));
        return obj;
    }

    private JSONArray parseTiers(String tiers) {
        if (tiers == null || tiers.isBlank()) return new JSONArray();
        try {
            return JSON.parseArray(tiers);
        } catch (RuntimeException e) {
            return new JSONArray();
        }
    }

    private String creatorName(Long adminId) {
        if (adminId == null) return "-";
        return adminUsers.findById(adminId)
                .map(admin -> firstText(admin.getName(), admin.getUsername()))
                .orElse("管理员" + adminId);
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private Long currentAdminId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof LoginUser user ? user.id() : null;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    public record InsuranceProductRequest(String carrier, String code, Boolean enabled,
                                          List<Map<String, Object>> tiers, String remark) {}
}
