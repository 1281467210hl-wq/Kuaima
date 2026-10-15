package com.kuaima.app.controller.boss;

import com.kuaima.app.common.*;
import com.kuaima.app.domain.reward.service.BossRewardService;
import com.kuaima.app.domain.reward.service.WorkerRewardWithdrawalFailedException;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/boss/rewards")
@Tag(name = "老板端-奖励金")
public class BossRewardController {
    private final BossRewardService service;
    public BossRewardController(BossRewardService service) { this.service = service; }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(Authentication authentication) {
        return Result.success(service.overview(bossId(authentication)));
    }

    @GetMapping("/records")
    public Result<Map<String, Object>> records(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "ALL") String type,
            Authentication authentication) {
        validatePage(page, size);
        Page<Map<String, Object>> result = service.records(bossId(authentication), type,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return Result.success(Map.of("records", result.getContent(), "total", result.getTotalElements(), "page", result.getNumber()));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "申请奖励金提现", description = "amount单位为元；建议每次请求携带唯一Idempotency-Key")
    public Result<Map<String, Object>> withdraw(@RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication authentication) {
        try {
            Result<Map<String, Object>> result = Result.success(service.withdraw(
                    bossId(authentication),
                    moneyValue(body == null ? null : body.get("amount")),
                    textValue(body == null ? null : body.get("channel")),
                    textValue(body == null ? null : body.get("account")),
                    key));
            result.setMessage("提现申请已提交");
            return result;
        } catch (WorkerRewardWithdrawalFailedException e) {
            return Result.error(502, e.getMessage());
        }
    }

    @GetMapping("/withdrawals")
    public Result<Map<String, Object>> withdrawals(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        validatePage(page, size);
        Page<Map<String, Object>> result = service.withdrawals(bossId(authentication),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt", "id")));
        return Result.success(Map.of("records", result.getContent(), "total", result.getTotalElements(), "page", result.getNumber()));
    }

    private Long bossId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user
                && user.id() != null && UserRole.BOSS.equals(user.role())) return user.id();
        throw new ForbiddenBusinessException("当前登录账号不是老板端身份，请先完成企业认证");
    }
    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page 必须大于等于0，size范围为1-100");
    }
    private BigDecimal moneyValue(Object value) {
        try { return value == null ? null : new BigDecimal(String.valueOf(value)); }
        catch (Exception e) { throw new IllegalArgumentException("amount 必须是元金额"); }
    }
    private String textValue(Object value) { return value == null ? null : String.valueOf(value); }
}
