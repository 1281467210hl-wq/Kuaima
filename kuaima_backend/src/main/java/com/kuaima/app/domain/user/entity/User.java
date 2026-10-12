package com.kuaima.app.domain.user.entity;


import java.time.LocalDate;

import com.alibaba.fastjson2.annotation.JSONField;
import com.kuaima.app.domain.base.entity.BaseEntity;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.constant.UserBusinessCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sys_user")
@Getter
@Setter
public class User extends BaseEntity {

    /** 零工对外业务编号，如 G0010258；与数据库主键无关。 */
    @Column(name = "worker_code", unique = true, length = 8)
    private String workerCode;

    /** 老板对外业务编号，如 B0020156；企业认证通过后生成。 */
    @Column(name = "boss_code", unique = true, length = 8)
    private String bossCode;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    @JSONField(serialize = false)
    private String password;

    @Column(nullable = false, length = 20)
    private String role;

    @Column
    private String openid;

    @Column
    private String nickname;

    @Column
    private String avatar;

    @Column
    private String phone;

    @Column
    private String email;

    @Column
    private String status;

    @Column
    private String remark;

    @Column(comment = "年龄")
    private Integer age;

    @Column(comment = "性别")
    private String gender;

    @Transient
    private Integer balance;

    // ============ admin 管理扩展字段 ============

    /** 认证状态: 未认证/待审核/已通过/已拒绝 */
    @Column(length = 20)
    private String certStatus;

    /** 信用分 0~100 */
    @Column
    private Integer creditScore;

    /** 零工星级分，独立于老板信用分；初始为0。 */
    @Column
    private Integer starScore = 0;

    /** 认证类型: REALNAME(零工实名) / ENTERPRISE(企业认证) */
    @Column(length = 30)
    private String certType;

    /** 个人实名认证状态：UNVERIFIED/PENDING/APPROVED/REJECTED。 */
    @Column(length = 20)
    private String realnameStatus = "UNVERIFIED";

    /** 企业认证状态：UNVERIFIED/PENDING/APPROVED/REJECTED。 */
    @Column(length = 20)
    private String enterpriseStatus = "UNVERIFIED";

    /** 技能标签（逗号分隔） */
    @Column(length = 200)
    private String skills;

    /** 企业名称（仅 BOSS 用户） */
    @Column(length = 100)
    private String companyName;

    /** 企业认证通过后生成的唯一运营编号。 */
    @Column(name = "company_code", unique = true, length = 32)
    private String companyCode;

    /** 行业类型（仅 BOSS） */
    @Column(length = 50)
    private String industry;

    /** 联系人（仅 BOSS） */
    @Column(length = 50)
    private String contact;

    /** 联系人电话（仅 BOSS） */
    @Column(length = 20)
    private String contactPhone;

    /** 身份证号（实名认证） */
    @Column(length = 50)
    private String idCard;

    /** 真实姓名（实名认证） */
    @Column(length = 50)
    private String realName;

    /** 营业执照号（企业认证） */
    @Column(length = 50)
    private String licenseNo;

    /** 营业执照图片在 OSS 中的对象地址（企业认证） */
    @Column(length = 500)
    private String licenseImageUrl;

    /** 成立日期（企业认证） */
    @Column(name = "founding_date")
    private java.time.LocalDate foundingDate;

    /** 经营范围（企业认证） */
    @Column(name = "business_scope", length = 1000)
    private String businessScope;

    /** 实名认证身份证人像面图片地址 */
    @Column(length = 500)
    private String realnameIdCardImageUrl;

    /** 实名认证人脸照片地址 */
    @Column(length = 500)
    private String realnameFaceImageUrl;

    /** 法人代表（企业认证） */
    @Column(length = 50)
    private String legalRep;

    /** 父账号ID（子账号管理） */
    @Column
    private Long parentUserId;

    /** 子账号角色 */
    @Column(length = 20)
    private String subRole;

    /** 城市 */
    @Column(length = 50)
    private String city;

    /** 出生日期（零工个人资料） */
    @Column
    private LocalDate birthday;

    /** 工作年限（零工个人资料） */
    @Column
    private Integer workYears;

    /** 是否接受夜班（零工个人资料） */
    @Column
    private Boolean acceptNightShift;

    /** 个人简介（零工个人资料） */
    @Column(length = 500)
    private String introduction;

    @PrePersist
    @PreUpdate
    private void ensureBusinessCodes() {
        UserBusinessCode.ensureWorker(this);
        if (CertificationStatus.APPROVED.equalsIgnoreCase(enterpriseStatus)
                || ("ENTERPRISE".equalsIgnoreCase(certType) && "已通过".equals(certStatus))) {
            UserBusinessCode.ensureBoss(this);
        }
    }
}
