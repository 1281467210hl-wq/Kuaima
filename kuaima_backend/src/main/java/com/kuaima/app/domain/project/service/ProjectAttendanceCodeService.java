package com.kuaima.app.domain.project.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kuaima.app.domain.project.entity.Project;
import com.kuaima.app.domain.project.entity.ProjectAttendanceCode;
import com.kuaima.app.domain.project.repository.ProjectAttendanceCodeRepository;

/** 项目签到码：生成/刷新当日有效签到码，旧码自动置为失效。 */
@Service
public class ProjectAttendanceCodeService {

    public static final String CODE_ACTIVE = "active";
    public static final String CODE_EXPIRED = "expired";

    private final ProjectAttendanceCodeRepository codeRepository;

    public ProjectAttendanceCodeService(ProjectAttendanceCodeRepository codeRepository) {
        this.codeRepository = codeRepository;
    }

    /** 取当前有效码；不存在/已过期/要求刷新时重新生成。 */
    @Transactional
    public ProjectAttendanceCode current(Project project, boolean refresh) {
        List<ProjectAttendanceCode> actives =
                codeRepository.findByProjectIdAndStatusOrderByIdDesc(project.getId(), CODE_ACTIVE);
        ProjectAttendanceCode latest = actives.isEmpty() ? null : actives.get(0);
        if (!refresh && latest != null && isValid(latest)) {
            return latest;
        }
        // 旧码全部失效后生成新码
        for (ProjectAttendanceCode old : actives) {
            old.setStatus(CODE_EXPIRED);
        }
        codeRepository.saveAll(actives);
        return generate(project);
    }

    private ProjectAttendanceCode generate(Project project) {
        int expireMinutes = project.getSignCodeExpire() != null ? project.getSignCodeExpire() : 60;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(expireMinutes);

        ProjectAttendanceCode code = new ProjectAttendanceCode();
        code.setProjectId(project.getId());
        code.setCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        code.setAttendDate(LocalDate.now());
        code.setQrContent("https://kuaima.com/attendance?projectId=" + project.getId()
                + "&code=" + code.getCode());
        code.setExpireMinutes(expireMinutes);
        code.setExpireTime(Date.from(expireAt.atZone(ZoneId.systemDefault()).toInstant()));
        code.setStatus(CODE_ACTIVE);
        return codeRepository.save(code);
    }

    private boolean isValid(ProjectAttendanceCode code) {
        return code.getExpireTime() == null || code.getExpireTime().after(new Date());
    }
}
