package com.kuaima.app.domain.message.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kuaima.app.domain.message.entity.Message;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.repository.MessageRepository;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.admin.entity.MessageTemplate;
import com.kuaima.app.admin.repository.MessageTemplateRepository;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * 站内消息中心：事件写入收件箱，支撑 未读数/列表/已读 接口（前端轮询）。
 * 消息写入与业务同事务，目标用户不存在时静默跳过，不影响主流程。
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageTemplateRepository messageTemplateRepository;

    @Autowired
    public MessageService(MessageRepository messageRepository, UserRepository userRepository,
            MessageTemplateRepository messageTemplateRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messageTemplateRepository = messageTemplateRepository;
    }


    /** 发送给单个用户（老板/零工均可），目标用户不存在时跳过并返回 null */
    @Transactional
    public Message sendToUser(Long userId, String type, String title, String content,
            String bizType, Long bizId) {
        return sendToUser(userId, null, type, title, content, bizType, bizId);
    }

    /** 发送给指定身份的用户；role 不为空时不再依赖用户当前切换身份。 */
    @Transactional
    public Message sendToUser(Long userId, String role, String type, String title, String content,
            String bizType, Long bizId) {
        return sendToUser(userId, role, type, title, content, bizType, bizId, Map.of());
    }

    /** 使用 Web 模板发送并替换 {变量名} 占位符。 */
    @Transactional
    public Message sendToUser(Long userId, String role, String type, String title, String content,
            String bizType, Long bizId, Map<String, ?> variables) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        MessageTemplate template = messageTemplateRepository.findFirstByEventAndStatusOrderByUpdateTimeDesc(eventOf(type), "enabled").orElse(null);
        return messageRepository.save(build(user.getId(), role != null ? role : user.getRole(), type,
                render(templateTitle(template, title), variables), render(templateContent(template, content), variables), bizType, bizId));
    }

    /** 发给一组用户（已按 userId 去重），用户不存在自动跳过 */
    @Transactional
    public void sendToList(List<Long> userIds, String type, String title, String content,
            String bizType, Long bizId) {
        sendToList(userIds, null, type, title, content, bizType, bizId);
    }

    /** 发给一组用户并固定接收身份，避免账号切换后消息角色随用户当前角色漂移。 */
    @Transactional
    public void sendToList(List<Long> userIds, String role, String type, String title, String content,
            String bizType, Long bizId) {
        sendToList(userIds, role, type, title, content, bizType, bizId, Map.of());
    }

    @Transactional
    public void sendToList(List<Long> userIds, String role, String type, String title, String content,
            String bizType, Long bizId, Map<String, ?> variables) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<Long> distinctIds = userIds.stream().distinct().collect(Collectors.toList());
        List<Message> messages = distinctIds.stream()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(u -> u != null)
                .map(u -> {
                    MessageTemplate t = messageTemplateRepository.findFirstByEventAndStatusOrderByUpdateTimeDesc(eventOf(type), "enabled").orElse(null);
                    return build(u.getId(), role != null ? role : u.getRole(), type,
                            render(templateTitle(t, title), variables), render(templateContent(t, content), variables), bizType, bizId);
                })
                .toList();
        if (!messages.isEmpty()) {
            messageRepository.saveAll(messages);
        }
    }

    /** 广播给全部员工(USER)：岗位发布视为招聘广播（量大后可按城市/类型定向） */
    @Transactional
    public void broadcastToUsers(String type, String title, String content, String bizType, Long bizId) {
        broadcastToUsers(type, title, content, bizType, bizId, Map.of());
    }

    @Transactional
    public void broadcastToUsers(String type, String title, String content, String bizType, Long bizId,
            Map<String, ?> variables) {
        List<User> users = userRepository.findWorkerIdentities();
        if (users.isEmpty()) {
            return;
        }
        List<Message> messages = users.stream()
                .map(u -> {
                    MessageTemplate t = messageTemplateRepository.findFirstByEventAndStatusOrderByUpdateTimeDesc(eventOf(type), "enabled").orElse(null);
                    return build(u.getId(), UserRole.USER, type,
                            render(templateTitle(t, title), variables), render(templateContent(t, content), variables), bizType, bizId);
                })
                .toList();
        messageRepository.saveAll(messages);
    }

    /** 未读消息数 */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return messageRepository.countByUserIdAndReadFlagFalse(userId);
    }

    /** 消息列表分页（page 从 0 开始，与 /boss/order 一致）；read 为空表示不分已读/未读 */
    @Transactional(readOnly = true)
    public Page<Message> list(Long userId, Boolean read, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        if (read == null) {
            return messageRepository.findByUserIdOrderByIdDesc(userId, pageable);
        }
        return messageRepository.findByUserIdAndReadFlagOrderByIdDesc(userId, read, pageable);
    }

    /** 按接收者身份查询消息；role 为空时保持查询全部身份的兼容行为。 */
    @Transactional(readOnly = true)
    public Page<Message> list(Long userId, String role, Boolean read, int page, int size) {
        if (role == null || role.isBlank()) {
            return list(userId, read, page, size);
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        if (read == null) {
            return messageRepository.findByUserIdAndEffectiveRole(
                    userId, role, MessageType.BOSS_MESSAGE_TYPES, MessageType.USER_MESSAGE_TYPES, pageable);
        }
        return messageRepository.findByUserIdAndEffectiveRoleAndReadFlag(
                userId, role, read, MessageType.BOSS_MESSAGE_TYPES, MessageType.USER_MESSAGE_TYPES, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId, String role) {
        if (role == null || role.isBlank()) {
            return unreadCount(userId);
        }
        return messageRepository.countByUserIdAndEffectiveRoleAndReadFlagFalse(
                userId, role, MessageType.BOSS_MESSAGE_TYPES, MessageType.USER_MESSAGE_TYPES);
    }

    /** 系统通知列表分页（按 type 过滤，如 SYSTEM_NOTICE） */
    @Transactional(readOnly = true)
    public Page<Message> listByType(Long userId, String type, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        return messageRepository.findByUserIdAndTypeOrderByIdDesc(userId, type, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Message> listSystem(Long userId, String role, Boolean read, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (read == null) return messageRepository.findByUserIdAndRoleAndTypeOrderByIdDesc(userId, role, "SYSTEM_NOTICE", pageable);
        return messageRepository.findByUserIdAndRoleAndTypeAndReadFlagOrderByIdDesc(userId, role, "SYSTEM_NOTICE", read, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Message> listByType(Long userId, String role, String type, Boolean read, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        if (read == null) return messageRepository.findByUserIdAndRoleAndTypeOrderByIdDesc(userId, role, type, pageable);
        return messageRepository.findByUserIdAndRoleAndTypeAndReadFlagOrderByIdDesc(userId, role, type, read, pageable);
    }

    /** 消息详情：校验归属，不属于该用户返回 null */
    @Transactional(readOnly = true)
    public Message getById(Long userId, Long messageId) {
        return messageRepository.findByIdAndUserId(messageId, userId).orElse(null);
    }

    /** 单条标记已读，校验归属，消息不存在或不属于该用户返回 false */
    @Transactional
    public boolean markRead(Long userId, Long messageId) {
        Message message = messageRepository.findByIdAndUserId(messageId, userId).orElse(null);
        if (message == null || Boolean.TRUE.equals(message.getReadFlag())) {
            return message != null;
        }
        message.setReadFlag(true);
        message.setReadTime(LocalDateTime.now());
        messageRepository.save(message);
        return true;
    }

    /** 全部标记已读，返回本次标记条数 */
    @Transactional
    public int markAllRead(Long userId) {
        return messageRepository.markAllRead(userId, LocalDateTime.now());
    }

    private Message build(Long userId, String role, String type, String title, String content,
            String bizType, Long bizId) {
        Message message = new Message();
        message.setUserId(userId);
        message.setRole(role);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setReadFlag(false);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private String eventOf(String type) {
        return switch (type) {
            case MessageType.ORDER_PUBLISH -> "order_publish";
            case MessageType.ORDER_APPLY -> "order_apply";
            case MessageType.ORDER_HIRE -> "order_hire";
            case MessageType.ORDER_APPLY_REJECT -> "order_apply_reject";
            case MessageType.ORDER_START_REMIND -> "order_start_remind";
            case MessageType.ORDER_CANCEL -> "order_cancel";
            case MessageType.ITEM_CANCEL -> "item_cancel";
            case MessageType.ITEM_WORK_CONFIRM -> "item_work_confirm";
            case MessageType.SETTLE_PAID -> "settlement";
            case MessageType.WITHDRAW_FAIL -> "withdraw_fail";
            case MessageType.BOSS_INVITE -> "boss_invite";
            default -> type;
        };
    }

    private String templateTitle(MessageTemplate template, String fallback) {
        return template != null && template.getName() != null && !template.getName().isBlank() ? template.getName() : fallback;
    }

    private String templateContent(MessageTemplate template, String fallback) {
        return template != null && template.getContent() != null && !template.getContent().isBlank() ? template.getContent() : fallback;
    }

    private String render(String text, Map<String, ?> variables) {
        if (text == null || variables == null || variables.isEmpty()) return text;
        String result = text;
        for (var entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
