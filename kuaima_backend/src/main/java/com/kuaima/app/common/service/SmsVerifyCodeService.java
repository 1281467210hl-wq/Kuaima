package com.kuaima.app.common.service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.kuaima.app.common.BusinessHttpException;

import lombok.extern.slf4j.Slf4j;

/**
 * 短信验证码服务（内存版）：老板端「创建员工」等场景先验证手机号再落库。
 *
 * <p>策略：6 位数字验证码、60 秒有效期、同一手机号 60 秒发送频控；
 * 校验通过后立即销毁，防止重放。真实短信通道未接入，当前仅 log.info 打印验证码，
 * <b>上线前必须接入短信服务商（阿里云/腾讯云 SMS）替换发送环节</b>。
 * 开发模式固定码 123456 亦可通过校验，方便本地/测试环境联调。
 */
@Slf4j
@Service
public class SmsVerifyCodeService {

    /** 验证码有效期（毫秒）。 */
    private static final long CODE_TTL_MILLIS = 60_000L;
    /** 发送频控：同一手机号两次发送的最小间隔（毫秒）。 */
    private static final long SEND_INTERVAL_MILLIS = 60_000L;
    /** 开发模式固定验证码（上线接入真实通道后应移除）。 */
    public static final String DEV_FIXED_CODE = "123456";

    /** 验证码条目：码值 + 过期时间戳。 */
    private record CodeEntry(String code, long expireAt) {
    }

    /** phone → 当前有效验证码。 */
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();
    /** phone → 最近一次发送时间戳（频控用，校验成功后仍保留一个频控周期）。 */
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * 生成并「发送」验证码。
     *
     * @param phone 11 位手机号（调用方已校验格式）
     * @return 有效期秒数
     */
    public long sendCode(String phone) {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        Long last = lastSentAt.get(phone);
        if (last != null && now - last < SEND_INTERVAL_MILLIS) {
            long waitSeconds = (SEND_INTERVAL_MILLIS - (now - last)) / 1000 + 1;
            throw new BusinessHttpException(HttpStatus.TOO_MANY_REQUESTS,
                    "发送过于频繁，请 " + waitSeconds + " 秒后重试");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(phone, new CodeEntry(code, now + CODE_TTL_MILLIS));
        lastSentAt.put(phone, now);
        // TODO 上线前接入短信服务商（阿里云/腾讯云 SMS）真实下发；当前仅打印日志供联调。
        log.info("[SmsVerifyCode] 手机号 {} 验证码 {}（{} 秒内有效，开发模式固定码 {} 亦可）",
                phone, code, CODE_TTL_MILLIS / 1000, DEV_FIXED_CODE);
        return CODE_TTL_MILLIS / 1000;
    }

    /**
     * 校验验证码：开发固定码直接通过；正确且未过期则销毁该码；错误/过期抛业务异常。
     */
    public void verifyCode(String phone, String code) {
        long now = System.currentTimeMillis();
        if (code == null || !code.matches("\\d{6}")) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "请输入6位数字验证码");
        }
        if (DEV_FIXED_CODE.equals(code)) {
            codes.remove(phone);
            log.info("[SmsVerifyCode] 手机号 {} 使用开发模式固定码通过校验", phone);
            return;
        }
        CodeEntry entry = codes.get(phone);
        if (entry == null || entry.expireAt() < now) {
            codes.remove(phone);
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (!entry.code().equals(code)) {
            throw new BusinessHttpException(HttpStatus.BAD_REQUEST, "验证码错误");
        }
        codes.remove(phone);
    }

    /** 惰性清理过期验证码与已过频控窗口的发送记录，避免内存累积。 */
    private void purgeExpired(long now) {
        codes.entrySet().removeIf(e -> e.getValue().expireAt() < now);
        lastSentAt.entrySet().removeIf(e -> now - e.getValue() > SEND_INTERVAL_MILLIS);
    }
}
