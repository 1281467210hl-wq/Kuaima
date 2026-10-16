package com.kuaima.app.domain.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.constant.WithDrawStatus;
import com.kuaima.app.domain.wallet.entity.WithDraw;

class WorkerWalletWechatWithdrawalServiceTests {
    @Test
    void transferFailureRefundsWallet() {
        WalletService wallets = mock(WalletService.class); UserRepository users = mock(UserRepository.class);
        WechatMerchantTransferClient transfers = mock(WechatMerchantTransferClient.class);
        User worker = new User(); worker.setId(3L); worker.setOpenid("openid-3");
        when(users.findById(3L)).thenReturn(Optional.of(worker));
        WithDraw draw = draw(); when(wallets.submitWechatWithdraw(3L, new BigDecimal("10.00"), "openid-3",
                "worker-wallet:3:key1")).thenReturn(draw);
        when(transfers.transfer(any())).thenThrow(new IllegalStateException("wechat error"));
        WorkerWalletWechatWithdrawalService service = new WorkerWalletWechatWithdrawalService(wallets, users, transfers);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.withdraw(3L, new BigDecimal("10.00"), "key1"));

        assertEquals("微信商家转账发起失败，余额已退回：wechat error", exception.getMessage());
        verify(wallets).failWechatWithdraw(88L, "wechat error", null, null);
    }

    private WithDraw draw() {
        WithDraw draw = new WithDraw(); draw.setId(88L); draw.setUserId(3L); draw.setAmount(new BigDecimal("10.00"));
        draw.setStatus(WithDrawStatus.PENDING); draw.setMerchantBatchNo("WWB1"); draw.setMerchantDetailNo("WWD1");
        draw.setApplyTime(LocalDateTime.now()); return draw;
    }
}
