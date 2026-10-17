package com.kuaima.app.controller.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.kuaima.app.admin.entity.PointPurchaseOrder;
import com.kuaima.app.admin.repository.PointPurchaseOrderRepository;
import com.kuaima.app.domain.boss.entity.BossBalanceRechargeOrder;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BossBalanceRechargeOrderRepository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.entity.SettlementPaymentOrder;
import com.kuaima.app.domain.wallet.repository.SettlementPaymentOrderRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import com.kuaima.app.security.model.LoginUser;

class BossExpenseDetailControllerTests {
    @Test
    void listShouldAggregateThreeExpenseTypesAndSummary() {
        PointPurchaseOrderRepository pointOrders = mock(PointPurchaseOrderRepository.class);
        SettlementPaymentOrderRepository settlementPayments = mock(SettlementPaymentOrderRepository.class);
        SettlementRespository settlements = mock(SettlementRespository.class);
        BossBalanceRechargeOrderRepository rechargeOrders = mock(BossBalanceRechargeOrderRepository.class);
        BossOrderRespository orders = mock(BossOrderRespository.class);
        BossExpenseDetailController controller = new BossExpenseDetailController(
                pointOrders, settlementPayments, settlements, rechargeOrders, orders);

        PointPurchaseOrder pointOrder = new PointPurchaseOrder();
        pointOrder.setId(1L);
        pointOrder.setPoints(1000L);
        pointOrder.setAmount(new BigDecimal("100.00"));
        pointOrder.setPayMethod("支付宝支付");
        pointOrder.setStatus("支付成功");
        pointOrder.setPurchaseTime(LocalDateTime.now().minusDays(1));
        when(pointOrders.findByBossIdOrderByPurchaseTimeDescIdDesc(7L)).thenReturn(List.of(pointOrder));
        when(settlementPayments.findByBossIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of());
        when(rechargeOrders.findByBossIdOrderByIdDesc(7L)).thenReturn(List.of(recharge(new BigDecimal("300.00"))));

        var data = controller.list(bossAuthentication()).getData();

        assertEquals(new BigDecimal("400.00"), data.get("total"));
        assertEquals(new BigDecimal("100.00"), data.get("points"));
        assertEquals(BigDecimal.ZERO, data.get("settle"));
        assertEquals(new BigDecimal("300.00"), data.get("recharge"));
        assertEquals(2, ((List<?>) data.get("records")).size());
    }

    @Test
    void settlementRecordShouldUseOrderSnapshotAndPaymentStatus() {
        PointPurchaseOrderRepository pointOrders = mock(PointPurchaseOrderRepository.class);
        SettlementPaymentOrderRepository settlementPayments = mock(SettlementPaymentOrderRepository.class);
        SettlementRespository settlements = mock(SettlementRespository.class);
        BossBalanceRechargeOrderRepository rechargeOrders = mock(BossBalanceRechargeOrderRepository.class);
        BossOrderRespository orders = mock(BossOrderRespository.class);
        BossExpenseDetailController controller = new BossExpenseDetailController(
                pointOrders, settlementPayments, settlements, rechargeOrders, orders);

        Settlement settlement = new Settlement();
        settlement.setId(11L);
        settlement.setOrderId(12L);
        SettlementPaymentOrder payment = new SettlementPaymentOrder();
        payment.setId(13L);
        payment.setBossId(7L);
        payment.setSettlementIds("11");
        payment.setAmount(new BigDecimal("1440.00"));
        payment.setStatus("PENDING");
        payment.setCreatedAt(LocalDateTime.now().minusHours(2));
        BossOrder order = new BossOrder();
        order.setId(12L);
        order.setOrderNo("DD202609100001");
        order.setOrderTitle("仓库装卸工");
        when(pointOrders.findByBossIdOrderByPurchaseTimeDescIdDesc(7L)).thenReturn(List.of());
        when(settlementPayments.findByBossIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(List.of(payment));
        when(settlements.findAllById(any())).thenReturn(List.of(settlement));
        when(orders.findAllById(any())).thenReturn(List.of(order));
        when(rechargeOrders.findByBossIdOrderByIdDesc(7L)).thenReturn(List.of());

        var records = (List<?>) controller.list(bossAuthentication()).getData().get("records");
        var record = (java.util.Map<?, ?>) records.get(0);

        assertEquals("settle", record.get("type"));
        assertEquals("招工订单结算 · 仓库装卸工", record.get("title"));
        assertEquals("processing", record.get("status"));
        assertEquals("结算中", record.get("statusText"));
        assertEquals(List.of("1人", "DD202609100001"), record.get("tags"));
    }

    private BossBalanceRechargeOrder recharge(BigDecimal amount) {
        BossBalanceRechargeOrder order = new BossBalanceRechargeOrder();
        order.setId(2L);
        order.setAmount(amount);
        order.setPayMethod("alipay");
        order.setStatus("paid");
        order.setTimestamp(Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
        return order;
    }

    private Authentication bossAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                new LoginUser(7L, "boss", UserRole.BOSS), null);
    }
}
