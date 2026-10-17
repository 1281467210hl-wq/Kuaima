package com.kuaima.app.controller.boss;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.common.Result;
import com.kuaima.app.domain.boss.service.RecruitRuleSettings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 招工规则（只读）：小程序发布页、报名页据此做前置提示。 */
@RestController
@RequestMapping("/public/recruit-rules")
@Tag(name = "招工规则", description = "后台系统设置中的招工规则")
public class PublicRecruitRulesController {

    private final RecruitRuleSettings rules;

    public PublicRecruitRulesController(RecruitRuleSettings rules) {
        this.rules = rules;
    }

    @Operation(summary = "招工规则", description = "jobAudit 招工审核、realNameRequired 实名要求、minDailyWage 最低日薪（元，未配置为 null）、maxJobValidity 工作日期区间最多天数（未配置为 null）")
    @GetMapping
    public Result<Map<String, Object>> get() {
        return Result.success(rules.view());
    }
}
