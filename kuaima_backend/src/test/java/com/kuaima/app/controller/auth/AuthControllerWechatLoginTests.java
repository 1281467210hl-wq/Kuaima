package com.kuaima.app.controller.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.dto.WechatLoginDto;
import com.kuaima.app.security.util.JwtUtil;
import com.kuaima.app.service.SmsService;
import com.kuaima.app.wechat.service.WechatService;
import com.kuaima.app.wechat.service.WechatService.WechatUserInfo;

class AuthControllerWechatLoginTests {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private WechatService wechatService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(JwtUtil.class);
        wechatService = mock(WechatService.class);
        controller = new AuthController(userRepository, passwordEncoder, jwtUtil,
                wechatService, mock(SmsService.class));
        when(wechatService.loginByCode("login-code"))
                .thenReturn(new WechatUserInfo("openid-1", null, null, null));
        when(jwtUtil.generateAccessToken(any(), any(), any())).thenReturn("access-token");
    }

    @Test
    void cancelledUser_shouldNotLoginAgainByWechat() {
        User cancelled = user(10L, "13800000000");
        cancelled.setStatus("注销");
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.of(cancelled));

        var result = controller.wechatLogin(dto(null));

        // 注销后不能再用微信登录拿到新 token，否则注销形同虚设
        assertEquals(403, result.getCode());
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void oldUser_shouldLoginDirectly() {
        User oldUser = user(10L, "13800000000");
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.of(oldUser));

        var result = controller.wechatLogin(dto(null));

        assertFalse((Boolean) result.getData().get("needPhoneNumber"));
        assertEquals("access-token", result.getData().get("accessToken"));
        assertEquals(10L, result.getData().get("userId"));
        assertEquals(UserRole.USER, result.getData().get("role"));
        assertEquals("13800000000", result.getData().get("phone"));
        verify(wechatService, never()).getPhoneNumber(any());
    }

    @Test
    void oldUserWithoutPhone_shouldStillLoginDirectly() {
        User oldUser = user(11L, null);
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.of(oldUser));

        var result = controller.wechatLogin(dto("unused-phone-code"));

        assertFalse((Boolean) result.getData().get("needPhoneNumber"));
        assertEquals("access-token", result.getData().get("accessToken"));
        assertNull(result.getData().get("phone"));
        verify(wechatService, never()).getPhoneNumber(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void newUserWithoutPhoneCode_shouldRequestPhoneAuthorization() {
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(jwtUtil.generateWechatRegistrationToken("openid-1"))
                .thenReturn("registration-token");

        var result = controller.wechatLogin(dto(null));

        assertTrue((Boolean) result.getData().get("needPhoneNumber"));
        assertEquals("registration-token", result.getData().get("registrationToken"));
        assertFalse(result.getData().containsKey("accessToken"));
        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void registrationContinuation_shouldUseTokenWithoutExchangingLoginCodeAgain() {
        when(jwtUtil.getWechatRegistrationOpenid("registration-token"))
                .thenReturn("openid-1");
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(wechatService.getPhoneNumber("phone-code")).thenReturn("13900000000");
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        WechatLoginDto dto = registrationDto("registration-token", "phone-code");
        var result = controller.wechatLogin(dto);

        assertEquals(200, result.getCode());
        assertFalse((Boolean) result.getData().get("needPhoneNumber"));
        assertEquals(12L, result.getData().get("userId"));
        verify(wechatService, never()).loginByCode(any());
        verify(wechatService).getPhoneNumber("phone-code");
    }

    @Test
    void invalidRegistrationToken_shouldRequireFreshLogin() {
        when(jwtUtil.getWechatRegistrationOpenid("invalid-token"))
                .thenThrow(new IllegalArgumentException("invalid"));

        var result = controller.wechatLogin(registrationDto("invalid-token", "phone-code"));

        assertEquals(400, result.getCode());
        assertEquals("注册凭证无效或已过期，请重新登录", result.getMessage());
        verify(wechatService, never()).loginByCode(any());
        verify(wechatService, never()).getPhoneNumber(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registrationContinuationWithoutPhoneCode_shouldBeRejected() {
        var result = controller.wechatLogin(registrationDto("registration-token", null));

        assertEquals(400, result.getCode());
        assertEquals("手机号授权 code 不能为空", result.getMessage());
        verify(jwtUtil, never()).getWechatRegistrationOpenid(any());
        verify(wechatService, never()).loginByCode(any());
    }

    @Test
    void newUserWithPhoneCode_shouldRegisterAndLogin() {
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(wechatService.getPhoneNumber("phone-code")).thenReturn("13900000000");
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        var result = controller.wechatLogin(dto("phone-code"));

        assertFalse((Boolean) result.getData().get("needPhoneNumber"));
        assertEquals("access-token", result.getData().get("accessToken"));
        assertEquals(12L, result.getData().get("userId"));
        assertEquals(UserRole.USER, result.getData().get("role"));
        assertEquals("13900000000", result.getData().get("phone"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void newWorkerWithoutNickname_shouldReceiveIncrementedDefaultNickname() {
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(userRepository.findByRole(UserRole.USER)).thenReturn(java.util.List.of(userWithNickname(3L, UserRole.USER, "零工01"), userWithNickname(4L, UserRole.USER, "零工03")));
        when(wechatService.getPhoneNumber("phone-code")).thenReturn("13900000000");
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        controller.wechatLogin(dto("phone-code"));
        verify(userRepository).save(argThat(u -> "零工04".equals(u.getNickname())));
    }

    @Test
    void newBossLoginWithoutNickname_shouldStillReceiveWorkerDefaultNickname() {
        when(wechatService.loginByCode("login-code")).thenReturn(new WechatUserInfo("openid-boss", null, null, null));
        when(userRepository.findByOpenid("openid-boss")).thenReturn(Optional.empty());
        when(userRepository.findByRole(UserRole.USER)).thenReturn(java.util.List.of(userWithNickname(5L, UserRole.USER, "零工02")));
        when(wechatService.getPhoneNumber("phone-code")).thenReturn("13900000000");
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WechatLoginDto dto = dto("phone-code"); dto.setRole(UserRole.BOSS);
        controller.wechatLogin(dto);
        verify(userRepository).save(argThat(u -> "零工03".equals(u.getNickname()) && UserRole.USER.equals(u.getRole())));
    }

    @Test
    void authorizedEmployee_shouldBindWechatAndLoginBossAccountByOwnPhone() {
        User employee = new User();
        employee.setId(20L);
        employee.setUsername("sub_20");
        employee.setRole(UserRole.BOSS);
        employee.setPhone("13900000000");
        employee.setNickname("王娜");
        employee.setParentUserId(10L);
        employee.setSubRole("OPERATOR");
        employee.setStatus("正常");
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(wechatService.getPhoneNumber("phone-code")).thenReturn("13900000000");
        when(userRepository.findUnboundActiveSubAccountsByPhone("13900000000"))
                .thenReturn(java.util.List.of(employee));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WechatLoginDto dto = dto("phone-code");
        dto.setRole(UserRole.BOSS);
        var result = controller.wechatLogin(dto);

        assertEquals(200, result.getCode());
        assertEquals(20L, result.getData().get("userId"));
        assertEquals(UserRole.BOSS, result.getData().get("role"));
        assertEquals("openid-1", employee.getOpenid());
        verify(userRepository).save(employee);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).findByRole(any());
    }

    @Test
    void multipleAuthorizedEmployeeAccounts_shouldRejectAmbiguousPhoneLogin() {
        User first = authorizedEmployee(20L, "13900000000");
        User second = authorizedEmployee(21L, "13900000000");
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(wechatService.getPhoneNumber("phone-code")).thenReturn("13900000000");
        when(userRepository.findUnboundActiveSubAccountsByPhone("13900000000"))
                .thenReturn(java.util.List.of(first, second));

        WechatLoginDto dto = dto("phone-code");
        dto.setRole(UserRole.BOSS);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> controller.wechatLogin(dto));

        assertEquals("该手机号关联多个老板授权账号，请联系老板确认后重新授权", error.getMessage());
        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void phoneAuthorizationFailure_shouldNotCreateUser() {
        when(userRepository.findByOpenid("openid-1")).thenReturn(Optional.empty());
        when(wechatService.getPhoneNumber("bad-phone-code"))
                .thenThrow(new RuntimeException("wechat error"));

        var result = controller.wechatLogin(dto("bad-phone-code"));

        assertEquals(400, result.getCode());
        assertEquals("手机号授权失败", result.getMessage());
        verify(userRepository, never()).save(any());
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    void repeatedRequest_shouldNotRegisterTwice() {
        AtomicReference<User> storedUser = new AtomicReference<>();
        when(userRepository.findByOpenid("openid-1"))
                .thenAnswer(invocation -> Optional.ofNullable(storedUser.get()));
        when(wechatService.getPhoneNumber("phone-code")).thenReturn("13900000000");
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(13L);
            storedUser.set(saved);
            return saved;
        });

        var first = controller.wechatLogin(dto("phone-code"));
        var second = controller.wechatLogin(dto("phone-code"));

        assertEquals(13L, first.getData().get("userId"));
        assertEquals(13L, second.getData().get("userId"));
        verify(userRepository, times(1)).save(any(User.class));
        verify(wechatService, times(1)).getPhoneNumber("phone-code");
    }

    private WechatLoginDto dto(String phoneCode) {
        WechatLoginDto dto = new WechatLoginDto();
        dto.setCode("login-code");
        dto.setRole(UserRole.USER);
        dto.setPhoneCode(phoneCode);
        return dto;
    }

    private WechatLoginDto registrationDto(String registrationToken, String phoneCode) {
        WechatLoginDto dto = new WechatLoginDto();
        dto.setRegistrationToken(registrationToken);
        dto.setRole(UserRole.USER);
        dto.setPhoneCode(phoneCode);
        return dto;
    }

    private User user(Long id, String phone) {
        User user = new User();
        user.setId(id);
        user.setUsername("wx_openid-1");
        user.setRole(UserRole.USER);
        user.setOpenid("openid-1");
        user.setPhone(phone);
        return user;
    }

    private User userWithNickname(Long id, String role, String nickname) {
        User user = user(id, null); user.setRole(role); user.setNickname(nickname); return user;
    }

    private User authorizedEmployee(Long id, String phone) {
        User user = new User();
        user.setId(id);
        user.setUsername("sub_" + id);
        user.setRole(UserRole.BOSS);
        user.setPhone(phone);
        user.setParentUserId(10L);
        user.setStatus("正常");
        return user;
    }
}
