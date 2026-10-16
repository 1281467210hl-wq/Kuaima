package com.kuaima.app.domain.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kuaima.app.alipay.service.AlipayFundTransferClient;
import com.kuaima.app.domain.reward.entity.RewardWithdrawal;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardFlowRepository;
import com.kuaima.app.domain.reward.repository.RewardWithdrawalRepository;
import com.kuaima.app.domain.reward.service.RewardWithdrawSettingsService.RewardWithdrawSettings;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferResult;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

class BossRewardWithdrawalTests {
    @Test
    void bossWithdrawalUsesWechatTransferWithoutDeductingWalletAgain() {
        UserRepository users = mock(UserRepository.class);
        RewardWithdrawSettingsService settings = mock(RewardWithdrawSettingsService.class);
        WorkerRewardWithdrawalProcessor processor = mock(WorkerRewardWithdrawalProcessor.class);
        WechatMerchantTransferClient transfer = mock(WechatMerchantTransferClient.class);
        User boss = new User(); boss.setId(7L); boss.setEnterpriseStatus("APPROVED"); boss.setOpenid("openid-7");
        when(users.findById(7L)).thenReturn(Optional.of(boss));
        RewardWithdrawSettings config = new RewardWithdrawSettings(1000L, true, null, "WECHAT");
        when(settings.settings()).thenReturn(config);
        RewardWithdrawal withdrawal = withdrawal();
        when(processor.existing(eq(7L), eq(1000L), eq("WECHAT"), any())).thenReturn(Optional.empty());
        when(processor.submit(eq(7L), eq(1000L), eq("WECHAT"), eq(config), any(), eq("BOSS"), eq(null)))
                .thenReturn(withdrawal);
        MerchantTransferResult accepted = new MerchantTransferResult("RWB1", "batch-1", null, "ACCEPTED", "{}");
        when(transfer.transfer(any())).thenReturn(accepted);
        when(processor.transferAccepted(withdrawal, accepted)).thenReturn(withdrawal);
        BossRewardService service = new BossRewardService(mock(RewardAccountRepository.class),
                mock(RewardFlowRepository.class), mock(RewardWithdrawalRepository.class), users,
                settings, processor, transfer);

        var result = service.withdraw(7L, new BigDecimal("10.00"), "request-1");

        assertEquals(new BigDecimal("10.00"), result.get("amount"));
        verify(transfer).transfer(any(WechatMerchantTransferClient.MerchantTransferRequest.class));
        verify(processor).submit(7L, 1000L, "WECHAT", config, "boss-reward:7:request-1", "BOSS", null);
    }

    @Test
    void bossWithdrawalUsesAlipayTransferAndStoresPayeeAccount() {
        UserRepository users = mock(UserRepository.class);
        RewardWithdrawSettingsService settings = mock(RewardWithdrawSettingsService.class);
        WorkerRewardWithdrawalProcessor processor = mock(WorkerRewardWithdrawalProcessor.class);
        AlipayFundTransferClient transfer = mock(AlipayFundTransferClient.class);
        User boss = new User();
        boss.setId(7L); boss.setEnterpriseStatus("APPROVED"); boss.setRealnameStatus("APPROVED");
        when(users.findById(7L)).thenReturn(Optional.of(boss));
        RewardWithdrawSettings config = new RewardWithdrawSettings(1000L, true, null, "WECHAT");
        when(settings.settings()).thenReturn(config);
        when(transfer.transferEnabled()).thenReturn(true);
        RewardWithdrawal withdrawal = withdrawal("ALIPAY");
        when(processor.existing(eq(7L), eq(1000L), eq("ALIPAY"), any())).thenReturn(Optional.empty());
        when(processor.submit(eq(7L), eq(1000L), eq("ALIPAY"), eq(config), any(), eq("BOSS"), eq("test@example.com")))
                .thenReturn(withdrawal);
        AlipayFundTransferClient.TransferResult result = new AlipayFundTransferClient.TransferResult(
                "alipay-order-1", "pay-fund-1", "SUCCESS", "{\"status\":\"SUCCESS\"}");
        when(transfer.transfer("RWD1", "test@example.com", new BigDecimal("10.00"), "快马日结奖励金提现"))
                .thenReturn(result);
        when(processor.transferAccepted(withdrawal, result)).thenReturn(withdrawal);
        when(processor.transferSucceeded(withdrawal.getId(), result.orderId(), result.rawResponse()))
                .thenReturn(withdrawal);
        BossRewardService service = new BossRewardService(mock(RewardAccountRepository.class),
                mock(RewardFlowRepository.class), mock(RewardWithdrawalRepository.class), users,
                settings, processor, null, transfer);

        var resultView = service.withdraw(
                7L, new BigDecimal("10.00"), "ALIPAY", "test@example.com", "request-1");

        assertEquals("ALIPAY", resultView.get("channel"));
        assertEquals("test@example.com", resultView.get("payeeAccount"));
        verify(transfer).transfer("RWD1", "test@example.com", new BigDecimal("10.00"), "快马日结奖励金提现");
    }

    @Test
    void bossWechatWithdrawalFailureReturnsWechatReason() {
        UserRepository users = mock(UserRepository.class);
        RewardWithdrawSettingsService settings = mock(RewardWithdrawSettingsService.class);
        WorkerRewardWithdrawalProcessor processor = mock(WorkerRewardWithdrawalProcessor.class);
        WechatMerchantTransferClient transfer = mock(WechatMerchantTransferClient.class);
        User boss = new User(); boss.setId(7L); boss.setEnterpriseStatus("APPROVED"); boss.setOpenid("openid-7");
        when(users.findById(7L)).thenReturn(Optional.of(boss));
        RewardWithdrawSettings config = new RewardWithdrawSettings(1000L, true, null, "WECHAT");
        when(settings.settings()).thenReturn(config);
        RewardWithdrawal withdrawal = withdrawal();
        when(processor.existing(eq(7L), eq(1000L), eq("WECHAT"), any())).thenReturn(Optional.empty());
        when(processor.submit(eq(7L), eq(1000L), eq("WECHAT"), eq(config), any(), eq("BOSS"), eq(null)))
                .thenReturn(withdrawal);
        when(transfer.transfer(any())).thenThrow(new IllegalStateException("HTTP 403, code=NO_AUTH"));
        BossRewardService service = new BossRewardService(mock(RewardAccountRepository.class),
                mock(RewardFlowRepository.class), mock(RewardWithdrawalRepository.class), users,
                settings, processor, transfer);

        WorkerRewardWithdrawalFailedException exception = assertThrows(WorkerRewardWithdrawalFailedException.class,
                () -> service.withdraw(7L, new BigDecimal("10.00"), "request-1"));

        assertEquals("微信商家转账发起失败，奖励金已退回：HTTP 403, code=NO_AUTH", exception.getMessage());
        verify(processor).transferFailed(withdrawal.getId(), "HTTP 403, code=NO_AUTH");
    }

    private RewardWithdrawal withdrawal() {
        return withdrawal("WECHAT");
    }

    private RewardWithdrawal withdrawal(String channel) {
        RewardWithdrawal withdrawal = new RewardWithdrawal();
        withdrawal.setId(91L); withdrawal.setUserId(7L); withdrawal.setAmount(new BigDecimal("10.00"));
        withdrawal.setChannel(channel); withdrawal.setStatus("PENDING"); withdrawal.setMerchantBatchNo("RWB1");
        withdrawal.setMerchantDetailNo("RWD1"); withdrawal.setAppliedAt(LocalDateTime.now());
        withdrawal.setPayeeAccount("ALIPAY".equals(channel) ? "test@example.com" : null);
        return withdrawal;
    }
}
