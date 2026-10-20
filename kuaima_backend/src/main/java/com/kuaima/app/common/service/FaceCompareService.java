package com.kuaima.app.common.service;

import java.util.Base64;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aliyun.facebody20191230.Client;
import com.aliyun.facebody20191230.models.CompareFaceRequest;
import com.aliyun.facebody20191230.models.CompareFaceResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;

import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云人脸 1:1 比对：输入身份证人像面与人脸照片，返回置信度（0-100）。
 * 优先直接传图片内容（ImageData），不受「图片 URL 须为上海地域 OSS」的限制；拿不到内容时才回退为传 URL。
 */
@Slf4j
@Service
public class FaceCompareService {

    /** 用户照片问题（无人脸、模糊、格式不支持等）：提示用户重拍。 */
    static final String PHOTO_PROBLEM = "未能识别出清晰的人脸，请在光线充足处正对镜头重新拍摄，并确保身份证人像面完整清晰";
    /** 服务配置或额度问题：用户重拍也无济于事，不能误导用户。 */
    static final String SERVICE_PROBLEM = "实名认证服务暂时不可用，请稍后重试或联系客服";

    @Value("${aliyun.face.endpoint:facebody.cn-shanghai.aliyuncs.com}")
    private String endpoint;

    @Value("${aliyun.face.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.face.access-key-secret:}")
    private String accessKeySecret;

    /** 传图片内容比对（推荐）。 */
    public double compareImages(byte[] imageA, byte[] imageB) {
        Base64.Encoder encoder = Base64.getEncoder();
        return compare(new CompareFaceRequest()
                .setImageDataA(encoder.encodeToString(imageA))
                .setImageDataB(encoder.encodeToString(imageB)));
    }

    /** 传图片 URL 比对：阿里云要求为上海地域 OSS 链接，其他地域可能拉取失败。 */
    public double compare(String imageUrlA, String imageUrlB) {
        return compare(new CompareFaceRequest().setImageURLA(imageUrlA).setImageURLB(imageUrlB));
    }

    private double compare(CompareFaceRequest request) {
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            log.error("[FaceCompare] 未配置 ALIYUN_FACE_ACCESS_KEY_ID / ALIYUN_FACE_ACCESS_KEY_SECRET");
            throw new IllegalStateException(SERVICE_PROBLEM);
        }
        try {
            Client client = new Client(new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint));
            CompareFaceResponse response = client.compareFace(request);
            Float confidence = response == null || response.getBody() == null || response.getBody().getData() == null
                    ? null : response.getBody().getData().getConfidence();
            return confidence == null ? 0d : confidence;
        } catch (TeaException e) {
            // 原来所有异常都提示「请检查图片清晰度」，真实原因被吞掉；这里记录阿里云错误码便于排查
            log.warn("[FaceCompare] 阿里云人脸比对失败 endpoint={} code={} message={}", endpoint, e.getCode(), e.getMessage());
            throw new IllegalStateException(userMessage(e.getCode(), e.getMessage()), e);
        } catch (Exception e) {
            log.error("[FaceCompare] 调用人脸比对服务异常 endpoint={}", endpoint, e);
            throw new IllegalStateException(SERVICE_PROBLEM, e);
        }
    }

    /** 按阿里云错误码区分「照片问题」和「服务问题」。 */
    static String userMessage(String code, String message) {
        String text = ((code == null ? "" : code) + " " + (message == null ? "" : message)).toLowerCase(Locale.ROOT);
        // 图片下载失败多是 URL 地域或签名问题，属于服务侧，不归为照片问题
        if (text.contains("download")) return SERVICE_PROBLEM;
        boolean photoProblem = text.contains("invalidimage") || text.contains("noface") || text.contains("face.notfound")
                || text.contains("notfoundface") || text.contains("imagecontent") || text.contains("invalidfile");
        return photoProblem ? PHOTO_PROBLEM : SERVICE_PROBLEM;
    }
}
