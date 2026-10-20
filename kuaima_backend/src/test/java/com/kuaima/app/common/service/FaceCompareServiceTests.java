package com.kuaima.app.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FaceCompareServiceTests {

    @Test
    void photoErrorsShouldAskUserToRetake() {
        assertEquals(FaceCompareService.PHOTO_PROBLEM,
                FaceCompareService.userMessage("InvalidImage.NotFoundFace", "no face detected"));
        assertEquals(FaceCompareService.PHOTO_PROBLEM,
                FaceCompareService.userMessage("InvalidFile.Content", "image content invalid"));
    }

    @Test
    void serviceErrorsShouldNotBlameUserPhoto() {
        // 账号、权限、开通、跨地域拉图失败等都不是照片问题，不能提示用户「检查图片清晰度」
        assertEquals(FaceCompareService.SERVICE_PROBLEM,
                FaceCompareService.userMessage("InvalidAccessKeyId.NotFound", "Specified access key is not found."));
        assertEquals(FaceCompareService.SERVICE_PROBLEM,
                FaceCompareService.userMessage("Forbidden.NoPermission", "not purchased"));
        assertEquals(FaceCompareService.SERVICE_PROBLEM,
                FaceCompareService.userMessage("InvalidImage.Download", "download image failed: url region not supported"));
    }

    @Test
    void missingCredentialsShouldReportServiceProblem() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new FaceCompareService().compare("a", "b"));

        assertEquals(FaceCompareService.SERVICE_PROBLEM, error.getMessage());
    }
}
