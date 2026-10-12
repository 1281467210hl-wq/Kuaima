package com.kuaima.app.admin.controller;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.entity.Certification;
import com.kuaima.app.admin.repository.CertificationRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.user.service.CertificationService;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

/** 认证审核 */
@RestController
@RequestMapping("/admin/certifications")
@Tag(name = "后台-认证审核", description = "身份认证审核记录")
public class CertificationController {

    private final CertificationRepository repo;
    private final CertificationService certificationService;
    private final UserRepository userRepository;
    private final com.kuaima.app.common.service.OssStorageService ossStorageService;

    public CertificationController(CertificationRepository repo, CertificationService certificationService) {
        this(repo, certificationService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CertificationController(CertificationRepository repo, CertificationService certificationService,
                                    UserRepository userRepository,
                                    com.kuaima.app.common.service.OssStorageService ossStorageService) {
        this.repo = repo;
        this.certificationService = certificationService;
        this.userRepository = userRepository;
        this.ossStorageService = ossStorageService;
    }

    /** 列表（分页，按 type/status 过滤，按 id 倒序） */
    @Operation(summary = "认证审核列表", description = "按 type(零工实名/企业认证) 和 status(待审核/已通过/已拒绝) 过滤分页，按 id 倒序")
    @GetMapping
    public Result<List<Certification>> list(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        if ("ENTERPRISE".equalsIgnoreCase(type) && userRepository != null) {
            return enterpriseList(status, keyword, dateFrom, dateTo, page, size);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Certification> result;
        boolean hasType = type != null && !type.isEmpty();
        boolean hasStatus = status != null && !status.isEmpty();
        if (hasType && hasStatus) {
            result = repo.findByTypeAndStatus(type, status, pageable);
        } else if (hasType) {
            result = repo.findByType(type, pageable);
        } else if (hasStatus) {
            result = repo.findByStatus(status, pageable);
        } else {
            result = repo.findAll(pageable);
        }
        return Result.success(result.getContent(), page, result.getTotalElements());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Result<List<Certification>> enterpriseList(String status, String keyword, LocalDate dateFrom, LocalDate dateTo, int page, int size) {
        String normalized = normalizeStatus(status);
        String kw = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> all = new ArrayList<>();
        java.util.Set<Long> seenUsers = new java.util.HashSet<>();
        for (Certification record : repo.findByTypeOrderByIdDesc("ENTERPRISE")) {
            if (!seenUsers.add(record.getUserId())) continue;
            if (normalized != null && !normalized.equals(normalizeStatus(record.getStatus()))) continue;
            if (record.getApplyTime() != null) {
                if (dateFrom != null && record.getApplyTime().toLocalDate().isBefore(dateFrom)) continue;
                if (dateTo != null && record.getApplyTime().toLocalDate().isAfter(dateTo)) continue;
            } else if (dateFrom != null || dateTo != null) continue;
            User user = userRepository.findById(record.getUserId()).orElse(null);
            if (user == null) continue;
            if (kw != null && !kw.isBlank() && !contains(user.getCompanyName(), kw) && !contains(user.getLicenseNo(), kw)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId()); row.put("certificationId", record.getId());
            row.put("companyName", user.getCompanyName()); row.put("licenseNo", user.getLicenseNo());
            row.put("companyCode", user.getCompanyCode()); row.put("industry", user.getIndustry());
            row.put("legalRep", user.getLegalRep()); row.put("contact", user.getContact());
            row.put("contactPhone", user.getContactPhone()); row.put("phone", user.getPhone());
            row.put("username", user.getUsername()); row.put("enterpriseStatus", normalizeStatus(record.getStatus()));
            row.put("status", record.getStatus()); row.put("certificationApplyTime", record.getApplyTime());
            row.put("applyTime", record.getApplyTime()); String licenseImage = record.getLicenseImageUrl() != null ? record.getLicenseImageUrl() : user.getLicenseImageUrl();
            row.put("licenseImageUrl", ossStorageService == null ? licenseImage : ossStorageService.playableUrl(licenseImage));
            row.put("foundingDate", user.getFoundingDate()); row.put("businessScope", user.getBusinessScope());
            all.add(row);
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return Result.success((List) all.subList(from, to), safePage, all.size());
    }

    private boolean contains(String value, String keyword) { return value != null && value.toLowerCase(Locale.ROOT).contains(keyword); }
    private String normalizeStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        return switch (value.toUpperCase(Locale.ROOT)) { case "PENDING", "待审核" -> "PENDING"; case "APPROVED", "已通过" -> "APPROVED"; case "REJECTED", "已拒绝" -> "REJECTED"; default -> value.toUpperCase(Locale.ROOT); };
    }

    @Operation(summary = "认证详情", description = "按 id 查询 Certification 完整信息")
    @GetMapping("/{id}")
    public Result<Certification> get(@PathVariable Long id) {
        return Result.success(repo.findById(id).orElseThrow());
    }

    /** 审核通过 */
    @Operation(summary = "认证审核通过", description = "将 status 置为「已通过」，记录 auditTime")
    @PutMapping("/{id}/pass")
    public Result<Certification> pass(@PathVariable Long id) {
        return Result.success(certificationService.audit(id, true, null));
    }

    /** 审核拒绝 */
    @Operation(summary = "认证审核拒绝", description = "将 status 置为「已拒绝」，记录 rejectReason 和 auditTime")
    @PutMapping("/{id}/reject")
    public Result<Certification> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return Result.success(certificationService.audit(id, false, reason));
    }
}
