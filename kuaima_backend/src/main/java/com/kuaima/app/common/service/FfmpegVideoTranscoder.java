package com.kuaima.app.common.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 将微信小程序不兼容的 HEVC 视频转换为 H.264/AAC MP4。 */
@Service
public class FfmpegVideoTranscoder {
    private final String executable;
    private final Duration timeout;

    public boolean isHevc(Path input) throws IOException {
        byte[] hvc1 = "hvc1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] hev1 = "hev1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] buffer = new byte[64 * 1024 + 3];
        int carry = 0;
        try (var reader = Files.newBufferedReader(input, java.nio.charset.StandardCharsets.ISO_8859_1)) {
            int character;
            while ((character = reader.read()) != -1) {
                buffer[carry++] = (byte) character;
                if (carry == buffer.length) {
                    if (contains(buffer, hvc1) || contains(buffer, hev1)) return true;
                    System.arraycopy(buffer, buffer.length - 3, buffer, 0, 3);
                    carry = 3;
                }
            }
        }
        return contains(buffer, 0, carry, hvc1) || contains(buffer, 0, carry, hev1);
    }

    private boolean contains(byte[] buffer, byte[] pattern) {
        return contains(buffer, 0, buffer.length, pattern);
    }

    private boolean contains(byte[] buffer, int offset, int length, byte[] pattern) {
        for (int start = offset; start <= length - pattern.length; start++) {
            int index = 0;
            while (index < pattern.length && buffer[start + index] == pattern[index]) index++;
            if (index == pattern.length) return true;
        }
        return false;
    }

    public FfmpegVideoTranscoder(
            @Value("${kuaima.media.ffmpeg-path:ffmpeg}") String executable,
            @Value("${kuaima.media.transcode-timeout-seconds:900}") long timeoutSeconds) {
        this.executable = executable;
        this.timeout = Duration.ofSeconds(Math.max(60, timeoutSeconds));
    }

    public Path transcodeToH264(Path input) throws IOException {
        Path output = Files.createTempFile("kuaima-academy-h264-", ".mp4");
        List<String> command = List.of(executable, "-y", "-i", input.toString(),
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
                "-movflags", "+faststart", output.toString());
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("服务器未安装 FFmpeg，无法自动转换 H.265 视频", e);
        }
        boolean finished;
        try {
            finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            Files.deleteIfExists(output);
            throw new IllegalStateException("视频转码被中断", e);
        }
        if (!finished) {
            process.destroyForcibly();
            Files.deleteIfExists(output);
            throw new IllegalStateException("视频转码超时，请压缩视频后重试");
        }
        if (process.exitValue() != 0 || !Files.exists(output) || Files.size(output) == 0) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("视频转码失败，请确认文件编码和服务器 FFmpeg 配置");
        }
        return output;
    }
}
