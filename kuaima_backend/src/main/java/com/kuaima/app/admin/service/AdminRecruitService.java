package com.kuaima.app.admin.service;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.position.entity.BossPosition;
import com.kuaima.app.domain.recruit.entity.LiveShowcase;
import com.kuaima.app.domain.recruit.entity.PositionApplication;
import com.kuaima.app.domain.resume.entity.Resume;
import com.kuaima.app.domain.resume.entity.ResumeExperience;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

/**
 * admin 智能招聘视图组装：把 Boss/简历/岗位/投递/橱窗实体映射为 admin-web SmartRecruit 页字段。
 * 字段命名对齐前端 DEMO_POSITIONS / DEMO_RESUMES / DEMO_CANDIDATES / DEMO_LIVES。
 */
@Service
public class AdminRecruitService {

    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<String, String> APPLICATION_STATUS_TEXT = Map.of(
            "PENDING", "待筛选",
            "INTERVIEWING", "面试中",
            "HIRED", "已录用",
            "REJECTED", "已拒绝");

    private final UserRepository userRepository;

    public AdminRecruitService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ==================== 岗位 ====================

    /** 岗位行：type/headcount/applied/interviewed/hired + recruiting/paused/filled 状态映射。 */
    public Map<String, Object> positionRow(BossPosition p, String bossName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("code", p.getCode());
        row.put("name", p.getName());
        row.put("type", p.getCategory());
        row.put("department", p.getDepartment());
        row.put("location", p.getLocation());
        row.put("salaryRange", p.getSalaryRange());
        row.put("experience", p.getExperience());
        row.put("education", p.getEducation());
        row.put("headcount", p.getHireCount());
        row.put("applied", p.getApplyCount());
        row.put("interviewed", p.getInterviewCount());
        row.put("hired", p.getHiredCount());
        String uiStatus = positionUiStatus(p);
        row.put("status", uiStatus);
        row.put("rawStatus", p.getStatus());
        row.put("statusName", positionStatusName(uiStatus));
        row.put("statusClass", positionStatusClass(uiStatus));
        row.put("publishTime", p.getPublishTime() == null ? null : p.getPublishTime().format(TIME_FORMAT));
        row.put("bossId", p.getCreateBy());
        row.put("bossName", bossName);
        return row;
    }

    /** on 在招中（招满则 filled）/ off 已暂停，对齐前端 recruiting/paused/filled。 */
    public String positionUiStatus(BossPosition p) {
        if (!"on".equals(p.getStatus())) {
            return "paused";
        }
        int hire = p.getHireCount() == null ? 0 : p.getHireCount();
        int hired = p.getHiredCount() == null ? 0 : p.getHiredCount();
        return hire > 0 && hired >= hire ? "filled" : "recruiting";
    }

    private String positionStatusName(String uiStatus) {
        return switch (uiStatus) {
            case "recruiting" -> "招聘中";
            case "filled" -> "已满编";
            default -> "已暂停";
        };
    }

    private String positionStatusClass(String uiStatus) {
        return switch (uiStatus) {
            case "recruiting" -> "success";
            case "filled" -> "info";
            default -> "warning";
        };
    }

    // ==================== 简历 ====================

    /** 简历行：name/job/exp/edu/uploadTime 对齐 DEMO_RESUMES。 */
    public Map<String, Object> resumeRow(Resume r, String bossName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.getId());
        row.put("name", r.getName());
        row.put("job", r.getPosition());
        row.put("exp", r.getExperience());
        row.put("edu", r.getEducation());
        row.put("uploadTime", r.getTimestamp() == null ? null : r.getTimestamp().toString());
        row.put("gender", r.getGender());
        row.put("age", r.getAge());
        row.put("phone", r.getPhone());
        row.put("city", r.getCity());
        row.put("jobCategory", r.getJobCategory());
        row.put("expectedSalary", r.getExpectedSalary());
        row.put("status", r.getStatus());
        row.put("favorite", r.getFavorite());
        row.put("source", r.getSource());
        row.put("bossId", r.getBossId());
        row.put("bossName", bossName);
        return row;
    }

    /** 简历详情：主体 + 教育/工作/项目经历（edu/work/project）。 */
    public Map<String, Object> resumeDetail(Resume r, String bossName, List<ResumeExperience> experiences) {
        Map<String, Object> detail = resumeRow(r, bossName);
        detail.put("fileUrl", r.getFileUrl());
        detail.put("previewUrl", r.getPreviewUrl());
        detail.put("edu", experiences.stream().filter(e -> "EDU".equals(e.getType())).map(this::experienceRow).toList());
        detail.put("work", experiences.stream().filter(e -> "WORK".equals(e.getType())).map(this::experienceRow).toList());
        detail.put("project", experiences.stream().filter(e -> "PROJECT".equals(e.getType())).map(this::experienceRow).toList());
        return detail;
    }

    public Map<String, Object> experienceRow(ResumeExperience e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("type", e.getType());
        row.put("title", e.getTitle());
        row.put("org", e.getOrg());
        row.put("startDate", e.getStartDate());
        row.put("endDate", e.getEndDate());
        row.put("description", e.getDescription());
        row.put("tags", e.getTags());
        return row;
    }

    // ==================== 候选人（岗位投递） ====================

    /** 候选人行：name/job/applyTime/interviewTime/statusName 对齐 DEMO_CANDIDATES。 */
    public Map<String, Object> candidateRow(PositionApplication a, BossPosition position, Resume resume,
                                            String bossName, ResumeExperience firstEdu) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.getId());
        row.put("positionId", a.getPositionId());
        row.put("resumeId", a.getResumeId());
        row.put("userId", a.getUserId());
        row.put("bossId", a.getBossId());
        row.put("bossName", bossName);
        row.put("job", position == null ? null : position.getName());
        row.put("applyTime", a.getApplyTime() == null ? null : a.getApplyTime().format(MINUTE_FORMAT));
        row.put("interviewTime", a.getInterviewTime() == null ? "—"
                : a.getInterviewTime().format(MINUTE_FORMAT));
        row.put("status", a.getStatus());
        row.put("statusName", APPLICATION_STATUS_TEXT.getOrDefault(a.getStatus(), a.getStatus()));
        row.put("remark", a.getRemark());
        if (resume != null) {
            row.put("name", resume.getName());
            row.put("gender", resume.getGender());
            row.put("age", resume.getAge());
            row.put("phone", resume.getPhone());
            row.put("city", resume.getCity());
            row.put("expLabel", resume.getExperience());
            row.put("edu", resume.getEducation());
        }
        if (firstEdu != null) {
            row.put("school", firstEdu.getTitle());
            row.put("major", firstEdu.getOrg());
            row.put("schoolYears", joinYears(firstEdu));
            row.put("schoolTag", resume == null ? null : resume.getEducation());
        }
        return row;
    }

    /** 候选人详情：行字段 + 简历原件信息 + 三类经历。 */
    public Map<String, Object> candidateDetail(PositionApplication a, BossPosition position, Resume resume,
                                               String bossName, List<ResumeExperience> experiences) {
        ResumeExperience firstEdu = experiences.stream()
                .filter(e -> "EDU".equals(e.getType())).findFirst().orElse(null);
        Map<String, Object> detail = candidateRow(a, position, resume, bossName, firstEdu);
        detail.put("positionName", position == null ? null : position.getName());
        detail.put("decisionTime", a.getDecisionTime() == null ? null : a.getDecisionTime().format(TIME_FORMAT));
        if (resume != null) {
            detail.put("resume", resumeRow(resume, bossName));
            detail.put("edu", experiences.stream().filter(e -> "EDU".equals(e.getType())).map(this::experienceRow).toList());
            detail.put("work", experiences.stream().filter(e -> "WORK".equals(e.getType())).map(this::experienceRow).toList());
            detail.put("project", experiences.stream().filter(e -> "PROJECT".equals(e.getType())).map(this::experienceRow).toList());
        }
        return detail;
    }

    private String joinYears(ResumeExperience edu) {
        if (!StringUtils.hasText(edu.getStartDate())) {
            return null;
        }
        return StringUtils.hasText(edu.getEndDate())
                ? edu.getStartDate() + " ~ " + edu.getEndDate() : edu.getStartDate() + " ~";
    }

    // ==================== 直播橱窗 ====================

    /** 橱窗行：title/host/account/time 对齐 DEMO_LIVES。 */
    public Map<String, Object> showcaseRow(LiveShowcase s) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId());
        row.put("title", s.getTitle());
        row.put("host", s.getAnchorName());
        row.put("account", s.getDouyinAccount());
        row.put("time", s.getLiveTime() != null ? s.getLiveTime().format(MINUTE_FORMAT)
                : (s.getTimestamp() == null ? null : s.getTimestamp().toString()));
        row.put("status", s.getStatus());
        row.put("statusName", showcaseStatusName(s.getStatus()));
        row.put("bossId", s.getBossId());
        row.put("bossName", s.getBossName());
        return row;
    }

    public static String showcaseStatusName(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "LIVE" -> "直播中";
            case "ENDED" -> "已结束";
            default -> "待开播";
        };
    }

    // ==================== bossId → 名称（沿用 user.companyName > realName > nickname > username） ====================

    public Map<Long, String> bossNames(Collection<Long> bossIds) {
        Set<Long> ids = bossIds.stream().filter(Objects::nonNull).filter(id -> id > 0).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> map = new LinkedHashMap<>();
        userRepository.findAllById(ids).forEach(u -> map.put(u.getId(), displayName(u)));
        return map;
    }

    public String bossName(Long bossId) {
        return bossId == null ? null : userRepository.findById(bossId).map(this::displayName).orElse(null);
    }

    private String displayName(User user) {
        if (StringUtils.hasText(user.getCompanyName())) {
            return user.getCompanyName();
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName();
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return user.getUsername();
    }
}
