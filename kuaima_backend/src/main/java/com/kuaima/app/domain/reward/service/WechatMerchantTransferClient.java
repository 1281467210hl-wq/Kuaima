package com.kuaima.app.domain.reward.service;

/** 微信商家转账出站端口，便于服务层隔离微信 API 细节并测试失败退款。 */
public interface WechatMerchantTransferClient {
    MerchantTransferResult transfer(MerchantTransferRequest request);

    MerchantTransferStatus query(String outBatchNo);

    MerchantTransferStatus queryLegacy(String outBatchNo);

    /**
     * outDetailNo 仅兼容旧调用方保留；升级版商家转账使用 outBillNo 作为唯一商户单号。
     */
    record MerchantTransferRequest(
            String outBatchNo,
            String outDetailNo,
            long amount,
            String openid,
            String remark) {
    }

    record MerchantTransferResult(
            String outBatchNo,
            String transferBillNo,
            String createTime,
            String state,
            String packageInfo,
            String appId,
            String mchId,
            String rawResponse) {

        public MerchantTransferResult(
                String outBatchNo,
                String transferBillNo,
                String createTime,
                String state,
                String rawResponse) {
            this(outBatchNo, transferBillNo, createTime, state, null, null, null, rawResponse);
        }
    }

    record MerchantTransferStatus(
            String state,
            String transferBillNo,
            String failReason,
            String detailStatus,
            String rawResponse) {

        public MerchantTransferStatus(
                String state,
                String transferBillNo,
                String failReason,
                String rawResponse) {
            this(state, transferBillNo, failReason, null, rawResponse);
        }
    }
}
