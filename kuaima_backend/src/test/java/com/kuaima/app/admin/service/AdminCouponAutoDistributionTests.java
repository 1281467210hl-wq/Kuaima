package com.kuaima.app.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.kuaima.app.domain.coupon.entity.Coupon;
import com.kuaima.app.domain.coupon.entity.UserCoupon;
import com.kuaima.app.domain.coupon.repository.CouponRepository;
import com.kuaima.app.domain.coupon.repository.UserCouponRepository;
import com.kuaima.app.domain.user.constant.CertificationStatus;
import com.kuaima.app.domain.user.constant.UserRole;
import com.kuaima.app.domain.user.entity.User;
import com.kuaima.app.domain.user.repository.UserRepository;

class AdminCouponAutoDistributionTests {
    private CouponRepository coupons;
    private UserCouponRepository claims;
    private UserRepository users;
    private AdminCouponService service;

    @BeforeEach
    void setUp() {
        coupons = mock(CouponRepository.class); claims = mock(UserCouponRepository.class);
        users = mock(UserRepository.class); service = new AdminCouponService(coupons, claims, users);
        when(coupons.saveAndFlush(any())).thenAnswer(invocation -> {
            Coupon c = invocation.getArgument(0); c.setId(10L); return c;
        });
        when(coupons.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(claims.findUserIdsByCouponId(any())).thenReturn(List.of());
        when(claims.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void immediateCustomCouponShouldIssueToEverySpecifiedUser() {
        User boss = user(1L, UserRole.BOSS); User worker = user(2L, UserRole.USER);
        when(users.findById(1L)).thenReturn(Optional.of(boss));
        when(users.findById(2L)).thenReturn(Optional.of(worker));

        Coupon coupon = service.create(body("全部用户", "custom", List.of("1", "2"), 2, "now"));

        assertEquals(2, coupon.getClaimed());
        verify(claims).saveAll(argThat(values -> {
            List<UserCoupon> list = (List<UserCoupon>) values;
            return list.size() == 2 && list.stream().allMatch(v -> "UNUSED".equals(v.getStatus()));
        }));
    }

    @Test
    void allScopeShouldFilterRoleAndRespectTotalLimit() {
        when(users.findAll()).thenReturn(List.of(user(1L, UserRole.BOSS), user(2L, UserRole.USER),
                user(3L, UserRole.BOSS)));

        Coupon coupon = service.create(body("老板", "all", List.of(), 1, "now"));

        assertEquals(1, coupon.getClaimed());
        verify(claims).saveAll(argThat(values -> ((List<?>) values).size() == 1));
    }

    @Test
    void timingCouponShouldNotIssueBeforeGrantTimeAndShouldBeIdempotentWhenDue() {
        Map<String,Object> body = body("老板", "all", List.of(), 5, "timing");
        body.put("grantStart", "2099-01-01 00:00:00");
        service.create(body);
        verify(claims, never()).saveAll(any());

        Coupon due = new Coupon(); due.setId(11L); due.setTarget("老板"); due.setScope("all");
        due.setTotal(5); due.setGrantMode("timing"); due.setGrantStart(java.time.LocalDateTime.now().minusMinutes(1));
        due.setValidMode("after"); due.setValidDays(7); due.setDeleted(false); due.setStopped(false);
        when(coupons.findByIdForUpdate(11L)).thenReturn(Optional.of(due));
        when(claims.findUserIdsByCouponId(11L)).thenReturn(List.of(), List.of(1L));
        when(users.findAll()).thenReturn(List.of(user(1L, UserRole.BOSS)));

        service.distributeDue(11L);
        service.distributeDue(11L);

        verify(claims, times(1)).saveAll(any());
    }

    @Test
    void immediateCouponWithMissedInitialDistributionShouldBePickedUpByDueTask() {
        Coupon due = new Coupon(); due.setId(12L); due.setTarget("老板"); due.setScope("all");
        due.setTotal(4); due.setClaimed(0); due.setGrantMode("now");
        due.setGrantStart(java.time.LocalDateTime.now().minusMinutes(1));
        due.setValidMode("after"); due.setValidDays(7); due.setDeleted(false); due.setStopped(false);
        when(coupons.findByIdForUpdate(12L)).thenReturn(Optional.of(due));
        when(users.findAll()).thenReturn(List.of(user(64L, UserRole.BOSS)));

        service.distributeDue(12L);

        verify(claims).saveAll(argThat(values -> ((List<?>) values).size() == 1));
        assertEquals(1, due.getClaimed());
    }

    @Test
    void workerCouponShouldAcceptUncertifiedUserLastLoggedInAsBoss() {
        // 复现：用户最后停留在老板端（role=BOSS）但未做企业认证，候选列表把他列为零工
        User lastOnBossSide = new User(); lastOnBossSide.setId(11L); lastOnBossSide.setRole(UserRole.BOSS);
        when(users.findById(11L)).thenReturn(Optional.of(lastOnBossSide));

        Coupon coupon = service.create(body("零工", "custom", List.of("11"), 2, "now"));

        assertEquals(1, coupon.getClaimed());
    }

    @Test
    void workerCouponShouldRejectEnterpriseCertifiedBoss() {
        User boss = user(12L, UserRole.USER);
        boss.setEnterpriseStatus(CertificationStatus.APPROVED);
        when(users.findById(12L)).thenReturn(Optional.of(boss));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(body("零工", "custom", List.of("12"), 2, "now")));

        assertEquals("指定用户身份与发放对象不匹配: 12", error.getMessage());
    }

    @Test
    void allScopeWorkerCouponShouldUseBusinessIdentityInsteadOfLastLoginSide() {
        User uncertifiedOnBossSide = new User(); uncertifiedOnBossSide.setId(21L); uncertifiedOnBossSide.setRole(UserRole.BOSS);
        when(users.findAll()).thenReturn(List.of(user(20L, UserRole.BOSS), uncertifiedOnBossSide, user(22L, UserRole.USER)));

        Coupon coupon = service.create(body("零工", "all", List.of(), 10, "now"));

        assertEquals(2, coupon.getClaimed());
    }

    /** 老板 = 企业认证通过（业务身份），与 role 字段无关。 */
    private User user(Long id, String role) {
        User u = new User(); u.setId(id); u.setRole(role);
        if (UserRole.BOSS.equals(role)) u.setEnterpriseStatus(CertificationStatus.APPROVED);
        return u;
    }
    private Map<String,Object> body(String target, String scope, List<String> ids, int total, String mode) {
        Map<String,Object> b = new HashMap<>(); b.put("name", "新人券"); b.put("type", "立减券");
        b.put("target", target); b.put("scope", scope); b.put("assignUsers", ids); b.put("total", total);
        b.put("limit", 1); b.put("grantMode", mode); b.put("validMode", "after"); b.put("validDays", 7);
        b.put("amount", 10); return b;
    }
}
