package com.kuaima.app.wechat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferRequest;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferResult;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient.MerchantTransferStatus;
import com.kuaima.app.wechat.config.WechatPayProperties;
import com.kuaima.app.wechat.config.WechatProperties;

class WechatMerchantTransferClientImplTests {
    private WechatPayService payService;
    private WechatMerchantTransferClientImpl client;

    @BeforeEach
    void setUp() {
        payService = mock(WechatPayService.class);
        WechatProperties wechat = new WechatProperties();
        wechat.setAppid("wx-app-id");
        WechatPayProperties payProperties = new WechatPayProperties();
        payProperties.setMchid("1900000001");
        payProperties.setTransferSceneId("1000");
        payProperties.setTransferSceneReportInfos(List.of(
                reportInfo("活动名称", "快马日结奖励活动"),
                reportInfo("奖励说明", "平台奖励提现")));
        client = new WechatMerchantTransferClientImpl(payService, wechat, payProperties);
    }

    @Test
    void transferUsesUpgradedMerchantTransferApi() {
        JSONObject response = new JSONObject();
        response.put("out_bill_no", "RWB123456789012345678901234");
        response.put("transfer_bill_no", "bill-1");
        response.put("create_time", "2026-10-15T22:00:00+08:00");
        response.put("state", "WAIT_USER_CONFIRM");
        response.put("package_info", "package-info");
        when(payService.signedPost(anyString(), any(JSONObject.class), nullable(String.class))).thenReturn(response);

        MerchantTransferResult result = client.transfer(request(100L));

        ArgumentCaptor<JSONObject> bodyCaptor = ArgumentCaptor.forClass(JSONObject.class);
        verify(payService).signedPost(
                org.mockito.ArgumentMatchers.eq("/v3/fund-app/mch-transfer/transfer-bills"),
                bodyCaptor.capture(), nullable(String.class));
        JSONObject body = bodyCaptor.getValue();
        assertEquals("wx-app-id", body.getString("appid"));
        assertEquals("RWB123456789012345678901234", body.getString("out_bill_no"));
        assertEquals("1000", body.getString("transfer_scene_id"));
        assertEquals(100, body.getIntValue("transfer_amount"));
        assertEquals(2, body.getJSONArray("transfer_scene_report_infos").size());
        assertEquals("WAIT_USER_CONFIRM", result.state());
        assertEquals("package-info", result.packageInfo());
        assertEquals("wx-app-id", result.appId());
        assertEquals("1900000001", result.mchId());
    }

    @Test
    void queryUsesMerchantBillNoEndpoint() {
        JSONObject response = new JSONObject();
        response.put("state", "FAIL");
        response.put("transfer_bill_no", "bill-1");
        response.put("fail_reason", "PAYEE_ACCOUNT_ABNORMAL");
        when(payService.signedGet(anyString())).thenReturn(response);

        MerchantTransferStatus status = client.query("RWB123456789012345678901234");

        verify(payService).signedGet(
                "/v3/fund-app/mch-transfer/transfer-bills/out-bill-no/RWB123456789012345678901234");
        assertEquals("FAIL", status.state());
        assertEquals("bill-1", status.transferBillNo());
        assertEquals("PAYEE_ACCOUNT_ABNORMAL", status.failReason());
    }

    @Test
    void legacyQueryRemainsAvailableForHistoricalPendingWithdrawals() {
        JSONObject batch = new JSONObject();
        batch.put("batch_status", "FINISHED");
        JSONObject response = new JSONObject();
        response.put("transfer_batch", batch);
        response.put("transfer_detail_list", List.of(new JSONObject()
                .fluentPut("detail_status", "SUCCESS")
                .fluentPut("detail_id", "detail-1")));
        when(payService.signedGet(anyString())).thenReturn(response);

        MerchantTransferStatus status = client.queryLegacy("RWB123456789012345678901234");

        verify(payService).signedGet("/v3/transfer/batches/out-batch-no/RWB123456789012345678901234"
                + "?need_query_detail=true&detail_status=ALL");
        assertEquals("FINISHED", status.state());
        assertEquals("detail-1", status.transferBillNo());
        assertEquals("SUCCESS", status.detailStatus());
    }

    @Test
    void rejectLargeAmountBeforeCreatingTransfer() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> client.transfer(request(200_000L)));

        assertEquals("当前暂不支持单笔2000元及以上微信提现，请降低金额后重试", exception.getMessage());
    }

    private MerchantTransferRequest request(long amount) {
        return new MerchantTransferRequest(
                "RWB123456789012345678901234", "RWD123456789012345678901234",
                amount, "openid-1", "快马日结奖励金提现");
    }

    private WechatPayProperties.TransferSceneReportInfo reportInfo(String type, String content) {
        WechatPayProperties.TransferSceneReportInfo info = new WechatPayProperties.TransferSceneReportInfo();
        info.setInfoType(type);
        info.setInfoContent(content);
        return info;
    }
}
