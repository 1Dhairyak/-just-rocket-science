package com.justrocketscience.auth.security;

import com.justrocketscience.auth.entity.User;
import com.justrocketscience.auth.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security's view of an authenticated user.
 *
 * <p>This class is the adapter between our domain model ({@link User}) and the contract
 * Spring Security requires ({@link UserDetails}). It exists for three reasons:
 *
 * <ol>
 *   <li><strong>Isolation</strong> — The {@link User} entity is a JPA-managed object with a
 *       lifecycle tied to a persistence context. Passing it directly into the SecurityContext
 *       means a detached entity lives in a thread-local for the duration of the request. Any
 *       attempt to lazily load a relationship on that detached entity throws
 *       {@code LazyInitializationException}. {@link UserPrincipal} is a plain object — no
 *       persistence context dependency, safe to hold anywhere.</li>
 *
 *   <li><strong>Least privilege</strong> — {@link User} carries {@code passwordHash}. If the
 *       entity were stored in the SecurityContext, {@code passwordHash} would be reachable from
 *       anywhere in the application that calls
 *       {@code SecurityContextHolder.getContext().getAuthentication().getPrincipal()}. This
 *       class copies only the fields controllers and services actually need: {@code userId},
 *       {@code email}, {@code role}, {@code isActive}. The password hash is consumed by the
 *       authentication step and discarded.</li>
 *
 *   <li><strong>Single responsibility</strong> — Authority mapping ({@code ROLE_USER},
 *       {@code ROLE_ADMIN}) lives here, not scattered across the entity, service, and
 *       filter layers.</li>
 * </ol>
 *
 * <p><strong>Design note on {@code passwordHash} visibility:</strong><br>
 * {@link UserDetails#getPassword()} must return the stored credential so that
 * {@code DaoAuthenticationProvider} can call {@code BCryptPasswordEncoder.matches()} against it
 * during login. However, {@code getPassword()} is only called by the authentication
 * infrastructure, never by application code. After {@code DaoAuthenticationProvider} completes,
 * the {@link UserPrincipal} stored in the {@link org.springframework.security.core.Authentication}
 * object is the one used for the rest of the request — and by that point no Spring Security
 * component will call {@code getPassword()} again. The field is intentionally {@code private
 * final} with no getter exposed beyond the {@link UserDetails} contract.
 */
public final class UserPrincipal implements UserDetails {

    // -------------------------------------------------------------------------
    // Identity fields — what the application actually needs at runtime
    // -------------------------------------------------------------------------

    /**
     * The user's database primary key. This is what services should use to look up
     * or correlate user records — not email, which can change.
     */
    private final UUID userId;

    /**
     * Stored for display and audit purposes. Also serves as the {@code username}
     * in the Spring Security sense (i.e. what was used to authenticate).
     */
    private final String email;

    /**
     * The user's role. Stored here so {@link #getAuthorities()} can produce the
     * correct {@link GrantedAuthority} without a second DB call.
     */
    private final UserRole role;

    /**
     * Account status. Drives {@link #isEnabled()} and {@link #isAccountNonLocked()}.
     * Both map to the same flag in our model — deactivated users are both disabled
     * and effectively locked.
     */
    private final boolean isActive;

    // -------------------------------------------------------------------------
    // Credential field — consumed by DaoAuthenticationProvider, then irrelevant
    // -------------------------------------------------------------------------

    /**
     * The BCrypt hash of the user's password. Required by {@link UserDetails#getPassword()}
     * so {@code DaoAuthenticationProvider} can verify credentials during login.
     *
     * <p>This field has no public getter beyond the {@link UserDetails} interface.
     * It is intentionally not exposed via a separate accessor. Once authentication
     * completes, this value serves no further purpose in the application.
     */
    private final String passwordHash;

    // -------------------------------------------------------------------------
    // Constructor — package-private; only UserDetailsServiceImpl should create these
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link UserPrincipal} from a {@link User} entity.
     *
     * <p>Package-private to enforce that only {@link UserDetailsServiceImpl} produces
     * instances. Application code should never construct a {@link UserPrincipal} directly
     * — it should be retrieved from the SecurityContext.
     *
     * @param user the fully-loaded {@link User} entity; must not be {@code null}
     */
    UserPrincipal(final User user) {
        this.userId       = user.getId();
        this.email        = user.getEmail();
        this.role         = user.getRole();
        this.isActive     = user.getIsActive();
        this.passwordHash = user.getPasswordHash();
    }

    // -------------------------------------------------------------------------
    // Static factory — the only public way to create instances
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link UserPrincipal} from a {@link User} entity.
     *
     * <p>Prefer this over calling the constructor directly. When code in other packages
     * needs a {@link UserPrincipal} (e.g. test setup), this is the approved entry point.
     *
     * @param user the domain user entity
     * @return a fully-initialised {@link UserPrincipal}
     */
    public static UserPrincipal from(final User user) {
        return new UserPrincipal(user);
    }

    // -------------------------------------------------------------------------
    // Application-facing accessors — not part of UserDetails
    // -------------------------------------------------------------------------

    /**
     * Returns the user's database primary key.
     *
     * <p>Use this — not {@link #getUsername()} — when you need to look up or associate
     * records with a user. Email addresses can change; UUIDs don't.
     *
     * @return the user's UUID primary key; never {@code null}
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * Returns the user's email address.
     *
     * <p>This is also the value returned by {@link #getUsername()}, since email is
     * the login credential in this application.
     *
     * @return the user's email; never {@code null}
     */
    public String getEmail() {
        return email;
    }

    /**
     * Returns the user's role as a domain enum.
     *
     * <p>Use this when your code needs to branch on role semantics. Use
     * {@link #getAuthorities()} when you need to integrate with Spring Security
     * authorization infrastructure (e.g. {@code @PreAuthorize}, {@code hasRole()}).
     *
     * @return the user's {@link UserRole}; never {@code null}
     */
    public UserRole getRole() {
        return role;
    }

    // -------------------------------------------------------------------------
    // UserDetails contract
    // -------------------------------------------------------------------------

    /**
     * Returns the Spring Security authority for this user's role.
     *
     * <p>Spring Security's convention is {@code ROLE_} prefix for role-based authorities.
     * {@code hasRole("USER")} in a security expression matches {@code ROLE_USER} here.
     * {@code hasAuthority("ROLE_USER")} also matches — both spellings work.
     *
     * <p>A single user has exactly one role and therefore exactly one authority. If the
     * application later requires fine-grained permissions, they should be added here as
     * additional {@link GrantedAuthority} instances loaded from a permissions table.
     *
     * @return an immutable singleton list containing the role-derived authority
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Returns the BCrypt password hash for credential verification.
     *
     * <p>Called exclusively by {@code DaoAuthenticationProvider} during the login flow.
     * Application code must never call this method — there is no legitimate reason for
     * a controller or service to inspect a user's password hash.
     *
     * @return the BCrypt password hash; never {@code null}
     */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /**
     * Returns the email address as the Spring Security username.
     *
     * <p>In Spring Security's vocabulary, "username" means "the unique login identifier",
     * not necessarily a display name. This application uses email as that identifier.
     *
     * @return the user's email address; never {@code null}
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Returns whether the account has not expired.
     *
     * <p>This application does not model account expiry. Returns {@code true} unconditionally.
     * If account expiry is added later, add an {@code accountExpiresAt} field to {@link User}
     * and check it here.
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Returns whether the account is not locked.
     *
     * <p>Maps to {@code isActive}. A deactivated user is treated as locked — they cannot
     * authenticate even if they present valid credentials.
     *
     * <p>This means a deactivated account fails authentication with
     * {@code LockedException} rather than {@code BadCredentialsException}, which is the
     * correct semantic: the credentials are fine, but the account status prevents login.
     */
    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    /**
     * Returns whether the credentials (password) have not expired.
     *
     * <p>This application does not model password expiry. Returns {@code true}
     * unconditionally. If a "must change password on next login" feature is added,
     * add a {@code passwordExpired} column to {@code users} and check it here.
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Returns whether the account is enabled.
     *
     * <p>Maps to {@code isActive}. Disabled users are rejected by
     * {@code DaoAuthenticationProvider} before password verification occurs, which means
     * the failure reason ("account disabled") is communicated to the caller without
     * revealing whether the password was correct.
     */
    @Override
    public boolean isEnabled() {
        return isActive;
    }

    // -------------------------------------------------------------------------
    // Object identity
    // -------------------------------------------------------------------------

    /**
     * Two {@link UserPrincipal} instances are equal if they represent the same user.
     * Identity is determined by {@code userId} — the immutable primary key — not by
     * email (which can change) or role (which can be promoted/demoted).
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof UserPrincipal other)) return false;
        return userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    /**
     * Deliberately excludes {@code passwordHash} from the string representation.
     * This object is logged in debug output, audit trails, and exception messages
     * from Spring Security. The password hash must never appear in any log.
     */
    @Override
    public String toString() {
        return "UserPrincipal{" +
               "userId=" + userId +
               ", email='" + email + '\'' +
               ", role=" + role +
               ", isActive=" + isActive +
               '}';
    }
}
