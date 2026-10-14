package com.kuaima.app.admin.controller;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.service.AdminRecruitService;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.position.entity.BossPosition;
import com.kuaima.app.domain.position.repository.BossPositionRepository;
import com.kuaima.app.domain.position.service.BossPositionService;
import com.kuaima.app.domain.recruit.entity.PositionApplication;
import com.kuaima.app.domain.recruit.repository.PositionApplicationRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;

/**
 * 后台-智能招聘/岗位管理（平台全局视角）。
 * 归属老板通过 BossPosition.createBy（boss 用户 id）关联补齐 bossName。
 */
@RestController
@RequestMapping("/admin/positions")
@Tag(name = "后台-智能招聘岗位", description = "全局岗位列表/详情/统计/导出，字段对齐 admin-web 智能招聘页")
public class AdminPositionController {

    private final BossPositionRepository positionRepository;
    private final com.kuaima.app.domain.recruit.repository.AdminBossPositionRepository adminPositionRepository;
    private final BossPositionService positionService;
    private final PositionApplicationRepository applicationRepository;
    private final AdminRecruitService recruitService;

    public AdminPositionController(BossPositionRepository positionRepository,
                                   com.kuaima.app.domain.recruit.repository.AdminBossPositionRepository adminPositionRepository,
                                   BossPositionService positionService,
                                   PositionApplicationRepository applicationRepository,
                                   AdminRecruitService recruitService) {
        this.positionRepository = positionRepository;
        this.adminPositionRepository = adminPositionRepository;
        this.positionService = positionService;
        this.applicationRepository = applicationRepository;
        this.recruitService = recruitService;
    }

    @GetMapping
    @Operation(summary = "岗位列表（全局）", description = "keyword 匹配岗位名/编码，status 传 recruiting/paused/filled 或原始 on/off，可按 bossId 过滤")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) Long bossId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        Page<BossPosition> positions = search(keyword, status, bossId, page, size);
        Map<Long, String> bossNames = recruitService.bossNames(
                positions.getContent().stream().map(BossPosition::getCreateBy).collect(Collectors.toSet()));
        List<Map<String, Object>> rows = positions.getContent().stream()
                .map(p -> recruitService.positionRow(p, bossNames.get(p.getCreateBy())))
                .toList();
        return Result.success(rows, page, positions.getTotalElements());
    }

    @GetMapping("/stats")
    @Operation(summary = "岗位统计", description = "岗位总数/在招/停用 + 招聘/面试/录用汇总 + 投递(候选人)总数")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>(positionService.getStatistics());
        stats.put("applicationCount", applicationRepository.count());
        stats.put("pendingCount", applicationRepository.countByStatus("PENDING"));
        stats.put("interviewingCount", applicationRepository.countByStatus("INTERVIEWING"));
        stats.put("hiredCount", applicationRepository.countByStatus("HIRED"));
        return Result.success(stats);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "导出岗位 CSV", description = "与列表同筛选条件，最多导出 10000 条")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long bossId) {
        Page<BossPosition> positions = search(keyword, status, bossId, 0, 10000);
        Map<Long, String> bossNames = recruitService.bossNames(
                positions.getContent().stream().map(BossPosition::getCreateBy).collect(Collectors.toSet()));
        StringBuilder csv = new StringBuilder("岗位编码,岗位名称,类型,所属老板,招聘人数,已投递,面试中,已录用,状态,发布时间\n");
        for (BossPosition p : positions) {
            Map<String, Object> row = recruitService.positionRow(p, bossNames.get(p.getCreateBy()));
            csv.append(csvCell(row.get("code"))).append(',')
                    .append(csvCell(row.get("name"))).append(',')
                    .append(csvCell(row.get("type"))).append(',')
                    .append(csvCell(row.get("bossName"))).append(',')
                    .append(csvCell(row.get("headcount"))).append(',')
                    .append(csvCell(row.get("applied"))).append(',')
                    .append(csvCell(row.get("interviewed"))).append(',')
                    .append(csvCell(row.get("hired"))).append(',')
                    .append(csvCell(row.get("statusName"))).append(',')
                    .append(csvCell(row.get("publishTime"))).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=admin-positions.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping
    @Operation(summary = "发布岗位（全局代录）",
            description = "bossId 为归属老板 user id（query 参数，建议必传，否则 bossName 为空）；code 缺省时自动生成 POS+时间戳；复用 BossPositionService.createPosition")
    public Result<Map<String, Object>> create(@RequestBody BossPosition position,
                                              @RequestParam(required = false) Long bossId) {
        if (position.getName() == null || position.getName().isBlank()) {
            throw new IllegalArgumentException("岗位名称必填");
        }
        if (bossId != null) {
            position.setCreateBy(bossId);
        }
        if (position.getCode() == null || position.getCode().isBlank()) {
            position.setCode("POS" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        }
        BossPosition saved = positionService.createPosition(position);
        return Result.success(recruitService.positionRow(saved, recruitService.bossName(saved.getCreateBy())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑岗位（name/code/category/department/location/hireCount/salaryRange/experience/education/description/status 由 Service 覆盖）")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody BossPosition updates) {
        BossPosition saved = positionService.updatePosition(id, updates);
        return Result.success(recruitService.positionRow(saved, recruitService.bossName(saved.getCreateBy())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除岗位（物理删除，复用 BossPositionService.deletePosition）")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.deletePosition(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "岗位状态切换（on/off，复用 Service.toggleStatus）")
    public Result<Map<String, Object>> toggleStatus(@PathVariable Long id) {
        BossPosition saved = positionService.toggleStatus(id);
        return Result.success(recruitService.positionRow(saved, recruitService.bossName(saved.getCreateBy())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "岗位详情", description = "岗位完整字段 + bossName + 该岗位最近投递（最多20条）")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        BossPosition position = positionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("岗位不存在: " + id));
        Map<String, Object> detail = recruitService.positionRow(position,
                recruitService.bossName(position.getCreateBy()));
        detail.put("description", position.getDescription());
        List<PositionApplication> applications = applicationRepository.findByPositionIdOrderByIdDesc(id);
        List<Map<String, Object>> recent = applications.stream().limit(20).map(application -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", application.getId());
            item.put("resumeId", application.getResumeId());
            item.put("userId", application.getUserId());
            item.put("status", application.getStatus());
            item.put("applyTime", application.getApplyTime());
            return item;
        }).toList();
        detail.put("recentApplications", recent);
        detail.put("applicationCount", (long) applications.size());
        return Result.success(detail);
    }

    /** status 兼容前端 UI 值（recruiting/paused/filled）与实体原始值（on/off）。 */
    private Page<BossPosition> search(String keyword, String status, Long bossId, int page, int size) {
        String normalizedKeyword = blankToNull(keyword);
        String normalizedStatus = normalizeStatus(status);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        if (bossId == null) {
            return positionRepository.searchByKeywordAndStatus(normalizedKeyword, normalizedStatus, pageable);
        }
        return adminPositionRepository.findAll((Specification<BossPosition>) (root, cq, cb) -> cb.and(
                cb.equal(root.get("createBy"), bossId),
                normalizedKeyword == null ? cb.conjunction()
                        : cb.or(cb.like(root.get("name"), "%" + normalizedKeyword + "%"),
                                cb.like(root.get("code"), "%" + normalizedKeyword + "%")),
                normalizedStatus == null ? cb.conjunction()
                        : cb.equal(root.get("status"), normalizedStatus)), pageable);
    }

    private String normalizeStatus(String status) {
        String value = blankToNull(status);
        if (value == null || "all".equalsIgnoreCase(value)) {
            return null;
        }
        return switch (value) {
            case "recruiting", "filled" -> "on";
            case "paused" -> "off";
            default -> value;
        };
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }
}
