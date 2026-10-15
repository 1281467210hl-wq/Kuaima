package com.kuaima.app.wechat.service;

import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient;
import com.kuaima.app.wechat.config.WechatPayProperties;
import com.kuaima.app.wechat.config.WechatProperties;

/** 微信商家转账到零钱 v3 客户端。不在日志或异常中输出 openid、密钥和商户证书信息。 */
@Service
public class WechatMerchantTransferClientImpl implements WechatMerchantTransferClient {
    private static final long REALNAME_REQUIRED_AMOUNT = 200_000L;
    private static final String TRANSFER_BILLS_PATH = "/v3/fund-app/mch-transfer/transfer-bills";
    private static final String LEGACY_TRANSFER_BATCHES_PATH = "/v3/transfer/batches";

    private final WechatPayService payService;
    private final WechatProperties wechat;
    private final WechatPayProperties payProperties;

    public WechatMerchantTransferClientImpl(WechatPayService payService, WechatProperties wechat,
            WechatPayProperties payProperties) {
        this.payService = payService;
        this.wechat = wechat;
        this.payProperties = payProperties;
    }

    @Override
    public MerchantTransferResult transfer(MerchantTransferRequest request) {
        if (request == null || !isValidOutBillNo(request.outBatchNo())
                || !StringUtils.hasText(request.openid()) || request.amount() <= 0) {
            throw new IllegalArgumentException("微信商家转账参数无效");
        }
        if (request.amount() >= REALNAME_REQUIRED_AMOUNT) {
            throw new IllegalArgumentException("当前暂不支持单笔2000元及以上微信提现，请降低金额后重试");
        }
        String remark = StringUtils.hasText(request.remark()) ? request.remark() : "奖励金提现";
        if (remark.length() > 32) throw new IllegalArgumentException("微信转账备注不能超过32个字符");

        JSONObject body = new JSONObject();
        body.put("appid", wechat.getAppid());
        body.put("out_bill_no", request.outBatchNo());
        body.put("transfer_scene_id", requireTransferSceneId());
        body.put("openid", request.openid());
        body.put("transfer_amount", request.amount());
        body.put("transfer_remark", remark);
        body.put("transfer_scene_report_infos", transferSceneReportInfos());
        if (StringUtils.hasText(payProperties.getTransferNotifyUrl())) {
            body.put("notify_url", payProperties.getTransferNotifyUrl().trim());
        }

        JSONObject response = payService.signedPost(
                TRANSFER_BILLS_PATH, body, payProperties.getPlatformPublicKeyId());
        String transferBillNo = response.getString("transfer_bill_no");
        String state = response.getString("state");
        if (!StringUtils.hasText(transferBillNo)) throw new IllegalStateException("微信商家转账未返回转账单号");
        if (!StringUtils.hasText(state)) throw new IllegalStateException("微信商家转账未返回单据状态");
        response.put("app_id", wechat.getAppid());
        response.put("mch_id", payProperties.getMchid());
        return new MerchantTransferResult(
                response.getString("out_bill_no"), transferBillNo, response.getString("create_time"), state,
                response.getString("package_info"), wechat.getAppid(), payProperties.getMchid(),
                response.toJSONString());
    }

    @Override
    public MerchantTransferStatus query(String outBatchNo) {
        if (!isValidOutBillNo(outBatchNo)) throw new IllegalArgumentException("微信商家转账商户单号无效");
        String encodedBatchNo = URLEncoder.encode(outBatchNo, StandardCharsets.UTF_8);
        JSONObject response = payService.signedGet(
                TRANSFER_BILLS_PATH + "/out-bill-no/" + encodedBatchNo);
        String state = response.getString("state");
        if (!StringUtils.hasText(state)) throw new IllegalStateException("微信商家转账查询未返回单据状态");
        return new MerchantTransferStatus(state, response.getString("transfer_bill_no"),
                response.getString("fail_reason"), response.toJSONString());
    }

    @Override
    public MerchantTransferStatus queryLegacy(String outBatchNo) {
        if (!isValidOutBillNo(outBatchNo)) throw new IllegalArgumentException("微信商家转账商户单号无效");
        String encodedBatchNo = URLEncoder.encode(outBatchNo, StandardCharsets.UTF_8);
        JSONObject response = payService.signedGet(
                LEGACY_TRANSFER_BATCHES_PATH + "/out-batch-no/" + encodedBatchNo
                        + "?need_query_detail=true&detail_status=ALL");
        JSONObject batch = response.getJSONObject("transfer_batch");
        if (batch == null) throw new IllegalStateException("微信商家转账查询未返回批次信息");
        var details = response.getJSONArray("transfer_detail_list");
        JSONObject detail = details == null || details.isEmpty() ? null : details.getJSONObject(0);
        String detailStatus = detail == null ? null : detail.getString("detail_status");
        return new MerchantTransferStatus(
                batch.getString("batch_status"), detail == null ? null : detail.getString("detail_id"),
                "FAIL".equals(detailStatus) ? "微信商家转账失败" : null, detailStatus, response.toJSONString());
    }

    private boolean isValidOutBillNo(String value) {
        return StringUtils.hasText(value) && value.trim().length() <= 32 && value.trim().matches("[A-Za-z0-9]+");
    }

    private String requireTransferSceneId() {
        String sceneId = payProperties.getTransferSceneId();
        if (!StringUtils.hasText(sceneId)) throw new IllegalStateException("微信商家转账场景ID未配置");
        return sceneId.trim();
    }

    private List<JSONObject> transferSceneReportInfos() {
        List<com.kuaima.app.wechat.config.WechatPayProperties.TransferSceneReportInfo> configured =
                payProperties.getTransferSceneReportInfos();
        if (configured == null || configured.isEmpty()) {
            throw new IllegalStateException("微信商家转账场景报备信息未配置");
        }
        return configured.stream().map(info -> {
            JSONObject item = new JSONObject();
            item.put("info_type", info.getInfoType());
            item.put("info_content", info.getInfoContent());
            return item;
        }).toList();
    }
}
