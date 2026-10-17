package com.kuaima.app.domain.social.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.kuaima.app.common.ForbiddenBusinessException;
import com.kuaima.app.common.service.OssStorageService;
import com.kuaima.app.domain.social.dto.SocialGroupCreateRequest;
import com.kuaima.app.domain.social.entity.SocialGroup;
import com.kuaima.app.domain.social.repository.SocialGroupMemberRepository;
import com.kuaima.app.domain.social.repository.SocialGroupRepository;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.security.model.LoginUser;

class SocialGroupServiceStorageTests {

    @Test
    void create_shouldStoreObjectKeyAndReturnSignedPreview() {
        SocialGroupRepository groups = mock(SocialGroupRepository.class);
        SocialGroupMemberRepository members = mock(SocialGroupMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        OssStorageService storage = mock(OssStorageService.class);
        User worker = new User();
        worker.setId(8L);
        worker.setRole("USER");
        worker.setRealnameStatus(CertificationStatus.APPROVED);
        when(users.findById(8L)).thenReturn(Optional.of(worker));
        when(groups.save(any(SocialGroup.class))).thenAnswer(invocation -> {
            SocialGroup group = invocation.getArgument(0);
            group.setId(21L);
            return group;
        });
        when(members.findByGroupIdAndUserId(21L, 8L)).thenReturn(Optional.empty());
        when(users.findAllById(any())).thenReturn(java.util.List.of());
        when(storage.inlinePreviewUrl("insurance/files/2026/10/14/qr.png"))
                .thenReturn("https://oss.example.com/signed.png");

        var view = new SocialGroupService(groups, members, users, storage)
                .create(new SocialGroupCreateRequest("日结交流群", "insurance/files/2026/10/14/qr.png"),
                        new LoginUser(8L, "worker", "USER"));

        ArgumentCaptor<SocialGroup> saved = ArgumentCaptor.forClass(SocialGroup.class);
        org.mockito.Mockito.verify(groups).save(saved.capture());
        assertEquals("insurance/files/2026/10/14/qr.png", saved.getValue().getQrcodeUrl());
        assertEquals("https://oss.example.com/signed.png", view.qrcodeUrl());
    }

    @Test
    void create_shouldAllowEnterpriseCertifiedUserOnWorkerSide() {
        SocialGroupRepository groups = mock(SocialGroupRepository.class);
        SocialGroupMemberRepository members = mock(SocialGroupMemberRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setId(9L);
        user.setRealnameStatus(CertificationStatus.APPROVED);
        user.setEnterpriseStatus(CertificationStatus.APPROVED);
        when(users.findById(9L)).thenReturn(Optional.of(user));
        when(groups.save(any(SocialGroup.class))).thenAnswer(invocation -> {
            SocialGroup group = invocation.getArgument(0);
            group.setId(22L);
            return group;
        });
        when(members.findByGroupIdAndUserId(22L, 9L)).thenReturn(Optional.empty());
        when(users.findAllById(any())).thenReturn(java.util.List.of());

        var view = new SocialGroupService(groups, members, users, mock(OssStorageService.class))
                .create(new SocialGroupCreateRequest("周末兼职群", null), new LoginUser(9L, "dual", "USER"));

        assertEquals("周末兼职群", view.name());
    }

    @Test
    void create_shouldRejectBossSideLogin() {
        UserRepository users = mock(UserRepository.class);
        SocialGroupService service = new SocialGroupService(mock(SocialGroupRepository.class),
                mock(SocialGroupMemberRepository.class), users, mock(OssStorageService.class));

        ForbiddenBusinessException error = assertThrows(ForbiddenBusinessException.class,
                () -> service.create(new SocialGroupCreateRequest("周末兼职群", null), new LoginUser(9L, "dual", "BOSS")));

        assertEquals("请切换到零工端使用社群", error.getMessage());
    }
}
