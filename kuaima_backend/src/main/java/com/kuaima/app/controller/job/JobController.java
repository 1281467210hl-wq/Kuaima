package com.kuaima.app.controller.job;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.model.BossOrderQuery;
import com.kuaima.app.domain.boss.service.BossOrderService;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.domain.browsehistory.entity.BrowseHistory;
import com.kuaima.app.domain.browsehistory.repository.BrowseHistoryRepository;
import com.kuaima.app.domain.jobfavorite.entity.JobFavorite;
import com.kuaima.app.domain.jobfavorite.repository.JobFavoriteRepository;
import com.kuaima.app.domain.enterprise.repository.EnterpriseRepository;
import com.kuaima.app.common.service.OssStorageService;
import org.springframework.util.StringUtils;

/**
 * 零工端岗位收藏与浏览记录。
 */
@RestController
@RequestMapping("/jobs")
@Tag(name = "零工-招工", description = "零工浏览/搜索/报名招工订单")
public class JobController {

    private final JobFavoriteRepository favoriteRepository;
    private final BrowseHistoryRepository browseHistoryRepository;
    private final BossOrderRespository orderRepository;
    private final BossOrderService bossOrderService;
    private final EnterpriseRepository enterpriseRepository;
    private final OssStorageService ossStorageService;

    public JobController(JobFavoriteRepository favoriteRepository,
                         BrowseHistoryRepository browseHistoryRepository,
                         BossOrderRespository orderRepository,
                         BossOrderService bossOrderService) {
        this(favoriteRepository, browseHistoryRepository, orderRepository, bossOrderService, null, null);
    }

    public JobController(JobFavoriteRepository favoriteRepository,
                         BrowseHistoryRepository browseHistoryRepository,
                         BossOrderRespository orderRepository,
                         BossOrderService bossOrderService,
                         EnterpriseRepository enterpriseRepository) {
        this(favoriteRepository, browseHistoryRepository, orderRepository, bossOrderService, enterpriseRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JobController(JobFavoriteRepository favoriteRepository,
                         BrowseHistoryRepository browseHistoryRepository,
                         BossOrderRespository orderRepository,
                         BossOrderService bossOrderService,
                         EnterpriseRepository enterpriseRepository,
                         OssStorageService ossStorageService) {
        this.favoriteRepository = favoriteRepository;
        this.browseHistoryRepository = browseHistoryRepository;
        this.orderRepository = orderRepository;
        this.bossOrderService = bossOrderService;
        this.enterpriseRepository = enterpriseRepository;
        this.ossStorageService = ossStorageService;
    }

    /** 零工公开岗位列表：GET /jobs。仅返回招工中的岗位，不按老板归属过滤。 */
    @Operation(summary = "零工公开岗位列表", description = "按类型、城市、薪资、标签等条件查询招工中的岗位")
    @GetMapping
    public Result<List<BossOrder>> listPublicJobs(@ModelAttribute BossOrderQuery query) {
        query.setStatus("招工中");
        Page<BossOrder> result = bossOrderService.listOrders(null, query);
        return Result.success(result.getContent(), result.getNumber(), result.getTotalElements());
    }

    /** 零工岗位详情：GET /jobs/{id}。 */
    @Operation(summary = "零工岗位详情", description = "查询招工中的公开岗位详情")
    @GetMapping("/{id}")
    public Result<BossOrder> getPublicJob(@PathVariable Long id) {
        BossOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("岗位不存在: " + id));
        if (!"招工中".equals(order.getOrderStatus())) {
            throw new jakarta.persistence.EntityNotFoundException("岗位不存在或已停止招工");
        }
        if (enterpriseRepository != null && order.getEnterpriseId() != null) {
            enterpriseRepository.findById(order.getEnterpriseId())
                    .ifPresent(enterprise -> order.setCompanyName(
                            enterprise.getCompanyName() == null ? "" : enterprise.getCompanyName()));
        }
        if (order.getCompanyName() == null) order.setCompanyName("");
        fillMediaPreview(order);
        return Result.success(order);
    }

    private void fillMediaPreview(BossOrder order) {
        if (ossStorageService == null || order == null) return;
        if (StringUtils.hasText(order.getImageKeys())) {
            order.setImageUrls(java.util.Arrays.stream(order.getImageKeys().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(ossStorageService::inlinePreviewUrl)
                    .toList());
        }
        if (StringUtils.hasText(order.getVideoKey())) {
            order.setVideoUrl(ossStorageService.playableUrl(order.getVideoKey()));
        }
    }

    /** 零工报名：POST /jobs/{orderId}/apply。用户身份从 JWT 获取。 */
    @Operation(summary = "零工报名岗位", description = "当前登录零工报名公开岗位，不接受 userId")
    @PostMapping("/{orderId}/apply")
    public Result<BaseOrderItem> applyPublicJob(@PathVariable Long orderId,
                                                 @RequestBody(required = false) Map<String, Object> body,
                                                 Authentication authentication) {
        LoginUser loginUser = currentWorker(authentication);
        String remark = body == null || body.get("remark") == null ? null : body.get("remark").toString();
        Boolean trial = body == null || body.get("trial") == null
                ? null : Boolean.valueOf(body.get("trial").toString());
        return Result.success(bossOrderService.applyOrder(orderId, loginUser.id(), remark, trial));
    }

    private LoginUser currentWorker(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.USER.equals(loginUser.role()) && loginUser.id() != null) {
            return loginUser;
        }
        throw new com.kuaima.app.common.ForbiddenBusinessException("当前登录账号不是零工账号");
    }

    // ==================== 收藏 ====================

    /** 收藏岗位列表：GET /jobs/favorites?userId=1 */
    @Operation(summary = "收藏岗位列表", description = "返回 JobFavorite 列表，每项含收藏 ID 与关联 BossOrder 订单详情")
    @GetMapping("/favorites")
    public Result<List<Map<String, Object>>> listFavorites(@RequestParam Long userId) {
        List<JobFavorite> favorites = favoriteRepository.findByUserIdOrderByIdDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JobFavorite fav : favorites) {
            Map<String, Object> item = new HashMap<>();
            item.put("favoriteId", fav.getId());
            item.put("orderId", fav.getOrderId());
            orderRepository.findById(fav.getOrderId()).ifPresent(order -> item.put("order", order));
            result.add(item);
        }
        return Result.success(result);
    }

    /** 收藏岗位：POST /jobs/favorites  body: { "userId": 1, "orderId": 2 } */
    @Operation(summary = "收藏岗位", description = "请求体含 userId 和 orderId。重复收藏返回 400 已收藏过该岗位")
    @PostMapping("/favorites")
    @Transactional
    public Result<JobFavorite> favoriteJob(@RequestBody Map<String, Long> body) {
        Long userId = body.get("userId");
        Long orderId = body.get("orderId");
        if (userId == null || orderId == null) {
            throw new IllegalArgumentException("userId 和 orderId 不能为空");
        }
        if (favoriteRepository.existsByUserIdAndOrderId(userId, orderId)) {
            return Result.error(400, "已收藏过该岗位");
        }
        JobFavorite fav = new JobFavorite();
        fav.setUserId(userId);
        fav.setOrderId(orderId);
        return Result.success(favoriteRepository.save(fav));
    }

    /** 取消收藏：DELETE /jobs/favorites/{id} */
    @Operation(summary = "取消收藏岗位", description = "按收藏记录 ID 删除 JobFavorite")
    @DeleteMapping("/favorites/{id}")
    @Transactional
    public Result<Void> unfavoriteJob(@PathVariable Long id) {
        favoriteRepository.deleteById(id);
        return Result.success();
    }

    /** 检查收藏状态：GET /jobs/favorites/check?userId=1&orderId=2 */
    @Operation(summary = "检查收藏状态", description = "返回 {favorited: true/false} 表示当前用户是否已收藏该订单")
    @GetMapping("/favorites/check")
    public Result<Map<String, Object>> checkFavorite(@RequestParam Long userId, @RequestParam Long orderId) {
        Map<String, Object> result = new HashMap<>();
        result.put("favorited", favoriteRepository.existsByUserIdAndOrderId(userId, orderId));
        return Result.success(result);
    }

    // ==================== 浏览记录 ====================

    /** 浏览记录分页：GET /jobs/history?userId=1&page=0&size=20 */
    @Operation(summary = "浏览记录列表", description = "返回 BrowseHistory 分页列表，每项含 historyId、orderId、viewedAt 与关联 BossOrder 详情")
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> listBrowseHistory(@RequestParam Long userId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<BrowseHistory> result = browseHistoryRepository.findByUserIdOrderByViewedAtDesc(userId, pageable);
        List<Map<String, Object>> list = new ArrayList<>();
        for (BrowseHistory h : result.getContent()) {
            Map<String, Object> item = new HashMap<>();
            item.put("historyId", h.getId());
            item.put("orderId", h.getOrderId());
            item.put("viewedAt", h.getViewedAt());
            orderRepository.findById(h.getOrderId()).ifPresent(order -> item.put("order", order));
            list.add(item);
        }
        return Result.success(list, result.getNumber(), result.getTotalElements());
    }

    /** 记录浏览：POST /jobs/history  body: { "userId": 1, "orderId": 2 } */
    @Operation(summary = "记录岗位浏览", description = "请求体含 userId 和 orderId，写入 BrowseHistory 并记录 viewedAt 时间")
    @PostMapping("/history")
    @Transactional
    public Result<BrowseHistory> recordBrowse(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"), "userId");
        Long orderId = toLong(body.get("orderId"), "orderId");
        if (userId == null || orderId == null) {
            throw new IllegalArgumentException("userId 和 orderId 不能为空");
        }
        BrowseHistory history = new BrowseHistory();
        history.setUserId(userId);
        history.setOrderId(orderId);
        history.setViewedAt(new Timestamp(System.currentTimeMillis()));
        return Result.success(browseHistoryRepository.save(history));
    }

    private Long toLong(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " 必须是整数");
        }
    }

    /** 清空浏览记录：DELETE /jobs/history?userId=1 */
    @Operation(summary = "清空浏览记录", description = "按 userId 删除该用户的全部 BrowseHistory 记录")
    @DeleteMapping("/history")
    @Transactional
    public Result<Void> clearBrowseHistory(@RequestParam Long userId) {
        browseHistoryRepository.deleteByUserId(userId);
        return Result.success();
    }
}
