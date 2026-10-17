package com.kuaima.app.admin.controller;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.dto.BossOrderView;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;

/**
 * 后台 Dashboard 聚合统计接口
 */
@RestController
@RequestMapping("/admin/dashboard")
@Tag(name = "后台-仪表盘", description = "运营数据看板")
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final BossOrderRespository orderRepository;
    private final SettlementRespository settlementRepository;
    private final BaseOrderItemRespository orderItemRepository;

    public AdminDashboardController(UserRepository userRepository,
                                    BossOrderRespository orderRepository,
                                    SettlementRespository settlementRepository,
                                    BaseOrderItemRespository orderItemRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /** 基础统计 */
    @Operation(summary = "Dashboard 基础统计", description = "返回 workerTotal、bossTotal、orderTotal、settledTotal、pendingAudit、totalRevenue(累计营收/元)")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();

        long workerCount = userRepository.countWorkerIdentities();
        long bossCount = userRepository.countBossIdentities();
        List<BossOrder> allOrders = orderRepository.findAll();
        long orderTotal = countOrdersOnDate(allOrders, new Date());
        long settledTotal = settlementRepository.count();

        data.put("workerTotal", workerCount);
        data.put("bossTotal", bossCount);
        data.put("orderTotal", orderTotal);
        data.put("settledTotal", settledTotal);
        data.put("totalRevenue", settlementRepository.sumPaidTotalAmount());

        // 待审核订单数
        long pendingAudit = allOrders.stream()
                .filter(o -> "待审核".equals(o.getOrderStatus())).count();
        data.put("pendingAudit", pendingAudit);

        return Result.success(data);
    }

    private long countOrdersOnDate(List<BossOrder> orders, Date date) {
        Calendar start = Calendar.getInstance();
        start.setTime(date);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 1);
        return orders.stream()
                .map(BossOrder::getTimestamp)
                .filter(Objects::nonNull)
                .filter(ts -> !ts.before(start.getTime()) && ts.before(end.getTime()))
                .count();
    }

    /** 订单趋势（按时间范围每日订单数） */
    @Operation(summary = "订单趋势", description = "range=7d/30d/month，默认 7d。返回 {labels: [...], values: [...]}")
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "7d") String range) {
        List<BossOrder> allOrders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));

        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        int dayCount;
        switch (range == null ? "" : range) {
            case "30d" -> {
                dayCount = 30;
                start.add(Calendar.DAY_OF_MONTH, -29);
            }
            case "month" -> {
                start.set(Calendar.DAY_OF_MONTH, 1);
                dayCount = start.getActualMaximum(Calendar.DAY_OF_MONTH);
            }
            case "7d", "" -> {
                dayCount = 7;
                start.add(Calendar.DAY_OF_MONTH, -6);
            }
            default -> throw new IllegalArgumentException("range 只能是 7d、30d 或 month");
        }
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, dayCount);

        // 按日期分组统计指定范围
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");

        Map<String, Long> dailyCount = new LinkedHashMap<>();
        for (int i = 0; i < dayCount; i++) {
            Calendar c = (Calendar) start.clone();
            c.add(Calendar.DAY_OF_MONTH, i);
            dailyCount.put(sdf.format(c.getTime()), 0L);
        }

        for (BossOrder order : allOrders) {
            Timestamp ts = order.getTimestamp();
            if (ts != null && !ts.before(start.getTime()) && ts.before(end.getTime())) {
                String key = sdf.format(ts);
                dailyCount.computeIfPresent(key, (ignored, count) -> count + 1);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("labels", new ArrayList<>(dailyCount.keySet()));
        data.put("values", new ArrayList<>(dailyCount.values()));
        return Result.success(data);
    }

    /** 工种分布（按 type 字段分组统计） */
    @Operation(summary = "工种分布", description = "按招工类型（日结/压薪日结/月结）分组统计订单数，用于饼图。每项含 name、value、color")
    @GetMapping("/distribution")
    public Result<List<Map<String, Object>>> distribution() {
        List<BossOrder> allOrders = orderRepository.findAll();

        Map<String, Long> typeCount = allOrders.stream()
                .collect(Collectors.groupingBy(
                        o -> {
                            String t = o.getType();
                            if (t == null || t.isBlank()) return "其他";
                            return switch (t) {
                                case "daily" -> "日结";
                                case "heldBack" -> "压薪日结";
                                case "month" -> "月结";
                                default -> t;
                            };
                        },
                        Collectors.counting()
                ));

        String[] colors = {"#FF6B35", "#2563EB", "#10B981", "#F59E0B", "#8B5CF6"};
        List<Map<String, Object>> result = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Long> entry : typeCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            item.put("color", colors[i % colors.length]);
            result.add(item);
            i++;
        }
        return Result.success(result);
    }

    /** 最近订单（最新8条，含雇主名称） */
    @Operation(summary = "最近订单", description = "返回最新 8 条订单（含雇主、零工名称），优先使用报名记录状态，与后台订单管理页保持一致")
    @GetMapping("/recent-orders")
    public Result<List<BossOrderView>> recentOrders() {
        PageRequest pageable = PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "id"));
        Page<BossOrder> orders = orderRepository.findAll(pageable);

        // 批量查询雇主名称
        Set<Long> employerIds = orders.stream()
                .map(BossOrder::getCreateBy)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> employerNames = new HashMap<>();
        if (!employerIds.isEmpty()) {
            userRepository.findAllById(employerIds).forEach(u -> {
                String name = (u.getCompanyName() != null && !u.getCompanyName().isBlank())
                        ? u.getCompanyName()
                        : (u.getNickname() != null ? u.getNickname() : u.getUsername());
                employerNames.put(u.getId(), name);
            });
        }

        List<Long> orderIds = orders.getContent().stream().map(BossOrder::getId).toList();
        Map<Long, BaseOrderItem> workerItems = new HashMap<>();
        if (!orderIds.isEmpty()) {
            orderItemRepository.findByOrderIdIn(orderIds).stream()
                    .sorted(Comparator.comparing(BaseOrderItem::getId).reversed())
                    .forEach(item -> workerItems.putIfAbsent(item.getOrderId(), item));
        }
        Set<Long> workerIds = workerItems.values().stream().map(BaseOrderItem::getUserId)
                .filter(id -> id != null && id > 0).collect(Collectors.toSet());
        Map<Long, String> workerNames = new HashMap<>();
        userRepository.findAllById(workerIds).forEach(user -> {
            String name = user.getRealName();
            if (name == null || name.isBlank()) name = user.getNickname();
            if (name == null || name.isBlank()) name = user.getUsername();
            workerNames.put(user.getId(), name);
        });

        List<BossOrderView> views = orders.getContent().stream()
                .map(order -> new BossOrderView(
                        order.getId(),
                        order.getOrderTitle(),
                        order.getOrderContent(),
                        null,
                        order.getOrderNum(),
                        Optional.ofNullable(workerItems.get(order.getId()))
                                .map(BaseOrderItem::getStatus)
                                .filter(status -> status != null && !status.isBlank())
                                .orElse(order.getOrderStatus()),
                        order.getType(),
                        order.getPostion(),
                        order.getDuration(),
                        order.getSalary(),
                        order.getAddress(),
                        order.getLongitude(),
                        order.getLatitude(),
                        order.getTags(),
                        order.getTrialDuration(),
                        order.getTimestamp(),
                        order.getStartTime(),
                        order.getEndTime(),
                        order.getCreateBy() != null ? employerNames.getOrDefault(order.getCreateBy(), "未知雇主") : "未知雇主",
                        Optional.ofNullable(workerItems.get(order.getId())).map(BaseOrderItem::getUserId)
                                .map(workerNames::get).orElse(null),
                        0L,
                        order.getAuditBy(),
                        order.getAuditTime(),
                        null
                ))
                .collect(Collectors.toList());

        return Result.success(views);
    }
}
