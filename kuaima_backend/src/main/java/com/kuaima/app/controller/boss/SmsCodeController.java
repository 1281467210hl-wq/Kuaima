package com.kuaima.app.controller.boss;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.BusinessHttpException;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.SmsVerifyCodeService;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 老板端-短信验证码（uniapp 创建员工流程：先发码、后校验）。
 * 登录即可调用，无需企业上下文；真实短信通道未接入，见 {@link SmsVerifyCodeService}。
 */
@RestController
@RequestMapping("/boss/sms")
@Tag(name = "老板-短信验证码", description = "创建员工等场景的验证码发送与校验（内存版，开发模式固定码 123456）")
public class SmsCodeController {

    private final SmsVerifyCodeService smsService;

    public SmsCodeController(SmsVerifyCodeService smsService) {
        this.smsService = smsService;
    }

    @PostMapping("/code")
    @Operation(summary = "发送验证码（body: {phone}；60 秒有效期 + 同手机号 60 秒频控）")
    public Result<Map<String, Object>> sendCode(Authentication auth, @RequestBody Map<String, String> body) {
        requireLogin(auth);
        String phone = body.getOrDefault("phone", "").trim();
        if (!phone.matches("1\\d{10}")) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "请输入正确的11位手机号");
        }
        long expiresIn = smsService.sendCode(phone);
        return Result.success(Map.of(
                "phone", maskPhone(phone),
                "expiresIn", expiresIn,
                "devMode", true));
    }

    @PostMapping("/verify")
    @Operation(summary = "校验验证码（body: {phone, code}；通过后该码销毁；开发模式固定码 123456 亦可通过）")
    public Result<Void> verifyCode(Authentication auth, @RequestBody Map<String, String> body) {
        requireLogin(auth);
        String phone = body.getOrDefault("phone", "").trim();
        if (!phone.matches("1\\d{10}")) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "请输入正确的11位手机号");
        }
        smsService.verifyCode(phone, body.get("code"));
        return Result.success();
    }

    /** 登录即可：SecurityConfig 已对 /boss/** 强制认证，这里仅兜底解析 principal。 */
    private void requireLogin(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser login) || login.id() == null) {
            throw new ForbiddenBusinessException("未登录或Token无效");
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
