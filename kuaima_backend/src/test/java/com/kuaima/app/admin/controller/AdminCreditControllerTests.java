package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.CreditFlowRepository;
import com.kuaima.app.domain.user.repository.AdminCreditDetailRow;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.service.CreditScoreService;
import com.kuaima.app.security.model.LoginUser;

class AdminCreditControllerTests {
    @Test
    void workerListUsesWorkerStarScoreType() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        CreditScoreService scores = mock(CreditScoreService.class);
        User worker = user(7L, false);
        when(users.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(worker));
        when(flows.findByUserIdAndScoreTypeOrderByTimestampDesc(7L, CreditScoreService.WORKER_STAR)).thenReturn(List.of());

        var result = new AdminCreditController(users, scores, flows).users(null, null, null, null, 0, 10, admin());

        assertEquals(CreditScoreService.WORKER_STAR, result.getData().getContent().get(0).get("scoreType"));
        assertEquals(7L, result.getData().getContent().get(0).get("userId"));
        assertTrue(String.valueOf(result.getData().getContent().get(0).get("id")).matches("G\\d{7}"));
        verify(users).save(worker);
        verify(flows).findByUserIdAndScoreTypeOrderByTimestampDesc(7L, CreditScoreService.WORKER_STAR);
    }

    @Test
    void adjustRequiresTheIdentitySpecificScoreTypeAndWritesAdjustment() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        CreditScoreService scores = mock(CreditScoreService.class);
        User worker = user(7L, false);
        when(users.findById(7L)).thenReturn(Optional.of(worker));
        AdminCreditDetailRow detailRow = mock(AdminCreditDetailRow.class);
        when(detailRow.getUserId()).thenReturn(7L); when(detailRow.getBusinessId()).thenReturn("G0000007");
        when(detailRow.getCreditScore()).thenReturn(80); when(detailRow.getStarScore()).thenReturn(70);
        when(detailRow.getScoreType()).thenReturn(CreditScoreService.WORKER_STAR);
        when(users.findAdminCreditDetail("7")).thenReturn(List.of(detailRow));
        when(scores.adjust(eq(7L), eq(CreditScoreService.WORKER_STAR), eq(-3), anyString(), eq("ADMIN"), anyString(), eq("原因")))
                .thenReturn(true);

        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("scoreType", CreditScoreService.WORKER_STAR); body.put("delta", -3); body.put("reason", "原因");
        var controller = new AdminCreditController(users, scores, flows);
        controller.adjust("7", body, admin());

        verify(scores).adjust(eq(7L), eq(CreditScoreService.WORKER_STAR), eq(-3), eq("ADMIN_MANUAL_ADJUST"),
                eq("ADMIN"), anyString(), eq("原因"));
    }

    @Test
    void detailAndAdjustAcceptWorkerBusinessCode() {
        UserRepository users = mock(UserRepository.class);
        CreditScoreService scores = mock(CreditScoreService.class);
        User worker = user(7L, false); worker.setWorkerCode("G0010258");
        when(users.findByWorkerCode("G0010258")).thenReturn(Optional.of(worker));
        AdminCreditDetailRow detailRow = mock(AdminCreditDetailRow.class);
        when(detailRow.getUserId()).thenReturn(7L); when(detailRow.getBusinessId()).thenReturn("G0010258");
        when(detailRow.getCreditScore()).thenReturn(80); when(detailRow.getStarScore()).thenReturn(70);
        when(detailRow.getScoreType()).thenReturn(CreditScoreService.WORKER_STAR);
        when(detailRow.getFlowId()).thenReturn(42L);
        when(detailRow.getFlowBizNo()).thenReturn("XF20260908004");
        when(detailRow.getDelta()).thenReturn(5);
        when(detailRow.getAfterScore()).thenReturn(75);
        when(users.findAdminCreditDetail("g0010258")).thenReturn(List.of(detailRow));
        when(users.findAdminCreditDetail("G0010258")).thenReturn(List.of(detailRow));
        when(scores.adjust(eq(7L), eq(CreditScoreService.WORKER_STAR), eq(2), anyString(), eq("ADMIN"), anyString(), anyString()))
                .thenReturn(true);

        var controller = new AdminCreditController(users, scores, mock(CreditFlowRepository.class));
        var detail = controller.detail("g0010258", admin());
        assertEquals("G0010258", detail.getData().get("id"));
        assertEquals(7L, detail.getData().get("userId"));
        assertEquals(10, detail.getData().get("flowLimit"));
        verify(users).findAdminCreditDetail("g0010258");
        var creditFlows = (java.util.List<?>) detail.getData().get("creditFlows");
        assertEquals("XF20260908004", ((java.util.Map<?, ?>) creditFlows.get(0)).get("id"));

        controller.adjust("G0010258", java.util.Map.of("scoreType", CreditScoreService.WORKER_STAR, "delta", 2), admin());
        verify(scores).adjust(eq(7L), eq(CreditScoreService.WORKER_STAR), eq(2), anyString(), eq("ADMIN"), anyString(), anyString());
    }

    @Test
    void detailPaginatesCreditFlowsByTenAndKeepsTotalsAcrossPages() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        User worker = user(8L, false); worker.setWorkerCode("G5892580");
        List<AdminCreditDetailRow> detailRows = java.util.stream.IntStream.rangeClosed(1, 25).mapToObj(i -> {
                    AdminCreditDetailRow row = mock(AdminCreditDetailRow.class);
                    when(row.getUserId()).thenReturn(8L);
                    when(row.getBusinessId()).thenReturn("G5892580");
                    when(row.getScoreType()).thenReturn(CreditScoreService.WORKER_STAR);
                    when(row.getFlowId()).thenReturn((long) i);
                    when(row.getFlowBizNo()).thenReturn("XF202610070" + i);
                    when(row.getDelta()).thenReturn(i % 2 == 0 ? -2 : 3);
                    when(row.getAfterScore()).thenReturn(70);
                    return row;
                }).toList();
        when(users.findAdminCreditDetail("G5892580")).thenReturn(detailRows);
        when(flows.findByUserIdAndScoreTypeOrderByTimestampDesc(8L, CreditScoreService.WORKER_STAR))
                .thenReturn(List.of());

        var controller = new AdminCreditController(users, mock(CreditScoreService.class), flows);
        var page = controller.detail("G5892580", 1, 10, admin());

        assertEquals(10, ((List<?>) page.getData().get("creditFlows")).size());
        assertEquals(25L, page.getData().get("total"));
        assertEquals(1, page.getData().get("page"));
        assertEquals(10, page.getData().get("size"));
        assertEquals(3L, page.getData().get("totalPages"));
        assertEquals(39, page.getData().get("addTotal"));
        assertEquals(24, page.getData().get("subTotal"));
    }

    @Test
    void bossListUsesPersistedBossBusinessCode() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        User boss = user(9L, true); boss.setWorkerCode("G0010258"); boss.setBossCode("B0020156");
        when(users.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(boss));
        when(flows.findByUserIdAndScoreTypeOrderByTimestampDesc(9L, CreditScoreService.BOSS_CREDIT)).thenReturn(List.of());

        var result = new AdminCreditController(users, mock(CreditScoreService.class), flows)
                .users("B0020156", null, "boss", null, 0, 10, admin());

        assertEquals("B0020156", result.getData().getContent().get(0).get("id"));
        assertEquals("boss", result.getData().getContent().get(0).get("role"));
        verify(users, never()).save(any());
    }

    @Test
    void idSearchMatchesBusinessIdAndLegacyNumericUserId() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        CreditScoreService scores = mock(CreditScoreService.class);
        User worker = user(27L, false); worker.setWorkerCode("G0010027");
        when(users.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(worker));
        when(flows.findByUserIdAndScoreTypeOrderByTimestampDesc(27L, CreditScoreService.WORKER_STAR)).thenReturn(List.of());
        var controller = new AdminCreditController(users, scores, flows);

        assertEquals(1, controller.users("G0010027", null, "worker", null, 0, 10, admin()).getData().getContent().size());
        assertEquals(1, controller.users(null, "27", "worker", null, 0, 10, admin()).getData().getContent().size());
    }

    @Test
    void listDoesNotExposeLegacyWechatIdAsUserName() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        User worker = user(28L, false);
        worker.setWorkerCode("G0010028");
        worker.setUsername("wx_3lf3DyLCISHaC123");
        worker.setPhone("18812344421");
        when(users.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(worker));
        when(flows.findByUserIdAndScoreTypeOrderByTimestampDesc(28L, CreditScoreService.WORKER_STAR)).thenReturn(List.of());

        var result = new AdminCreditController(users, mock(CreditScoreService.class), flows)
                .users(null, null, "worker", null, 0, 10, admin());

        assertEquals("188****4421", result.getData().getContent().get(0).get("name"));
    }

    @Test
    void listUsesPlaceholderWhenLegacyWechatAccountHasNoProfileOrPhone() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        User worker = user(29L, false);
        worker.setWorkerCode("G0010029");
        worker.setUsername("wx_syJez8gxKCpB123");
        when(users.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(worker));
        when(flows.findByUserIdAndScoreTypeOrderByTimestampDesc(29L, CreditScoreService.WORKER_STAR)).thenReturn(List.of());

        var result = new AdminCreditController(users, mock(CreditScoreService.class), flows)
                .users(null, null, "worker", null, 0, 10, admin());

        assertEquals("未设置昵称", result.getData().getContent().get(0).get("name"));
    }

    @Test
    void adjustmentCannotDeductBelowZeroButHasNoUpperLimit() {
        UserRepository users = mock(UserRepository.class);
        CreditScoreService scores = mock(CreditScoreService.class);
        User worker = user(31L, false); worker.setWorkerCode("G0010031"); worker.setStarScore(3);
        when(users.findByWorkerCode("G0010031")).thenReturn(Optional.of(worker));
        when(scores.adjust(eq(31L), eq(CreditScoreService.WORKER_STAR), eq(500), anyString(),
                eq("ADMIN"), anyString(), anyString())).thenReturn(false);
        AdminCreditController controller = new AdminCreditController(users, scores, mock(CreditFlowRepository.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.adjust("G0010031", java.util.Map.of(
                        "scoreType", CreditScoreService.WORKER_STAR, "delta", -4), admin()));
        assertEquals("扣分不能超过当前信用分", error.getMessage());

        assertThrows(IllegalStateException.class,
                () -> controller.adjust("G0010031", java.util.Map.of(
                        "scoreType", CreditScoreService.WORKER_STAR, "delta", 500), admin()));
        verify(scores).adjust(eq(31L), eq(CreditScoreService.WORKER_STAR), eq(500), anyString(),
                eq("ADMIN"), anyString(), anyString());
    }

    @Test
    void zeroBalanceCannotBeDeducted() {
        UserRepository users = mock(UserRepository.class);
        User worker = user(32L, false); worker.setWorkerCode("G0010032"); worker.setStarScore(0);
        when(users.findByWorkerCode("G0010032")).thenReturn(Optional.of(worker));
        AdminCreditController controller = new AdminCreditController(users, mock(CreditScoreService.class), mock(CreditFlowRepository.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> controller.adjust("G0010032", java.util.Map.of(
                        "scoreType", CreditScoreService.WORKER_STAR, "delta", -1), admin()));

        assertEquals("当前信用分为0，不支持扣分", error.getMessage());
    }

    private User user(Long id, boolean boss) {
        User user = new User(); user.setId(id); user.setCreditScore(80); user.setStarScore(70);
        if (boss) user.setEnterpriseStatus("APPROVED");
        return user;
    }

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(new LoginUser(99L, "admin", "ADMIN_ADMIN"), null);
    }
}
