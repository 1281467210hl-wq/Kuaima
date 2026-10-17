package com.kuaima.app.domain.boss.constant;

/**
 * 招工订单与报名记录的状态常量
 */
public final class BossStatus {

    private BossStatus() {
    }

    /** 订单状态（含 admin 审核环节） */
    public static final String ORDER_DRAFT = "草稿";
    public static final String ORDER_PENDING_AUDIT = "待审核";
    public static final String ORDER_AUDIT_REJECT = "审核拒绝";
    public static final String ORDER_RECRUITING = "招工中";
    public static final String ORDER_RECRUIT_END = "招工结束";
    public static final String ORDER_WORKING = "工作中";
    public static final String ORDER_PENDING_SETTLE = "待结算";
    public static final String ORDER_COMPLETED = "已完成";
    public static final String ORDER_CANCELED = "取消招工";

    /** 报名状态 */
    public static final String ITEM_APPLIED = "已报名";
    public static final String ITEM_HIRED = "已录用";
    public static final String ITEM_ON_WORK = "已到岗";
    public static final String ITEM_PENDING_SETTLE = "待结算";
    public static final String ITEM_FINISHED = "已完成";
    public static final String ITEM_CANCELED = "取消报名";
    public static final String ITEM_REJECTED = "已拒绝";

    /** 订单被取消时，报名记录统一置为的状态 */
    public static final String ITEM_CANCEL_BY_BOSS = "取消招工";

    /** 零工端三大整合状态（前端展示与筛选用，不落库） */
    public static final String GROUP_WORKING = "工作中";
    public static final String GROUP_COMPLETED = "已完成";
    public static final String GROUP_CANCELED = "已取消";

    /** 零工端展示文案：已到岗 → 工作中（0912 产品调整） */
    public static final String DISPLAY_ON_WORK = "工作中";
}
