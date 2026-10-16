package com.kuaima.app.alipay.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayFundTransUniTransferModel;
import com.alipay.api.domain.AlipayFundTransCommonQueryModel;
import com.alipay.api.domain.Participant;
import com.alipay.api.request.AlipayFundTransCommonQueryRequest;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.response.AlipayFundTransCommonQueryResponse;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import com.kuaima.app.alipay.config.AlipayProperties;

@Service
public class AlipayFundTransferClient {
    private final AlipayProperties properties;
    private volatile AlipayClient client;

    public AlipayFundTransferClient(AlipayProperties properties) {
        this.properties = properties;
    }

    public boolean transferEnabled() {
        return properties.isEnabled() && properties.isWithdrawEnabled()
                && StringUtils.hasText(properties.getAppId())
                && privateKey() != null
                && (StringUtils.hasText(properties.getAlipayPublicKey()) || certificateMode());
    }

    public TransferResult transfer(String outBizNo, String account, BigDecimal amount, String remark) {
        if (!transferEnabled()) throw new IllegalStateException("支付宝提现暂未开通");
        if (!StringUtils.hasText(outBizNo)) throw new IllegalArgumentException("支付宝转账单号不能为空");
        if (!StringUtils.hasText(account)) throw new IllegalArgumentException("支付宝账户不能为空");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("支付宝转账金额必须大于0");
        }

        Participant payee = new Participant();
        payee.setIdentity(account.trim());
        payee.setIdentityType("ALIPAY_LOGON_ID");
        AlipayFundTransUniTransferModel model = new AlipayFundTransUniTransferModel();
        model.setOutBizNo(outBizNo);
        model.setProductCode("TRANS_ACCOUNT_NO_PWD");
        model.setBizScene("DIRECT_TRANSFER");
        model.setTransAmount(amount.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
        model.setPayeeInfo(payee);
        model.setRemark(remark);
        AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();
        request.setBizModel(model);

        try {
            AlipayFundTransUniTransferResponse response = certificateMode()
                    ? alipayClient().certificateExecute(request)
                    : alipayClient().execute(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException(StringUtils.hasText(response.getSubMsg())
                        ? response.getSubMsg() : "支付宝转账发起失败");
            }
            return new TransferResult(
                    response.getOrderId(),
                    response.getPayFundOrderId(),
                    response.getStatus(),
                    response.getBody());
        } catch (AlipayApiException exception) {
            throw new IllegalStateException("支付宝转账接口请求失败", exception);
        }
    }

    public QueryResult query(String outBizNo) {
        if (!transferEnabled()) throw new IllegalStateException("支付宝提现暂未开通");
        if (!StringUtils.hasText(outBizNo)) throw new IllegalArgumentException("支付宝转账单号不能为空");

        AlipayFundTransCommonQueryModel model = new AlipayFundTransCommonQueryModel();
        model.setOutBizNo(outBizNo.trim());
        model.setProductCode("TRANS_ACCOUNT_NO_PWD");
        model.setBizScene("DIRECT_TRANSFER");
        AlipayFundTransCommonQueryRequest request = new AlipayFundTransCommonQueryRequest();
        request.setBizModel(model);
        try {
            AlipayFundTransCommonQueryResponse response = certificateMode()
                    ? alipayClient().certificateExecute(request)
                    : alipayClient().execute(request);
            if (!response.isSuccess()) {
                throw new IllegalStateException(StringUtils.hasText(response.getSubMsg())
                        ? response.getSubMsg() : "支付宝转账状态查询失败");
            }
            return new QueryResult(response.getStatus(), response.getFailReason(), response.getBody());
        } catch (AlipayApiException exception) {
            throw new IllegalStateException("支付宝转账状态查询接口请求失败", exception);
        }
    }

    private AlipayClient alipayClient() {
        AlipayClient current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) {
                AlipayConfig config = new AlipayConfig();
                config.setServerUrl(properties.getGatewayUrl());
                config.setAppId(properties.getAppId());
                config.setPrivateKey(privateKey());
                config.setFormat("json");
                config.setCharset("UTF-8");
                config.setSignType(properties.getSignType());
                if (certificateMode()) {
                    config.setAppCertPath(properties.getAppCertPath());
                    config.setAlipayPublicCertPath(properties.getAlipayPublicCertPath());
                    config.setRootCertPath(properties.getRootCertPath());
                } else {
                    config.setAlipayPublicKey(properties.getAlipayPublicKey());
                }
                try {
                    client = new DefaultAlipayClient(config);
                } catch (AlipayApiException exception) {
                    throw new IllegalStateException("支付宝客户端初始化失败", exception);
                }
            }
            return client;
        }
    }

    private boolean certificateMode() {
        return StringUtils.hasText(properties.getAppCertPath())
                && StringUtils.hasText(properties.getAlipayPublicCertPath())
                && StringUtils.hasText(properties.getRootCertPath());
    }

    private String privateKey() {
        if (StringUtils.hasText(properties.getPrivateKey())) return properties.getPrivateKey().trim();
        if (!StringUtils.hasText(properties.getPrivateKeyPath())) return null;
        try {
            return Files.readString(Path.of(properties.getPrivateKeyPath())).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取支付宝应用私钥", exception);
        }
    }

    public record TransferResult(String orderId, String payFundOrderId, String status, String rawResponse) {
    }

    public record QueryResult(String status, String failReason, String rawResponse) {
    }
}
