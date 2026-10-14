package com.kuaima.app.domain.enterprise.constant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 企业成员工作台统一权限码常量。
 *
 * <p>收敛背景：
 * <ul>
 *   <li>admin-web 企业成员页（EnterpriseMember.vue）的 6 项核心工作台权限开关
 *       （岗位发布/成员管理/制单发薪/发薪审批/考勤统计/记录导出）对应本类 CORE 常量；
 *       EnterpriseMemberService.PERMISSIONS 里硬编码的同名中文权限（"岗位发布与编辑"
 *       "成员管理与邀请"等，顺序即 permIndexes 0..5）通过 {@link #normalize(Object)}
 *       归一化为这些 code，历史存量数据无需迁移。</li>
 *   <li>老板端 EnterpriseContextService、SubAccountController、BossController 已在使用的
 *       HOME_VIEW / ORDER_VIEW / MEMBER_WRITE 等 *_VIEW、*_WRITE 码原样保留于此，
 *       后端新接口统一按「大写 code」写入 enterprise_member.permissions JSON 数组，
 *       读侧（Context.can / 子账号权限判断）天然兼容。</li>
 *   <li>前端模块小写 key（resume_pool、job_manage、batch_payroll 等）也收录进别名表，
 *       前端可直接回传原始 key，由后端归一化。</li>
 * </ul>
 */
public final class WorkbenchPermCodes {

    private WorkbenchPermCodes() {
    }

    /* ==================== 6 项核心工作台权限开关 ==================== */

    /** 岗位发布（对应 EnterpriseMemberService.PERMISSIONS[0]"岗位发布与编辑"）。 */
    public static final String POSITION_PUBLISH = "POSITION_PUBLISH";
    /** 成员管理（对应 PERMISSIONS[1]"成员管理与邀请"）。 */
    public static final String MEMBER_MANAGE = "MEMBER_MANAGE";
    /** 制单发薪（对应 PERMISSIONS[2]"制单发薪"）。 */
    public static final String PAYROLL_CREATE = "PAYROLL_CREATE";
    /** 发薪审批（对应 PERMISSIONS[3]"发薪审批"）。 */
    public static final String PAYROLL_APPROVE = "PAYROLL_APPROVE";
    /** 考勤统计（对应 PERMISSIONS[4]"考勤统计"）。 */
    public static final String ATTENDANCE_STATS = "ATTENDANCE_STATS";
    /** 记录导出（对应 PERMISSIONS[5]"记录导出"）。 */
    public static final String EXPORT_RECORDS = "EXPORT_RECORDS";

    /** 6 项核心权限，顺序与 EnterpriseMemberService.permIndexes 的 0..5 一致。 */
    public static final List<String> CORE = List.of(
            POSITION_PUBLISH, MEMBER_MANAGE, PAYROLL_CREATE, PAYROLL_APPROVE, ATTENDANCE_STATS, EXPORT_RECORDS);

    /* ==================== 既有权端权限码（复用，不重命名） ==================== */

    /** 工作台首页（SubAccountController / EnterpriseContextService 既有码）。 */
    public static final String HOME_VIEW = "HOME_VIEW";
    /** 工作台总览（SubAccountController 既有码）。 */
    public static final String WORKBENCH_VIEW = "WORKBENCH_VIEW";
    /** 用工订单查看（BossController / ContextService 既有码）。 */
    public static final String ORDER_VIEW = "ORDER_VIEW";
    /** 成员查看（boss/enterprise EnterpriseMemberController 既有码）。 */
    public static final String MEMBER_VIEW = "MEMBER_VIEW";
    /** 成员编辑（既有写权限码）。 */
    public static final String MEMBER_WRITE = "MEMBER_WRITE";
    /** 人才库查看（EnterpriseContextService.RECRUITER 默认权限）。 */
    public static final String TALENT_VIEW = "TALENT_VIEW";
    public static final String ADDRESS_VIEW = "ADDRESS_VIEW";
    public static final String ADDRESS_WRITE = "ADDRESS_WRITE";
    public static final String SETTINGS_VIEW = "SETTINGS_VIEW";
    public static final String SETTINGS_WRITE = "SETTINGS_WRITE";
    public static final String ATTENDANCE_VIEW = "ATTENDANCE_VIEW";
    public static final String SETTLEMENT_VIEW = "SETTLEMENT_VIEW";
    public static final String COUPON_VIEW = "COUPON_VIEW";
    public static final String COUPON_WRITE = "COUPON_WRITE";
    public static final String MESSAGE_VIEW = "MESSAGE_VIEW";
    public static final String REPORT_VIEW = "REPORT_VIEW";

    /* ==================== admin-web 前端模块补充权限码 ==================== */

    /** 简历库。 */
    public static final String RESUME_VIEW = "RESUME_VIEW";
    /** 项目管理。 */
    public static final String PROJECT_MANAGE = "PROJECT_MANAGE";
    /** 直播橱窗。 */
    public static final String LIVE_SHOWCASE = "LIVE_SHOWCASE";
    /** 候选人。 */
    public static final String CANDIDATE_MANAGE = "CANDIDATE_MANAGE";
    /** 转账记录。 */
    public static final String TRANSFER_VIEW = "TRANSFER_VIEW";
    /** 余额查询。 */
    public static final String BALANCE_VIEW = "BALANCE_VIEW";

    /* ==================== 定义目录（GET workbench-perms 返回） ==================== */

    /** 权限定义项：code + 分组 + 中文名。 */
    public record Entry(String code, String group, String name) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(HOME_VIEW, "工作台", "首页"),
            new Entry(WORKBENCH_VIEW, "工作台", "工作台总览"),
            new Entry(POSITION_PUBLISH, "智能招聘", "岗位发布"),
            new Entry(RESUME_VIEW, "智能招聘", "简历库"),
            new Entry(LIVE_SHOWCASE, "智能招聘", "直播橱窗"),
            new Entry(CANDIDATE_MANAGE, "智能招聘", "候选人"),
            new Entry(ORDER_VIEW, "智能招聘", "用工订单查看"),
            new Entry(TALENT_VIEW, "智能招聘", "人才库查看"),
            new Entry(MEMBER_VIEW, "企业与成员", "成员查看"),
            new Entry(MEMBER_MANAGE, "企业与成员", "成员管理"),
            new Entry(MEMBER_WRITE, "企业与成员", "成员编辑"),
            new Entry(PROJECT_MANAGE, "企业与成员", "项目管理"),
            new Entry(ADDRESS_VIEW, "企业与成员", "地址查看"),
            new Entry(ADDRESS_WRITE, "企业与成员", "地址编辑"),
            new Entry(SETTINGS_VIEW, "企业与成员", "设置查看"),
            new Entry(SETTINGS_WRITE, "企业与成员", "设置编辑"),
            new Entry(PAYROLL_CREATE, "闪电发薪", "制单发薪"),
            new Entry(PAYROLL_APPROVE, "闪电发薪", "发薪审批"),
            new Entry(TRANSFER_VIEW, "闪电发薪", "转账记录"),
            new Entry(BALANCE_VIEW, "闪电发薪", "余额查询"),
            new Entry(SETTLEMENT_VIEW, "闪电发薪", "结算查看"),
            new Entry(COUPON_VIEW, "闪电发薪", "卡券查看"),
            new Entry(COUPON_WRITE, "闪电发薪", "卡券操作"),
            new Entry(ATTENDANCE_STATS, "考勤与报表", "考勤统计"),
            new Entry(ATTENDANCE_VIEW, "考勤与报表", "考勤查看"),
            new Entry(REPORT_VIEW, "考勤与报表", "报表查看"),
            new Entry(EXPORT_RECORDS, "考勤与报表", "记录导出"),
            new Entry(MESSAGE_VIEW, "消息", "消息查看"));

    /** code → 中文名。 */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    /** 中文权限名 / 前端小写 key → 统一 code 的别名表。 */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        for (Entry entry : ENTRIES) {
            NAMES.put(entry.code(), entry.name());
        }
        alias(HOME_VIEW, "首页", "home");
        alias(WORKBENCH_VIEW, "工作台");
        alias(POSITION_PUBLISH, "岗位发布", "岗位发布与编辑", "岗位管理", "job_manage");
        alias(RESUME_VIEW, "简历库", "resume_pool");
        alias(LIVE_SHOWCASE, "直播橱窗", "live_showcase");
        alias(CANDIDATE_MANAGE, "候选人", "candidate");
        alias(ORDER_VIEW, "订单", "用工订单");
        alias(TALENT_VIEW, "人才库");
        alias(MEMBER_MANAGE, "企业成员", "成员管理", "成员管理与邀请", "员工列表", "member_list");
        alias(PROJECT_MANAGE, "项目管理", "project_manage", "project");
        alias(PAYROLL_CREATE, "制单发薪", "批量发薪", "batch_payroll");
        alias(PAYROLL_APPROVE, "发薪审批", "待我审批", "payroll_approve");
        alias(TRANSFER_VIEW, "转账记录", "transfer_record");
        alias(BALANCE_VIEW, "余额查询", "balance_view");
        alias(SETTLEMENT_VIEW, "结算");
        alias(ATTENDANCE_STATS, "考勤统计", "考勤", "attendance", "考勤打卡", "attend_check");
        alias(ATTENDANCE_VIEW, "考勤查看");
        alias(EXPORT_RECORDS, "记录导出", "导出");
        alias(REPORT_VIEW, "报表");
        alias(MESSAGE_VIEW, "消息", "站内消息");
    }

    private static void alias(String code, String... keys) {
        for (String key : keys) {
            ALIASES.put(key, code);
        }
    }

    /** 权限定义目录，供前端渲染开关树。 */
    public static List<Map<String, Object>> catalog() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", entry.code());
            item.put("group", entry.group());
            item.put("name", entry.name());
            item.put("core", CORE.contains(entry.code()));
            list.add(item);
        }
        return list;
    }

    /**
     * 把任意来源的权限项（大写 code / 中文权限名 / 前端小写 key）归一化为统一 code 列表；
     * 未知但形如 [A-Z0-9_] 的值原样保留（向后兼容将来新增码），其余丢弃。
     */
    public static List<String> normalize(Object permissions) {
        if (permissions == null) {
            return List.of();
        }
        Collection<?> items = permissions instanceof Collection<?> collection
                ? collection : List.of(String.valueOf(permissions));
        Set<String> result = new LinkedHashSet<>();
        for (Object raw : items) {
            if (raw == null) {
                continue;
            }
            String value = String.valueOf(raw).trim();
            if (value.isEmpty()) {
                continue;
            }
            String code = ALIASES.get(value);
            if (code == null) {
                code = ALIASES.get(value.toLowerCase(Locale.ROOT));
            }
            if (code == null) {
                String upper = value.toUpperCase(Locale.ROOT);
                if (upper.matches("[A-Z0-9_]{2,60}")) {
                    code = upper;
                }
            }
            if (code != null) {
                result.add(code);
            }
        }
        return new ArrayList<>(result);
    }

    /** code → 中文名；未知 code 回退其本身。 */
    public static String label(String code) {
        return NAMES.getOrDefault(code, code);
    }

    /**
     * 角色默认工作台权限，与 EnterpriseMemberService.permIndexes 对齐：
     * OWNER 全开（0..5），ADMIN 不含发薪审批（0,1,2,4,5），其余为空。
     */
    public static List<String> defaultCodes(String memberRole) {
        if ("OWNER".equals(memberRole)) {
            return CORE;
        }
        if ("ADMIN".equals(memberRole)) {
            return List.of(POSITION_PUBLISH, MEMBER_MANAGE, PAYROLL_CREATE, ATTENDANCE_STATS, EXPORT_RECORDS);
        }
        return List.of();
    }

    /** 与 EnterpriseMemberService.permIndexes 对齐的权限下标（CORE 的 0..5 顺序）。 */
    public static List<Integer> permIndexes(String memberRole) {
        List<String> defaults = defaultCodes(memberRole);
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < CORE.size(); i++) {
            if (defaults.contains(CORE.get(i))) {
                indexes.add(i);
            }
        }
        return indexes;
    }
}
