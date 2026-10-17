package com.kuaima.app.domain.boss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.message.constant.BizType;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;

class BossOrderManualReviewNotificationTests {

    @Test
    void manualReview_shouldNotifyBossEvenWhenSignNotifyIsDisabled() {
        BossOrderRespository orderRepository = mock(BossOrderRespository.class);
        BaseOrderItemRespository itemRepository = mock(BaseOrderItemRespository.class);
        UserRepository userRepository = mock(UserRepository.class);
        MessageService messageService = mock(MessageService.class);
        BossOrderService service = new BossOrderService(orderRepository, itemRepository,
                userRepository, messageService, mock(SettlementRespository.class));
        BossOrder order = new BossOrder();
        order.setId(21L);
        order.setCreateBy(1L);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order.setOrderNum(1);
        order.setOrderTitle("五金厂CNC操作工");
        order.setPostion("CNC操作工");
        order.setSignMode("manual");
        order.setSignNotify(false);
        User worker = verifiedWorker();
        worker.setNickname("测试零工");
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(userRepository.findById(2L)).thenReturn(Optional.of(worker));
        when(itemRepository.existsByOrderIdAndUserId(21L, 2L)).thenReturn(false);
        when(itemRepository.findByOrderId(21L)).thenReturn(List.of());
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            BaseOrderItem item = invocation.getArgument(0);
            item.setId(31L);
            return item;
        });

        BaseOrderItem result = service.applyOrder(21L, 2L, null, false);

        assertEquals(BossStatus.ITEM_APPLIED, result.getStatus());
        verify(messageService).sendToUser(eq(1L), eq(UserRole.BOSS), eq(MessageType.ORDER_APPLY),
                eq("有新的报名待审核"), any(String.class), eq(BizType.ITEM), eq(31L));
    }

    @Test
    void applyOrder_shouldTranslateDuplicateKeyConflict() {
        BossOrderRespository orderRepository = mock(BossOrderRespository.class);
        BaseOrderItemRespository itemRepository = mock(BaseOrderItemRespository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BossOrderService service = new BossOrderService(orderRepository, itemRepository,
                userRepository, mock(MessageService.class), mock(SettlementRespository.class));
        BossOrder order = new BossOrder();
        order.setId(22L);
        order.setCreateBy(1L);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order.setOrderNum(2);
        order.setOrderTitle("仓库分拣员");
        order.setPostion("分拣员");
        when(orderRepository.findById(22L)).thenReturn(Optional.of(order));
        when(userRepository.findById(3L)).thenReturn(Optional.of(verifiedWorker()));
        when(itemRepository.existsByOrderIdAndUserId(22L, 3L)).thenReturn(false);
        when(itemRepository.findByOrderId(22L)).thenReturn(List.of());
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("uk_boss_order_item_order_user"))
                .when(itemRepository).flush();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.applyOrder(22L, 3L, null, false));

        assertEquals("您已报名过该订单", exception.getMessage());
    }

    @Test
    void autoSignMode_shouldHireApplicantAndEndRecruitWhenFull() {
        BossOrderRespository orderRepository = mock(BossOrderRespository.class);
        BaseOrderItemRespository itemRepository = mock(BaseOrderItemRespository.class);
        UserRepository userRepository = mock(UserRepository.class);
        MessageService messageService = mock(MessageService.class);
        BossOrderService service = new BossOrderService(orderRepository, itemRepository,
                userRepository, messageService, mock(SettlementRespository.class));
        BossOrder order = new BossOrder();
        order.setId(62L);
        order.setCreateBy(1L);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order.setOrderNum(1);
        order.setOrderTitle("电子厂普工");
        order.setPostion("普工");
        order.setSignMode("auto");
        order.setSignNotify(false);
        when(orderRepository.findById(62L)).thenReturn(Optional.of(order));
        when(userRepository.findById(66L)).thenReturn(Optional.of(verifiedWorker()));
        when(itemRepository.existsByOrderIdAndUserId(62L, 66L)).thenReturn(false);
        BaseOrderItem[] saved = new BaseOrderItem[1];
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            BaseOrderItem item = invocation.getArgument(0);
            item.setId(51L);
            saved[0] = item;
            return item;
        });
        // 报名前无人录用；录用后重新统计时包含刚自动录用的这一条
        when(itemRepository.findByOrderId(62L)).thenReturn(List.of())
                .thenAnswer(invocation -> List.of(saved[0]));

        BaseOrderItem result = service.applyOrder(62L, 66L, null, false);

        assertEquals(BossStatus.ITEM_HIRED, result.getStatus());
        assertEquals(BossStatus.ORDER_RECRUIT_END, order.getOrderStatus());
        verify(messageService).sendToUser(eq(66L), eq(UserRole.USER), eq(MessageType.ORDER_HIRE),
                eq("录用通知"), anyString(), eq(BizType.ITEM), eq(51L), anyMap());
        // 报名通知已关闭，自动通过模式不打扰老板
        verify(messageService, never()).sendToUser(eq(1L), eq(UserRole.BOSS), eq(MessageType.ORDER_APPLY),
                anyString(), anyString(), eq(BizType.ITEM), eq(51L));
    }

    @Test
    void applyOrder_shouldRejectWorkerWithoutFaceRealname() {
        BossOrderRespository orderRepository = mock(BossOrderRespository.class);
        BaseOrderItemRespository itemRepository = mock(BaseOrderItemRespository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BossOrderService service = new BossOrderService(orderRepository, itemRepository,
                userRepository, mock(MessageService.class), mock(SettlementRespository.class));
        BossOrder order = new BossOrder();
        order.setId(23L);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order.setOrderNum(1);
        // 旧流程遗留：状态是 APPROVED 但没有人脸照片，不算实名
        User legacy = new User();
        legacy.setRealnameStatus(CertificationStatus.APPROVED);
        when(orderRepository.findById(23L)).thenReturn(Optional.of(order));
        when(userRepository.findById(4L)).thenReturn(Optional.of(legacy));

        ForbiddenBusinessException error = assertThrows(ForbiddenBusinessException.class,
                () -> service.applyOrder(23L, 4L, null, false));

        assertEquals("请先完成实名认证", error.getMessage());
        verify(itemRepository, never()).save(any());
    }

    private static User verifiedWorker() {
        User user = new User();
        user.setRealnameStatus(CertificationStatus.APPROVED);
        user.setRealnameIdCardImageUrl("insurance/realname/id-card.png");
        user.setRealnameFaceImageUrl("insurance/realname/face.png");
        return user;
    }
}
