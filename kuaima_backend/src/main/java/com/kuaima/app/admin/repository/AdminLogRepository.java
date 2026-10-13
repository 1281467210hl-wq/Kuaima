package com.kuaima.app.admin.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.admin.entity.AdminLog;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    /** 按操作类型过滤 + 分页 */
    Page<AdminLog> findByType(String type, Pageable pageable);

    /** 按操作人 ID 过滤 + 分页 */
    Page<AdminLog> findByOperatorId(Long operatorId, Pageable pageable);

    /** 按操作人 ID + 操作类型过滤 + 分页 */
    Page<AdminLog> findByOperatorIdAndType(Long operatorId, String type, Pageable pageable);

    /** 后台日志全局筛选：时间、类型、操作人姓名/账号、操作对象。 */
    @Query("""
            select l from AdminLog l
            left join AdminUser a on a.id = l.operatorId
            where (:operatorId is null or l.operatorId = :operatorId)
              and (:type is null or l.type = :type)
              and (:startTime is null or l.createTime >= :startTime)
              and (:endTime is null or l.createTime < :endTime)
              and (:keyword is null
                   or lower(coalesce(a.name, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(a.username, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.operator, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(l.target, '')) like lower(concat('%', :keyword, '%')))
            order by l.id desc
            """)
    Page<AdminLog> search(@Param("operatorId") Long operatorId,
                          @Param("type") String type,
                          @Param("keyword") String keyword,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          Pageable pageable);
}
