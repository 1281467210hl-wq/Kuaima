package com.kuaima.app.domain.wallet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.domain.reward.service.WechatMerchantTransferClient;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.entity.WithDraw;

import jakarta.persistence.EntityNotFoundException;

@Service
public class WorkerWalletWechatWithdrawalService {
    private final WalletService wallets;
    private final UserRepository users;
    private final WechatMerchantTransferClient transfers;

    public WorkerWalletWechatWithdrawalService(WalletService wallets, UserRepository users,
            WechatMerchantTransferClient transfers) {
        this.wallets = wallets; this.users = users; this.transfers = transfers;
    }

    public Map<String, Object> withdraw(Long userId, BigDecimal amount, String idempotencyKey) {
        if (amount == null) throw new IllegalArgumentException("amount 不能为空");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.compareTo(new BigDecimal("0.01")) < 0) throw new IllegalArgumentException("提现金额不能低于0.01元");
        String key = key(userId, idempotencyKey);
        WithDraw old = wallets.findByIdempotencyKey(key).orElse(null);
        if (old != null) return view(requireSame(old, userId, amount));
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("用户不存在"));
        if (!StringUtils.hasText(user.getOpenid())) throw new IllegalArgumentException("请先绑定微信账户");
        WithDraw draw;
        try {
            draw = wallets.submitWechatWithdraw(userId, amount, user.getOpenid(), key);
        } catch (DataIntegrityViolationException e) {
            WithDraw concurrent = wallets.findByIdempotencyKey(key).orElse(null);
            if (concurrent != null) return view(requireSame(concurrent, userId, amount));
            throw e;
        }
        WechatMerchantTransferClient.MerchantTransferResult result;
        try {
            result = transfers.transfer(new WechatMerchantTransferClient.MerchantTransferRequest(
                    draw.getMerchantBatchNo(), draw.getMerchantDetailNo(), amount.movePointRight(2).longValueExact(),
                    user.getOpenid(), "快马日结钱包提现"));
        } catch (RuntimeException e) {
            String reason = e.getMessage();
            wallets.failWechatWithdraw(draw.getId(), reason, null, null);
            throw new IllegalStateException("微信商家转账发起失败，余额已退回：" + reason);
        }
        return view(wallets.acceptWechatWithdraw(draw.getId(), result.transferBillNo(), result.rawResponse()));
    }

    private String key(Long userId, String key) {
        if (!StringUtils.hasText(key)) throw new IllegalArgumentException("Idempotency-Key 不能为空");
        String value = key.trim(); if (value.length() > 100) throw new IllegalArgumentException("Idempotency-Key 长度不能超过100");
        return "worker-wallet:" + userId + ":" + value;
    }
    private WithDraw requireSame(WithDraw draw, Long userId, BigDecimal amount) {
        if (!userId.equals(draw.getUserId()) || draw.getAmount().compareTo(amount) != 0)
            throw new IllegalArgumentException("同一Idempotency-Key不能用于不同提现请求");
        return draw;
    }
    private Map<String, Object> view(WithDraw draw) {
        Map<String, Object> m = new LinkedHashMap<>(); m.put("id", draw.getId()); m.put("amount", draw.getAmount());
        m.put("channel", draw.getChannel()); m.put("status", draw.getStatus()); m.put("appliedAt", draw.getApplyTime());
        m.put("paidAt", draw.getPayTime()); m.put("failureReason", draw.getRemark());
        JSONObject rawResponse = StringUtils.hasText(draw.getTransferResponse())
                ? JSONObject.parseObject(draw.getTransferResponse()) : null;
        m.put("state", rawResponse == null ? null : rawResponse.getString("state"));
        m.put("appId", rawResponse == null ? null : rawResponse.getString("app_id"));
        m.put("mchId", rawResponse == null ? null : rawResponse.getString("mch_id"));
        m.put("packageInfo", rawResponse == null ? null : rawResponse.getString("package_info"));
        return m;
    }
}
