package com.kuaima.app.domain.enterprise.service;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.domain.enterprise.entity.EnterpriseInvite;
import com.kuaima.app.domain.enterprise.entity.EnterpriseJoinApply;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.repository.EnterpriseInviteRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseJoinApplyRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseMemberRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseRepository;
import com.kuaima.app.domain.enterprise.service.EnterpriseContextService.Context;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnterpriseMemberService {

    private static final List<String> PERMISSIONS = List.of(
            "岗位发布与编辑", "成员管理与邀请", "制单发薪", "发薪审批", "考勤统计", "记录导出");

    private final EnterpriseMemberRepository memberRepo;
    private final EnterpriseJoinApplyRepository applyRepo;
    private final EnterpriseInviteRepository inviteRepo;
    private final UserRepository userRepo;

    public EnterpriseMemberService(EnterpriseMemberRepository memberRepo,
                                  EnterpriseJoinApplyRepository applyRepo,
                                  EnterpriseInviteRepository inviteRepo,
                                  UserRepository userRepo) {
        this.memberRepo = memberRepo;
        this.applyRepo = applyRepo;
        this.inviteRepo = inviteRepo;
        this.userRepo = userRepo;
    }

    public List<Map<String, Object>> listMembers(Enterprise enterprise, String keyword) {
        List<EnterpriseMember> members = memberRepo.findByEnterpriseIdAndStatus(enterprise.getId(), "ACTIVE");
        return members.stream().filter(m -> {
            if (keyword == null || keyword.isBlank()) return true;
            User u = userRepo.findById(m.getUserId()).orElse(null);
            if (u == null) return false;
            return (u.getNickname() != null && u.getNickname().contains(keyword))
                    || (u.getPhone() != null && u.getPhone().contains(keyword))
                    || (m.getTitle() != null && m.getTitle().contains(keyword));
        }).map(m -> {
            User u = userRepo.findById(m.getUserId()).orElse(null);
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", m.getId());
            item.put("memberId", m.getId());
            item.put("userId", m.getUserId());
            item.put("name", u != null && u.getNickname() != null ? u.getNickname()
                    : (u != null ? u.getUsername() : "成员" + m.getUserId()));
            item.put("phone", u != null ? maskPhone(u.getPhone()) : "");
            item.put("role", m.getMemberRole());
            item.put("title", m.getTitle() != null ? m.getTitle() : "");
            item.put("portalEnabled", "OWNER".equals(m.getMemberRole()) || !Boolean.FALSE.equals(m.getPortalEnabled()));
            item.put("permissions", m.getPermissions() == null
                    ? List.of() : com.alibaba.fastjson2.JSON.parseArray(m.getPermissions()));
            item.put("joinDate", m.getDate() != null ? m.getDate().toString() : "");
            String gender = m.getGender() != null ? m.getGender()
                    : com.kuaima.app.common.util.IdCardUtils.gender(m.getIdCard());
            Integer age = com.kuaima.app.common.util.IdCardUtils.age(m.getIdCard());
            item.put("gender", gender);
            item.put("age", age != null ? age : m.getAge());
            return item;
        }).toList();
    }

    public Map<String, Object> memberDetail(EnterpriseMember member) {
        User user = userRepo.findById(member.getUserId()).orElseThrow();
        EnterpriseMember inviter = member.getInvitedBy() != null
                ? memberRepo.findById(member.getInvitedBy()).orElse(null) : null;
        Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("id", member.getId());
        detail.put("userId", member.getUserId());
        detail.put("name", user.getNickname() != null ? user.getNickname() : user.getUsername());
        detail.put("phone", maskPhone(user.getPhone()));
        detail.put("role", member.getMemberRole());
        detail.put("title", member.getTitle() != null ? member.getTitle() : "");
        detail.put("joinDate", member.getDate() != null ? member.getDate().toString() : "");
        detail.put("invitedByName", inviter != null ? userRepo.findById(inviter.getUserId())
                .map(u -> u.getNickname() != null ? u.getNickname() : u.getUsername()).orElse("") : "");
        detail.put("permissions", member.getPermissions() == null
                ? List.of() : com.alibaba.fastjson2.JSON.parseArray(member.getPermissions()));
        detail.put("portalEnabled", "OWNER".equals(member.getMemberRole()) || !Boolean.FALSE.equals(member.getPortalEnabled()));
        detail.put("permIndexes", permIndexes(member.getMemberRole()));
        detail.put("jobCount", 0);
        detail.put("self", false);
        return detail;
    }

    @Transactional
    public EnterpriseMember updateRole(EnterpriseMember member, String newRole) {
        if ("OWNER".equals(member.getMemberRole())) {
            throw new ForbiddenBusinessException("超级管理员角色不可修改");
        }
        member.setMemberRole(newRole);
        if (!"ADMIN".equals(newRole)) member.setTitle(null);
        return memberRepo.save(member);
    }

    @Transactional
    public void removeMember(EnterpriseMember member) {
        if ("OWNER".equals(member.getMemberRole())) {
            throw new ForbiddenBusinessException("不可移出超级管理员");
        }
        member.setStatus("LEFT");
        memberRepo.save(member);
    }

    public Page<EnterpriseJoinApply> listApplies(Enterprise enterprise, String status, int page, int size) {
        return applyRepo.search(enterprise.getId(), status, PageRequest.of(page, size));
    }

    public long countPendingApplies(Enterprise enterprise) {
        return applyRepo.countByEnterpriseIdAndStatus(enterprise.getId(), "PENDING");
    }

    @Transactional
    public EnterpriseJoinApply agreeApply(EnterpriseJoinApply apply, Long handlerId) {
        if (!"PENDING".equals(apply.getStatus())) {
            throw new ForbiddenBusinessException("该申请已处理");
        }
        apply.setStatus("AGREED");
        apply.setHandledBy(handlerId);
        applyRepo.save(apply);

        if (apply.getUserId() != null) {
            EnterpriseMember m = new EnterpriseMember();
            m.setEnterpriseId(apply.getEnterpriseId());
            m.setUserId(apply.getUserId());
            m.setMemberRole(apply.getApplyRole());
            m.setStatus("ACTIVE");
            memberRepo.save(m);
        }
        return apply;
    }

    @Transactional
    public EnterpriseJoinApply refuseApply(EnterpriseJoinApply apply, Long handlerId) {
        if (!"PENDING".equals(apply.getStatus())) {
            throw new ForbiddenBusinessException("该申请已处理");
        }
        apply.setStatus("REFUSED");
        apply.setHandledBy(handlerId);
        return applyRepo.save(apply);
    }

    @Transactional
    public EnterpriseInvite createInvite(Enterprise enterprise, Long inviterId, String phone, String role) {
        List<EnterpriseInvite> existing = inviteRepo.findByEnterpriseIdAndPhoneAndStatusOrderByIdDesc(
                enterprise.getId(), phone, "PENDING");
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        EnterpriseInvite invite = new EnterpriseInvite();
        invite.setEnterpriseId(enterprise.getId());
        invite.setInviterId(inviterId);
        invite.setPhone(phone);
        invite.setName("新成员");
        invite.setInviteRole(role);
        invite.setInviteCode(generateInviteCode());
        invite.setStatus("PENDING");
        return inviteRepo.save(invite);
    }

    public Page<EnterpriseInvite> listInvites(Enterprise enterprise, String status, int page, int size) {
        return inviteRepo.search(enterprise.getId(), status, PageRequest.of(page, size));
    }

    @Transactional
    public void acceptInvite(Enterprise enterprise, EnterpriseInvite invite, Long userId) {
        if (!"PENDING".equals(invite.getStatus())) return;
        invite.setStatus("ACCEPTED");
        invite.setAcceptedAt(Timestamp.valueOf(LocalDateTime.now()));
        inviteRepo.save(invite);

        if (!memberRepo.existsByEnterpriseIdAndUserIdAndStatus(enterprise.getId(), userId, "ACTIVE")) {
            EnterpriseMember m = new EnterpriseMember();
            m.setEnterpriseId(enterprise.getId());
            m.setUserId(userId);
            m.setMemberRole(invite.getInviteRole());
            m.setStatus("ACTIVE");
            m.setInvitedBy(invite.getInviterId());
            memberRepo.save(m);
        }
    }

    @Transactional
    public void refuseInvite(EnterpriseInvite invite) {
        if (!"PENDING".equals(invite.getStatus())) return;
        invite.setStatus("REFUSED");
        inviteRepo.save(invite);
    }

    public Map<String, Object> exitInfo(EnterpriseMember member, Enterprise enterprise) {
        return Map.of(
                "companyName", enterprise.getCompanyName(),
                "memberCount", memberRepo.countByEnterpriseIdAndStatus(enterprise.getId(), "ACTIVE"),
                "joinDate", member.getDate() != null ? member.getDate().toString() : "",
                "role", member.getMemberRole(),
                "jobCount", 0,
                "pendingApproval", 0);
    }

    @Transactional
    public void exitEnterprise(EnterpriseMember member) {
        if ("OWNER".equals(member.getMemberRole())) {
            throw new ForbiddenBusinessException("超级管理员不可直接退出企业，请先转让所有权");
        }
        member.setStatus("LEFT");
        memberRepo.save(member);
    }

    public List<Integer> permIndexes(String role) {
        if ("OWNER".equals(role)) return List.of(0, 1, 2, 3, 4, 5);
        if ("ADMIN".equals(role)) return List.of(0, 1, 2, 4, 5);
        return List.of();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
