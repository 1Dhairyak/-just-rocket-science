package com.justrocketscience.auth.entity;

/**
 * Represents the role of a user in the JRS platform.
 *
 * Maps to the PostgreSQL ENUM type: user_role ('USER', 'ADMIN')
 * defined in V1__create_users.sql.
 *
 * Stored in the DB via @Enumerated(EnumType.STRING) on User.role.
 * EnumType.STRING stores the literal name ("USER", "ADMIN") —
 * never use EnumType.ORDINAL in production (adding a value
 * in the middle silently corrupts every existing row).
 *
 * Spring Security integration:
 *   UserDetailsServiceImpl converts this to a GrantedAuthority
 *   with the "ROLE_" prefix: ROLE_USER, ROLE_ADMIN.
 *   @PreAuthorize("hasRole('ADMIN')") checks for ROLE_ADMIN.
 */
public enum UserRole {

    /**
     * Standard authenticated user.
     * Default role on registration.
     * Can: browse, search, save favorites, compare rockets.
     * Cannot: create/update/delete agency or rocket data.
     */
    USER,

    /**
     * Elevated privileges.
     * Granted only via direct DB update or admin promotion endpoint.
     * Can: everything USER can + write access to agency/rocket/launch data.
     */
    ADMIN
}
