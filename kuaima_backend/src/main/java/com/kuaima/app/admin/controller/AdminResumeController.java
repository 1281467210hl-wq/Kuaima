package com.kuaima.app.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kuaima.app.admin.service.AdminRecruitService;
import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.common.util.PdfUtil;
import com.kuaima.app.common.util.ResumeTextExtractor;
import com.kuaima.app.domain.recruit.repository.AdminRecruitResumeRepository;
import com.kuaima.app.domain.resume.entity.Resume;
import com.kuaima.app.domain.resume.entity.ResumeExperience;
import com.kuaima.app.domain.resume.entity.ResumeImportRecord;
import com.kuaima.app.domain.resume.repository.ResumeExperienceRepository;
import com.kuaima.app.domain.resume.service.ResumeFieldParser;
import com.kuaima.app.domain.resume.service.ResumeService;
import com.kuaima.app.domain.user.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;

/**
 * 后台-智能招聘/简历库（平台全局视角，不限定单个老板）。
 * 导入上传复用 boss 端 OSS + 文本解析 + ResumeService.importFromFile 链路，bossId 由参数指定。
 */
@RestController
@RequestMapping("/admin/resumes")
@Tag(name = "后台-智能招聘简历库", description = "全局简历列表/详情/删除/导入")
public class AdminResumeController {

    private final AdminRecruitResumeRepository resumeRepository;
    private final ResumeExperienceRepository experienceRepository;
    private final ResumeService resumeService;
    private final AdminRecruitService recruitService;
    private final UserRepository userRepository;
    private final OssStorageService ossStorageService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final ResumeFieldParser resumeFieldParser;

    public AdminResumeController(AdminRecruitResumeRepository resumeRepository,
                                 ResumeExperienceRepository experienceRepository,
                                 ResumeService resumeService,
                                 AdminRecruitService recruitService,
                                 UserRepository userRepository,
                                 OssStorageService ossStorageService,
                                 ResumeTextExtractor resumeTextExtractor,
                                 ResumeFieldParser resumeFieldParser) {
        this.resumeRepository = resumeRepository;
        this.experienceRepository = experienceRepository;
        this.resumeService = resumeService;
        this.recruitService = recruitService;
        this.userRepository = userRepository;
        this.ossStorageService = ossStorageService;
        this.resumeTextExtractor = resumeTextExtractor;
        this.resumeFieldParser = resumeFieldParser;
    }

    @GetMapping
    @Operation(summary = "简历列表（全局）", description = "tab: all/pending/viewed/sent/fav/new/import/apply；keyword 匹配姓名/岗位/工种/经验/手机号；可按 bossId、status 过滤")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String tab,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long bossId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        Page<Resume> resumes = resumeRepository.searchGlobal(bossId,
                tab == null || tab.isBlank() ? "all" : tab.toLowerCase(),
                blankToNull(keyword), blankToNull(status),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, String> bossNames = recruitService.bossNames(
                resumes.getContent().stream().map(Resume::getBossId).collect(Collectors.toSet()));
        List<Map<String, Object>> rows = resumes.getContent().stream()
                .map(r -> recruitService.resumeRow(r, bossNames.get(r.getBossId())))
                .toList();
        return Result.success(rows, page, resumes.getTotalElements());
    }

    @GetMapping("/{resumeId}")
    @Operation(summary = "简历详情", description = "含教育/工作/项目经历（edu/work/project）与原件预览地址")
    public Result<Map<String, Object>> detail(@PathVariable Long resumeId) {
        Resume resume = requireResume(resumeId);
        List<ResumeExperience> experiences = experienceRepository.findByResumeIdOrderByIdAsc(resumeId);
        Map<String, Object> detail = recruitService.resumeDetail(resume,
                recruitService.bossName(resume.getBossId()), experiences);
        boolean hasPreview = StringUtils.hasText(resume.getPreviewUrl());
        String source = hasPreview ? resume.getPreviewUrl() : resume.getFileUrl();
        detail.put("filePreviewUrl", ossStorageService.playableUrl(source));
        detail.put("filePreviewIsImage", hasPreview || isImageUrl(resume.getFileUrl()));
        return Result.success(detail);
    }

    @DeleteMapping("/{resumeId}")
    @Operation(summary = "删除简历（平台侧，含经历草稿）")
    public Result<Void> delete(@PathVariable Long resumeId) {
        Resume resume = requireResume(resumeId);
        resumeRepository.delete(resume);
        experienceRepository.deleteByResumeId(resumeId);
        return Result.success();
    }

    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入简历文件", description = "form-data: file（必填）+ bossId（必填，归属老板）+ fileName（可选）；上传 OSS、渲染预览图并自动解析字段，生成 IMPORT 来源简历")
    public Result<ResumeImportRecord> createImport(@RequestPart("file") MultipartFile file,
                                                   @RequestParam("bossId") Long bossId,
                                                   @RequestParam(value = "fileName", required = false) String fileName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要导入的简历文件");
        }
        if (bossId == null || !userRepository.existsById(bossId)) {
            throw new IllegalArgumentException("归属老板不存在: " + bossId);
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

        String previewUrl = null;
        if (content != null && PdfUtil.isPdfExtension(ext)) {
            byte[] png = PdfUtil.renderFirstPageToPng(content);
            if (png != null) {
                previewUrl = ossStorageService.upload(png, "image/png", "resume/preview", ".png").get("url");
            }
        }

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
        return Result.success(resumeService.importFromFile(bossId, original, uploaded.get("url"), previewUrl, parsed));
    }

    @GetMapping("/stats")
    @Operation(summary = "简历库统计（全局）", description = "总数/导入数/投递数/收藏数")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", resumeRepository.count());
        stats.put("importCount", resumeRepository.countBySource("IMPORT"));
        stats.put("applyCount", resumeRepository.countBySource("APPLY"));
        stats.put("favoriteCount", resumeRepository.countByFavoriteTrue());
        return Result.success(stats);
    }

    private Resume requireResume(Long resumeId) {
        return resumeRepository.findById(resumeId)
                .orElseThrow(() -> new EntityNotFoundException("简历不存在: " + resumeId));
    }

    private boolean isImageUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String path = url.split("\\?")[0];
        int dot = path.lastIndexOf('.');
        return dot > 0 && PdfUtil.isImageExtension(path.substring(dot));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
