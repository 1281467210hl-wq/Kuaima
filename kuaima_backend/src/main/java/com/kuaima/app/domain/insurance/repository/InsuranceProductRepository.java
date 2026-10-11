package com.kuaima.app.domain.insurance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kuaima.app.domain.insurance.entity.InsuranceProduct;

public interface InsuranceProductRepository extends JpaRepository<InsuranceProduct, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("""
            select p from InsuranceProduct p
            where (:keyword is null or lower(coalesce(p.carrier, '')) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(p.code, '')) like lower(concat('%', :keyword, '%')))
              and (:enabled is null or p.enabled = :enabled)
            """)
    Page<InsuranceProduct> search(@Param("keyword") String keyword,
                                  @Param("enabled") Boolean enabled,
                                  Pageable pageable);
}
