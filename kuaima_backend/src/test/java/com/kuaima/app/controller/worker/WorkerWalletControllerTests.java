package com.kuaima.app.controller.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.wallet.entity.Wallet;
import com.kuaima.app.domain.wallet.entity.WithDraw;
import com.kuaima.app.domain.wallet.service.WalletService;
import com.kuaima.app.domain.wallet.service.WorkerWalletWechatWithdrawalService;
import com.kuaima.app.security.model.LoginUser;

class WorkerWalletControllerTests {
    @Test
    void walletUsesJwtUserIdInsteadOfCachedFrontendUserId() {
        WalletService wallets = mock(WalletService.class);
        Wallet wallet = new Wallet();
        wallet.setUserId(66L);
        wallet.setRole("USER");
        wallet.setBalance(new BigDecimal("128.50"));
        when(wallets.getOrCreateWallet(66L, "USER")).thenReturn(wallet);
        WorkerWalletController controller = new WorkerWalletController(
                mock(WorkerWalletWechatWithdrawalService.class), wallets);

        var result = controller.wallet(authentication(66L));

        assertEquals(66L, result.getData().get("userId"));
        assertEquals(new BigDecimal("128.50"), result.getData().get("balance"));
    }

    @Test
    void withdrawRecordsUseJwtUserId() {
        WalletService wallets = mock(WalletService.class);
        List<WithDraw> records = List.of(new WithDraw());
        when(wallets.listWithdraws(66L)).thenReturn(records);
        WorkerWalletController controller = new WorkerWalletController(
                mock(WorkerWalletWechatWithdrawalService.class), wallets);

        var result = controller.withdrawRecords(authentication(66L));

        assertEquals(records, result.getData());
        verify(wallets).listWithdraws(66L);
    }

    @Test
    void rejectBossIdentityForWorkerWallet() {
        WorkerWalletController controller = new WorkerWalletController(
                mock(WorkerWalletWechatWithdrawalService.class), mock(WalletService.class));

        assertThrows(ForbiddenBusinessException.class,
                () -> controller.wallet(authentication("BOSS")));
    }

    private Authentication authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(new LoginUser(userId, "worker", "USER"), null);
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(new LoginUser(7L, "boss", role), null);
    }
}
