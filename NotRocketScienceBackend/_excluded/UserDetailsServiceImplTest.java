package com.justrocketscience.auth.service;

import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.entity.UserRole;
import com.justrocketscience.auth.repository.UserRepository;
import com.justrocketscience.auth.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserDetailsServiceImpl}.
 *
 * <p>No Spring context. Uses Mockito to stub {@link UserRepository}.
 * Tests run in ~30ms.
 *
 * <p>The most important test in this class is
 * {@code inactiveUser_returnsUserDetails_doesNotThrow} — it verifies the timing-safe
 * inactive user handling described in the implementation's Javadoc.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl")
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(UUID.randomUUID())
                .email("active@example.com")
                .username("activeuser")
                .passwordHash("$2a$12$someHashValue")
                .role(UserRole.USER)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@example.com")
                .username("inactiveuser")
                .passwordHash("$2a$12$someHashValue")
                .role(UserRole.USER)
                .isActive(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Active user - happy path")
    class ActiveUserTests {

        @Test
        @DisplayName("Returns a UserPrincipal for a known active user")
        void activeUser_returnsUserPrincipal() {
            when(userRepository.findByEmail("active@example.com"))
                    .thenReturn(Optional.of(activeUser));

            final UserDetails result = userDetailsService.loadUserByUsername("active@example.com");

            assertThat(result).isInstanceOf(UserPrincipal.class);
        }

        @Test
        @DisplayName("Returned principal has correct email as username")
        void activeUser_principalHasCorrectEmail() {
            when(userRepository.findByEmail("active@example.com"))
                    .thenReturn(Optional.of(activeUser));

            final UserDetails result = userDetailsService.loadUserByUsername("active@example.com");

            assertThat(result.getUsername()).isEqualTo("active@example.com");
        }

        @Test
        @DisplayName("Returned principal has correct authority for USER role")
        void activeUser_principalHasCorrectAuthority() {
            when(userRepository.findByEmail("active@example.com"))
                    .thenReturn(Optional.of(activeUser));

            final UserDetails result = userDetailsService.loadUserByUsername("active@example.com");

            assertThat(result.getAuthorities())
                    .hasSize(1)
                    .extracting(a -> a.getAuthority())
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("Calls userRepository.findByEmail exactly once with the provided email")
        void activeUser_callsRepositoryOnce() {
            when(userRepository.findByEmail("active@example.com"))
                    .thenReturn(Optional.of(activeUser));

            userDetailsService.loadUserByUsername("active@example.com");

            verify(userRepository, times(1)).findByEmail("active@example.com");
            verifyNoMoreInteractions(userRepository);
        }

        @Test
        @DisplayName("Active user: isEnabled() = true")
        void activeUser_isEnabled() {
            when(userRepository.findByEmail("active@example.com"))
                    .thenReturn(Optional.of(activeUser));

            final UserDetails result = userDetailsService.loadUserByUsername("active@example.com");

            assertThat(result.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Active user: isAccountNonLocked() = true")
        void activeUser_isAccountNonLocked() {
            when(userRepository.findByEmail("active@example.com"))
                    .thenReturn(Optional.of(activeUser));

            final UserDetails result = userDetailsService.loadUserByUsername("active@example.com");

            assertThat(result.isAccountNonLocked()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // User not found
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("User not found")
    class UserNotFoundTests {

        @Test
        @DisplayName("Throws UsernameNotFoundException when email is not in the database")
        void unknownEmail_throwsUsernameNotFoundException() {
            when(userRepository.findByEmail("unknown@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@example.com"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("Exception message is generic — does not reveal whether the account exists")
        void exception_hasGenericMessage_preventsUserEnumeration() {
            when(userRepository.findByEmail("unknown@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@example.com"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageNotContaining("unknown@example.com")  // no email in message
                    .hasMessageNotContaining("not found")             // no "not found" confirmation
                    .hasMessageNotContaining("does not exist");       // no "does not exist" confirmation
        }

        @Test
        @DisplayName("Repository is still called once even on failure")
        void unknownEmail_repositoryCalledOnce() {
            when(userRepository.findByEmail(anyString()))
                    .thenReturn(Optional.empty());

            try {
                userDetailsService.loadUserByUsername("unknown@example.com");
            } catch (UsernameNotFoundException ignored) {}

            verify(userRepository, times(1)).findByEmail("unknown@example.com");
        }
    }

    // -------------------------------------------------------------------------
    // Inactive user — critical timing-safe behaviour
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Inactive user - timing-safe status check")
    class InactiveUserTests {

        /**
         * This is the most important test in the class.
         *
         * <p>An inactive user must NOT cause {@code loadUserByUsername()} to throw.
         * Throwing here would shortcut the BCrypt verification step entirely. An attacker
         * observing response times could then determine whether an email belongs to a
         * disabled account (fast response, no BCrypt) vs. a non-existent account (also
         * fast, also no BCrypt) vs. a wrong-password attempt (slow, BCrypt ran).
         *
         * <p>By returning a UserPrincipal with {@code isEnabled() = false}, we ensure
         * {@code DaoAuthenticationProvider} always runs BCrypt before evaluating account
         * status — constant-time regardless of account state.
         */
        @Test
        @DisplayName("CRITICAL: Inactive user - loadUserByUsername() returns UserDetails, does NOT throw")
        void inactiveUser_returnsUserDetails_doesNotThrow() {
            when(userRepository.findByEmail("inactive@example.com"))
                    .thenReturn(Optional.of(inactiveUser));

            // Must NOT throw UsernameNotFoundException or any other exception.
            // The disabled check happens in DaoAuthenticationProvider, not here.
            final UserDetails result = userDetailsService.loadUserByUsername("inactive@example.com");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Inactive user: isEnabled() = false → disables account in DaoAuthenticationProvider")
        void inactiveUser_principalIsDisabled() {
            when(userRepository.findByEmail("inactive@example.com"))
                    .thenReturn(Optional.of(inactiveUser));

            final UserDetails result = userDetailsService.loadUserByUsername("inactive@example.com");

            assertThat(result.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Inactive user: isAccountNonLocked() = false → locks account in DaoAuthenticationProvider")
        void inactiveUser_principalIsLocked() {
            when(userRepository.findByEmail("inactive@example.com"))
                    .thenReturn(Optional.of(inactiveUser));

            final UserDetails result = userDetailsService.loadUserByUsername("inactive@example.com");

            assertThat(result.isAccountNonLocked()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Admin user
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Admin user")
    class AdminUserTests {

        @Test
        @DisplayName("Admin user has ROLE_ADMIN authority")
        void adminUser_hasRoleAdmin() {
            final User adminUser = User.builder()
                    .id(UUID.randomUUID())
                    .email("admin@example.com")
                    .username("admin")
                    .passwordHash("$2a$12$someAdminHash")
                    .role(UserRole.ADMIN)
                    .isActive(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(userRepository.findByEmail("admin@example.com"))
                    .thenReturn(Optional.of(adminUser));

            final UserDetails result = userDetailsService.loadUserByUsername("admin@example.com");

            assertThat(result.getAuthorities())
                    .extracting(a -> a.getAuthority())
                    .containsExactly("ROLE_ADMIN");
        }
    }

    // -------------------------------------------------------------------------
    // Repository interaction contract
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Repository interaction")
    class RepositoryInteractionTests {

        @Test
        @DisplayName("Only findByEmail is called — no other repository methods touched")
        void onlyFindByEmail_isInvoked() {
            when(userRepository.findByEmail("active@example.com"))
                    .thenReturn(Optional.of(activeUser));

            userDetailsService.loadUserByUsername("active@example.com");

            verify(userRepository).findByEmail("active@example.com");
            verifyNoMoreInteractions(userRepository);
        }

        @Test
        @DisplayName("Email is passed to repository exactly as received — no transformation")
        void emailPassedToRepository_unchanged() {
            // Verify the service does not lowercase, trim, or transform the email.
            // Normalisation should happen at the DTO validation layer, not here.
            when(userRepository.findByEmail("User@Example.COM"))
                    .thenReturn(Optional.empty());

            try {
                userDetailsService.loadUserByUsername("User@Example.COM");
            } catch (UsernameNotFoundException ignored) {}

            verify(userRepository).findByEmail("User@Example.COM");
        }
    }
}
