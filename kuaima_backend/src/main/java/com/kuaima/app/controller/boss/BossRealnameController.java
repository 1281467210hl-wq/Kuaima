package com.kuaima.app.controller.boss;

import java.util.HashMap;
import java.util.Map;

import com.kuaima.app.common.service.FaceCompareService;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.common.Result;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.security.model.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 实名认证：身份证人像面与人脸照片 1:1 比对，老板端与零工端共用。 */
@RestController
@RequestMapping({"/boss/realname", "/worker/realname"})
@Tag(name = "实名认证", description = "人脸比对完成实名认证（老板端/零工端）")
public class BossRealnameController {

    /** 置信度阈值（0-100）：达到阈值判定为同一人 */
    private static final double PASS_CONFIDENCE = 80.0;

    private final UserRepository userRepository;
    private final FaceCompareService faceCompareService;
    private final OssStorageService ossStorageService;

    public BossRealnameController(UserRepository userRepository, FaceCompareService faceCompareService,
                                   OssStorageService ossStorageService) {
        this.userRepository = userRepository;
        this.faceCompareService = faceCompareService;
        this.ossStorageService = ossStorageService;
    }

    @Operation(summary = "实名认证状态", description = "返回当前用户实名状态及认证照片，已认证时再次进入直接展示")
    @GetMapping("/status")
    public Result<Map<String, Object>> status(Authentication authentication) {
        User user = currentUser(authentication);
        Map<String, Object> data = new HashMap<>();
        // 新实名口径 = 人脸比对通过：旧流程（姓名/证号审核）遗留的 APPROVED 没有人脸照片，
        // 不能直接判为已认证，需重新完成人脸比对。
        boolean faceVerified = "APPROVED".equals(user.getRealnameStatus())
                && hasText(user.getRealnameIdCardImageUrl())
                && hasText(user.getRealnameFaceImageUrl());
        data.put("realnameStatus", faceVerified ? CertificationStatus.APPROVED : CertificationStatus.UNVERIFIED);
        data.put("idCardImageUrl", user.getRealnameIdCardImageUrl());
        data.put("faceImageUrl", user.getRealnameFaceImageUrl());
        return Result.success(data);
    }

    @Operation(summary = "人脸比对实名认证", description = "对身份证人像面与人脸照片做 1:1 比对；置信度达到阈值即认证通过")
    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody Map<String, String> body, Authentication authentication) {
        String idCardImageUrl = normalizeUrl(body == null ? null : body.get("idCardImageUrl"));
        String faceImageUrl = normalizeUrl(body == null ? null : body.get("faceImageUrl"));
        if (idCardImageUrl == null) throw new IllegalArgumentException("请上传身份证人像面");
        if (faceImageUrl == null) throw new IllegalArgumentException("请上传人脸图片");

        // Bucket 为私有读写：先转成限时签名 URL，再交给阿里云人脸比对服务拉取图片。
        double confidence = faceCompareService.compare(
                ossStorageService.playableUrl(idCardImageUrl),
                ossStorageService.playableUrl(faceImageUrl));
        boolean passed = confidence >= PASS_CONFIDENCE;
        User user = currentUser(authentication);
        user.setRealnameIdCardImageUrl(idCardImageUrl);
        user.setRealnameFaceImageUrl(faceImageUrl);
        user.setRealnameStatus(passed ? CertificationStatus.APPROVED : CertificationStatus.REJECTED);
        user.setCertType(CertificationStatus.APPROVED.equals(user.getRealnameStatus())
                ? com.kuaima.app.domain.user.service.CertificationService.REALNAME : user.getCertType());
        userRepository.save(user);

        Map<String, Object> data = new HashMap<>();
        data.put("passed", passed);
        data.put("confidence", Math.round(confidence * 10d) / 10d);
        data.put("message", passed ? "人脸与身份证匹配成功" : "人脸与身份证不匹配，认证失败");
        return Result.success(data);
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        return url.startsWith("http://") || url.startsWith("https://") ? url : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser) || loginUser.id() == null) {
            throw new IllegalArgumentException("无法识别当前用户，请重新登录");
        }
        return userRepository.findById(loginUser.id())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("用户不存在"));
    }
}
