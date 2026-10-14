package com.kuaima.app.domain.recruit.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kuaima.app.domain.recruit.entity.PositionApplication;

/** admin 智能招聘-候选人（岗位投递视角）查询。 */
@Repository
public interface PositionApplicationRepository extends JpaRepository<PositionApplication, Long> {

    long countByStatus(String status);

    long countByPositionId(Long positionId);

    List<PositionApplication> findByPositionIdOrderByIdDesc(Long positionId);

    @Query("""
            select a from PositionApplication a
            where (:status is null or a.status = :status)
              and (:bossId is null or a.bossId = :bossId)
              and (:positionId is null or a.positionId = :positionId)
              and (:resumeIds is null or a.resumeId in :resumeIds)
              and (:fromDate is null or a.date >= :fromDate)
              and (:toDate is null or a.date <= :toDate)
            order by a.id desc
            """)
    Page<PositionApplication> search(@Param("status") String status,
                                     @Param("bossId") Long bossId,
                                     @Param("positionId") Long positionId,
                                     @Param("resumeIds") List<Long> resumeIds,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate,
                                     Pageable pageable);
}
