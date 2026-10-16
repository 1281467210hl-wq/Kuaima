package com.kuaima.app.admin.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.admin.entity.Agreement;

public interface AgreementRepository extends JpaRepository<Agreement, Long> {

    @Query("""
            select a from Agreement a
            where (:type is null or a.type = :type)
              and (:keyword is null or lower(a.name) like lower(concat('%', :keyword, '%')))
            """)
    Page<Agreement> search(@Param("type") String type,
                           @Param("keyword") String keyword,
                           Pageable pageable);
}
