package com.kuaima.app.domain.review.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.review.entity.BossReview;
import com.kuaima.app.domain.review.repository.BossReviewRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

class BossReviewServiceTests {
    @Test
    void listFromWorkersUsesEnterpriseOwnershipAndWorkerNames() {
        BossReviewRepository reviews = mock(BossReviewRepository.class);
        BaseOrderItemRespository items = mock(BaseOrderItemRespository.class);
        BossOrderRespository orders = mock(BossOrderRespository.class);
        UserRepository users = mock(UserRepository.class);
        BossOrder order = new BossOrder();
        order.setId(10L); order.setCreateBy(100L); order.setEnterpriseId(3L);
        when(orders.findById(10L)).thenReturn(Optional.of(order));
        BossReview review = new BossReview();
        review.setId(1L); review.setItemId(2L); review.setOrderId(10L);
        review.setWorkerId(21L); review.setBossId(100L);
        review.setAttitudeScore(5); review.setSettlementScore(4); review.setEnvironmentScore(5);
        when(reviews.findByOrderIdOrderByIdAsc(10L)).thenReturn(List.of(review));
        User worker = new User(); worker.setId(21L); worker.setNickname("测试零工");
        when(users.findAllById(List.of(21L))).thenReturn(List.of(worker));
        BossReviewService service = new BossReviewService(reviews, items, orders, users);

        var result = service.listFromWorkers(88L, 3L, 10L);

        assertEquals(1, result.size());
        assertEquals("测试零工", result.get(0).workerName());
        assertEquals(5, result.get(0).attitudeScore());
        assertEquals(4, result.get(0).settlementScore());
        assertEquals(5, result.get(0).environmentScore());
    }

    @Test
    void listFromWorkersRejectsOtherEnterprise() {
        BossReviewRepository reviews = mock(BossReviewRepository.class);
        BaseOrderItemRespository items = mock(BaseOrderItemRespository.class);
        BossOrderRespository orders = mock(BossOrderRespository.class);
        UserRepository users = mock(UserRepository.class);
        BossOrder order = new BossOrder();
        order.setId(10L); order.setCreateBy(100L); order.setEnterpriseId(3L);
        when(orders.findById(10L)).thenReturn(Optional.of(order));
        BossReviewService service = new BossReviewService(reviews, items, orders, users);

        assertThrows(ForbiddenBusinessException.class,
                () -> service.listFromWorkers(88L, 4L, 10L));
    }
}
