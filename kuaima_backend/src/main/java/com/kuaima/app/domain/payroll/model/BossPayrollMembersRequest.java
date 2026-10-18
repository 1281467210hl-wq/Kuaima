package com.kuaima.app.domain.payroll.model;

import java.util.List;

import com.kuaima.app.domain.payroll.entity.PayrollDetail;

import lombok.Getter;
import lombok.Setter;

/** 老板端「草稿-添加/替换发薪人员」与「设置薪资」请求体。 */
@Getter
@Setter
public class BossPayrollMembersRequest {

    /** 添加/替换人员：{userId,name,phone,job,dailyWage?,attendDays?} */
    private List<PayrollDetail> members;

    /** 设置薪资：{userId,phone,dailyWage(分),attendDays} */
    private List<PayrollDetail> salaries;
}
