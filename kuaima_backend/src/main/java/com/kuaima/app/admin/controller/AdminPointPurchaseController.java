package com.kuaima.app.admin.controller;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.dto.PointPurchaseDtos.CreateRequest;
import com.kuaima.app.admin.dto.PointPurchaseDtos.PayRequest;
import com.kuaima.app.admin.dto.PointPurchaseDtos.OrderResponse;
import com.kuaima.app.admin.entity.PointPurchaseOrder;
import com.kuaima.app.admin.repository.AdminUserRepository;
import com.kuaima.app.admin.service.PointPurchaseService;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/admin/point-purchase")
@Tag(name = "后台-积分购买")
public class AdminPointPurchaseController {
    private final PointPurchaseService service;
    private final AdminUserRepository adminRepo;
    public AdminPointPurchaseController(PointPurchaseService service, AdminUserRepository adminRepo) { this.service=service; this.adminRepo=adminRepo; }

    @GetMapping("/stats")
    @Operation(summary="积分购买统计")
    public Result<java.util.Map<String,Object>> stats(@RequestParam(required=false) String date) { requireAdmin(false); return Result.success(service.stats(parseDate(date))); }

    @GetMapping("/orders")
    @Operation(summary="积分购买订单分页")
    public Result<List<OrderResponse>> orders(@RequestParam(required=false) String keyword, @RequestParam(required=false) String payMethod,
            @RequestParam(required=false) String status, @RequestParam(required=false) String date,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        requireAdmin(false); if(page<0||size<1||size>200) throw new IllegalArgumentException("page 或 size 参数无效");
        Page<OrderResponse> result=service.search(keyword,payMethod,status,parseDate(date),PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"purchaseTime")));
        return Result.success(result.getContent(),page,result.getTotalElements());
    }

    @GetMapping("/orders/{orderNo}")
    @Operation(summary="查询积分购买订单")
    public Result<OrderResponse> order(@PathVariable String orderNo) {
        requireAdmin(false); return Result.success(service.getOrder(orderNo));
    }

    @GetMapping("/orders/{orderNo}/pay-url")
    @Operation(summary="创建微信 Native 扫码支付")
    public Result<java.util.Map<String,Object>> payUrl(@PathVariable String orderNo,
            @RequestParam(defaultValue="微信支付") String payMethod) {
        requireAdmin(true);
        if (!"微信支付".equals(payMethod)) throw new IllegalArgumentException("当前仅支持微信扫码支付");
        return Result.success(service.createWechatNativePayment(orderNo));
    }

    @PostMapping("/orders")
    @Operation(summary="管理员代客购买积分")
    public Result<OrderResponse> create(@RequestBody CreateRequest request) {
        LoginUser u=requireAdmin(true); String name=u.username();
        var admin=adminRepo.findById(u.id()).orElse(null); if(admin!=null && admin.getName()!=null) name=admin.getName();
        return Result.success(service.create(request,u.id(),name));
    }

    @PostMapping("/orders/{orderNo}/pay")
    @Operation(summary="确认积分购买订单支付")
    public Result<OrderResponse> confirmPayment(@org.springframework.web.bind.annotation.PathVariable String orderNo, @RequestBody(required=false) PayRequest request) {
        LoginUser u=requireAdmin(true); String name=u.username();
        var admin=adminRepo.findById(u.id()).orElse(null); if(admin!=null && admin.getName()!=null) name=admin.getName();
        return Result.success(service.confirmPayment(orderNo, request == null ? null : request.payMethod(), request == null ? null : request.transactionId(), u.id(), name));
    }

    @GetMapping("/orders/export")
    @Operation(summary="导出积分购买订单")
    public ResponseEntity<byte[]> export(@RequestParam(required=false) String keyword, @RequestParam(required=false) String payMethod,
            @RequestParam(required=false) String status, @RequestParam(required=false) String date) throws Exception {
        requireAdmin(false); List<PointPurchaseOrder> list=service.searchAll(keyword,payMethod,status,parseDate(date));
        try(XSSFWorkbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            var sheet=wb.createSheet("积分购买订单"); String[] heads={"订单号","老板ID","老板名称","企业名称","购买积分","实付金额","兑换单价","支付方式","状态","购买时间","备注","操作管理员"};
            Row h=sheet.createRow(0); for(int i=0;i<heads.length;i++) h.createCell(i).setCellValue(heads[i]);
            int r=1; for(PointPurchaseOrder o:list){ Row row=sheet.createRow(r++); Object[] v={o.getOrderNo(),o.getBossId(),o.getBossName(),o.getCompanyName(),o.getPoints(),o.getAmount().toPlainString(),o.getUnitPrice().toPlainString(),o.getPayMethod(),o.getStatus(),o.getPurchaseTime().toString(),o.getRemark(),o.getOperatorName()}; for(int i=0;i<v.length;i++) row.createCell(i).setCellValue(v[i]==null?"":String.valueOf(v[i])); }
            for(int i=0;i<heads.length;i++) sheet.autoSizeColumn(i); wb.write(out); String fn=URLEncoder.encode("积分购买订单.xlsx",StandardCharsets.UTF_8).replace("+","%20");
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fn).build().toString()).body(out.toByteArray());
        }
    }
    private LocalDate parseDate(String date){ if(date==null||date.isBlank()) return null; try{return LocalDate.parse(date);}catch(Exception e){throw new IllegalArgumentException("date 必须是 yyyy-MM-dd 格式");} }
    private LoginUser requireAdmin(boolean write){ Object p=SecurityContextHolder.getContext().getAuthentication()==null?null:SecurityContextHolder.getContext().getAuthentication().getPrincipal(); if(!(p instanceof LoginUser u)||u.id()==null||u.role()==null||!u.role().startsWith("ADMIN_")) throw new ForbiddenBusinessException("仅管理员可操作"); if(write && u.role().endsWith("VIEWER")) throw new ForbiddenBusinessException("当前管理员无积分购买权限"); var a=adminRepo.findById(u.id()).orElseThrow(()->new ForbiddenBusinessException("管理员账号不存在")); if("禁用".equals(a.getStatus())||"DISABLED".equalsIgnoreCase(a.getStatus())) throw new ForbiddenBusinessException("管理员账号已禁用"); return u; }
}
