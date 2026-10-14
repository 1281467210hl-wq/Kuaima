package com.kuaima.app.domain.boss.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.kuaima.app.domain.base.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "boss_order", indexes = {
        @Index(name = "idx_boss_order_owner_status", columnList = "create_by,order_status"),
        @Index(name = "idx_boss_order_start_time", columnList = "start_time"),
        @Index(name = "idx_boss_order_job_category", columnList = "job_category_id"),
        @Index(name = "idx_boss_order_salary", columnList = "salary")
})
@Setter
@Getter
public class BossOrder extends BaseEntity {
    public void setSalary(Integer salary) { this.salary = salary == null ? null : BigDecimal.valueOf(salary); }
    public void setSalary(int salary) { this.salary = BigDecimal.valueOf(salary); }
    public void setSalary(BigDecimal salary) { this.salary = salary; }

    /** 订单所属企业；createBy 继续保留为实际操作人。 */
    @Column(name = "enterprise_id")
    private Long enterpriseId;

    /** 公开岗位详情展示用企业名称，不落库。 */
    @Transient
    private String companyName;

    @Column(comment = "订单标题")
    private String orderTitle;

    /** 对外展示和检索的业务订单号，不暴露数据库主键。 */
    @Column(name = "order_no", length = 40, unique = true)
    private String orderNo;

    @Column(comment = "订单状态:招工中,招工结束,待结算，已完成，取消招工")
    private String orderStatus;

    @Column(comment = "招工类型:每天日结(daily)/压薪日结(heldBack)/月结(month)")
    private String type;

    @Column(comment = "招工内容", length = 500)
    private String orderContent;

    @Column(comment = "订单备注")
    private String orderRemark;

    @Column(comment = "招聘岗位")
    private String postion;

    @Column(comment = "招工人数")
    private Integer orderNum;

    @Column(comment = "工作时长：日结/小时，压薪日结/天")
    private Integer duration;

    @Column(comment = "工作地点")
    private String address;

    @Column(comment = "标签")
    private String tags;

    /** 招工详情图片 OSS objectKey，多个用英文逗号分隔；数据库不保存签名 URL。 */
    @Column(name = "image_keys", length = 1000)
    private String imageKeys;

    /** 招工详情视频 OSS objectKey；数据库不保存签名 URL。 */
    @Column(name = "video_key", length = 500)
    private String videoKey;

    @Column(comment = "开始时间")
    private Date startTime;

    @Column(comment = "结束时间")
    private Date endTime;

    @Column(precision = 18, scale = 2, comment = "工资（元/天）")
    private BigDecimal salary;

    @Column(comment = "月结才有试工时间")
    private String trialDuration;

    @Column(name = "job_category_id", comment = "工种分类ID")
    private Long jobCategoryId;

    @Column(name = "industry_id") private Long industryId;
    @Column(name = "enterprise_type_ids", length = 500) private String enterpriseTypeIds;
    @Column(name = "job_ids", length = 500) private String jobIds;

    @Column(precision = 10, scale = 7, comment = "工作地点经度")
    private BigDecimal longitude;

    @Column(precision = 10, scale = 7, comment = "工作地点纬度")
    private BigDecimal latitude;

    @Column(length = 30, comment = "经验要求编码")
    private String experience;

    @Column(length = 20, comment = "性别要求编码")
    private String gender;

    @Column(name = "sign_mode", length = 10, comment = "报名方式：auto自动通过/manual手动审核")
    private String signMode;

    @Column(name = "phone_notify", comment = "报名电话通知")
    private Boolean phoneNotify;

    @Column(name = "sign_notify", comment = "零工报名通知")
    private Boolean signNotify;

    @Column(name = "start_remind", comment = "开工提醒")
    private Boolean startRemind;

    @Column(name = "settle_notify", comment = "结算通知")
    private Boolean settleNotify;

    @Column(length = 50, comment = "审核人（管理员用户名）")
    private String auditBy;

    @Column(comment = "审核时间")
    private Date auditTime;

    /** 列表展示用：有效报名数，不落库 */
    @Transient
    private Long currentApply;

    /** 发布订单时选择的用户优惠券领取记录 ID，仅作为请求参数，不落订单表。 */
    @Transient
    private Long userCouponId;

    /** 创建/编辑岗位时引用的企业常用地址 ID，不落库。 */
    @Transient
    private Long addressId;

    /** 展示用图片签名 URL，不落库。 */
    @Transient
    private java.util.List<String> imageUrls;

    /** 展示用视频签名 URL，不落库。 */
    @Transient
    private String videoUrl;

}
