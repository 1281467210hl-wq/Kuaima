package com.kuaima.app.admin.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSON;
import com.kuaima.app.common.BusinessHttpException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.enterprise.constant.WorkbenchPermCodes;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.repository.AdminEnterpriseMemberRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 后台-企业工作台权限定义。
 * GET 返回统一权限码目录（{@link WorkbenchPermCodes}）与企业内成员现状；
 * PUT 将权限模板批量落到成员（memberIds 为空 = 作用于该企业全部在职成员）。
 */
@RestController
@RequestMapping("/admin/enterprises/{enterpriseId}/workbench-perms")
@Tag(name = "后台-工作台权限定义", description = "统一权限码目录 + 企业级权限模板批量下发")
public class AdminWorkbenchPermController {

    private final AdminEnterpriseMemberRepository memberRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final UserRepository userRepository;

    public AdminWorkbenchPermController(AdminEnterpriseMemberRepository memberRepository,
            EnterpriseRepository enterpriseRepository, UserRepository userRepository) {
        this.memberRepository = memberRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Operation(summary = "工作台权限定义（权限码目录 + 角色默认值 + 企业成员当前权限）")
    public Result<Map<String, Object>> get(@PathVariable Long enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.NOT_FOUND, "企业不存在"));
        List<EnterpriseMember> members = memberRepository.findByEnterpriseIdAndStatus(enterpriseId, "ACTIVE");
        Map<Long, User> users = members.isEmpty() ? Map.of()
                : userRepository.findAllById(members.stream().map(EnterpriseMember::getUserId)
                        .distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> memberViews = new ArrayList<>();
        for (EnterpriseMember member : members) {
            User user = users.get(member.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("memberId", member.getId());
            item.put("userId", member.getUserId());
            item.put("name", user != null && StringUtils.hasText(user.getNickname()) ? user.getNickname()
                    : (user != null ? user.getUsername() : "成员" + member.getUserId()));
            item.put("memberRole", member.getMemberRole());
            item.put("permissions", normalizeStored(member.getPermissions()));
            item.put("portalEnabled", member.getPortalEnabled());
            item.put("permissionVersion", member.getPermissionVersion());
            memberViews.add(item);
        }

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("OWNER", WorkbenchPermCodes.defaultCodes("OWNER"));
        defaults.put("ADMIN", WorkbenchPermCodes.defaultCodes("ADMIN"));
        defaults.put("STAFF", WorkbenchPermCodes.defaultCodes("STAFF"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enterpriseId", enterprise.getId());
        data.put("enterpriseName", enterprise.getCompanyName());
        data.put("perms", WorkbenchPermCodes.catalog());
        data.put("defaults", defaults);
        data.put("members", memberViews);
        return Result.success(data);
    }

    @PutMapping
    @Operation(summary = "批量下发权限模板（body: permissions[] + memberIds[]，memberIds 省略则作用于全部在职成员）")
    public Result<Map<String, Object>> put(@PathVariable Long enterpriseId,
            @RequestBody Map<String, Object> body) {
        enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessHttpException(HttpStatus.NOT_FOUND, "企业不存在"));
        List<String> codes = WorkbenchPermCodes.normalize(body.get("permissions"));

        List<EnterpriseMember> targets;
        Object rawIds = body.get("memberIds");
        if (rawIds instanceof List<?> idList && !idList.isEmpty()) {
            List<Long> memberIds = new ArrayList<>();
            for (Object item : idList) {
                if (item != null) {
                    try {
                        memberIds.add(Long.valueOf(String.valueOf(item).trim()));
                    } catch (NumberFormatException ignored) {
                        // 非法 id 直接跳过
                    }
                }
            }
            targets = memberRepository.findAllById(memberIds).stream()
                    .filter(m -> enterpriseId.equals(m.getEnterpriseId()))
                    .collect(Collectors.toList());
        } else {
            targets = memberRepository.findByEnterpriseIdAndStatus(enterpriseId, "ACTIVE");
        }

        for (EnterpriseMember member : targets) {
            member.setPermissions(JSON.toJSONString(codes));
            Integer version = member.getPermissionVersion();
            member.setPermissionVersion(version == null ? 1 : version + 1);
        }
        memberRepository.saveAll(targets);
        return Result.success(Map.of("updated", targets.size(), "permissions", codes));
    }

    private static List<String> normalizeStored(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<String> stored;
        try {
            String trimmed = raw.trim();
            stored = trimmed.startsWith("[")
                    ? JSON.parseArray(trimmed, String.class) : List.of(trimmed.split(","));
        } catch (RuntimeException ex) {
            return List.of();
        }
        return WorkbenchPermCodes.normalize(stored);
    }
}
