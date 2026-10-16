package com.kuaima.app.controller.finance;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.points.entity.PointsAccount;
import com.kuaima.app.domain.points.entity.PointsFlow;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.reward.entity.Reward;
import com.kuaima.app.domain.reward.entity.RewardExchange;
import com.kuaima.app.domain.reward.repository.RewardExchangeRepository;
import com.kuaima.app.domain.reward.repository.RewardRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.security.model.LoginUser;

import jakarta.persistence.EntityNotFoundException;

/**
 * 积分奖励兑换。
 */
@RestController
@RequestMapping("/rewards")
@Tag(name = "悬赏", description = "订单悬赏加价")
public class RewardController {

    private final RewardRepository rewardRepository;
    private final RewardExchangeRepository exchangeRepository;
    private final PointsAccountRepository pointsAccountRepository;
    private final PointsFlowRepository pointsFlowRepository;

    public RewardController(RewardRepository rewardRepository,
                            RewardExchangeRepository exchangeRepository,
                            PointsAccountRepository pointsAccountRepository,
                            PointsFlowRepository pointsFlowRepository) {
        this.rewardRepository = rewardRepository;
        this.exchangeRepository = exchangeRepository;
        this.pointsAccountRepository = pointsAccountRepository;
        this.pointsFlowRepository = pointsFlowRepository;
    }

    /** 奖励列表：GET /rewards */
    @Operation(summary = "奖励列表", description = "返回全部可兑换奖品，无分页")
    @GetMapping
    public Result<List<Reward>> listRewards() {
        return Result.success(rewardRepository.findAll());
    }

    /** 奖励详情：GET /rewards/{id} */
    @Operation(summary = "奖励详情", description = "按 id 查询奖品；不存在抛出 EntityNotFoundException")
    @GetMapping("/{id}")
    public Result<Reward> getReward(@PathVariable Long id) {
        return Result.success(rewardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("奖品不存在: " + id)));
    }

    /** 积分兑换：POST /rewards/{id}/exchange?userId=1 */
    @Operation(summary = "积分兑换", description = "用积分兑换奖品：校验库存（不足报错）与积分余额（不足报错），扣减积分并记录流水（bizType=REWARD_EXCHANGE）、扣减库存、创建 status=PENDING 的兑换记录；userId 必填")
    @PostMapping("/{id}/exchange")
    @Transactional
    public Result<RewardExchange> exchange(@PathVariable Long id, @RequestParam Long userId,
                                           Authentication authentication) {
        requireWorkerUser(userId, authentication);
        Reward reward = rewardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("奖品不存在: " + id));
        if (reward.getStock() != null && reward.getStock() <= 0) {
            throw new IllegalStateException("奖品库存不足");
        }
        // 扣减积分
        java.util.List<PointsAccount> lockedAccounts =
                pointsAccountRepository.findAllByUserIdAndRoleForUpdate(userId, UserRole.USER);
        PointsAccount account = lockedAccounts.isEmpty()
                ? pointsAccountRepository.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER).orElseGet(() -> {
                    PointsAccount a = new PointsAccount();
                    a.setUserId(userId);
                    a.setRole(UserRole.USER);
                    a.setBalance(0);
                    return pointsAccountRepository.saveAndFlush(a);
                })
                : lockedAccounts.get(0);
        int cost = reward.getPointsCost() != null ? reward.getPointsCost() : 0;
        int before = account.getBalance() == null ? 0 : account.getBalance();
        if (before < cost) {
            throw new IllegalStateException("积分余额不足");
        }
        int after = before - cost;
        account.setBalance(after);
        pointsAccountRepository.save(account);
        // 记录积分流水
        PointsFlow flow = new PointsFlow();
        flow.setUserId(userId);
        flow.setRole(UserRole.USER);
        flow.setDelta(-cost);
        flow.setBizType("REWARD_EXCHANGE");
        flow.setRemark("兑换奖品: " + reward.getTitle());
        flow.setBalanceAfter(after);
        pointsFlowRepository.save(flow);
        // 扣减库存
        if (reward.getStock() != null) {
            reward.setStock(reward.getStock() - 1);
            rewardRepository.save(reward);
        }
        // 创建兑换记录
        RewardExchange exchange = new RewardExchange();
        exchange.setUserId(userId);
        exchange.setRewardId(id);
        exchange.setStatus("PENDING");
        return Result.success(exchangeRepository.save(exchange));
    }

    /** 兑换记录：GET /rewards/exchanges?userId=1 */
    @Operation(summary = "兑换记录", description = "按用户 id 查询全部兑换记录；userId 必填")
    @GetMapping("/exchanges")
    public Result<List<RewardExchange>> listExchanges(@RequestParam Long userId,
                                                      Authentication authentication) {
        requireWorkerUser(userId, authentication);
        return Result.success(exchangeRepository.findByUserId(userId));
    }

    private void requireWorkerUser(Long userId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)
                || user.id() == null || !user.id().equals(userId)
                || !UserRole.USER.equals(user.role())) {
            throw new ForbiddenBusinessException("只能使用当前零工身份的积分");
        }
    }
}
