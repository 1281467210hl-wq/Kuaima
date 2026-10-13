package com.kuaima.app.domain.boss.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.kuaima.app.domain.boss.entity.BaseOrderItem;

public interface BaseOrderItemRespository extends JpaRepository<BaseOrderItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from BaseOrderItem i where i.id = :id")
    Optional<BaseOrderItem> findByIdForUpdate(@Param("id") Long id);

    /** 查询某订单的所有报名记录 */
    List<BaseOrderItem> findByOrderId(Long orderId);

    List<BaseOrderItem> findByOrderIdIn(Collection<Long> orderIds);

    /** 当前老板全部报名记录，单次批量查询用于个人页统计。 */
    @Query("select i from BaseOrderItem i join BossOrder o on i.orderId = o.id where o.createBy = :bossId")
    List<BaseOrderItem> findAllByBossId(@Param("bossId") Long bossId);

    /** 后台订单列表分页：按报名状态过滤 + 分页（status 为空时返回全部） */
    Page<BaseOrderItem> findByStatus(String status, Pageable pageable);

    /** 后台订单列表按招工订单的计划用工开始时间筛选。结束时间采用开区间，避免漏掉结束日期当天的记录。 */
    @Query("""
            select i from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where (:status is null or i.status = :status)
              and (:keyword is null or lower(coalesce(o.orderNo, '')) like lower(concat('%', :keyword, '%')))
              and (:startTime is null or o.startTime >= :startTime)
              and (:endTime is null or o.startTime < :endTime)
              and (:enterpriseTypeId is null or coalesce(o.enterpriseTypeIds, '') like concat('%', :enterpriseTypeId, '%'))
            order by i.id desc
            """)
    Page<BaseOrderItem> searchAdminOrders(@Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("startTime") java.util.Date startTime,
                                          @Param("endTime") java.util.Date endTime,
                                          @Param("enterpriseTypeId") String enterpriseTypeId,
                                          Pageable pageable);

    @Query("""
            select i.status, count(i) from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where (:status is null or i.status = :status)
              and (:startTime is null or o.startTime >= :startTime)
              and (:endTime is null or o.startTime < :endTime)
              and (:enterpriseTypeId is null or coalesce(o.enterpriseTypeIds, '') like concat('%', :enterpriseTypeId, '%'))
            group by i.status
            """)
    List<Object[]> countAdminOrderStatuses(@Param("status") String status,
                                           @Param("startTime") java.util.Date startTime,
                                           @Param("endTime") java.util.Date endTime,
                                           @Param("enterpriseTypeId") String enterpriseTypeId);

    /** 查询某用户报名过的订单记录 */
    List<BaseOrderItem> findByUserId(Long userId);

    /** 客服会话右侧关联订单：按报名记录倒序取最近订单。 */
    Page<BaseOrderItem> findByUserId(Long userId, Pageable pageable);

    /** 批量查询多个零工的报名记录，供后台列表统计使用，避免逐用户 N+1 查询。 */
    List<BaseOrderItem> findByUserIdIn(Collection<Long> userIds);

    long countByUserIdAndStatus(Long userId, String status);

    long countByUserId(Long userId);

    long countByUserIdAndHireDateIsNotNull(Long userId);

    long countByUserIdAndStatusAndEarlyLeaveTrue(Long userId, String status);

    @Query("""
            select count(i) from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where i.userId = :userId
              and i.hireDate is not null
              and i.workDate is null
              and o.endTime < :now
            """)
    long countNoShowByUserId(@Param("userId") Long userId, @Param("now") java.util.Date now);

    /** 零工订单聚合列表：报名记录与订单类型、状态一起分页查询。statuses 为空则不过滤状态。 */
    @Query("""
            select i from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where i.userId = :userId
              and (:type is null or o.type = :type)
              and (:statuses is null or i.status in :statuses)
            order by i.id desc
            """)
    Page<BaseOrderItem> findWorkerOrders(@Param("userId") Long userId,
                                         @Param("type") String type,
                                         @Param("statuses") Collection<String> statuses,
                                         Pageable pageable);

    /** 查询某订单下某用户是否已报名 */
    boolean existsByOrderIdAndUserId(Long orderId, Long userId);
    Optional<BaseOrderItem> findByOrderIdAndUserId(Long orderId, Long userId);

    /**
     * 某零工在某招工类型(daily/heldBack/month)下的报名记录（按订单类型关联过滤）。
     * 用于"我的月结/压薪日结"列表。
     */
    @Query("""
            select i from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where i.userId = :userId and o.type = :type
            order by i.id desc
            """)
    List<BaseOrderItem> findByUserIdAndOrderType(@Param("userId") Long userId,
                                                 @Param("type") String type);

    /**
     * 某老板发布的全部订单下的报名记录（用于历史工人/报名总数统计）。
     */
    @Query("""
            select i from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where o.createBy = :bossId
            order by i.id desc
            """)
    List<BaseOrderItem> findByBossIdJoinOrder(@Param("bossId") Long bossId);

    /**
     * 某老板已经完成或已经支付结算的合作记录，按最近合作时间倒序。
     * 结算记录使用实体名 Settlement，避免仅依赖报名记录状态造成已结算历史遗漏。
     */
    @Query("""
            select i from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where o.createBy = :bossId
              and (i.status = '已完成'
                   or exists (select s.id from Settlement s
                              where s.itemId = i.id
                                and s.status = '已支付'))
            order by coalesce(i.finishDate, i.workDate, i.hireDate, i.applyDate) desc, i.id desc
            """)
    List<BaseOrderItem> findHistoryByBossId(@Param("bossId") Long bossId);

    /** 某老板发布的全部订单下的报名总数 */
    @Query("""
            select count(i) from BaseOrderItem i
            join BossOrder o on i.orderId = o.id
            where o.createBy = :bossId
            """)
    long countApplicantsByBossId(@Param("bossId") Long bossId);

    /** 按订单批量统计有效报名数，避免岗位列表逐单查询 */
    @Query("""
            select i.orderId, count(i)
            from BaseOrderItem i
            where i.orderId in :orderIds and i.status in :statuses
            group by i.orderId
            """)
    List<Object[]> countByOrderIdsAndStatuses(@Param("orderIds") Collection<Long> orderIds,
                                              @Param("statuses") Collection<String> statuses);

    /** 按零工批量统计已完成订单数 */
    @Query("""
            select i.userId, count(i)
            from BaseOrderItem i
            where i.userId in :userIds and i.status = '已完成'
            group by i.userId
            """)
    List<Object[]> countCompletedByUserIds(@Param("userIds") Collection<Long> userIds);
}
