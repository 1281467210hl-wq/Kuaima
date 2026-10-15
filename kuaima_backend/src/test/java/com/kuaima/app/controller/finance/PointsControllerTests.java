package com.kuaima.app.controller.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;

class PointsControllerTests {
    private final PointsAccountRepository accounts = mock(PointsAccountRepository.class);
    private final PointsFlowRepository flows = mock(PointsFlowRepository.class);
    private final PointsController controller = new PointsController(accounts, flows);

    @Test
    void balanceUsesCurrentRoleInsteadOfSharedUserAccount() {
        PointsAccount worker = new PointsAccount();
        worker.setUserId(7L);
        worker.setRole(UserRole.USER);
        worker.setBalance(0);
        when(accounts.findFirstByUserIdAndRoleOrderByIdDesc(7L, UserRole.USER)).thenReturn(Optional.of(worker));

        Result<Map<String, Object>> result = controller.getBalance(7L, authentication(7L, UserRole.USER));

        assertEquals(0, result.getData().get("balance"));
        assertEquals(UserRole.USER, result.getData().get("role"));
    }

    @Test
    void flowsAreFilteredByCurrentRole() {
        Pageable pageable = PageRequest.of(0, 20);
        when(flows.findByUserIdAndRoleOrderByTimestampDesc(7L, UserRole.USER, pageable))
                .thenReturn(new PageImpl<>(List.of(new PointsFlow())));

        var result = controller.listFlows(7L, authentication(7L, UserRole.USER), 0, 20);

        assertEquals(1, result.getData().size());
        assertEquals(1L, result.getTotal());
    }

    @Test
    void balanceRejectsOtherUserOrUnsupportedIdentity() {
        assertThrows(ForbiddenBusinessException.class,
                () -> controller.getBalance(8L, authentication(7L, UserRole.USER)));
        assertThrows(ForbiddenBusinessException.class,
                () -> controller.getBalance(7L, authentication(7L, "ADMIN")));
    }

    private Authentication authentication(Long userId, String role) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new LoginUser(userId, "user", role));
        return authentication;
    }
}
