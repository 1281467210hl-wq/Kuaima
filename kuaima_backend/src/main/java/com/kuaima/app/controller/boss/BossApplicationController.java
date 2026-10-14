package com.kuaima.app.controller.boss;

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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.service.AdminRecruitService;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.position.entity.BossPosition;
import com.kuaima.app.domain.position.repository.BossPositionRepository;
import com.kuaima.app.domain.recruit.entity.PositionApplication;
import com.kuaima.app.domain.recruit.repository.AdminRecruitResumeRepository;
import com.kuaima.app.domain.recruit.repository.PositionApplicationRepository;
import com.kuaima.app.domain.resume.entity.Resume;
import com.kuaima.app.domain.resume.entity.ResumeExperience;
import com.kuaima.app.domain.resume.repository.ResumeExperienceRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;

/**
 * 老板端-候选人（岗位投递视角，仅当前登录老板 bossId 的投递）。
 * 数据来自 position_application 表（与 admin /admin/candidates 同源），
 * 行/详情字段复用 AdminRecruitService.candidateRow/candidateDetail，避免 N+1 批量回联。
 * 状态机 6 态：PENDING 待筛选 / PREINTERVIEW 待面试 / INTERVIEWING 面试中 / HIRED 已录用 / REJECTED 已拒绝（all=全部）。
 */
@RestController
@RequestMapping("/boss/applications")
@Tag(name = "老板端候选人", description = "岗位投递列表/详情/状态流转/面试安排")
public class BossApplicationController {

    private static final Set<String> ALLOWED_STATUS = Set.of("PENDING", "PREINTERVIEW", "INTERVIEWING", "HIRED", "REJECTED");

    private final PositionApplicationRepository applicationRepository;
    private final AdminRecruitResumeRepository resumeRepository;
    private final ResumeExperienceRepository experienceRepository;
    private final BossPositionRepository positionRepository;
    private final AdminRecruitService recruitService;

    public BossApplicationController(PositionApplicationRepository applicationRepository,
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
    @Operation(summary = "候选人列表", description = "status: all|PENDING|PREINTERVIEW|INTERVIEWING|HIRED|REJECTED；keyword 匹配简历姓名/意向岗位；positionId 可选；仅返回当前登录老板的投递")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false, defaultValue = "all") String status,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long positionId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  Authentication authentication) {
        Long bossId = requireBossId(authentication);
        List<Long> resumeIds = null;
        if (keyword != null && !keyword.isBlank()) {
            resumeIds = resumeRepository.findIdsMatchingKeyword(keyword.trim());
            if (resumeIds.isEmpty()) {
                return Result.success(List.of(), page, 0L);
            }
        }
        Page<PositionApplication> applications = applicationRepository.search(
                normalizeStatus(status), bossId, positionId, resumeIds, null, null,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "id")));
        List<Map<String, Object>> rows = enrichRows(applications.getContent());
        return Result.success(rows, page, applications.getTotalElements());
    }

    @GetMapping("/{id}")
    @Operation(summary = "候选人详情", description = "投递信息 + 简历主体 + 教育/工作/项目经历；仅限本人（bossId）投递")
    public Result<Map<String, Object>> detail(@PathVariable Long id, Authentication authentication) {
        PositionApplication application = requireOwnedApplication(id, authentication);
        Resume resume = resumeRepository.findById(application.getResumeId()).orElse(null);
        BossPosition position = positionRepository.findById(application.getPositionId()).orElse(null);
        List<ResumeExperience> experiences = resume == null ? List.of()
                : experienceRepository.findByResumeIdOrderByIdAsc(resume.getId());
        Map<String, Object> detail = recruitService.candidateDetail(application, position, resume,
                recruitService.bossName(application.getBossId()), experiences);
        detail.put("interviewLocation", application.getInterviewLocation());
        detail.put("interviewer", application.getInterviewer());
        return Result.success(detail);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新候选人状态",
            description = "body: {status: PENDING|PREINTERVIEW|INTERVIEWING|HIRED|REJECTED, interviewTime?: 'yyyy-MM-dd HH:mm[:ss]', remark?}；越权 403")
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body,
                                                    Authentication authentication) {
        PositionApplication application = requireOwnedApplication(id, authentication);
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
        if ("HIRED".equals(application.getStatus()) || "REJECTED".equals(application.getStatus())) {
            application.setDecisionTime(LocalDateTime.now());
        }
        applicationRepository.save(application);
        return detail(id, authentication);
    }

    @PostMapping("/{id}/interview")
    @Operation(summary = "安排面试",
            description = "body: {interviewTime: 'yyyy-MM-dd HH:mm[:ss]', location?, interviewer?}；保存后状态置 INTERVIEWING 并记 interviewTime/interview_location/interviewer；越权 403")
    public Result<Map<String, Object>> arrangeInterview(@PathVariable Long id,
                                                        @RequestBody Map<String, String> body,
                                                        Authentication authentication) {
        PositionApplication application = requireOwnedApplication(id, authentication);
        String interviewTime = body == null ? null : body.get("interviewTime");
        if (interviewTime == null || interviewTime.isBlank()) {
            throw new IllegalArgumentException("interviewTime 必填，格式 yyyy-MM-dd HH:mm[:ss]");
        }
        application.setInterviewTime(parseDateTime(interviewTime));
        if (body.get("location") != null && !body.get("location").isBlank()) {
            application.setInterviewLocation(body.get("location").trim());
        }
        if (body.get("interviewer") != null && !body.get("interviewer").isBlank()) {
            application.setInterviewer(body.get("interviewer").trim());
        }
        application.setStatus("INTERVIEWING");
        applicationRepository.save(application);
        return detail(id, authentication);
    }

    /** 批量回联简历/岗位/老板名/首条教育经历，避免 N+1 查询（对齐 admin 候选人列表行字段）。 */
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
        List<Map<String, Object>> rows = applications.stream().map(a -> {
            Map<String, Object> row = recruitService.candidateRow(a,
                    positions.get(a.getPositionId()), resumes.get(a.getResumeId()),
                    bossNames.get(a.getBossId()), firstEdu.get(a.getResumeId()));
            row.put("interviewLocation", a.getInterviewLocation());
            row.put("interviewer", a.getInterviewer());
            return row;
        }).toList();
        return rows;
    }

    private PositionApplication requireOwnedApplication(Long id, Authentication authentication) {
        Long bossId = requireBossId(authentication);
        PositionApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("投递记录不存在: " + id));
        if (!bossId.equals(application.getBossId())) {
            throw new ForbiddenBusinessException("无权访问该候选人投递");
        }
        return application;
    }

    private Long requireBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.BOSS.equals(loginUser.role()) && loginUser.id() != null) {
            return loginUser.id();
        }
        throw new ForbiddenBusinessException("当前登录账号不是有效的老板账号");
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new IllegalArgumentException("非法状态，仅支持 all/PENDING/PREINTERVIEW/INTERVIEWING/HIRED/REJECTED");
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
