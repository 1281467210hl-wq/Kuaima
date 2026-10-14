package com.kuaima.app.domain.resume.service;

import com.kuaima.app.common.BusinessHttpException;
import com.kuaima.app.domain.resume.entity.Resume;
import com.kuaima.app.domain.resume.entity.ResumeExperience;
import com.kuaima.app.domain.resume.entity.ResumeImportRecord;
import com.kuaima.app.domain.resume.repository.ResumeExperienceRepository;
import com.kuaima.app.domain.resume.repository.ResumeImportRecordRepository;
import com.kuaima.app.domain.resume.repository.ResumeRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepo;
    private final ResumeExperienceRepository expRepo;
    private final ResumeImportRecordRepository importRepo;
    /** 解析开关：关闭时不做解析，导入记录一律记成功（不能把「未开启解析」误报成「解析失败」） */
    private final boolean parseEnabled;

    public ResumeService(ResumeRepository resumeRepo,
                         ResumeExperienceRepository expRepo,
                         ResumeImportRecordRepository importRepo,
                         @Value("${kuaima.resume.parse.enabled:true}") boolean parseEnabled) {
        this.resumeRepo = resumeRepo;
        this.expRepo = expRepo;
        this.importRepo = importRepo;
        this.parseEnabled = parseEnabled;
    }

    public Map<String, Object> stats(Long bossId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", resumeRepo.countByBossId(bossId));
        stats.put("todayNew", resumeRepo.countByBossIdAndDate(bossId, LocalDate.now()));
        stats.put("pending", resumeRepo.countByBossIdAndStatus(bossId, "PENDING"));
        stats.put("sent", resumeRepo.countByBossIdAndStatus(bossId, "SENT"));
        stats.put("favorite", resumeRepo.countByBossIdAndFavoriteTrue(bossId));
        return stats;
    }

    public List<Resume> latest(Long bossId) {
        return resumeRepo.findTop5ByBossIdOrderByIdDesc(bossId);
    }

    public Page<Resume> list(Long bossId, String tab, String keyword, String jobCategory,
                             String education, String expectedSalary, String status,
                             int page, int size) {
        String normalizedTab = tab == null || tab.isBlank() ? "all" : tab.toLowerCase();
        return resumeRepo.search(bossId, normalizedTab,
                blankToNull(keyword), blankToNull(jobCategory), blankToNull(education),
                blankToNull(expectedSalary), blankToNull(status),
                PageRequest.of(page, size));
    }

    public Map<String, Object> detail(Long bossId, Long resumeId) {
        Resume resume = requireOwned(bossId, resumeId);
        List<ResumeExperience> experiences = expRepo.findByResumeIdOrderByIdAsc(resumeId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("resume", resume);
        detail.put("edu", experiences.stream().filter(e -> "EDU".equals(e.getType())).toList());
        detail.put("work", experiences.stream().filter(e -> "WORK".equals(e.getType())).toList());
        detail.put("project", experiences.stream().filter(e -> "PROJECT".equals(e.getType())).toList());
        return detail;
    }

    @Transactional
    public Resume toggleFavorite(Long bossId, Long resumeId) {
        Resume resume = requireOwned(bossId, resumeId);
        resume.setFavorite(!Boolean.TRUE.equals(resume.getFavorite()));
        return resumeRepo.save(resume);
    }

    @Transactional
    public Resume updateStatus(Long bossId, Long resumeId, String status) {
        Resume resume = requireOwned(bossId, resumeId);
        resume.setStatus(status);
        return resumeRepo.save(resume);
    }

    @Transactional
    public void delete(Long bossId, Long resumeId) {
        Resume resume = requireOwned(bossId, resumeId);
        resumeRepo.delete(resume);
        expRepo.deleteByResumeId(resumeId);
    }

    @Transactional
    public Map<String, Object> batch(Long bossId, List<Long> ids, String action) {
        List<Resume> resumes = resumeRepo.findAllById(ids).stream()
                .filter(r -> r.getBossId().equals(bossId)).toList();
        switch (action) {
            case "read" -> resumes.forEach(r -> {
                r.setStatus("VIEWED");
                resumeRepo.save(r);
            });
            case "fav" -> resumes.forEach(r -> {
                r.setFavorite(true);
                resumeRepo.save(r);
            });
            case "del" -> resumes.forEach(r -> delete(bossId, r.getId()));
            case "process" -> resumes.forEach(r -> {
                r.setStatus("SENT");
                resumeRepo.save(r);
            });
            default -> throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "不支持的操作类型");
        }
        return Map.of("count", resumes.size(), "action", action);
    }

    /**
     * 文件导入简历：创建一条 IMPORT 来源的简历草稿，并写入导入记录（回填 resumeId）。
     *
     * <p>若传入文本解析结果，则把解析出的字段补进简历，并写入经历草稿；解析出的字段只覆盖
     * 空值，解析不出来的字段保持不写（不会写入空字符串）。解析失败（parsed 为 null）时仅入库
     * 原件与预览图，状态仍为 SUCCESS。简历、经历与导入记录都在同一事务内落库，避免出现
     * 「有记录无简历」的孤儿导入记录。
     *
     * @param fileName   原始文件名（用于生成简历姓名占位与记录展示）
     * @param fileUrl    OSS 上的可访问地址
     * @param previewUrl OSS 上的预览图地址（PDF 首页渲染，可为 null）
     * @param parsed     文本字段解析结果（可为 null）
     */
    @Transactional
    public ResumeImportRecord importFromFile(Long bossId, String fileName, String fileUrl,
                                             String previewUrl, ResumeFieldParser.ParsedResume parsed) {
        String safeName = (fileName == null || fileName.isBlank()) ? "未命名简历" : fileName.trim();
        if (safeName.length() > 200) {
            safeName = safeName.substring(0, 200);
        }

        Resume resume = new Resume();
        resume.setBossId(bossId);
        // 姓名：解析出的姓名优先，否则沿用「文件名去扩展名」占位
        String parsedName = parsed == null ? null : parsed.getName();
        resume.setName(StringUtils.hasText(parsedName) ? parsedName : deriveResumeName(safeName));
        resume.setStatus("NEW");
        resume.setSource("IMPORT");
        resume.setFavorite(false);
        resume.setFileUrl(fileUrl);
        // 图片简历：原件本身就是可展示内容，无 PDF 渲染预览时直接用原件做预览图
        boolean imageResume = isImageExtension(safeName);
        resume.setPreviewUrl(StringUtils.hasText(previewUrl) ? previewUrl : (imageResume ? fileUrl : null));
        applyParsedFields(resume, parsed);
        Resume saved = resumeRepo.save(resume);

        if (parsed != null && parsed.getExperiences() != null && !parsed.getExperiences().isEmpty()) {
            for (ResumeExperience experience : parsed.getExperiences()) {
                experience.setResumeId(saved.getId());
                expRepo.save(experience);
            }
        }

        ResumeImportRecord record = new ResumeImportRecord();
        record.setBossId(bossId);
        record.setFileName(safeName);
        record.setFileUrl(fileUrl);
        // 开启解析但一个字段都没解析出来时，记录标记为「解析失败」，前端据此展示失败态与「重传」。
        // 图片简历例外：原件即内容，OCR（tesseract）缺失或不识图不算导入失败。
        boolean parseFailed = parseEnabled && !imageResume && (parsed == null || !parsed.hasAnyContent());
        record.setStatus(parseFailed ? "FAILED" : "SUCCESS");
        record.setFailReason(parseFailed ? "未能从文件中解析出简历信息，请重新上传" : null);
        record.setResumeId(saved.getId());
        return importRepo.save(record);
    }

    /** 按文件名后缀判断是否为图片简历。 */
    private static boolean isImageExtension(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".jpe")
                || lower.endsWith(".png") || lower.endsWith(".bmp") || lower.endsWith(".webp")
                || lower.endsWith(".gif");
    }

    /** 把解析出的字段补进简历，只覆盖空值，不写空字符串。 */
    private void applyParsedFields(Resume resume, ResumeFieldParser.ParsedResume parsed) {
        if (parsed == null) {
            return;
        }
        if (!StringUtils.hasText(resume.getGender())) {
            resume.setGender(blankToNull(parsed.getGender()));
        }
        if (resume.getAge() == null) {
            resume.setAge(parsed.getAge());
        }
        if (!StringUtils.hasText(resume.getPhone())) {
            resume.setPhone(blankToNull(parsed.getPhone()));
        }
        if (!StringUtils.hasText(resume.getEmail())) {
            resume.setEmail(blankToNull(parsed.getEmail()));
        }
        if (!StringUtils.hasText(resume.getCity())) {
            resume.setCity(blankToNull(parsed.getCity()));
        }
        if (!StringUtils.hasText(resume.getIdCard())) {
            resume.setIdCard(blankToNull(parsed.getIdCard()));
        }
        if (!StringUtils.hasText(resume.getPosition())) {
            resume.setPosition(blankToNull(parsed.getPosition()));
        }
        if (!StringUtils.hasText(resume.getJobCategory())) {
            resume.setJobCategory(blankToNull(parsed.getJobCategory()));
        }
        if (!StringUtils.hasText(resume.getEducation())) {
            resume.setEducation(blankToNull(parsed.getEducation()));
        }
        if (!StringUtils.hasText(resume.getExperience())) {
            resume.setExperience(blankToNull(parsed.getExperience()));
        }
        if (!StringUtils.hasText(resume.getExpectedSalary())) {
            resume.setExpectedSalary(blankToNull(parsed.getExpectedSalary()));
        }
        if (!StringUtils.hasText(resume.getWorkLocation())) {
            resume.setWorkLocation(blankToNull(parsed.getWorkLocation()));
        }
    }

    /** 用文件名（去掉扩展名）作为简历姓名占位，按实体列长 50 截断。 */
    private String deriveResumeName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = (dot > 0 ? fileName.substring(0, dot) : fileName).trim();
        if (base.isEmpty()) {
            base = "未命名简历";
        }
        return base.length() > 50 ? base.substring(0, 50) : base;
    }

    public List<ResumeImportRecord> listImports(Long bossId) {
        return importRepo.findTop20ByBossIdOrderByIdDesc(bossId);
    }

    private Resume requireOwned(Long bossId, Long resumeId) {
        Resume resume = resumeRepo.findById(resumeId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "简历不存在"));
        if (!resume.getBossId().equals(bossId)) {
            throw new BusinessHttpException(HttpStatus.FORBIDDEN, "无权操作该简历");
        }
        return resume;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
