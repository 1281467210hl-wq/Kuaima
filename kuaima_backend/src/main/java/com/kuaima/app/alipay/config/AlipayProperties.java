package com.kuaima.app.alipay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {
    private boolean enabled = false;
    private boolean withdrawEnabled = false;
    private String appId;
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
    private String privateKey;
    private String privateKeyPath;
    private String alipayPublicKey;
    private String appCertPath;
    private String alipayPublicCertPath;
    private String rootCertPath;
    private String signType = "RSA2";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isWithdrawEnabled() { return withdrawEnabled; }
    public void setWithdrawEnabled(boolean withdrawEnabled) { this.withdrawEnabled = withdrawEnabled; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getGatewayUrl() { return gatewayUrl; }
    public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKey = privateKeyPath; }
    public String getAlipayPublicKey() { return alipayPublicKey; }
    public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }
    public String getAppCertPath() { return appCertPath; }
    public void setAppCertPath(String appCertPath) { this.appCertPath = appCertPath; }
    public String getAlipayPublicCertPath() { return alipayPublicCertPath; }
    public void setAlipayPublicCertPath(String alipayPublicCertPath) { this.alipayPublicCertPath = alipayPublicCertPath; }
    public String getRootCertPath() { return rootCertPath; }
    public void setRootCertPath(String rootCertPath) { this.rootCertPath = rootCertPath; }
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
}
