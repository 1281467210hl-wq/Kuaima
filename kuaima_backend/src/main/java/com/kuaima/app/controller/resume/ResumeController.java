package com.kuaima.app.controller.resume;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.util.PdfUtil;
import com.kuaima.app.common.util.ResumeTextExtractor;
import com.kuaima.app.domain.resume.entity.Resume;
import com.kuaima.app.domain.resume.entity.ResumeImportRecord;
import com.kuaima.app.domain.resume.repository.ResumeRepository;
import com.kuaima.app.domain.resume.service.ResumeFieldParser;
import com.kuaima.app.domain.resume.service.ResumeService;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/boss/resumes")
@Tag(name = "老板-简历库", description = "简历库统计、列表筛选、详情、收藏、批量操作、简历导入")
public class ResumeController {

    private final ResumeService service;
    private final ResumeRepository resumeRepository;
    private final OssStorageService ossStorageService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final ResumeFieldParser resumeFieldParser;

    public ResumeController(ResumeService service, ResumeRepository resumeRepository,
                            OssStorageService ossStorageService,
                            ResumeTextExtractor resumeTextExtractor, ResumeFieldParser resumeFieldParser) {
        this.service = service;
        this.resumeRepository = resumeRepository;
        this.ossStorageService = ossStorageService;
        this.resumeTextExtractor = resumeTextExtractor;
        this.resumeFieldParser = resumeFieldParser;
    }

    @GetMapping("/stats")
    @Operation(summary = "简历库统计：总数/今日新增/待处理/已投递/收藏 + 增长率（totalRate 近7天环比、todayNewRate 今日对昨日）")
    public Result<Map<String, Object>> stats(Authentication auth) {
        Long bossId = currentUserId(auth);
        Map<String, Object> stats = new LinkedHashMap<>(service.stats(bossId));
        LocalDate today = LocalDate.now();
        long last7 = resumeRepository.countByBossIdAndDateBetween(bossId, today.minusDays(6), today);
        long previous7 = resumeRepository.countByBossIdAndDateBetween(bossId, today.minusDays(13), today.minusDays(7));
        long todayNew = resumeRepository.countByBossIdAndDate(bossId, today);
        long yesterdayNew = resumeRepository.countByBossIdAndDate(bossId, today.minusDays(1));
        stats.put("totalRate", growthRate(last7, previous7));
        stats.put("todayNewRate", growthRate(todayNew, yesterdayNew));
        return Result.success(stats);
    }

    /** 增长率百分比：(本期-上期)/上期，负增长返回负数；上期为 0 时除零兜底（本期有新增记 100，否则 0）。 */
    private static int growthRate(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? 100 : 0;
        }
        return (int) ((current - previous) * 100 / previous);
    }

    @GetMapping("/latest")
    @Operation(summary = "最新简历（首页展示，最多5条）")
    public Result<List<Resume>> latest(Authentication auth) {
        return Result.success(service.latest(currentUserId(auth)));
    }

    @GetMapping
    @Operation(summary = "简历列表（tab: all/pending/viewed/sent/fav；支持关键词与筛选）")
    public Result<Page<Resume>> list(Authentication auth,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String jobCategory,
            @RequestParam(required = false) String education,
            @RequestParam(required = false) String expectedSalary,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.list(currentUserId(auth), tab, keyword, jobCategory,
                education, expectedSalary, status, page, size));
    }

    @GetMapping("/{resumeId}")
    @Operation(summary = "简历详情（含教育/工作/项目经历）")
    public Result<Map<String, Object>> detail(Authentication auth, @PathVariable Long resumeId) {
        Map<String, Object> detail = service.detail(currentUserId(auth), resumeId);
        // 优先给「预览图」（PDF 首页渲染的 PNG）；没有预览图则回退原始文件
        // 私有 Bucket 下返回带签名的临时地址，公开 Bucket 原样返回
        Object resume = detail.get("resume");
        if (resume instanceof Resume r) {
            boolean hasPreview = StringUtils.hasText(r.getPreviewUrl());
            String source = hasPreview ? r.getPreviewUrl() : r.getFileUrl();
            detail.put("filePreviewUrl", ossStorageService.playableUrl(source));
            // 前端据此决定：图片 → previewImage 直接看；其他 → downloadFile + openDocument
            detail.put("filePreviewIsImage", hasPreview || isImageUrl(r.getFileUrl()));
        }
        return Result.success(detail);
    }

    /** 按扩展名判断原始文件是否为图片（忽略 URL 上的签名参数）。 */
    private boolean isImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String path = url.split("\\?")[0];
        int dot = path.lastIndexOf('.');
        return dot > 0 && PdfUtil.isImageExtension(path.substring(dot));
    }

    @PutMapping("/{resumeId}/favorite")
    @Operation(summary = "收藏/取消收藏")
    public Result<Resume> toggleFavorite(Authentication auth, @PathVariable Long resumeId) {
        return Result.success(service.toggleFavorite(currentUserId(auth), resumeId));
    }

    @PutMapping("/{resumeId}/status")
    @Operation(summary = "更新简历状态（NEW/PENDING/VIEWED/SENT）")
    public Result<Resume> updateStatus(Authentication auth, @PathVariable Long resumeId,
            @RequestBody Map<String, String> body) {
        return Result.success(service.updateStatus(currentUserId(auth), resumeId,
                body.getOrDefault("status", "VIEWED")));
    }

    @DeleteMapping("/{resumeId}")
    @Operation(summary = "删除简历")
    public Result<Void> delete(Authentication auth, @PathVariable Long resumeId) {
        service.delete(currentUserId(auth), resumeId);
        return Result.success();
    }

    @PostMapping("/batch")
    @Operation(summary = "批量操作（action: read/fav/del/process）")
    public Result<Map<String, Object>> batch(Authentication auth, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) body.getOrDefault("ids", List.of());
        List<Long> ids = rawIds.stream().map(Number::longValue).toList();
        String action = String.valueOf(body.getOrDefault("action", ""));
        return Result.success(service.batch(currentUserId(auth), ids, action));
    }

    @PostMapping(value = "/imports", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入简历文件", description = "form-data: file（必填）+ fileName（可选，默认取原始文件名）；文件上传 OSS 并自动创建一条 IMPORT 来源的简历草稿，返回带 resumeId 的导入记录")
    public Result<ResumeImportRecord> createImport(Authentication auth,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "fileName", required = false) String fileName) {
        Long userId = currentUserId(auth);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的简历文件");
        }
        String original = StringUtils.hasText(fileName) ? fileName.trim() : file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            original = "未命名简历";
        }
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot > 0) {
            ext = original.substring(dot).toLowerCase();
        }
        Map<String, String> uploaded = ossStorageService.upload(file, "resume", ext);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (java.io.IOException e) {
            content = null;
        }

        // PDF 额外渲染首页为 PNG 预览图，小程序侧可直接当图片看，不依赖 downloadFile 域名白名单
        String previewUrl = null;
        if (content != null && PdfUtil.isPdfExtension(ext)) {
            byte[] png = PdfUtil.renderFirstPageToPng(content);
            if (png != null) {
                previewUrl = ossStorageService.upload(png, "image/png", "resume/preview", ".png").get("url");
            }
        }

        // 提取正文 → 解析字段；任何一步失败都只降级为「不解析」，不影响原件入库
        ResumeFieldParser.ParsedResume parsed = null;
        if (content != null) {
            try {
                String text = resumeTextExtractor.extract(content, ext);
                if (StringUtils.hasText(text)) {
                    parsed = resumeFieldParser.parse(text);
                }
            } catch (RuntimeException e) {
                parsed = null;
            }
        }
        return Result.success(service.importFromFile(userId, original, uploaded.get("url"), previewUrl, parsed));
    }

    @GetMapping("/imports")
    @Operation(summary = "导入记录列表")
    public Result<List<ResumeImportRecord>> listImports(Authentication auth) {
        return Result.success(service.listImports(currentUserId(auth)));
    }

    private Long currentUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof LoginUser login && login.id() != null) {
            return login.id();
        }
        throw new com.kuaima.app.common.ForbiddenBusinessException("请先登录老板账号");
    }
}
