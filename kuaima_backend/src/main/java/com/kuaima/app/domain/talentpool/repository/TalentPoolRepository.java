package com.kuaima.app.domain.talentpool.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kuaima.app.domain.talentpool.entity.TalentPool;

import java.util.Optional;

@Repository
public interface TalentPoolRepository extends JpaRepository<TalentPool, Long> {

    Optional<TalentPool> findByBossIdAndWorkerId(Long bossId, Long workerId);

    @Query("SELECT t FROM TalentPool t WHERE t.bossId = :bossId AND " +
           "(:keyword IS NULL OR t.name LIKE %:keyword% OR t.skills LIKE %:keyword% OR t.category LIKE %:keyword%) AND " +
           "(:type IS NULL OR t.type = :type) AND " +
           "(:favorite IS NULL OR t.favorite = :favorite) " +
           "ORDER BY t.favorite DESC, t.id DESC")
    Page<TalentPool> searchByBoss(@Param("bossId") Long bossId,
                                   @Param("keyword") String keyword,
                                   @Param("type") String type,
                                   @Param("favorite") Boolean favorite,
                                   Pageable pageable);

    List<TalentPool> findByBossIdOrderByIdDesc(Long bossId);
}
