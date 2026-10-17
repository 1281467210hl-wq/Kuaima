package com.kuaima.app.controller.boss;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.enterprise.service.EnterpriseContextService;
import com.kuaima.app.domain.review.model.BossReviewModels;
import com.kuaima.app.domain.review.model.WorkerReviewModels.*;
import com.kuaima.app.domain.review.service.BossReviewService;
import com.kuaima.app.domain.review.service.WorkerReviewService;
import com.kuaima.app.security.model.LoginUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/boss/orders")
@RequiredArgsConstructor
public class BossWorkerReviewController {
    private final WorkerReviewService service;
    private final BossReviewService bossReviewService;
    private final EnterpriseContextService enterpriseContexts;

    @PutMapping("/{orderId}/worker-review")
    public Result<List<ReviewView>> save(@PathVariable Long orderId, @RequestBody SaveRequest request,
                                         Authentication authentication) {
        EnterpriseContextService.Context context = enterpriseContexts.require(authentication, "ORDER_CREATE");
        return Result.success(service.saveOrderReviews(
                context.user().getId(), context.enterprise().getId(), orderId, request));
    }

    @GetMapping("/{orderId}/worker-review")
    public Result<List<ReviewView>> list(@PathVariable Long orderId, Authentication authentication) {
        EnterpriseContextService.Context context = enterpriseContexts.require(authentication, "ORDER_VIEW");
        return Result.success(service.list(
                context.user().getId(), context.enterprise().getId(), orderId));
    }

    @GetMapping("/{orderId}/boss-review")
    public Result<List<BossReviewModels.ReviewView>> listFromWorkers(
            @PathVariable Long orderId, Authentication authentication) {
        EnterpriseContextService.Context context = enterpriseContexts.require(authentication, "ORDER_VIEW");
        return Result.success(bossReviewService.listFromWorkers(
                context.user().getId(), context.enterprise().getId(), orderId));
    }

    private Long boss(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user && user.id() != null) return user.id();
        throw new ForbiddenBusinessException("请先登录老板账号");
    }
}
