package com.kuaima.app.admin.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.entity.AdminLog;
import com.kuaima.app.admin.repository.AdminLogRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.security.model.LoginUser;

/** 操作日志查询（只读） */
@RestController
@RequestMapping("/admin/logs")
@Tag(name = "后台-操作日志", description = "管理员操作日志查询")
public class AdminLogController {

    private final AdminLogRepository repo;
    private final AdminUserRepository adminUserRepository;

    public AdminLogController(AdminLogRepository repo, AdminUserRepository adminUserRepository) {
        this.repo = repo;
        this.adminUserRepository = adminUserRepository;
    }

    /** 获取当前登录管理员 */
    private LoginUser currentLoginUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof LoginUser u ? u : null;
    }

    @Operation(summary = "操作日志列表", description = "仅超级管理员可查看全部日志；管理员及其他角色仅可查看本人操作日志")
    @GetMapping
    public Result<Page<JSONObject>> list(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        LoginUser u = currentLoginUser();
        // 仅超级管理员可查看全部；其他角色（含普通管理员）仅查看本人日志
        // JWT 中角色带 ADMIN_ 前缀：ADMIN_SUPER_ADMIN / ADMIN_ADMIN / ADMIN_EDITOR / ADMIN_VIEWER
        boolean isSuperAdmin = u != null && u.role() != null && u.role().endsWith("SUPER_ADMIN");

        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay(zone).toLocalDateTime();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay(zone).toLocalDateTime();
        Page<AdminLog> result = repo.search(
                isSuperAdmin ? null : u == null ? 0L : u.id(),
                type == null || type.isBlank() ? null : type,
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                startTime,
                endTime,
                pageable);
        Map<Long, AdminUser> operators = adminUsers(result);
        Page<JSONObject> views = result.map(log -> {
            JSONObject view = (JSONObject) JSON.toJSON(log);
            AdminUser operator = log.getOperatorId() == null ? null : operators.get(log.getOperatorId());
            String displayName = operator == null || operator.getName() == null || operator.getName().isBlank()
                    ? log.getOperator() : operator.getName();
            view.put("operator", displayName == null || displayName.isBlank() ? "-" : displayName);
            view.put("operatorAccount", operator == null ? log.getOperator() : operator.getUsername());
            return view;
        });
        return Result.success(views, page, result.getTotalElements());
    }

    private Map<Long, AdminUser> adminUsers(Page<AdminLog> logs) {
        Set<Long> ids = logs.stream()
                .map(AdminLog::getOperatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return adminUserRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AdminUser::getId, Function.identity()));
    }
}
