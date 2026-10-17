package com.kuaima.app.controller.boss;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.service.BossOrderService;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.service.EnterpriseContextService;
import com.kuaima.app.domain.jobcategory.service.JobCategoryService;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.service.CertificationService;
import com.kuaima.app.security.model.LoginUser;

class BossOrderPublishEligibilityControllerTests {

    private final BossOrderService orders = mock(BossOrderService.class);
    private final CertificationService certifications = mock(CertificationService.class);
    private final EnterpriseContextService contexts = mock(EnterpriseContextService.class);
    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken(new LoginUser(7L, "boss", "BOSS"), null);

    @Test
    void createOrder_shouldBlockOwnerWithoutRealnameOrEnterpriseCert() {
        when(contexts.require(authentication, "ORDER_CREATE")).thenReturn(context("OWNER"));
        doThrow(new ForbiddenBusinessException("请先完成人脸实名认证和企业认证"))
                .when(certifications).requirePublishEligibility(7L, false);

        assertThrows(ForbiddenBusinessException.class,
                () -> controller().createOrder(new BossOrder(), authentication));

        verify(orders, never()).createOrder(any());
    }

    @Test
    void createOrder_shouldOnlyRequireRealnameForEnterpriseMember() {
        when(contexts.require(authentication, "ORDER_CREATE")).thenReturn(context("RECRUITER"));

        controller().createOrder(new BossOrder(), authentication);

        // 成员的企业认证由老板完成，只校验本人实名
        verify(certifications).requirePublishEligibility(7L, true);
        verify(orders).createOrder(any());
    }

    private BossController controller() {
        return new BossController(orders, mock(JobCategoryService.class), certifications,
                null, null, null, null, null, null, null, contexts, null, null, null);
    }

    private EnterpriseContextService.Context context(String memberRole) {
        User user = new User();
        user.setId(7L);
        Enterprise enterprise = new Enterprise();
        enterprise.setId(3L);
        EnterpriseMember member = new EnterpriseMember();
        member.setMemberRole(memberRole);
        return new EnterpriseContextService.Context(user, enterprise, member);
    }
}
