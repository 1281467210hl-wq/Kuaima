package com.kuaima.app.admin.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * 后台「转账记录」查询条件（平台全局视角，老板/项目均为可选筛选，不再绑定登录身份）。
 * 金额单位：payroll_order/payroll_detail 以「分」存储；boss_balance_recharge_order 以「元」存储。
 */
@Data
public class AdminTransferQueryDto {

    /** 转账方向：out=发薪出账（默认主体数据），in=充值入账，all/空=两者合并 */
    private String direction;

    /** 转账状态：success=成功 / processing=处理中 / failed=失败；出账方向按明细状态派生 */
    private String status;

    /** 关键词：转账单号 / 薪单标题 / 公司 / 项目 / 收款人姓名 */
    private String keyword;

    /** 老板（制单人）user.id，可选 */
    private Long bossId;

    /** 老板登录名/企业名关键词，可选（与 bossId 二选一） */
    private String bossKeyword;

    /** 项目 id，可选 */
    private Long projectId;

    /** 发薪类型：wage/advance/other，仅出账方向有效 */
    private String type;

    /** 支付方式：wechat/alipay，仅入账方向有效 */
    private String payMethod;

    /** 转账开始日期（含） */
    private LocalDate startDate;

    /** 转账结束日期（含） */
    private LocalDate endDate;

    /** 页码，0 起 */
    private int page = 0;

    /** 每页条数 */
    private int size = 10;
}
