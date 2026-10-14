package com.kuaima.app.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpOutputMessage;

class Fastjson2HttpMessageConverterTests {

    @Test
    void write_shouldExposeUtf8CharsetAndKeepChineseReadable() throws Exception {
        MockHttpOutputMessage output = new MockHttpOutputMessage();

        new Fastjson2HttpMessageConverter().write(
                new TestMessage("OSS 上传权限不足"), MediaType.APPLICATION_JSON, output);

        assertEquals(MediaType.APPLICATION_JSON.getType(), output.getHeaders().getContentType().getType());
        assertEquals(MediaType.APPLICATION_JSON.getSubtype(), output.getHeaders().getContentType().getSubtype());
        assertEquals(StandardCharsets.UTF_8, output.getHeaders().getContentType().getCharset());
        assertTrue(new String(output.getBodyAsBytes(), StandardCharsets.UTF_8)
                .contains("OSS 上传权限不足"));
    }

    private record TestMessage(String message) {
    }
}
