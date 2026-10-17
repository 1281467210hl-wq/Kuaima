package com.kuaima.app.domain.user.service;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kuaima.app.domain.boss.constant.BossStatus;
import com.kuaima.app.domain.boss.entity.BossOrder;
import com.kuaima.app.domain.boss.repository.BaseOrderItemRespository;
import com.kuaima.app.domain.boss.repository.BossOrderRespository;
import com.kuaima.app.domain.boss.service.BossAttendanceCodeService;
import com.kuaima.app.domain.boss.service.BossOrderService;
import jakarta.persistence.EntityNotFoundException;
@Service
public class WorkerAttendanceService {
 private final BossOrderRespository orders; private final BaseOrderItemRespository items; private final BossAttendanceCodeService codes; private final BossOrderService bossOrders;
 public WorkerAttendanceService(BossOrderRespository orders,BaseOrderItemRespository items,BossAttendanceCodeService codes,BossOrderService bossOrders){this.orders=orders;this.items=items;this.codes=codes;this.bossOrders=bossOrders;}
 @Transactional public Map<String,Object> checkIn(Long workerId,Long orderId,String code){BossOrder o=orders.findById(orderId).orElseThrow(()->new EntityNotFoundException("订单不存在: "+orderId));var i=items.findByOrderIdAndUserId(orderId,workerId).orElseThrow(()->new EntityNotFoundException("未找到当前零工的报名记录"));if(!BossStatus.ITEM_HIRED.equals(i.getStatus()))throw new IllegalArgumentException("仅已录用零工可以开工");verifyCode(o,true,code);i.setStatus(BossStatus.ITEM_ON_WORK);i.setWorkDate(Date.valueOf(LocalDate.now()));i.setWorkAt(java.time.LocalDateTime.now());items.save(i);if(BossStatus.ORDER_RECRUITING.equals(o.getOrderStatus())||BossStatus.ORDER_RECRUIT_END.equals(o.getOrderStatus())||BossStatus.ORDER_WORKING.equals(o.getOrderStatus())){o.setOrderStatus(BossStatus.ORDER_WORKING);orders.save(o);}return Map.of("checkedIn",true,"orderId",orderId,"status",BossStatus.ORDER_WORKING,"itemStatus",i.getStatus());}
 @Transactional public Map<String,Object> earlyLeave(Long workerId,Long orderId,String code){BossOrder o=orders.findById(orderId).orElseThrow(()->new EntityNotFoundException("订单不存在: "+orderId));var i=items.findByOrderIdAndUserId(orderId,workerId).orElseThrow(()->new EntityNotFoundException("未找到当前零工的报名记录"));if(!BossStatus.ITEM_ON_WORK.equals(i.getStatus()))throw new IllegalArgumentException("仅开工中的零工可以早退");verifyCode(o,false,code);BossOrder updated=bossOrders.finishByEarlyLeave(i.getId());return Map.of("earlyLeft",true,"orderId",orderId,"status",updated.getOrderStatus(),"itemStatus",BossStatus.ITEM_PENDING_SETTLE);}
 private void verifyCode(BossOrder order, boolean work, String code) {
   if (order.getEnterpriseId() != null) {
     codes.verifyByEnterprise(order.getEnterpriseId(), order.getCreateBy(), work, code);
   } else {
     // 历史订单没有企业归属时，继续按发布人兼容校验。
     codes.verify(order.getCreateBy(), work, code);
   }
 }
}
