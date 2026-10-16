package com.kuaima.app.admin.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import com.kuaima.app.admin.entity.AdminLog;
import com.kuaima.app.admin.entity.AdminUser;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminlogs;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminLogRepositoryTests {

    @Autowired
    private AdminLogRepository repository;

    @Autowired
    private AdminUserRepository adminUsers;

    @Test
    void searchShouldFilterByOperatorNameTypeAndFullDateRange() {
        AdminUser admin = new AdminUser();
        admin.setUsername("admin");
        admin.setPassword("password");
        admin.setName("张管理员");
        admin.setRole("SUPER_ADMIN");
        admin.setStatus("启用");
        admin = adminUsers.saveAndFlush(admin);
        repository.save(log(admin.getId(), "登录", "后台登录", LocalDateTime.of(2026, 10, 13, 23, 59)));
        repository.save(log(admin.getId(), "编辑", "系统设置", LocalDateTime.of(2026, 10, 14, 0, 0)));
        repository.flush();

        var result = repository.search(null, "登录", "张管理员",
                LocalDateTime.of(2026, 10, 13, 0, 0),
                LocalDateTime.of(2026, 10, 14, 0, 0),
                PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("登录", result.getContent().get(0).getType());
    }

    @Test
    void searchEditTypeShouldIncludeLegacyWriteTypes() {
        repository.save(log(null, "审核", "招工审核", LocalDateTime.of(2026, 10, 14, 10, 0)));
        repository.save(log(null, "删除", "公告管理", LocalDateTime.of(2026, 10, 14, 9, 0)));
        repository.flush();

        var result = repository.search(null, "编辑", null, null, null, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("审核", result.getContent().get(0).getType());
    }

    private AdminLog log(Long operatorId, String type, String target, LocalDateTime createTime) {
        AdminLog log = new AdminLog();
        log.setOperator("admin");
        log.setOperatorId(operatorId);
        log.setType(type);
        log.setTarget(target);
        log.setCreateTime(createTime);
        return log;
    }
}
