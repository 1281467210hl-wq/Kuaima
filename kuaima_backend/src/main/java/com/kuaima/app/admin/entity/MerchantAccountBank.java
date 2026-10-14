package com.kuaima.app.admin.entity;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 商户账户银行信息：boss_merchant_account 没有开户银行/对公账号/冻结金额列，
 * 后台商户账户维护需要这些字段，故新建 1:1 扩展表承载（不改已有实体）。金额以「分」存储。
 */
@Entity
@Table(name = "boss_merchant_account_bank", indexes = {
        @Index(name = "idx_bma_bank_account", columnList = "account_id"),
        @Index(name = "idx_bma_bank_boss", columnList = "boss_id")
})
@Getter
@Setter
public class MerchantAccountBank extends BaseEntity {

    @Column(name = "account_id", nullable = false, comment = "关联 boss_merchant_account.id")
    private Long accountId;

    @Column(name = "boss_id", comment = "所属老板 user.id，冗余便于按老板查询")
    private Long bossId;

    @Column(name = "bank_name", length = 100, comment = "开户银行")
    private String bankName;

    @Column(name = "bank_branch", length = 100, comment = "开户支行")
    private String bankBranch;

    @Column(name = "bank_account_no", length = 64, comment = "对公银行账号")
    private String bankAccountNo;

    @Column(name = "frozen_amount", columnDefinition = "BIGINT", comment = "冻结金额（分）")
    private Long frozenAmount = 0L;

    @Column(length = 500, comment = "备注")
    private String remark;
}
