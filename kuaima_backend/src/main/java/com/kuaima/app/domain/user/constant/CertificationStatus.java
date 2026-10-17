package com.kuaima.app.domain.user.constant;

import org.springframework.util.StringUtils;

import com.kuaima.app.domain.user.entity.User;

/** 用户认证状态。 */
public final class CertificationStatus {

    public static final String UNVERIFIED = "UNVERIFIED";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private CertificationStatus() {
    }

    /** 实名口径 = 人脸比对通过且两张认证照片齐全；旧流程遗留的无照片 APPROVED 不算。 */
    public static boolean isFaceVerified(User user) {
        return user != null && APPROVED.equals(user.getRealnameStatus())
                && StringUtils.hasText(user.getRealnameIdCardImageUrl())
                && StringUtils.hasText(user.getRealnameFaceImageUrl());
    }
}
