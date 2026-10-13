package com.kuaima.app.domain.message.constant;

import java.util.List;

/**
 * 站内消息类型常量。
 * 事件发生时写入接收者收件箱(sys_message)，接收者角色与文案见注释。
 */
public final class MessageType {

    /** 后台模板测试/系统通知 -> 指定用户 */
    public static final String SYSTEM_NOTICE = "SYSTEM_NOTICE";

    /** 岗位发布(招聘广播) -> 全部员工 USER */
    public static final String ORDER_PUBLISH = "ORDER_PUBLISH";

    /** 有零工新报名 -> 订单老板 BOSS */
    public static final String ORDER_APPLY = "ORDER_APPLY";

    /** 零工被录用 -> 被录用者 USER */
    public static final String ORDER_HIRE = "ORDER_HIRE";

    /** 老板拒绝报名 -> 被拒绝的零工 USER */
    public static final String ORDER_APPLY_REJECT = "ORDER_APPLY_REJECT";

    /** 岗位快开始提醒 -> 该单已报名/已录用/已到岗 USER（预留，由前端/手动触发，暂不做定时） */
    public static final String ORDER_START_REMIND = "ORDER_START_REMIND";

    /** 订单取消招工 -> 该单未完成的报名者 USER */
    public static final String ORDER_CANCEL = "ORDER_CANCEL";

    /** 零工取消报名 -> 订单老板 BOSS */
    public static final String ITEM_CANCEL = "ITEM_CANCEL";

    /** 零工确认到岗 -> 订单老板 BOSS */
    public static final String ITEM_WORK_CONFIRM = "ITEM_WORK_CONFIRM";

    /** 工资结算到账 -> 收款零工 USER */
    public static final String SETTLE_PAID = "SETTLE_PAID";

    /** 提现打款失败已退回 -> 提现零工 USER */
    public static final String WITHDRAW_FAIL = "WITHDRAW_FAIL";

    /** 老板邀请零工 -> 被邀请者 USER */
    public static final String BOSS_INVITE = "BOSS_INVITE";

    /** 只应出现在老板身份收件箱的业务消息。 */
    public static final List<String> BOSS_MESSAGE_TYPES = List.of(
            ORDER_APPLY,
            ITEM_CANCEL,
            ITEM_WORK_CONFIRM);

    /** 只应出现在零工身份收件箱的业务消息。 */
    public static final List<String> USER_MESSAGE_TYPES = List.of(
            ORDER_PUBLISH,
            ORDER_HIRE,
            ORDER_APPLY_REJECT,
            ORDER_START_REMIND,
            ORDER_CANCEL,
            SETTLE_PAID,
            WITHDRAW_FAIL,
            BOSS_INVITE);

    private MessageType() {
    }
}
