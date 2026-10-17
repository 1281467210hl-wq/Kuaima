package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.coupon.entity.Coupon;
import com.kuaima.app.domain.coupon.entity.UserCoupon;
import com.kuaima.app.domain.coupon.repository.CouponRepository;
import com.kuaima.app.domain.coupon.repository.UserCouponRepository;
import com.kuaima.app.domain.message.constant.BizType;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.constant.SettlementStatus;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.domain.wallet.service.SettlementService;

class AdminSettlementControllerTests {
    @Test
    void statsShouldCalculateSettlementIndicators() {
        SettlementRespository settlements = mock(SettlementRespository.class);
        when(settlements.countByStatus("待支付")).thenReturn(4L);
        when(settlements.countByStatus("已支付")).thenReturn(95L);
        when(settlements.countByStatus("已取消")).thenReturn(1L);
        when(settlements.count()).thenReturn(100L);
        when(settlements.sumWageByStatusAndPayTimeBetween(any(), any(), any())).thenReturn(864500L);
        AdminSettlementController controller = controller(settlements,
                mock(BossOrderRespository.class), mock(UserRepository.class),
                mock(UserCouponRepository.class), mock(CouponRepository.class));

        var result = controller.stats().getData();

        assertEquals(4L, result.get("pendingCount"));
        assertEquals(new BigDecimal("8645.00"), result.get("settledAmount"));
        assertEquals(95L, result.get("settledCount"));
        assertEquals(new BigDecimal("99.0"), result.get("successRate"));
        assertEquals(100L, result.get("totalCount"));
    }

    @Test
    void listShouldAttachNamesAndCouponDeduction() {
        SettlementRespository settlements = mock(SettlementRespository.class);
        BossOrderRespository orders = mock(BossOrderRespository.class);
        UserRepository users = mock(UserRepository.class);
        UserCouponRepository userCoupons = mock(UserCouponRepository.class);
        CouponRepository coupons = mock(CouponRepository.class);
        Settlement settlement = new Settlement();
        settlement.setId(12L); settlement.setOrderId(30L); settlement.setWorkerId(20L);
        settlement.setWage(30000L); settlement.setServiceFee(3000L); settlement.setTotalAmount(33000L);
        settlement.setStatus("待支付");
        settlement.setPayTime(LocalDateTime.of(2026, 10, 9, 10, 30));
        BossOrder order = new BossOrder(); order.setId(30L); order.setCreateBy(10L);
        order.setOrderTitle("电子厂普工"); order.setSalary(200); order.setDuration(3); order.setOrderNum(2);
        User employer = new User(); employer.setId(10L); employer.setCompanyName("快马科技");
        User worker = new User(); worker.setId(20L); worker.setNickname("张师傅");
        UserCoupon userCoupon = new UserCoupon(); userCoupon.setId(40L);
        userCoupon.setCouponId(41L); userCoupon.setUseOrderId(30L);
        Coupon coupon = new Coupon(); coupon.setId(41L); coupon.setTitle("招工满减券");
        coupon.setType("FULL"); coupon.setAmount(new BigDecimal("50"));
        when(settlements.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(settlement), PageRequest.of(0, 10), 1));
        when(orders.findAllById(any())).thenReturn(List.of(order));
        when(users.findAllById(any())).thenReturn(List.of(employer, worker));
        when(userCoupons.findByUseOrderIdIn(any())).thenReturn(List.of(userCoupon));
        when(coupons.findAllById(any())).thenReturn(List.of(coupon));
        AdminSettlementController controller = controller(settlements, orders, users, userCoupons, coupons);

        var row = controller.list("待结算", null, null, null, 0, 10)
                .getData().getContent().get(0);

        assertEquals("快马科技", row.getString("employerName"));
        assertEquals("张师傅", row.getString("workerName"));
        assertEquals("待结算", row.getString("status"));
        assertEquals("2026-10-09T10:30", row.getString("time"));
        assertEquals(new BigDecimal("50.00"), row.getBigDecimal("couponAmount"));
    }

    @Test
    void batchPayShouldSettleSelectedIdsAndCollectFailures() {
        SettlementService service = mock(SettlementService.class);
        Settlement paid = new Settlement();
        paid.setId(1L);
        paid.setStatus("已支付");
        when(service.mockPay(1L)).thenReturn(paid);
        when(service.mockPay(2L)).thenThrow(new IllegalStateException("仅待支付的结算单可以支付"));
        AdminSettlementController controller = controller(service);

        var data = controller.batchPay(new AdminSettlementController.BatchPayRequest(List.of(1L, 2L, 1L)))
                .getData();

        assertEquals(2, data.get("total"));
        assertEquals(1, data.get("successCount"));
        assertEquals(1, data.get("failedCount"));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> failures = (List<java.util.Map<String, Object>>) data.get("failures");
        assertEquals(2L, failures.get(0).get("id"));
        assertEquals("仅待支付的结算单可以支付", failures.get(0).get("reason"));
    }

    @Test
    void remind_shouldSendSettleReminderToOrderBoss() {
        SettlementRespository settlements = mock(SettlementRespository.class);
        BossOrderRespository orders = mock(BossOrderRespository.class);
        UserRepository users = mock(UserRepository.class);
        MessageService messages = mock(MessageService.class);
        Settlement settlement = pendingSettlement(SettlementStatus.PENDING);
        BossOrder order = new BossOrder();
        order.setId(62L);
        order.setCreateBy(7L);
        order.setOrderTitle("电子厂普工");
        User worker = new User();
        worker.setNickname("测试零工");
        when(settlements.findById(5L)).thenReturn(Optional.of(settlement));
        when(orders.findById(62L)).thenReturn(Optional.of(order));
        when(users.findById(66L)).thenReturn(Optional.of(worker));

        new AdminSettlementController(settlements, mock(SettlementService.class), users, orders,
                mock(UserCouponRepository.class), mock(CouponRepository.class), messages).remind(5L);

        verify(messages).sendToUser(eq(7L), eq(UserRole.BOSS), eq(MessageType.SETTLE_REMIND), eq("结算提醒"),
                eq("您发布的「电子厂普工」岗位，零工 测试零工 的工资尚未结算，需支付 294.00 元，请尽快完成结算。"),
                eq(BizType.SETTLE), eq(5L));
    }

    @Test
    void remind_shouldRejectSettledSettlement() {
        SettlementRespository settlements = mock(SettlementRespository.class);
        MessageService messages = mock(MessageService.class);
        when(settlements.findById(5L)).thenReturn(Optional.of(pendingSettlement(SettlementStatus.PAID)));

        AdminSettlementController controller = new AdminSettlementController(settlements, mock(SettlementService.class),
                mock(UserRepository.class), mock(BossOrderRespository.class),
                mock(UserCouponRepository.class), mock(CouponRepository.class), messages);

        assertThrows(IllegalStateException.class, () -> controller.remind(5L));
        verify(messages, never()).sendToUser(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private Settlement pendingSettlement(String status) {
        Settlement settlement = new Settlement();
        settlement.setId(5L);
        settlement.setOrderId(62L);
        settlement.setWorkerId(66L);
        settlement.setTotalAmount(new BigDecimal("294"));
        settlement.setStatus(status);
        return settlement;
    }

    private AdminSettlementController controller(SettlementRespository settlements,
                                                 BossOrderRespository orders,
                                                 UserRepository users,
                                                 UserCouponRepository userCoupons,
                                                 CouponRepository coupons) {
        return new AdminSettlementController(settlements, mock(SettlementService.class),
                users, orders, userCoupons, coupons);
    }

    private AdminSettlementController controller(SettlementService service) {
        return new AdminSettlementController(mock(SettlementRespository.class), service,
                mock(UserRepository.class), mock(BossOrderRespository.class),
                mock(UserCouponRepository.class), mock(CouponRepository.class));
    }
}
