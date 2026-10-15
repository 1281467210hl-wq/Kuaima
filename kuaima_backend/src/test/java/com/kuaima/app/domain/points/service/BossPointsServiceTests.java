package com.kuaima.app.domain.points.service;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import java.math.BigDecimal;import java.util.Optional;import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import com.kuaima.app.admin.entity.PointPurchaseOrder;import com.kuaima.app.admin.entity.PointPackageSetting;import com.kuaima.app.admin.repository.PointPurchaseOrderRepository;import com.kuaima.app.admin.repository.PointPackageSettingRepository;import com.kuaima.app.domain.points.entity.*;import com.kuaima.app.domain.points.repository.*;import com.kuaima.app.domain.user.constant.UserRole;import com.kuaima.app.domain.user.entity.User;import com.kuaima.app.domain.user.repository.UserRepository;import com.kuaima.app.wechat.service.WechatPayService;
class BossPointsServiceTests {
 PointsAccountRepository accounts=mock(PointsAccountRepository.class);PointsFlowRepository flows=mock(PointsFlowRepository.class);PointPackageSettingRepository packages=mock(PointPackageSettingRepository.class);PointPurchaseOrderRepository orders=mock(PointPurchaseOrderRepository.class);PointsGiftRepository gifts=mock(PointsGiftRepository.class);UserRepository users=mock(UserRepository.class);BossPointsService service=new BossPointsService(accounts,flows,packages,orders,gifts,users);
 @Test void overviewShouldUseAdminEnabledPackages(){PointsAccount a=new PointsAccount();a.setBalance(320);a.setRole(UserRole.BOSS);PointPackageSetting p=new PointPackageSetting();p.setId(1L);p.setName("体验套餐");p.setPoints(1000L);p.setPrice(new BigDecimal("0.01"));p.setRec(true);when(accounts.findFirstByUserIdAndRoleOrderByIdDesc(7L,UserRole.BOSS)).thenReturn(Optional.of(a));when(packages.findByEnabledTrueOrderBySortDescIdAsc()).thenReturn(java.util.List.of(p));var r=service.overview(7L);assertEquals(320,r.get("balance"));var views=(java.util.List<?>)r.get("packages");assertEquals(1,views.size());assertEquals(new BigDecimal("0.01"),((java.util.Map<?,?>)views.get(0)).get("price"));assertEquals(true,((java.util.Map<?,?>)views.get(0)).get("hot"));}
 @Test void giftShouldRejectInvalidPoints(){assertThrows(IllegalArgumentException.class,()->service.gift(7L,9L,50,"x"));}
 @Test void giftShouldTransferAndWriteRoleIsolatedFlows(){User worker=new User();worker.setId(9L);PointsAccount boss=new PointsAccount();boss.setUserId(7L);boss.setRole(UserRole.BOSS);boss.setBalance(1000);PointsAccount target=new PointsAccount();target.setUserId(9L);target.setRole(UserRole.USER);target.setBalance(20);when(users.findById(9L)).thenReturn(Optional.of(worker));when(gifts.findByIdempotencyKey("gift-1")).thenReturn(Optional.empty());when(accounts.findAllByUserIdAndRoleForUpdate(7L,UserRole.BOSS)).thenReturn(java.util.List.of(boss));when(accounts.findAllByUserIdAndRoleForUpdate(9L,UserRole.USER)).thenReturn(java.util.List.of(target));when(gifts.save(any())).thenAnswer(i->{PointsGift g=i.getArgument(0);g.setId(3L);return g;});service.gift(7L,9L,500,"gift-1");assertEquals(500,boss.getBalance());assertEquals(520,target.getBalance());ArgumentCaptor<PointsFlow> captor=ArgumentCaptor.forClass(PointsFlow.class);verify(flows,times(2)).save(captor.capture());assertEquals(java.util.List.of(UserRole.BOSS,UserRole.USER),captor.getAllValues().stream().map(PointsFlow::getRole).toList());}
 @Test void giftShouldAllowUnverifiedUserEvenWhenCurrentRoleIsBoss(){User worker=new User();worker.setId(9L);worker.setRole("BOSS");worker.setEnterpriseStatus("UNVERIFIED");PointsAccount boss=new PointsAccount();boss.setUserId(7L);boss.setRole(UserRole.BOSS);boss.setBalance(1000);PointsAccount target=new PointsAccount();target.setUserId(9L);target.setRole(UserRole.USER);target.setBalance(0);when(users.findById(9L)).thenReturn(Optional.of(worker));when(gifts.findByIdempotencyKey("gift-role-switch")).thenReturn(Optional.empty());when(accounts.findAllByUserIdAndRoleForUpdate(7L,UserRole.BOSS)).thenReturn(java.util.List.of(boss));when(accounts.findAllByUserIdAndRoleForUpdate(9L,UserRole.USER)).thenReturn(java.util.List.of(target));when(gifts.save(any())).thenAnswer(i->{PointsGift g=i.getArgument(0);g.setId(4L);return g;});service.gift(7L,9L,100,"gift-role-switch");assertEquals(900,boss.getBalance());assertEquals(100,target.getBalance());assertEquals(UserRole.USER,target.getRole());}
 @Test void giftShouldRejectLegacyApprovedEnterprise(){User worker=new User();worker.setId(9L);worker.setEnterpriseStatus("UNVERIFIED");worker.setCertType("ENTERPRISE");worker.setCertStatus("已通过");when(users.findById(9L)).thenReturn(Optional.of(worker));var error=assertThrows(IllegalArgumentException.class,()->service.gift(7L,9L,100,"gift-enterprise"));assertEquals("赠送对象不是有效零工",error.getMessage());verifyNoInteractions(accounts,flows,gifts);}
 @Test void markPaidShouldBeIdempotent(){PointPurchaseOrder o=new PointPurchaseOrder();o.setOrderNo("BP1");o.setPoints(1000L);o.setPointsGranted(true);when(orders.findByOrderNoAndBossId("BP1",7L)).thenReturn(Optional.of(o));assertSame(o,service.markPaid("BP1",7L));verifyNoInteractions(accounts,flows);}
 @Test void purchaseShouldTreatPackagePriceAsYuan(){PointPackageSetting p=new PointPackageSetting();p.setId(1L);p.setPoints(1000L);p.setPrice(new BigDecimal("0.01"));when(packages.findByIdAndEnabledTrue(1L)).thenReturn(Optional.of(p));when(orders.findByIdempotencyKey("p1")).thenReturn(Optional.empty());when(orders.save(any())).thenAnswer(i->i.getArgument(0));var r=service.purchase(7L,1L,"ALIPAY","p1");assertEquals(new BigDecimal("0.01"),r.get("amount"));verify(orders).save(argThat(o->new BigDecimal("0.01").compareTo(o.getAmount())==0));}
 @Test void wechatPrepayShouldReceiveYuanOrderAmount(){WechatPayService wechat=mock(WechatPayService.class);BossPointsService wechatService=new BossPointsService(accounts,flows,packages,orders,gifts,users,wechat);PointPackageSetting p=new PointPackageSetting();p.setId(1L);p.setPoints(5000L);p.setPrice(new BigDecimal("50.00"));User user=new User();user.setOpenid("openid-7");when(packages.findByIdAndEnabledTrue(1L)).thenReturn(Optional.of(p));when(orders.findByIdempotencyKey("wx-1")).thenReturn(Optional.empty());when(orders.save(any())).thenAnswer(i->i.getArgument(0));when(users.findById(7L)).thenReturn(Optional.of(user));when(wechat.prepay(anyString(),anyString(),any(),eq("openid-7"))).thenReturn(new com.alibaba.fastjson2.JSONObject());wechatService.purchase(7L,1L,"WECHAT","wx-1");verify(wechat).prepay(eq("快马日结积分购买"),anyString(),argThat(amount->new BigDecimal("50.00").compareTo(amount)==0),eq("openid-7"));}
 @Test void recordsCategoryPurchaseShouldIncludeAdminPurchase(){
  PointsFlow flow=new PointsFlow();flow.setId(1L);flow.setBizType("ADMIN_PURCHASE");flow.setDelta(1000);
  var pageable=org.springframework.data.domain.PageRequest.of(0,20);
  when(flows.findByUserIdAndRoleAndBizTypeIn(eq(7L),eq(UserRole.BOSS),eq(java.util.List.of("PURCHASE","ADMIN_PURCHASE")),eq(pageable)))
   .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(flow)));
  var result=service.records(7L,null,"PURCHASE",pageable);
  assertEquals(1,result.getTotalElements());assertEquals("ADMIN_PURCHASE",result.getContent().get(0).get("type"));
 }
 @Test void recordsCategoryExchangeShouldQueryNonPurchaseDeductions(){
  var pageable=org.springframework.data.domain.PageRequest.of(0,20);
  when(flows.findByUserIdAndRoleAndNonPurchaseDeduction(eq(7L),eq(UserRole.BOSS),eq(java.util.List.of("PURCHASE","ADMIN_PURCHASE")),eq(pageable)))
   .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
  service.records(7L,null,"exchange",pageable);
  verify(flows).findByUserIdAndRoleAndNonPurchaseDeduction(eq(7L),eq(UserRole.BOSS),eq(java.util.List.of("PURCHASE","ADMIN_PURCHASE")),eq(pageable));
 }
 @Test void recordsShouldKeepDetailedTypeFilter(){
  var pageable=org.springframework.data.domain.PageRequest.of(0,20);
  when(flows.findByUserIdAndRoleAndType(eq(7L),eq(UserRole.BOSS),eq("GIFT"),eq(pageable)))
   .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
  service.records(7L,"GIFT",null,pageable);
  verify(flows).findByUserIdAndRoleAndType(eq(7L),eq(UserRole.BOSS),eq("GIFT"),eq(pageable));
 }
 @Test void recordsShouldDefaultToAllWhenFiltersAreAbsent(){
  var pageable=org.springframework.data.domain.PageRequest.of(0,20);
  when(flows.findByUserIdAndRoleAndType(eq(7L),eq(UserRole.BOSS),eq("ALL"),eq(pageable)))
   .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
  service.records(7L,null,null,pageable);
  verify(flows).findByUserIdAndRoleAndType(eq(7L),eq(UserRole.BOSS),eq("ALL"),eq(pageable));
 }
 @Test void recordsShouldRejectConflictingCategoryAndType(){
  var error=assertThrows(IllegalArgumentException.class,()->service.records(7L,"GIFT","EXCHANGE",org.springframework.data.domain.PageRequest.of(0,20)));
  assertEquals("category 与 type 不能同时传非 ALL 值",error.getMessage());
 }
}
