package com.kuaima.app.domain.user.service;

import java.util.Map;

/** 信用分/星级分流水规则的后台展示名称。 */
public final class CreditRuleNames {
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("BOSS_ORDER_SETTLED", "完成订单"),
            Map.entry("BOSS_COMPLETION_RATE_90D", "完成订单"),
            Map.entry("BOSS_SETTLE_WITHIN_1H", "及时结算"),
            Map.entry("BOSS_SETTLE_OVERDUE_24H", "超时结算"),
            Map.entry("BOSS_SETTLE_OVERDUE_48H", "超时结算"),
            Map.entry("BOSS_WAGE_ARREARS_72H", "拖欠报酬"),
            Map.entry("BOSS_CANCEL_ORDER", "雇主原因取消订单"),
            Map.entry("WORKER_COMPLETION_RATE_MONTH", "奖励星级分"),
            Map.entry("WORKER_WORK_HOURS", "工时星级分"),
            Map.entry("WORKER_FIVE_STAR_REVIEW", "好评星级分"),
            Map.entry("WORKER_BAD_REVIEW", "差评"),
            Map.entry("WORKER_EARLY_LEAVE", "早退"),
            Map.entry("WORKER_COMPLAINT", "投诉"),
            Map.entry("BOSS_COMPLAINT", "零工投诉"),
            Map.entry("BOSS_OFF_PLATFORM_ORDER", "飞单"),
            Map.entry("ADMIN_MANUAL_ADJUST", "管理员人工调整")
    );

    private CreditRuleNames() {}

    public static String display(String ruleCode) {
        return display(ruleCode, null);
    }

    public static String display(String ruleCode, Integer delta) {
        if (ruleCode == null || ruleCode.isBlank()) return ruleCode;
        if ("BOSS_REVIEW_SCORE".equals(ruleCode)) {
            return delta != null && delta < 0 ? "零工差评" : "零工好评";
        }
        return NAMES.getOrDefault(ruleCode, ruleCode);
    }
}
