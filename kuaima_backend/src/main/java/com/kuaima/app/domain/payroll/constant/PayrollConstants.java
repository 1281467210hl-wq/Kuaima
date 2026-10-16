package com.kuaima.app.domain.payroll.constant;

/** 发薪管理模块状态常量。 */
public final class PayrollConstants {

    public static final String ORDER_DRAFT = "draft";
    public static final String ORDER_PENDING = "pending";
    public static final String ORDER_APPROVED = "approved";
    public static final String ORDER_REJECTED = "rejected";
    public static final String ORDER_WITHDRAWN = "withdrawn";

    public static final String DETAIL_PENDING = "pending";
    public static final String DETAIL_SUCCESS = "success";
    public static final String DETAIL_FAILED = "failed";

    public static final String TYPE_WAGE = "wage";
    public static final String TYPE_ADVANCE = "advance";
    public static final String TYPE_OTHER = "other";

    private PayrollConstants() {
    }
}
