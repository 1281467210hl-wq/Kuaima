package com.kuaima.app.domain.review.service;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.review.entity.WorkerReview;
import com.kuaima.app.domain.review.model.WorkerReviewModels.*;
import com.kuaima.app.domain.review.repository.WorkerReviewRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.service.CreditScoreService;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WorkerReviewService {
    private final WorkerReviewRepository reviews;
    private final BossOrderRespository orders;
    private final BaseOrderItemRespository items;
    private final CreditScoreService scores;
    private final UserRepository users;

    @Transactional
    public List<ReviewView> saveOrderReviews(Long bossId, Long orderId, SaveRequest request) {
        return saveOrderReviews(bossId, null, orderId, request);
    }

    @Transactional
    public List<ReviewView> saveOrderReviews(Long bossId, Long enterpriseId, Long orderId, SaveRequest request) {
        validate(request);
        BossOrder order = orders.findById(orderId).orElseThrow(() -> new EntityNotFoundException("订单不存在: " + orderId));
        requireOrderAccess(bossId, enterpriseId, order);
        if (!BossStatus.ORDER_COMPLETED.equals(order.getOrderStatus())) throw new IllegalStateException("仅已完成订单可以评价零工");
        var completed = items.findByOrderId(orderId).stream()
                .filter(item -> BossStatus.ITEM_FINISHED.equals(item.getStatus()) && item.getUserId() != null)
                .toList();
        if (completed.isEmpty()) throw new IllegalStateException("该订单没有可评价的已完成零工");
        for (var item : completed) {
            if (reviews.existsByItemId(item.getId())) continue;
            WorkerReview review = new WorkerReview();
            review.setItemId(item.getId()); review.setOrderId(orderId); review.setWorkerId(item.getUserId()); review.setBossId(bossId);
            review.setOverallScore(request.overallScore()); review.setAttitudeScore(request.attitudeScore());
            review.setEfficiencyScore(request.efficiencyScore()); review.setSkillScore(request.skillScore());
            review.setContent(StringUtils.hasText(request.content()) ? request.content().trim() : null);
            WorkerReview saved = reviews.save(review);
            if (request.overallScore() == 5) {
                scores.adjust(item.getUserId(), CreditScoreService.WORKER_STAR, 100,
                        "WORKER_FIVE_STAR_REVIEW", "WORKER_REVIEW",
                        "WORKER_FIVE_STAR_REVIEW:" + item.getId(), "获得老板五星好评");
            }
        }
        return list(bossId, enterpriseId, orderId);
    }

    @Transactional(readOnly = true)
    public List<ReviewView> list(Long bossId, Long orderId) {
        return list(bossId, null, orderId);
    }

    @Transactional(readOnly = true)
    public List<ReviewView> list(Long bossId, Long enterpriseId, Long orderId) {
        BossOrder order = orders.findById(orderId).orElseThrow(() -> new EntityNotFoundException("订单不存在: " + orderId));
        requireOrderAccess(bossId, enterpriseId, order);
        List<WorkerReview> orderReviews = reviews.findByOrderIdOrderByIdAsc(orderId);
        java.util.Map<Long, String> workerNames = new java.util.HashMap<>();
        users.findAllById(orderReviews.stream().map(WorkerReview::getWorkerId).toList())
                .forEach(worker -> workerNames.put(worker.getId(),
                        StringUtils.hasText(worker.getRealName()) ? worker.getRealName() : worker.getNickname()));
        return orderReviews.stream()
                .map(review -> view(review, workerNames.getOrDefault(
                        review.getWorkerId(), "零工" + review.getWorkerId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, ReviewStatusView> statuses(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return java.util.Map.of();
        var completedByOrder = items.findByOrderIdIn(orderIds).stream()
                .filter(item -> BossStatus.ITEM_FINISHED.equals(item.getStatus()) && item.getUserId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        com.kuaima.app.domain.boss.entity.BaseOrderItem::getOrderId,
                        java.util.stream.Collectors.counting()));
        var reviewCountByOrder = reviews.findByOrderIdIn(orderIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        WorkerReview::getOrderId, java.util.stream.Collectors.counting()));
        return orderIds.stream().distinct().collect(java.util.stream.Collectors.toMap(
                orderId -> orderId,
                orderId -> new ReviewStatusView(
                        orderId,
                        completedByOrder.getOrDefault(orderId, 0L),
                        reviewCountByOrder.getOrDefault(orderId, 0L))));
    }

    private void validate(SaveRequest request) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        score("overallScore", request.overallScore()); score("attitudeScore", request.attitudeScore());
        score("efficiencyScore", request.efficiencyScore()); score("skillScore", request.skillScore());
        if (request.content() != null && request.content().codePointCount(0, request.content().length()) > 200)
            throw new IllegalArgumentException("content 最多200字");
    }
    private void score(String name, Integer value) {
        if (value == null || value < 1 || value > 5) throw new IllegalArgumentException(name + " 必须是1到5的整数");
    }

    private void requireOrderAccess(Long bossId, Long enterpriseId, BossOrder order) {
        boolean accessible = enterpriseId != null
                ? enterpriseId.equals(order.getEnterpriseId())
                : bossId != null && bossId.equals(order.getCreateBy());
        if (!accessible) throw new ForbiddenBusinessException("无权评价其他企业的订单");
    }

    private ReviewView view(WorkerReview r) {
        return view(r, "零工" + r.getWorkerId());
    }

    private ReviewView view(WorkerReview r, String workerName) {
        return new ReviewView(r.getId(), r.getItemId(), r.getOrderId(), r.getWorkerId(), r.getOverallScore(),
                r.getAttitudeScore(), r.getEfficiencyScore(), r.getSkillScore(), r.getContent(), r.getCreatedAt(),
                workerName);
    }
}
