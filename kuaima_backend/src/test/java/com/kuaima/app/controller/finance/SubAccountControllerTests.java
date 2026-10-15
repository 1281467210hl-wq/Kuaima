package com.kuaima.app.controller.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.subaccount.entity.SubAccount;
import com.kuaima.app.domain.subaccount.repository.SubAccountRepository;
import com.kuaima.app.domain.enterprise.entity.Enterprise;
import com.kuaima.app.domain.enterprise.entity.EnterpriseMember;
import com.kuaima.app.domain.enterprise.repository.EnterpriseMemberRepository;
import com.kuaima.app.domain.enterprise.service.EnterpriseContextService;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

class SubAccountControllerTests {
    @Test
    void list_shouldRejectAnotherBossParentId() {
        SubAccountController controller = controller(mock(SubAccountRepository.class), mock(UserRepository.class),
                mock(EnterpriseMemberRepository.class), mock(EnterpriseContextService.class), true);
        assertThrows(ForbiddenBusinessException.class,
                () -> controller.listSubAccounts(9L, bossAuthentication(1L)));
    }

    @Test
    void delete_shouldRejectAnotherBossResource() {
        SubAccountRepository repository = mock(SubAccountRepository.class);
        when(repository.findByIdAndParentId(10L, 1L)).thenReturn(Optional.empty());
        when(repository.existsById(10L)).thenReturn(true);
        SubAccountController controller = controller(repository, mock(UserRepository.class),
                mock(EnterpriseMemberRepository.class), mock(EnterpriseContextService.class), true);
        assertThrows(ForbiddenBusinessException.class,
                () -> controller.deleteSubAccount(10L, bossAuthentication(1L)));
    }

    @Test
    void create_shouldBindExistingEnterpriseMemberWithoutCreatingUser() {
        SubAccountRepository repository = mock(SubAccountRepository.class);
        UserRepository users = mock(UserRepository.class);
        EnterpriseMemberRepository members = mock(EnterpriseMemberRepository.class);
        EnterpriseContextService contextService = mock(EnterpriseContextService.class);
        Enterprise enterprise = new Enterprise(); enterprise.setId(13L);
        EnterpriseMember contextMember = new EnterpriseMember(); contextMember.setEnterpriseId(13L); contextMember.setStatus("ACTIVE");
        User worker = new User(); worker.setId(20L); worker.setPhone("13800000000");
        EnterpriseMember member = new EnterpriseMember(); member.setId(7L); member.setEnterpriseId(13L); member.setUserId(20L); member.setStatus("ACTIVE");
        when(contextService.require(any(), org.mockito.ArgumentMatchers.eq("MEMBER_WRITE")))
                .thenReturn(new EnterpriseContextService.Context(new User(), enterprise, contextMember));
        when(members.findById(7L)).thenReturn(Optional.of(member));
        when(users.findById(20L)).thenReturn(Optional.of(worker));
        when(repository.existsByParentIdAndUserId(1L, 20L)).thenReturn(false);
        when(repository.save(any(SubAccount.class))).thenAnswer(invocation -> { SubAccount s = invocation.getArgument(0); s.setId(10L); return s; });

        var result = controller(repository, users, members, contextService, true).createSubAccount(
                Map.of("memberId", 7L, "code", "123456", "role", "OPERATOR"), bossAuthentication(1L));

        assertEquals(10L, result.getData().getId());
        assertEquals(1L, result.getData().getParentId());
        assertEquals(20L, result.getData().getUserId());
        assertEquals("13800000000", result.getData().getPhone());
        assertEquals("ACTIVE", result.getData().getStatus());
    }

    @Test
    void create_shouldRejectNonMember() {
        EnterpriseMemberRepository members = mock(EnterpriseMemberRepository.class);
        EnterpriseContextService contextService = mock(EnterpriseContextService.class);
        Enterprise enterprise = new Enterprise(); enterprise.setId(13L);
        EnterpriseMember contextMember = new EnterpriseMember(); contextMember.setEnterpriseId(13L); contextMember.setStatus("ACTIVE");
        when(contextService.require(any(), org.mockito.ArgumentMatchers.eq("MEMBER_WRITE")))
                .thenReturn(new EnterpriseContextService.Context(new User(), enterprise, contextMember));
        EnterpriseMember member = new EnterpriseMember(); member.setId(7L); member.setEnterpriseId(99L); member.setUserId(20L); member.setStatus("ACTIVE");
        when(members.findById(7L)).thenReturn(Optional.of(member));
        SubAccountController controller = controller(mock(SubAccountRepository.class), mock(UserRepository.class), members, contextService, true);
        assertThrows(ForbiddenBusinessException.class, () -> controller.createSubAccount(
                Map.of("memberId", 7L, "code", "123456", "role", "OPERATOR"), bossAuthentication(1L)));
    }

    @Test
    void updatePermissions_shouldRejectCrossEnterpriseMember() {
        EnterpriseMemberRepository members = mock(EnterpriseMemberRepository.class);
        EnterpriseContextService contextService = mock(EnterpriseContextService.class);
        Enterprise enterprise = new Enterprise(); enterprise.setId(13L);
        EnterpriseMember contextMember = new EnterpriseMember(); contextMember.setEnterpriseId(13L); contextMember.setStatus("ACTIVE");
        EnterpriseMember member = new EnterpriseMember(); member.setId(7L); member.setEnterpriseId(99L); member.setUserId(20L);
        when(contextService.require(any(), org.mockito.ArgumentMatchers.eq("MEMBER_WRITE")))
                .thenReturn(new EnterpriseContextService.Context(new User(), enterprise, contextMember));
        when(members.findById(7L)).thenReturn(Optional.of(member));
        SubAccountController controller = controller(mock(SubAccountRepository.class), mock(UserRepository.class),
                members, contextService, true);

        ForbiddenBusinessException exception = assertThrows(ForbiddenBusinessException.class,
                () -> controller.updatePermissions(7L, Map.of(), bossAuthentication(1L)));

        assertEquals("该成员不属于当前企业，请从当前企业成员列表中选择", exception.getMessage());
    }

    @Test
    void updatePermissions_shouldRejectSelfModification() {
        EnterpriseMemberRepository members = mock(EnterpriseMemberRepository.class);
        EnterpriseContextService contextService = mock(EnterpriseContextService.class);
        Enterprise enterprise = new Enterprise(); enterprise.setId(13L);
        EnterpriseMember contextMember = new EnterpriseMember(); contextMember.setEnterpriseId(13L); contextMember.setStatus("ACTIVE");
        EnterpriseMember member = new EnterpriseMember(); member.setId(7L); member.setEnterpriseId(13L); member.setUserId(1L);
        when(contextService.require(any(), org.mockito.ArgumentMatchers.eq("MEMBER_WRITE")))
                .thenReturn(new EnterpriseContextService.Context(new User(), enterprise, contextMember));
        when(members.findById(7L)).thenReturn(Optional.of(member));
        SubAccountController controller = controller(mock(SubAccountRepository.class), mock(UserRepository.class),
                members, contextService, true);

        ForbiddenBusinessException exception = assertThrows(ForbiddenBusinessException.class,
                () -> controller.updatePermissions(7L, Map.of(), bossAuthentication(1L)));

        assertEquals("不能修改当前登录老板自己的权限", exception.getMessage());
    }

    private SubAccountController controller(SubAccountRepository repository, UserRepository users,
                                            EnterpriseMemberRepository members,
                                            EnterpriseContextService contextService, boolean dev) {
        PasswordEncoder encoder = mock(PasswordEncoder.class); when(encoder.encode(any())).thenReturn("encoded");
        Environment environment = mock(Environment.class); when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(dev);
        when(environment.getProperty("aliyun.sms.mock-enabled", "false")).thenReturn(Boolean.toString(dev));
        return new SubAccountController(repository, users, members, contextService, environment, "123456");
    }

    private Authentication bossAuthentication(Long id) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new LoginUser(id, "boss", "BOSS"));
        return authentication;
    }
}
