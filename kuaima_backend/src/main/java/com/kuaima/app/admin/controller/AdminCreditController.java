package com.kuaima.app.admin.controller;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.UUID;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.entity.CreditFlow;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.user.repository.AdminCreditDetailRow;
import com.kuaima.app.domain.user.repository.CreditFlowRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.constant.UserBusinessCode;
import com.kuaima.app.domain.user.service.CreditScoreService;
import com.kuaima.app.domain.user.service.CreditRuleNames;
import com.kuaima.app.security.model.LoginUser;

import jakarta.persistence.EntityNotFoundException;

/** 后台信用分/零工星级分查询及人工调整。 */
@RestController
@RequestMapping("/admin/credit")
public class AdminCreditController {
    private final UserRepository users;
    private final CreditScoreService scores;
    private final CreditFlowRepository flows;

    public AdminCreditController(UserRepository users, CreditScoreService scores) {
        this(users, scores, null);
    }

    @Autowired
    public AdminCreditController(UserRepository users, CreditScoreService scores, CreditFlowRepository flows) {
        this.users = users;
        this.scores = scores;
        this.flows = flows;
    }

    @GetMapping("/users")
    public Result<Page<Map<String, Object>>> users(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String id,
                                                    @RequestParam(required = false) String role,
                                                    @RequestParam(required = false) String level,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    Authentication authentication) {
        admin(authentication, false);
        String rawKeyword = id != null && !id.isBlank() ? id : keyword;
        String kw = rawKeyword == null || rawKeyword.isBlank() ? null : rawKeyword.trim().toLowerCase(Locale.ROOT);
        String normalizedRole = normalizeRole(role);
        if (role != null && !role.isBlank() && normalizedRole == null) {
            throw new IllegalArgumentException("role 参数无效");
        }
        List<User> candidates = users.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(user -> normalizedRole == null
                        ? (UserRole.isBossIdentity(user) || UserRole.isWorkerIdentity(user))
                        : (UserRole.BOSS.equals(normalizedRole) ? UserRole.isBossIdentity(user) : UserRole.isWorkerIdentity(user)))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User user : candidates) {
            boolean boss = UserRole.isBossIdentity(user);
            String businessId = ensureBusinessId(user, boss);
            String name = displayName(user);
            if (kw != null && !containsAny(kw, businessId, user.getId(), name, user.getUsername(), user.getNickname(), user.getPhone())) continue;
            String scoreType = boss
                    ? CreditScoreService.BOSS_CREDIT : CreditScoreService.WORKER_STAR;
            int score = boss ? value(user.getCreditScore()) : value(user.getStarScore());
            String levelKey = levelKey(score);
            if (level != null && !level.isBlank() && !levelKey.equals(level)) continue;
            List<CreditFlow> history = flows == null ? List.of()
                    : flows.findByUserIdAndScoreTypeOrderByTimestampDesc(user.getId(), scoreType);
            int added = history.stream().mapToInt(CreditFlow::getDelta).filter(delta -> delta > 0).sum();
            int deducted = history.stream().mapToInt(CreditFlow::getDelta).filter(delta -> delta < 0).map(Math::abs).sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", businessId); row.put("userId", user.getId()); row.put("role", boss ? "boss" : "worker");
            row.put("name", name); row.put("phone", user.getPhone()); row.put("score", score);
            row.put("level", levelKey); row.put("addTotal", added); row.put("subTotal", deducted);
            row.put("scoreType", scoreType);
            rows.add(row);
        }
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), rows.size());
        int to = Math.min(from + Math.max(size, 1), rows.size());
        Page<Map<String, Object>> result = new PageImpl<>(rows.subList(from, to), PageRequest.of(Math.max(page, 0), Math.max(size, 1)), rows.size());
        return Result.success(result, result.getNumber(), result.getTotalElements());
    }

    @GetMapping("/{userId}")
    public Result<Map<String, Object>> detail(@PathVariable String userId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              Authentication authentication) {
        return detailPage(userId, page, size, authentication);
    }

    /** 兼容已有内部调用：默认返回第 1 页、每页 10 条。 */
    public Result<Map<String, Object>> detail(String userId, Authentication authentication) {
        return detailPage(userId, 0, 10, authentication);
    }

    private Result<Map<String, Object>> detailPage(String userId, int page, int size, Authentication authentication) {
        admin(authentication, false);
        List<AdminCreditDetailRow> rows = users.findAdminCreditDetail(userId.trim());
        if (rows.isEmpty()) throw new EntityNotFoundException("用户不存在: " + userId);
        AdminCreditDetailRow first = rows.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", first.getBusinessId());
        data.put("userId", first.getUserId());
        data.put("creditScore", value(first.getCreditScore()));
        data.put("starScore", value(first.getStarScore()));
        data.put("scoreType", first.getScoreType());
        List<Map<String, Object>> allFlowViews = rows.stream()
                .filter(row -> row.getFlowId() != null)
                .map(this::flowView)
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<CreditFlow> flowPage = flows == null ? null
                : flows.findByUserIdAndScoreTypeOrderByTimestampDesc(first.getUserId(), first.getScoreType(),
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp", "id")));
        List<Map<String, Object>> currentFlows;
        long totalFlows;
        if (flowPage == null) {
            int from = Math.min(safePage * safeSize, allFlowViews.size());
            int to = Math.min(from + safeSize, allFlowViews.size());
            currentFlows = allFlowViews.subList(from, to);
            totalFlows = allFlowViews.size();
        } else {
            currentFlows = flowPage.stream().map(this::flowView).toList();
            totalFlows = flowPage.getTotalElements();
        }
        data.put("creditFlows", currentFlows);
        data.put("starFlows", currentFlows);
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("total", totalFlows);
        data.put("totalPages", totalFlows == 0 ? 0 : (totalFlows + safeSize - 1) / safeSize);
        List<CreditFlow> allFlows = flows == null ? List.of()
                : flows.findByUserIdAndScoreTypeOrderByTimestampDesc(first.getUserId(), first.getScoreType());
        int addTotal = allFlows.isEmpty()
                ? allFlowViews.stream().mapToInt(flow -> numberOrZero(flow.get("delta"))).filter(delta -> delta > 0).sum()
                : allFlows.stream().mapToInt(flow -> value(flow.getDelta())).filter(delta -> delta > 0).sum();
        int subTotal = allFlows.isEmpty()
                ? allFlowViews.stream().mapToInt(flow -> numberOrZero(flow.get("delta"))).filter(delta -> delta < 0).map(Math::abs).sum()
                : allFlows.stream().mapToInt(flow -> value(flow.getDelta())).filter(delta -> delta < 0).map(Math::abs).sum();
        data.put("addTotal", addTotal);
        data.put("subTotal", subTotal);
        data.put("flowLimit", 10);
        return Result.success(data);
    }

    private int numberOrZero(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Map<String, Object> flowView(AdminCreditDetailRow row) {
        Map<String, Object> flow = new LinkedHashMap<>();
        String businessId = row.getFlowBizNo() == null ? legacyFlowNo(row) : row.getFlowBizNo();
        flow.put("id", businessId);
        flow.put("businessId", businessId);
        flow.put("delta", row.getDelta());
        flow.put("beforeScore", row.getBeforeScore());
        flow.put("afterScore", row.getAfterScore());
        flow.put("ruleCode", CreditRuleNames.display(row.getRuleCode(), row.getDelta()));
        flow.put("bizType", row.getBizType());
        flow.put("reason", row.getReason());
        flow.put("timestamp", row.getFlowTimestamp());
        return flow;
    }

    private Map<String, Object> flowView(CreditFlow row) {
        Map<String, Object> flow = new LinkedHashMap<>();
        String businessId = row.getBizNo() == null ? legacyFlowNo(row) : row.getBizNo();
        flow.put("id", businessId);
        flow.put("businessId", businessId);
        flow.put("delta", row.getDelta());
        flow.put("beforeScore", row.getBeforeScore());
        flow.put("afterScore", row.getAfterScore());
        flow.put("ruleCode", CreditRuleNames.display(row.getRuleCode(), row.getDelta()));
        flow.put("bizType", row.getBizType());
        flow.put("reason", row.getReason());
        flow.put("timestamp", row.getTimestamp());
        return flow;
    }

    private String legacyFlowNo(CreditFlow row) {
        String date = row.getTimestamp() == null ? "00000000"
                : row.getTimestamp().toLocalDateTime().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "XF" + date + String.format("%03d", Math.max(0, row.getId() == null ? 0 : row.getId()));
    }

    private String legacyFlowNo(AdminCreditDetailRow row) {
        String date = row.getFlowTimestamp() == null ? "00000000"
                : row.getFlowTimestamp().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "XF" + date + String.format("%03d", Math.max(0, row.getFlowId() == null ? 0 : row.getFlowId()));
    }

    @PostMapping("/{userId}/adjust")
    public Result<Map<String, Object>> adjust(@PathVariable String userId, @RequestBody Map<String, Object> body,
                                               Authentication authentication) {
        LoginUser operator = admin(authentication, true);
        String type = text(body, "scoreType", CreditScoreService.BOSS_CREDIT);
        User target = resolveUser(userId);
        Long internalUserId = target.getId();
        String expectedType = scoreType(target);
        if (!expectedType.equals(type)) throw new IllegalArgumentException("scoreType 与用户身份不匹配");
        int delta = number(body.get("delta"));
        if (delta == 0) throw new IllegalArgumentException("delta 不能为0");
        int currentScore = type.equals(CreditScoreService.WORKER_STAR) ? value(target.getStarScore()) : value(target.getCreditScore());
        if (delta < 0 && currentScore == 0) throw new IllegalArgumentException("当前信用分为0，不支持扣分");
        if (delta < 0 && -((long) delta) > currentScore) throw new IllegalArgumentException("扣分不能超过当前信用分");
        String reason = text(body, "reason", "管理员人工调整");
        String ruleCode = text(body, "ruleCode", "ADMIN_MANUAL_ADJUST");
        String bizId = text(body, "bizId", String.valueOf(System.currentTimeMillis()));
        boolean adjusted = scores.adjust(internalUserId, type, delta, ruleCode, "ADMIN",
                "ADMIN:" + operator.id() + ":" + internalUserId + ":" + bizId + ":" + UUID.randomUUID(), reason);
        if (!adjusted) throw new IllegalStateException("信用分调整未生效");
        return detailPage(userId, 0, 10, authentication);
    }

    private String scoreType(User user) {
        return UserRole.isBossIdentity(user) ? CreditScoreService.BOSS_CREDIT : CreditScoreService.WORKER_STAR;
    }

    private User resolveUser(String id) {
        if (id != null && id.matches("(?i)G\\d{7}")) return users.findByWorkerCode(id.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        if (id != null && id.matches("(?i)B\\d{7}")) return users.findByBossCode(id.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        try {
            return users.findById(Long.valueOf(id))
                    .orElseThrow(() -> new EntityNotFoundException("用户不存在: " + id));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("用户ID格式无效");
        }
    }

    private String businessId(User user) {
        boolean boss = UserRole.isBossIdentity(user);
        return ensureBusinessId(user, boss);
    }

    /** 兼容尚未执行历史回填脚本的账号，首次读取时补齐并持久化业务编号。 */
    private String ensureBusinessId(User user, boolean boss) {
        boolean missingWorkerCode = user.getWorkerCode() == null || user.getWorkerCode().isBlank();
        boolean missingBossCode = boss && (user.getBossCode() == null || user.getBossCode().isBlank());
        UserBusinessCode.ensureWorker(user);
        if (boss) UserBusinessCode.ensureBoss(user);
        if (missingWorkerCode || missingBossCode) users.save(user);
        return boss ? user.getBossCode() : user.getWorkerCode();
    }

    private LoginUser admin(Authentication authentication, boolean write) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)
                || user.role() == null || !user.role().startsWith("ADMIN_")) {
            throw new ForbiddenBusinessException("仅管理员可操作");
        }
        if (write && user.role().endsWith("VIEWER")) throw new ForbiddenBusinessException("当前管理员无信用分管理权限");
        return user;
    }

    private String text(Map<String, Object> body, String key, String fallback) {
        Object value = body == null ? null : body.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private int number(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { throw new IllegalArgumentException("delta 必须是整数"); }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return null;
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "worker", "user", "零工" -> UserRole.USER;
            case "boss", "老板" -> UserRole.BOSS;
            default -> null;
        };
    }

    private String levelKey(int score) {
        if (score >= 90) return "excellent";
        if (score >= 75) return "good";
        if (score >= 60) return "medium";
        return "low";
    }

    private int value(Integer score) { return score == null ? 0 : score; }

    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "-";
    }

    /**
     * 历史微信账号曾使用 wx_{openid} 作为 username，该值不是用户名称，不应直接展示。
     * 普通账号仍可使用 username 兜底；微信账号优先展示脱敏手机号。
     */
    private String displayName(User user) {
        String profileName = firstText(user.getRealName(), user.getNickname());
        if (!"-".equals(profileName)) return profileName;
        String username = user.getUsername();
        if (username != null && !username.isBlank()
                && !username.toLowerCase(Locale.ROOT).startsWith("wx_")) {
            return username;
        }
        String phone = user.getPhone();
        if (phone != null && !phone.isBlank()) return maskPhone(phone);
        return "未设置昵称";
    }

    private String maskPhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (value.length() < 7) return value;
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private boolean containsAny(String keyword, Object... values) {
        for (Object value : values) if (value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(keyword)) return true;
        return false;
    }
}
