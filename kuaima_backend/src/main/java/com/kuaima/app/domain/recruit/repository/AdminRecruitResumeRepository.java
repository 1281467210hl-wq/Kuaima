package com.kuaima.app.domain.recruit.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.kuaima.app.domain.resume.entity.Resume;

/**
 * admin 智能招聘-简历库（平台全局视角）查询。
 * 与 boss 端 ResumeRepository 映射同一实体，按任务约定独立新建，避免改动既有文件。
 */
@Repository
public interface AdminRecruitResumeRepository extends JpaRepository<Resume, Long> {

    @Query("""
            select r from Resume r
            where (:bossId is null or r.bossId = :bossId)
              and (:tab is null or :tab = 'all'
                   or (:tab = 'fav' and r.favorite = true)
                   or (:tab = 'pending' and r.status = 'PENDING')
                   or (:tab = 'viewed' and r.status = 'VIEWED')
                   or (:tab = 'sent' and r.status = 'SENT')
                   or (:tab = 'new' and r.status = 'NEW')
                   or (:tab = 'import' and r.source = 'IMPORT')
                   or (:tab = 'apply' and r.source = 'APPLY'))
              and (:keyword is null
                   or r.name like concat('%', :keyword, '%')
                   or r.position like concat('%', :keyword, '%')
                   or r.jobCategory like concat('%', :keyword, '%')
                   or r.experience like concat('%', :keyword, '%')
                   or r.phone like concat('%', :keyword, '%'))
              and (:status is null or r.status = :status)
            order by r.id desc
            """)
    Page<Resume> searchGlobal(@Param("bossId") Long bossId,
                              @Param("tab") String tab,
                              @Param("keyword") String keyword,
                              @Param("status") String status,
                              Pageable pageable);

    @Query("""
            select r.id from Resume r
            where r.name like concat('%', :keyword, '%')
               or r.position like concat('%', :keyword, '%')
            """)
    List<Long> findIdsMatchingKeyword(@Param("keyword") String keyword);

    long countBySource(String source);

    long countByFavoriteTrue();
}
