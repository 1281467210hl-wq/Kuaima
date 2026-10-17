package com.kuaima.app.controller.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import com.kuaima.app.admin.repository.CertificationRepository;
import com.kuaima.app.domain.user.entity.CreditFlow;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.CreditFlowRepository;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.service.CertificationService;

class UserControllerCreditFlowTests {
    @Test
    void creditFlowEndpointReturnsChineseRuleNameFromProductRules() {
        UserRepository users = mock(UserRepository.class);
        CreditFlowRepository flows = mock(CreditFlowRepository.class);
        User user = new User();
        user.setId(11L);
        CreditFlow flow = new CreditFlow();
        flow.setRuleCode("WORKER_FIVE_STAR_REVIEW");
        flow.setDelta(100);
        when(users.findById(11L)).thenReturn(Optional.of(user));
        when(flows.findByUserIdOrderByTimestampDesc(any(), any())).thenReturn(new PageImpl<>(List.of(flow)));

        UserController controller = new UserController(users, flows,
                mock(CertificationRepository.class), mock(CertificationService.class));
        var result = controller.listCreditFlows(11L, 0, 5);

        assertEquals("好评星级分", result.getData().get(0).get("ruleCode"));
    }
}
