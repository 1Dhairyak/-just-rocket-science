package com.justrocketscience.auth.service;

import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.repository.UserRepository;
import com.justrocketscience.auth.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security's bridge between the database and the authentication infrastructure.
 *
 * <p>{@link UserDetailsService} has exactly one method: {@link #loadUserByUsername(String)}.
 * Spring Security calls it through {@code DaoAuthenticationProvider} during the login
 * flow — nowhere else. The full sequence is:
 *
 * <pre>
 *   AuthService.login(LoginRequest)
 *     → AuthenticationManager.authenticate(UsernamePasswordAuthenticationToken)
 *       → DaoAuthenticationProvider.retrieveUser(email, token)
 *         → UserDetailsServiceImpl.loadUserByUsername(email)   ← this class
 *           → BCryptPasswordEncoder.matches(rawPassword, storedHash)
 *             → Authentication object stored in SecurityContext (if successful)
 * </pre>
 *
 * <p><strong>Why this class is separate from {@link com.justrocketscience.auth.service.AuthService}:</strong>
 * {@link UserDetailsService} is a Spring Security interface. Implementing it on {@code AuthService}
 * would couple the domain service to the security infrastructure and make the class harder to
 * test in isolation. Keeping them separate means {@code AuthService} has no Spring Security
 * imports at all — it deals only in domain objects.
 *
 * <p><strong>What this class intentionally does NOT do:</strong>
 * <ul>
 *   <li>It does not check {@code isActive} by throwing an exception. That check is encoded
 *       into {@link UserPrincipal#isEnabled()} and {@link UserPrincipal#isAccountNonLocked()},
 *       which {@code DaoAuthenticationProvider} evaluates after this method returns. This is
 *       the correct separation: this class answers "does this user exist and here are their
 *       details", not "should this user be allowed to authenticate right now".</li>
 *   <li>It does not verify the password. That is {@code DaoAuthenticationProvider}'s job.</li>
 *   <li>It does not create a SecurityContext entry. That happens after successful
 *       authentication, not during user loading.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by their email address for Spring Security's authentication process.
     *
     * <p>In this application, the "username" in Spring Security's sense is the email address.
     * The login form submits email, {@link AuthService} creates a
     * {@code UsernamePasswordAuthenticationToken} with email as the principal, and
     * {@code DaoAuthenticationProvider} passes that email here.
     *
     * <p><strong>What happens on a missing user:</strong>
     * {@link UsernameNotFoundException} is thrown — which is the correct contract. Critically,
     * {@code DaoAuthenticationProvider} catches this and converts it to
     * {@code BadCredentialsException} before propagating, so the caller cannot distinguish
     * "user does not exist" from "wrong password". This prevents user enumeration attacks:
     * both failures return the same exception type with the same message to the controller.
     *
     * <p><strong>What happens on an inactive user:</strong>
     * The method returns normally — it does NOT throw. The {@link UserPrincipal} returned
     * has {@code isEnabled() = false} and {@code isAccountNonLocked() = false}.
     * {@code DaoAuthenticationProvider} checks these flags after this method returns and
     * throws {@code DisabledException} or {@code LockedException} accordingly. The result
     * from the controller's perspective is still a 401, but the internal exception type
     * is specific — useful for audit logging in a future improvement.
     *
     * <p><strong>Security note on the log statement:</strong>
     * The DEBUG log at query time logs only the email — never the password, never the hash.
     * It is gated on DEBUG level so it never fires in production unless explicitly enabled.
     * The WARN on not-found logs the email so ops can identify misconfigured clients or
     * automated probing, but only at WARN level, not ERROR (it is not an application fault).
     *
     * @param email the email address submitted by the user; used as the Spring Security
     *              "username" throughout this application
     * @return a fully populated {@link UserPrincipal} wrapping the found {@link User}
     * @throws UsernameNotFoundException if no active or inactive user with this email
     *                                   exists in the database
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {

        log.debug("Loading user by email for authentication: {}", email);

        final User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    // WARN not ERROR — a missing user is not an application fault.
                    // It is normal traffic from a user who mis-typed their email,
                    // or from an attacker probing for valid accounts.
                    log.warn("Authentication attempt for unknown email: {}", email);

                    // The message is intentionally generic. DaoAuthenticationProvider
                    // re-wraps this as BadCredentialsException anyway, but defensive
                    // messaging is a good habit.
                    return new UsernameNotFoundException(
                            "No account found for the provided credentials"
                    );
                });

        log.debug("User found for authentication: userId={}, isActive={}", user.getId(), user.getIsActive());

        // Do NOT filter inactive users here. Return the UserPrincipal and let
        // DaoAuthenticationProvider evaluate isEnabled() and isAccountNonLocked().
        // This preserves correct Spring Security exception semantics:
        //   Active user, wrong password  → BadCredentialsException
        //   Inactive user, wrong password → BadCredentialsException  (not disabled — can't reach flag check)
        //   Inactive user, right password → DisabledException + LockedException
        //
        // If we threw here for inactive users, an attacker could distinguish
        // "account exists but is disabled" from "account does not exist" by
        // observing different response timing (one hits BCrypt, one doesn't).
        return UserPrincipal.from(user);
    }
}
