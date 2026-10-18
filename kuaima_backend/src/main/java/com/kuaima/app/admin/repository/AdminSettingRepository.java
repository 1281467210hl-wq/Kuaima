package com.kuaima.app.admin.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kuaima.app.admin.entity.AdminSetting;

public interface AdminSettingRepository extends JpaRepository<AdminSetting, String> {
    List<AdminSetting> findByCategory(String category);

    /** 历史数据：通用保存接口曾不写 category，按 key 前缀（如 platform.）兜底查回。 */
    List<AdminSetting> findByCategoryIsNullAndSettingKeyStartingWith(String keyPrefix);
}
