package com.kuaima.app.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

/**
 * 基于 fastjson2 的 JSON 消息转换器。
 * <p>
 * 全局统一配置：
 * <ul>
 *   <li>{@code java.util.Date} 序列化为 {@code "yyyy-MM-dd HH:mm:ss"} 字符串
 *       （fastjson2 默认输出毫秒时间戳 long，前端无法直接展示）</li>
 *   <li>不输出 null 字段（减少传输体积）</li>
 *   <li>BigDecimal 保持原始精度</li>
 * </ul>
 */
public class Fastjson2HttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    /** 日期格式：让 fastjson2 把 java.util.Date 输出为 "2026-09-29 10:15:33" 而不是毫秒时间戳。 */
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** 写入 features：不输出 null 值。 */
    private static final JSONWriter.Feature[] WRITER_FEATURES = { JSONWriter.Feature.WriteNulls };

    public Fastjson2HttpMessageConverter() {
        super(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8),
                new MediaType("application", "*+json", StandardCharsets.UTF_8));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        // 只处理业务接口响应对象，SpringDoc/byte[]/String 交给 Jackson 或原生处理器
        if (clazz == byte[].class || clazz == String.class) {
            return false;
        }
        String className = clazz.getName();
        if (className.startsWith("io.swagger.v3.") || className.startsWith("org.springdoc.")) {
            return false;
        }
        return true;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        String text = StreamUtils.copyToString(inputMessage.getBody(), StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return null;
        }
        return JSON.parseObject(text, clazz, JSONReader.Feature.UseBigDecimalForDoubles);
    }

    @Override
    protected void writeInternal(Object value, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        outputMessage.getHeaders().setContentType(
                new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        if (value instanceof byte[]) {
            outputMessage.getBody().write((byte[]) value);
            return;
        }
        if (value instanceof String) {
            outputMessage.getBody().write(((String) value).getBytes(StandardCharsets.UTF_8));
            return;
        }
        // JSON.toJSONBytes(value, dateFormat, features...) — 第二个参数就是日期格式字符串
        // 这样 java.util.Date / java.sql.Timestamp 都会被格式化为 "yyyy-MM-dd HH:mm:ss"
        byte[] bytes = JSON.toJSONBytes(value, DATE_FORMAT, WRITER_FEATURES);
        outputMessage.getBody().write(bytes);
    }
}
