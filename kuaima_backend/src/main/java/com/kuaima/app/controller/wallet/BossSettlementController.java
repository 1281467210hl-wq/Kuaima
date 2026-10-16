package com.kuaima.app.controller.wallet;

import java.util.List;
import java.util.Arrays;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.wallet.model.PendingSettlementModels.PendingSettlementOrder;
import com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayRequest;
import com.kuaima.app.domain.wallet.model.SettlementPaymentModels.WechatPayView;
import com.kuaima.app.domain.wallet.model.SettlementPaymentModels.SettlementFeePreview;
import com.kuaima.app.domain.wallet.service.SettlementService;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 老板端结算聚合接口。 */
@RestController
@RequestMapping("/boss/settlements")
@Tag(name = "老板-结算", description = "老板待结算岗位聚合查询")
public class BossSettlementController {

    private final SettlementService settlementService;

    public BossSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(summary = "老板全部待结算订单", description = "按当前老板 JWT 返回全部岗位的待结算报名记录、零工、金额和结算状态；不接受前端传入 userId/bossId")
    @GetMapping("/pending")
    public Result<List<PendingSettlementOrder>> listPending(Authentication authentication) {
        return Result.success(settlementService.listPendingByBoss(requireBossId(authentication)));
    }

    @Operation(summary = "结算费用明细预览", description = "进入结算确认页时查询；只读，不创建支付订单，不生成微信预支付参数")
    @GetMapping("/payments/preview")
    public Result<SettlementFeePreview> preview(@RequestParam String settlementIds,
                                                @RequestParam(required = false) Long userCouponId,
                                                @RequestParam(required = false, defaultValue = "0") Integer pointsToUse,
                                                Authentication authentication) {
        List<Long> ids;
        try {
            ids = Arrays.stream(settlementIds.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).distinct().toList();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("settlementIds 必须是逗号分隔的整数");
        }
        return Result.success(settlementService.previewWechatPayment(requireBossId(authentication), ids,
                userCouponId, pointsToUse));
    }

    @Operation(summary = "创建订单结算微信支付", description = "校验结算单归属当前老板并创建微信 JSAPI 预支付单；金额由后端按结算单汇总，不信任前端金额")
    @PostMapping("/payments/wechat")
    public Result<WechatPayView> createWechatPayment(
            @RequestBody WechatPayRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return Result.success(settlementService.createWechatPayment(requireBossId(authentication),
                body == null ? null : body.settlementIds(), body == null ? null : body.userCouponId(),
                body == null ? 0 : body.pointsToUse(), idempotencyKey));
    }

    @Operation(summary = "查询订单结算微信支付状态")
    @GetMapping("/payments/{paymentNo}")
    public Result<WechatPayView> getWechatPayment(@PathVariable String paymentNo,
                                                   Authentication authentication) {
        return Result.success(settlementService.getWechatPayment(requireBossId(authentication), paymentNo));
    }

    private Long requireBossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.BOSS.equals(loginUser.role()) && loginUser.id() != null) {
            return loginUser.id();
        }
        throw new IllegalStateException("当前登录账号不是有效的老板账号");
    }
}
