# Step 9 — AuthService

**File:** `AuthService.java`

---

## Package Structure

```
src/
├── main/java/com/justrocketscience/auth/
│   └── service/
│       └── AuthService.java
└── test/java/com/justrocketscience/auth/
    └── service/
        └── AuthServiceTest.java
```

---

## Method Reference

| Method | Transaction | Auth Required | Description |
|---|---|---|---|
| `register()` | `@Transactional` (write) | None | Create account, return success message |
| `login()` | `@Transactional` (write) | None | Authenticate, issue token pair |
| `refresh()` | `@Transactional` (write) | None | Rotate refresh token pair |
| `logout()` | `@Transactional` (write) | Bearer | Blacklist JTI + revoke refresh token |
| `logoutAllDevices()` | `@Transactional` (write) | Bearer | Blacklist JTI + revoke all sessions |
| `changePassword()` | `@Transactional` (write) | Bearer | Re-verify current password, rotate all tokens |
| `getCurrentUser()` | `@Transactional(readOnly=true)` | Bearer | Read profile from DB |

---

## Transaction Boundaries

Each method is a **single DB transaction**. No method spans multiple transactions.

**Why single transactions?**

`login()` and `refresh()` write exactly one row (a `RefreshToken`). If that write fails, no partial state is committed. There is no scenario where a token is issued but its DB record is missing — the insert either succeeds atomically or the exception propagates before the response is sent.

`refresh()` atomically revokes the old token row and inserts the new one. This is the critical invariant: the old token must be invalidated in the same transaction as the new token is created. If the DB commit fails, the old token remains valid and the client can retry. There is no window where both tokens are invalid simultaneously.

`changePassword()` atomically updates the password hash and issues the revoke-all. If the write fails, neither change is committed. The user's password and their active sessions remain consistent at all times.

**Redis writes are intentionally outside the DB transaction.**

`tokenBlacklistService.blacklistJti()` is called after the DB transaction commits (for `logout`) or before it completes (for `changePassword`). In `changePassword`, the blacklist write is in a try-catch — failure is logged but non-fatal. The refresh tokens are revoked in the DB; worst case, the access token remains usable for its remaining TTL (≤15 minutes). This is an acceptable trade-off: a Redis failure should not roll back a successful password change.

---

## Security Decisions

### Decision 1 — `AuthenticationManager.authenticate()` is the only BCrypt call for login

`AuthService.login()` never calls `passwordEncoder.matches()` directly. `AuthenticationManager.authenticate()` delegates to `DaoAuthenticationProvider`, which calls `UserDetailsServiceImpl.loadUserByUsername()` and then BCrypt. This preserves the timing-safe inactive-account handling implemented in Step 8. If `AuthService` called BCrypt directly, it would need to re-implement that timing safety independently, creating two maintenance surfaces.

### Decision 2 — `changePassword()` verifies current password even with a valid JWT

A valid JWT proves the user authenticated at some point in the past. It does not prove the user is present at the keyboard right now. The current password re-verification defends against:
- CSRF (even without cookies, if a request can be forged)
- Stolen-but-unexpired access tokens (attacker can't change the password without knowing the current one)

This matches the pattern used by GitHub, Google, and every major identity provider for credential-changing operations.

### Decision 3 — Refresh token reuse detection triggers full session revocation

If `refresh()` receives a hash that is in the DB but already has `revoked = true`, it means one of three things:
1. The user's device retried a request that already succeeded (network error)
2. The token was stolen and the attacker rotated it before the victim did
3. A bug in the client caused a token to be re-submitted

Cases 1 and 3 are rare and recoverable (re-login required). Case 2 is the dangerous one. Because there is no way to distinguish them from the server side, the safe default is to revoke all sessions for the user. The victim will need to re-login; the attacker's stolen rotation is also revoked.

This is the **refresh token family** pattern described in RFC 6819 §4.1.2.

### Decision 4 — `logout()` uses `extractClaimsIgnoreExpiry()` for JTI extraction

If a token expires during the 200ms window between the frontend calling `POST /logout` and the backend processing it, a strict expiry check on the access token would make the logout fail. The user would be stuck — they cannot log out, and their (just-expired) token can no longer be used anyway. Using `extractClaimsIgnoreExpiry()` handles this race condition. The signature is still validated — only the expiry check is relaxed for this specific call.

### Decision 5 — No user data in `AuthResponse`

`AuthResponse` contains tokens and expiry metadata only. Profile data is not included. This prevents coupling the auth response shape to the profile entity. When the profile adds fields in a future phase, the auth flow is unchanged. Clients are expected to call `GET /me` after login if they need profile data.

### Decision 6 — `register()` does not return tokens

Registration does not issue tokens. The client must perform an explicit `POST /login`. Reasons:
- Future phases may require email verification before first login; tokens would need to be invalidated retroactively
- Registration and authentication are separate concerns; coupling them complicates future MFA and email-verify flows
- The 201 response is clean — no auth state to manage

### Decision 7 — Duplicate check uses a single OR query, not two separate exists queries

`userRepository.existsByEmailOrUsername(email, username)` is one round-trip to the DB. Two separate calls (`existsByEmail` + `existsByUsername`) would be two round-trips. The DB unique constraint remains the authoritative guard in the face of the TOCTOU race between the check and the insert. The single query is a UX optimisation only — "account already exists" — not a security control.

The error message deliberately does not specify which field conflicts, preventing user enumeration. An attacker sending known emails cannot determine which ones have accounts based on the error text.

### Decision 8 — `hashToken()` uses SHA-256, not BCrypt

BCrypt for refresh token storage would be ~400ms per call (strength 12) — applied at every refresh and logout, this is 400ms of avoidable latency. Refresh tokens are high-entropy JWTs (the signature alone provides >128 bits of entropy). SHA-256 is appropriate here because the input is already high-entropy; BCrypt's salt and cost factor provide no additional security over SHA-256 for pre-entropy inputs. The stored hash is used for lookup-by-hash, not for password-style verification.

### Decision 9 — `maskEmail()` for log safety

Email addresses are PII. Logging the full email in access logs violates GDPR Article 5 data minimisation. `maskEmail()` produces `ali***@example.com` — enough to correlate a log line with a support request, not enough to harvest email addresses from log files.

---

## Exception Types

| Exception | HTTP | Trigger |
|---|---|---|
| `DuplicateUserException` | 409 Conflict | Email or username already exists |
| `BadCredentialsException` (Spring) | 401 | Wrong password on login or changePassword |
| `DisabledException` (Spring) | 401 | Inactive account on login |
| `InvalidTokenException` | 401 | Malformed, expired, or wrong-type token |
| `TokenRevokedException` | 401 | Revoked token presented (reuse detection) |
| `UserNotFoundException` | 404 | userId from valid JWT no longer in DB |
| `AccountDisabledException` | 403 | Account inactive at refresh/changePassword time |
| `SamePasswordException` | 400 | New password identical to current |
| `AuthenticationRequiredException` | 401 | SecurityContext empty on guarded endpoint |

All exceptions are mapped to RFC 7807 Problem JSON in `AuthExceptionHandler` (Step 10).

---

## Audit Logging Points

| Log Key | Level | When |
|---|---|---|
| `AUTH_REGISTER_ATTEMPT` | INFO | Registration request received |
| `AUTH_REGISTER_CONFLICT` | WARN | Email or username duplicate detected |
| `AUTH_REGISTER_SUCCESS` | INFO | User persisted |
| `AUTH_LOGIN_ATTEMPT` | INFO | Login request received (email masked) |
| `AUTH_LOGIN_FAILED_BAD_CREDENTIALS` | WARN | Wrong password |
| `AUTH_LOGIN_FAILED_DISABLED` | WARN | Inactive account |
| `AUTH_LOGIN_SUCCESS` | INFO | Tokens issued, userId logged |
| `AUTH_REFRESH_ATTEMPT` | INFO | Refresh request received |
| `AUTH_REFRESH_INVALID_TOKEN` | WARN | Token structurally invalid |
| `AUTH_REFRESH_TOKEN_NOT_FOUND` | WARN | Hash not in DB |
| `AUTH_REFRESH_TOKEN_REUSE_DETECTED` | **ERROR** | Revoked token re-presented — possible theft |
| `AUTH_REFRESH_TOKEN_EXPIRED` | WARN | DB row past expiry |
| `AUTH_REFRESH_SUCCESS` | INFO | New pair issued |
| `AUTH_LOGOUT` | INFO | Access token JTI + userId |
| `AUTH_LOGOUT_REFRESH_REVOKED` | INFO | Refresh token row revoked |
| `AUTH_LOGOUT_ALL_DEVICES` | INFO | All sessions revoked |
| `AUTH_LOGOUT_MALFORMED_TOKEN` | WARN | Couldn't extract JTI |
| `AUTH_CHANGE_PASSWORD_ATTEMPT` | INFO | Request received |
| `AUTH_CHANGE_PASSWORD_BAD_CURRENT_PASSWORD` | WARN | Wrong current password |
| `AUTH_CHANGE_PASSWORD_SUCCESS` | INFO | Password updated, all sessions revoked |
| `AUTH_CHANGE_PASSWORD_JTI_BLACKLIST_FAILED` | WARN | Redis write failed (non-fatal) |
| `AUTH_GET_CURRENT_USER_NOT_FOUND` | ERROR | JWT references deleted account |

**Note:** No passwords, raw tokens, or full email addresses ever appear in any log line.

---

## Failure Handling Strategy

### DB failures

Spring's `@Transactional` rolls back on any unchecked exception. All repository calls are wrapped by JPA and will throw `DataAccessException` subclasses on DB errors, which propagate up and trigger a rollback. The controller catches unhandled exceptions via `AuthExceptionHandler` and returns a 500.

### Redis failures (blacklist)

The `TokenBlacklistService` implements asymmetric failure:
- **Write failure** (blacklist on logout): fail-open — log and continue. The refresh token is still revoked in the DB. The access token may be usable for its remaining TTL.
- **Read failure** (check on request validation in Gateway): fail-closed — return `true` (treat as blacklisted, reject the request).

This is the correct trade-off: allowing a logged-out access token to be briefly usable is a minor security degradation; allowing an unknown blacklist state to pass through is worse.

In `changePassword`, the JTI blacklist failure is caught explicitly, logged as WARN, and does not roll back the password change. The password change is the more important operation; the TTL on the access token is the backstop.

### Race conditions

**Duplicate registration:** Two simultaneous registrations of the same email. The `existsByEmailOrUsername` check is not transactionally isolated between the two requests. Both may pass the check and attempt the insert. One will succeed; one will hit the DB unique constraint and receive a `DataIntegrityViolationException`. `AuthExceptionHandler` maps this to a 409.

**Token rotation race:** Two simultaneous refresh calls with the same token. Both find the token as not-revoked. Both attempt to revoke it and insert a new token. The second `revokeByTokenHash` call has an `AND revoked = false` guard and updates 0 rows. The second `persistRefreshToken` succeeds but the second response is effectively a duplicate. This is benign — one response reaches the client; the other is ignored.

---

## Unit Testing Strategy

**`AuthServiceTest`** — Mockito-based, no Spring context, ~100ms total.

All collaborators are mocked: `AuthenticationManager`, `BCryptPasswordEncoder`, `JwtService`, `TokenBlacklistService`, `UserRepository`, `RefreshTokenRepository`, `UserMapper`, `JwtConfig`.

### `register()` tests

- `register_success` — uniqueness check passes, user saved, response contains success message
- `register_duplicateEmailOrUsername` — `existsByEmailOrUsername` returns true → `DuplicateUserException`
- `register_passwordIsEncodedByPasswordEncoder` — verify `passwordEncoder.encode()` called with raw password
- `register_mapperIsNeverCalledWithPasswordHash` — verify mapper doesn't receive the password
- `register_setsActiveAndRoleOnEntity` — entity has `isActive=true`, `role=USER` before save

### `login()` tests

- `login_success` — authentication passes, tokens generated, refresh token persisted, `AuthResponse` returned
- `login_badCredentials` — `AuthenticationManager` throws `BadCredentialsException` → propagated
- `login_disabledAccount` — `AuthenticationManager` throws `DisabledException` → propagated
- `login_refreshTokenPersistedAsHash` — captured `RefreshToken.tokenHash` differs from raw token
- `login_rawTokenNeverStoredDirectly` — assert `tokenHash` != raw refresh token string

### `refresh()` tests

- `refresh_success_rotatesTokens` — old token revoked, new tokens issued
- `refresh_invalidJwt` — `isRefreshTokenValid` returns false → `InvalidTokenException`
- `refresh_tokenNotFound` — DB returns empty → `InvalidTokenException`
- `refresh_alreadyRevoked_triggersFullRevocation` — `isRevoked()` true → `revokeAllByUserId` called → `TokenRevokedException`
- `refresh_expiredDbRow` — `isExpired()` true → `InvalidTokenException`
- `refresh_inactiveUser` — user `isActive=false` → `AccountDisabledException`
- `refresh_atomicRotation` — `revokeByTokenHash` called before `persistRefreshToken` (argument capture + order verification)

### `logout()` tests

- `logout_success` — JTI blacklisted, refresh token revoked
- `logout_noRefreshToken` — only JTI is blacklisted, no repo call for refresh
- `logout_malformedToken` — exception in claim extraction → method returns silently (no throw)
- `logout_idempotent` — second call with same token doesn't throw
- `logout_justExpiredToken_stillBlacklists` — `extractJtiIgnoreExpiry` succeeds even for expired token

### `changePassword()` tests

- `changePassword_success` — password updated, all tokens revoked, JTI blacklisted
- `changePassword_wrongCurrentPassword` — `matches(currentPassword, hash)` returns false → `BadCredentialsException`
- `changePassword_samePassword` — new password matches stored hash → `SamePasswordException`
- `changePassword_updateReturnsZero_throws` — `updatePasswordHash` returns 0 → `AccountDisabledException`
- `changePassword_redisFailure_doesNotRollbackPasswordUpdate` — blacklist throws → password still updated (mock verify)
- `changePassword_allRefreshTokensRevoked` — `revokeAllByUserId` called before method returns

### `getCurrentUser()` tests

- `getCurrentUser_success` — returns mapped profile
- `getCurrentUser_userNotFound` — repository returns empty → `UserNotFoundException`
- `getCurrentUser_noSecurityContext` — `SecurityContextHolder` empty → `AuthenticationRequiredException`

### `hashToken()` tests (via integration with login/refresh)

- `hashToken_deterministicForSameInput` — two calls with same string produce same hash
- `hashToken_differentForDifferentInput` — two different tokens produce different hashes
- `hashToken_rawTokenNotInStoredRow` — captured saved entity's `tokenHash` != raw token string

---

*Next step: Step 10 — `AuthController.java` + `AuthExceptionHandler.java`*
