package com.kuaima.app.domain.recruit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kuaima.app.domain.recruit.entity.LiveShowcase;

/** admin 智能招聘-直播橱窗查询。 */
@Repository
public interface LiveShowcaseRepository extends JpaRepository<LiveShowcase, Long> {

    long countByStatus(String status);

    @Query("""
            select s from LiveShowcase s
            where (:status is null or s.status = :status)
              and (:bossId is null or s.bossId = :bossId)
              and (:keyword is null
                   or s.title like concat('%', :keyword, '%')
                   or s.anchorName like concat('%', :keyword, '%')
                   or s.douyinAccount like concat('%', :keyword, '%')
                   or s.bossName like concat('%', :keyword, '%'))
            order by s.id desc
            """)
    Page<LiveShowcase> search(@Param("status") String status,
                              @Param("bossId") Long bossId,
                              @Param("keyword") String keyword,
                              Pageable pageable);
}
