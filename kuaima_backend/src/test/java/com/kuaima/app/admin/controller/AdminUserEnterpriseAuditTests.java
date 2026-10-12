package com.kuaima.app.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.kuaima.app.admin.entity.Certification;
import com.kuaima.app.admin.repository.CertificationRepository;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.coupon.repository.CouponRepository;
import com.kuaima.app.domain.coupon.repository.UserCouponRepository;
import com.kuaima.app.domain.points.repository.PointsAccountRepository;
import com.kuaima.app.domain.points.repository.PointsFlowRepository;
import com.kuaima.app.domain.reward.repository.RewardAccountRepository;
import com.kuaima.app.domain.reward.repository.RewardFlowRepository;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;
import com.kuaima.app.domain.wallet.repository.WalletRespository;

class AdminUserEnterpriseAuditTests {

    @Test
    void enterprisePassShouldSyncLatestCertificationRecord() {
        UserRepository users = mock(UserRepository.class);
        CertificationRepository certifications = mock(CertificationRepository.class);
        User user = new User();
        user.setId(86L);
        when(users.findById(86L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        Certification record = new Certification();
        record.setId(4L);
        record.setUserId(86L);
        record.setType("ENTERPRISE");
        record.setStatus("待审核");
        when(certifications.findTopByUserIdAndTypeOrderByIdDesc(86L, "ENTERPRISE"))
                .thenReturn(Optional.of(record));
        when(certifications.save(any())).thenReturn(record);

        controller(users, certifications).enterprisePass(86L);

        ArgumentCaptor<Certification> captor = ArgumentCaptor.forClass(Certification.class);
        verify(certifications).save(captor.capture());
        assertEquals("已通过", captor.getValue().getStatus());
        assertEquals(null, captor.getValue().getRejectReason());
        assertEquals("APPROVED", user.getEnterpriseStatus());
    }

    @Test
    void enterpriseRejectShouldSyncLatestCertificationRecord() {
        UserRepository users = mock(UserRepository.class);
        CertificationRepository certifications = mock(CertificationRepository.class);
        User user = new User();
        user.setId(86L);
        when(users.findById(86L)).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        Certification record = new Certification();
        record.setId(4L);
        record.setUserId(86L);
        record.setType("ENTERPRISE");
        record.setStatus("待审核");
        when(certifications.findTopByUserIdAndTypeOrderByIdDesc(86L, "ENTERPRISE"))
                .thenReturn(Optional.of(record));
        when(certifications.save(any())).thenReturn(record);

        controller(users, certifications).enterpriseReject(86L);

        ArgumentCaptor<Certification> captor = ArgumentCaptor.forClass(Certification.class);
        verify(certifications).save(captor.capture());
        assertEquals("已拒绝", captor.getValue().getStatus());
        assertEquals("管理员审核拒绝", captor.getValue().getRejectReason());
        assertEquals("REJECTED", user.getEnterpriseStatus());
    }

    private AdminUserController controller(UserRepository users, CertificationRepository certifications) {
        return new AdminUserController(users,
                mock(BaseOrderItemRespository.class), mock(BossOrderRespository.class),
                mock(WalletRespository.class), mock(PointsAccountRepository.class),
                mock(RewardAccountRepository.class), mock(PointsFlowRepository.class),
                mock(RewardFlowRepository.class), mock(UserCouponRepository.class),
                mock(CouponRepository.class), null, null, null, null, certifications);
    }
}
