package com.kuaima.app.controller.user;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.model.WorkerOrderModels.WorkerOrder;
import com.kuaima.app.domain.user.service.WorkerProfileService;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/worker/orders")
@Tag(name = "零工-订单", description = "当前零工报名订单聚合查询")
public class WorkerOrderController {

    private final WorkerProfileService workerProfileService;

    public WorkerOrderController(WorkerProfileService workerProfileService) {
        this.workerProfileService = workerProfileService;
    }

    @GetMapping
    @Operation(summary = "零工订单列表", description = "按当前JWT零工查询报名记录及岗位完整信息。支持三大整合状态过滤：工作中/已完成/已取消（0912产品调整），同时兼容底层状态值透传。type=daily/heldBack/month。")
    public Result<List<WorkerOrder>> listOrders(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        LoginUser worker = currentWorker(authentication);
        Page<WorkerOrder> result = workerProfileService.listOrders(worker.id(), type, status,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "id")));
        return Result.success(result.getContent(), result.getNumber(), result.getTotalElements());
    }

    @GetMapping("/{itemId}")
    @Operation(summary = "零工订单详情", description = "按报名条目 ID 查询当前登录零工的订单及岗位快照信息；岗位停止招工后仍可查看")
    public Result<WorkerOrder> getOrder(@PathVariable Long itemId, Authentication authentication) {
        LoginUser worker = currentWorker(authentication);
        return Result.success(workerProfileService.getOrder(worker.id(), itemId));
    }

    private LoginUser currentWorker(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser
                && UserRole.USER.equals(loginUser.role()) && loginUser.id() != null) {
            return loginUser;
        }
        throw new ForbiddenBusinessException("当前登录账号不是零工账号");
    }
}
