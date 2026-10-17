package com.kuaima.app.controller.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuaima.app.admin.service.AdminLogService;
import com.kuaima.app.common.BusinessHttpException;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.boss.entity.BossRecruitAccount;
import com.kuaima.app.domain.boss.model.BossProfileModels.AddAccountRequest;
import com.kuaima.app.domain.boss.model.BossProfileModels.AddAccountResponse;
import com.kuaima.app.domain.boss.model.BossProfileModels.AddedAccountView;
import com.kuaima.app.domain.boss.model.BossProfileModels.QuickLoginResponse;
import com.kuaima.app.domain.boss.model.BossProfileModels.SwitchAccountRequest;
import com.kuaima.app.domain.boss.repository.BossRecruitAccountRepository;
import com.kuaima.app.domain.boss.service.BossRecruitAccountService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.security.util.JwtUtil;

class BossRecruitAccountControllerTests {
    @Test
    void switchingAccount_shouldLeaveExactlyOneCurrentAccount() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        BossRecruitAccountController controller = controller(repository);
        BossRecruitAccount first = account(1L, 7L, true);
        BossRecruitAccount second = account(2L, 7L, false);
        when(repository.findByIdAndOwnerUserId(2L, 7L)).thenReturn(Optional.of(second));
        when(repository.findByOwnerUserIdOrderByIdAsc(7L)).thenReturn(List.of(first, second));

        controller.current(new SwitchAccountRequest(2L), bossAuthentication(7L), new MockHttpServletRequest());

        assertFalse(first.getCurrent());
        assertTrue(second.getCurrent());
        verify(repository).saveAll(anyList());
    }

    @Test
    void switchingAccount_shouldUseTypedDtoForJacksonSmallIntegerAccountId() throws Exception {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        BossRecruitAccountController controller = controller(repository);
        SwitchAccountRequest body = new ObjectMapper().readValue("{\"accountId\":2}", SwitchAccountRequest.class);
        BossRecruitAccount selected = account(2L, 7L, false);
        when(repository.findByIdAndOwnerUserId(2L, 7L)).thenReturn(Optional.of(selected));
        when(repository.findByOwnerUserIdOrderByIdAsc(7L)).thenReturn(List.of(selected));

        controller.current(body, bossAuthentication(7L), new MockHttpServletRequest());

        assertEquals(2L, body.accountId());
        assertTrue(selected.getCurrent());
        verify(repository).saveAll(anyList());
    }

    @Test
    void switchingAccount_shouldRejectAnotherBossAccount() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        BossRecruitAccountController controller = controller(repository);
        when(repository.findByIdAndOwnerUserId(9L, 7L)).thenReturn(Optional.empty());
        assertThrows(ForbiddenBusinessException.class, () -> controller.current(
                new SwitchAccountRequest(9L), bossAuthentication(7L), new MockHttpServletRequest()));
    }

    @Test
    void addingAccount_shouldReturnNestedResponseAndWriteAuditLog() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BossRecruitAccountService accountService = mock(BossRecruitAccountService.class);
        AdminLogService logService = mock(AdminLogService.class);
        BossRecruitAccountController controller = new BossRecruitAccountController(
                repository, userRepository, accountService, logService, mock(JwtUtil.class));
        AddedAccountView account = new AddedAccountView(5L, "企业管理员", "", "139****1234",
                "ENTERPRISE", false, false);
        AddAccountResponse response = new AddAccountResponse(account, 1L);
        AddAccountRequest request = new AddAccountRequest("13900131234", "123456", true);
        when(accountService.add(7L, request)).thenReturn(response);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.0.2.1");
        Result<AddAccountResponse> result = controller.add(request, bossAuthentication(7L), httpRequest);

        assertEquals(200, result.getCode());
        assertEquals("账号添加成功", result.getMessage());
        assertEquals(5L, result.getData().account().id());
        assertEquals(1L, result.getData().currentAccountId());
        verify(logService).record(eq("boss"), eq(7L), eq("添加招聘账号"), eq("账号=5"),
                eq("192.0.2.1"), eq("成功"), eq("手机号=139****1234"));
    }

    @Test
    void addingAccount_shouldRejectTooManyRequests() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        BossRecruitAccountService accountService = mock(BossRecruitAccountService.class);
        BossRecruitAccountController controller = new BossRecruitAccountController(repository,
                mock(UserRepository.class), accountService, mock(AdminLogService.class), mock(JwtUtil.class));
        Authentication authentication = bossAuthentication(7L);
        when(accountService.add(any(Long.class), any())).thenReturn(new AddAccountResponse(
                new AddedAccountView(5L, "账号", "", "139****1234", "PERSONAL", false, false), 1L));

        BusinessHttpException error = assertThrows(BusinessHttpException.class, () -> {
            controller.add(new AddAccountRequest("13900131234", "123456", true), authentication,
                    new MockHttpServletRequest());
            controller.add(new AddAccountRequest("13900131234", "123456", true), authentication,
                    new MockHttpServletRequest());
        });

        assertEquals(429, error.getStatus().value());
    }

    @Test
    void list_shouldUseAccountGroupOwnerIdFromQuickLoginToken() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BossRecruitAccountController controller = new BossRecruitAccountController(repository,
                userRepository, mock(BossRecruitAccountService.class), mock(AdminLogService.class),
                mock(JwtUtil.class));
        BossRecruitAccount current = account(1L, 7L, true);
        current.setTargetUserId(31L);
        when(repository.findByOwnerUserIdOrderByIdAsc(7L)).thenReturn(List.of(current));
        when(userRepository.findById(31L)).thenReturn(Optional.of(user(31L)));

        var result = controller.list(bossAuthentication(31L, 7L));

        assertEquals(1L, result.getData().currentAccountId());
        verify(repository).findByOwnerUserIdOrderByIdAsc(7L);
    }

    @Test
    void list_shouldMarkAccountMatchingCurrentJwtUserAsCurrent() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BossRecruitAccountController controller = new BossRecruitAccountController(repository,
                userRepository, mock(BossRecruitAccountService.class), mock(AdminLogService.class),
                mock(JwtUtil.class));
        BossRecruitAccount first = account(1L, 1L, false);
        first.setTargetUserId(null);
        first.setName("章鱼哥");
        BossRecruitAccount second = account(4L, 1L, true);
        second.setTargetUserId(4L);
        when(repository.findByOwnerUserIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));

        var result = controller.list(bossAuthentication(1L));

        assertEquals(1L, result.getData().currentAccountId());
        assertTrue(first.getCurrent());
        assertFalse(second.getCurrent());
        assertEquals("176****1111", result.getData().accounts().get(0).phone());
        verify(repository).saveAll(anyList());
    }

    @Test
    void quickLogin_shouldRepairLegacyOwnerAccountWithoutTargetUserId() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        BossRecruitAccountController controller = new BossRecruitAccountController(repository,
                userRepository, mock(BossRecruitAccountService.class), mock(AdminLogService.class), jwtUtil);
        BossRecruitAccount legacy = account(1L, 1L, false);
        legacy.setTargetUserId(null);
        User owner = user(1L);
        owner.setUsername("zhangyuge");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(owner));
        when(repository.findById(1L)).thenReturn(Optional.of(legacy));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(repository.findByOwnerUserIdOrderByIdAsc(1L)).thenReturn(List.of(legacy));
        when(jwtUtil.generateAccessToken("zhangyuge", UserRole.BOSS, 1L, 1L)).thenReturn("legacy-token");

        Result<QuickLoginResponse> result = controller.quickLogin(1L,
                bossAuthentication(1L), new MockHttpServletRequest());

        assertEquals(1L, legacy.getTargetUserId());
        assertTrue(legacy.getCurrent());
        assertEquals("legacy-token", result.getData().accessToken());
        assertEquals(1L, result.getData().userId());
    }

    @Test
    void quickLogin_shouldUpdateCurrentSignGroupAwareTokenAndAudit() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AdminLogService logService = mock(AdminLogService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        BossRecruitAccountController controller = new BossRecruitAccountController(repository,
                userRepository, mock(BossRecruitAccountService.class), logService, jwtUtil);
        BossRecruitAccount original = account(1L, 7L, true);
        original.setTargetUserId(7L);
        BossRecruitAccount target = account(5L, 7L, false);
        target.setTargetUserId(31L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L)));
        when(userRepository.findById(31L)).thenReturn(Optional.of(user(31L)));
        when(repository.findById(5L)).thenReturn(Optional.of(target));
        when(repository.findByOwnerUserIdOrderByIdAsc(7L)).thenReturn(List.of(original, target));
        when(jwtUtil.generateAccessToken("boss-31", UserRole.BOSS, 31L, 7L)).thenReturn("new-token");

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.0.2.1");
        Result<QuickLoginResponse> result = controller.quickLogin(5L,
                bossAuthentication(31L, 7L), httpRequest);

        assertFalse(original.getCurrent());
        assertTrue(target.getCurrent());
        assertEquals("new-token", result.getData().accessToken());
        assertEquals(31L, result.getData().userId());
        assertEquals("账号切换成功", result.getMessage());
        verify(jwtUtil).generateAccessToken("boss-31", UserRole.BOSS, 31L, 7L);
        verify(logService).record(eq("boss"), eq(31L), eq("快速切换招聘账号"),
                eq("账号=5，目标用户=31"), eq("192.0.2.1"), eq("成功"), any());
    }

    @Test
    void quickLogin_shouldRejectUnlinkedAccount() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BossRecruitAccountController controller = controller(repository, userRepository);
        BossRecruitAccount unlinked = account(9L, 8L, false);
        unlinked.setTargetUserId(31L);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L)));
        when(repository.findById(9L)).thenReturn(Optional.of(unlinked));

        ForbiddenBusinessException error = assertThrows(ForbiddenBusinessException.class,
                () -> controller.quickLogin(9L, bossAuthentication(31L, 7L), new MockHttpServletRequest()));

        assertEquals("账号未关联当前账号组", error.getMessage());
    }

    @Test
    void quickLogin_shouldRejectDisabledTargetWithConflict() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BossRecruitAccountController controller = controller(repository, userRepository);
        BossRecruitAccount linked = account(5L, 7L, false);
        linked.setTargetUserId(31L);
        User disabled = user(31L);
        disabled.setStatus("禁用");
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L)));
        when(repository.findById(5L)).thenReturn(Optional.of(linked));
        when(userRepository.findById(31L)).thenReturn(Optional.of(disabled));

        BusinessHttpException error = assertThrows(BusinessHttpException.class,
                () -> controller.quickLogin(5L, bossAuthentication(31L, 7L), new MockHttpServletRequest()));

        assertEquals(409, error.getStatus().value());
    }

    @Test
    void quickLogin_shouldRepairLegacyNullStatusTarget() {
        BossRecruitAccountRepository repository = mock(BossRecruitAccountRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        BossRecruitAccountController controller = new BossRecruitAccountController(repository,
                userRepository, mock(BossRecruitAccountService.class), mock(AdminLogService.class), jwtUtil);
        BossRecruitAccount original = account(1L, 7L, true);
        original.setTargetUserId(7L);
        BossRecruitAccount target = account(5L, 7L, false);
        target.setTargetUserId(31L);
        User legacyTarget = user(31L);
        legacyTarget.setStatus(null);
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user(7L)));
        when(repository.findById(5L)).thenReturn(Optional.of(target));
        when(userRepository.findById(31L)).thenReturn(Optional.of(legacyTarget));
        when(repository.findByOwnerUserIdOrderByIdAsc(7L)).thenReturn(List.of(original, target));
        when(jwtUtil.generateAccessToken("boss-31", UserRole.BOSS, 31L, 7L)).thenReturn("legacy-token");

        Result<QuickLoginResponse> result = controller.quickLogin(5L,
                bossAuthentication(31L, 7L), new MockHttpServletRequest());

        assertEquals("正常", legacyTarget.getStatus());
        assertEquals("legacy-token", result.getData().accessToken());
        assertTrue(target.getCurrent());
        assertFalse(original.getCurrent());
    }

    private Authentication bossAuthentication(Long id) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new LoginUser(id, "boss", "BOSS"));
        return authentication;
    }

    private Authentication bossAuthentication(Long id, Long accountGroupOwnerId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(
                new LoginUser(id, "boss", "BOSS", accountGroupOwnerId));
        return authentication;
    }

    private BossRecruitAccount account(Long id, Long ownerId, boolean current) {
        BossRecruitAccount account = new BossRecruitAccount();
        account.setId(id);
        account.setOwnerUserId(ownerId);
        account.setTargetUserId(ownerId);
        account.setCurrent(current);
        account.setName("账号" + id);
        return account;
    }

    private BossRecruitAccountController controller(BossRecruitAccountRepository repository) {
        return controller(repository, mock(UserRepository.class));
    }

    private BossRecruitAccountController controller(BossRecruitAccountRepository repository,
                                                    UserRepository userRepository) {
        return new BossRecruitAccountController(repository, userRepository,
                mock(BossRecruitAccountService.class), mock(AdminLogService.class), mock(JwtUtil.class));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("boss-" + id);
        user.setRole(UserRole.BOSS);
        user.setStatus("正常");
        user.setPhone("17611111111");
        user.setCertStatus("已通过");
        return user;
    }
}
