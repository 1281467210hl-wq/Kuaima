package com.kuaima.app.admin.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kuaima.app.admin.entity.AdminSetting;
import com.kuaima.app.admin.repository.AdminSettingRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.domain.message.constant.MessageType;
import com.kuaima.app.domain.message.service.MessageService;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.service.SmsService;

import java.time.LocalDateTime;
import java.util.Map;

/** 系统设置 */
@RestController
@RequestMapping("/admin/settings")
@Tag(name = "后台-设置", description = "平台参数设置")
public class AdminSettingController {

    private final AdminSettingRepository repo;
    private final SmsService smsService;
    private final MessageService messageService;
    private final UserRepository userRepository;
    private final OssStorageService ossStorageService;

    public AdminSettingController(AdminSettingRepository repo, SmsService smsService,
                                   MessageService messageService, UserRepository userRepository,
                                   OssStorageService ossStorageService) {
        this.repo = repo;
        this.smsService = smsService;
        this.messageService = messageService;
        this.userRepository = userRepository;
        this.ossStorageService = ossStorageService;
    }

    @Operation(summary = "设置列表", description = "返回全部平台参数设置，无分页")
    @GetMapping
    public Result<List<AdminSetting>> list() {
        return Result.success(withLogoPreview(repo.findAll()));
    }

    @Operation(summary = "按分类查询设置", description = "按分类 category 返回该分类下全部设置项")
    @GetMapping("/category/{category}")
    public Result<List<AdminSetting>> byCategory(@PathVariable String category) {
        List<AdminSetting> settings = new java.util.ArrayList<>(repo.findByCategory(category));
        settings.addAll(repo.findByCategoryIsNullAndSettingKeyStartingWith(category + "."));
        return Result.success(withLogoPreview(settings));
    }

    @Operation(summary = "设置详情", description = "按 settingKey 查询单条设置；不存在抛出异常")
    @GetMapping("/{key}")
    public Result<AdminSetting> get(@PathVariable String key) {
        return Result.success(withLogoPreview(repo.findById(key).orElseThrow()));
    }

    private List<AdminSetting> withLogoPreview(List<AdminSetting> settings) {
        settings.forEach(this::withLogoPreview);
        return settings;
    }

    private AdminSetting withLogoPreview(AdminSetting setting) {
        if (!"platform.logo".equals(setting.getSettingKey()) || !isOssObject(setting.getSettingValue())) {
            return setting;
        }
        setting.setDisplayValue(ossStorageService.inlinePreviewUrl(setting.getSettingValue()));
        return setting;
    }

    private boolean isOssObject(String value) {
        return value != null && (value.startsWith("insurance/")
                || value.contains(".aliyuncs.com/insurance/"));
    }

    /** 保存/更新 设置 */
    @Operation(summary = "保存/更新设置", description = "按 settingKey 保存或更新设置项，body 为 AdminSetting")
    @PutMapping("/{key}")
    public Result<AdminSetting> save(@PathVariable String key, @RequestBody AdminSetting setting) {
        AdminSetting existing = repo.findById(key).orElse(null);
        setting.setSettingKey(key);
        // 前端只提交 settingValue/description；category 缺失会导致按分类查询不到，刷新后设置“丢失”
        if (!StringUtils.hasText(setting.getCategory())) {
            setting.setCategory(existing != null && StringUtils.hasText(existing.getCategory())
                    ? existing.getCategory() : categoryOf(key));
        }
        if (setting.getDescription() == null && existing != null) setting.setDescription(existing.getDescription());
        return Result.success(withLogoPreview(repo.save(setting)));
    }

    /** settingKey 约定为「分类.名称」，如 platform.logo → platform。 */
    private static String categoryOf(String key) {
        int dot = key.indexOf('.');
        return dot > 0 ? key.substring(0, dot) : null;
    }

    // ==================== 银行账户设置 ====================
    private static final String BANK_ACCOUNT_KEY = "bank_account_info";
    private static final String DEFAULT_BANK_INFO = "{\"bankName\":\"中国工商银行\",\"cardNumber\":\"6222 **** **** 8888\",\"holder\":\"快马日结科技有限公司\",\"branch\":\"北京海淀支行\",\"swiftCode\":\"ICBKCNBJ\",\"bankCode\":\"102100000458\",\"accountType\":\"对公账户\"}";

    /** 获取银行账户信息（返回 JSON 字符串，前端解析） */
    @Operation(summary = "获取银行账户信息", description = "返回平台收款银行账户 JSON 字符串；未配置时返回默认工商银行信息")
    @GetMapping("/bank-account")
    public Result<String> getBankAccount() {
        String value = repo.findById(BANK_ACCOUNT_KEY)
                .map(AdminSetting::getSettingValue)
                .orElse(DEFAULT_BANK_INFO);
        return Result.success(value);
    }

    /** 保存银行账户信息（接收 JSON 字符串） */
    @Operation(summary = "保存银行账户信息", description = "接收 JSON 字符串保存为平台收款银行账户，category=platform；异常返回错误提示")
    @PutMapping("/bank-account")
    public Result<String> saveBankAccount(@RequestBody String json) {
        try {
            AdminSetting setting = repo.findById(BANK_ACCOUNT_KEY).orElseGet(AdminSetting::new);
            setting.setSettingKey(BANK_ACCOUNT_KEY);
            setting.setCategory("platform");
            setting.setSettingValue(json);
            setting.setDescription("平台收款银行账户信息");
            repo.save(setting);
            return Result.success(json);
        } catch (Exception e) {
            return Result.error("保存银行账户信息失败: " + e.getMessage());
        }
    }

    // ==================== 钱包账户设置 ====================
    private static final String WALLET_ACCOUNT_KEY = "wallet_account_info";
    private static final String DEFAULT_WALLET_INFO = "{\"alipay\":{\"account\":\"kuaima@163.com\",\"holder\":\"快马日结科技有限公司\",\"isDefault\":true},\"wechat\":{\"account\":\"k_m_riji001\",\"holder\":\"快马日结科技有限公司\",\"isDefault\":false}}";

    /** 获取钱包账户信息（返回 JSON 字符串，前端解析） */
    @Operation(summary = "获取钱包账户信息", description = "返回平台收款钱包账户 JSON 字符串（含支付宝/微信）；未配置时返回默认信息")
    @GetMapping("/wallet-account")
    public Result<String> getWalletAccount() {
        String value = repo.findById(WALLET_ACCOUNT_KEY)
                .map(AdminSetting::getSettingValue)
                .orElse(DEFAULT_WALLET_INFO);
        return Result.success(value);
    }

    /** 保存钱包账户信息（接收 JSON 字符串） */
    @Operation(summary = "保存钱包账户信息", description = "接收 JSON 字符串保存为平台收款钱包账户，category=platform；异常返回错误提示")
    @PutMapping("/wallet-account")
    public Result<String> saveWalletAccount(@RequestBody String json) {
        try {
            AdminSetting setting = repo.findById(WALLET_ACCOUNT_KEY).orElseGet(AdminSetting::new);
            setting.setSettingKey(WALLET_ACCOUNT_KEY);
            setting.setCategory("platform");
            setting.setSettingValue(json);
            setting.setDescription("平台收款钱包账户信息");
            repo.save(setting);
            return Result.success(json);
        } catch (Exception e) {
            return Result.error("保存钱包账户信息失败: " + e.getMessage());
        }
    }

    // ==================== 模板测试发送 ====================

    /**
     * 发送模板测试消息
     * 请求体: JSON 字符串 { "type":"sms", "templateTitle":"...", "receiver":"...", "content":"..." }
     * 使用原始 String 接收，避免依赖 Jackson 反序列化
     */
    @Operation(summary = "模板测试发送", description = "body 为 JSON 字符串：{type,templateTitle,receiver,content}；receiver 必须为 11 位手机号；type=sms 调用 SmsService 发送验证码，其他类型走站内信日志；返回脱敏手机号与发送时间")
    @PostMapping("/test-send")
    public Result<Map<String, Object>> testSend(@RequestBody String json) {
        String type = extractJsonField(json, "type", "sms");
        String receiver = extractJsonField(json, "receiver", "");
        String content = extractJsonField(json, "content", "");
        String templateTitle = extractJsonField(json, "templateTitle", "");

        if (receiver.isEmpty() || !receiver.matches("^1[3-9]\\d{9}$")) {
            return Result.error("请输入正确的11位手机号");
        }

        if ("sms".equals(type)) {
            // 短信：调用阿里云 SmsService 发送验证码
            String err = smsService.sendCode(receiver);
            if (err != null) {
                return Result.error("短信发送失败: " + err);
            }
            String masked = receiver.substring(0, 3) + "****" + receiver.substring(7);
            return Result.success(Map.of(
                    "success", true,
                    "maskedReceiver", masked,
                    "sentAt", LocalDateTime.now().toString()
            ));
        }
        if ("both".equals(type)) {
            String err = smsService.sendCode(receiver);
            if (err != null) {
                return Result.error("短信发送失败: " + err);
            }
        }

        // 站内信：按手机号定位用户并写入真实收件箱；both 渠道同时发送短信。
        var targetUser = userRepository.findByPhone(receiver).stream().findFirst().orElse(null);
        if (targetUser == null) {
            return Result.error("该手机号未注册平台账号，无法发送站内信");
        }
        String title = templateTitle.isBlank() ? "测试消息" : templateTitle;
        String body = content.isBlank() ? "这是一条来自后台的模板测试消息。" : content;
        messageService.sendToUser(targetUser.getId(), MessageType.SYSTEM_NOTICE, title, body,
                "TEMPLATE_TEST", null);
        String masked = receiver.substring(0, 3) + "****" + receiver.substring(7);
        return Result.success(Map.of(
                "success", true,
                "maskedReceiver", masked,
                "sentAt", LocalDateTime.now().toString()
        ));
    }

    /** 从简单 JSON 字符串中提取字段值（仅支持字符串值） */
    private String extractJsonField(String json, String key, String defaultValue) {
        if (json == null || json.isEmpty()) return defaultValue;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return defaultValue;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return defaultValue;
        // 跳过空白
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return defaultValue;
        if (json.charAt(start) != '"') return defaultValue;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return defaultValue;
        return json.substring(start + 1, end);
    }
}
