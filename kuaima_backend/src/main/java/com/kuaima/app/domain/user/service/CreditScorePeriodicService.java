package com.kuaima.app.domain.user.service;

import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.wallet.entity.Settlement;
import com.kuaima.app.domain.wallet.repository.SettlementRespository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.time.YearMonth;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.boss.constant.BossStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 只处理已有可靠业务数据来源的周期信用规则。 */
@Service
@RequiredArgsConstructor
public class CreditScorePeriodicService {
    private final SettlementRespository settlements;
    private final BaseOrderItemRespository items;
    private final BossOrderRespository orders;
    private final CreditScoreService scores;
    private final UserRepository users;

    public void processOverdueSettlements(LocalDateTime effectiveFrom, LocalDateTime now) {
        List<Settlement> pending = settlements.findPendingFinishedBetween(effectiveFrom, now.minusHours(24));
        for (Settlement settlement : pending) {
            applyOverdue(settlement, now);
        }
    }

    /** 计算零工自然月完单率，达到95%后奖励300星级分。 */
    public void processWorkerMonthlyCompletion(YearMonth month) {
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        for (User user : users.findWorkerIdentities()) {
            if (user.getId() == null) continue;
            List<BaseOrderItem> records = items.findByUserId(user.getId());
            int total = 0, completed = 0;
            for (BaseOrderItem item : records) {
                if (item.getHireDate() == null) continue;
                LocalDateTime hired = item.getHireDate().toLocalDate().atStartOfDay();
                if (hired.isBefore(start) || !hired.isBefore(end)) continue;
                total++;
                if (BossStatus.ITEM_FINISHED.equals(item.getStatus())
                        || settlements.existsByItemIdAndStatusIn(item.getId(), List.of("已支付"))) completed++;
            }
            if (total > 0 && completed * 100 >= total * 95) {
                scores.adjust(user.getId(), CreditScoreService.WORKER_STAR, 300,
                        "WORKER_COMPLETION_RATE_MONTH", "PERIODIC",
                        "WORKER_COMPLETION_RATE_MONTH:" + user.getId() + ":" + month,
                        "自然月完单率达到95%");
            }
        }
    }

    private void applyOverdue(Settlement settlement, LocalDateTime now) {
        BaseOrderItem item = items.findById(settlement.getItemId()).orElse(null);
        BossOrder order = orders.findById(settlement.getOrderId()).orElse(null);
        if (item == null || item.getFinishAt() == null || order == null || order.getCreateBy() == null) return;
        long overdueHours = Duration.between(item.getFinishAt(), now).toHours();
        boolean heldBack = "heldBack".equalsIgnoreCase(order.getType());
        if (overdueHours >= 24) {
            scores.adjust(order.getCreateBy(), CreditScoreService.BOSS_CREDIT, -5,
                    "BOSS_SETTLE_OVERDUE_24H", "SETTLEMENT",
                    "BOSS_SETTLE_OVERDUE_24H:" + settlement.getId(), "完工24小时仍未结算");
        }
        if (!heldBack && overdueHours >= 48) {
            scores.adjust(order.getCreateBy(), CreditScoreService.BOSS_CREDIT, -5,
                    "BOSS_SETTLE_OVERDUE_48H", "SETTLEMENT",
                    "BOSS_SETTLE_OVERDUE_48H:" + settlement.getId(), "完工48小时仍未结算，累计扣10分");
        }
        if (overdueHours >= 72) {
            LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
            LocalDateTime nextMonth = monthStart.plusMonths(1);
            int deducted = Math.abs(Math.min(0, scores.ruleDelta(order.getCreateBy(),
                    "BOSS_WAGE_ARREARS_72H", monthStart, nextMonth)));
            if (deducted < 1000) {
                scores.adjust(order.getCreateBy(), CreditScoreService.BOSS_CREDIT, -Math.min(100, 1000 - deducted),
                        "BOSS_WAGE_ARREARS_72H", "SETTLEMENT",
                        "BOSS_WAGE_ARREARS_72H:" + settlement.getId(), "拖欠报酬达到72小时");
            }
        }
    }
}
