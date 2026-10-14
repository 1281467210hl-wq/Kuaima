package com.kuaima.app.admin.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.dto.AdminMerchantAccountDto;
import com.kuaima.app.admin.service.AdminMerchantAccountService;
import com.kuaima.app.common.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 后台-闪电发薪「商户账户余额」：跨老板商户账户查询与维护、充值订单查询。
 * <p>余额按「分」存储，出参同时给 balanceFen（分）与 balanceYuan（元字符串）；充值订单金额按「元」存储。
 */
@RestController
@RequestMapping("/admin/merchant-accounts")
@Tag(name = "后台-商户账户", description = "商户账户余额列表/详情/增删改、充值订单查询")
public class AdminMerchantAccountController {

    private final AdminMerchantAccountService merchantAccountService;

    public AdminMerchantAccountController(AdminMerchantAccountService merchantAccountService) {
        this.merchantAccountService = merchantAccountService;
    }

    @GetMapping
    @Operation(summary = "商户账户列表（跨老板）",
            description = "keyword 命中账户简称/主体全称/商户号；bossId 或 bossKeyword 筛选老板；status 不传则排除已删除(deleted)")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long bossId,
                                            @RequestParam(required = false) String bossKeyword,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Boolean isDefault,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        AdminMerchantAccountDto.Query query = new AdminMerchantAccountDto.Query();
        query.setKeyword(keyword);
        query.setBossId(bossId);
        query.setBossKeyword(bossKeyword);
        query.setStatus(status);
        query.setIsDefault(isDefault);
        query.setPage(page);
        query.setSize(size);
        Map<String, Object> data = merchantAccountService.list(query);
        return Result.success(data, page, ((Number) data.getOrDefault("total", 0L)).longValue());
    }

    @GetMapping("/recharge-orders")
    @Operation(summary = "充值订单查询（跨老板）",
            description = "支持 bossId/bossKeyword/status(paid|pending|failed|canceled|manual_pending|manual_confirmed)/payMethod/orderNo/日期范围 + 分页")
    public Result<Map<String, Object>> rechargeOrders(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Long bossId,
                                                      @RequestParam(required = false) Long accountId,
                                                      @RequestParam(required = false) String bossKeyword,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String payMethod,
                                                      @RequestParam(required = false) String orderNo,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        AdminMerchantAccountDto.Query query = new AdminMerchantAccountDto.Query();
        query.setKeyword(keyword);
        query.setBossId(bossId);
        query.setAccountId(accountId);
        query.setBossKeyword(bossKeyword);
        query.setStatus(status);
        query.setPayMethod(payMethod);
        query.setOrderNo(orderNo);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPage(page);
        query.setSize(size);
        Map<String, Object> data = merchantAccountService.rechargeOrders(query);
        return Result.success(data, page, ((Number) data.getOrDefault("total", 0L)).longValue());
    }

    @GetMapping("/{id}")
    @Operation(summary = "商户账户余额详情",
            description = "返回 account（含分/元余额、冻结、开户银行、默认标记）、stats（累计充值/累计发薪支出）、最近充值订单与转账记录")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(merchantAccountService.detail(id));
    }

    @PostMapping
    @Operation(summary = "新增商户账户", description = "bossId/accountName(公司简称)/subjectName(主体全称)/merchantNo 必填；同一老板仅一个默认账户")
    public Result<Map<String, Object>> create(@RequestBody AdminMerchantAccountDto.SaveRequest request) {
        return Result.success(merchantAccountService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑商户账户", description = "字段按需传入（null 不覆盖）；status 可置 active/frozen/deleted（软删除）")
    public Result<Map<String, Object>> update(@PathVariable Long id,
                                              @RequestBody AdminMerchantAccountDto.SaveRequest request) {
        return Result.success(merchantAccountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商户账户（软删除）", description = "置 status=deleted 并取消默认标记，余额与流水保留")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        return Result.success(merchantAccountService.delete(id));
    }
}
