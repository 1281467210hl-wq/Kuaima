package com.kuaima.app.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kuaima.app.admin.dto.PointPurchaseDtos.CreateRequest;
import com.kuaima.app.admin.entity.PointPurchaseOrder;
import com.kuaima.app.admin.repository.PointPurchaseOrderRepository;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.wechat.service.WechatPayService;

class PointPurchaseServiceTests {

    @Test
    void createShouldAllowPurchasingOnePoint() {
        PointPurchaseOrderRepository orders = mock(PointPurchaseOrderRepository.class);
        UserRepository users = mock(UserRepository.class);
        PointPurchaseService service = new PointPurchaseService(orders, users,
                mock(PointsAccountRepository.class), mock(PointsFlowRepository.class));
        User boss = new User();
        boss.setId(7L);
        boss.setEnterpriseStatus("APPROVED");
        when(users.findById(7L)).thenReturn(Optional.of(boss));
        when(orders.findByIdempotencyKey("one-point")).thenReturn(Optional.empty());
        when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new CreateRequest(7L, 1L, "微信支付", "pending", null, "one-point"),
                1L, "admin");

        assertEquals(1L, result.points());
        assertEquals(new BigDecimal("0.01"), result.amount());
    }

    @Test
    void createWechatNativePaymentShouldReturnCodeUrlForPendingWechatOrder() {
        PointPurchaseOrderRepository orders = mock(PointPurchaseOrderRepository.class);
        WechatPayService wechat = mock(WechatPayService.class);
        PointPurchaseService service = new PointPurchaseService(orders, mock(UserRepository.class),
                mock(PointsAccountRepository.class), mock(PointsFlowRepository.class), wechat);
        PointPurchaseOrder order = new PointPurchaseOrder();
        order.setOrderNo("PO202610110001"); order.setStatus("待支付"); order.setPayMethod("微信支付");
        order.setAmount(new BigDecimal("100.00"));
        when(orders.findByOrderNo(order.getOrderNo())).thenReturn(Optional.of(order));
        when(wechat.prepayNative("快马日结积分购买", order.getOrderNo(), order.getAmount()))
                .thenReturn("weixin://wxpay/bizpayurl?pr=test");

        var result = service.createWechatNativePayment(order.getOrderNo());

        assertEquals(order.getOrderNo(), result.get("orderNo"));
        assertEquals("weixin://wxpay/bizpayurl?pr=test", result.get("codeUrl"));
    }
}
