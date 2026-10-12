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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @Operation(summary = "上传实名认证照片", description = "仅支持 JPG、JPEG、PNG，不超过 10MB，保存到 insurance/realname 目录")
    @PostMapping("/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("图片大小不能超过10MB");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        // 小程序 uni.uploadFile 的分片 Content-Type 常为 application/octet-stream 或空，
        // 因此以扩展名为准；仅在客户端给出明确的非图片 MIME 时拒绝。
        boolean imageExtension = java.util.Set.of("jpg", "jpeg", "png").contains(extension);
        boolean unknownOrImageMime = contentType.isBlank()
                || "application/octet-stream".equals(contentType)
                || java.util.Set.of("image/jpeg", "image/png").contains(contentType);
        if (!imageExtension || !unknownOrImageMime) {
            throw new IllegalArgumentException("仅支持 JPG、JPEG、PNG 图片");
        }
        Map<String, String> result = ossStorageService.upload(file, "insurance/realname", "." + extension);
        // Bucket 私有读写：objectKey 用于落库/比对，url 转成限时签名地址供页面回显。
        result.put("url", ossStorageService.playableUrl(result.get("objectKey")));
        return result;
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
        data.put("idCardImageKey", user.getRealnameIdCardImageUrl());
        data.put("faceImageKey", user.getRealnameFaceImageUrl());
        data.put("idCardImageUrl", ossStorageService.playableUrl(user.getRealnameIdCardImageUrl()));
        data.put("faceImageUrl", ossStorageService.playableUrl(user.getRealnameFaceImageUrl()));
        return Result.success(data);
    }

    @Operation(summary = "人脸比对实名认证", description = "对身份证人像面与人脸照片做 1:1 比对；置信度达到阈值即认证通过")
    @PostMapping("/verify")
    public Result<Map<String, Object>> verify(@RequestBody Map<String, String> body, Authentication authentication) {
        String idCardImageUrl = normalizeImageReference(body == null ? null : body.get("idCardImageUrl"));
        String faceImageUrl = normalizeImageReference(body == null ? null : body.get("faceImageUrl"));
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

    /** 接受私有 Bucket 的对象 key（insurance/realname/...）或完整 http(s) URL，二者都能被 playableUrl 签名。 */
    private String normalizeImageReference(String value) {
        if (value == null || value.isBlank()) return null;
        String reference = value.trim();
        if (reference.startsWith("http://") || reference.startsWith("https://")) return reference;
        // 相对对象 key 必须带目录分隔符，且不能是其他协议。
        return reference.contains("/") && !reference.contains("://") ? reference : null;
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
