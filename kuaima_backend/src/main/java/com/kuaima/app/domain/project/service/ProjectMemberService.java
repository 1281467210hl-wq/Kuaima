package com.kuaima.app.domain.project.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.domain.position.entity.BossPosition;
import com.kuaima.app.domain.position.repository.BossPositionRepository;
import com.kuaima.app.domain.project.constant.ProjectConstants;
import com.kuaima.app.domain.project.entity.Project;
import com.kuaima.app.domain.project.entity.ProjectMember;
import com.kuaima.app.domain.project.repository.ProjectMemberRepository;
import com.kuaima.app.domain.project.repository.ProjectRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class ProjectMemberService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final BossPositionRepository positionRepository;

    public ProjectMemberService(ProjectMemberRepository memberRepository,
            ProjectRepository projectRepository, BossPositionRepository positionRepository) {
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.positionRepository = positionRepository;
    }

    public List<ProjectMember> listMembers(Long projectId, String status) {
        if (ProjectConstants.MEMBER_ACTIVE.equals(status)
                || ProjectConstants.MEMBER_TEMP.equals(status)
                || ProjectConstants.MEMBER_LEFT.equals(status)) {
            return memberRepository.findByProjectIdAndStatus(projectId, status);
        }
        return memberRepository.findByProjectId(projectId);
    }

    public ProjectMember getOrThrow(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("项目成员不存在: " + id));
    }

    @Transactional
    public ProjectMember add(ProjectMember member, Long operatorId) {
        member.setProjectId(member.getProjectId());
        if (member.getStatus() == null) {
            member.setStatus(ProjectConstants.MEMBER_TEMP);
        }
        // 日薪来源：body 未显式传入时，按「项目老板 + 岗位名称」匹配在招岗位，取其薪资范围
        if (!StringUtils.hasText(member.getSalary())) {
            member.setSalary(resolveSalaryFromPosition(member));
        }
        if (operatorId != null) {
            member.setCreateBy(operatorId);
        }
        return memberRepository.save(member);
    }

    /**
     * 日薪取值来源=岗位薪资范围(salaryRange)：以项目 createBy（老板）为作用域，
     * 用成员岗位名称匹配该老板在招岗位（status=on），命中则返回其薪资范围，否则 null。
     */
    private String resolveSalaryFromPosition(ProjectMember member) {
        if (member.getProjectId() == null || !StringUtils.hasText(member.getRole())) {
            return null;
        }
        Long bossId = projectRepository.findById(member.getProjectId())
                .map(Project::getCreateBy).orElse(null);
        String role = member.getRole().trim();
        return positionRepository.searchByKeywordAndStatus(role, "on", PageRequest.of(0, 50))
                .getContent().stream()
                .filter(p -> role.equals(p.getName()))
                .filter(p -> bossId == null || bossId.equals(p.getCreateBy()))
                .map(BossPosition::getSalaryRange)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public ProjectMember updateStatus(Long id, String status) {
        ProjectMember member = getOrThrow(id);
        member.setStatus(status);
        return memberRepository.save(member);
    }

    @Transactional
    public void remove(Long id) {
        if (!memberRepository.existsById(id)) {
            throw new EntityNotFoundException("项目成员不存在: " + id);
        }
        memberRepository.deleteById(id);
    }

    /** 成员统计：全部 / 在职 / 离职 / 临时。 */
    public Map<String, Long> stats(Long projectId) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<ProjectMember> all = memberRepository.findByProjectId(projectId);
        long active = all.stream().filter(m -> ProjectConstants.MEMBER_ACTIVE.equals(m.getStatus())).count();
        long left = all.stream().filter(m -> ProjectConstants.MEMBER_LEFT.equals(m.getStatus())).count();
        long temp = all.stream().filter(m -> ProjectConstants.MEMBER_TEMP.equals(m.getStatus())).count();
        result.put("all", (long) all.size());
        result.put("active", active);
        result.put("left", left);
        result.put("temp", temp);
        return result;
    }
}
