package com.kuaima.app.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.admin.repository.CertificationRepository;

class CertificationServiceTests {

    @Test
    void publishEligibilityShouldRejectLegacyApprovalWithoutFacePhotos() {
        User user = approvedBoss();
        CertificationService service = service(user);

        Map<String, Object> result = service.publishEligibility(7L);

        assertFalse((Boolean) result.get("canPublish"));
        assertEquals(CertificationStatus.UNVERIFIED, result.get("realnameStatus"));
        assertTrue(((java.util.List<?>) result.get("missing")).contains("REALNAME"));
    }

    @Test
    void publishEligibilityShouldPassWhenFacePhotosExist() {
        User user = approvedBoss();
        user.setRealnameIdCardImageUrl("http://oss/id.jpg");
        user.setRealnameFaceImageUrl("http://oss/face.jpg");
        CertificationService service = service(user);

        Map<String, Object> result = service.publishEligibility(7L);

        assertTrue((Boolean) result.get("canPublish"));
        assertEquals(CertificationStatus.APPROVED, result.get("realnameStatus"));
        assertTrue(((java.util.List<?>) result.get("missing")).isEmpty());
    }

    @Test
    void requirePublishEligibilityShouldBlockLegacyApprovalWithoutFacePhotos() {
        CertificationService service = service(approvedBoss());

        ForbiddenBusinessException error = assertThrows(ForbiddenBusinessException.class,
                () -> service.requirePublishEligibility(7L));

        assertEquals("请先完成人脸实名认证和企业认证", error.getMessage());
    }

    private CertificationService service(User user) {
        UserRepository users = mock(UserRepository.class);
        CertificationRepository certifications = mock(CertificationRepository.class);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        return new CertificationService(users, certifications);
    }

    private User approvedBoss() {
        User user = new User();
        user.setId(7L);
        user.setRealnameStatus(CertificationStatus.APPROVED);
        user.setEnterpriseStatus(CertificationStatus.APPROVED);
        return user;
    }
}
