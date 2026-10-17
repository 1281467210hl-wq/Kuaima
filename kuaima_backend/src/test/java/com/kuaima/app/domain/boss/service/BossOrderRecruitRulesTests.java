package com.kuaima.app.domain.boss.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kuaima.app.admin.entity.AdminSetting;
import com.kuaima.app.admin.repository.AdminSettingRepository;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.constant.BossType;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.message.constant.BizType;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;

/** 后台「系统设置 - 招工规则」在发布、修改、报名链路上生效。 */
class BossOrderRecruitRulesTests {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BossOrderRespository orderRepository;
    private BaseOrderItemRespository itemRepository;
    private UserRepository userRepository;
    private MessageService messageService;
    private AdminSettingRepository settings;
    private BossOrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(BossOrderRespository.class);
        itemRepository = mock(BaseOrderItemRespository.class);
        userRepository = mock(UserRepository.class);
        messageService = mock(MessageService.class);
        settings = mock(AdminSettingRepository.class);
        when(settings.findById(any())).thenReturn(Optional.empty());
        when(orderRepository.save(any(BossOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new BossOrderService(orderRepository, itemRepository, userRepository, messageService,
                mock(SettlementRespository.class), null, null, null, settings);
    }

    @Test
    void create_shouldWaitForAuditByDefault() {
        BossOrder saved = service.createOrder(order(150, at(2026, 10, 20, 8), at(2026, 10, 20, 18)));

        assertEquals(BossStatus.ORDER_PENDING_AUDIT, saved.getOrderStatus());
        verify(messageService, never()).broadcastToUsers(anyString(), anyString(), anyString(), anyString(), any(), anyMap());
    }

    @Test
    void create_shouldGoLiveAndBroadcastWhenJobAuditDisabled() {
        setting(RecruitRuleSettings.JOB_AUDIT, "false");

        BossOrder saved = service.createOrder(order(150, at(2026, 10, 20, 8), at(2026, 10, 20, 18)));

        assertEquals(BossStatus.ORDER_RECRUITING, saved.getOrderStatus());
        verify(messageService).broadcastToUsers(eq(MessageType.ORDER_PUBLISH), eq("新岗位发布"), anyString(),
                eq(BizType.ORDER), any(), anyMap());
    }

    @Test
    void create_shouldRejectSalaryBelowMinDailyWage() {
        setting(RecruitRuleSettings.MIN_DAILY_WAGE, "150");

        IllegalArgumentException below = assertThrows(IllegalArgumentException.class,
                () -> service.createOrder(order(149, at(2026, 10, 20, 8), at(2026, 10, 20, 18))));
        BossOrder boundary = service.createOrder(order(150, at(2026, 10, 20, 8), at(2026, 10, 20, 18)));

        assertEquals("日薪不能低于平台最低标准 150 元", below.getMessage());
        assertEquals(BossStatus.ORDER_PENDING_AUDIT, boundary.getOrderStatus());
    }

    @Test
    void create_shouldLimitWorkDateRangeToMaxJobValidity() {
        setting(RecruitRuleSettings.MAX_JOB_VALIDITY, "30");

        // 10-01 至 10-30 含首尾正好 30 天，允许；延到 10-31 为 31 天，拒绝
        service.createOrder(order(150, at(2026, 10, 1, 8), at(2026, 10, 30, 18)));
        IllegalArgumentException tooLong = assertThrows(IllegalArgumentException.class,
                () -> service.createOrder(order(150, at(2026, 10, 1, 8), at(2026, 10, 31, 18))));

        assertEquals("招工有效期不能超过 30 天", tooLong.getMessage());
    }

    @Test
    void create_shouldNotCountOvernightShiftEndAsExtraDay() {
        setting(RecruitRuleSettings.MAX_JOB_VALIDITY, "1");

        // 单日夜班 20:00 至次日 06:00 只算 1 个工作日
        BossOrder saved = service.createOrder(order(150, at(2026, 10, 20, 20), at(2026, 10, 21, 6)));

        assertEquals(BossStatus.ORDER_PENDING_AUDIT, saved.getOrderStatus());
    }

    @Test
    void update_shouldAlsoEnforceMinDailyWage() {
        setting(RecruitRuleSettings.MIN_DAILY_WAGE, "150");
        BossOrder existing = order(200, at(2026, 10, 20, 8), at(2026, 10, 20, 18));
        existing.setId(9L);
        existing.setOrderStatus(BossStatus.ORDER_RECRUITING);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(existing));
        BossOrder update = new BossOrder();
        update.setSalary(new BigDecimal("100"));

        assertThrows(IllegalArgumentException.class, () -> service.updateOrder(9L, update));
    }

    @Test
    void apply_shouldSkipRealnameCheckWhenRequirementDisabled() {
        setting(RecruitRuleSettings.REALNAME_REQUIRED, "false");
        BossOrder order = recruitingOrder();
        when(userRepository.findById(5L)).thenReturn(Optional.of(new User()));
        when(itemRepository.findByOrderId(30L)).thenReturn(List.of());
        when(itemRepository.save(any())).thenAnswer(invocation -> {
            BaseOrderItem item = invocation.getArgument(0);
            item.setId(40L);
            return item;
        });

        BaseOrderItem item = service.applyOrder(order.getId(), 5L, null, false);

        assertEquals(BossStatus.ITEM_APPLIED, item.getStatus());
    }

    @Test
    void apply_shouldRequireRealnameByDefault() {
        recruitingOrder();
        when(userRepository.findById(5L)).thenReturn(Optional.of(new User()));

        assertThrows(ForbiddenBusinessException.class, () -> service.applyOrder(30L, 5L, null, false));
    }

    private BossOrder recruitingOrder() {
        BossOrder order = order(150, at(2026, 10, 20, 8), at(2026, 10, 20, 18));
        order.setId(30L);
        order.setCreateBy(1L);
        order.setOrderStatus(BossStatus.ORDER_RECRUITING);
        order.setSignMode("manual");
        when(orderRepository.findById(30L)).thenReturn(Optional.of(order));
        return order;
    }

    private void setting(String key, String value) {
        AdminSetting setting = new AdminSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        when(settings.findById(key)).thenReturn(Optional.of(setting));
    }

    private static Date at(int year, int month, int day, int hour) {
        return Date.from(LocalDateTime.of(year, month, day, hour, 0).atZone(ZONE).toInstant());
    }

    private static BossOrder order(int salary, Date start, Date end) {
        BossOrder order = new BossOrder();
        order.setOrderTitle("测试岗位");
        order.setType(BossType.DAILY);
        order.setPostion("普工");
        order.setOrderNum(1);
        order.setDuration(8);
        order.setSalary(salary);
        order.setLongitude(new BigDecimal("121.4737010"));
        order.setLatitude(new BigDecimal("31.2304160"));
        order.setStartTime(start);
        order.setEndTime(end);
        return order;
    }
}
