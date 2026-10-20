package com.kuaima.app.controller.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;
import com.kuaima.app.security.util.JwtUtil;
import com.kuaima.app.service.SmsService;
import com.kuaima.app.wechat.service.WechatService;

class AuthControllerSmsLoginTests {

    private static final String TEST_PHONE = "13900130001";

    private UserRepository users;
    private JwtUtil jwtUtil;
    private SmsService sms;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.generateAccessToken(anyString(), anyString(), anyLong())).thenReturn("access-token");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) user.setId(100L);
            return user;
        });
        sms = new SmsService();
        ReflectionTestUtils.setField(sms, "mockEnabled", true);
        ReflectionTestUtils.setField(sms, "mockCode", "000000");
        ReflectionTestUtils.setField(sms, "mockLoginPhones", TEST_PHONE);
        ReflectionTestUtils.setField(sms, "codeExpiration", 300);
        ReflectionTestUtils.setField(sms, "resendInterval", -1);
        ReflectionTestUtils.setField(sms, "dailyLimit", 10);
        controller = new AuthController(users, mock(PasswordEncoder.class), jwtUtil, mock(WechatService.class), sms);
    }

    @Test
    void smsLoginShouldRejectNonWhitelistedPhoneInMockMode() {
        // 固定验证码模式下，真实用户手机号不能被「000000」登录，否则任何人都能登录他人账号
        var result = controller.smsLogin(new AuthController.SmsLoginRequest("13900139999", "000000", "USER"));

        assertEquals(403, result.getCode());
        verify(users, never()).findByPhone(anyString());
    }

    @Test
    void smsLoginShouldSignInExistingAccountWithoutChangingRealnameStatus() {
        User existing = user(7L, CertificationStatus.UNVERIFIED);
        when(users.findByPhone(TEST_PHONE)).thenReturn(List.of(existing));
        sms.sendCode(TEST_PHONE);

        var result = controller.smsLogin(new AuthController.SmsLoginRequest(TEST_PHONE, "000000", "BOSS"));

        assertEquals(200, result.getCode());
        Map<String, Object> data = result.getData();
        assertEquals("access-token", data.get("accessToken"));
        assertEquals(7L, data.get("userId"));
        assertEquals(UserRole.BOSS, data.get("role"));
        // 短信只证明手机号归属，不能等同于实名认证
        assertEquals(CertificationStatus.UNVERIFIED, existing.getRealnameStatus());
        verify(users, never()).save(existing);
    }

    @Test
    void smsLoginShouldRegisterNewPhoneAsWorker() {
        when(users.findByPhone(TEST_PHONE)).thenReturn(List.of());
        sms.sendCode(TEST_PHONE);

        var result = controller.smsLogin(new AuthController.SmsLoginRequest(TEST_PHONE, "000000", "BOSS"));

        assertEquals(200, result.getCode());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(TEST_PHONE, saved.getValue().getPhone());
        // 持久化身份从零工开始，BOSS 只代表本次登录端
        assertEquals(UserRole.USER, saved.getValue().getRole());
        assertEquals("正常", saved.getValue().getStatus());
        assertNull(saved.getValue().getOpenid());
    }

    @Test
    void smsLoginShouldRejectWrongCode() {
        sms.sendCode(TEST_PHONE);

        var result = controller.smsLogin(new AuthController.SmsLoginRequest(TEST_PHONE, "123456", "USER"));

        assertEquals(400, result.getCode());
        assertEquals("验证码错误", result.getMessage());
    }

    @Test
    void smsLoginShouldRejectCancelledAccount() {
        User cancelled = user(8L, CertificationStatus.UNVERIFIED);
        cancelled.setStatus("注销");
        when(users.findByPhone(TEST_PHONE)).thenReturn(List.of(cancelled));
        sms.sendCode(TEST_PHONE);

        var result = controller.smsLogin(new AuthController.SmsLoginRequest(TEST_PHONE, "000000", "USER"));

        assertEquals(403, result.getCode());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void cancelShouldOnlyCancelCurrentLoginAccount() {
        User me = user(7L, CertificationStatus.UNVERIFIED);
        when(users.findById(7L)).thenReturn(Optional.of(me));
        var login = new UsernamePasswordAuthenticationToken(new LoginUser(7L, "user7", "USER"), null);

        // 传别人的 userId：拒绝（原接口任何已登录用户都能注销他人账号）
        assertEquals(403, controller.cancel(8L, "恶意注销", login).getCode());
        verify(users, never()).save(any(User.class));

        var result = controller.cancel(null, "用户主动注销", login);

        assertEquals(200, result.getCode());
        assertEquals("注销", me.getStatus());
    }

    @Test
    void cancelShouldRequireLogin() {
        assertEquals(401, controller.cancel(7L, null, null).getCode());
        verify(users, never()).findById(anyLong());
    }

    private User user(Long id, String realnameStatus) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setPhone(TEST_PHONE);
        user.setStatus("正常");
        user.setRealnameStatus(realnameStatus);
        return user;
    }
}
