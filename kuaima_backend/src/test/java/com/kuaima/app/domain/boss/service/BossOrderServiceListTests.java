package com.kuaima.app.domain.boss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.entity.BossRecruitSettings;
import com.kuaima.app.domain.boss.model.BossOrderQuery;
import com.kuaima.app.domain.boss.repository.BossAddressRepository;
import com.kuaima.app.domain.boss.repository.BossRecruitSettingsRepository;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.service.CertificationService;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.constant.SettlementStatus;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.admin.repository.AdminSettingRepository;

class BossOrderServiceListTests {

    private BossOrderRespository orderRepository;
    private BaseOrderItemRespository itemRepository;
    private SettlementRespository settlementRepository;
    private BossOrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(BossOrderRespository.class);
        itemRepository = mock(BaseOrderItemRespository.class);
        settlementRepository = mock(SettlementRespository.class);
        service = new BossOrderService(
                orderRepository,
                itemRepository,
                mock(UserRepository.class),
                mock(MessageService.class),
                settlementRepository);
    }

    @Test
    void bossList_shouldFilterByOwnerAndFillActiveApplicantCount() {
        BossOrder order = new BossOrder();
        order.setId(11L);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(itemRepository.countByOrderIdsAndStatuses(eq(List.of(11L)), any()))
                .thenReturn(List.<Object[]>of(new Object[] {11L, 3L}));

        var result = service.listOrders(2L, null, "招工中", null, 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals(3L, result.getContent().get(0).getCurrentApply());
        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(itemRepository).countByOrderIdsAndStatuses(
                eq(List.of(11L)),
                eq(List.of(
                        BossStatus.ITEM_APPLIED,
                        BossStatus.ITEM_HIRED,
                        BossStatus.ITEM_ON_WORK,
                        BossStatus.ITEM_PENDING_SETTLE,
                        BossStatus.ITEM_FINISHED)));
    }

    @Test
    void publicList_shouldKeepExistingGlobalQueryAndReturnZeroWhenNoApplicants() {
        BossOrder order = new BossOrder();
        order.setId(12L);
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(itemRepository.countByOrderIdsAndStatuses(eq(List.of(12L)), any()))
                .thenReturn(List.of());

        var result = service.listOrders(null, null, null, null, 0, 20);

        assertEquals(0L, result.getContent().get(0).getCurrentApply());
        verify(orderRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void list_shouldLimitPageSizeToOneHundred() {
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        BossOrderQuery query = new BossOrderQuery();
        query.setPage(-1);
        query.setSize(500);

        service.listOrders(2L, query);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void list_shouldRejectInvalidRangeAndDistanceParameters() {
        BossOrderQuery salaryQuery = new BossOrderQuery();
        salaryQuery.setSalaryMin(200);
        salaryQuery.setSalaryMax(100);
        assertThrows(IllegalArgumentException.class, () -> service.listOrders(2L, salaryQuery));

        BossOrderQuery distanceQuery = new BossOrderQuery();
        distanceQuery.setDistanceKm(5D);
        assertThrows(IllegalArgumentException.class, () -> service.listOrders(2L, distanceQuery));

        BossOrderQuery tagModeQuery = new BossOrderQuery();
        tagModeQuery.setTags("包吃住,日结");
        tagModeQuery.setTagMode("INVALID");
        assertThrows(IllegalArgumentException.class, () -> service.listOrders(2L, tagModeQuery));
    }

    @Test
    void hire_shouldAutomaticallyEndRecruitmentWhenHeadcountIsFull() {
        BossOrder order = new BossOrder();
        order.setId(21L);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order.setOrderNum(1);
        BaseOrderItem item = new BaseOrderItem();
        item.setId(31L);
        item.setOrderId(21L);
        item.setUserId(41L);
        item.setStatus(BossStatus.ITEM_APPLIED);
        when(itemRepository.findById(31L)).thenReturn(java.util.Optional.of(item));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByOrderId(21L)).thenReturn(List.of(item));
        when(orderRepository.findById(21L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.hireItem(31L);

        assertEquals(BossStatus.ORDER_RECRUIT_END, order.getOrderStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void confirmWork_shouldMoveOrderToWorking() {
        BossOrder order = new BossOrder();
        order.setId(22L);
        order.setCreateBy(51L);
        order.setOrderStatus(BossStatus.ORDER_RECRUIT_END);
        order.setSalary(200);
        BaseOrderItem arriving = new BaseOrderItem();
        arriving.setId(32L);
        arriving.setOrderId(22L);
        arriving.setUserId(42L);
        arriving.setStatus(BossStatus.ITEM_HIRED);
        BaseOrderItem arrived = new BaseOrderItem();
        arrived.setId(33L);
        arrived.setOrderId(22L);
        arrived.setStatus(BossStatus.ITEM_ON_WORK);
        when(itemRepository.findById(32L)).thenReturn(java.util.Optional.of(arriving));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByOrderId(22L)).thenReturn(List.of(arriving, arrived));
        when(orderRepository.findById(22L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlementRepository.existsByItemIdAndStatusIn(eq(32L), any())).thenReturn(false);
        when(settlementRepository.existsByItemIdAndStatusIn(eq(33L), any())).thenReturn(false);

        service.confirmWork(32L);

        assertEquals(BossStatus.ORDER_WORKING, order.getOrderStatus());
        verify(orderRepository).save(order);
        verify(settlementRepository, times(0)).save(any(Settlement.class));
    }

    @Test
    void finishItem_shouldCreatePendingSettlementWhenOrderAlreadyPendingSettlement() {
        BossOrder order = new BossOrder();
        order.setId(26L);
        order.setOrderStatus(BossStatus.ORDER_PENDING_SETTLE);
        order.setSalary(200);
        BaseOrderItem item = new BaseOrderItem();
        item.setId(36L);
        item.setOrderId(26L);
        item.setUserId(46L);
        item.setStatus(BossStatus.ITEM_ON_WORK);
        item.setWorkDate(Date.valueOf(java.time.LocalDate.now()));
        when(itemRepository.findById(36L)).thenReturn(java.util.Optional.of(item));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByOrderId(26L)).thenReturn(List.of(item));
        when(orderRepository.findById(26L)).thenReturn(java.util.Optional.of(order));
        when(settlementRepository.existsByItemIdAndStatusIn(eq(36L), any())).thenReturn(false);
        when(settlementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseOrderItem finished = service.finishItem(36L);

        assertEquals(BossStatus.ITEM_PENDING_SETTLE, finished.getStatus());
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());
        Settlement settlement = captor.getValue();
        assertEquals(36L, settlement.getItemId());
        assertEquals(26L, settlement.getOrderId());
        assertEquals(46L, settlement.getWorkerId());
        assertEquals(1, settlement.getWorkDays());
        assertEquals(new java.math.BigDecimal("200.00"), settlement.getTotalAmount());
        assertEquals(SettlementStatus.PENDING, settlement.getStatus());
    }

    @Test
    void updateOrder_shouldKeepOrderWorkingWhenAllActiveApplicantsArrived() {
        BossOrder order = new BossOrder(); order.setId(39L); order.setOrderStatus(BossStatus.ORDER_RECRUITING); order.setOrderNum(3); order.setSalary(200);
        BaseOrderItem finished = new BaseOrderItem(); finished.setId(390L); finished.setOrderId(39L); finished.setUserId(99L); finished.setStatus(BossStatus.ITEM_FINISHED); finished.setWorkDate(Date.valueOf(java.time.LocalDate.now())); finished.setFinishDate(Date.valueOf(java.time.LocalDate.now()));
        BossOrder update = new BossOrder(); update.setOrderNum(1);
        when(orderRepository.findById(39L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByOrderId(39L)).thenReturn(List.of(finished));
        when(settlementRepository.existsByItemIdAndStatusIn(eq(390L), any())).thenReturn(false);
        when(settlementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BossOrder result = service.updateOrder(39L, update);

        assertEquals(BossStatus.ORDER_WORKING, result.getOrderStatus());
        verify(settlementRepository, times(0)).save(any(Settlement.class));
    }

    @Test
    void changeOrderStatus_shouldMarkHiredWorkersFinishedAndCreateSettlements() {
        BossOrder order = new BossOrder();
        order.setId(26L);
        order.setOrderStatus(BossStatus.ORDER_RECRUIT_END);
        order.setSalary(200);
        BaseOrderItem hired = new BaseOrderItem();
        hired.setId(37L);
        hired.setOrderId(26L);
        hired.setUserId(47L);
        hired.setStatus(BossStatus.ITEM_HIRED);
        when(orderRepository.findById(26L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByOrderId(26L)).thenReturn(List.of(hired));
        when(settlementRepository.existsByItemIdAndStatusIn(eq(37L), any())).thenReturn(false);
        when(settlementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BossOrder result = service.changeOrderStatus(26L, BossStatus.ORDER_PENDING_SETTLE);

        assertEquals(BossStatus.ORDER_PENDING_SETTLE, result.getOrderStatus());
        assertEquals(BossStatus.ITEM_PENDING_SETTLE, hired.getStatus());
        assertEquals(Date.valueOf(java.time.LocalDate.now()), hired.getWorkDate());
        verify(itemRepository, times(2)).saveAll(List.of(hired));
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());
        assertEquals(37L, captor.getValue().getItemId());
        assertEquals(SettlementStatus.PENDING, captor.getValue().getStatus());
        verify(itemRepository, times(3)).findByOrderId(26L);
    }

    @Test
    void manualFinish_shouldRejectWhenEarlyLeaveCodeEnabled() {
        BossRecruitSettingsRepository recruitSettingsRepository = mock(BossRecruitSettingsRepository.class);
        BossOrderService guardedService = guardedService(recruitSettingsRepository);
        BossOrder order = new BossOrder();
        order.setId(28L);
        order.setCreateBy(58L);
        order.setOrderStatus(BossStatus.ORDER_WORKING);
        BaseOrderItem item = new BaseOrderItem();
        item.setId(38L);
        item.setOrderId(28L);
        item.setStatus(BossStatus.ITEM_ON_WORK);
        when(orderRepository.findById(28L)).thenReturn(java.util.Optional.of(order));
        when(itemRepository.findById(38L)).thenReturn(java.util.Optional.of(item));
        when(recruitSettingsRepository.findByBossId(58L))
                .thenReturn(java.util.Optional.of(attendanceSettings(false, true)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> guardedService.finishItemManually(38L));

        assertEquals("已开启早退码，请由零工输入早退码后完成工作", error.getMessage());
        verify(itemRepository, times(0)).save(any());
    }

    @Test
    void workingOrderCompletion_shouldRejectWhenEarlyLeaveCodeEnabled() {
        BossRecruitSettingsRepository recruitSettingsRepository = mock(BossRecruitSettingsRepository.class);
        BossOrderService guardedService = guardedService(recruitSettingsRepository);
        BossOrder order = new BossOrder();
        order.setId(29L);
        order.setCreateBy(59L);
        order.setOrderStatus(BossStatus.ORDER_WORKING);
        when(orderRepository.findById(29L)).thenReturn(java.util.Optional.of(order));
        when(recruitSettingsRepository.findByBossId(59L))
                .thenReturn(java.util.Optional.of(attendanceSettings(false, true)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> guardedService.changeOrderStatus(29L, BossStatus.ORDER_PENDING_SETTLE));

        assertEquals("已开启早退码，请由零工输入早退码后完成工作", error.getMessage());
        verify(orderRepository, times(0)).save(any());
    }

    private BossOrderService guardedService(BossRecruitSettingsRepository recruitSettingsRepository) {
        return new BossOrderService(
                orderRepository,
                itemRepository,
                mock(UserRepository.class),
                mock(MessageService.class),
                settlementRepository,
                mock(CertificationService.class),
                recruitSettingsRepository,
                mock(com.kuaima.app.domain.coupon.service.BossCouponService.class),
                mock(AdminSettingRepository.class),
                mock(BossAddressRepository.class));
    }

    private BossRecruitSettings attendanceSettings(boolean startCode, boolean earlyCode) {
        BossRecruitSettings settings = new BossRecruitSettings();
        settings.setStartCodeEnabled(startCode);
        settings.setEarlyCodeEnabled(earlyCode);
        return settings;
    }

    @Test
    void pendingAuditOrder_shouldBeCancelable() {
        BossOrder order = new BossOrder();
        order.setId(51L);
        order.setOrderStatus(BossStatus.ORDER_PENDING_AUDIT);
        BaseOrderItem applied = new BaseOrderItem();
        applied.setId(511L);
        applied.setOrderId(51L);
        applied.setUserId(61L);
        applied.setStatus(BossStatus.ITEM_APPLIED);
        when(orderRepository.findById(51L)).thenReturn(java.util.Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemRepository.findByOrderId(51L)).thenReturn(List.of(applied));

        BossOrder result = service.changeOrderStatus(51L, BossStatus.ORDER_CANCELED);

        assertEquals(BossStatus.ORDER_CANCELED, result.getOrderStatus());
        assertEquals(BossStatus.ITEM_CANCEL_BY_BOSS, applied.getStatus());
        verify(itemRepository).saveAll(List.of(applied));
        verify(orderRepository).save(order);
    }

}
