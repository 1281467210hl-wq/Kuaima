package com.kuaima.app.domain.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kuaima.app.alipay.service.AlipayFundTransferClient;
import com.kuaima.app.domain.reward.entity.RewardWithdrawal;
import com.kuaima.app.domain.reward.repository.RewardWithdrawalRepository;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferStatus;

class WorkerRewardWithdrawalSyncServiceTests {
    private RewardWithdrawalRepository withdrawals;
    private WorkerRewardWithdrawalProcessor processor;
    private WechatMerchantTransferClient client;
    private AlipayFundTransferClient alipayClient;
    private WorkerRewardWithdrawalSyncService service;
    private RewardWithdrawal pending;

    @BeforeEach
    void setUp() {
        withdrawals = mock(RewardWithdrawalRepository.class);
        processor = mock(WorkerRewardWithdrawalProcessor.class);
        client = mock(WechatMerchantTransferClient.class);
        alipayClient = mock(AlipayFundTransferClient.class);
        service = new WorkerRewardWithdrawalSyncService(withdrawals, processor, client, alipayClient);
        pending = new RewardWithdrawal();
        pending.setId(90001L);
        pending.setMerchantBatchNo("RWB2026092118300012345678");
        when(withdrawals.findTop20ByStatusAndAppliedAtBeforeOrderByAppliedAtAscIdAsc(
                eq("PENDING"), any(LocalDateTime.class))).thenReturn(List.of(pending));
    }

    @Test
    void successUpdatesWithdrawalAndFlowThroughProcessor() {
        MerchantTransferStatus status = new MerchantTransferStatus(
                "SUCCESS", "bill-1", null, null, "{\"state\":\"SUCCESS\"}");
        when(client.query(pending.getMerchantBatchNo())).thenReturn(status);

        int changed = service.syncPending();

        assertEquals(1, changed);
        verify(processor).transferSucceeded(90001L, "bill-1", status.rawResponse());
        verify(processor, never()).transferFailed(any(), anyString(), anyString(), anyString());
    }

    @Test
    void failedOrCancelledTransferRefundsReward() {
        MerchantTransferStatus status = new MerchantTransferStatus(
                "FAIL", "bill-1", "PAYEE_ACCOUNT_ABNORMAL", null, "{\"state\":\"FAIL\"}");
        when(client.query(pending.getMerchantBatchNo())).thenReturn(status);

        int changed = service.syncPending();

        assertEquals(1, changed);
        verify(processor).transferFailed(90001L, "PAYEE_ACCOUNT_ABNORMAL", "bill-1", status.rawResponse());
        verify(processor, never()).transferSucceeded(any(), anyString(), anyString());
    }

    @Test
    void processingBatchRemainsPending() {
        when(client.query(pending.getMerchantBatchNo())).thenReturn(
                new MerchantTransferStatus("WAIT_USER_CONFIRM", "bill-1", null, null, "{}"));

        assertEquals(0, service.syncPending());

        verify(processor, never()).transferSucceeded(any(), anyString(), anyString());
        verify(processor, never()).transferFailed(any(), anyString(), anyString(), anyString());
    }

    @Test
    void legacyPendingTransferStillUsesLegacyQuery() {
        pending.setTransferResponse("{\"batch_id\":\"batch-1\"}");
        MerchantTransferStatus status = new MerchantTransferStatus(
                "FINISHED", "detail-1", null, "SUCCESS", "{\"batch_status\":\"FINISHED\"}");
        when(client.queryLegacy(pending.getMerchantBatchNo())).thenReturn(status);

        int changed = service.syncPending();

        assertEquals(1, changed);
        verify(client).queryLegacy(pending.getMerchantBatchNo());
        verify(client, never()).query(anyString());
        verify(processor).transferSucceeded(90001L, "detail-1", status.rawResponse());
    }

    @Test
    void queryFailureDoesNotMistakenlyRefund() {
        when(client.query(pending.getMerchantBatchNo())).thenThrow(new IllegalStateException("查询失败"));

        assertEquals(0, service.syncPending());

        verify(processor, never()).transferFailed(any(), anyString(), anyString(), anyString());
    }

    @Test
    void alipayFailureRefundsReward() {
        pending.setChannel("ALIPAY");
        pending.setMerchantDetailNo("RWD2026092118300012345678");
        when(alipayClient.transferEnabled()).thenReturn(true);
        AlipayFundTransferClient.QueryResult status = new AlipayFundTransferClient.QueryResult(
                "FAIL", "收款账户不存在", "{\"status\":\"FAIL\"}");
        when(alipayClient.query(pending.getMerchantDetailNo())).thenReturn(status);

        int changed = service.syncPending();

        assertEquals(1, changed);
        verify(processor).transferFailed(90001L, "收款账户不存在");
        verify(client, never()).query(anyString());
    }
}
