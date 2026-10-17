package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;

class AdminDashboardControllerTests {
    @Test
    void trendShouldUseSelectedMonthAndExcludeEarlierOrders() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        Calendar now = Calendar.getInstance();
        Calendar today = (Calendar) now.clone();
        today.set(Calendar.HOUR_OF_DAY, 10);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Calendar earlier = (Calendar) today.clone();
        earlier.set(Calendar.DAY_OF_MONTH, 1);
        earlier.add(Calendar.MILLISECOND, -1);
        when(orders.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(order(today.getTime()), order(earlier.getTime())));

        var data = controller(orders).trend("month").getData();
        List<?> labels = (List<?>) data.get("labels");
        List<?> values = (List<?>) data.get("values");

        int expectedDays = today.getActualMaximum(Calendar.DAY_OF_MONTH);
        assertEquals(expectedDays, labels.size());
        assertEquals(expectedDays, values.size());
        assertEquals(1L, values.get(today.get(Calendar.DAY_OF_MONTH) - 1));
    }

    @Test
    void trendShouldRejectUnknownRange() {
        assertThrows(IllegalArgumentException.class,
                () -> controller(mock(BossOrderRespository.class)).trend("90d"));
    }

    private BossOrder order(java.util.Date time) {
        BossOrder order = new BossOrder();
        order.setTimestamp(new Timestamp(time.getTime()));
        return order;
    }

    private AdminDashboardController controller(BossOrderRespository orders) {
        return new AdminDashboardController(mock(UserRepository.class), orders,
                mock(SettlementRespository.class), mock(BaseOrderItemRespository.class));
    }
}
