package com.kuaima.app.domain.recruit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.kuaima.app.domain.recruit.entity.DouyinAccount;

/** 老板抖音直播账号查询。 */
@Repository
public interface DouyinAccountRepository extends JpaRepository<DouyinAccount, Long> {

    List<DouyinAccount> findByBossIdOrderByIdDesc(Long bossId);

    boolean existsByBossIdAndName(Long bossId, String name);

    boolean existsByBossIdAndDouyinId(Long bossId, String douyinId);
}
