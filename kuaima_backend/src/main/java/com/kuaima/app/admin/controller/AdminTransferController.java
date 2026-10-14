package com.kuaima.app.admin.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.dto.AdminTransferQueryDto;
import com.kuaima.app.admin.service.AdminTransferService;
import com.kuaima.app.common.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 后台-闪电发薪「转账记录」：平台全局视角的转账列表/统计、人员明细、汇总与导出。
 * <p>金额单位与实体保持一致：payroll_order/payroll_detail 为「分」，充值单为「元」（接口已换算为分并附元字符串）。
 */
@RestController
@RequestMapping("/admin/transfers")
@Tag(name = "后台-转账记录", description = "转账记录列表与统计、人员明细、状态汇总、CSV 导出")
public class AdminTransferController {

    private final AdminTransferService transferService;

    public AdminTransferController(AdminTransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    @Operation(summary = "转账记录列表与统计",
            description = "direction=out 出账(发薪)/in 入账(充值)/all 合并；status=success|processing|failed；"
                    + "bossId/bossKeyword 老板筛选，projectId 项目筛选，keyword 命中单号/标题/公司/项目/收款人；返回 data.stats 统计与 data.records 当页列表")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String direction,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long bossId,
                                            @RequestParam(required = false) String bossKeyword,
                                            @RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String payMethod,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        AdminTransferQueryDto query = buildQuery(direction, status, keyword, bossId, bossKeyword, projectId,
                type, payMethod, startDate, endDate, page, size);
        Map<String, Object> data = transferService.list(query);
        long total = ((Number) data.getOrDefault("total", 0L)).longValue();
        return Result.success(data, page, total);
    }

    @GetMapping("/{id}/details")
    @Operation(summary = "转账人员明细", description = "id 为出账记录（payroll_order.id）的主键；入账记录（充值单）无人员明细，请用 /admin/merchant-accounts/recharge-orders 查询")
    public Result<Map<String, Object>> details(@PathVariable Long id) {
        return Result.success(transferService.details(id));
    }

    @GetMapping("/summary")
    @Operation(summary = "转账汇总",
            description = "按筛选条件统计成功/处理中/失败的笔数与金额（分）：out/in 分方向 + merged 合并，out.detailStatus 为人员维度状态汇总")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) String direction,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long bossId,
                                               @RequestParam(required = false) String bossKeyword,
                                               @RequestParam(required = false) Long projectId,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(required = false) String payMethod,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                               @RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AdminTransferQueryDto query = buildQuery(direction, status, keyword, bossId, bossKeyword, projectId,
                type, payMethod, startDate, endDate, 0, 10);
        return Result.success(transferService.summary(query));
    }

    @PostMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "导出转账记录 CSV", description = "按筛选条件导出，返回 text/csv 文本流（含 UTF-8 BOM），文件名 admin-transfers-yyyyMMdd-HHmmss.csv")
    public ResponseEntity<byte[]> export(@RequestBody(required = false) AdminTransferQueryDto body) {
        AdminTransferQueryDto query = body == null ? new AdminTransferQueryDto() : body;
        byte[] csv = transferService.exportCsv(query);
        String filename = "admin-transfers-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv);
    }

    /** 列表/汇总/导出的公共查询装配：size 上限 200，导出可用请求体自带 size（未使用）。 */
    private AdminTransferQueryDto buildQuery(String direction, String status, String keyword, Long bossId,
                                             String bossKeyword, Long projectId, String type, String payMethod,
                                             LocalDate startDate, LocalDate endDate, int page, int size) {
        AdminTransferQueryDto query = new AdminTransferQueryDto();
        query.setDirection(direction);
        query.setStatus(status);
        query.setKeyword(keyword);
        query.setBossId(bossId);
        query.setBossKeyword(bossKeyword);
        query.setProjectId(projectId);
        query.setType(type);
        query.setPayMethod(payMethod);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPage(page);
        query.setSize(size);
        return query;
    }
}
