package com.kuaima.app.admin.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.kuaima.app.admin.dto.PointPurchaseDtos.CreateRequest;
import com.kuaima.app.admin.dto.PointPurchaseDtos.OrderResponse;
import com.kuaima.app.admin.entity.PointPurchaseOrder;
import com.kuaima.app.admin.repository.PointPurchaseOrderRepository;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.wechat.service.WechatPayService;

import jakarta.persistence.EntityNotFoundException;

@Service
public class PointPurchaseService {
    public static final BigDecimal UNIT_PRICE = new BigDecimal("0.01");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ORDER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final PointPurchaseOrderRepository orderRepo;
    private final UserRepository userRepo;
    private final PointsAccountRepository accountRepo;
    private final PointsFlowRepository flowRepo;
    private final WechatPayService wechatPay;

    public PointPurchaseService(PointPurchaseOrderRepository orderRepo, UserRepository userRepo,
            PointsAccountRepository accountRepo, PointsFlowRepository flowRepo) {
        this(orderRepo, userRepo, accountRepo, flowRepo, null);
    }

    @Autowired
    public PointPurchaseService(PointPurchaseOrderRepository orderRepo, UserRepository userRepo,
            PointsAccountRepository accountRepo, PointsFlowRepository flowRepo, WechatPayService wechatPay) {
        this.orderRepo = orderRepo; this.userRepo = userRepo; this.accountRepo = accountRepo; this.flowRepo = flowRepo; this.wechatPay = wechatPay;
    }

    @Transactional
    public OrderResponse create(CreateRequest req, Long operatorId, String operatorName) {
        if (req == null || req.bossId() == null) throw new IllegalArgumentException("bossId 不能为空");
        if (req.points() == null || req.points() < 1) throw new IllegalArgumentException("积分必须是正整数，最少1积分");
        if (!List.of("微信支付", "支付宝", "对公转账").contains(req.payMethod())) throw new IllegalArgumentException("payMethod 只能是微信支付、支付宝或对公转账");
        if (!List.of("paid", "pending").contains(req.deal())) throw new IllegalArgumentException("deal 只能是 paid 或 pending");
        User boss = userRepo.findById(req.bossId()).orElseThrow(() -> new EntityNotFoundException("老板不存在: " + req.bossId()));
        if (!UserRole.hasApprovedEnterprise(boss)) throw new IllegalArgumentException("指定用户未通过企业认证，不是有效老板账号");
        if ("冻结".equals(boss.getStatus()) || "禁用".equals(boss.getStatus()) || "DISABLED".equalsIgnoreCase(boss.getStatus())) throw new IllegalArgumentException("老板账号已冻结或禁用");
        String key = req.idempotencyKey();
        if (key == null || key.isBlank()) key = hash(operatorId + "|" + req.bossId() + "|" + req.points() + "|" + req.payMethod() + "|" + req.deal() + "|" + (req.remark() == null ? "" : req.remark()));
        var old = orderRepo.findByIdempotencyKey(key);
        if (old.isPresent()) return toDto(old.get());
        PointPurchaseOrder o = new PointPurchaseOrder();
        o.setIdempotencyKey(key); o.setOrderNo("PO" + LocalDateTime.now(ZONE).format(ORDER_TIME) + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        o.setBossId(boss.getId()); o.setBossName(boss.getNickname()); o.setCompanyName(boss.getCompanyName()); o.setPoints(req.points());
        o.setAmount(UNIT_PRICE.multiply(BigDecimal.valueOf(req.points())).setScale(2)); o.setUnitPrice(UNIT_PRICE);
        o.setPayMethod(req.payMethod()); o.setStatus("paid".equals(req.deal()) ? "支付成功" : "待支付"); o.setPurchaseTime(LocalDateTime.now(ZONE));
        o.setRemark(req.remark()); o.setOperatorId(operatorId); o.setOperatorName(operatorName); o.setOperatorTime(LocalDateTime.now(ZONE)); o.setPointsGranted(false);
        o = orderRepo.saveAndFlush(o);
        if ("paid".equals(req.deal())) grant(o);
        return toDto(o);
    }

    private void grant(PointPurchaseOrder o) {
        if (Boolean.TRUE.equals(o.getPointsGranted())) return;
        java.util.List<PointsAccount> lockedAccounts =
                accountRepo.findAllByUserIdAndRoleForUpdate(o.getBossId(), UserRole.BOSS);
        PointsAccount account = lockedAccounts.isEmpty()
                ? accountRepo.findFirstByUserIdAndRoleOrderByIdDesc(o.getBossId(), UserRole.BOSS).orElseGet(() -> {
                    PointsAccount a = new PointsAccount(); a.setUserId(o.getBossId()); a.setRole(UserRole.BOSS); a.setBalance(0); return accountRepo.saveAndFlush(a);
                })
                : lockedAccounts.get(0);
        long before = account.getBalance() == null ? 0 : account.getBalance(); long after = before + o.getPoints();
        if (after > Integer.MAX_VALUE) throw new IllegalArgumentException("积分余额超出系统上限");
        account.setBalance((int) after); accountRepo.save(account);
        PointsFlow flow = new PointsFlow(); flow.setUserId(o.getBossId()); flow.setRole(UserRole.BOSS); flow.setDelta(Math.toIntExact(o.getPoints())); flow.setBizType("ADMIN_PURCHASE"); flow.setRemark("积分购买订单 " + o.getOrderNo() + "，余额=" + after); flow.setBalanceAfter((int) after); flow.setBizNo(o.getOrderNo()); flow.setOperatorId(o.getOperatorId()); flowRepo.save(flow);
        o.setPointsGranted(true); orderRepo.save(o);
    }

    @Transactional
    public OrderResponse confirmPayment(String orderNo, String payMethod, String transactionId, Long operatorId, String operatorName) {
        if (orderNo == null || orderNo.isBlank()) throw new IllegalArgumentException("订单号不能为空");
        PointPurchaseOrder o = orderRepo.findByOrderNoForUpdate(orderNo).orElseThrow(() -> new EntityNotFoundException("积分购买订单不存在: " + orderNo));
        if ("微信支付".equals(o.getPayMethod()) && wechatPay != null) {
            throw new IllegalStateException("微信支付订单必须等待微信支付回调确认");
        }
        if ("支付成功".equals(o.getStatus())) return toDto(o);
        if (!"待支付".equals(o.getStatus())) throw new IllegalArgumentException("当前订单状态不能确认支付");
        if (payMethod != null && !payMethod.isBlank() && !payMethod.equals(o.getPayMethod())) throw new IllegalArgumentException("支付方式与订单不一致");
        o.setStatus("支付成功"); o.setPaidAt(LocalDateTime.now(ZONE)); o.setOperatorId(operatorId); o.setOperatorName(operatorName); o.setOperatorTime(LocalDateTime.now(ZONE));
        if (transactionId != null && !transactionId.isBlank()) o.setWechatTransactionId(transactionId.trim());
        grant(o); return toDto(o);
    }

    public OrderResponse getOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) throw new IllegalArgumentException("订单号不能为空");
        return orderRepo.findByOrderNo(orderNo).map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("积分购买订单不存在: " + orderNo));
    }

    public Map<String, Object> createWechatNativePayment(String orderNo) {
        if (wechatPay == null) throw new IllegalStateException("微信支付服务未配置");
        PointPurchaseOrder order = orderRepo.findByOrderNo(orderNo)
                .orElseThrow(() -> new EntityNotFoundException("积分购买订单不存在: " + orderNo));
        if (!"待支付".equals(order.getStatus())) throw new IllegalArgumentException("当前订单状态不能发起支付");
        if (!"微信支付".equals(order.getPayMethod())) throw new IllegalArgumentException("当前订单不是微信支付订单");
        String codeUrl = order.getWechatCodeUrl();
        if (codeUrl == null || codeUrl.isBlank()) {
            codeUrl = wechatPay.prepayNative("快马日结积分购买", order.getOrderNo(), order.getAmount());
            order.setWechatCodeUrl(codeUrl);
            orderRepo.save(order);
        }
        return Map.of("orderNo", order.getOrderNo(), "payMethod", order.getPayMethod(), "amount", order.getAmount(), "codeUrl", codeUrl);
    }

    public Page<OrderResponse> search(String keyword, String payMethod, String status, LocalDate date, Pageable pageable) {
        var range = range(date); Page<PointPurchaseOrder> p = orderRepo.search(blank(keyword), blank(payMethod), blank(status), range[0], range[1], pageable); return p.map(this::toDto);
    }
    public List<PointPurchaseOrder> searchAll(String keyword, String payMethod, String status, LocalDate date) { var r=range(date); return orderRepo.searchAll(blank(keyword),blank(payMethod),blank(status),r[0],r[1]); }
    public Map<String,Object> stats(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now(ZONE) : date; LocalDateTime from=d.atStartOfDay(), to=d.plusDays(1).atStartOfDay(); LocalDateTime prevFrom=d.minusDays(1).atStartOfDay(), prevTo=from;
        LocalDate month=d.withDayOfMonth(1), next=month.plusMonths(1), prevMonth=month.minusMonths(1);
        long count=orderRepo.countPaid(from,to), prevCount=orderRepo.countPaid(prevFrom,prevTo); Long points=orderRepo.sumPaidPoints(from,to); Long prevPoints=orderRepo.sumPaidPoints(prevFrom,prevTo); BigDecimal amount=orderRepo.sumPaidAmount(from,to), prevAmount=orderRepo.sumPaidAmount(prevFrom,prevTo); Long monthPoints=orderRepo.sumPaidPoints(month.atStartOfDay(),next.atStartOfDay()); BigDecimal monthAmount=orderRepo.sumPaidAmount(month.atStartOfDay(),next.atStartOfDay()); Long prevMonthPoints=orderRepo.sumPaidPoints(prevMonth.atStartOfDay(),month.atStartOfDay()); BigDecimal prevMonthAmount=orderRepo.sumPaidAmount(prevMonth.atStartOfDay(),month.atStartOfDay());
        Map<String,Object> changes=new LinkedHashMap<>(); changes.put("todayCount", pct(count,prevCount)); changes.put("todayPoints",pct(points,prevPoints)); changes.put("monthPoints",pct(monthPoints,prevMonthPoints)); changes.put("monthAmount",pct(monthAmount,prevMonthAmount));
        Map<String,Object> out=new LinkedHashMap<>(); out.put("todayCount",count); out.put("todayPoints",points); out.put("monthPoints",monthPoints); out.put("monthAmount",monthAmount); out.put("changes",changes); return out;
    }
    private double pct(Number a, Number b){ double x=a==null?0:a.doubleValue(), y=b==null?0:b.doubleValue(); return y==0?(x==0?0:100):Math.round((x-y)*1000.0/y)/10.0; }
    private LocalDateTime[] range(LocalDate d){ return d==null?new LocalDateTime[]{null,null}:new LocalDateTime[]{d.atStartOfDay(),d.plusDays(1).atStartOfDay()}; }
    private String blank(String s){ return s==null||s.isBlank()?null:s.trim(); }
    public OrderResponse toDto(PointPurchaseOrder o){
        String employerId = userRepo.findById(o.getBossId())
                .map(user -> user.getCompanyCode() != null && !user.getCompanyCode().isBlank()
                        ? user.getCompanyCode() : user.getBossCode())
                .orElse(null);
        if (employerId == null || employerId.isBlank()) {
            employerId = "B" + String.format("%07d", o.getBossId());
        }
        return new OrderResponse(o.getId(),o.getOrderNo(),o.getBossId(),employerId,o.getBossName(),o.getCompanyName(),o.getPoints(),o.getAmount(),o.getUnitPrice(),o.getPayMethod(),o.getStatus(),o.getPurchaseTime(),o.getRemark(),o.getOperatorId(),o.getOperatorName());
    }
    private String hash(String s){ try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){ throw new IllegalStateException(e); } }
}
