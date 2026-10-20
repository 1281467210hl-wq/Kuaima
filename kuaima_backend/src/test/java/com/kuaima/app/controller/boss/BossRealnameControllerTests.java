package com.kuaima.app.controller.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.kuaima.app.common.service.FaceCompareService;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

class BossRealnameControllerTests {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifyShouldPassWhenConfidenceReachesThreshold() {
        UserRepository users = mock(UserRepository.class);
        FaceCompareService faceCompare = mock(FaceCompareService.class);
        User user = new User();
        user.setId(5L);
        when(users.findById(5L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        when(faceCompare.compare("http://oss/id.jpg?signed", "http://oss/face.jpg?signed")).thenReturn(92.35);

        var data = controller(users, faceCompare).verify(Map.of(
                "idCardImageUrl", "http://oss/id.jpg",
                "faceImageUrl", "http://oss/face.jpg"), login()).getData();

        assertEquals(Boolean.TRUE, data.get("passed"));
        assertEquals(92.4, ((Number) data.get("confidence")).doubleValue());
        assertEquals("APPROVED", user.getRealnameStatus());
        assertEquals("REALNAME", user.getCertType());
        assertEquals("http://oss/id.jpg", user.getRealnameIdCardImageUrl());
        assertEquals("http://oss/face.jpg", user.getRealnameFaceImageUrl());
    }

    @Test
    void verifyShouldRejectWhenConfidenceBelowThreshold() {
        UserRepository users = mock(UserRepository.class);
        FaceCompareService faceCompare = mock(FaceCompareService.class);
        User user = new User();
        user.setId(5L);
        when(users.findById(5L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        when(faceCompare.compare("http://oss/id.jpg?signed", "http://oss/face.jpg?signed")).thenReturn(41.2);

        var data = controller(users, faceCompare).verify(Map.of(
                "idCardImageUrl", "http://oss/id.jpg",
                "faceImageUrl", "http://oss/face.jpg"), login()).getData();

        assertEquals(Boolean.FALSE, data.get("passed"));
        assertEquals("REJECTED", user.getRealnameStatus());
    }

    @Test
    void statusShouldNotTreatLegacyApprovalWithoutFacePhotosAsVerified() {
        UserRepository users = mock(UserRepository.class);
        User legacyUser = new User();
        legacyUser.setId(5L);
        legacyUser.setRealnameStatus("APPROVED");
        when(users.findById(5L)).thenReturn(Optional.of(legacyUser));

        var data = controller(users, mock(FaceCompareService.class)).status(login()).getData();

        assertEquals("UNVERIFIED", data.get("realnameStatus"));
        assertEquals(null, data.get("idCardImageUrl"));
        assertEquals(null, data.get("faceImageUrl"));
    }

    @Test
    void statusShouldReportVerifiedWhenFacePhotosExist() {
        UserRepository users = mock(UserRepository.class);
        User verifiedUser = new User();
        verifiedUser.setId(5L);
        verifiedUser.setRealnameStatus("APPROVED");
        verifiedUser.setRealnameIdCardImageUrl("http://oss/id.jpg");
        verifiedUser.setRealnameFaceImageUrl("http://oss/face.jpg");
        when(users.findById(5L)).thenReturn(Optional.of(verifiedUser));

        var data = controller(users, mock(FaceCompareService.class)).status(login()).getData();

        assertEquals("APPROVED", data.get("realnameStatus"));
        assertEquals("http://oss/id.jpg?signed", data.get("idCardImageUrl"));
        assertEquals("http://oss/face.jpg?signed", data.get("faceImageUrl"));
        assertEquals("http://oss/id.jpg", data.get("idCardImageKey"));
        assertEquals("http://oss/face.jpg", data.get("faceImageKey"));
    }

    @Test
    void verifyShouldAcceptRelativeObjectKeysFromMiniProgram() {
        UserRepository users = mock(UserRepository.class);
        FaceCompareService faceCompare = mock(FaceCompareService.class);
        User user = new User();
        user.setId(5L);
        when(users.findById(5L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        when(faceCompare.compare("id-signed", "face-signed")).thenReturn(93d);

        var data = controllerWithOss(users, faceCompare,
                "insurance/realname/id.png", "insurance/realname/face.png")
                .verify(java.util.Map.of(
                        "idCardImageUrl", "insurance/realname/id.png",
                        "faceImageUrl", "insurance/realname/face.png"), login()).getData();

        assertEquals(Boolean.TRUE, data.get("passed"));
        assertEquals("insurance/realname/id.png", user.getRealnameIdCardImageUrl());
        assertEquals("insurance/realname/face.png", user.getRealnameFaceImageUrl());
    }

    @Test
    void verifyShouldSendImageContentWhenObjectsReadableFromOss() {
        UserRepository users = mock(UserRepository.class);
        FaceCompareService faceCompare = mock(FaceCompareService.class);
        User user = new User();
        user.setId(5L);
        when(users.findById(5L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        byte[] idBytes = {1, 2, 3};
        byte[] faceBytes = {4, 5, 6};
        com.kuaima.app.common.service.OssStorageService oss =
                mock(com.kuaima.app.common.service.OssStorageService.class);
        when(oss.readObjectBytes(org.mockito.ArgumentMatchers.eq("insurance/realname/id.png"), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(idBytes);
        when(oss.readObjectBytes(org.mockito.ArgumentMatchers.eq("insurance/realname/face.png"), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(faceBytes);
        when(faceCompare.compareImages(idBytes, faceBytes)).thenReturn(95d);

        var data = new BossRealnameController(users, faceCompare, oss).verify(Map.of(
                "idCardImageUrl", "insurance/realname/id.png",
                "faceImageUrl", "insurance/realname/face.png"), login()).getData();

        // 直接传图片内容，不再依赖签名 URL（避免阿里云跨地域拉图失败）
        assertEquals(Boolean.TRUE, data.get("passed"));
        org.mockito.Mockito.verify(faceCompare, org.mockito.Mockito.never())
                .compare(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private BossRealnameController controller(UserRepository users, FaceCompareService faceCompare) {
        com.kuaima.app.common.service.OssStorageService oss =
                mock(com.kuaima.app.common.service.OssStorageService.class);
        when(oss.playableUrl("http://oss/id.jpg")).thenReturn("http://oss/id.jpg?signed");
        when(oss.playableUrl("http://oss/face.jpg")).thenReturn("http://oss/face.jpg?signed");
        return new BossRealnameController(users, faceCompare, oss);
    }

    private UsernamePasswordAuthenticationToken login() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(new LoginUser(5L, "boss", "BOSS"), null);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }
    private BossRealnameController controllerWithOss(UserRepository users, FaceCompareService faceCompare,
                                                      String idKey, String faceKey) {
        com.kuaima.app.common.service.OssStorageService oss =
                mock(com.kuaima.app.common.service.OssStorageService.class);
        when(oss.playableUrl(idKey)).thenReturn("id-signed");
        when(oss.playableUrl(faceKey)).thenReturn("face-signed");
        return new BossRealnameController(users, faceCompare, oss);
    }
}
