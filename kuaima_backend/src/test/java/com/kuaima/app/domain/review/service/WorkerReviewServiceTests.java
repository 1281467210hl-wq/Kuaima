package com.kuaima.app.domain.review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.review.entity.WorkerReview;
import com.kuaima.app.domain.review.model.WorkerReviewModels.SaveRequest;
import com.kuaima.app.domain.review.repository.WorkerReviewRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.service.CreditScoreService;

class WorkerReviewServiceTests {
    private WorkerReviewRepository reviews;
    private BossOrderRespository orders;
    private BaseOrderItemRespository items;
    private CreditScoreService scores;
    private UserRepository users;
    private WorkerReviewService service;

    @BeforeEach
    void setUp() {
        reviews = mock(WorkerReviewRepository.class);
        orders = mock(BossOrderRespository.class);
        items = mock(BaseOrderItemRespository.class);
        scores = mock(CreditScoreService.class);
        users = mock(UserRepository.class);
        service = new WorkerReviewService(reviews, orders, items, scores, users);
        when(reviews.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void completedOrder_shouldCreateOneReviewPerCompletedWorker() {
        order(10L, 99L, BossStatus.ORDER_COMPLETED);
        when(items.findByOrderId(10L)).thenReturn(List.of(item(1L, 10L, 21L, BossStatus.ITEM_FINISHED),
                item(2L, 10L, 22L, BossStatus.ITEM_FINISHED), item(3L, 10L, 23L, BossStatus.ITEM_ON_WORK)));
        when(reviews.existsByItemId(any())).thenReturn(false);

        var result = service.saveOrderReviews(99L, 10L, new SaveRequest(5, 4, 5, 4, "  做得很好  "));

        assertNotNull(result);
        verify(reviews, times(2)).save(any(WorkerReview.class));
        verify(scores).adjust(21L, CreditScoreService.WORKER_STAR, 100, "WORKER_FIVE_STAR_REVIEW", "WORKER_REVIEW", "WORKER_FIVE_STAR_REVIEW:1", "获得老板五星好评");
        verify(scores).adjust(22L, CreditScoreService.WORKER_STAR, 100, "WORKER_FIVE_STAR_REVIEW", "WORKER_REVIEW", "WORKER_FIVE_STAR_REVIEW:2", "获得老板五星好评");
    }

    @Test
    void repeatedSubmit_shouldNotCreateOrAddScoreAgain() {
        order(10L, 99L, BossStatus.ORDER_COMPLETED);
        when(items.findByOrderId(10L)).thenReturn(List.of(item(1L, 10L, 21L, BossStatus.ITEM_FINISHED)));
        when(reviews.existsByItemId(1L)).thenReturn(true);

        assertTrue(service.saveOrderReviews(99L, 10L, new SaveRequest(5, 5, 5, 5, null)).isEmpty());
        verify(reviews, never()).save(any());
        verifyNoInteractions(scores);
    }

    @Test
    void nonOwnerAndIncompleteOrder_shouldBeRejected() {
        order(10L, 99L, BossStatus.ORDER_COMPLETED);
        when(items.findByOrderId(10L)).thenReturn(List.of(item(1L, 10L, 21L, BossStatus.ITEM_FINISHED)));
        assertThrows(ForbiddenBusinessException.class,
                () -> service.saveOrderReviews(88L, 10L, new SaveRequest(5, 5, 5, 5, null)));

        order(11L, 99L, BossStatus.ORDER_RECRUIT_END);
        assertThrows(IllegalStateException.class,
                () -> service.saveOrderReviews(99L, 11L, new SaveRequest(5, 5, 5, 5, null)));
    }

    @Test
    void enterpriseMember_shouldEvaluateOrderCreatedByAnotherMember() {
        BossOrder order = new BossOrder();
        order.setId(12L); order.setCreateBy(100L); order.setEnterpriseId(3L);
        order.setOrderStatus(BossStatus.ORDER_COMPLETED);
        when(orders.findById(12L)).thenReturn(Optional.of(order));
        when(items.findByOrderId(12L)).thenReturn(List.of(item(4L, 12L, 21L, BossStatus.ITEM_FINISHED)));
        when(reviews.existsByItemId(4L)).thenReturn(false);
        WorkerReview saved = new WorkerReview();
        saved.setItemId(4L); saved.setOrderId(12L); saved.setWorkerId(21L);
        when(reviews.findByOrderIdOrderByIdAsc(12L)).thenReturn(List.of(saved));
        User worker = new User(); worker.setId(21L); worker.setNickname("测试零工");
        when(users.findAllById(List.of(21L))).thenReturn(List.of(worker));

        assertNotNull(service.saveOrderReviews(88L, 3L, 12L,
                new SaveRequest(4, 4, 4, 4, "合作顺利")));

        verify(reviews).save(any(WorkerReview.class));
    }

    @Test
    void statuses_shouldCountReviewableAndReviewedWorkersByOrder() {
        when(items.findByOrderIdIn(java.util.List.of(10L, 11L))).thenReturn(List.of(
                item(1L, 10L, 21L, BossStatus.ITEM_FINISHED),
                item(2L, 10L, null, BossStatus.ITEM_FINISHED),
                item(3L, 11L, 22L, BossStatus.ITEM_ON_WORK)));
        WorkerReview review = new WorkerReview();
        review.setOrderId(10L); review.setWorkerId(21L);
        when(reviews.findByOrderIdIn(java.util.List.of(10L, 11L))).thenReturn(List.of(review));

        var statuses = service.statuses(java.util.List.of(10L, 11L));

        assertEquals(1L, statuses.get(10L).reviewableWorkerCount());
        assertEquals(1L, statuses.get(10L).reviewCount());
        assertEquals(0L, statuses.get(11L).reviewableWorkerCount());
        assertEquals(0L, statuses.get(11L).reviewCount());
    }

    @Test
    void invalidScoresAndLongContent_shouldBeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.saveOrderReviews(99L, 10L, new SaveRequest(0, 5, 5, 5, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.saveOrderReviews(99L, 10L, new SaveRequest(5, 5, 5, 5, "x".repeat(201))));
    }

    private void order(Long id, Long bossId, String status) {
        BossOrder order = new BossOrder();
        order.setId(id); order.setCreateBy(bossId); order.setOrderStatus(status);
        when(orders.findById(id)).thenReturn(Optional.of(order));
    }

    private BaseOrderItem item(Long id, Long orderId, Long workerId, String status) {
        BaseOrderItem item = new BaseOrderItem();
        item.setId(id); item.setOrderId(orderId); item.setUserId(workerId); item.setStatus(status);
        return item;
    }
}
