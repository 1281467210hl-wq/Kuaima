package com.kuaima.app.wechat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WechatPayProperties {
    public static class TransferSceneReportInfo {
        private String infoType;
        private String infoContent;
        public String getInfoType() { return infoType; }
        public void setInfoType(String infoType) { this.infoType = infoType; }
        public String getInfoContent() { return infoContent; }
        public void setInfoContent(String infoContent) { this.infoContent = infoContent; }
    }

    private String mchid;
    private String apiV3Key;
    private String certSerialNo;
    private String privateKeyPath;
    private String platformPublicKeyPath;
    private String platformPublicKeyId;
    private String notifyUrl = "https://ke.taifang.xyz/api/pay/wechat/notify";
    private String transferSceneId = "1000";
    private java.util.List<TransferSceneReportInfo> transferSceneReportInfos = java.util.List.of();
    private String transferNotifyUrl;
    private boolean enabled = false;
    public String getMchid() { return mchid; }
    public void setMchid(String mchid) { this.mchid = mchid; }
    public String getApiV3Key() { return apiV3Key; }
    public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }
    public String getCertSerialNo() { return certSerialNo; }
    public void setCertSerialNo(String certSerialNo) { this.certSerialNo = certSerialNo; }
    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
    public String getPlatformPublicKeyPath() { return platformPublicKeyPath; }
    public void setPlatformPublicKeyPath(String platformPublicKeyPath) { this.platformPublicKeyPath = platformPublicKeyPath; }
    public String getPlatformPublicKeyId() { return platformPublicKeyId; }
    public void setPlatformPublicKeyId(String platformPublicKeyId) { this.platformPublicKeyId = platformPublicKeyId; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getTransferSceneId() { return transferSceneId; }
    public void setTransferSceneId(String transferSceneId) { this.transferSceneId = transferSceneId; }
    public java.util.List<TransferSceneReportInfo> getTransferSceneReportInfos() { return transferSceneReportInfos; }
    public void setTransferSceneReportInfos(java.util.List<TransferSceneReportInfo> transferSceneReportInfos) {
        this.transferSceneReportInfos = transferSceneReportInfos == null
                ? java.util.List.of() : transferSceneReportInfos;
    }
    public String getTransferNotifyUrl() { return transferNotifyUrl; }
    public void setTransferNotifyUrl(String transferNotifyUrl) { this.transferNotifyUrl = transferNotifyUrl; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
