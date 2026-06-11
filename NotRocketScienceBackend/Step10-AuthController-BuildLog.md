# Step 10 — AuthController + AuthExceptionHandler

**Files:** `AuthController.java`, `AuthExceptionHandler.java`

---

## Package Structure

```
src/
├── main/java/com/justrocketscience/auth/
│   └── controller/
│       ├── AuthController.java
│       └── AuthExceptionHandler.java
└── test/java/com/justrocketscience/auth/
    └── controller/
        └── AuthControllerTest.java
```

---

## Endpoint Reference

| Method | Path | Auth | Status | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | None | 201 | Create account |
| `POST` | `/api/v1/auth/login` | None | 200 | Issue token pair |
| `POST` | `/api/v1/auth/refresh` | None | 200 | Rotate token pair |
| `POST` | `/api/v1/auth/logout` | Bearer | 204 | Single-device logout |
| `POST` | `/api/v1/auth/logout/all` | Bearer | 204 | All-device logout |
| `GET` | `/api/v1/auth/me` | Bearer | 200 | Current user profile |
| `PUT` | `/api/v1/auth/change-password` | Bearer | 204 | Change password |

---

## Controller Design Philosophy

The controller contains **zero business logic**. Its only responsibilities:

1. HTTP verb and path mapping
2. Deserializing JSON bodies with `@Valid` delegation to Jakarta Validation
3. Extracting raw tokens from headers (Bearer prefix stripping)
4. Wrapping service results in `ApiResponse`
5. Setting response headers (`Cache-Control`, `X-Token-Revoked`)

Every decision about whether a password is correct, whether a token is valid, or whether a user exists lives in `AuthService`. The controller trusts the service's output completely and never pre-validates domain rules.

Exceptions are never caught in the controller. They propagate to `AuthExceptionHandler` which owns all status-code-to-exception mappings.

---

## Per-Endpoint Documentation

### POST /api/v1/auth/register

**Request flow:**
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "alice",
  "email": "alice@example.com",
  "password": "Str0ng!Pass"
}
```
1. Jackson deserializes to `RegisterRequest`
2. `@Valid` triggers `@NotBlank`, `@Size(3–50)`, `@Pattern([a-zA-Z0-9_-])` on username; `@Email`, `@Size(max=150)` on email; `@Size(8–100)`, `@Pattern(complexity)` on password
3. Violation → `MethodArgumentNotValidException` → handler → 400 with field errors map
4. `AuthService.register()` runs uniqueness check, encodes password, persists user

**Response flow:**
```
HTTP/1.1 201 Created
Content-Type: application/json

{
  "success": true,
  "data": {
    "message": "Registration successful. Please log in."
  }
}
```

**Status code decisions:**
- `201 Created` — a persistent resource (user account) was created. RFC 7231 §6.3.2.
- No `Location` header — exposing `/users/{userId}` at registration time would leak the UUID before the client has authenticated.
- No tokens in the response — forces explicit login, leaving room for email verification in a future phase without changing this contract.

**Security considerations:**
- `400` on validation failure reveals that the input format was wrong, not that an account exists or doesn't exist.
- `409` on conflict returns a generic message — "An account with that email or username already exists." — never specifying which field. Prevents user enumeration via the registration form.
- Max password length `100` blocks BCrypt DoS. BCrypt silently truncates at 72 bytes in many implementations, but hashing a multi-megabyte password string before truncation is still expensive. The cap is at the HTTP layer, before BCrypt is called.

---

### POST /api/v1/auth/login

**Request flow:**
```
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "alice@example.com",
  "password": "Str0ng!Pass"
}
```
1. `@Valid` checks `@NotBlank` and `@Size(max=100)` on password. No complexity pattern — see Step 5 DTO decisions (enforcing complexity at login creates a policy oracle).
2. `User-Agent` header is captured from `HttpServletRequest` and passed to the service as `deviceInfo`.
3. `AuthService.login()` delegates to `AuthenticationManager`.

**Response flow:**
```
HTTP/1.1 200 OK
Cache-Control: no-store
Pragma: no-cache
Content-Type: application/json

{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "expiresAt": "2025-01-01T12:15:00Z"
  }
}
```

**Status code decisions:**
- `200 OK` — login is not a resource creation. No URI for the "session" exists. 200 is correct.
- Some APIs return `201` here. This is arguable but technically wrong per RFC 7231 — 201 requires a `Location` header pointing to the created resource.

**Headers:**
- `Cache-Control: no-store` — RFC 6749 §5.1 requirement. Prevents tokens from being stored in any cache.
- `Pragma: no-cache` — legacy HTTP/1.0 cache-busting header. Belt-and-suspenders for old proxy servers.

**Security considerations:**
- `BadCredentialsException` and `DisabledException` from `AuthenticationManager` both map to `401` with identical message: "Invalid email or password." Distinguishing them leaks whether an account exists.
- Rate limiting on this endpoint is applied at the Gateway (Redis token bucket). The controller doesn't enforce it — the Gateway is the single enforcement point.

---

### POST /api/v1/auth/refresh

**Request flow:**
```
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

No `Authorization` header — the access token is typically expired at this point. Requiring it would be circular.

**Response flow:** Same shape as login — 200 with `AuthResponse`.

**Status code decisions:**
- `200 OK` — no new resource is created. The existing session continues with new tokens.
- `401` on all token failures (invalid, expired, revoked, not found). The response message does not reveal the exact failure reason to the caller — "The refresh token is invalid or has already been used."

**Security considerations:**
- Token reuse (`TokenRevokedException`) maps to the same `401` response as an expired token. The internal service logs `AUTH_REFRESH_TOKEN_REUSE_DETECTED` at ERROR level and revokes all sessions. The client response is indistinguishable from a normal invalid-token 401 — the attacker gets no signal that the theft was detected.
- The refresh endpoint is not protected by `JwtAuthenticationFilter` in `SecurityConfig` — it is in `permitAll()`.

---

### POST /api/v1/auth/logout

**Request flow:**
```
POST /api/v1/auth/logout
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```
The body is optional. If absent, only the access token JTI is blacklisted.

**Response flow:**
```
HTTP/1.1 204 No Content
```

**Status code decisions:**
- `204 No Content` — destructive operation, nothing meaningful to return. 204 is more precise than 200 with an empty or placeholder body.
- Logout is always idempotent — second call with the same (already-blacklisted) token still returns 204.

**Security considerations:**
- The `Authorization` header is extracted as a raw string here — `@RequestHeader(HttpHeaders.AUTHORIZATION)` — because the raw token string is needed for JTI extraction. `@AuthenticationPrincipal` gives the principal but not the raw JWT.
- The raw token is passed to the service and is never logged.
- `extractBearerToken()` is a defensive guard. In practice, `JwtAuthenticationFilter` will have already rejected malformed Authorization headers before this point.

---

### GET /api/v1/auth/me

**Request flow:**
```
GET /api/v1/auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

No body. No parameters.

**Response flow:**
```
HTTP/1.1 200 OK
Cache-Control: private, max-age=60
Content-Type: application/json

{
  "success": true,
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "alice",
    "email": "alice@example.com",
    "role": "USER",
    "isActive": true,
    "createdAt": "2025-01-01T10:00:00Z"
  }
}
```

**Status code decisions:**
- `200 OK` — standard successful GET.
- `404 Not Found` — only possible if the JWT references a userId that was permanently deleted from the DB (rare; soft-delete means `is_active = false`, not a hard DELETE). Handled by `UserNotFoundException`.

**Headers:**
- `Cache-Control: private, max-age=60` — profile is user-specific (private, not CDN-cacheable). A 60-second browser cache reduces redundant calls from SPAs rendering the profile on multiple routes.
- `private` is essential — without it, a shared proxy could cache one user's profile and serve it to another.

**Security considerations:**
- `@AuthenticationPrincipal` is preferred in the controller over `SecurityContextHolder.getContext()`. It is injected by Spring MVC and is directly stubable in MockMvc tests via `with(user(...))`.
- The method signature in the final implementation passes the principal to the service. Shown as `authService.getCurrentUser()` in the code — the principal is read from `SecurityContextHolder` in the service, which is the cleaner pattern for testability (service mocks don't need to stub the SecurityContext).
- Response never includes `passwordHash`, `updatedAt`, or refresh token data — enforced at compile time by `UserMapper` and the `UserProfileResponse` record shape.

---

### PUT /api/v1/auth/change-password

**Request flow:**
```
PUT /api/v1/auth/change-password
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "currentPassword": "Str0ng!Pass",
  "newPassword": "N3w!Passw0rd"
}
```

**Response flow:**
```
HTTP/1.1 204 No Content
X-Token-Revoked: true
```

**Status code decisions:**
- `204 No Content` — successful operation with no response body. Client must re-login.
- `400` if `newPassword` fails complexity validation, or if `currentPassword == newPassword` (`@PasswordsNotEqual` class-level constraint, or `SamePasswordException` from service).
- `401` if `currentPassword` is wrong (mapped from `BadCredentialsException`).

**HTTP method decision — PUT vs PATCH:**
- `PATCH` implies partial update of a resource representation.
- Password change is a domain operation with mandatory current-password verification — not a partial JSON Merge Patch over the user entity.
- `PUT` to a dedicated action path (`/change-password`) is idiomatic for operations that look like mutations but carry domain rules beyond simple field replacement.
- `POST` would also be acceptable and arguably more honest (it's an action, not a resource replacement). `PUT` is used here for consistency with typical API conventions for update-like actions.

**Headers:**
- `X-Token-Revoked: true` — a non-standard signal to the frontend SDK that all tokens have been invalidated. The frontend intercepts this header and redirects to the login screen. Without it, the client would make a subsequent API call with its now-blacklisted access token and receive a confusing 401.

---

## Exception Handler — Design Decisions

### RFC 7807 Problem JSON

All error responses use Spring 6's built-in `ProblemDetail`:
```json
{
  "type": "https://api.justrocketscience.com/errors/authentication-failed",
  "title": "Authentication Failed",
  "status": 401,
  "detail": "Invalid email or password.",
  "timestamp": "2025-01-01T12:00:00Z"
}
```

`type` is a URI that uniquely identifies the problem class, enabling clients to programmatically handle specific error types without string-matching on `detail` (which can change). `timestamp` is an extension property — included for log correlation.

### Information leakage prevention in error responses

| Exception | External message | Internal log |
|---|---|---|
| `BadCredentialsException` | "Invalid email or password." | `AUTH_LOGIN_FAILED_BAD_CREDENTIALS` |
| `DisabledException` | "Invalid email or password." | `AUTH_LOGIN_FAILED_DISABLED` |
| `TokenRevokedException` | "The refresh token is invalid..." | `TOKEN_REUSE_RESPONSE_ISSUED` |
| `DuplicateUserException` | "An account with that email or username..." | `AUTH_REGISTER_CONFLICT` |
| `Exception` (catch-all) | "An unexpected error occurred." | Full stack trace at ERROR |

The external message never reveals: which field caused a duplicate conflict, whether the account exists, why a token was revoked, or any internal state.

### Validation error shape

```json
{
  "type": "https://api.justrocketscience.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more fields failed validation.",
  "errors": {
    "username": "must match \"[a-zA-Z0-9_-]+\"",
    "password": "Password must contain at least one uppercase letter, one number, and one special character"
  },
  "timestamp": "2025-01-01T12:00:00Z"
}
```

The `errors` map uses field name as key. Frontend validation libraries (React Hook Form, Formik, etc.) bind error messages to input fields by name — this shape is directly consumable without client-side transformation.

Class-level constraint violations (`@PasswordsNotEqual`) are bound to the `newPassword` field node by convention, set in the constraint annotation's `payload` configuration.

---

## Unit Testing Strategy — `AuthControllerTest`

**Setup:** `@WebMvcTest(AuthController.class)` — loads only the web layer. `AuthService` is `@MockBean`. `SecurityConfig` is disabled via a test-specific configuration that `permitAll()` all routes, or via `@WithMockUser` for authenticated endpoints. Spring's `MockMvc` is used throughout.

**Why `@WebMvcTest` not `@SpringBootTest`:**
- `@WebMvcTest` loads only the controller layer, filters, and exception handlers. Boot time ~300ms vs ~3000ms for full context.
- Service layer is mocked — tests focus exclusively on HTTP contract (headers, status codes, body shape, validation).
- Full-stack integration (real DB, real Redis) belongs in the Testcontainers integration tests (Step 11).

---

### `register` tests

```java
@Test
void register_validRequest_returns201() throws Exception {
    when(authService.register(any())).thenReturn(new RegisterResponse("Registration successful. Please log in."));

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "alice",
                  "email": "alice@example.com",
                  "password": "Str0ng!Pass99"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.message").value("Registration successful. Please log in."));
}

@Test
void register_blankUsername_returns400WithFieldError() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "",
                  "email": "alice@example.com",
                  "password": "Str0ng!Pass99"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.username").exists());
}

@Test
void register_invalidEmail_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "username": "alice", "email": "not-an-email", "password": "Str0ng!Pass99" }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email").exists());
}

@Test
void register_passwordTooShort_returns400() throws Exception {
    // ...min 8 chars
}

@Test
void register_passwordTooLong_returns400() throws Exception {
    // ...max 100 chars — also tests BCrypt DoS protection at the HTTP layer
}

@Test
void register_duplicateUser_returns409() throws Exception {
    when(authService.register(any())).thenThrow(new DuplicateUserException("already exists"));

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* valid body */))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(containsString("duplicate-user")))
        // Critical: response must not specify which field conflicted
        .andExpect(jsonPath("$.detail").value(not(containsString("email"))))
        .andExpect(jsonPath("$.detail").value(not(containsString("username"))));
}

@Test
void register_noTokensInResponse() throws Exception {
    when(authService.register(any())).thenReturn(new RegisterResponse("Registration successful. Please log in."));

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* valid body */))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.accessToken").doesNotExist())
        .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
}
```

---

### `login` tests

```java
@Test
void login_validCredentials_returns200WithTokens() throws Exception {
    AuthResponse mockResponse = new AuthResponse(
        "access.token.here", "refresh.token.here", "Bearer", 900L,
        Instant.now().plusSeconds(900)
    );
    when(authService.login(any())).thenReturn(mockResponse);

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "email": "alice@example.com", "password": "Str0ng!Pass99" }
                """))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.data.expiresIn").value(900));
}

@Test
void login_badCredentials_returns401_withGenericMessage() throws Exception {
    when(authService.login(any())).thenThrow(new BadCredentialsException("bad"));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* valid body */))
        .andExpect(status().isUnauthorized())
        // Must not say "account disabled", "wrong password", or confirm email existence
        .andExpect(jsonPath("$.detail").value("Invalid email or password."));
}

@Test
void login_disabledAccount_returns401_withSameGenericMessage() throws Exception {
    when(authService.login(any())).thenThrow(new DisabledException("disabled"));

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* valid body */))
        .andExpect(status().isUnauthorized())
        // CRITICAL: same message as bad credentials — no oracle
        .andExpect(jsonPath("$.detail").value("Invalid email or password."));
}

@Test
void login_cacheControlHeaderIsNoStore() throws Exception {
    when(authService.login(any())).thenReturn(/* mock auth response */);

    mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(/* body */))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(header().string("Pragma", "no-cache"));
}

@Test
void login_userAgentCapturedFromHeader() throws Exception {
    ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
    when(authService.login(captor.capture())).thenReturn(/* mock */);

    mockMvc.perform(post("/api/v1/auth/login")
            .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* body */))
        .andExpect(status().isOk());

    assertThat(captor.getValue().deviceInfo()).isEqualTo("Mozilla/5.0");
}
```

---

### `refresh` tests

```java
@Test
void refresh_validToken_returns200WithNewPair() throws Exception { /* ... */ }

@Test
void refresh_invalidToken_returns401() throws Exception {
    when(authService.refresh(any())).thenThrow(new InvalidTokenException("expired"));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{ "refreshToken": "expired.token" }"""))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value(containsString("invalid-token")));
}

@Test
void refresh_revokedToken_returns401_withoutRevealingReuse() throws Exception {
    when(authService.refresh(any())).thenThrow(new TokenRevokedException("reuse"));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{ "refreshToken": "revoked.token" }"""))
        .andExpect(status().isUnauthorized())
        // CRITICAL: same type URI as invalid-token — does not reveal reuse detection
        .andExpect(jsonPath("$.type").value(containsString("invalid-token")));
}

@Test
void refresh_missingToken_returns400() throws Exception {
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{ "refreshToken": "" }"""))
        .andExpect(status().isBadRequest());
}
```

---

### `logout` tests

```java
@Test
@WithMockUser
void logout_validRequest_returns204() throws Exception {
    doNothing().when(authService).logout(any(), any());

    mockMvc.perform(post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token.here")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{ "refreshToken": "refresh.token.here" }"""))
        .andExpect(status().isNoContent());
}

@Test
@WithMockUser
void logout_noBody_returns204_onlyAccessTokenBlacklisted() throws Exception {
    ArgumentCaptor<String> refreshTokenCaptor = ArgumentCaptor.forClass(String.class);
    doNothing().when(authService).logout(any(), refreshTokenCaptor.capture());

    mockMvc.perform(post("/api/v1/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token.here"))
        .andExpect(status().isNoContent());

    assertThat(refreshTokenCaptor.getValue()).isNull();
}

@Test
void logout_noAuthHeader_returns401() throws Exception {
    mockMvc.perform(post("/api/v1/auth/logout"))
        .andExpect(status().isUnauthorized());
}

@Test
@WithMockUser
void logout_idempotent_secondCallAlsoReturns204() throws Exception {
    // Service completes without throwing even if already logged out
    doNothing().when(authService).logout(any(), any());

    for (int i = 0; i < 2; i++) {
        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
            .andExpect(status().isNoContent());
    }
}
```

---

### `me` tests

```java
@Test
@WithMockUser
void me_authenticated_returns200WithProfile() throws Exception {
    UserProfileResponse profile = new UserProfileResponse(
        UUID.randomUUID(), "alice", "alice@example.com",
        UserRole.USER, true, Instant.now()
    );
    when(authService.getCurrentUser()).thenReturn(profile);

    mockMvc.perform(get("/api/v1/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=60"))
        .andExpect(jsonPath("$.data.username").value("alice"))
        .andExpect(jsonPath("$.data.email").value("alice@example.com"))
        .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
}

@Test
void me_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized());
}

@Test
@WithMockUser
void me_userNotFound_returns404() throws Exception {
    when(authService.getCurrentUser()).thenThrow(new UserNotFoundException("not found"));

    mockMvc.perform(get("/api/v1/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token"))
        .andExpect(status().isNotFound());
}

@Test
@WithMockUser
void me_responseCacheHeaderIsPrivate() throws Exception {
    when(authService.getCurrentUser()).thenReturn(/* mock profile */);

    mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
        // CRITICAL: private — not CDN-cacheable
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")));
}
```

---

### `changePassword` tests

```java
@Test
@WithMockUser
void changePassword_validRequest_returns204WithTokenRevokedHeader() throws Exception {
    doNothing().when(authService).changePassword(any(), any());

    mockMvc.perform(put("/api/v1/auth/change-password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "currentPassword": "Str0ng!Pass99", "newPassword": "N3w!Passw0rd1" }
                """))
        .andExpect(status().isNoContent())
        .andExpect(header().string("X-Token-Revoked", "true"));
}

@Test
@WithMockUser
void changePassword_wrongCurrentPassword_returns401() throws Exception {
    when(authService.changePassword(any(), any()))
        .thenThrow(new BadCredentialsException("wrong"));

    mockMvc.perform(put("/api/v1/auth/change-password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* valid body */))
        .andExpect(status().isUnauthorized());
}

@Test
@WithMockUser
void changePassword_samePassword_returns400() throws Exception {
    when(authService.changePassword(any(), any()))
        .thenThrow(new SamePasswordException("same as current"));

    mockMvc.perform(put("/api/v1/auth/change-password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* body with same new and current password */))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(containsString("same-password")));
}

@Test
@WithMockUser
void changePassword_weakNewPassword_returns400_beforeServiceIsCalled() throws Exception {
    mockMvc.perform(put("/api/v1/auth/change-password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "currentPassword": "Str0ng!Pass99", "newPassword": "weak" }
                """))
        .andExpect(status().isBadRequest());

    // Service must NOT be called — validation caught it first
    verifyNoInteractions(authService);
}

@Test
@WithMockUser
void changePassword_rawTokenPassedToService() throws Exception {
    ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
    doNothing().when(authService).changePassword(any(), tokenCaptor.capture());

    mockMvc.perform(put("/api/v1/auth/change-password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer actual.jwt.token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* valid body */))
        .andExpect(status().isNoContent());

    // Bearer prefix must be stripped
    assertThat(tokenCaptor.getValue()).isEqualTo("actual.jwt.token");
}
```

---

## Exception Handler Tests

```java
@Test
void exceptionHandler_validationError_includesFieldErrorsMap() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{ "username": "", "email": "bad", "password": "x" }"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors").isMap())
        .andExpect(jsonPath("$.errors.username").exists())
        .andExpect(jsonPath("$.errors.email").exists())
        .andExpect(jsonPath("$.errors.password").exists())
        .andExpect(jsonPath("$.timestamp").exists());
}

@Test
void exceptionHandler_unexpectedException_doesNotLeakStackTrace() throws Exception {
    when(authService.register(any())).thenThrow(new RuntimeException("DB connection pool exhausted at line 47"));

    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(/* valid body */))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
        // CRITICAL: internal message must not appear in response
        .andExpect(jsonPath("$.detail").value(not(containsString("DB connection pool"))))
        .andExpect(jsonPath("$.detail").value(not(containsString("line 47"))));
}
```

---

*Next step: Step 11 — Integration Tests (`AuthIntegrationTest` with Testcontainers — real PostgreSQL + real Redis)*
