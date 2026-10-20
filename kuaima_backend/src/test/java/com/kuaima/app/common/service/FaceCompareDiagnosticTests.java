package com.kuaima.app.common.service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.aliyun.facebody20191230.Client;
import com.aliyun.facebody20191230.models.CompareFaceRequest;
import com.aliyun.facebody20191230.models.CompareFaceResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;

/**
 * 人脸比对线上配置诊断（手动运行，平时自动跳过）。
 *
 * 用法（在自己的终端里执行，不要把密钥贴到聊天或提交到仓库）：
 *   export ALIYUN_FACE_ACCESS_KEY_ID=...
 *   export ALIYUN_FACE_ACCESS_KEY_SECRET=...
 *   export ALIYUN_FACE_ENDPOINT=...            # 不设则用 facebody.cn-shanghai.aliyuncs.com
 *   # 可选：两张本地照片，不设则用空白图片
 *   export FACE_TEST_IMAGE_A=/path/id.jpg FACE_TEST_IMAGE_B=/path/face.jpg
 *   mvn -o test -Dtest=FaceCompareDiagnosticTests
 *
 * 判读：不设照片时，返回「未检测到人脸」类错误码即说明密钥、权限、开通状态、服务地址都正常。
 */
@EnabledIfEnvironmentVariable(named = "ALIYUN_FACE_ACCESS_KEY_ID", matches = ".+")
class FaceCompareDiagnosticTests {

    @Test
    void diagnoseAliyunFaceCompare() throws Exception {
        String endpoint = envOr("ALIYUN_FACE_ENDPOINT", "facebody.cn-shanghai.aliyuncs.com");
        String imageA = imageBase64("FACE_TEST_IMAGE_A");
        String imageB = imageBase64("FACE_TEST_IMAGE_B");
        boolean blankImages = System.getenv("FACE_TEST_IMAGE_A") == null;

        System.out.println("\n========== 人脸比对诊断 ==========");
        System.out.println("服务地址 : " + endpoint);
        System.out.println("测试图片 : " + (blankImages ? "空白图片（预期返回「未检测到人脸」）" : "本地照片"));
        try {
            Client client = new Client(new Config()
                    .setAccessKeyId(System.getenv("ALIYUN_FACE_ACCESS_KEY_ID"))
                    .setAccessKeySecret(System.getenv("ALIYUN_FACE_ACCESS_KEY_SECRET"))
                    .setEndpoint(endpoint));
            CompareFaceResponse response = client.compareFace(new CompareFaceRequest()
                    .setImageDataA(imageA).setImageDataB(imageB));
            Float confidence = response.getBody() == null || response.getBody().getData() == null
                    ? null : response.getBody().getData().getConfidence();
            System.out.println("调用结果 : 成功，相似度 = " + confidence + "（80 及以上为通过）");
            System.out.println("结论     : 密钥、权限、服务地址都正常");
        } catch (TeaException e) {
            System.out.println("错误码   : " + e.getCode());
            System.out.println("错误信息 : " + e.getMessage());
            System.out.println("结论     : " + explain(e.getCode() + " " + e.getMessage(), blankImages));
        } catch (Exception e) {
            System.out.println("调用异常 : " + e);
            System.out.println("结论     : 连不上服务地址，检查 ALIYUN_FACE_ENDPOINT 是否正确、服务器能否访问外网");
        }
        System.out.println("==================================\n");
    }

    private static String explain(String text, boolean blankImages) {
        String t = text.toLowerCase();
        if (t.contains("accesskeyid") || t.contains("signature")) return "密钥错误：AccessKey ID 或 Secret 不对";
        if (t.contains("forbidden") || t.contains("permission") || t.contains("notpurchase") || t.contains("notopen")
                || t.contains("unauthorized")) {
            return "没有权限或未开通：在阿里云开通「视觉智能开放平台 - 人脸人体」，并给该 AccessKey 授权 AliyunVIAPIFullAccess";
        }
        if (t.contains("quota") || t.contains("throttl") || t.contains("arrear") || t.contains("balance")) {
            return "额度或欠费问题：检查阿里云账户余额和调用额度";
        }
        if (t.contains("invalidimage") || t.contains("face")) {
            return blankImages
                    ? "正常：空白图片没有人脸，说明密钥、权限、开通状态、服务地址都没问题"
                    : "照片问题：照片中检测不到清晰人脸";
        }
        if (t.contains("endpoint") || t.contains("region") || t.contains("product")) {
            return "服务地址或地域不对：改为 facebody.cn-shanghai.aliyuncs.com 再试";
        }
        return "未归类错误，请把上面的错误码和错误信息发给开发排查";
    }

    private static String imageBase64(String envName) throws Exception {
        String path = System.getenv(envName);
        byte[] bytes = path == null ? blankPng() : Files.readAllBytes(Path.of(path));
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] blankPng() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(200, 200,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
