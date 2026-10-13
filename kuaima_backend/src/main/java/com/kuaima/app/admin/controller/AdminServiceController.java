package com.kuaima.app.admin.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.TextMessage;

import com.alibaba.fastjson2.JSONObject;

import com.kuaima.app.admin.entity.QuickReply;
import com.kuaima.app.admin.repository.QuickReplyRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.domain.chat.entity.ChatMessage;
import com.kuaima.app.domain.chat.entity.ChatSession;
import com.kuaima.app.domain.chat.repository.ChatMessageRepository;
import com.kuaima.app.domain.chat.repository.ChatSessionRepository;
import com.kuaima.app.domain.boss.entity.BaseOrderItem;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.faq.entity.Faq;
import com.kuaima.app.domain.faq.repository.FaqRepository;
import com.kuaima.app.websocket.WebSocketSessionManager;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import lombok.RequiredArgsConstructor;

/**
 * 后台客服管理 Controller
 * 提供会话管理、快捷回复、FAQ 管理等接口
 */
@RestController
@RequestMapping("/admin/service")
@RequiredArgsConstructor
@Tag(name = "后台-服务", description = "服务工单管理")
public class AdminServiceController {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final QuickReplyRepository quickReplyRepository;
    private final FaqRepository faqRepository;
    private final UserRepository userRepository;
    private final WebSocketSessionManager webSocketSessionManager;
    private final BaseOrderItemRespository orderItemRepository;
    private final BossOrderRespository bossOrderRepository;

    // ==================== 客服统计 ====================

    @Operation(summary = "客服统计", description = "返回开话/闭话会话数、快捷回复总数、FAQ 总数")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("openSessions", chatSessionRepository.countByStatus("OPEN"));
        stats.put("closedSessions", chatSessionRepository.countByStatus("CLOSED"));
        stats.put("totalQuickReplies", quickReplyRepository.count());
        stats.put("totalFaqs", faqRepository.count());
        return Result.success(stats);
    }

    // ==================== 会话管理 ====================

    @Operation(summary = "会话列表", description = "可按 status 过滤(OPEN/CLOSED)，按时间倒序内存分页。参数：status(可选)、page(默认0)、size(默认20)")
    @GetMapping("/sessions")
    public Result<List<java.util.Map<String, Object>>> sessions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<ChatSession> all = (status != null && !status.isEmpty())
                ? chatSessionRepository.findByStatusOrderByTimestampDesc(status)
                : chatSessionRepository.findAll();
        // 排序
        all.sort((a, b) -> {
            if (a.getTimestamp() == null || b.getTimestamp() == null) return 0;
            return b.getTimestamp().compareTo(a.getTimestamp());
        });
        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return Result.success(all.subList(from, to).stream().map(this::sessionView).toList(), page, total);
    }

    /** 会话视图：附加用户名称/手机号，供后台列表与聊天页展示。 */
    private java.util.Map<String, Object> sessionView(ChatSession session) {
        java.util.Map<String, Object> view = new HashMap<>();
        view.put("id", session.getId());
        view.put("userId", session.getUserId());
        User user = session.getUserId() == null ? null
                : userRepository.findById(session.getUserId()).orElse(null);
        view.put("userName", user == null ? "未知用户"
                : firstText(user.getNickname(), user.getRealName(), user.getUsername(), "用户" + session.getUserId()));
        view.put("userPhone", user == null ? null : user.getPhone());
        view.put("realnameStatus", user == null ? null : user.getRealnameStatus());
        view.put("creditScore", user == null ? null
                : UserRole.isWorkerIdentity(user) ? user.getStarScore() : user.getCreditScore());
        view.put("agentId", session.getAgentId());
        view.put("status", session.getStatus());
        view.put("type", session.getType() == null ? "other" : session.getType());
        view.put("timestamp", session.getTimestamp());
        return view;
    }

    private Map<String, Object> sessionDetailView(ChatSession session) {
        Map<String, Object> view = sessionView(session);
        view.put("relatedOrders", relatedOrders(session.getUserId()));
        return view;
    }

    private List<Map<String, Object>> relatedOrders(Long userId) {
        if (userId == null) return List.of();
        return orderItemRepository
                .findByUserId(userId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "id")))
                .getContent().stream().map(this::relatedOrderView).toList();
    }

    private Map<String, Object> relatedOrderView(BaseOrderItem item) {
        Map<String, Object> view = new HashMap<>();
        BossOrder order = item.getOrderId() == null ? null
                : bossOrderRepository.findById(item.getOrderId()).orElse(null);
        view.put("itemId", item.getId());
        view.put("orderId", item.getOrderId());
        view.put("orderNo", order == null ? null : order.getOrderNo());
        view.put("title", order == null ? "未知岗位" : firstText(order.getOrderTitle(), order.getPostion()));
        view.put("position", order == null ? null : order.getPostion());
        view.put("salary", order == null ? null : order.getSalary());
        view.put("status", item.getStatus());
        view.put("orderStatus", order == null ? null : order.getOrderStatus());
        view.put("startTime", order == null ? null : order.getStartTime());
        view.put("endTime", order == null ? null : order.getEndTime());
        return view;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    @Operation(summary = "会话详情", description = "按会话 id 查询；不存在返回错误提示")
    @GetMapping("/sessions/{id}")
    public Result<java.util.Map<String, Object>> sessionDetail(@PathVariable Long id) {
        return chatSessionRepository.findById(id)
                .map(s -> Result.success(sessionDetailView(s)))
                .orElse(Result.error("会话不存在"));
    }

    @Operation(summary = "会话消息列表", description = "按会话 id 查询消息，时间正序内存分页。参数：page(默认0)、size(默认50)")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> sessionMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<ChatMessage> all = chatMessageRepository.findBySessionIdOrderByTimestampAsc(id);
        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return Result.success(all.subList(from, to), page, total);
    }

    @Operation(summary = "客服发送消息", description = "body：{\\\"content\\\":\\\"文本\\\"}，以 AGENT 身份发送 TEXT 消息并刷新会话时间戳；content 为空返回错误提示")
    @PostMapping("/sessions/{id}/messages")
    public Result<ChatMessage> sendMessage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        if (content == null || content.isEmpty()) {
            return Result.error("消息内容不能为空");
        }
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(id);
        msg.setFromId(1L); // TODO: 从登录上下文获取
        msg.setFromType("AGENT");
        msg.setContent(content);
        msg.setContentType("TEXT");
        ChatMessage saved = chatMessageRepository.save(msg);

        // 更新会话时间
        chatSessionRepository.findById(id).ifPresent(s -> {
            s.setTimestamp(new java.sql.Timestamp(System.currentTimeMillis()));
            chatSessionRepository.save(s);
        });
        return Result.success(saved);
    }

    @Operation(summary = "接入会话", description = "将会话状态置为 PROCESSING 并记录接待客服；不存在返回错误提示")
    @PutMapping("/sessions/{id}/claim")
    public Result<java.util.Map<String, Object>> claimSession(@PathVariable Long id,
                                                                org.springframework.security.core.Authentication authentication) {
        return chatSessionRepository.findById(id).map(s -> {
            if ("CLOSED".equals(s.getStatus())) return Result.<java.util.Map<String, Object>>error("会话已结束，无法接入");
            s.setStatus("PROCESSING");
            Object principal = authentication == null ? null : authentication.getPrincipal();
            if (principal instanceof com.kuaima.app.security.model.LoginUser operator && operator.id() != null) {
                s.setAgentId(operator.id());
            }
            return Result.success(sessionView(chatSessionRepository.save(s)));
        }).orElse(Result.error("会话不存在"));
    }

    @Operation(summary = "更新对话类型", description = "type 只能是 consult/complaint/other，分别对应咨询/投诉/其他")
    @PutMapping("/sessions/{id}/type")
    public Result<java.util.Map<String, Object>> updateSessionType(@PathVariable Long id,
                                                                    @RequestBody Map<String, Object> body) {
        String type = body.get("type") == null ? null : String.valueOf(body.get("type"));
        if (!Set.of("consult", "complaint", "other").contains(type)) {
            return Result.error("type 只能是 consult/complaint/other");
        }
        return chatSessionRepository.findById(id).map(s -> {
            s.setType(type);
            return Result.success(sessionView(chatSessionRepository.save(s)));
        }).orElse(Result.error("会话不存在"));
    }

    @Operation(summary = "关闭会话", description = "将会话状态置为 CLOSED；不存在返回错误提示")
    @PutMapping("/sessions/{id}/close")
    public Result<ChatSession> closeSession(@PathVariable Long id) {
        return chatSessionRepository.findById(id).map(s -> {
            s.setStatus("CLOSED");
            ChatSession saved = chatSessionRepository.save(s);
            notifySessionClosed(saved);
            return Result.success(saved);
        }).orElse(Result.error("会话不存在"));
    }

    /** 平台结束会话后向双方在线 WebSocket 连接推送 SESSION_CLOSED，让用户端实时收到结束提醒。 */
    private void notifySessionClosed(ChatSession session) {
        if (session == null || session.getId() == null) return;
        JSONObject push = new JSONObject();
        push.put("type", "SESSION_CLOSED");
        push.put("sessionId", session.getId());
        push.put("timestamp", System.currentTimeMillis());
        String payload = push.toJSONString();
        sendIfOnline(session.getUserId(), payload);
        sendIfOnline(session.getAgentId(), payload);
    }

    private void sendIfOnline(Long userId, String payload) {
        if (userId == null) return;
        try {
            var target = webSocketSessionManager.getSession(String.valueOf(userId));
            if (target != null && target.isOpen()) target.sendMessage(new TextMessage(payload));
        } catch (Exception ignored) {
            // 推送失败不影响会话关闭结果，用户端可通过轮询兜底感知状态。
        }
    }

    // ==================== 快捷回复管理 ====================

    @Operation(summary = "快捷回复列表", description = "返回全部快捷回复，无分页")
    @GetMapping("/quick-replies")
    public Result<List<QuickReply>> quickReplies() {
        return Result.success(quickReplyRepository.findAll());
    }

    @Operation(summary = "创建快捷回复", description = "body 为 QuickReply；缺省 enabled=true、sortOrder=0")
    @PostMapping("/quick-replies")
    public Result<QuickReply> createQuickReply(@RequestBody QuickReply reply) {
        if (reply.getEnabled() == null) reply.setEnabled(true);
        if (reply.getSortOrder() == null) reply.setSortOrder(0);
        return Result.success(quickReplyRepository.save(reply));
    }

    @Operation(summary = "更新快捷回复", description = "按 id 整体更新 content/category/sortOrder/enabled；不存在返回错误提示")
    @PutMapping("/quick-replies/{id}")
    public Result<QuickReply> updateQuickReply(@PathVariable Long id, @RequestBody QuickReply reply) {
        return quickReplyRepository.findById(id).map(existing -> {
            existing.setContent(reply.getContent());
            existing.setCategory(reply.getCategory());
            existing.setSortOrder(reply.getSortOrder());
            existing.setEnabled(reply.getEnabled());
            return Result.success(quickReplyRepository.save(existing));
        }).orElse(Result.error("快捷回复不存在"));
    }

    @Operation(summary = "删除快捷回复", description = "按 id 删除快捷回复")
    @DeleteMapping("/quick-replies/{id}")
    public Result<Void> deleteQuickReply(@PathVariable Long id) {
        quickReplyRepository.deleteById(id);
        return Result.success(null);
    }

    // ==================== FAQ 管理 ====================

    @Operation(summary = "FAQ 列表", description = "返回全部 FAQ，无分页")
    @GetMapping("/faqs")
    public Result<List<Faq>> faqs() {
        return Result.success(faqRepository.findAll());
    }

    @Operation(summary = "创建 FAQ", description = "body 为 Faq；缺省 enabled=true、sortOrder=0")
    @PostMapping("/faqs")
    public Result<Faq> createFaq(@RequestBody Faq faq) {
        if (faq.getEnabled() == null) faq.setEnabled(true);
        if (faq.getSortOrder() == null) faq.setSortOrder(0);
        return Result.success(faqRepository.save(faq));
    }

    @Operation(summary = "更新 FAQ", description = "按 id 整体更新 question/answer/category/sortOrder/enabled；不存在返回错误提示")
    @PutMapping("/faqs/{id}")
    public Result<Faq> updateFaq(@PathVariable Long id, @RequestBody Faq faq) {
        return faqRepository.findById(id).map(existing -> {
            existing.setQuestion(faq.getQuestion());
            existing.setAnswer(faq.getAnswer());
            existing.setCategory(faq.getCategory());
            existing.setSortOrder(faq.getSortOrder());
            existing.setEnabled(faq.getEnabled());
            return Result.success(faqRepository.save(existing));
        }).orElse(Result.error("FAQ不存在"));
    }

    @Operation(summary = "删除 FAQ", description = "按 id 删除 FAQ")
    @DeleteMapping("/faqs/{id}")
    public Result<Void> deleteFaq(@PathVariable Long id) {
        faqRepository.deleteById(id);
        return Result.success(null);
    }
}
