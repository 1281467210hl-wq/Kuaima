package com.kuaima.app.wechat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WechatPayServiceTests {
    @Test
    void errorSummaryIncludesSafeWechatCodeAndMessage() {
        assertEquals("微信支付接口请求失败: HTTP 403, code=NO_AUTH, message=商户无权限",
                WechatPayService.errorSummary("微信支付接口请求失败", 403,
                        "{\"code\":\"NO_AUTH\",\"message\":\"商户无权限\"}"));
    }

    @Test
    void errorSummaryDoesNotExposeMalformedResponseBody() {
        assertEquals("微信支付接口请求失败: HTTP 502, 微信未返回业务错误信息",
                WechatPayService.errorSummary("微信支付接口请求失败", 502, "<html>gateway error</html>"));
    }
}
