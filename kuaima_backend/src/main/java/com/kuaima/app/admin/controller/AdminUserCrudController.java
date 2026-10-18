package com.kuaima.app.admin.controller;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.entity.AdminUser;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.security.model.LoginUser;

/** 管理员账号管理 */
@RestController
@RequestMapping("/admin/admin-users")
@Tag(name = "后台-用户CRUD", description = "用户增删改查")
public class AdminUserCrudController {

    private final AdminUserRepository repo;
    private final PasswordEncoder passwordEncoder;

    public AdminUserCrudController(AdminUserRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    /** 获取当前登录管理员的 ID（来自 JWT 过滤器写入的 SecurityContext） */
    private Long currentAdminId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser u) {
            return u.id();
        }
        return null;
    }

    /** 管理员密码规则（新建 / 编辑 / 重置 / 修改共用）：8-50 位，同时包含字母和数字；不符合时返回提示文案。 */
    static String passwordProblem(String password) {
        if (password == null || password.length() < 8) return "密码至少8位";
        if (password.length() > 50) return "密码不能超过50位";
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) return "密码必须同时包含字母和数字";
        return null;
    }

    /** 当前登录账号是否为超级管理员 */
    private boolean currentIsSuperAdmin() {
        Long currentId = currentAdminId();
        return currentId != null
                && repo.findById(currentId).map(a -> "SUPER_ADMIN".equals(a.getRole())).orElse(false);
    }

    @Operation(summary = "账号列表", description = "分页查询管理员账号列表，按 id 倒序；超级管理员返回全部，普通管理员仅返回自己的账号及自己创建的账号")
    @GetMapping
    public Result<Page<AdminUser>> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Long currentId = currentAdminId();
        Page<AdminUser> result;
        // 判断当前管理员是否为超级管理员：是则返回全部，否则仅返回自己 + 自己创建的
        boolean isSuperAdmin = (currentId != null
                && repo.findById(currentId).map(a -> "SUPER_ADMIN".equals(a.getRole())).orElse(false));
        if (isSuperAdmin) {
            result = repo.findAll(pageable);
        } else {
            result = repo.findVisibleByAdmin(currentId, pageable);
        }
        return Result.success(result, page, result.getTotalElements());
    }

    /** 管理员详情 */
    @Operation(summary = "管理员详情", description = "按 ID 获取管理员账号详情，包含角色、权限树、创建者等")
    @GetMapping("/{id}")
    public Result<AdminUser> get(@PathVariable Long id) {
        return Result.success(repo.findById(id).orElseThrow());
    }

    @Operation(summary = "新建管理员", description = "新建管理员账号；不允许新建超级管理员（role=SUPER_ADMIN）；createdBy 自动记录为当前登录管理员 ID")
    @PostMapping
    public Result<AdminUser> create(@RequestBody AdminUser admin) {
        // 禁止新建超级管理员
        if ("SUPER_ADMIN".equals(admin.getRole())) {
            return Result.error(403, "不允许新建超级管理员账号");
        }
        if (repo.existsByUsername(admin.getUsername())) {
            return Result.error(400, "账号已存在");
        }
        String problem = passwordProblem(admin.getPassword());
        if (problem != null) return Result.error(400, problem);
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        admin.setCreatedBy(currentAdminId());
        admin.setCreateTime(LocalDateTime.now());
        admin.setUpdateTime(LocalDateTime.now());
        return Result.success(repo.save(admin));
    }

    @Operation(summary = "编辑账号", description = "编辑账号；超级管理员受系统保护不可修改角色/权限/状态；超级管理员可操作所有账号，普通管理员仅可操作自己创建的账号（角色/权限树/状态/密码）")
    @PutMapping("/{id}")
    public Result<AdminUser> update(@PathVariable Long id, @RequestBody AdminUser patch) {
        AdminUser existing = repo.findById(id).orElseThrow();
        boolean targetIsSuperAdmin = "SUPER_ADMIN".equals(existing.getRole());
        Long currentId = currentAdminId();
        // 有权操作角色/权限/状态/密码：当前登录为超级管理员，或该账号是自己创建的
        boolean canOperate = currentIsSuperAdmin()
                || (currentId != null && currentId.equals(existing.getCreatedBy()));

        // 超级管理员保护：不允许修改角色、权限、状态
        if (targetIsSuperAdmin) {
            if (patch.getRole() != null && !patch.getRole().equals(existing.getRole())) {
                return Result.error(403, "超级管理员角色不可修改");
            }
            if (patch.getPermissions() != null && !patch.getPermissions().equals(existing.getPermissions())) {
                return Result.error(403, "超级管理员权限不可修改");
            }
            if (patch.getStatus() != null && !patch.getStatus().equals(existing.getStatus())) {
                return Result.error(403, "超级管理员账号不可禁用");
            }
            if (Boolean.FALSE.equals(patch.getAllowLogin())) {
                return Result.error(403, "超级管理员账号不可禁止登录");
            }
        } else if (!canOperate) {
            // 非自己创建的账号（且当前不是超级管理员）：只能编辑基本信息
            if ((patch.getRole() != null && !patch.getRole().equals(existing.getRole()))
                    || (patch.getPermissions() != null && !patch.getPermissions().equals(existing.getPermissions()))
                    || (patch.getStatus() != null && !patch.getStatus().equals(existing.getStatus()))
                    || (patch.getAllowLogin() != null && !patch.getAllowLogin().equals(existing.getAllowLogin()))
                    || (patch.getReceiveNotifications() != null && !patch.getReceiveNotifications().equals(existing.getReceiveNotifications()))
                    || (patch.getPassword() != null && !patch.getPassword().isBlank())) {
                return Result.error(403, "只能编辑自己创建的账号的角色/权限/状态");
            }
        }

        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getAvatar() != null) existing.setAvatar(patch.getAvatar());
        if (patch.getRole() != null && !targetIsSuperAdmin && canOperate) existing.setRole(patch.getRole());
        if (patch.getDept() != null) existing.setDept(patch.getDept());
        if (patch.getPhone() != null) existing.setPhone(patch.getPhone());
        if (patch.getEmail() != null) existing.setEmail(patch.getEmail());
        if (patch.getRemark() != null) existing.setRemark(patch.getRemark());
        if (patch.getPermissions() != null && !targetIsSuperAdmin && canOperate) existing.setPermissions(patch.getPermissions());
        if (patch.getStatus() != null && canOperate) existing.setStatus(patch.getStatus());
        if (patch.getAllowLogin() != null && canOperate) existing.setAllowLogin(patch.getAllowLogin());
        if (patch.getReceiveNotifications() != null && canOperate) existing.setReceiveNotifications(patch.getReceiveNotifications());
        if (patch.getPassword() != null && !patch.getPassword().isBlank() && canOperate) {
            String problem = passwordProblem(patch.getPassword());
            if (problem != null) return Result.error(400, problem);
            existing.setPassword(passwordEncoder.encode(patch.getPassword()));
        }
        existing.setUpdateTime(LocalDateTime.now());
        return Result.success(repo.save(existing));
    }

    /** 重置密码 */
    @Operation(summary = "重置密码", description = "由操作人指定新密码（newPassword 必填，8-50 位且含字母和数字）；超级管理员可重置所有账号，普通管理员只能重置自己创建的账号")
    @PutMapping("/{id}/reset-password")
    public Result<AdminUser> resetPassword(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        AdminUser existing = repo.findById(id).orElseThrow();
        Long currentId = currentAdminId();
        boolean canOperate = currentIsSuperAdmin()
                || (currentId != null && currentId.equals(existing.getCreatedBy()));
        if (!canOperate) {
            return Result.error(403, "只能重置自己创建的账号的密码");
        }
        // 不再默认重置为 123456：弱口令且所有被重置账号相同
        String pwd = body == null ? null : body.get("newPassword");
        String problem = passwordProblem(pwd);
        if (problem != null) return Result.error(400, problem);
        existing.setPassword(passwordEncoder.encode(pwd));
        existing.setUpdateTime(LocalDateTime.now());
        return Result.success(repo.save(existing));
    }

    /** 当前登录管理员修改自己的密码 */
    @Operation(summary = "修改本人密码", description = "body：{oldPassword, newPassword}；校验旧密码，新密码 8-50 位且含字母和数字，且不能与旧密码相同")
    @PutMapping("/me/password")
    public Result<Void> changeOwnPassword(@RequestBody java.util.Map<String, String> body) {
        Long currentId = currentAdminId();
        if (currentId == null) return Result.error(401, "请先登录");
        AdminUser me = repo.findById(currentId).orElse(null);
        if (me == null) return Result.error(404, "管理员不存在");
        String oldPassword = body == null ? null : body.get("oldPassword");
        String newPassword = body == null ? null : body.get("newPassword");
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, me.getPassword())) {
            return Result.error(400, "原密码不正确");
        }
        String problem = passwordProblem(newPassword);
        if (problem != null) return Result.error(400, problem);
        if (newPassword.equals(oldPassword)) return Result.error(400, "新密码不能与原密码相同");
        me.setPassword(passwordEncoder.encode(newPassword));
        me.setUpdateTime(LocalDateTime.now());
        repo.save(me);
        return Result.success();
    }

    @Operation(summary = "删除管理员", description = "删除管理员账号；超级管理员账号不可删除；超级管理员可删除所有账号，普通管理员只能删除自己创建的账号")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AdminUser existing = repo.findById(id).orElseThrow();
        if ("SUPER_ADMIN".equals(existing.getRole())) {
            return Result.error(403, "超级管理员账号不可删除");
        }
        Long currentId = currentAdminId();
        boolean canOperate = currentIsSuperAdmin()
                || (currentId != null && currentId.equals(existing.getCreatedBy()));
        if (!canOperate) {
            return Result.error(403, "只能删除自己创建的账号");
        }
        repo.deleteById(id);
        return Result.success();
    }
}
