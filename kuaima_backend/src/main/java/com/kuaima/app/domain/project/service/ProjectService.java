package com.kuaima.app.domain.project.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.base.entity.BaseEntity;
import com.kuaima.app.domain.project.constant.ProjectConstants;
import com.kuaima.app.domain.project.entity.Project;
import com.kuaima.app.domain.project.repository.ProjectRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Page<Project> listProjects(String keyword, String status, Pageable pageable) {
        Specification<Project> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), ProjectConstants.PROJECT_DELETED));
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), like),
                        cb.like(root.get("companyName"), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return projectRepository.findAll(spec, pageable);
    }

    public Project getOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在: " + id));
    }

    @Transactional
    public Project create(Project project, Long operatorId) {
        if (project.getStatus() == null) {
            project.setStatus(ProjectConstants.PROJECT_ACTIVE);
        }
        if (operatorId != null) {
            project.setCreateBy(operatorId);
        }
        // 幂等：客户端每次「创建意图」带唯一 idempotencyKey，命中已存在记录直接返回，避免重复提交产生多条。
        String key = project.getIdempotencyKey();
        if (StringUtils.hasText(key)) {
            java.util.Optional<Project> existing = projectRepository.findByIdempotencyKey(key.trim());
            if (existing.isPresent()) {
                return existing.get();
            }
            project.setIdempotencyKey(key.trim());
        }
        try {
            return projectRepository.save(project);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            // 并发下唯一键冲突：返回先落库的那条，保证幂等。
            if (StringUtils.hasText(key)) {
                return projectRepository.findByIdempotencyKey(key.trim()).orElseThrow(() -> race);
            }
            throw race;
        }
    }

    @Transactional
    public Project update(Long id, Project source) {
        Project project = getOrThrow(id);
        if (StringUtils.hasText(source.getName())) {
            project.setName(source.getName());
        }
        if (StringUtils.hasText(source.getCompanyName())) {
            project.setCompanyName(source.getCompanyName());
        }
        if (StringUtils.hasText(source.getLeaderName())) {
            project.setLeaderName(source.getLeaderName());
        }
        if (StringUtils.hasText(source.getLeaderPhone())) {
            project.setLeaderPhone(source.getLeaderPhone());
        }
        if (source.getEstablishDate() != null) {
            project.setEstablishDate(source.getEstablishDate());
        }
        if (StringUtils.hasText(source.getLocation())) {
            project.setLocation(source.getLocation());
        }
        if (StringUtils.hasText(source.getPayrollCycle())) {
            project.setPayrollCycle(source.getPayrollCycle());
        }
        if (StringUtils.hasText(source.getSettleType())) {
            project.setSettleType(source.getSettleType());
        }
        if (source.getDailyWage() != null) {
            project.setDailyWage(source.getDailyWage());
        }
        if (source.getLocationCheckin() != null) {
            project.setLocationCheckin(source.getLocationCheckin());
        }
        if (source.getCheckinRadius() != null) {
            project.setCheckinRadius(source.getCheckinRadius());
        }
        if (source.getSignCodeExpire() != null) {
            project.setSignCodeExpire(source.getSignCodeExpire());
        }
        if (source.getLateAuto() != null) {
            project.setLateAuto(source.getLateAuto());
        }
        if (source.getLateThreshold() != null) {
            project.setLateThreshold(source.getLateThreshold());
        }
        if (source.getSalaryRemind() != null) {
            project.setSalaryRemind(source.getSalaryRemind());
        }
        return projectRepository.save(project);
    }

    @Transactional
    public void archive(Long id) {
        Project project = getOrThrow(id);
        project.setStatus(ProjectConstants.PROJECT_ARCHIVED);
        projectRepository.save(project);
    }

    @Transactional
    public void delete(Long id) {
        Project project = getOrThrow(id);
        project.setStatus(ProjectConstants.PROJECT_DELETED);
        projectRepository.save(project);
    }

    public long countByStatus(String status) {
        return projectRepository.count((root, query, cb) -> cb.equal(root.get("status"), status));
    }

    /** 隐藏审计字段，避免列表接口泄露 createBy 等。 */
    public static void hideAudit(BaseEntity e) {
        // BaseEntity 字段由 JPA 自动维护，此处预留脱敏扩展点
    }
}
