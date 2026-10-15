package com.kuaima.app.controller.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.reward.entity.Reward;
import com.kuaima.app.domain.reward.repository.RewardExchangeRepository;
import com.kuaima.app.domain.reward.repository.RewardRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;

class RewardControllerRoleIsolationTests {
    private final RewardRepository rewards = mock(RewardRepository.class);
    private final RewardExchangeRepository exchanges = mock(RewardExchangeRepository.class);
    private final PointsAccountRepository accounts = mock(PointsAccountRepository.class);
    private final PointsFlowRepository flows = mock(PointsFlowRepository.class);
    private final RewardController controller = new RewardController(rewards, exchanges, accounts, flows);

    @Test
    void exchangeUsesWorkerPointsWhenSameUserAlsoHasBossPoints() {
        Reward reward = new Reward();
        reward.setTitle("礼物");
        reward.setPointsCost(100);
        reward.setStock(2);
        PointsAccount workerAccount = new PointsAccount();
        workerAccount.setUserId(7L);
        workerAccount.setRole(UserRole.USER);
        workerAccount.setBalance(100);
        when(rewards.findById(1L)).thenReturn(Optional.of(reward));
        when(accounts.findAllByUserIdAndRoleForUpdate(7L, UserRole.USER))
                .thenReturn(java.util.List.of(workerAccount));
        when(exchanges.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.exchange(1L, 7L, authentication(7L, UserRole.USER));

        assertEquals(0, workerAccount.getBalance());
        ArgumentCaptor<PointsFlow> captor = ArgumentCaptor.forClass(PointsFlow.class);
        verify(flows).save(captor.capture());
        assertEquals(UserRole.USER, captor.getValue().getRole());
        assertEquals(0, captor.getValue().getBalanceAfter());
    }

    @Test
    void exchangeRejectsBossIdentity() {
        assertThrows(ForbiddenBusinessException.class,
                () -> controller.exchange(1L, 7L, authentication(7L, UserRole.BOSS)));
    }

    private Authentication authentication(Long userId, String role) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new LoginUser(userId, "user", role));
        return authentication;
    }
}
