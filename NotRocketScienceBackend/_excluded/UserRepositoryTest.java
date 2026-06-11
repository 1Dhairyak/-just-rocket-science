package com.justrocketscience.auth.repository;

import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice test for {@link UserRepository}.
 *
 * <p>{@code @DataJpaTest} loads only:
 * <ul>
 *   <li>JPA infrastructure (EntityManager, DataSource, JPA repositories)</li>
 *   <li>Flyway migrations (runs V1, V2 against real Postgres container)</li>
 * </ul>
 * No web layer, no service beans, no Redis — fast and focused.
 *
 * <p>Uses a real PostgreSQL container via Testcontainers so the
 * PostgreSQL ENUM type and all constraints are enforced exactly as in production.
 * H2 in-memory is intentionally avoided — it doesn't support {@code CREATE TYPE}.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jrs_auth_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestEntityManager em;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        activeUser = userRepository.save(User.builder()
                .username("john_doe")
                .email("john@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .role(UserRole.USER)
                .isActive(true)
                .build());

        inactiveUser = userRepository.save(User.builder()
                .username("jane_doe")
                .email("jane@example.com")
                .passwordHash("$2a$10$hashedpassword2")
                .role(UserRole.USER)
                .isActive(false)
                .build());

        em.flush();
        em.clear(); // force all subsequent finds to hit DB, not L1 cache
    }

    // =========================================================================
    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("returns user when email exists")
        void returnsUser_whenEmailExists() {
            Optional<User> result = userRepository.findByEmail("john@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("john_doe");
        }

        @Test
        @DisplayName("returns empty when email not found")
        void returnsEmpty_whenEmailNotFound() {
            Optional<User> result = userRepository.findByEmail("nobody@example.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("email lookup is case-sensitive (DB collation)")
        void emailLookupIsCaseSensitive() {
            // PostgreSQL text columns are case-sensitive by default
            Optional<User> result = userRepository.findByEmail("JOHN@EXAMPLE.COM");

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("findActiveByEmail")
    class FindActiveByEmail {

        @Test
        @DisplayName("returns active user")
        void returnsActiveUser() {
            Optional<User> result = userRepository.findActiveByEmail("john@example.com");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("returns empty for inactive user — deactivated accounts cannot log in")
        void returnsEmpty_forInactiveUser() {
            Optional<User> result = userRepository.findActiveByEmail("jane@example.com");

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("existsByEmail and existsByUsername")
    class ExistenceChecks {

        @Test
        @DisplayName("existsByEmail returns true for existing email")
        void existsByEmail_returnsTrue() {
            assertThat(userRepository.existsByEmail("john@example.com")).isTrue();
        }

        @Test
        @DisplayName("existsByEmail returns false for unknown email")
        void existsByEmail_returnsFalse() {
            assertThat(userRepository.existsByEmail("unknown@example.com")).isFalse();
        }

        @Test
        @DisplayName("existsByUsername returns true for existing username")
        void existsByUsername_returnsTrue() {
            assertThat(userRepository.existsByUsername("john_doe")).isTrue();
        }

        @Test
        @DisplayName("existsByEmailOrUsername — detects conflict on email only")
        void existsByEmailOrUsername_emailConflict() {
            assertThat(userRepository.existsByEmailOrUsername("john@example.com", "new_user")).isTrue();
        }

        @Test
        @DisplayName("existsByEmailOrUsername — detects conflict on username only")
        void existsByEmailOrUsername_usernameConflict() {
            assertThat(userRepository.existsByEmailOrUsername("new@example.com", "john_doe")).isTrue();
        }

        @Test
        @DisplayName("existsByEmailOrUsername — returns false when both are unique")
        void existsByEmailOrUsername_noConflict() {
            assertThat(userRepository.existsByEmailOrUsername("new@example.com", "new_user")).isFalse();
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Schema constraints")
    class SchemaConstraints {

        @Test
        @DisplayName("duplicate email throws — DB enforces uq_users_email")
        void duplicateEmail_throwsConstraintViolation() {
            User duplicate = User.builder()
                    .username("another_user")
                    .email("john@example.com") // same email
                    .passwordHash("$2a$10$hash")
                    .build();

            assertThatThrownBy(() -> {
                userRepository.saveAndFlush(duplicate);
            }).hasMessageContaining("constraint");
        }

        @Test
        @DisplayName("duplicate username throws — DB enforces uq_users_username")
        void duplicateUsername_throwsConstraintViolation() {
            User duplicate = User.builder()
                    .username("john_doe") // same username
                    .email("unique@example.com")
                    .passwordHash("$2a$10$hash")
                    .build();

            assertThatThrownBy(() -> {
                userRepository.saveAndFlush(duplicate);
            }).hasMessageContaining("constraint");
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("findAllActive (paginated)")
    class FindAllActive {

        @Test
        @DisplayName("returns only active users")
        void returnsOnlyActiveUsers() {
            Page<User> page = userRepository.findAllActive(PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getEmail()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("pagination works correctly")
        void paginationWorks() {
            // add a second active user
            userRepository.save(User.builder()
                    .username("third_user")
                    .email("third@example.com")
                    .passwordHash("$2a$10$hash3")
                    .isActive(true)
                    .build());
            em.flush(); em.clear();

            Page<User> page = userRepository.findAllActive(PageRequest.of(0, 1));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("countActiveByRole")
    class CountActiveByRole {

        @Test
        @DisplayName("counts only active users with matching role")
        void countsCorrectly() {
            // 1 active USER, 1 inactive USER, 0 ADMIN
            assertThat(userRepository.countActiveByRole(UserRole.USER)).isEqualTo(1);
            assertThat(userRepository.countActiveByRole(UserRole.ADMIN)).isEqualTo(0);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("@Modifying — deactivateById")
    class DeactivateById {

        @Test
        @DisplayName("sets isActive=false and returns 1")
        void deactivatesUser() {
            int updated = userRepository.deactivateById(activeUser.getId(), Instant.now());

            assertThat(updated).isEqualTo(1);

            em.clear();
            User reloaded = userRepository.findById(activeUser.getId()).orElseThrow();
            assertThat(reloaded.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("returns 0 for unknown ID — does not throw")
        void returnsZero_forUnknownId() {
            int updated = userRepository.deactivateById(UUID.randomUUID(), Instant.now());

            assertThat(updated).isEqualTo(0);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("@Modifying — updatePasswordHash")
    class UpdatePasswordHash {

        @Test
        @DisplayName("updates password for active user")
        void updatesPassword_forActiveUser() {
            String newHash = "$2a$10$newHash";

            int updated = userRepository.updatePasswordHash(activeUser.getId(), newHash, Instant.now());

            assertThat(updated).isEqualTo(1);
            em.clear();
            assertThat(userRepository.findById(activeUser.getId()).orElseThrow().getPasswordHash())
                    .isEqualTo(newHash);
        }

        @Test
        @DisplayName("does not update password for inactive user")
        void doesNotUpdate_forInactiveUser() {
            int updated = userRepository.updatePasswordHash(
                    inactiveUser.getId(), "$2a$10$newHash", Instant.now());

            assertThat(updated).isEqualTo(0);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("findAllActiveByIds — batch fetch")
    class FindAllActiveByIds {

        @Test
        @DisplayName("returns only active users from the given IDs")
        void returnsOnlyActiveUsersFromIds() {
            List<UUID> ids = List.of(activeUser.getId(), inactiveUser.getId());

            List<User> result = userRepository.findAllActiveByIds(ids);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(activeUser.getId());
        }

        @Test
        @DisplayName("returns empty list when no IDs match")
        void returnsEmpty_whenNoMatch() {
            List<User> result = userRepository.findAllActiveByIds(
                    List.of(UUID.randomUUID(), UUID.randomUUID()));

            assertThat(result).isEmpty();
        }
    }
}
