package com.kuaima.app.domain.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.constant.SettlementStatus;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.entity.SettlementPaymentOrder;
import com.kuaima.app.domain.wallet.repository.SettlementPaymentOrderRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.wechat.service.WechatPayService;

class SettlementWechatPaymentTests {
    private SettlementRespository settlements;
    private SettlementPaymentOrderRepository payments;
    private BossOrderRespository orders;
    private BaseOrderItemRespository items;
    private UserRepository users;
    private WalletService wallets;
    private MessageService messages;
    private WechatPayService wechat;
    private PointsAccountRepository pointAccounts;
    private PointsFlowRepository pointFlows;
    private SettlementService service;

    @BeforeEach
    void setUp() {
        settlements = mock(SettlementRespository.class);
        payments = mock(SettlementPaymentOrderRepository.class);
        orders = mock(BossOrderRespository.class);
        items = mock(BaseOrderItemRespository.class);
        users = mock(UserRepository.class);
        wallets = mock(WalletService.class);
        messages = mock(MessageService.class);
        wechat = mock(WechatPayService.class);
        pointAccounts = mock(PointsAccountRepository.class);
        pointFlows = mock(PointsFlowRepository.class);
        service = new SettlementService(settlements, orders, items, wallets, messages, users,
                null, null, payments, wechat);
        when(payments.save(any(SettlementPaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(settlements.save(any(Settlement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createWechatPaymentShouldUseBackendAmountAndReturnJsapiParams() {
        Settlement settlement = settlement(10L, 101L, new BigDecimal("10.50"));
        BossOrder order = order(101L, 7L);
        User boss = new User(); boss.setId(7L); boss.setOpenid("openid-7");
        JSONObject params = new JSONObject(); params.put("package", "prepay_id=wx-test");
        when(settlements.findByIdForUpdate(10L)).thenReturn(Optional.of(settlement));
        when(orders.findById(101L)).thenReturn(Optional.of(order));
        when(users.findById(7L)).thenReturn(Optional.of(boss));
        when(wechat.prepay(eq("快马日结订单结算"), anyString(), eq(new BigDecimal("10.50")), eq("openid-7")))
                .thenReturn(params);

        var result = service.createWechatPayment(7L, List.of(10L), "settle-pay-1");

        assertEquals(new BigDecimal("10.50"), result.amount());
        assertEquals(List.of(10L), result.settlementIds());
        assertEquals("prepay_id=wx-test", result.payParams().get("package"));
        verify(wechat).prepay(eq("快马日结订单结算"), eq(result.paymentNo()),
                eq(new BigDecimal("10.50")), eq("openid-7"));
    }

    @Test
    void createWechatPaymentShouldRejectSettlementOwnedByAnotherBoss() {
        Settlement settlement = settlement(10L, 101L, new BigDecimal("10.50"));
        when(settlements.findByIdForUpdate(10L)).thenReturn(Optional.of(settlement));
        when(orders.findById(101L)).thenReturn(Optional.of(order(101L, 8L)));

        assertThrows(ForbiddenBusinessException.class,
                () -> service.createWechatPayment(7L, List.of(10L), "settle-pay-2"));
        verify(wechat, times(0)).prepay(anyString(), anyString(), any(), anyString());
    }

    @Test
    void fullPointsDeductionShouldCompleteSettlementWithoutWechatPrepay() {
        Settlement settlement = settlement(11L, 102L, new BigDecimal("10.50"));
        settlement.setWorkerId(21L);
        settlement.setWage(new BigDecimal("10.00"));
        settlement.setWorkDays(1);
        BossOrder order = order(102L, 7L);
        PointsAccount account = new PointsAccount();
        account.setUserId(7L);
        account.setRole("BOSS");
        account.setBalance(1100);
        SettlementService pointService = new SettlementService(settlements, orders, items, wallets, messages, users,
                null, null, payments, wechat, pointAccounts, null, null, pointFlows);
        when(settlements.findByIdForUpdate(11L)).thenReturn(Optional.of(settlement));
        when(orders.findById(102L)).thenReturn(Optional.of(order));
        when(pointAccounts.findByUserIdAndRole(7L, "BOSS")).thenReturn(Optional.of(account));
        when(pointAccounts.findByUserIdAndRoleForUpdate(7L, "BOSS")).thenReturn(Optional.of(account));
        when(pointFlows.save(any(PointsFlow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.findById(1L)).thenReturn(Optional.empty());

        var result = pointService.createWechatPayment(7L, List.of(11L), null, 1050, "settle-points-1");

        assertEquals(new BigDecimal("0.00"), result.amount());
        assertEquals(1050, result.pointsUsed());
        assertEquals(new BigDecimal("10.50"), result.pointsDeductAmount());
        assertEquals(SettlementService.PAYMENT_PAID, result.status());
        assertEquals(50, account.getBalance());
        assertEquals(SettlementStatus.PAID, settlement.getStatus());
        verify(wechat, times(0)).prepay(anyString(), anyString(), any(), anyString());
        verify(wallets).credit(21L, new BigDecimal("10.00"), WalletService.BIZ_WAGE, 11L,
                "工资结算 orderId=102 天数=1");
    }

    @Test
    void duplicateWechatCallbackShouldCreditWalletOnlyOnce() {
        Settlement settlement = settlement(10L, 101L, new BigDecimal("10.50"));
        settlement.setWorkerId(20L);
        settlement.setWage(new BigDecimal("10.00"));
        settlement.setWorkDays(1);
        SettlementPaymentOrder payment = new SettlementPaymentOrder();
        payment.setPaymentNo("SP202609220001");
        payment.setSettlementIds("10");
        payment.setAmount(new BigDecimal("10.50"));
        payment.setStatus(SettlementService.PAYMENT_PENDING);
        when(payments.findByPaymentNoForUpdate("SP202609220001")).thenReturn(Optional.of(payment));
        when(settlements.findByIdForUpdate(10L)).thenReturn(Optional.of(settlement));
        when(items.findById(1L)).thenReturn(Optional.empty());
        when(orders.findById(101L)).thenReturn(Optional.of(order(101L, 7L)));

        service.markWechatPaymentPaid("SP202609220001", "wx-transaction-1");
        service.markWechatPaymentPaid("SP202609220001", "wx-transaction-1");

        assertEquals(SettlementStatus.PAID, settlement.getStatus());
        assertEquals("wx-transaction-1", settlement.getWechatTransactionId());
        assertEquals(SettlementService.PAYMENT_PAID, payment.getStatus());
        verify(wallets, times(1)).credit(20L, new BigDecimal("10.00"), WalletService.BIZ_WAGE, 10L,
                "工资结算 orderId=101 天数=1");
    }

    private Settlement settlement(Long id, Long orderId, BigDecimal total) {
        Settlement settlement = new Settlement();
        settlement.setId(id);
        settlement.setItemId(1L);
        settlement.setOrderId(orderId);
        settlement.setTotalAmount(total);
        settlement.setStatus(SettlementStatus.PENDING);
        return settlement;
    }

    private BossOrder order(Long id, Long bossId) {
        BossOrder order = new BossOrder();
        order.setId(id);
        order.setCreateBy(bossId);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        return order;
    }
}
