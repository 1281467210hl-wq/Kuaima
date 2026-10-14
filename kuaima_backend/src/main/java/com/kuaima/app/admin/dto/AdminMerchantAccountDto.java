package com.kuaima.app.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 后台「商户账户」相关入参。
 * 单位约定：余额/冻结金额以「分」(balanceFen/frozenFen) 入库；为便于后台表单填写，
 * 同时接受 balanceYuan/frozenYuan（元），二者同时存在时以「分」为准。
 */
public final class AdminMerchantAccountDto {

    private AdminMerchantAccountDto() {
    }

    /** 列表/充值订单查询条件 */
    @Data
    public static class Query {
        /** 账户名称/主体全称/商户号 关键词 */
        private String keyword;
        /** 关联商户账户 id（充值订单查询用，对应 boss_balance_recharge_order.accountId） */
        private Long accountId;
        /** 所属老板 user.id，可选 */
        private Long bossId;
        /** 老板登录名/企业名关键词，可选 */
        private String bossKeyword;
        /** 账户状态：active/frozen/deleted；不传则排除 deleted */
        private String status;
        /** 是否默认账户，可选 */
        private Boolean isDefault;
        /** 订单号（充值订单查询用） */
        private String orderNo;
        /** 支付方式：wechat/alipay */
        private String payMethod;
        private LocalDate startDate;
        private LocalDate endDate;
        private int page = 0;
        private int size = 10;
    }

    /** 新增/编辑入参 */
    @Data
    public static class SaveRequest {
        /** 所属老板 user.id（新增必填，编辑可不传） */
        private Long bossId;
        /** 账户名称/公司简称，如 晴时科技 */
        private String accountName;
        /** 主体全称，如 上海晴时网络科技有限公司 */
        private String subjectName;
        /** 商户号 */
        private String merchantNo;
        /** 账户余额（分） */
        private Long balanceFen;
        /** 账户余额（元） */
        private BigDecimal balanceYuan;
        /** 开户银行（扩展表存储） */
        private String bankName;
        /** 开户支行（扩展表存储） */
        private String bankBranch;
        /** 对公银行账号（扩展表存储） */
        private String bankAccountNo;
        /** 冻结金额（分，扩展表存储） */
        private Long frozenFen;
        /** 冻结金额（元，扩展表存储） */
        private BigDecimal frozenYuan;
        /** 是否默认账户 */
        private Boolean isDefault;
        /** 状态：active/frozen/deleted */
        private String status;
        /** 备注 */
        private String remark;
    }
}
