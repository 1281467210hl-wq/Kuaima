package com.kuaima.app.controller.enterprise;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSON;
import com.kuaima.app.common.BusinessHttpException;
import com.kuaima.app.common.Result;
import com.kuaima.app.common.util.QrCodeUtils;
import com.kuaima.app.domain.enterprise.constant.WorkbenchPermCodes;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.domain.enterprise.entity.EnterpriseInvite;
import com.kuaima.app.domain.enterprise.entity.EnterpriseJoinApply;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.repository.EnterpriseInviteRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseJoinApplyRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseMemberRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseRepository;
import com.kuaima.app.domain.enterprise.service.EnterpriseContextService;
import com.kuaima.app.domain.enterprise.service.EnterpriseMemberService;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/boss/enterprise")
@Tag(name = "老板-企业成员", description = "企业成员列表、申请审批、邀请、退出企业")
public class EnterpriseMemberController {

    private final EnterpriseContextService context;
    private final EnterpriseMemberService service;
    private final EnterpriseMemberRepository memberRepo;
    private final EnterpriseJoinApplyRepository applyRepo;
    private final EnterpriseInviteRepository inviteRepo;
    private final EnterpriseRepository enterpriseRepo;
    private final UserRepository userRepo;

    public EnterpriseMemberController(EnterpriseContextService context,
                                      EnterpriseMemberService service,
                                      EnterpriseMemberRepository memberRepo,
                                      EnterpriseJoinApplyRepository applyRepo,
                                      EnterpriseInviteRepository inviteRepo,
                                      EnterpriseRepository enterpriseRepo,
                                      UserRepository userRepo) {
        this.context = context;
        this.service = service;
        this.memberRepo = memberRepo;
        this.applyRepo = applyRepo;
        this.inviteRepo = inviteRepo;
        this.enterpriseRepo = enterpriseRepo;
        this.userRepo = userRepo;
    }

    @GetMapping("/members")
    @Operation(summary = "企业成员列表（keyword 可选）")
    public Result<List<Map<String, Object>>> members(Authentication auth,
            @RequestParam(required = false) String keyword) {
        var ctx = context.require(auth);
        return Result.success(service.listMembers(ctx.enterprise(), keyword));
    }

    @GetMapping("/members/{memberId}")
    @Operation(summary = "成员详情（含权限明细）")
    public Result<Map<String, Object>> detail(Authentication auth, @PathVariable Long memberId) {
        var ctx = context.require(auth);
        EnterpriseMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "成员不存在"));
        if (!member.getEnterpriseId().equals(ctx.enterprise().getId())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "无权查看该成员");
        }
        Map<String, Object> detail = service.memberDetail(member);
        // remark 列本轮新增，service.memberDetail 未返回，这里补齐供 member-detail/member-edit 回显。
        detail.put("remark", member.getRemark() != null ? member.getRemark() : "");
        // 性别/年龄/身份证（1016 原型）：年龄优先按身份证实时计算，身份证脱敏返回。
        detail.put("gender", member.getGender() != null ? member.getGender()
                : com.kuaima.app.common.util.IdCardUtils.gender(member.getIdCard()));
        detail.put("idCard", com.kuaima.app.common.util.IdCardUtils.mask(member.getIdCard()));
        Integer computedAge = com.kuaima.app.common.util.IdCardUtils.age(member.getIdCard());
        detail.put("age", computedAge != null ? computedAge : member.getAge());
        return Result.success(detail);
    }

    @PutMapping("/members/{memberId}/role")
    @Operation(summary = "编辑成员角色（ADMIN/STAFF）")
    public Result<EnterpriseMember> updateRole(Authentication auth, @PathVariable Long memberId,
            @RequestBody Map<String, String> body) {
        var ctx = context.require(auth, "MEMBER_WRITE");
        EnterpriseMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "成员不存在"));
        if (!member.getEnterpriseId().equals(ctx.enterprise().getId())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "无权操作该成员");
        }
        return Result.success(service.updateRole(member, body.getOrDefault("role", "STAFF")));
    }

    @DeleteMapping("/members/{memberId}")
    @Operation(summary = "移出企业")
    public Result<Void> remove(Authentication auth, @PathVariable Long memberId) {
        var ctx = context.require(auth, "MEMBER_WRITE");
        EnterpriseMember member = memberRepo.findById(memberId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "成员不存在"));
        if (!member.getEnterpriseId().equals(ctx.enterprise().getId())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "无权操作该成员");
        }
        service.removeMember(member);
        return Result.success();
    }

    @GetMapping("/applies")
    @Operation(summary = "入企申请列表（status: pending/agreed/refused）")
    public Result<Page<EnterpriseJoinApply>> applies(Authentication auth,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = context.require(auth, "MEMBER_VIEW");
        String normalized = status == null ? null : status.toUpperCase();
        return Result.success(service.listApplies(ctx.enterprise(), normalized, page, size));
    }

    @GetMapping("/applies/pending-count")
    @Operation(summary = "待处理申请数量")
    public Result<Long> pendingCount(Authentication auth) {
        var ctx = context.require(auth, "MEMBER_VIEW");
        return Result.success(service.countPendingApplies(ctx.enterprise()));
    }

    @PostMapping("/applies/{applyId}/agree")
    @Operation(summary = "同意入企申请")
    public Result<EnterpriseJoinApply> agree(Authentication auth, @PathVariable Long applyId) {
        var ctx = context.require(auth, "MEMBER_WRITE");
        EnterpriseJoinApply apply = applyRepo.findById(applyId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "申请不存在"));
        if (!apply.getEnterpriseId().equals(ctx.enterprise().getId())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "无权处理该申请");
        }
        return Result.success(service.agreeApply(apply, ctx.user().getId()));
    }

    @PostMapping("/applies/{applyId}/refuse")
    @Operation(summary = "拒绝入企申请")
    public Result<EnterpriseJoinApply> refuse(Authentication auth, @PathVariable Long applyId) {
        var ctx = context.require(auth, "MEMBER_WRITE");
        EnterpriseJoinApply apply = applyRepo.findById(applyId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "申请不存在"));
        if (!apply.getEnterpriseId().equals(ctx.enterprise().getId())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "无权处理该申请");
        }
        return Result.success(service.refuseApply(apply, ctx.user().getId()));
    }

    @PostMapping("/invites")
    @Operation(summary = "通过手机号邀请新成员")
    public Result<Map<String, Object>> invite(Authentication auth, @RequestBody Map<String, String> body) {
        var ctx = context.require(auth, "MEMBER_WRITE");
        String phone = body.getOrDefault("phone", "").trim();
        if (!phone.matches("1\\d{10}")) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "请输入正确的11位手机号");
        }
        if (userRepo.findByPhone(phone).isEmpty()) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "未找到该手机号对应的成员，请先确认对方已注册");
        }
        var invite = service.createInvite(ctx.enterprise(), ctx.user().getId(),
                phone, body.getOrDefault("role", "STAFF"));
        return Result.success(Map.of(
                "id", invite.getId(),
                "phone", maskPhone(phone),
                "role", invite.getInviteRole(),
                "inviteCode", invite.getInviteCode(),
                "status", invite.getStatus()));
    }

    @GetMapping("/invites")
    @Operation(summary = "邀请记录列表")
    public Result<Page<Map<String, Object>>> invites(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var ctx = context.require(auth, "MEMBER_VIEW");
        var invites = service.listInvites(ctx.enterprise(), null, page, size);
        return Result.success(invites.map(i -> Map.<String, Object>of(
                "id", i.getId(),
                "name", i.getName() != null ? i.getName() : "新成员",
                "phone", maskPhone(i.getPhone()),
                "role", i.getInviteRole(),
                "status", i.getStatus(),
                "time", i.getTimestamp() != null
                        ? i.getTimestamp().toLocalDateTime().toString().replace("T", " ").substring(0, 16) : "")));
    }

    @PostMapping("/invites/{inviteId}/remind")
    @Operation(summary = "重新发送邀请提醒")
    public Result<Void> remind(Authentication auth, @PathVariable Long inviteId) {
        context.require(auth, "MEMBER_WRITE");
        return Result.success();
    }

    // ==================== 被邀请人视角（不要求企业上下文，按登录人手机号匹配） ====================

    @GetMapping("/invites/my")
    @Operation(summary = "我的企业邀请（被邀请人视角，按当前登录手机号匹配待处理邀请）")
    public Result<List<Map<String, Object>>> myInvites(Authentication auth) {
        User me = requireLoginUser(auth);
        if (!StringUtils.hasText(me.getPhone())) {
            return Result.success(List.of());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (EnterpriseInvite invite : inviteRepo.findByPhoneAndStatusOrderByIdDesc(me.getPhone().trim(), "PENDING")) {
            Enterprise enterprise = enterpriseRepo.findById(invite.getEnterpriseId()).orElse(null);
            User inviter = userRepo.findById(invite.getInviterId()).orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", invite.getId());
            item.put("enterpriseName", enterprise != null ? enterprise.getCompanyName() : "企业");
            item.put("inviterName", inviter != null && inviter.getNickname() != null ? inviter.getNickname()
                    : (inviter != null ? inviter.getUsername() : ""));
            item.put("role", invite.getInviteRole());
            item.put("status", invite.getStatus());
            item.put("time", invite.getTimestamp() != null
                    ? invite.getTimestamp().toLocalDateTime().toString().replace("T", " ").substring(0, 16) : "");
            list.add(item);
        }
        return Result.success(list);
    }

    @PostMapping("/invites/{inviteId}/accept")
    @Operation(summary = "被邀请人同意入企邀请（同意后成为企业成员）")
    public Result<Void> acceptMyInvite(Authentication auth, @PathVariable Long inviteId) {
        User me = requireLoginUser(auth);
        EnterpriseInvite invite = requireMyInvite(inviteId, me);
        Enterprise enterprise = enterpriseRepo.findById(invite.getEnterpriseId())
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "企业不存在"));
        service.acceptInvite(enterprise, invite, me.getId());
        return Result.success();
    }

    @PostMapping("/invites/{inviteId}/refuse")
    @Operation(summary = "被邀请人拒绝入企邀请")
    public Result<Void> refuseMyInvite(Authentication auth, @PathVariable Long inviteId) {
        User me = requireLoginUser(auth);
        EnterpriseInvite invite = requireMyInvite(inviteId, me);
        service.refuseInvite(invite);
        return Result.success();
    }

    private User requireLoginUser(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof LoginUser login && login.id() != null) {
            return userRepo.findById(login.id())
                    .orElseThrow(() -> new BusinessHttpException(HttpStatus.UNAUTHORIZED, "登录用户不存在"));
        }
        throw new BusinessHttpException(HttpStatus.UNAUTHORIZED, "未登录或Token无效");
    }

    private EnterpriseInvite requireMyInvite(Long inviteId, User me) {
        EnterpriseInvite invite = inviteRepo.findById(inviteId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "邀请不存在"));
        if (me.getPhone() == null || !me.getPhone().trim().equals(invite.getPhone())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "无权处理该邀请");
        }
        if (!"PENDING".equals(invite.getStatus())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "该邀请已处理");
        }
        return invite;
    }

    @GetMapping("/invite-qr")
    @Operation(summary = "获取邀请二维码信息（邀请码 + 链接 + 企业名称 + base64二维码图）")
    public Result<Map<String, Object>> inviteQr(Authentication auth) {
        var ctx = context.require(auth, "MEMBER_WRITE");
        var invite = service.createInvite(ctx.enterprise(), ctx.user().getId(),
                ctx.user().getPhone() != null ? ctx.user().getPhone() : "00000000000", "STAFF");
        String link = "https://ke.taifang.xyz/invite?code=" + invite.getInviteCode();
        String qrImage = QrCodeUtils.generateBase64Png(link, 300);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inviteCode", invite.getInviteCode());
        data.put("link", link);
        data.put("enterpriseName", ctx.enterprise().getCompanyName());
        data.put("qrImage", qrImage);
        return Result.success(data);
    }

    @GetMapping("/exit-info")
    @Operation(summary = "退出企业前信息")
    public Result<Map<String, Object>> exitInfo(Authentication auth) {
        var ctx = context.require(auth);
        return Result.success(service.exitInfo(ctx.member(), ctx.enterprise()));
    }

    @PostMapping("/exit")
    @Operation(summary = "退出当前企业")
    public Result<Void> exit(Authentication auth) {
        var ctx = context.require(auth);
        service.exitEnterprise(ctx.member());
        return Result.success();
    }

    @PutMapping("/members/{userId}/info")
    @Operation(summary = "编辑成员信息（title=职位/remark=备注；姓名与手机号属 sys_user 域，本接口不修改）")
    public Result<EnterpriseMember> updateInfo(Authentication auth, @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        var ctx = context.require(auth, "MEMBER_WRITE");
        EnterpriseMember member = requireActiveMemberByUserId(ctx, userId);
        if ("OWNER".equals(member.getMemberRole())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "不可编辑超级管理员信息");
        }
        // title 即成员「职位」，remark 为备注，均落 enterprise_member；
        // name/phone 属于 sys_user（User）表字段，不在本接口（企业成员域）写入，由用户中心维护。
        if (body.containsKey("title")) {
            String title = body.get("title");
            member.setTitle(title == null || title.isBlank() ? null : title.trim());
        }
        if (body.containsKey("remark")) {
            String remark = body.get("remark");
            if (remark != null && remark.length() > 500) {
                throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "备注不能超过500字");
            }
            member.setRemark(remark == null || remark.isBlank() ? null : remark.trim());
        }
        if (body.containsKey("gender")) {
            String g = body.get("gender");
            member.setGender(g == null || g.isBlank() ? null : g.trim());
        }
        if (body.containsKey("idCard")) {
            String idc = body.get("idCard");
            member.setIdCard(idc == null || idc.isBlank() ? null : idc.trim());
        }
        if (body.containsKey("age")) {
            String ageStr = body.get("age");
            if (ageStr == null || ageStr.isBlank()) {
                member.setAge(null);
            } else {
                try {
                    member.setAge(Integer.parseInt(ageStr.trim()));
                } catch (NumberFormatException ignore) {
                    // 非法年龄忽略，读取时按身份证回退计算
                }
            }
        }
        return Result.success(memberRepo.save(member));
    }

    @PutMapping("/members/{userId}/permissions")
    @Operation(summary = "编辑成员工作台权限（permissions 支持 code/中文名/前端 key，写 JSON 并自增 permission_version；portalEnabled 为工作台访问开关；仅 OWNER/ADMIN 可操作）")
    public Result<Map<String, Object>> updatePermissions(Authentication auth, @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        var ctx = context.require(auth);
        if (!ctx.ownerOrAdmin()) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "仅超级管理员和管理员可设置成员权限");
        }
        EnterpriseMember member = requireActiveMemberByUserId(ctx, userId);
        if ("OWNER".equals(member.getMemberRole())) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "超级管理员默认拥有全部权限，不可修改");
        }
        if (body.containsKey("permissions")) {
            // 与 admin 端 AdminEnterpriseMemberController.applyPermissions 同一写法：
            // WorkbenchPermCodes.normalize 归一为大写 code 后 fastjson2 序列化存 JSON 数组。
            List<String> codes = WorkbenchPermCodes.normalize(body.get("permissions"));
            member.setPermissions(JSON.toJSONString(codes));
            Integer version = member.getPermissionVersion();
            member.setPermissionVersion(version == null ? 1 : version + 1);
        }
        if (body.containsKey("portalEnabled")) {
            Object value = body.get("portalEnabled");
            member.setPortalEnabled(value instanceof Boolean flag ? flag
                    : value != null && "true".equalsIgnoreCase(String.valueOf(value).trim()));
        }
        EnterpriseMember saved = memberRepo.save(member);
        List<String> savedCodes = saved.getPermissions() == null
                ? List.of() : WorkbenchPermCodes.normalize(JSON.parseArray(saved.getPermissions()));
        return Result.success(Map.of(
                "memberId", saved.getId(),
                "userId", saved.getUserId(),
                "permissions", savedCodes,
                "permissionVersion", saved.getPermissionVersion() == null ? 1 : saved.getPermissionVersion(),
                "portalEnabled", !Boolean.FALSE.equals(saved.getPortalEnabled())));
    }

    /** 按 sys_user 的 userId 定位当前企业的 ACTIVE 成员（注意：路径参数是 userId，非成员表主键）。 */
    private EnterpriseMember requireActiveMemberByUserId(EnterpriseContextService.Context ctx, Long userId) {
        return memberRepo.findByEnterpriseIdAndUserId(ctx.enterprise().getId(), userId)
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.BAD_REQUEST, "成员不存在"));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
