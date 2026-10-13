package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.kuaima.app.admin.repository.QuickReplyRepository;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.chat.entity.ChatSession;
import com.kuaima.app.domain.chat.repository.ChatMessageRepository;
import com.kuaima.app.domain.chat.repository.ChatSessionRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.faq.repository.FaqRepository;
import com.kuaima.app.websocket.WebSocketSessionManager;

class AdminServiceControllerTests {

    @Test
    void sessionDetail_shouldIncludeRecentWorkerOrders() {
        ChatSessionRepository chatSessions = mock(ChatSessionRepository.class);
        UserRepository users = mock(UserRepository.class);
        BaseOrderItemRespository orderItems = mock(BaseOrderItemRespository.class);
        BossOrderRespository orders = mock(BossOrderRespository.class);
        ChatSession session = new ChatSession();
        session.setId(10L);
        session.setUserId(66L);
        User user = new User();
        user.setId(66L);
        user.setNickname("张三");
        BaseOrderItem item = new BaseOrderItem();
        item.setId(201L);
        item.setOrderId(56L);
        item.setStatus("已完成");
        BossOrder order = new BossOrder();
        order.setId(56L);
        order.setOrderNo("KM20261013001");
        order.setOrderTitle("电子厂普工");
        order.setPostion("普工");
        order.setSalary(200);

        when(chatSessions.findById(10L)).thenReturn(Optional.of(session));
        when(users.findById(66L)).thenReturn(Optional.of(user));
        when(orderItems.findByUserId(eq(66L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(item)));
        when(orders.findById(56L)).thenReturn(Optional.of(order));

        var data = controller(chatSessions, users, orderItems, orders).sessionDetail(10L).getData();

        assertEquals(1, ((List<?>) data.get("relatedOrders")).size());
        var related = (List<java.util.Map<String, Object>>) data.get("relatedOrders");
        assertEquals(201L, related.get(0).get("itemId"));
        assertEquals("KM20261013001", related.get(0).get("orderNo"));
        assertEquals("电子厂普工", related.get(0).get("title"));
        assertEquals("已完成", related.get(0).get("status"));
    }

    private AdminServiceController controller(ChatSessionRepository chatSessions, UserRepository users,
                                              BaseOrderItemRespository orderItems, BossOrderRespository orders) {
        return new AdminServiceController(chatSessions, mock(ChatMessageRepository.class),
                mock(QuickReplyRepository.class), mock(FaqRepository.class), users,
                mock(WebSocketSessionManager.class), orderItems, orders);
    }
}
