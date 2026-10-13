package com.kuaima.app.domain.user.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.model.WorkerProfileModels.UpdateWorkerProfileRequest;
import com.kuaima.app.domain.user.model.WorkerProfileModels.WorkerProfile;
import com.kuaima.app.domain.user.model.WorkerOrderModels.WorkerOrder;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.review.entity.BossReview;
import com.kuaima.app.domain.review.model.BossReviewModels.ReviewSummary;
import com.kuaima.app.domain.review.repository.BossReviewRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class WorkerProfileService {

    private final UserRepository userRepository;
    private final BaseOrderItemRespository itemRepository;
    private final BossOrderRespository orderRepository;
    private final BossReviewRepository reviewRepository;

    public WorkerProfileService(UserRepository userRepository,
                                BaseOrderItemRespository itemRepository,
                                BossOrderRespository orderRepository,
                                BossReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public WorkerProfile getProfile(Long userId) {
        return toProfile(getUserOrThrow(userId));
    }

    @Transactional
    public WorkerProfile updateProfile(Long userId, UpdateWorkerProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        validate(request);
        User user = getUserOrThrow(userId);
        if (request.avatar() != null) user.setAvatar(trimToNull(request.avatar()));
        if (request.nickname() != null) user.setNickname(trimToNull(request.nickname()));
        if (request.gender() != null) user.setGender(trimToNull(request.gender()));
        if (request.birthday() != null) user.setBirthday(request.birthday());
        if (request.city() != null) user.setCity(trimToNull(request.city()));
        if (request.skills() != null) user.setSkills(trimToNull(request.skills()));
        if (request.workYears() != null) user.setWorkYears(request.workYears());
        if (request.acceptNightShift() != null) user.setAcceptNightShift(request.acceptNightShift());
        if (request.introduction() != null) user.setIntroduction(trimToNull(request.introduction()));
        return toProfile(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<WorkerOrder> listOrders(Long userId, String type, String status, Pageable pageable) {
        if (type != null && !type.isBlank()
                && !"daily".equals(type) && !"heldBack".equals(type) && !"month".equals(type)) {
            throw new IllegalArgumentException("订单类型不合法: " + type);
        }
        Collection<String> statuses = resolveStatuses(status);
        Page<BaseOrderItem> items = itemRepository.findWorkerOrders(userId,
                blankToNull(type), statuses, pageable);
        if (items.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, items.getTotalElements());
        }
        Map<Long, BossOrder> orders = orderRepository.findAllById(
                        items.getContent().stream().map(BaseOrderItem::getOrderId).distinct().toList())
                .stream().collect(Collectors.toMap(BossOrder::getId, Function.identity()));
        Map<Long, User> bosses = userRepository.findAllById(orders.values().stream()
                        .map(BossOrder::getCreateBy).filter(id -> id != null).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, BossReview> reviews = reviewRepository.findByItemIdIn(
                        items.getContent().stream().map(BaseOrderItem::getId).toList())
                .stream().collect(Collectors.toMap(BossReview::getItemId, Function.identity()));
        return items.map(item -> toWorkerOrder(item, orders.get(item.getOrderId()), bosses, reviews.get(item.getId())));
    }

    @Transactional(readOnly = true)
    public WorkerOrder getOrder(Long userId, Long itemId) {
        BaseOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + itemId));
        if (!userId.equals(item.getUserId())) {
            throw new ForbiddenBusinessException("只能查询当前登录零工的订单详情");
        }
        BossOrder order = orderRepository.findById(item.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("订单关联的岗位不存在: " + item.getOrderId()));
        User boss = order.getCreateBy() == null ? null
                : userRepository.findById(order.getCreateBy()).orElse(null);
        BossReview review = reviewRepository.findByItemId(item.getId()).orElse(null);
        return toWorkerOrder(item, order, boss == null ? Map.of() : Map.of(order.getCreateBy(), boss), review);
    }

    /**
     * 零工端整合三大状态 ↔ 底层报名状态的映射（0912 产品调整）。
     * <ul>
     *   <li>工作中 → 已报名 / 已录用 / 已到岗 / 待结算（进行中全部）</li>
     *   <li>已完成 → 已完成</li>
     *   <li>已取消 → 取消报名 / 已拒绝 / 取消招工（老板取消订单统一置为此）</li>
     *   <li>其他值（向后兼容，直接传底层状态）→ 单值集合</li>
     *   <li>null/blank → null（不过滤）</li>
     * </ul>
     */
    private Collection<String> resolveStatuses(String status) {
        String trimmed = blankToNull(status);
        if (trimmed == null) {
            return null;
        }
        return switch (trimmed) {
            case BossStatus.GROUP_WORKING ->
                    Arrays.asList(BossStatus.ITEM_APPLIED, BossStatus.ITEM_HIRED,
                            BossStatus.ITEM_ON_WORK, BossStatus.ITEM_PENDING_SETTLE);
            case BossStatus.GROUP_COMPLETED ->
                    List.of(BossStatus.ITEM_FINISHED);
            case BossStatus.GROUP_CANCELED ->
                    Arrays.asList(BossStatus.ITEM_CANCELED, BossStatus.ITEM_REJECTED, BossStatus.ITEM_CANCEL_BY_BOSS);
            default ->
                    List.of(trimmed);
        };
    }

    private WorkerOrder toWorkerOrder(BaseOrderItem item, BossOrder order, Map<Long, User> bosses, BossReview review) {
        if (order == null) {
            throw new EntityNotFoundException("报名关联的订单不存在: " + item.getOrderId());
        }
        User boss = order.getCreateBy() == null ? null : bosses.get(order.getCreateBy());
        return new WorkerOrder(item.getId(), item.getOrderId(), mapDisplayStatus(item.getStatus()), item.getTrialRequested(),
                item.getApplyDate(), item.getHireDate(), item.getWorkDate(), item.getFinishDate(),
                order.getOrderTitle(), order.getType(), order.getSalary(), order.getAddress(),
                order.getLongitude(), order.getLatitude(),
                order.getStartTime(), order.getEndTime(), boss == null ? null : boss.getCompanyName(),
                order.getPostion(), order.getOrderStatus(), order.getDuration(), order.getTags(), review != null,
                review == null ? null : new ReviewSummary(review.getAttitudeScore(), review.getSettlementScore(),
                        review.getEnvironmentScore(), review.getContent(), review.getUpdatedAt()));
    }

    /**
     * 0912 零工端状态文案统一：
     * 已到岗 → 工作中；取消报名/取消招工/已拒绝 → 已取消；其余不变。
     */
    private String mapDisplayStatus(String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        return switch (rawStatus) {
            case BossStatus.ITEM_ON_WORK -> BossStatus.DISPLAY_ON_WORK;
            case BossStatus.ITEM_CANCELED, BossStatus.ITEM_CANCEL_BY_BOSS, BossStatus.ITEM_REJECTED ->
                    BossStatus.GROUP_CANCELED;
            default -> rawStatus;
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validate(UpdateWorkerProfileRequest request) {
        if (request.nickname() != null && request.nickname().trim().isEmpty()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (request.birthday() != null && request.birthday().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("出生日期不能晚于今天");
        }
        if (request.workYears() != null && (request.workYears() < 0 || request.workYears() > 80)) {
            throw new IllegalArgumentException("工作年限必须在0到80年之间");
        }
    }

    private WorkerProfile toProfile(User user) {
        return new WorkerProfile(
                user.getId(), user.getAvatar(), user.getNickname(), user.getPhone(), user.getGender(),
                user.getBirthday(), user.getCity(), user.getSkills(), user.getWorkYears(),
                user.getAcceptNightShift(), user.getIntroduction());
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + userId));
    }

    private String trimToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
