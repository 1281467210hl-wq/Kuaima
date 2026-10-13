package com.kuaima.app.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.model.WorkerOrderModels.WorkerOrder;
import com.kuaima.app.domain.user.model.WorkerProfileModels.UpdateWorkerProfileRequest;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.review.repository.BossReviewRepository;
import com.kuaima.app.domain.review.entity.BossReview;
import java.time.LocalDateTime;

class WorkerProfileServiceTests {

    private UserRepository userRepository;
    private BaseOrderItemRespository itemRepository;
    private BossOrderRespository orderRepository;
    private WorkerProfileService service;
    private BossReviewRepository reviewRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        itemRepository = mock(BaseOrderItemRespository.class);
        orderRepository = mock(BossOrderRespository.class);
        reviewRepository = mock(BossReviewRepository.class);
        service = new WorkerProfileService(userRepository, itemRepository, orderRepository, reviewRepository);
        when(reviewRepository.findByItemIdIn(any())).thenReturn(List.of());
    }

    @Test
    void updateProfile_shouldPersistPrototypeFieldsAndKeepPhoneReadOnly() {
        User user = new User();
        user.setId(30L);
        user.setPhone("13800005678");
        when(userRepository.findById(30L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateWorkerProfileRequest request = new UpdateWorkerProfileRequest(
                "avatar.png", "海绵宝宝", "女", LocalDate.of(1990, 1, 1),
                "上海市浦东新区", "分拣打包,搬运装卸", 3, true, "介绍一下自己");

        var result = service.updateProfile(30L, request);

        assertEquals("海绵宝宝", result.nickname());
        assertEquals("13800005678", result.phone());
        assertEquals(LocalDate.of(1990, 1, 1), result.birthday());
        assertEquals(3, result.workYears());
        assertEquals(true, result.acceptNightShift());
        assertEquals("介绍一下自己", result.introduction());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_shouldRejectFutureBirthdayAndInvalidWorkYears() {
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(30L,
                new UpdateWorkerProfileRequest(null, null, null, LocalDate.now().plusDays(1),
                        null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(30L,
                new UpdateWorkerProfileRequest(null, null, null, null,
                        null, null, 81, null, null)));
    }

    @Test
    void listOrders_groupStatus_working_shouldExpandToFourUnderlyingStatuses() {
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        Page<BaseOrderItem> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemRepository.findWorkerOrders(eq(1L), eq(null), captor.capture(), any(Pageable.class)))
                .thenReturn(empty);

        service.listOrders(1L, null, BossStatus.GROUP_WORKING, PageRequest.of(0, 20));

        Collection<String> actual = captor.getValue();
        assertEquals(4, actual.size());
        assertTrue(actual.containsAll(Arrays.asList(
                BossStatus.ITEM_APPLIED, BossStatus.ITEM_HIRED,
                BossStatus.ITEM_ON_WORK, BossStatus.ITEM_PENDING_SETTLE)));
    }

    @Test
    void listOrders_groupStatus_canceled_shouldExpandToThreeCancelKinds() {
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        Page<BaseOrderItem> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemRepository.findWorkerOrders(eq(1L), eq(null), captor.capture(), any(Pageable.class)))
                .thenReturn(empty);

        service.listOrders(1L, null, BossStatus.GROUP_CANCELED, PageRequest.of(0, 20));

        Collection<String> actual = captor.getValue();
        assertEquals(3, actual.size());
        assertTrue(actual.containsAll(Arrays.asList(
                BossStatus.ITEM_CANCELED, BossStatus.ITEM_REJECTED, BossStatus.ITEM_CANCEL_BY_BOSS)));
    }

    @Test
    void listOrders_statusText_shouldMapDisplayCorrectly() {
        BaseOrderItem onWork = new BaseOrderItem();
        onWork.setId(101L);
        onWork.setOrderId(9001L);
        onWork.setUserId(1L);
        onWork.setStatus(BossStatus.ITEM_ON_WORK);
        onWork.setApplyDate(Date.valueOf(LocalDate.of(2026, 9, 10)));
        onWork.setHireDate(Date.valueOf(LocalDate.of(2026, 9, 10)));
        onWork.setWorkDate(Date.valueOf(LocalDate.of(2026, 9, 10)));

        BaseOrderItem cancelByBoss = new BaseOrderItem();
        cancelByBoss.setId(102L);
        cancelByBoss.setOrderId(9002L);
        cancelByBoss.setUserId(1L);
        cancelByBoss.setStatus(BossStatus.ITEM_CANCEL_BY_BOSS);
        cancelByBoss.setApplyDate(Date.valueOf(LocalDate.of(2026, 9, 8)));

        BaseOrderItem finished = new BaseOrderItem();
        finished.setId(103L);
        finished.setOrderId(9003L);
        finished.setUserId(1L);
        finished.setStatus(BossStatus.ITEM_FINISHED);
        finished.setApplyDate(Date.valueOf(LocalDate.of(2026, 9, 1)));
        finished.setFinishDate(Date.valueOf(LocalDate.of(2026, 9, 1)));

        BossOrder order1 = new BossOrder();
        order1.setId(9001L);
        order1.setOrderTitle("分拣员1");
        order1.setType("daily");
        order1.setSalary(300);
        order1.setAddress("上海A");
        order1.setLongitude(new java.math.BigDecimal("121.47"));
        order1.setLatitude(new java.math.BigDecimal("31.23"));
        order1.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order1.setCreateBy(2L);
        BossOrder order2 = new BossOrder();
        order2.setId(9002L);
        order2.setOrderTitle("分拣员2");
        order2.setType("daily");
        order2.setSalary(280);
        order2.setAddress("上海B");
        order2.setOrderStatus(BossStatus.ORDER_CANCELED);
        order2.setCreateBy(3L);
        BossOrder order3 = new BossOrder();
        order3.setId(9003L);
        order3.setOrderTitle("搬运工");
        order3.setType("month");
        order3.setSalary(8000);
        order3.setAddress("上海C");
        order3.setOrderStatus(BossStatus.ORDER_COMPLETED);
        order3.setCreateBy(2L);

        User boss2 = new User();
        boss2.setId(2L);
        boss2.setCompanyName("快马一公司");
        User boss3 = new User();
        boss3.setId(3L);
        boss3.setCompanyName("快马二公司");

        PageRequest pageable = PageRequest.of(0, 20);
        Page<BaseOrderItem> page = new PageImpl<>(List.of(onWork, cancelByBoss, finished), pageable, 3);
        when(itemRepository.findWorkerOrders(eq(1L), eq(null), any(), eq(pageable))).thenReturn(page);
        when(orderRepository.findAllById(any())).thenReturn(List.of(order1, order2, order3));
        when(userRepository.findAllById(any())).thenReturn(List.of(boss2, boss3));
        BossReview review = new BossReview(); review.setItemId(103L); review.setAttitudeScore(5);
        review.setSettlementScore(4); review.setEnvironmentScore(5); review.setContent("很好");
        review.setUpdatedAt(LocalDateTime.of(2026, 9, 17, 12, 0));
        when(reviewRepository.findByItemIdIn(any())).thenReturn(List.of(review));

        Page<WorkerOrder> result = service.listOrders(1L, null, null, pageable);

        List<WorkerOrder> list = result.getContent();
        assertEquals(3, list.size());
        assertEquals(BossStatus.DISPLAY_ON_WORK, list.get(0).status()); // 已到岗 → 工作中
        assertEquals(BossStatus.GROUP_CANCELED, list.get(1).status()); // 取消招工 → 已取消
        assertEquals(BossStatus.ITEM_FINISHED, list.get(2).status()); // 已完成 → 不变
        assertEquals(false, list.get(0).reviewed());
        assertEquals(true, list.get(2).reviewed());
        assertEquals(5, list.get(2).bossReview().attitudeScore());
        assertEquals(new java.math.BigDecimal("121.47"), list.get(0).longitude());
        assertEquals(new java.math.BigDecimal("31.23"), list.get(0).latitude());
    }

    @Test
    void getOrder_shouldReturnStoppedJobSnapshotForOwner() {
        BaseOrderItem item = new BaseOrderItem();
        item.setId(201L);
        item.setOrderId(9100L);
        item.setUserId(1L);
        item.setStatus(BossStatus.ITEM_FINISHED);
        item.setFinishDate(Date.valueOf(LocalDate.of(2026, 10, 12)));
        BossOrder order = new BossOrder();
        order.setId(9100L);
        order.setOrderTitle("已停止招工的分拣岗");
        order.setType("daily");
        order.setSalary(320);
        order.setAddress("上海市浦东新区");
        order.setOrderStatus(BossStatus.ORDER_CANCELED);
        order.setCreateBy(2L);
        User boss = new User();
        boss.setId(2L);
        boss.setCompanyName("快马一公司");
        when(itemRepository.findById(201L)).thenReturn(Optional.of(item));
        when(orderRepository.findById(9100L)).thenReturn(Optional.of(order));
        when(userRepository.findById(2L)).thenReturn(Optional.of(boss));
        when(reviewRepository.findByItemId(201L)).thenReturn(Optional.empty());

        WorkerOrder result = service.getOrder(1L, 201L);

        assertEquals(201L, result.id());
        assertEquals(9100L, result.orderId());
        assertEquals("已停止招工的分拣岗", result.orderTitle());
        assertEquals(BossStatus.ORDER_CANCELED, result.orderStatus());
        assertEquals("快马一公司", result.companyName());
    }

    @Test
    void getOrder_shouldRejectOtherWorkersItem() {
        BaseOrderItem item = new BaseOrderItem();
        item.setId(202L);
        item.setUserId(1L);
        when(itemRepository.findById(202L)).thenReturn(Optional.of(item));

        assertThrows(com.kuaima.app.common.ForbiddenBusinessException.class,
                () -> service.getOrder(2L, 202L));
    }
}
