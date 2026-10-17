package com.kuaima.app.domain.review.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.review.entity.BossReview;
import com.kuaima.app.domain.review.model.BossReviewModels.*;
import com.kuaima.app.domain.review.repository.BossReviewRepository;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.service.CreditScoreService;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;

@Service
public class BossReviewService {
    private final BossReviewRepository reviews;
    private final BaseOrderItemRespository items;
    private final BossOrderRespository orders;
    private final UserRepository users;
    private CreditScoreService creditScoreService;

    public BossReviewService(BossReviewRepository reviews, BaseOrderItemRespository items,
                             BossOrderRespository orders, UserRepository users) {
        this.reviews = reviews; this.items = items; this.orders = orders; this.users = users;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCreditScoreService(CreditScoreService creditScoreService) {
        this.creditScoreService = creditScoreService;
    }

    @Transactional
    public ReviewView save(Long workerId, Long itemId, SaveRequest request) {
        if (request == null) throw new IllegalArgumentException("请求体不能为空");
        validateScore("attitudeScore", request.attitudeScore());
        validateScore("settlementScore", request.settlementScore());
        validateScore("environmentScore", request.environmentScore());
        String content = normalizeContent(request.content());
        BaseOrderItem item = ownedItem(workerId, itemId, true);
        if (!BossStatus.ITEM_FINISHED.equals(item.getStatus())) {
            throw new IllegalStateException("仅已完成的订单条目允许评价老板，当前状态：" + item.getStatus());
        }
        BossOrder order = orders.findById(item.getOrderId())
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + item.getOrderId()));
        if (order.getCreateBy() == null || !users.existsById(order.getCreateBy())) {
            throw new EntityNotFoundException("订单所属老板不存在");
        }
        BossReview review = reviews.findByItemId(itemId).orElseGet(BossReview::new);
        int previousDelta = review.getId() == null ? 0 : reviewDelta(review);
        if (review.getId() != null && (!workerId.equals(review.getWorkerId())
                || !item.getOrderId().equals(review.getOrderId())
                || !order.getCreateBy().equals(review.getBossId()))) {
            throw new ForbiddenBusinessException("评价归属关系异常，无权修改");
        }
        review.setItemId(itemId); review.setOrderId(item.getOrderId()); review.setWorkerId(workerId);
        review.setBossId(order.getCreateBy()); review.setAttitudeScore(request.attitudeScore());
        review.setSettlementScore(request.settlementScore()); review.setEnvironmentScore(request.environmentScore());
        review.setContent(content);
        BossReview saved = reviews.save(review);
        if (creditScoreService != null) {
            int average = (saved.getAttitudeScore() + saved.getSettlementScore() + saved.getEnvironmentScore()) / 3;
            int delta = reviewDelta(saved) - previousDelta;
            creditScoreService.adjust(saved.getBossId(), CreditScoreService.BOSS_CREDIT, delta,
                    "BOSS_REVIEW_SCORE", "REVIEW", "BOSS_REVIEW_SCORE:" + saved.getId() + ":" + saved.getAttitudeScore() + ":" + saved.getSettlementScore() + ":" + saved.getEnvironmentScore(), "零工评价信用分调整");
        }
        return view(saved);
    }

    private int reviewDelta(BossReview review) {
        if (review.getAttitudeScore() == null || review.getSettlementScore() == null || review.getEnvironmentScore() == null) return 0;
        int average = (review.getAttitudeScore() + review.getSettlementScore() + review.getEnvironmentScore()) / 3;
        return average == 5 ? 5 : average <= 2 ? -10 : 0;
    }

    @Transactional(readOnly = true)
    public ReviewView get(Long workerId, Long itemId) {
        ownedItem(workerId, itemId, false);
        return reviews.findByItemId(itemId).map(this::view).orElse(null);
    }

    @Transactional(readOnly = true)
    public java.util.List<ReviewView> listFromWorkers(Long bossId, Long enterpriseId, Long orderId) {
        BossOrder order = orders.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("订单不存在: " + orderId));
        boolean accessible = enterpriseId != null
                ? enterpriseId.equals(order.getEnterpriseId())
                : bossId != null && bossId.equals(order.getCreateBy());
        if (!accessible) throw new ForbiddenBusinessException("无权查看其他企业的订单评价");
        var orderReviews = reviews.findByOrderIdOrderByIdAsc(orderId);
        Map<Long, String> workerNames = new HashMap<>();
        users.findAllById(orderReviews.stream().map(BossReview::getWorkerId).toList())
                .forEach(worker -> workerNames.put(worker.getId(),
                        worker.getRealName() != null ? worker.getRealName() : worker.getNickname()));
        return orderReviews.stream()
                .map(review -> view(review, workerNames.getOrDefault(
                        review.getWorkerId(), "零工" + review.getWorkerId())))
                .toList();
    }

    private BaseOrderItem ownedItem(Long workerId, Long itemId, boolean lock) {
        if (!users.existsById(workerId)) throw new EntityNotFoundException("零工不存在: " + workerId);
        BaseOrderItem item = (lock ? items.findByIdForUpdate(itemId) : items.findById(itemId))
                .orElseThrow(() -> new EntityNotFoundException("订单条目不存在: " + itemId));
        if (!workerId.equals(item.getUserId())) throw new ForbiddenBusinessException("无权评价其他零工的订单条目");
        return item;
    }
    private void validateScore(String name, Integer value) {
        if (value == null) throw new IllegalArgumentException(name + " 不能为空");
        if (value < 1 || value > 5) throw new IllegalArgumentException(name + " 必须是1到5的整数");
    }
    private String normalizeContent(String value) {
        if (value == null) return null;
        String content = value.trim();
        if (content.codePointCount(0, content.length()) > 200) throw new IllegalArgumentException("content 最多200字");
        return content.isEmpty() ? null : content;
    }
    private ReviewView view(BossReview r) {
        return view(r, "零工" + r.getWorkerId());
    }

    private ReviewView view(BossReview r, String workerName) {
        return new ReviewView(r.getId(), r.getItemId(), r.getOrderId(), r.getWorkerId(), r.getBossId(), r.getAttitudeScore(),
                r.getSettlementScore(), r.getEnvironmentScore(), r.getContent(), r.getCreatedAt(), r.getUpdatedAt(),
                workerName);
    }
}
