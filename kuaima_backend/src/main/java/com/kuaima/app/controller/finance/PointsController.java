package com.kuaima.app.controller.finance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.security.model.LoginUser;

/**
 * 当前登录用户在当前身份下的积分账户与流水。
 */
@RestController
@RequestMapping("/points")
@Tag(name = "积分", description = "用户积分账户与流水")
public class PointsController {

    private final PointsAccountRepository accountRepository;
    private final PointsFlowRepository flowRepository;

    public PointsController(PointsAccountRepository accountRepository, PointsFlowRepository flowRepository) {
        this.accountRepository = accountRepository;
        this.flowRepository = flowRepository;
    }

    /** 积分余额：GET /points/{userId} */
    @Operation(summary = "积分余额查询", description = "积分按用户+身份隔离；返回当前JWT身份的余额，账户不存在时自动创建余额为 0 的账户")
    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getBalance(@PathVariable Long userId, Authentication authentication) {
        LoginUser operator = requireCurrentUser(userId, authentication);
        PointsAccount account = getOrCreateAccount(userId, operator.role());
        Map<String, Object> data = new HashMap<>();
        data.put("balance", account.getBalance());
        data.put("role", operator.role());
        return Result.success(data);
    }

    /** 积分明细分页：GET /points/{userId}/flows?page=0&size=20 */
    @Operation(summary = "积分明细分页", description = "按用户 id + 当前JWT身份分页查询积分流水，按时间倒序。参数：page(默认0)、size(默认20,上限100)")
    @GetMapping("/{userId}/flows")
    public Result<List<PointsFlow>> listFlows(@PathVariable Long userId,
                                              Authentication authentication,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        LoginUser operator = requireCurrentUser(userId, authentication);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<PointsFlow> result = flowRepository
                .findByUserIdAndRoleOrderByTimestampDesc(userId, operator.role(), pageable);
        return Result.success(result.getContent(), result.getNumber(), result.getTotalElements());
    }

    private PointsAccount getOrCreateAccount(Long userId, String role) {
        return accountRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, role).orElseGet(() -> {
            PointsAccount account = new PointsAccount();
            account.setUserId(userId);
            account.setRole(role);
            account.setBalance(0);
            return accountRepository.save(account);
        });
    }

    private LoginUser requireCurrentUser(Long userId, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                && user.id() != null && user.id().equals(userId)
                && UserRole.isValid(user.role())) {
            return user;
        }
        throw new ForbiddenBusinessException("只能查询当前登录身份的积分");
    }
}
