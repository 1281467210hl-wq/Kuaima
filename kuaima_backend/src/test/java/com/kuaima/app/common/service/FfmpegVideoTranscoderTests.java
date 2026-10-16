package com.kuaima.app.common.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegVideoTranscoderTests {
    @TempDir
    Path temporaryDir;

    @Test
    void isHevcShouldDetectHvc1AndHev1Samples() throws Exception {
        FfmpegVideoTranscoder transcoder = new FfmpegVideoTranscoder("ffmpeg", 60);
        Path hevc = Files.writeString(temporaryDir.resolve("hevc.mp4"), "prefix hvc1 suffix");
        Path hevc2 = Files.writeString(temporaryDir.resolve("hevc2.mp4"), "prefix hev1 suffix");
        Path h264 = Files.writeString(temporaryDir.resolve("h264.mp4"), "prefix avc1 suffix");

        assertTrue(transcoder.isHevc(hevc));
        assertTrue(transcoder.isHevc(hevc2));
        assertFalse(transcoder.isHevc(h264));
    }
}
