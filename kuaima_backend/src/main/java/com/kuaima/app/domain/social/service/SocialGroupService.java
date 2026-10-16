package com.kuaima.app.domain.social.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.domain.social.dto.SocialGroupJoinRequest;
import com.kuaima.app.domain.social.dto.SocialGroupCreateRequest;
import com.kuaima.app.domain.social.dto.SocialGroupView;
import com.kuaima.app.domain.social.entity.SocialGroup;
import com.kuaima.app.domain.social.entity.SocialGroupMember;
import com.kuaima.app.domain.social.repository.SocialGroupMemberRepository;
import com.kuaima.app.domain.social.repository.SocialGroupRepository;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

import jakarta.persistence.EntityNotFoundException;

@Service
public class SocialGroupService {

    private static final String WORKER = "WORKER";
    private static final String ACTIVE = "ACTIVE";
    private static final int DEFAULT_MEMBER_LIMIT = 200;

    private final SocialGroupRepository groups;
    private final SocialGroupMemberRepository members;
    private final UserRepository users;
    private final OssStorageService ossStorageService;

    public SocialGroupService(SocialGroupRepository groups,
                              SocialGroupMemberRepository members,
                              UserRepository users,
                              OssStorageService ossStorageService) {
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.ossStorageService = ossStorageService;
    }

    @Transactional(readOnly = true)
    public Page<SocialGroupView> list(LoginUser login, String role, String status, int page, int size) {
        User current = currentWorker(login);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page 或 size 参数无效");
        }
        String requestedRole = normalize(role, WORKER);
        String requestedStatus = normalize(status, ACTIVE);
        if (!WORKER.equals(requestedRole)) {
            throw new IllegalArgumentException("零工端 role 必须为 WORKER");
        }
        if (!ACTIVE.equals(requestedStatus)) {
            throw new IllegalArgumentException("零工端 status 必须为 ACTIVE");
        }
        return groups.findVisibleGroups(WORKER, ACTIVE, PageRequest.of(page, size))
                .map(group -> view(group, current.getId()));
    }

    @Transactional
    public SocialGroupView join(Long groupId, SocialGroupJoinRequest request, LoginUser login) {
        User current = currentWorker(login);
        if (!isRealnameApproved(current)) {
            throw new ForbiddenBusinessException("请先完成实名认证");
        }
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new IllegalArgumentException("请确认已扫码加入群聊");
        }
        String source = StringUtils.hasText(request.source()) ? request.source().trim() : "WORKER_GROUP_PAGE";
        if (source.length() > 100) {
            throw new IllegalArgumentException("source 长度不能超过100");
        }

        SocialGroup group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new EntityNotFoundException("社群不存在: " + groupId));
        requireJoinable(group);

        SocialGroupMember existing = members.findByGroupIdAndUserId(groupId, current.getId()).orElse(null);
        if (existing == null) {
            int count = count(group);
            int limit = limit(group);
            if (count >= limit) {
                throw new IllegalStateException("该群已满员");
            }
            SocialGroupMember member = new SocialGroupMember();
            member.setGroupId(groupId);
            member.setUserId(current.getId());
            member.setSource(source);
            member.setConfirmed(true);
            member.setJoinedAt(LocalDateTime.now());
            members.save(member);
            group.setMemberCount(count + 1);
            groups.save(group);
        } else if (!Boolean.TRUE.equals(existing.getConfirmed())) {
            existing.setConfirmed(true);
            existing.setSource(source);
            existing.setJoinedAt(LocalDateTime.now());
            members.save(existing);
        }
        return view(group, current.getId());
    }

    @Transactional
    public SocialGroupView create(SocialGroupCreateRequest request, LoginUser login) {
        User current = currentWorker(login);
        if (!isRealnameApproved(current)) throw new ForbiddenBusinessException("请先完成实名认证");
        if (request == null || !StringUtils.hasText(request.name())) throw new IllegalArgumentException("群名称不能为空");
        String name = request.name().trim();
        if (name.length() > 100) throw new IllegalArgumentException("群名称不能超过100个字符");
        String qrcodeUrl = StringUtils.hasText(request.qrcodeUrl())
                ? storedQrObjectKey(request.qrcodeUrl().trim()) : null;
        if (qrcodeUrl != null && qrcodeUrl.length() > 500) throw new IllegalArgumentException("群二维码地址过长");
        SocialGroup group = new SocialGroup();
        group.setName(name); group.setCategory("找活交流"); group.setQrcodeUrl(qrcodeUrl);
        group.setDescription("零工找活交流群"); group.setRole(WORKER); group.setStatus(ACTIVE);
        group.setMemberLimit(DEFAULT_MEMBER_LIMIT); group.setMemberCount(1); group.setCreatedAt(LocalDateTime.now());
        group.setSort(0); group = groups.save(group);
        SocialGroupMember member = new SocialGroupMember();
        member.setGroupId(group.getId()); member.setUserId(current.getId()); member.setSource("WORKER_GROUP_CREATE");
        member.setConfirmed(true); member.setJoinedAt(LocalDateTime.now()); members.save(member);
        return view(group, current.getId());
    }

    private void requireJoinable(SocialGroup group) {
        if (Boolean.TRUE.equals(group.getDeleted())
                || !WORKER.equals(normalize(group.getRole(), WORKER))
                || !ACTIVE.equals(normalize(group.getStatus(), ACTIVE))) {
            throw new IllegalStateException("该群当前不可加入");
        }
    }

    private SocialGroupView view(SocialGroup group, Long userId) {
        int count = count(group);
        int limit = limit(group);
        boolean joined = members.findByGroupIdAndUserId(group.getId(), userId)
                .map(SocialGroupMember::getConfirmed).orElse(false);
        List<Long> memberUserIds = members.findUserIdsByGroupId(group.getId(), PageRequest.of(0, 5));
        List<String> avatars = users.findAllById(memberUserIds).stream()
                .map(User::getAvatar)
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
        LocalDateTime createdAt = group.getCreatedAt();
        if (createdAt == null && group.getDate() != null) {
            createdAt = group.getDate().atStartOfDay();
        }
        return new SocialGroupView(group.getId(), group.getName(), group.getCategory(), group.getDescription(),
                qrPreviewUrl(group.getQrcodeUrl()), count, limit, createdAt, normalize(group.getStatus(), ACTIVE), joined,
                count >= limit, avatars, Objects.requireNonNullElse(group.getSort(), 0));
    }

    private String storedQrObjectKey(String value) {
        if (value.startsWith("insurance/") || value.startsWith("expenses/")) {
            return value.split("\\?", 2)[0];
        }
        int marker = value.indexOf("/insurance/files/");
        if (marker < 0) marker = value.indexOf("/insurance/");
        if (marker < 0) marker = value.indexOf("/expenses/");
        return marker >= 0 ? value.substring(marker + 1).split("\\?", 2)[0] : value;
    }

    private String qrPreviewUrl(String value) {
        if (!StringUtils.hasText(value) || ossStorageService == null) return value;
        boolean ownObject = value.startsWith("insurance/") || value.startsWith("expenses/")
                || value.contains(".aliyuncs.com/insurance/") || value.contains(".aliyuncs.com/expenses/");
        return ownObject ? ossStorageService.inlinePreviewUrl(value) : value;
    }

    private User currentWorker(LoginUser login) {
        if (login == null || login.id() == null) {
            throw new ForbiddenBusinessException("请先登录");
        }
        User user = users.findById(login.id())
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + login.id()));
        if (!UserRole.isWorkerIdentity(user)) {
            throw new ForbiddenBusinessException("当前用户不是零工身份");
        }
        return user;
    }

    private boolean isRealnameApproved(User user) {
        return CertificationStatus.APPROVED.equals(user.getRealnameStatus())
                || ("REALNAME".equalsIgnoreCase(user.getCertType()) && "已通过".equals(user.getCertStatus()));
    }

    private int count(SocialGroup group) {
        return Math.max(Objects.requireNonNullElse(group.getMemberCount(), 0), 0);
    }

    private int limit(SocialGroup group) {
        int value = Objects.requireNonNullElse(group.getMemberLimit(), DEFAULT_MEMBER_LIMIT);
        return value > 0 ? value : DEFAULT_MEMBER_LIMIT;
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : fallback;
    }
}
