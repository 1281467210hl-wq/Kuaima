package com.kuaima.app.admin.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.service.AdminRecruitService;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.position.entity.BossPosition;
import com.kuaima.app.domain.position.repository.BossPositionRepository;
import com.kuaima.app.domain.recruit.entity.PositionApplication;
import com.kuaima.app.domain.recruit.repository.AdminRecruitResumeRepository;
import com.kuaima.app.domain.recruit.repository.PositionApplicationRepository;
import com.kuaima.app.domain.resume.entity.Resume;
import com.kuaima.app.domain.resume.entity.ResumeExperience;
import com.kuaima.app.domain.resume.repository.ResumeExperienceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;

/**
 * 后台-智能招聘/候选人（岗位投递视角，平台全局）。
 * 数据来自 position_application 表，姓名/经历等展示字段回联简历与岗位。
 */
@RestController
@RequestMapping("/admin/candidates")
@Tag(name = "后台-智能招聘候选人", description = "岗位投递列表/详情/状态流转")
public class AdminCandidateController {

    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "PREINTERVIEW", "INTERVIEWING", "HIRED", "REJECTED");

    private final PositionApplicationRepository applicationRepository;
    private final AdminRecruitResumeRepository resumeRepository;
    private final ResumeExperienceRepository experienceRepository;
    private final BossPositionRepository positionRepository;
    private final AdminRecruitService recruitService;

    public AdminCandidateController(PositionApplicationRepository applicationRepository,
                                    AdminRecruitResumeRepository resumeRepository,
                                    ResumeExperienceRepository experienceRepository,
                                    BossPositionRepository positionRepository,
                                    AdminRecruitService recruitService) {
        this.applicationRepository = applicationRepository;
        this.resumeRepository = resumeRepository;
        this.experienceRepository = experienceRepository;
        this.positionRepository = positionRepository;
        this.recruitService = recruitService;
    }

    @GetMapping
    @Operation(summary = "候选人列表", description = "status: PENDING/PREINTERVIEW/INTERVIEWING/HIRED/REJECTED；keyword 匹配简历姓名/意向岗位；支持 bossId、positionId 与申请日期区间（startDate/endDate）")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long bossId,
                                                  @RequestParam(required = false) Long positionId,
                                                  @RequestParam(required = false) LocalDate startDate,
                                                  @RequestParam(required = false) LocalDate endDate,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        List<Long> resumeIds = null;
        if (keyword != null && !keyword.isBlank()) {
            resumeIds = resumeRepository.findIdsMatchingKeyword(keyword.trim());
            if (resumeIds.isEmpty()) {
                return Result.success(List.of(), page, 0L);
            }
        }
        Page<PositionApplication> applications = applicationRepository.search(
                blankToNormalStatus(status), bossId, positionId, resumeIds, startDate, endDate,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<Map<String, Object>> rows = enrichRows(applications.getContent());
        return Result.success(rows, page, applications.getTotalElements());
    }

    @GetMapping("/{id}")
    @Operation(summary = "候选人详情", description = "投递信息 + 简历主体 + 教育/工作/项目经历")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        PositionApplication application = requireApplication(id);
        Resume resume = resumeRepository.findById(application.getResumeId()).orElse(null);
        BossPosition position = positionRepository.findById(application.getPositionId()).orElse(null);
        List<ResumeExperience> experiences = resume == null ? List.of()
                : experienceRepository.findByResumeIdOrderByIdAsc(resume.getId());
        Map<String, Object> detail = recruitService.candidateDetail(application, position, resume,
                recruitService.bossName(application.getBossId()), experiences);
        return Result.success(detail);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新候选人状态", description = "body: {status: PENDING|PREINTERVIEW|INTERVIEWING|HIRED|REJECTED, interviewTime?: 'yyyy-MM-dd HH:mm[:ss]', remark?}；进入面试态可附带面试时间")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        PositionApplication application = requireApplication(id);
        String status = body.get("status");
        if (status == null || !ALLOWED_STATUS.contains(status.trim().toUpperCase())) {
            throw new IllegalArgumentException("非法状态，仅支持 PENDING/PREINTERVIEW/INTERVIEWING/HIRED/REJECTED");
        }
        application.setStatus(status.trim().toUpperCase());
        String interviewTime = body.get("interviewTime");
        if (interviewTime != null && !interviewTime.isBlank()) {
            application.setInterviewTime(parseDateTime(interviewTime));
        }
        if (body.get("remark") != null) {
            application.setRemark(body.get("remark"));
        }
        if (!"PENDING".equals(application.getStatus()) && !"INTERVIEWING".equals(application.getStatus())) {
            application.setDecisionTime(LocalDateTime.now());
        }
        applicationRepository.save(application);
        return detail(id);
    }

    @PostMapping
    @Operation(summary = "手动新增候选人（运营兜底：worker 报名链路暂未写投递表时使用）",
            description = "body: {positionId, resumeId, remark?}；bossId 取岗位 createBy，userId 取简历 userId，状态初始 PENDING，岗位 applyCount+1")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long positionId = toLong(body.get("positionId"));
        Long resumeId = toLong(body.get("resumeId"));
        if (positionId == null || resumeId == null) {
            throw new IllegalArgumentException("positionId、resumeId 均必填");
        }
        BossPosition position = positionRepository.findById(positionId)
                .orElseThrow(() -> new EntityNotFoundException("岗位不存在: " + positionId));
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new EntityNotFoundException("简历不存在: " + resumeId));

        PositionApplication application = new PositionApplication();
        application.setPositionId(position.getId());
        application.setResumeId(resume.getId());
        application.setUserId(resume.getUserId());
        application.setBossId(position.getCreateBy());
        application.setStatus("PENDING");
        application.setApplyTime(LocalDateTime.now());
        Object remark = body.get("remark");
        if (remark != null && !String.valueOf(remark).isBlank()) {
            application.setRemark(String.valueOf(remark));
        }
        applicationRepository.save(application);

        position.setApplyCount((position.getApplyCount() == null ? 0 : position.getApplyCount()) + 1);
        positionRepository.save(position);
        return detail(application.getId());
    }

    @PostMapping("/from-resume")
    @Operation(summary = "简历库-加入候选人",
            description = "body: {resumeId}；按「简历意向岗位 ↔ 岗位名称」定位岗位，命中且无同(position,resume)投递时创建 source=MANUAL 的 PENDING 候选人（岗位 applyCount+1）；"
                    + "匹配不到岗位时返回 400，前端据此回退到岗位下拉手动选择。响应带 created=false 表示已在候选人列表中。")
    public Result<Map<String, Object>> fromResume(@RequestBody Map<String, Object> body) {
        Long resumeId = toLong(body.get("resumeId"));
        if (resumeId == null) {
            throw new IllegalArgumentException("resumeId 必填");
        }
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new EntityNotFoundException("简历不存在: " + resumeId));
        BossPosition position = resolvePositionByResumeIntent(resume);
        if (position == null) {
            throw new IllegalArgumentException("未能按意向岗位「" + resumeIntent(resume)
                    + "」匹配到岗位，请手动选择应聘岗位");
        }

        boolean created = false;
        // 复用 search（positionId + resumeIds 均走索引）判重，避免整岗位投递记录载入内存
        PositionApplication application = applicationRepository
                .search(null, null, position.getId(), List.of(resume.getId()), null, null, PageRequest.of(0, 1))
                .getContent().stream().findFirst().orElse(null);
        if (application == null) {
            application = new PositionApplication();
            application.setPositionId(position.getId());
            application.setResumeId(resume.getId());
            application.setUserId(resume.getUserId());
            application.setBossId(position.getCreateBy());
            application.setStatus("PENDING");
            application.setSource("MANUAL");
            application.setApplyTime(LocalDateTime.now());
            applicationRepository.save(application);
            position.setApplyCount((position.getApplyCount() == null ? 0 : position.getApplyCount()) + 1);
            positionRepository.save(position);
            created = true;
        }

        Map<String, Object> row = detail(application.getId()).getData();
        row.put("created", created);
        return Result.success(row);
    }

    /**
     * 简历意向岗位 → 岗位：沿用 searchByKeywordAndStatus 的 LIKE 命中集（限量 50），再要求岗位名称完全一致；
     * 同名多条时优先在招（status=on）岗位，其次取最新创建的岗位。
     */
    private BossPosition resolvePositionByResumeIntent(Resume resume) {
        String intent = resumeIntent(resume);
        if (intent.isEmpty()) {
            return null;
        }
        List<BossPosition> matched = positionRepository
                .searchByKeywordAndStatus(intent, null, PageRequest.of(0, 50)).getContent().stream()
                .filter(p -> intent.equals(p.getName() == null ? null : p.getName().trim()))
                .toList();
        return matched.stream().filter(p -> "on".equals(p.getStatus())).findFirst()
                .orElse(matched.isEmpty() ? null : matched.get(0));
    }

    private String resumeIntent(Resume resume) {
        return resume.getPosition() == null ? "" : resume.getPosition().trim();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法 id: " + value);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除候选人投递记录（岗位 applyCount 同步 -1，不为负）")
    public Result<Void> delete(@PathVariable Long id) {
        PositionApplication application = requireApplication(id);
        positionRepository.findById(application.getPositionId()).ifPresent(p -> {
            int count = p.getApplyCount() == null ? 0 : p.getApplyCount();
            p.setApplyCount(Math.max(0, count - 1));
            positionRepository.save(p);
        });
        applicationRepository.delete(application);
        return Result.success();
    }

    /** 批量回联简历/岗位/老板名/首条教育经历，避免 N+1 查询。 */
    private List<Map<String, Object>> enrichRows(List<PositionApplication> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        Set<Long> resumeIds = applications.stream().map(PositionApplication::getResumeId)
                .collect(Collectors.toSet());
        Map<Long, Resume> resumes = resumeRepository.findAllById(resumeIds).stream()
                .collect(Collectors.toMap(Resume::getId, r -> r));
        Set<Long> positionIds = applications.stream().map(PositionApplication::getPositionId)
                .collect(Collectors.toSet());
        Map<Long, BossPosition> positions = positionRepository.findAllById(positionIds).stream()
                .collect(Collectors.toMap(BossPosition::getId, p -> p));
        Map<Long, String> bossNames = recruitService.bossNames(applications.stream()
                .map(PositionApplication::getBossId).collect(Collectors.toSet()));
        Map<Long, ResumeExperience> firstEdu = new HashMap<>();
        for (Long resumeId : resumes.keySet()) {
            experienceRepository.findByResumeIdOrderByIdAsc(resumeId).stream()
                    .filter(e -> "EDU".equals(e.getType())).findFirst()
                    .ifPresent(e -> firstEdu.put(resumeId, e));
        }
        return applications.stream().map(a -> recruitService.candidateRow(a,
                positions.get(a.getPositionId()), resumes.get(a.getResumeId()),
                bossNames.get(a.getBossId()), firstEdu.get(a.getResumeId()))).toList();
    }

    private PositionApplication requireApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("投递记录不存在: " + id));
    }

    private String blankToNormalStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new IllegalArgumentException("非法状态，仅支持 PENDING/PREINTERVIEW/INTERVIEWING/HIRED/REJECTED");
        }
        return normalized;
    }

    private LocalDateTime parseDateTime(String value) {
        String text = value.trim().replace('T', ' ');
        try {
            if (text.length() <= 16) {
                return LocalDateTime.parse(text + ":00", AdminRecruitService.MINUTE_FORMAT);
            }
            return LocalDateTime.parse(text, AdminRecruitService.TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式应为 yyyy-MM-dd HH:mm[:ss]: " + value);
        }
    }
}
