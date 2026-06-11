package com.jrs.gateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Generates test JWT tokens signed with the same HS256 secret as the gateway.
 * Secret must match application-test.yml → jrs.jwt.secret.
 */
public final class TestTokenFactory {

    // 256-bit Base64 secret — must match application-test.yml
    public static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQtZm9yLWpycy1nYXRld2F5LWludGVncmF0aW9uLXRlc3Rz";

    public static final String ISSUER   = "jrs-auth-service";
    public static final String AUDIENCE = "jrs-api-gateway";

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

    private TestTokenFactory() {}

    /** Standard valid access token. */
    public static String validToken(String subject) {
        return validToken(subject, "ROLE_USER");
    }

    /** Valid token with explicit role. */
    public static String validToken(String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("type", "access")
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(KEY)
                .compact();
    }

    /** Token signed with a different secret → invalid signature. */
    public static String wrongSecretToken(String subject) {
        String wrongSecret = "d3Jvbmctc2VjcmV0LWZvci10ZXN0aW5nLXNpZ25hdHVyZS1mYWlsdXJl";
        SecretKey wrongKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(wrongSecret));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(wrongKey)
                .compact();
    }

    /** Already-expired token. */
    public static String expiredToken(String subject) {
        Instant past = Instant.now().minusSeconds(3600);
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("type", "access")
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(900)))
                .signWith(KEY)
                .compact();
    }

    /** Token with wrong issuer. */
    public static String wrongIssuerToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer("rogue-auth-service")
                .audience().add(AUDIENCE).and()
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(KEY)
                .compact();
    }

    /** Token with wrong audience. */
    public static String wrongAudienceToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience().add("some-other-service").and()
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(KEY)
                .compact();
    }

    /** Token missing the 'type' claim → treated as invalid. */
    public static String noTypeClaimToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(KEY)
                .compact();
    }

    /** Refresh token (type=refresh) used against access-only endpoints. */
    public static String refreshToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(86400)))
                .signWith(KEY)
                .compact();
    }

    /** Bearer header value from a token string. */
    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
