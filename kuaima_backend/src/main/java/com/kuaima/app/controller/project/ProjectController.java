package com.kuaima.app.controller.project;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.domain.project.entity.Project;
import com.kuaima.app.domain.project.service.ProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/boss/projects")
@Tag(name = "老板-项目管理", description = "项目列表、详情、设置、归档、删除")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @Operation(summary = "项目列表（创建中/已归档）")
    public Result<List<Project>> list(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Project> result = projectService.listProjects(keyword, status, PageRequest.of(page, size));
        return Result.success(result.getContent(), page, result.getTotalElements());
    }

    @PostMapping
    @Operation(summary = "创建项目")
    public Result<Project> create(@RequestBody Project project,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            project.setIdempotencyKey(idempotencyKey.trim());
        }
        return Result.success(projectService.create(project, null));
    }

    @GetMapping("/{id}")
    @Operation(summary = "项目详情")
    public Result<Project> detail(@PathVariable Long id) {
        return Result.success(projectService.getOrThrow(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新项目（含项目设置）")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        return Result.success(projectService.update(id, project));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档项目")
    public Result<Void> archive(@PathVariable Long id) {
        projectService.archive(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除项目（软删除）")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success();
    }

    /** 项目维度统计（员工/项目入口使用）。 */
    @GetMapping("/stats/overview")
    @Operation(summary = "项目总览统计")
    public Result<Map<String, Long>> overview() {
        Map<String, Long> map = new java.util.LinkedHashMap<>();
        map.put("created", projectService.countByStatus("active"));
        map.put("archived", projectService.countByStatus("archived"));
        return Result.success(map);
    }
}
