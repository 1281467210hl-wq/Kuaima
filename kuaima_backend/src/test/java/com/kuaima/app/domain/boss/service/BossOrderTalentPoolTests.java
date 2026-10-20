package com.kuaima.app.domain.boss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.talentpool.entity.TalentPool;
import com.kuaima.app.domain.talentpool.repository.TalentPoolRepository;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;

class BossOrderTalentPoolTests {

    @Test
    void applyOrder_shouldAddApplicantToPublishingBossTalentPool() {
        BossOrderRespository orders = mock(BossOrderRespository.class);
        BaseOrderItemRespository items = mock(BaseOrderItemRespository.class);
        UserRepository users = mock(UserRepository.class);
        TalentPoolRepository talents = mock(TalentPoolRepository.class);
        BossOrderService service = new BossOrderService(orders, items, users,
                mock(MessageService.class), mock(SettlementRespository.class));
        service.setTalentPoolRepository(talents);

        BossOrder order = new BossOrder();
        order.setId(10L);
        order.setCreateBy(20L);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order.setOrderNum(2);
        order.setOrderTitle("仓库招工");
        order.setPostion("分拣员");
        order.setSignMode("manual");
        order.setSignNotify(false);

        User worker = new User();
        worker.setId(30L);
        worker.setNickname("测试零工");
        worker.setPhone("13812345678");
        worker.setSkills("分拣,打包");
        worker.setRealnameStatus(CertificationStatus.APPROVED);
        worker.setRealnameIdCardImageUrl("insurance/realname/id.jpg");
        worker.setRealnameFaceImageUrl("insurance/realname/face.jpg");

        when(orders.findById(10L)).thenReturn(Optional.of(order));
        when(users.findById(30L)).thenReturn(Optional.of(worker));
        when(items.existsByOrderIdAndUserId(10L, 30L)).thenReturn(false);
        when(items.findByOrderId(10L)).thenReturn(List.of());
        when(items.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(talents.findByBossIdAndWorkerId(20L, 30L)).thenReturn(Optional.empty());

        service.applyOrder(10L, 30L, null, false);

        ArgumentCaptor<TalentPool> captor = ArgumentCaptor.forClass(TalentPool.class);
        verify(talents).save(captor.capture());
        assertEquals(20L, captor.getValue().getBossId());
        assertEquals(30L, captor.getValue().getWorkerId());
        assertEquals("测试零工", captor.getValue().getName());
        assertEquals("138****5678", captor.getValue().getPhone());
        assertEquals("order", captor.getValue().getSource());
    }
}
