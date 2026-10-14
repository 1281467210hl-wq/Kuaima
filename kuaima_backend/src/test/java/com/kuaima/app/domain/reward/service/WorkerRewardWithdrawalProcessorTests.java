package com.kuaima.app.domain.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.reward.entity.RewardAccount;
import com.kuaima.app.domain.reward.entity.RewardFlow;
import com.kuaima.app.domain.reward.entity.RewardWithdrawal;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardFlowRepository;
import com.kuaima.app.domain.reward.repository.RewardWithdrawalRepository;
import com.kuaima.app.domain.reward.service.RewardWithdrawSettingsService.RewardWithdrawSettings;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferResult;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

class WorkerRewardWithdrawalProcessorTests {
    private UserRepository users;
    private RewardAccountRepository accounts;
    private RewardFlowRepository flows;
    private RewardWithdrawalRepository withdrawals;
    private WorkerRewardWithdrawalProcessor processor;
    private User worker;
    private RewardAccount account;
    private RewardWithdrawSettings settings;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        accounts = mock(RewardAccountRepository.class);
        flows = mock(RewardFlowRepository.class);
        withdrawals = mock(RewardWithdrawalRepository.class);
        processor = new WorkerRewardWithdrawalProcessor(users, accounts, flows, withdrawals);

        worker = new User();
        worker.setId(30L);
        worker.setRealnameStatus("APPROVED");
        worker.setOpenid("openid-30");
        account = new RewardAccount();
        account.setUserId(30L);
        account.setBalance(new BigDecimal("20.00"));
        account.setFrozenAmount(new BigDecimal("5.00"));
        settings = new RewardWithdrawSettings(1000L, true, null, "WECHAT");

        when(users.findByIdForUpdate(30L)).thenReturn(Optional.of(worker));
        when(accounts.findByUserIdForUpdate(30L)).thenReturn(Optional.of(account));
        when(accounts.findByUserIdAndRoleForUpdate(30L, "USER")).thenReturn(Optional.of(account));
        when(withdrawals.saveAndFlush(any())).thenAnswer(invocation -> {
            RewardWithdrawal withdrawal = invocation.getArgument(0);
            if (withdrawal.getId() == null) withdrawal.setId(90001L);
            return withdrawal;
        });
        when(accounts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submitLocksAccountDeductsBalanceAndWritesPendingFlow() {
        RewardWithdrawal result = processor.submit(30L, 1000L, "WECHAT", settings, "worker-reward:30:key");

        assertEquals(90001L, result.getId());
        assertEquals(new BigDecimal("10.00"), result.getAmount());
        assertEquals(new BigDecimal("10.00"), account.getBalance());
        assertEquals("PENDING", result.getStatus());
        verify(accounts).findByUserIdAndRoleForUpdate(30L, "USER");
        verify(flows).saveAndFlush(any(RewardFlow.class));
    }

    @Test
    void roleAwareSubmitMustDeclareItsOwnTransactionBoundary() throws Exception {
        Method submit = WorkerRewardWithdrawalProcessor.class.getMethod(
                "submit", Long.class, long.class, String.class,
                RewardWithdrawSettings.class, String.class, String.class);

        org.junit.jupiter.api.Assertions.assertNotNull(submit.getAnnotation(Transactional.class));
    }

    @Test
    void insufficientWithdrawableBalanceDoesNotCreateWithdrawal() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.submit(30L, 1600L, "WECHAT", settings, "worker-reward:30:key"));
        verify(withdrawals, never()).saveAndFlush(any());
        verify(flows, never()).saveAndFlush(any(RewardFlow.class));
        assertEquals(new BigDecimal("20.00"), account.getBalance());
    }

    @Test
    void minimumAmountComesFromSettings() {
        RewardWithdrawSettings higher = new RewardWithdrawSettings(2000L, true, null, "WECHAT");

        assertThrows(IllegalArgumentException.class,
                () -> processor.submit(30L, 1000L, "WECHAT", higher, "worker-reward:30:key"));
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void testStageAllowsAnyPositiveCentAmount() {
        RewardWithdrawSettings testStage = new RewardWithdrawSettings(1L, true, null, "WECHAT");

        RewardWithdrawal result = processor.submit(30L, 1L, "WECHAT", testStage, "worker-reward:30:one-cent");

        assertEquals(new BigDecimal("0.01"), result.getAmount());
        assertEquals(new BigDecimal("19.99"), account.getBalance());
    }

    @Test
    void unverifiedRealnameIsRejectedBeforeBalanceChanges() {
        worker.setRealnameStatus("UNVERIFIED");

        assertThrows(IllegalArgumentException.class,
                () -> processor.submit(30L, 1000L, "WECHAT", settings, "worker-reward:30:key"));
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void missingWechatBindingIsRejected() {
        worker.setOpenid("");

        assertThrows(IllegalArgumentException.class,
                () -> processor.submit(30L, 1000L, "WECHAT", settings, "worker-reward:30:key"));
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void disabledSwitchIsRejectedWithConfiguredReason() {
        RewardWithdrawSettings disabled = new RewardWithdrawSettings(1000L, false, "系统维护", "WECHAT");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> processor.submit(30L, 1000L, "WECHAT", disabled, "worker-reward:30:key"));
        assertEquals("系统维护", error.getMessage());
    }

    @Test
    void sameIdempotencyKeyCannotBeReusedByAnotherUserOrAmount() {
        RewardWithdrawal existing = withdrawal("PENDING");
        existing.setUserId(31L);
        when(withdrawals.findByIdempotencyKey("worker-reward:30:key")).thenReturn(Optional.of(existing));

        assertThrows(ForbiddenBusinessException.class,
                () -> processor.existing(30L, 1000L, "WECHAT", "worker-reward:30:key"));
    }

    @Test
    void transferFailureRefundsOnlyOnceAndWritesRefundFlow() {
        RewardWithdrawal withdrawal = withdrawal("PENDING");
        when(withdrawals.findByIdForUpdate(90001L)).thenReturn(Optional.of(withdrawal));
        RewardFlow withdrawFlow = new RewardFlow();
        when(flows.findByUserIdAndRoleAndBizTypeAndBizId(30L, "USER", "WECHAT_WITHDRAW", 90001L))
                .thenReturn(Optional.of(withdrawFlow));

        processor.transferFailed(90001L, "微信支付接口请求失败");
        processor.transferFailed(90001L, "重复回调");

        assertEquals(new BigDecimal("30.00"), account.getBalance());
        assertEquals("FAILED", withdrawal.getStatus());
        assertEquals("微信支付接口请求失败", withdrawal.getFailureReason());
        assertEquals("FAILED", withdrawFlow.getStatus());
        verify(accounts, times(1)).saveAndFlush(account);
        verify(flows, times(1)).saveAndFlush(any(RewardFlow.class));
    }

    @Test
    void acceptedTransferStoresWechatBatchNoAndRawResponse() {
        RewardWithdrawal withdrawal = withdrawal("PENDING");
        when(withdrawals.findByIdForUpdate(90001L)).thenReturn(Optional.of(withdrawal));
        MerchantTransferResult result = new MerchantTransferResult(
                withdrawal.getMerchantBatchNo(), "batch-1", "2026-09-21T18:30:00+08:00", "ACCEPTED", "{\"batch_id\":\"batch-1\"}");

        RewardWithdrawal accepted = processor.transferAccepted(withdrawal, result);

        assertEquals("batch-1", accepted.getWechatTransferNo());
        assertEquals("{\"batch_id\":\"batch-1\"}", accepted.getTransferResponse());
        assertEquals("PENDING", accepted.getStatus());
        verify(withdrawals).saveAndFlush(withdrawal);
    }

    @Test
    void finalSuccessUpdatesWithdrawalAndExistingFlowStatus() {
        RewardWithdrawal withdrawal = withdrawal("PENDING");
        when(withdrawals.findByIdForUpdate(90001L)).thenReturn(Optional.of(withdrawal));
        RewardFlow withdrawFlow = new RewardFlow();
        when(flows.findByUserIdAndRoleAndBizTypeAndBizId(30L, "USER", "WECHAT_WITHDRAW", 90001L))
                .thenReturn(Optional.of(withdrawFlow));

        RewardWithdrawal success = processor.transferSucceeded(
                90001L, "detail-1", "{\"detail_status\":\"SUCCESS\"}");

        assertEquals("SUCCESS", success.getStatus());
        assertEquals("detail-1", success.getWechatTransferNo());
        assertEquals("SUCCESS", withdrawFlow.getStatus());
        assertEquals(new BigDecimal("20.00"), account.getBalance());
        verify(accounts, never()).saveAndFlush(any());
    }

    private RewardWithdrawal withdrawal(String status) {
        RewardWithdrawal withdrawal = new RewardWithdrawal();
        withdrawal.setId(90001L);
        withdrawal.setUserId(30L);
        withdrawal.setAmount(new BigDecimal("10.00"));
        withdrawal.setChannel("WECHAT");
        withdrawal.setStatus(status);
        withdrawal.setIdempotencyKey("worker-reward:30:key");
        withdrawal.setMerchantBatchNo("RWB2026092118300012345678");
        withdrawal.setMerchantDetailNo("RWD2026092118300012345678");
        withdrawal.setAppliedAt(LocalDateTime.of(2026, 9, 21, 18, 30));
        withdrawal.setCreatedAt(withdrawal.getAppliedAt());
        withdrawal.setUpdatedAt(withdrawal.getAppliedAt());
        return withdrawal;
    }
}
