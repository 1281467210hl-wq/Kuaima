package com.kuaima.app.admin.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.kuaima.app.admin.entity.RewardCampaign;
import com.kuaima.app.admin.repository.*;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.admin.entity.RewardFundAccount;
import com.kuaima.app.domain.wallet.repository.*;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.kuaima.app.domain.wallet.entity.Wallet;
import com.kuaima.app.domain.wallet.entity.WalletFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminRewardServiceTests {
    private RewardCampaignRepository campaigns;
    private RewardGrantRepository grants;
    private RewardFundAccountRepository funds;
    private RewardFundFlowRepository fundFlows;
    private UserRepository users;
    private WalletRespository wallets;
    private WalletFlowRespository walletFlows;
    private AdminRewardService service;

    @BeforeEach
    void setUp() {
        campaigns = mock(RewardCampaignRepository.class);
        grants = mock(RewardGrantRepository.class);
        funds = mock(RewardFundAccountRepository.class);
        fundFlows = mock(RewardFundFlowRepository.class);
        users = mock(UserRepository.class);
        wallets = mock(WalletRespository.class);
        walletFlows = mock(WalletFlowRespository.class);
        service = new AdminRewardService(campaigns, grants, funds, fundFlows, users, wallets, walletFlows, null);
    }

    @Test
    void rejectsInvalidTargetBeforeWriting() {
        Map<String, Object> body = Map.of("name", "活动", "target", "未知", "scope", "全部",
                "amountMode", "fixed", "sendMode", "now", "count", 1, "amount", 10);
        assertThrows(IllegalArgumentException.class, () -> service.create(body, 1L, "管理员"));
        verifyNoInteractions(campaigns, users);
    }

    @Test
    void rejectsScheduledTimeInPast() {
        Map<String, Object> body = Map.of("name", "活动", "target", "老板", "scope", "全部",
                "amountMode", "fixed", "sendMode", "timing", "sendAt", "2020-01-01 00:00:00", "count", 1, "amount", 10);
        User boss = new User(); boss.setId(7L); boss.setRole("BOSS");
        when(users.findAll()).thenReturn(java.util.List.of(boss));
        assertThrows(IllegalArgumentException.class, () -> service.create(body, 1L, "管理员"));
        verify(campaigns, never()).save(any());
    }

    @Test
    void zeroBudgetDoesNotBlockRewardInUnlimitedMode() {
        RewardCampaign campaign = new RewardCampaign();
        campaign.setId(2L); campaign.setStatus("待发放"); campaign.setUsers("[7]");
        campaign.setAmountMode("fixed"); campaign.setAmount(1000L);
        RewardFundAccount account = new RewardFundAccount(); account.setBalance(new BigDecimal("9.99"));
        when(campaigns.findById(2L)).thenReturn(Optional.of(campaign));
        when(grants.existsByCampaignIdAndUserId(2L, 7L)).thenReturn(false);
        when(funds.findForUpdate(1L)).thenReturn(Optional.of(account));
        User user = new User(); user.setId(7L); user.setRole("USER");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        Wallet wallet = new Wallet(); wallet.setUserId(7L); wallet.setBalance(BigDecimal.ZERO);
        when(wallets.findAllByUserIdAndRoleForUpdate(7L, "USER")).thenReturn(List.of(wallet));
        when(wallets.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(walletFlows.save(any(WalletFlow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(grants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fundFlows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(campaigns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.execute(2L);
        assertEquals(new BigDecimal("-0.01"), account.getBalance());
        verify(wallets).save(any(Wallet.class));
        verify(walletFlows).save(any(WalletFlow.class));
        verify(fundFlows).save(any());
    }

    @Test
    void missingCampaignIsNotFound() {
        when(campaigns.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.get(99L));
    }

    @Test
    void alreadyDistributedCampaignIsIdempotent() {
        RewardCampaign campaign = new RewardCampaign();
        campaign.setId(1L); campaign.setStatus("已发放");
        when(campaigns.findById(1L)).thenReturn(Optional.of(campaign));
        assertEquals(campaign, service.execute(1L));
        verifyNoInteractions(grants, funds, wallets, walletFlows, fundFlows);
    }

    @Test
    void distributedCampaignCannotBeCanceled() {
        RewardCampaign campaign = new RewardCampaign();
        campaign.setId(3L); campaign.setStatus("已发放");
        when(campaigns.findById(3L)).thenReturn(Optional.of(campaign));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(3L));
        verify(campaigns, never()).save(any());
    }

    @Test
    void bossRecipientsUseEnterpriseIdentityInsteadOfCurrentLoginRole() {
        User certifiedBoss = new User(); certifiedBoss.setId(7L); certifiedBoss.setRole("USER");
        certifiedBoss.setCertType("ENTERPRISE"); certifiedBoss.setCertStatus("已通过");
        User uncertifiedLoginBoss = new User(); uncertifiedLoginBoss.setId(8L); uncertifiedLoginBoss.setRole("BOSS");
        when(users.findAll()).thenReturn(List.of(certifiedBoss, uncertifiedLoginBoss));
        AtomicReference<RewardCampaign> saved = new AtomicReference<>();
        when(campaigns.save(any())).thenAnswer(invocation -> {
            RewardCampaign campaign = invocation.getArgument(0); campaign.setId(10L); saved.set(campaign); return campaign;
        });
        when(campaigns.findById(10L)).thenAnswer(invocation -> Optional.of(saved.get()));
        Map<String, Object> body = Map.of("name", "活动", "target", "老板", "scope", "全部",
                "amountMode", "fixed", "sendMode", "timing", "sendAt", "2099-01-01 00:00:00",
                "count", 10, "amount", 10);

        RewardCampaign campaign = service.create(body, 1L, "管理员");

        assertEquals("[7]", campaign.getUsers());
        assertEquals(1, campaign.getPlannedCount());
    }
}
