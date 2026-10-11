package com.kuaima.app.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aliyun.facebody20191230.Client;
import com.aliyun.facebody20191230.models.CompareFaceRequest;
import com.aliyun.facebody20191230.models.CompareFaceResponse;
import com.aliyun.teaopenapi.models.Config;

/**
 * 阿里云人脸 1:1 比对：输入身份证人像面与人脸照片 URL，返回置信度（0-100）。
 */
@Service
public class FaceCompareService {

    @Value("${aliyun.face.endpoint:facebody.cn-hangzhou.aliyuncs.com}")
    private String endpoint;

    @Value("${aliyun.face.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.face.access-key-secret:}")
    private String accessKeySecret;

    public double compare(String imageUrlA, String imageUrlB) {
        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new IllegalStateException("人脸比对服务未配置，请设置 ALIYUN_FACE_ACCESS_KEY_ID / ALIYUN_FACE_ACCESS_KEY_SECRET");
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint);
            Client client = new Client(config);
            CompareFaceRequest request = new CompareFaceRequest()
                    .setImageURLA(imageUrlA)
                    .setImageURLB(imageUrlB);
            CompareFaceResponse response = client.compareFace(request);
            Float confidence = response == null || response.getBody() == null || response.getBody().getData() == null
                    ? null : response.getBody().getData().getConfidence();
            return confidence == null ? 0d : confidence;
        } catch (Exception e) {
            throw new IllegalStateException("人脸比对失败，请检查图片清晰度后重试", e);
        }
    }
}
