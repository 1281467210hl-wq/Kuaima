package com.kuaima.app.domain.reward.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import com.kuaima.app.domain.reward.entity.RewardAccount;
import com.kuaima.app.domain.reward.repository.*;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BossRewardIdentityTests {
    @Test
    void certifiedBossRewardAssetsRemainOwnedByUserId() {
        RewardAccountRepository accounts = mock(RewardAccountRepository.class);
        RewardFlowRepository flows = mock(RewardFlowRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User(); user.setId(7L); user.setRole("USER"); user.setCertType("ENTERPRISE"); user.setCertStatus("已通过");
        RewardAccount account = new RewardAccount(); account.setUserId(7L); account.setBalance(new BigDecimal("20.00"));
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(accounts.findFirstByUserIdAndRoleOrderByIdDesc(7L, "BOSS")).thenReturn(Optional.of(account));
        when(flows.sumByUserIdAndRoleAndType(7L, "BOSS", "INCOME")).thenReturn(new BigDecimal("20.00"));
        when(flows.sumByUserIdAndRoleAndType(7L, "BOSS", "EXPENSE")).thenReturn(BigDecimal.ZERO);
        BossRewardService service = new BossRewardService(accounts, flows, mock(RewardWithdrawalRepository.class), users,
                mock(RewardWithdrawSettingsService.class), mock(WorkerRewardWithdrawalProcessor.class),
                mock(WechatMerchantTransferClient.class));

        var result = service.overview(7L);

        assertEquals(new BigDecimal("20.00"), result.get("balance"));
    }

    @Test
    void unapprovedUserCanReadEmptyRewardOverviewBeforeCertification() {
        RewardAccountRepository accounts = mock(RewardAccountRepository.class);
        RewardFlowRepository flows = mock(RewardFlowRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User(); user.setId(86L); user.setRole("USER");
        when(users.findById(86L)).thenReturn(Optional.of(user));
        BossRewardService service = new BossRewardService(accounts, flows, mock(RewardWithdrawalRepository.class), users,
                mock(RewardWithdrawSettingsService.class), mock(WorkerRewardWithdrawalProcessor.class),
                mock(WechatMerchantTransferClient.class));

        var result = service.overview(86L);

        assertEquals(BigDecimal.ZERO, result.get("balance"));
        assertEquals(BigDecimal.ZERO, result.get("totalIncome"));
        assertEquals(BigDecimal.ZERO, result.get("totalExpense"));
    }
}
