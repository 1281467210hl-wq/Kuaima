package com.kuaima.app.admin.interceptor;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.kuaima.app.admin.repository.MessageTemplateRepository;
import com.kuaima.app.admin.service.AdminLogService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 操作日志拦截器：自动记录 /admin/** 下的所有写操作（POST/PUT/DELETE）。
 * - 登录接口（/admin/auth/login）由 AdminAuthController 自行记录，此处跳过。
 * - 日志查询接口（/admin/logs）跳过，避免递归。
 * - 操作对象 target 记录为菜单中文名称（含二级菜单，如「用户管理/零工管理 #5」），完整 URI 记录到 detail。
 * - 操作类型：登录/新建/编辑/删除/审核/启用/禁用/重置密码。
 */
@Component
public class AdminLogInterceptor implements HandlerInterceptor {

    private final AdminLogService logService;
    private final MessageTemplateRepository templateRepository;
    private final UserRepository userRepository;

    public AdminLogInterceptor(AdminLogService logService,
                               MessageTemplateRepository templateRepository,
                               UserRepository userRepository) {
        this.logService = logService;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
    }

    /** URI 前缀 → 菜单中文名称（一级/二级，按前缀从长到短匹配） */
    private static final LinkedHashMap<String, String> MODULE_MAP = new LinkedHashMap<>();
    static {
        MODULE_MAP.put("/admin/message-templates", "系统设置/通知模板");
        MODULE_MAP.put("/admin/admin-users", "系统设置/权限管理");
        MODULE_MAP.put("/admin/certifications", "内容管理/认证审核");
        MODULE_MAP.put("/admin/settlements", "订单结算/结算管理");
        MODULE_MAP.put("/admin/blacklists", "系统设置/黑名单管理");
        MODULE_MAP.put("/admin/dashboard", "数据统计/数据概览");
        MODULE_MAP.put("/admin/notices", "内容管理/公告管理");
        MODULE_MAP.put("/admin/rules", "内容管理/规则管理");
        MODULE_MAP.put("/admin/banners", "内容管理/Banner管理");
        MODULE_MAP.put("/admin/settings", "系统管理/系统设置");
        MODULE_MAP.put("/admin/service", "消息客服/客服管理");
        MODULE_MAP.put("/admin/reports", "消息客服/举报处理");
        MODULE_MAP.put("/admin/orders", "订单结算/用工订单");
        MODULE_MAP.put("/admin/point-purchase", "营销管理/积分购买");
        MODULE_MAP.put("/admin/points/withdrawals", "营销管理/积分提现");
        MODULE_MAP.put("/admin/coupons", "营销管理/优惠券管理");
        MODULE_MAP.put("/admin/rewards", "营销管理/奖励金管理");
        MODULE_MAP.put("/admin/auth", "后台登录");
        // 企业工作台
        MODULE_MAP.put("/admin/transfers", "企业工作台/闪电发薪");
        MODULE_MAP.put("/admin/merchant-accounts", "企业工作台/闪电发薪");
        MODULE_MAP.put("/admin/projects", "企业工作台/项目管理");
        MODULE_MAP.put("/admin/positions", "企业工作台/智能招聘");
        MODULE_MAP.put("/admin/resumes", "企业工作台/智能招聘");
        MODULE_MAP.put("/admin/candidates", "企业工作台/智能招聘");
        MODULE_MAP.put("/admin/live-showcases", "企业工作台/智能招聘");
        MODULE_MAP.put("/admin/enterprises", "企业工作台/企业成员");
    }

    private static final Pattern ID_PATTERN = Pattern.compile("/(\\d+)");

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (uri == null || !uri.startsWith("/admin/")) return;
        if (uri.startsWith("/admin/auth/login")) return;        // 登录由控制器记录
        if (uri.startsWith("/admin/logs")) return;              // 跳过日志自身
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) return;
        // 管理员标记公告已读，属个人行为，不记业务操作日志
        if (uri.matches("(?i)/admin/notices/\\d+/read")) return;

        // 操作人
        String operator = "system";
        Long operatorId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser u) {
            operator = u.username();
            operatorId = u.id();
        }

        String module = resolveModule(uri);
        String resourceId = extractId(uri);
        String target = module + (resourceId != null ? " #" + resourceId : "");
        String body = readBody(request);
        String type = resolveType(method, uri, body);
        if (type == null) return;   // 无需记录的动作（如标记最近使用）

        String result = (response.getStatus() >= 200 && response.getStatus() < 300) ? "成功" : "失败";
        String ip = clientIp(request);
        String safeBody = maskSensitive(body);
        String detail = method + " " + uri
                + (safeBody != null && !safeBody.isBlank() ? " | body=" + safeBody : "")
                + (ex != null ? " | ex=" + ex.getMessage() : "");

        logService.recordAsync(operator, operatorId, type, target, ip, result, detail);
    }

    /** 解析 URI 对应的菜单中文名称（一级/二级菜单） */
    private String resolveModule(String uri) {
        // 用户管理：按被操作用户的角色细分二级菜单（企业认证接口固定归企业认证）
        if (uri.startsWith("/admin/users")) {
            if (uri.matches("^/admin/users/\\d+/enterprise/.*")) return "用户管理/企业认证";
            String id = extractId(uri);
            if (id != null) {
                try {
                    User u = userRepository.findById(Long.valueOf(id)).orElse(null);
                    if (u != null) {
                        return UserRole.BOSS.equals(u.getRole()) ? "用户管理/老板管理" : "用户管理/零工管理";
                    }
                } catch (Exception ignored) {
                    // 查库失败时退回一级菜单
                }
            }
            return "用户管理";
        }
        // 招工管理：审核接口归招工审核，其余归招工管理
        if (uri.startsWith("/admin/jobs")) {
            if (uri.matches("^/admin/jobs/\\d+/audit/.*")) return "招工管理/招工审核";
            return "招工管理";
        }
        for (Map.Entry<String, String> e : MODULE_MAP.entrySet()) {
            if (uri.startsWith(e.getKey())) return e.getValue();
        }
        return "后台操作";
    }

    /** 提取 URI 中的第一段数字作为资源 ID */
    private String extractId(String uri) {
        Matcher m = ID_PATTERN.matcher(uri);
        return m.find() ? m.group(1) : null;
    }

    /** 读取被缓存的 JSON 请求体 */
    private String readBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper cw) {
            byte[] buf = cw.getContentAsByteArray();
            if (buf.length > 0) {
                String enc = cw.getCharacterEncoding() != null ? cw.getCharacterEncoding() : StandardCharsets.UTF_8.name();
                try {
                    return new String(buf, enc);
                } catch (Exception e) {
                    return new String(buf, StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }

    /** 从请求体 JSON 中取 status 字段 */
    private String bodyStatus(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JSONObject json = JSON.parseObject(body);
            return json == null ? null : json.getString("status");
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(\"(?:password|newPassword|oldPassword|pwd)\"\\s*:\\s*\")([^\"]*)(\")");

    /** 脱敏请求体中的密码字段，避免明文落日志 */
    private String maskSensitive(String body) {
        if (body == null || body.isBlank()) return body;
        return SENSITIVE_PATTERN.matcher(body).replaceAll("$1***$3");
    }

    /**
     * 推断操作类型。返回 null 表示该动作不记录。
     */
    private String resolveType(String method, String uri, String body) {
        String lower = uri.toLowerCase();

        // 1. 重置密码
        if (lower.contains("reset-password")) return "重置密码";

        // 2. 审核（招工审核、认证审核、举报处理）
        if (lower.contains("/audit/")
                || (lower.startsWith("/admin/certifications/") && (lower.endsWith("/pass") || lower.endsWith("/reject")))
                || lower.matches(".*/reports/\\d+/handle")) {
            return "审核";
        }

        // 3. 启用 / 禁用
        if (lower.endsWith("/unfreeze") || lower.endsWith("/unfreeze/batch")) return "启用";
        if (lower.endsWith("/freeze") || lower.endsWith("/freeze/batch")) return "禁用";
        if (lower.endsWith("/toggle")) {
            // 只有通知模板需要读取切换后的状态；其他模块不得误查通知模板表。
            String id = lower.startsWith("/admin/message-templates/") ? extractId(uri) : null;
            if (id != null) {
                try {
                    return templateRepository.findById(Long.valueOf(id))
                            .map(t -> "enabled".equalsIgnoreCase(t.getStatus()) ? "启用" : "禁用")
                            .orElse("启用/禁用");
                } catch (Exception e) {
                    return "启用/禁用";
                }
            }
            return "启用/禁用";
        }
        // 管理员账号通过整体更新切换状态（body.status 为 启用/禁用）
        if ("PUT".equalsIgnoreCase(method) && lower.startsWith("/admin/admin-users/")) {
            String status = bodyStatus(body);
            if ("启用".equals(status)) return "启用";
            if ("禁用".equals(status)) return "禁用";
        }

        // 4. 删除
        if ("DELETE".equalsIgnoreCase(method)) return "删除";

        // 5. POST：默认新建，动作型接口归入编辑
        if ("POST".equalsIgnoreCase(method)) {
            if (lower.endsWith("/test-send")) return "编辑";          // 测试发送
            if (lower.matches(".*/settlements/\\d+/pay")) return "编辑"; // 结算付款
            if (lower.matches(".*/service/sessions/\\d+/messages")) return "编辑"; // 客服回复
            if (lower.matches(".*/reports/\\d+/handle")) return "审核";
            return "新建";
        }

        // 6. PUT：标记最近使用不记录，其余为编辑
        if ("PUT".equalsIgnoreCase(method)) {
            if (lower.endsWith("/used")) return null;
            return "编辑";
        }
        return "编辑";
    }

    /** 获取真实客户端 IP（穿透 Nginx 等代理） */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) return ip.trim();
        ip = request.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) return ip.trim();
        return request.getRemoteAddr();
    }
}
