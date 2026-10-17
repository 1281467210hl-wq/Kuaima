package com.kuaima.app.admin.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.kuaima.app.admin.entity.MessageTemplate;
import com.kuaima.app.admin.repository.MessageTemplateRepository;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.service.SmsService;
import com.kuaima.app.admin.service.ScheduledJobRunner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 定时消息发送任务
 * 每分钟扫描 sendWay=定时 且 status=enabled 的模板，
 * 当 scheduledTime 匹配当前 HH:mm 且当天未发送过时，执行批量发送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledMessageTask {

    private final MessageTemplateRepository templateRepo;
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final SmsService smsService;
    private final ScheduledJobRunner jobs;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Scheduled(cron = "${jobs.message.cron:0 * * * * ?}", zone = "Asia/Shanghai")
    public void executeScheduledSend() {
        executeScheduledSend("AUTO");
    }

    public void executeScheduledSend(String triggerType) {
        jobs.run("scheduled-message", LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DATE_FMT), triggerType,
                this::executeInternal);
    }

    private void executeInternal() {
        String now = LocalTime.now(ZoneId.of("Asia/Shanghai")).format(HH_MM);
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DATE_FMT);

        List<MessageTemplate> templates = templateRepo.findByStatusAndSendWay("enabled", "定时");
        for (MessageTemplate tpl : templates) {
            // 时间不匹配则跳过
            if (!now.equals(tpl.getScheduledTime())) {
                continue;
            }
            // 当天已发送则跳过
            if (today.equals(tpl.getLastSentDate())) {
                continue;
            }
            // 执行发送
            try {
                sendTemplate(tpl);
                // 标记当天已发送
                tpl.setLastSentDate(today);
                tpl.setLastUsed(LocalDateTime.now());
                templateRepo.save(tpl);
                log.info("[定时发送] 模板「{}」发送完成, 时间={}", tpl.getName(), now);
            } catch (Exception e) {
                log.error("[定时发送] 模板「{}」发送失败: {}", tpl.getName(), e.getMessage());
            }
        }
    }

    private void sendTemplate(MessageTemplate tpl) {
        String channel = tpl.getChannel();
        String content = tpl.getContent() != null ? tpl.getContent() : "";
        String title = tpl.getName() != null ? tpl.getName() : "系统通知";

        // 站内信或 both：广播给全部零工用户
        if ("inapp".equals(channel) || "both".equals(channel)) {
            messageService.broadcastToUsers("SYSTEM_NOTICE", title, content, "TEMPLATE", tpl.getId());
            log.info("[定时发送] 站内信已广播: {}", tpl.getName());
        }

        // 短信或 both：向有手机号的用户发送短信
        if ("sms".equals(channel) || "both".equals(channel)) {
            List<User> users = userRepository.findWorkerIdentities();
            int sent = 0;
            int failed = 0;
            for (User user : users) {
                String phone = user.getPhone();
                if (phone == null || phone.isEmpty()) {
                    continue;
                }
                // 使用阿里云默认验证码模板发送（无自定义模板CODE时的回退方案）
                String err = smsService.sendCode(phone);
                if (err == null) {
                    sent++;
                } else {
                    failed++;
                    log.warn("[定时发送] 短信发送失败 phone={}: {}", phone, err);
                }
            }
            log.info("[定时发送] 短信发送完成: 成功={}, 失败={}", sent, failed);
        }
    }
}
