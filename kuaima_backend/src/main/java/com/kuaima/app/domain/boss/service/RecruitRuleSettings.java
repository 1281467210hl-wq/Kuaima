package com.kuaima.app.domain.boss.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.kuaima.app.admin.entity.AdminSetting;
import com.kuaima.app.admin.repository.AdminSettingRepository;

/**
 * 后台「系统设置 - 招工规则」的读取口径。
 * 未配置时：招工审核、实名要求默认开启；最低日薪、最长有效期不限制。
 */
@Component
public class RecruitRuleSettings {

    public static final String JOB_AUDIT = "rules.jobAudit";
    public static final String REALNAME_REQUIRED = "rules.realNameRequired";
    public static final String MIN_DAILY_WAGE = "rules.minDailyWage";
    public static final String MAX_JOB_VALIDITY = "rules.maxJobValidity";

    private final AdminSettingRepository settings;

    public RecruitRuleSettings(AdminSettingRepository settings) {
        this.settings = settings;
    }

    public boolean jobAuditEnabled() {
        return bool(JOB_AUDIT, true);
    }

    public boolean realnameRequired() {
        return bool(REALNAME_REQUIRED, true);
    }

    /** 最低日薪（元）；未配置或不大于 0 时返回 empty。 */
    public Optional<BigDecimal> minDailyWage() {
        return value(MIN_DAILY_WAGE).map(this::decimal).filter(v -> v.signum() > 0);
    }

    /** 工作日期区间最多天数；未配置或不大于 0 时返回 empty。 */
    public Optional<Integer> maxJobValidityDays() {
        return value(MAX_JOB_VALIDITY).map(this::decimal).filter(v -> v.signum() > 0).map(BigDecimal::intValue);
    }

    /** 提供给小程序发布页、报名页做前置提示；以后端校验为准。 */
    public Map<String, Object> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("jobAudit", jobAuditEnabled());
        view.put("realNameRequired", realnameRequired());
        view.put("minDailyWage", minDailyWage().orElse(null));
        view.put("maxJobValidity", maxJobValidityDays().orElse(null));
        return view;
    }

    private boolean bool(String key, boolean defaultValue) {
        return value(key).map(v -> !"false".equalsIgnoreCase(v)).orElse(defaultValue);
    }

    private Optional<String> value(String key) {
        if (settings == null) return Optional.empty();
        return settings.findById(key).map(AdminSetting::getSettingValue).filter(StringUtils::hasText).map(String::trim);
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
