package com.jaconis.finance_api.repository;

import com.jaconis.finance_api.config.JpaAuditingConfig;
import com.jaconis.finance_api.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindByEmail(){
        User user = User.builder()
                .email("matheusjaconis@gmail.com")
                .passwordHash("123456")
                .build();

         userRepository.save(user);


        assertThat(userRepository.findByEmail("matheusjaconis@gmail.com"))
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getEmail()).isEqualTo("matheusjaconis@gmail.com").isNotNull();
                    assertThat(found.getPasswordHash()).isEqualTo("123456").isNotNull();
                    assertThat(found.getCreatedAt()).isNotNull();
                    assertThat(found.getUpdatedAt()).isNotNull();
                });
    }
    @Test
    void findByEmail_returnsEmptyWhenNotFound() {
        assertThat(userRepository.findByEmail("naoexiste@example.com")).isEmpty();
    }
}
