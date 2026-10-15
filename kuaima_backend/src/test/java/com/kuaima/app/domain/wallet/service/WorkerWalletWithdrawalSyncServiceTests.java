package com.kuaima.app.domain.wallet.service;

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

import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient;
import com.kuaima.app.domain.wallet.constant.WithDrawStatus;
import com.kuaima.app.domain.wallet.entity.WithDraw;
import com.kuaima.app.domain.wallet.repository.WithDrawRespository;

class WorkerWalletWithdrawalSyncServiceTests {
    private WithDrawRespository withdrawals;
    private WalletService walletService;
    private WechatMerchantTransferClient transferClient;
    private WorkerWalletWithdrawalSyncService service;
    private WithDraw pending;

    @BeforeEach
    void setUp() {
        withdrawals = mock(WithDrawRespository.class);
        walletService = mock(WalletService.class);
        transferClient = mock(WechatMerchantTransferClient.class);
        service = new WorkerWalletWithdrawalSyncService(withdrawals, walletService, transferClient);
        pending = new WithDraw();
        pending.setId(88L);
        pending.setStatus(WithDrawStatus.PENDING);
        pending.setMerchantBatchNo("WWB123456789012345678901234");
        when(withdrawals.findTop20ByStatusAndApplyTimeBeforeAndMerchantBatchNoIsNotNullOrderByApplyTimeAsc(
                eq(WithDrawStatus.PENDING), any(LocalDateTime.class))).thenReturn(List.of(pending));
    }

    @Test
    void successUpdatesWalletWithdrawal() {
        when(transferClient.query(pending.getMerchantBatchNo())).thenReturn(
                new WechatMerchantTransferClient.MerchantTransferStatus(
                        "SUCCESS", "bill-1", null, null, "{\"state\":\"SUCCESS\"}"));

        int changed = service.syncPending();

        org.junit.jupiter.api.Assertions.assertEquals(1, changed);
        verify(walletService).succeedWechatWithdraw(88L, "bill-1", "{\"state\":\"SUCCESS\"}");
    }

    @Test
    void cancelledTransferRefundsWalletWithWechatReason() {
        when(transferClient.query(pending.getMerchantBatchNo())).thenReturn(
                new WechatMerchantTransferClient.MerchantTransferStatus(
                        "CANCELLED", "bill-1", "TRANSFER_CANCELLED", null, "{\"state\":\"CANCELLED\"}"));

        int changed = service.syncPending();

        org.junit.jupiter.api.Assertions.assertEquals(1, changed);
        verify(walletService).failWechatWithdraw(
                88L, "TRANSFER_CANCELLED", "bill-1", "{\"state\":\"CANCELLED\"}");
    }

    @Test
    void waitUserConfirmRemainsPending() {
        when(transferClient.query(pending.getMerchantBatchNo())).thenReturn(
                new WechatMerchantTransferClient.MerchantTransferStatus(
                        "WAIT_USER_CONFIRM", "bill-1", null, null, "{}"));

        int changed = service.syncPending();

        org.junit.jupiter.api.Assertions.assertEquals(0, changed);
        verify(walletService, never()).succeedWechatWithdraw(any(), anyString(), anyString());
        verify(walletService, never()).failWechatWithdraw(any(), anyString(), anyString(), anyString());
    }

    @Test
    void legacyPendingTransferStillUsesLegacyQuery() {
        pending.setTransferResponse("{\"batch_id\":\"batch-1\"}");
        when(transferClient.queryLegacy(pending.getMerchantBatchNo())).thenReturn(
                new WechatMerchantTransferClient.MerchantTransferStatus(
                        "FINISHED", "detail-1", null, "SUCCESS", "{\"batch_status\":\"FINISHED\"}"));

        int changed = service.syncPending();

        org.junit.jupiter.api.Assertions.assertEquals(1, changed);
        verify(transferClient).queryLegacy(pending.getMerchantBatchNo());
        verify(walletService).succeedWechatWithdraw(88L, "detail-1", "{\"batch_status\":\"FINISHED\"}");
    }
}
