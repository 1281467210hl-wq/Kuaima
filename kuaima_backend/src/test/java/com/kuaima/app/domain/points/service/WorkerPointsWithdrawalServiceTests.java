package com.kuaima.app.domain.points.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsWithdrawal;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.points.repository.PointsWithdrawalRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

class WorkerPointsWithdrawalServiceTests {
    private UserRepository users;
    private PointsAccountRepository accounts;
    private PointsFlowRepository flows;
    private PointsWithdrawalRepository withdrawals;
    private WorkerPointsWithdrawalService service;
    private PointsAccount account;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class); accounts = mock(PointsAccountRepository.class);
        flows = mock(PointsFlowRepository.class); withdrawals = mock(PointsWithdrawalRepository.class);
        service = new WorkerPointsWithdrawalService(users, accounts, flows, withdrawals);
        User user = new User(); user.setId(7L); user.setOpenid("openid-7");
        account = new PointsAccount(); account.setUserId(7L); account.setRole("USER"); account.setBalance(3280);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(accounts.findAllByUserIdAndRoleForUpdate(7L, "USER")).thenReturn(java.util.List.of(account));
        when(withdrawals.countValidToday(eq(7L), anyCollection(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);
        when(withdrawals.save(any())).thenAnswer(invocation -> { PointsWithdrawal w = invocation.getArgument(0); if (w.getId() == null) w.setId(10001L); return w; });
    }

    @Test
    void applyShouldLockAccountDeductPointsAndWriteFlow() {
        PointsWithdrawal result = service.apply(7L, 1000, "WECHAT", "withdraw-1");

        assertEquals(2280, account.getBalance()); assertEquals(new BigDecimal("10.00"), result.getAmount());
        assertEquals("PENDING", result.getStatus());
        verify(accounts).findAllByUserIdAndRoleForUpdate(7L, "USER");
        verify(accounts).save(account); verify(flows).save(any());
    }

    @Test
    void duplicateIdempotencyKeyShouldReturnSameWithdrawalWithoutSecondDeduction() {
        PointsWithdrawal existing = withdrawal("PENDING");
        when(withdrawals.findByIdempotencyKey("same-key")).thenReturn(Optional.of(existing));

        PointsWithdrawal result = service.apply(7L, 1000, "WECHAT", "same-key");

        assertSame(existing, result); assertEquals(3280, account.getBalance());
        verify(accounts, never()).save(any()); verify(flows, never()).save(any());
    }

    @Test
    void insufficientBalanceShouldNotCreateWithdrawal() {
        account.setBalance(900);
        assertThrows(IllegalArgumentException.class, () -> service.apply(7L, 1000, "WECHAT", "low-balance"));
        verify(withdrawals, never()).save(any()); verify(flows, never()).save(any());
    }

    @Test
    void secondValidApplicationTodayShouldBeRejected() {
        when(withdrawals.countValidToday(eq(7L), anyCollection(), any(), any())).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.apply(7L, 1000, "WECHAT", "second-today"));
        verify(accounts, never()).save(any());
    }

    @Test
    void rejectShouldRefundOnlyOnce() {
        PointsWithdrawal failed = withdrawal("PENDING");
        when(withdrawals.findByIdForUpdate(10001L)).thenReturn(Optional.of(failed));

        service.reject(10001L, 9L, "渠道失败");
        service.reject(10001L, 9L, "重复回调");

        assertEquals(4280, account.getBalance()); assertEquals("FAILED", failed.getStatus());
        verify(accounts, times(1)).save(account); verify(flows, times(1)).save(any());
    }

    @Test
    void validatesMinimumAndMultiple() {
        assertThrows(IllegalArgumentException.class, () -> service.apply(7L, 900, "WECHAT", "too-small"));
        assertThrows(IllegalArgumentException.class, () -> service.apply(7L, 1050, "WECHAT", "not-multiple"));
    }

    private PointsWithdrawal withdrawal(String status) {
        PointsWithdrawal w = new PointsWithdrawal(); w.setId(10001L); w.setUserId(7L); w.setRole("USER");
        w.setWithdrawNo("PWD202609190001"); w.setIdempotencyKey("same-key"); w.setPoints(1000);
        w.setAmount(new BigDecimal("10.00")); w.setFee(BigDecimal.ZERO.setScale(2)); w.setChannel("WECHAT"); w.setStatus(status);
        return w;
    }
}
