package com.kuaima.app.domain.user.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.kuaima.app.domain.user.entity.User;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_repository;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryTests {

    @Autowired
    private UserRepository repository;

    @Test
    void searchBossesRequiresApprovedEnterpriseIdentityInsteadOfRoleOnly() {
        repository.save(user("未认证但角色为老板", "BOSS", null, null, null));
        repository.save(user("新认证老板", "USER", "APPROVED", null, null));
        repository.save(user("历史认证老板", "USER", null, "ENTERPRISE", "已通过"));
        repository.save(user("待审核老板", "BOSS", "PENDING", null, null));
        repository.flush();

        Page<User> result = repository.searchBosses("BOSS", null, null, null, null, PageRequest.of(0, 20));

        assertEquals(2, result.getTotalElements());
        List<String> names = result.getContent().stream().map(User::getNickname).toList();
        assertEquals(Set.of("历史认证老板", "新认证老板"), Set.copyOf(names));
        assertEquals(2L, repository.countBossIdentities());

        Page<User> workers = repository.searchWorkers("USER", null, null, null, null, PageRequest.of(0, 20));
        assertEquals(2, workers.getTotalElements());
        assertEquals(Set.of("新认证老板", "历史认证老板"),
                Set.copyOf(workers.getContent().stream().map(User::getNickname).toList()));
        assertEquals(2, repository.findWorkerIdentityIds().size());
        assertEquals(2L, repository.countWorkerIdentities());
    }

    private User user(String nickname, String role, String enterpriseStatus, String certType, String certStatus) {
        User user = new User();
        user.setUsername(nickname);
        user.setPassword("test");
        user.setNickname(nickname);
        user.setRole(role);
        user.setEnterpriseStatus(enterpriseStatus);
        user.setCertType(certType);
        user.setCertStatus(certStatus);
        return user;
    }
}
