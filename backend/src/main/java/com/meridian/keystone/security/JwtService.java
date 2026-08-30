package com.meridian.keystone.security;

import com.meridian.keystone.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

/**
 * Issues and validates stateless JWTs (HS256). Uses the jjwt 0.12.x API.
 *
 * <p>The token subject is the user's email; role, user id, and full name ride
 * along as custom claims so the frontend and filter can avoid an extra lookup.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(AppProperties props) {
        byte[] secretBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        // HS256 requires a key of at least 256 bits (32 bytes). Fail fast if too short.
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256; got " + secretBytes.length);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMinutes = props.jwt().expirationMinutes();
    }

    /**
     * Generate a signed token for the given authenticated user.
     */
    public String generateToken(KeystoneUserDetails user) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(user.getUsername())
                .claims(Map.of(
                        "uid", user.getId(),
                        "role", user.getRole().name(),
                        "name", user.getFullName()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    /**
     * Parse and verify a token, returning its claims. Throws {@link JwtException}
     * if the signature is invalid or the token is expired/malformed.
     */
    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract the subject (email) from a token, or null if it cannot be parsed.
     */
    public String extractUsername(String token) {
        try {
            return parse(token).getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
