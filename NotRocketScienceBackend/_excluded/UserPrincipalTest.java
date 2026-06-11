package com.justrocketscience.auth.security;

import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserPrincipal}.
 *
 * <p>No Spring context. No mocks needed — UserPrincipal has no dependencies.
 * Tests run in ~10ms.
 *
 * <p>The most important tests are the security invariant tests:
 * <ul>
 *   <li>{@code passwordHash_neverAppearsInToString} — prevents accidental log exposure</li>
 *   <li>{@code getPassword_returnsHash_forAuthenticationProviderUseOnly} — ensures the
 *       hash IS available when Spring Security needs it</li>
 *   <li>{@code inactiveUser_isDisabled_andLocked} — ensures deactivated accounts can't log in</li>
 * </ul>
 */
@DisplayName("UserPrincipal")
class UserPrincipalTest {

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    private static final UUID   TEST_ID            = UUID.randomUUID();
    private static final String TEST_EMAIL         = "user@example.com";
    private static final String TEST_USERNAME      = "testuser";
    private static final String TEST_PASSWORD_HASH = "$2a$12$exampleBCryptHashThatIsExactly60CharactersLongXXXXXXXXX";

    private User activeUser;
    private User inactiveUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(TEST_ID)
                .email(TEST_EMAIL)
                .username(TEST_USERNAME)
                .passwordHash(TEST_PASSWORD_HASH)
                .role(UserRole.USER)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@example.com")
                .username("inactiveuser")
                .passwordHash(TEST_PASSWORD_HASH)
                .role(UserRole.USER)
                .isActive(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        adminUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .username("adminuser")
                .passwordHash(TEST_PASSWORD_HASH)
                .role(UserRole.ADMIN)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Construction and field mapping
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("from() copies all required fields from the User entity")
        void from_copiesAllFields() {
            final UserPrincipal principal = UserPrincipal.from(activeUser);

            assertThat(principal.getUserId()).isEqualTo(TEST_ID);
            assertThat(principal.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(principal.getUsername()).isEqualTo(TEST_EMAIL); // username == email
            assertThat(principal.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("getUsername() returns email, not the username field")
        void getUsername_returnsEmail_notUsernameField() {
            // Spring Security's "username" means "login identifier".
            // In this app that is the email address, not the display username.
            final UserPrincipal principal = UserPrincipal.from(activeUser);

            assertThat(principal.getUsername())
                    .isEqualTo(TEST_EMAIL)
                    .isNotEqualTo(TEST_USERNAME);
        }
    }

    // -------------------------------------------------------------------------
    // Authority mapping
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Authority mapping")
    class AuthorityTests {

        @Test
        @DisplayName("USER role maps to exactly ROLE_USER authority")
        void userRole_mapsTo_ROLE_USER() {
            final UserPrincipal principal = UserPrincipal.from(activeUser);
            final Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();

            assertThat(authorities)
                    .hasSize(1)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("ADMIN role maps to exactly ROLE_ADMIN authority")
        void adminRole_mapsTo_ROLE_ADMIN() {
            final UserPrincipal principal = UserPrincipal.from(adminUser);
            final Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();

            assertThat(authorities)
                    .hasSize(1)
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ADMIN");
        }

        @Test
        @DisplayName("getAuthorities() returns immutable collection")
        void getAuthorities_returnsImmutableCollection() {
            final UserPrincipal principal = UserPrincipal.from(activeUser);

            assertThat(principal.getAuthorities())
                    .isUnmodifiable();
        }

        @Test
        @DisplayName("getRole() returns the domain UserRole enum, not an authority string")
        void getRole_returnsDomainEnum() {
            final UserPrincipal principal = UserPrincipal.from(activeUser);

            // getRole() is for application code that branches on domain semantics.
            // getAuthorities() is for Spring Security infrastructure.
            // Both should be available.
            assertThat(principal.getRole()).isEqualTo(UserRole.USER);
        }
    }

    // -------------------------------------------------------------------------
    // Active user — UserDetails contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Active user - UserDetails contract")
    class ActiveUserDetailsTests {

        @Test
        @DisplayName("Active user: isEnabled() = true")
        void activeUser_isEnabled() {
            assertThat(UserPrincipal.from(activeUser).isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Active user: isAccountNonLocked() = true")
        void activeUser_isAccountNonLocked() {
            assertThat(UserPrincipal.from(activeUser).isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("Active user: isAccountNonExpired() = true (not modelled)")
        void activeUser_isAccountNonExpired() {
            assertThat(UserPrincipal.from(activeUser).isAccountNonExpired()).isTrue();
        }

        @Test
        @DisplayName("Active user: isCredentialsNonExpired() = true (not modelled)")
        void activeUser_isCredentialsNonExpired() {
            assertThat(UserPrincipal.from(activeUser).isCredentialsNonExpired()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Inactive user — critical security behaviour
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Inactive user - account status")
    class InactiveUserDetailsTests {

        @Test
        @DisplayName("Inactive user: isEnabled() = false → DaoAuthenticationProvider throws DisabledException")
        void inactiveUser_isDisabled() {
            assertThat(UserPrincipal.from(inactiveUser).isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Inactive user: isAccountNonLocked() = false → DaoAuthenticationProvider throws LockedException")
        void inactiveUser_isLocked() {
            assertThat(UserPrincipal.from(inactiveUser).isAccountNonLocked()).isFalse();
        }

        @Test
        @DisplayName("Inactive user: loadUserByUsername() still returns a principal (does not throw)")
        void inactiveUser_principalCanBeConstructed() {
            // The status check must happen via the UserDetails flags,
            // NOT by throwing in loadUserByUsername(). If we threw there,
            // the authentication provider would never call BCrypt, and an
            // attacker could use timing differences to enumerate disabled accounts.
            assertThat(UserPrincipal.from(inactiveUser)).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // Security invariants — the most important tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Security invariants")
    class SecurityInvariantTests {

        @Test
        @DisplayName("toString() must NEVER expose passwordHash")
        void passwordHash_neverAppearsInToString() {
            final UserPrincipal principal = UserPrincipal.from(activeUser);
            final String repr = principal.toString();

            assertThat(repr)
                    .doesNotContain(TEST_PASSWORD_HASH)
                    .doesNotContain("password")
                    .doesNotContain("hash")
                    .doesNotContainIgnoringCase("bcrypt");
        }

        @Test
        @DisplayName("getPassword() returns the hash — required by DaoAuthenticationProvider")
        void getPassword_returnsHash_forAuthenticationProviderUseOnly() {
            // getPassword() MUST return the hash so DaoAuthenticationProvider can call
            // BCryptPasswordEncoder.matches(raw, hash). This is intentional and correct.
            // The test name makes clear this is the ONLY legitimate caller.
            final UserPrincipal principal = UserPrincipal.from(activeUser);

            assertThat(principal.getPassword()).isEqualTo(TEST_PASSWORD_HASH);
        }

        @Test
        @DisplayName("UserPrincipal does not expose a getPasswordHash() accessor")
        void noGetPasswordHashMethod() throws NoSuchMethodException {
            // Regression test: ensure no one accidentally adds a getPasswordHash()
            // method to UserPrincipal. The password hash must only be accessible
            // through the UserDetails.getPassword() contract.
            final var methods = UserPrincipal.class.getMethods();
            final boolean hasPasswordHashGetter = java.util.Arrays.stream(methods)
                    .anyMatch(m -> m.getName().equalsIgnoreCase("getPasswordHash")
                                   || m.getName().equalsIgnoreCase("getpasswordhash"));

            assertThat(hasPasswordHashGetter)
                    .as("UserPrincipal must not expose a getPasswordHash() method")
                    .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Equality and identity
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Equality and identity")
    class EqualityTests {

        @Test
        @DisplayName("Two principals for the same userId are equal regardless of other fields")
        void twoInstancesForSameUserId_areEqual() {
            final UserPrincipal a = UserPrincipal.from(activeUser);

            // Same userId, but construct a second User with a different email
            // (simulating an email change between requests — should still be the same principal)
            final User sameUserDifferentEmail = User.builder()
                    .id(TEST_ID)
                    .email("newemail@example.com")
                    .username(TEST_USERNAME)
                    .passwordHash(TEST_PASSWORD_HASH)
                    .role(UserRole.USER)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            final UserPrincipal b = UserPrincipal.from(sameUserDifferentEmail);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Two principals for different userIds are not equal")
        void twoInstancesForDifferentUserIds_areNotEqual() {
            final UserPrincipal a = UserPrincipal.from(activeUser);
            final UserPrincipal b = UserPrincipal.from(adminUser);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("Principal is equal to itself (reflexive)")
        void reflexiveEquality() {
            final UserPrincipal principal = UserPrincipal.from(activeUser);
            assertThat(principal).isEqualTo(principal);
        }

        @Test
        @DisplayName("Principal is not equal to null")
        void notEqualToNull() {
            assertThat(UserPrincipal.from(activeUser)).isNotEqualTo(null);
        }
    }
}
