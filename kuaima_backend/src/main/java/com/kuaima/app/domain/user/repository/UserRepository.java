package com.kuaima.app.domain.user.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.kuaima.app.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByOpenid(String openid);

    Optional<User> findByWorkerCode(String workerCode);

    Optional<User> findByBossCode(String bossCode);

    /** 用户快照与最近信用流水一次查询返回，减少公网数据库串行往返。 */
    @Query(value = """
            select u.id as userId,
                   case when upper(coalesce(u.enterprise_status, '')) = 'APPROVED'
                              or (upper(coalesce(u.cert_type, '')) = 'ENTERPRISE' and u.cert_status = '已通过')
                        then u.boss_code else u.worker_code end as businessId,
                   coalesce(u.credit_score, 0) as creditScore,
                   coalesce(u.star_score, 0) as starScore,
                   case when upper(coalesce(u.enterprise_status, '')) = 'APPROVED'
                              or (upper(coalesce(u.cert_type, '')) = 'ENTERPRISE' and u.cert_status = '已通过')
                        then 'BOSS_CREDIT' else 'WORKER_STAR' end as scoreType,
                   f.id as flowId, f.biz_no as flowBizNo, f.delta as delta, f.before_score as beforeScore,
                   f.after_score as afterScore, f.rule_code as ruleCode, f.biz_type as bizType,
                   f.reason as reason, f.`timestamp` as flowTimestamp
            from sys_user u
            left join credit_flow f
              on f.user_id = u.id
             and f.score_type = case
                   when upper(coalesce(u.enterprise_status, '')) = 'APPROVED'
                        or (upper(coalesce(u.cert_type, '')) = 'ENTERPRISE' and u.cert_status = '已通过')
                   then 'BOSS_CREDIT' else 'WORKER_STAR' end
            where u.worker_code = upper(:identifier)
               or u.boss_code = upper(:identifier)
               or u.id = case when :identifier regexp '^[0-9]+$' then cast(:identifier as unsigned) else null end
            order by f.`timestamp` desc, f.id desc
            limit 50
            """, nativeQuery = true)
    List<AdminCreditDetailRow> findAdminCreditDetail(@Param("identifier") String identifier);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** 按手机号查询（可能多个用户共用同一手机号） */
    List<User> findByPhone(String phone);

    /** 查询尚未绑定微信、可登录老板端的授权员工子账号。 */
    @Query("""
            select u from User u
            where u.phone = :phone
              and u.parentUserId is not null
              and coalesce(u.status, '') = '正常'
              and u.openid is null
            """)
    List<User> findUnboundActiveSubAccountsByPhone(@Param("phone") String phone);

    /** 按角色查询（角色: BOSS/USER） */
    List<User> findByRole(String role);

    /** 按角色计数 */
    long countByRole(String role);

    long countByRoleAndCity(String role, String city);


    /** 按角色分页查询 */
    Page<User> findByRole(String role, Pageable pageable);

    /** 按 角色/状态/关键词 组合过滤分页查询 */
    @Query("""
            select u from User u
            where u.role = :role
              and (:status is null or u.status = :status)
              and ((:keyword is null) or (u.username like %:keyword%) or (u.nickname like %:keyword%) or (u.phone like %:keyword%) or (u.companyName like %:keyword%))
            """)
    Page<User> searchByRole(@Param("role") String role,
                            @Param("status") String status,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    /** 后台零工列表：USER 是基础身份，企业认证通过的用户同时保留零工身份。 */
    @Query("""
            select u from User u
            where (u.role = 'USER'
                   or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED'
                   or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过'))
              and (:status is null or u.status = :status)
              and (:startDate is null or u.date >= :startDate)
              and (:endDate is null or u.date <= :endDate)
              and ((:keyword is null) or (u.username like %:keyword%) or (u.nickname like %:keyword%) or (u.phone like %:keyword%) or (u.companyName like %:keyword%))
            """)
    Page<User> searchWorkers(@Param("role") String role,
                             @Param("status") String status,
                             @Param("keyword") String keyword,
                             @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate,
                             Pageable pageable);

    /** 后台零工在线/离线筛选：由管理端会话管理器提供目标用户 ID 集合。 */
    @Query("""
            select u from User u
            where (u.role = 'USER'
                   or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED'
                   or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过'))
              and u.id in :ids
              and (:startDate is null or u.date >= :startDate)
              and (:endDate is null or u.date <= :endDate)
              and ((:keyword is null) or (u.username like %:keyword%) or (u.nickname like %:keyword%) or (u.phone like %:keyword%) or (u.companyName like %:keyword%))
            """)
    Page<User> searchWorkersByIds(@Param("role") String role,
                                  @Param("ids") List<Long> ids,
                                  @Param("keyword") String keyword,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate,
                                  Pageable pageable);

    @Query("select u.id from User u where u.role = 'USER' or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED' or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过')")
    List<Long> findWorkerIdentityIds();

    @Query("select u from User u where u.role = 'USER' or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED' or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过')")
    List<User> findWorkerIdentities();

    @Query("select count(u) from User u where u.role = 'USER' or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED' or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过')")
    long countWorkerIdentities();

    @Query("select count(u) from User u where (u.role = 'USER' or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED' or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过')) and u.status = :status")
    long countWorkerIdentitiesByStatus(@Param("status") String status);

    @Query("select count(u) from User u where (u.role = 'USER' or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED' or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过')) and u.city = :city")
    long countWorkerIdentitiesByCity(@Param("city") String city);

    @Query("select count(u) from User u where u.date <= :date")
    long countWorkerIdentitiesByDateLessThanEqual(@Param("date") LocalDate date);

    @Query("select count(u) from User u where u.date >= :startDate and u.date <= :endDate")
    long countWorkerIdentitiesByDateBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query(value = "select count(*) from sys_user u where u.`date` >= date_format(current_date, '%Y-%m-01') and u.`date` < date_add(date_format(current_date, '%Y-%m-01'), interval 1 month)", nativeQuery = true)
    long countCurrentMonthWorkerIdentities();

    long countByRoleAndStatus(String role, String status);

    long countByRoleAndDateBetween(String role, LocalDate startDate, LocalDate endDate);

    /** 使用数据库当前日期统计本月新增，避免应用服务器与数据库时区不一致。 */
    @Query(value = """
            select count(*) from sys_user u
            where u.role = :role
              and u.`date` >= date_format(current_date, '%Y-%m-01')
              and u.`date` < date_add(date_format(current_date, '%Y-%m-01'), interval 1 month)
            """, nativeQuery = true)
    long countCurrentMonthByRole(@Param("role") String role);

    long countByRoleAndDateLessThanEqual(String role, LocalDate date);

    /** 按业务身份统计老板：企业认证通过才算老板，兼容历史认证字段。 */
    @Query("select count(u) from User u where upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED' or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过')")
    long countBossIdentities();

    @Query("select u from User u where (:role is null or u.role = :role) and (:keyword is null or u.username like concat('%', :keyword, '%') or u.nickname like concat('%', :keyword, '%') or u.phone like concat('%', :keyword, '%') or u.companyName like concat('%', :keyword, '%'))")
    Page<User> searchRecipients(@Param("role") String role, @Param("keyword") String keyword, Pageable pageable);

    @Query("select u from User u where (u.enterpriseStatus = 'APPROVED' or (upper(coalesce(u.certType,'')) = 'ENTERPRISE' and u.certStatus = '已通过')) and (:keyword is null or u.username like concat('%', :keyword, '%') or u.nickname like concat('%', :keyword, '%') or u.phone like concat('%', :keyword, '%') or u.companyName like concat('%', :keyword, '%'))")
    Page<User> searchBossIdentityRecipients(@Param("keyword") String keyword, Pageable pageable);

    @Query("select u from User u where (u.role = 'USER' or u.enterpriseStatus = 'APPROVED' or (upper(coalesce(u.certType,'')) = 'ENTERPRISE' and u.certStatus = '已通过')) and (:keyword is null or u.username like concat('%', :keyword, '%') or u.nickname like concat('%', :keyword, '%') or u.phone like concat('%', :keyword, '%') or u.companyName like concat('%', :keyword, '%'))")
    Page<User> searchWorkerIdentityRecipients(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select u from User u
            where (u.role = 'USER'
              or upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED'
              or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过'))
              and (:keyword is null or u.username like concat('%', :keyword, '%') or u.nickname like concat('%', :keyword, '%') or u.phone like concat('%', :keyword, '%') or u.skills like concat('%', :keyword, '%'))
              and (:category is null or u.skills like concat('%', :category, '%'))
              and (:minYears is null or coalesce(u.workYears, 0) >= :minYears)
              and (:maxYears is null or coalesce(u.workYears, 0) <= :maxYears)
              and (:favoriteOnly = false or exists (select f.id from TalentFavorite f where f.workerId = u.id and f.bossId = :bossId))
            """)
    Page<User> searchTalents(@Param("keyword") String keyword,
                             @Param("category") String category,
                             @Param("minYears") Integer minYears,
                             @Param("maxYears") Integer maxYears,
                             @Param("favoriteOnly") boolean favoriteOnly,
                             @Param("bossId") Long bossId,
                             Pageable pageable);

    /**
     * 按老板业务身份/状态/企业认证状态/行业/关键词分页查询。
     *
     * 老板业务身份以企业认证通过为准，不能仅凭用户当前 role=BOSS 判断；
     * 同时兼容历史数据中 certType=ENTERPRISE、certStatus=已通过的记录。
     */
    @Query("""
            select u from User u
            where (upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED'
                   or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过'))
              and (:status is null or u.status = :status)
              and (:enterpriseStatus is null or u.enterpriseStatus = :enterpriseStatus)
              and (:industry is null or u.industry = :industry)
              and ((:keyword is null) or (u.companyCode like %:keyword%) or (u.companyName like %:keyword%) or (u.username like %:keyword%) or (u.nickname like %:keyword%) or (u.phone like %:keyword%))
            """)
    Page<User> searchBosses(@Param("role") String role,
                            @Param("status") String status,
                            @Param("enterpriseStatus") String enterpriseStatus,
                            @Param("industry") String industry,
                            @Param("keyword") String keyword,
                            Pageable pageable);

    /** 在指定老板集合内继续按账号状态、认证状态和关键词分页。 */
    @Query("""
            select u from User u
            where (upper(coalesce(u.enterpriseStatus, '')) = 'APPROVED'
                   or (upper(coalesce(u.certType, '')) = 'ENTERPRISE' and u.certStatus = '已通过'))
              and u.id in :ids
              and (:status is null or u.status = :status)
              and (:enterpriseStatus is null or u.enterpriseStatus = :enterpriseStatus)
              and ((:keyword is null) or (u.companyCode like %:keyword%) or (u.companyName like %:keyword%) or (u.username like %:keyword%) or (u.nickname like %:keyword%) or (u.phone like %:keyword%))
            """)
    Page<User> searchBossesByIds(@Param("ids") Collection<Long> ids,
                                 @Param("status") String status,
                                 @Param("enterpriseStatus") String enterpriseStatus,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);
}
