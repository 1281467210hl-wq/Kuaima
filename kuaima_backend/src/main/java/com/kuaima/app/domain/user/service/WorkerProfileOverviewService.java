package com.kuaima.app.domain.user.service;

import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.starlevel.repository.UserStarLevelRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.model.WorkerProfileOverview;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Date;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerProfileOverviewService {
    private final UserRepository users;
    private final BaseOrderItemRespository items;
    private final SettlementRespository settlements;
    private final PointsAccountRepository points;
    private final UserStarLevelRepository starLevels;
    private final RewardAccountRepository rewardAccounts;

    public WorkerProfileOverviewService(UserRepository users, BaseOrderItemRespository items,
            SettlementRespository settlements, PointsAccountRepository points,
            UserStarLevelRepository starLevels, RewardAccountRepository rewardAccounts) {
        this.users = users; this.items = items; this.settlements = settlements;
        this.points = points; this.starLevels = starLevels; this.rewardAccounts = rewardAccounts;
    }

    @Transactional(readOnly = true)
    public WorkerProfileOverview overview(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new EntityNotFoundException("零工不存在: " + userId));
        long applicationTotal = items.countByUserId(userId);
        long finishedOrders = items.countByUserIdAndStatus(userId, BossStatus.ITEM_FINISHED);
        long earlyLeaveOrders = items.countByUserIdAndStatusAndEarlyLeaveTrue(userId, BossStatus.ITEM_FINISHED);
        long completedOrders = Math.max(0, finishedOrders - earlyLeaveOrders);
        long cancellations = items.countByUserIdAndStatus(userId, BossStatus.ITEM_CANCELED);
        long hiredTotal = items.countByUserIdAndHireDateIsNotNull(userId);
        long noShows = items.countNoShowByUserId(userId, new Date());
        long totalWorkDays = nullableLong(settlements.sumWorkDaysByWorkerId(userId));
        long earlyLeaveWorkDays = nullableLong(settlements.sumEarlyLeaveWorkDaysByWorkerId(userId));

        int level = starLevels.findFirstByUserIdOrderByIdDesc(userId)
                .map(v -> value(v.getLevel(), 1))
                .orElse(1);
        int creditScore = clamp(value(user.getCreditScore(), 0));
        int pointBalance = points.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER)
                .map(v -> value(v.getBalance(), 0))
                .orElse(0);
        long totalIncome = nullableLong(settlements.sumPaidWageByWorkerId(userId));
        // 零工个人页优先读取 USER 账户，并兼容历史重复账户数据。
        long rewardAmount = rewardAccounts.findFirstByUserIdAndRoleOrderByIdDesc(userId, UserRole.USER)
                .or(() -> rewardAccounts.findFirstByUserIdOrderByIdDesc(userId))
                .map(account -> account.getBalance() == null
                        ? 0L : account.getBalance().movePointRight(2).longValue())
                .orElse(0L);
        return new WorkerProfileOverview(level, creditScore,
                rate(completedOrders, applicationTotal), rate(cancellations, applicationTotal),
                rate(noShows, hiredTotal), rate(earlyLeaveWorkDays, totalWorkDays),
                totalIncome, completedOrders, pointBalance, rewardAmount);
    }

    private int rate(long count, long total) { return total == 0 ? 0 : clamp((int) Math.round(count * 100.0 / total)); }
    private int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private int value(Integer value, int fallback) { return value == null ? fallback : value; }
    private long nullableLong(Long value) { return value == null ? 0L : value; }
}
