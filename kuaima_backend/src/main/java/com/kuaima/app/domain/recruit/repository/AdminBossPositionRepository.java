package com.kuaima.app.domain.recruit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.kuaima.app.domain.position.entity.BossPosition;

/**
 * admin 智能招聘-岗位全局查询（与 BossPositionRepository 映射同一实体，独立新建避免改动既有文件）。
 * 提供 Specification 能力，支持按归属老板 createBy 过滤。
 */
@Repository
public interface AdminBossPositionRepository extends JpaRepository<BossPosition, Long>,
        JpaSpecificationExecutor<BossPosition> {
}
