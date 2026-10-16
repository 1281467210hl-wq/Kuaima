package com.kuaima.app.domain.boss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;

class BossOrderMediaServiceTests {

    @Test
    void createOrder_shouldNormalizeRecruitmentImageKeys() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        when(orders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BossOrder order = validOrder();
        order.setImageKeys(" insurance/recruitment/a.png , insurance/recruitment/b.png ,, ");

        BossOrder saved = new BossOrderService(orders, null, null, null, null)
                .createOrder(order);

        assertEquals("insurance/recruitment/a.png,insurance/recruitment/b.png", saved.getImageKeys());
    }

    @Test
    void createOrder_shouldRejectMediaOutsideRecruitmentDirectory() {
        BossOrder order = validOrder();
        order.setVideoKey("insurance/realname/face.png");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new BossOrderService(mock(BossOrderRespository.class), null, null, null, null)
                        .createOrder(order));

        assertEquals("招工详情视频地址无效", error.getMessage());
    }

    private BossOrder validOrder() {
        BossOrder order = new BossOrder();
        order.setOrderTitle("电子厂普工");
        order.setType("daily");
        order.setPostion("普工");
        order.setOrderNum(3);
        order.setDuration(8);
        order.setSalary(BigDecimal.valueOf(200));
        order.setAddress("上海市浦东新区");
        order.setLongitude(BigDecimal.ONE);
        order.setLatitude(BigDecimal.ONE);
        order.setStartTime(new Date());
        order.setEndTime(new Date(System.currentTimeMillis() + 3600_000));
        return order;
    }
}
