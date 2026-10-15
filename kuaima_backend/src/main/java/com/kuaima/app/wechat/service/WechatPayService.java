package com.kuaima.app.wechat.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.wechat.config.WechatProperties;
import com.kuaima.app.wechat.config.WechatPayProperties;

@Service
public class WechatPayService {
    private static final String JSAPI_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";
    private static final String NATIVE_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/native";
    private final WechatPayProperties properties;
    private final WechatProperties wechat;
    private final HttpClient http = HttpClient.newHttpClient();
    private volatile PrivateKey merchantPrivateKey;
    private volatile PublicKey platformPublicKey;

    public WechatPayService(WechatPayProperties properties, WechatProperties wechat) { this.properties = properties; this.wechat = wechat; }

    public JSONObject prepay(String description, String outTradeNo, BigDecimal amount, String openid) {
        requireEnabled();
        if (!StringUtils.hasText(openid)) throw new IllegalArgumentException("用户未绑定微信 openid");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("支付金额无效");
        int fen = amount.movePointRight(2).intValueExact();
        if (fen < 1) throw new IllegalArgumentException("微信支付金额不能低于0.01元");
        JSONObject body = new JSONObject(); body.put("appid", wechat.getAppid()); body.put("mchid", properties.getMchid());
        body.put("description", description); body.put("out_trade_no", outTradeNo); body.put("notify_url", properties.getNotifyUrl());
        body.put("amount", Map.of("total", fen, "currency", "CNY")); body.put("payer", Map.of("openid", openid));
        String payload = body.toJSONString();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(JSAPI_URL)).header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", authorization("POST", "/v3/pay/transactions/jsapi", payload)).POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("微信支付下单失败: " + response.body());
            String prepayId = JSON.parseObject(response.body()).getString("prepay_id");
            if (!StringUtils.hasText(prepayId)) throw new IllegalStateException("微信支付未返回 prepay_id");
            return jsapiParams(prepayId);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("微信支付下单请求被中断", e);
        } catch (IOException e) { throw new IllegalStateException("微信支付下单请求失败", e); }
    }

    /** 创建微信 Native 扫码支付订单，返回微信生成的 code_url。 */
    public String prepayNative(String description, String outTradeNo, BigDecimal amount) {
        requireEnabled();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("支付金额无效");
        int fen = amount.movePointRight(2).intValueExact();
        if (fen < 1) throw new IllegalArgumentException("微信支付金额不能低于0.01元");
        JSONObject body = new JSONObject(); body.put("appid", wechat.getAppid()); body.put("mchid", properties.getMchid());
        body.put("description", description); body.put("out_trade_no", outTradeNo); body.put("notify_url", properties.getNotifyUrl());
        body.put("amount", Map.of("total", fen, "currency", "CNY"));
        String payload = body.toJSONString();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(NATIVE_URL)).header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", authorization("POST", "/v3/pay/transactions/native", payload)).POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("微信支付下单失败: " + response.body());
            String codeUrl = JSON.parseObject(response.body()).getString("code_url");
            if (!StringUtils.hasText(codeUrl)) throw new IllegalStateException("微信支付未返回 code_url");
            return codeUrl;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("微信支付下单请求被中断", e);
        } catch (IOException e) { throw new IllegalStateException("微信支付下单请求失败", e); }
    }

    /** 发起已签名的微信支付 v3 JSON POST 请求；错误信息不携带密钥和完整响应。 */
    public JSONObject signedPost(String path, JSONObject body, String wechatpaySerial) {
        requireEnabled();
        String payload = body.toJSONString();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.mch.weixin.qq.com" + path))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .header("Authorization", authorization("POST", path, payload));
            if (StringUtils.hasText(wechatpaySerial)) builder.header("Wechatpay-Serial", wechatpaySerial);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(errorSummary("微信支付接口请求失败", response.statusCode(), response.body()));
            }
            return JSON.parseObject(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("微信支付接口请求失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("微信支付接口请求被中断", e);
        }
    }

    /** 发起已签名的微信支付 v3 JSON GET 请求；错误信息不携带密钥和完整响应。 */
    public JSONObject signedGet(String path) {
        requireEnabled();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mch.weixin.qq.com" + path))
                    .header("Accept", "application/json")
                    .header("Authorization", authorization("GET", path, ""))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(errorSummary("微信支付接口查询失败", response.statusCode(), response.body()));
            }
            return JSON.parseObject(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("微信支付接口查询失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("微信支付接口查询被中断", e);
        }
    }

    public JSONObject decryptNotify(String timestamp, String nonce, String signature, String serial, String body) {
        requireEnabled();
        if (StringUtils.hasText(properties.getPlatformPublicKeyId()) && !properties.getPlatformPublicKeyId().equals(serial)) throw new IllegalArgumentException("微信平台公钥序列号不匹配");
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(platformKey());
            verifier.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signature))) throw new IllegalArgumentException("微信支付回调签名无效");
            JSONObject resource = JSON.parseObject(body).getJSONObject("resource");
            String aad = resource.getString("associated_data"); String iv = resource.getString("nonce"); byte[] ciphertext = Base64.getDecoder().decode(resource.getString("ciphertext"));
            byte[] apiKey = properties.getApiV3Key().getBytes(StandardCharsets.UTF_8);
            if (apiKey.length != 32) throw new IllegalArgumentException("微信 API v3 Key 必须是 32 字节");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(apiKey, "AES"), new GCMParameterSpec(128, iv.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8)); return JSON.parseObject(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalArgumentException("微信支付回调验签或解密失败", e); }
    }

    static String errorSummary(String action, int statusCode, String responseBody) {
        String code = null;
        String message = null;
        try {
            JSONObject error = JSON.parseObject(responseBody);
            if (error != null) {
                code = error.getString("code");
                message = error.getString("message");
            }
        } catch (RuntimeException ignored) {
            code = null;
            message = null;
        }
        StringBuilder summary = new StringBuilder(action).append(": HTTP ").append(statusCode);
        if (StringUtils.hasText(code)) summary.append(", code=").append(code.trim());
        if (StringUtils.hasText(message)) {
            String text = message.trim();
            summary.append(", message=").append(text.length() > 240 ? text.substring(0, 240) : text);
        }
        if (!StringUtils.hasText(code) && !StringUtils.hasText(message)) summary.append(", 微信未返回业务错误信息");
        return summary.toString();
    }
    public String appId() { return wechat.getAppid(); }
    public String merchantId() { return properties.getMchid(); }

    private JSONObject jsapiParams(String prepayId) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond()); String nonce = UUID.randomUUID().toString().replace("-", ""); String pkg = "prepay_id=" + prepayId;
        String message = wechat.getAppid() + "\n" + timestamp + "\n" + nonce + "\n" + pkg + "\n";
        return new JSONObject(Map.of("appId", wechat.getAppid(), "timeStamp", timestamp, "nonceStr", nonce, "package", pkg, "signType", "RSA", "paySign", sign(message)));
    }
    private String authorization(String method, String path, String body) { String ts = String.valueOf(Instant.now().getEpochSecond()); String nonce = UUID.randomUUID().toString().replace("-", ""); String signature = sign(method + "\n" + path + "\n" + ts + "\n" + nonce + "\n" + body + "\n"); return "WECHATPAY2-SHA256-RSA2048 mchid=\"" + properties.getMchid() + "\",nonce_str=\"" + nonce + "\",timestamp=\"" + ts + "\",serial_no=\"" + properties.getCertSerialNo() + "\",signature=\"" + signature + "\""; }
    private String sign(String message) { try { Signature s = Signature.getInstance("SHA256withRSA"); s.initSign(merchantKey()); s.update(message.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(s.sign()); } catch (Exception e) { throw new IllegalStateException("微信支付请求签名失败", e); } }
    private PrivateKey merchantKey() { if (merchantPrivateKey == null) synchronized (this) { if (merchantPrivateKey == null) merchantPrivateKey = readPrivateKey(properties.getPrivateKeyPath()); } return merchantPrivateKey; }
    private PublicKey platformKey() { if (platformPublicKey == null) synchronized (this) { if (platformPublicKey == null) platformPublicKey = readPublicKey(properties.getPlatformPublicKeyPath()); } return platformPublicKey; }
    private PrivateKey readPrivateKey(String file) { try { String pem = Files.readString(Path.of(file)).replaceAll("-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s", ""); return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))); } catch (Exception e) { throw new IllegalStateException("无法读取微信商户私钥", e); } }
    private PublicKey readPublicKey(String file) { try { String pem = Files.readString(Path.of(file)).replaceAll("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s", ""); return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem))); } catch (Exception e) { throw new IllegalStateException("无法读取微信平台公钥", e); } }
    private void requireEnabled() { if (!properties.isEnabled()) throw new IllegalStateException("微信支付尚未启用"); if (!StringUtils.hasText(wechat.getAppid()) || !StringUtils.hasText(properties.getMchid()) || !StringUtils.hasText(properties.getApiV3Key()) || !StringUtils.hasText(properties.getCertSerialNo()) || !StringUtils.hasText(properties.getPrivateKeyPath())) throw new IllegalStateException("微信支付配置不完整"); }
}
