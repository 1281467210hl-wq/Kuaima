package com.kuaima.app.domain.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.kuaima.app.domain.reward.entity.RewardAccount;
import com.kuaima.app.domain.reward.entity.RewardEarningRule;
import com.kuaima.app.domain.reward.entity.RewardFlow;
import com.kuaima.app.domain.reward.entity.RewardWithdrawal;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardEarningRuleRepository;
import com.kuaima.app.domain.reward.repository.RewardFlowRepository;
import com.kuaima.app.domain.reward.service.RewardWithdrawSettingsService.RewardWithdrawSettings;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferRequest;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferResult;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

class WorkerRewardServiceTests {
    private UserRepository users;
    private RewardAccountRepository accounts;
    private RewardFlowRepository flows;
    private RewardEarningRuleRepository rules;
    private RewardWithdrawSettingsService settingsService;
    private WorkerRewardWithdrawalProcessor processor;
    private WechatMerchantTransferClient transferClient;
    private WorkerRewardService service;
    private User worker;
    private RewardWithdrawSettings settings;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        accounts = mock(RewardAccountRepository.class);
        flows = mock(RewardFlowRepository.class);
        rules = mock(RewardEarningRuleRepository.class);
        settingsService = mock(RewardWithdrawSettingsService.class);
        processor = mock(WorkerRewardWithdrawalProcessor.class);
        transferClient = mock(WechatMerchantTransferClient.class);
        service = new WorkerRewardService(users, accounts, flows, rules, settingsService, processor, transferClient);

        worker = new User();
        worker.setId(30L);
        worker.setRealnameStatus("APPROVED");
        worker.setOpenid("openid-30");
        settings = new RewardWithdrawSettings(1000L, true, null, "WECHAT");
        when(users.findById(30L)).thenReturn(Optional.of(worker));
        when(settingsService.settings()).thenReturn(settings);
    }

    @Test
    void overviewUsesCentsAndReadsWithdrawableBalance() {
        RewardAccount account = new RewardAccount();
        account.setBalance(new BigDecimal("20.00"));
        account.setFrozenAmount(new BigDecimal("5.00"));
        when(accounts.findByUserId(30L)).thenReturn(Optional.of(account));

        var result = service.overview(30L);

        assertEquals(2000L, result.get("balance"));
        assertEquals(1500L, result.get("withdrawableBalance"));
        assertEquals(1000L, result.get("minimumWithdrawAmount"));
        assertEquals(true, result.get("withdrawEnabled"));
        assertNull(result.get("withdrawDisabledReason"));
    }

    @Test
    void overviewExplainsWhyWorkerCannotWithdraw() {
        worker.setRealnameStatus("UNVERIFIED");
        RewardAccount account = new RewardAccount();
        account.setBalance(new BigDecimal("20.00"));
        when(accounts.findByUserId(30L)).thenReturn(Optional.of(account));

        var result = service.overview(30L);

        assertEquals(false, result.get("withdrawEnabled"));
        assertEquals("请先完成实名认证", result.get("withdrawDisabledReason"));
    }

    @Test
    void recordsArePagedAndWithdrawAmountIsNegativeCents() {
        RewardFlow income = new RewardFlow();
        income.setId(1L);
        income.setType("INCOME");
        income.setBizType("INVITE_FIRST_ORDER");
        income.setTitle("邀请好友奖励");
        income.setRemark("好友完成首单");
        income.setAmount(new BigDecimal("8.00"));
        income.setBalanceAfter(new BigDecimal("20.00"));
        income.setCreatedAt(LocalDateTime.of(2026, 9, 10, 14, 20));
        Pageable pageable = PageRequest.of(0, 20);
        RewardFlow withdraw = new RewardFlow();
        withdraw.setId(2L);
        withdraw.setType("EXPENSE");
        withdraw.setBizType("WECHAT_WITHDRAW");
        withdraw.setTitle("提现至微信零钱");
        withdraw.setAmount(new BigDecimal("15.00"));
        withdraw.setBalanceAfter(new BigDecimal("5.00"));
        withdraw.setStatus("SUCCESS");
        withdraw.setCreatedAt(LocalDateTime.of(2026, 9, 11, 16, 30));
        when(flows.findByUserIdOrderByCreatedAtDescIdDesc(30L, pageable))
                .thenReturn(new PageImpl<>(List.of(income, withdraw), pageable, 2));

        var result = service.records(30L, "ALL", pageable);

        assertEquals(2L, result.get("total"));
        assertEquals(0, result.get("page"));
        assertEquals(20, result.get("size"));
        assertEquals(false, result.get("hasMore"));
        var records = List.class.cast(result.get("records"));
        var incomeView = java.util.Map.class.cast(records.get(0));
        var withdrawView = java.util.Map.class.cast(records.get(1));
        assertEquals(800L, incomeView.get("amount"));
        assertEquals(-1500L, withdrawView.get("amount"));
        assertEquals("WITHDRAW", withdrawView.get("type"));
    }

    @Test
    void withdrawSubmitsWechatTransferAndReturnsCents() {
        RewardWithdrawal withdrawal = withdrawal("PENDING");
        when(processor.existing(eq(30L), eq(1000L), eq("WECHAT"), any())).thenReturn(Optional.empty());
        when(processor.submit(eq(30L), eq(1000L), eq("WECHAT"), eq(settings), any())).thenReturn(withdrawal);
        MerchantTransferResult accepted = new MerchantTransferResult(
                withdrawal.getMerchantBatchNo(), "batch-1", "2026-09-21T18:30:00+08:00", "ACCEPTED", "{}");
        when(transferClient.transfer(any())).thenReturn(accepted);
        when(processor.transferAccepted(eq(withdrawal), eq(accepted))).thenReturn(withdrawal);
        RewardAccount account = new RewardAccount();
        account.setBalance(new BigDecimal("10.00"));
        when(accounts.findByUserId(30L)).thenReturn(Optional.of(account));

        var result = service.withdraw(30L, 1000L, "WECHAT", "request-1");

        assertEquals(90001L, result.get("withdrawId"));
        assertEquals(1000L, result.get("amount"));
        assertEquals("PENDING", result.get("status"));
        assertEquals(1000L, result.get("balance"));
        verify(transferClient).transfer(any(MerchantTransferRequest.class));
    }

    @Test
    void duplicateIdempotencyKeyDoesNotSubmitAgain() {
        RewardWithdrawal existing = withdrawal("PENDING");
        when(processor.existing(eq(30L), eq(1000L), eq("WECHAT"), any())).thenReturn(Optional.of(existing));
        RewardAccount account = new RewardAccount();
        account.setBalance(new BigDecimal("10.00"));
        when(accounts.findByUserId(30L)).thenReturn(Optional.of(account));

        var result = service.withdraw(30L, 1000L, "WECHAT", "request-1");

        assertEquals(90001L, result.get("withdrawId"));
        verify(processor, never()).submit(any(), eq(1000L), any(), any(), any());
        verify(transferClient, never()).transfer(any());
    }

    @Test
    void concurrentIdempotencyInsertReturnsWinnerInsteadOfFailing() {
        RewardWithdrawal winner = withdrawal("PENDING");
        when(processor.existing(eq(30L), eq(1000L), eq("WECHAT"), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(processor.submit(eq(30L), eq(1000L), eq("WECHAT"), eq(settings), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        RewardAccount account = new RewardAccount();
        account.setBalance(new BigDecimal("10.00"));
        when(accounts.findByUserId(30L)).thenReturn(Optional.of(account));

        var result = service.withdraw(30L, 1000L, "WECHAT", "request-1");

        assertEquals(90001L, result.get("withdrawId"));
        verify(transferClient, never()).transfer(any());
    }

    @Test
    void wechatFailureRefundsAndThrowsExplicitError() {
        RewardWithdrawal submitted = withdrawal("PENDING");
        when(processor.existing(eq(30L), eq(1000L), eq("WECHAT"), any())).thenReturn(Optional.empty());
        when(processor.submit(eq(30L), eq(1000L), eq("WECHAT"), eq(settings), any(), eq("USER"))).thenReturn(submitted);
        String reason = "微信支付接口请求失败: HTTP 403, code=NO_AUTH, message=商户无权限";
        when(transferClient.transfer(any())).thenThrow(new IllegalStateException(reason));
        RewardWithdrawal failed = withdrawal("FAILED");
        when(processor.transferFailed(90001L, reason)).thenReturn(failed);

        WorkerRewardWithdrawalFailedException error = assertThrows(WorkerRewardWithdrawalFailedException.class,
                () -> service.withdraw(30L, 1000L, "WECHAT", "request-1"));

        assertEquals("微信商家转账发起失败，奖励金已退回：" + reason, error.getMessage());
        verify(processor).transferFailed(90001L, reason);
    }

    @Test
    void rulesComeFromDatabaseAndUseConfiguredMinimum() {
        RewardEarningRule invite = new RewardEarningRule();
        invite.setCode("INVITE_FIRST_ORDER");
        invite.setName("邀请好友接单");
        invite.setDescription("好友注册并完成首单后发放");
        invite.setRewardAmount(800L);
        invite.setActionType("NAVIGATE");
        invite.setActionPath("/pages/worker/invite");
        invite.setEnabled(true);
        when(rules.findByEnabledTrueOrderBySortAscIdAsc()).thenReturn(List.of(invite));

        var result = service.rules();

        assertEquals(1000L, result.get("minimumWithdrawAmount"));
        assertEquals("WECHAT", result.get("withdrawChannel"));
        var methods = List.class.cast(result.get("earningMethods"));
        var inviteView = java.util.Map.class.cast(methods.get(0));
        assertEquals(800L, inviteView.get("rewardAmount"));
    }

    private RewardWithdrawal withdrawal(String status) {
        RewardWithdrawal withdrawal = new RewardWithdrawal();
        withdrawal.setId(90001L);
        withdrawal.setUserId(30L);
        withdrawal.setAmount(new BigDecimal("10.00"));
        withdrawal.setChannel("WECHAT");
        withdrawal.setStatus(status);
        withdrawal.setMerchantBatchNo("RWB2026092118300012345678");
        withdrawal.setMerchantDetailNo("RWD2026092118300012345678");
        withdrawal.setAppliedAt(LocalDateTime.of(2026, 9, 21, 18, 30));
        withdrawal.setCreatedAt(withdrawal.getAppliedAt());
        withdrawal.setUpdatedAt(withdrawal.getAppliedAt());
        return withdrawal;
    }
}
