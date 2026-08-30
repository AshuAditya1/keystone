package com.meridian.keystone.security;

import com.meridian.keystone.config.AppProperties;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.meridian.keystone.support.TestFixtures.principal;
import static com.meridian.keystone.support.TestFixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token issuing and verification.
 *
 * <p>The round-trip test is the least interesting one here. What matters is that
 * a token this service did not sign is refused: without that, every
 * authorization rule in the application is decoration, because a caller could
 * mint their own MANAGER token.
 */
class JwtServiceTest {

    private static final String SECRET =
            "keystone-test-secret-key-that-is-long-enough-for-hs256";
    private static final String OTHER_SECRET =
            "a-completely-different-secret-of-sufficient-length!!";

    private final JwtService jwt = serviceWith(SECRET, 60);

    @Test
    @DisplayName("a token round-trips the user's identity and role")
    void roundTrip() {
        User manager = user(7L, Role.MANAGER, "Priya Raman");
        String token = jwt.generateToken(principal(manager));

        Claims claims = jwt.parse(token);
        assertThat(claims.getSubject()).isEqualTo(manager.getEmail());
        assertThat(claims.get("role", String.class)).isEqualTo("MANAGER");
        assertThat(claims.get("name", String.class)).isEqualTo("Priya Raman");
        assertThat(claims.get("uid", Number.class).longValue()).isEqualTo(7L);
        assertThat(jwt.extractUsername(token)).isEqualTo(manager.getEmail());
    }

    @Test
    @DisplayName("the token carries an issue and expiry time")
    void tokenIsTimeLimited() {
        String token = jwt.generateToken(principal(user(1L, Role.TECHNICIAN, "Sam Okafor")));
        Claims claims = jwt.parse(token);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(jwt.getExpirationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("a token signed with another key is refused")
    void foreignSignatureIsRefused() {
        JwtService attacker = serviceWith(OTHER_SECRET, 60);
        String forged = attacker.generateToken(principal(user(99L, Role.MANAGER, "Not Real")));

        assertThatThrownBy(() -> jwt.parse(forged)).isInstanceOf(JwtException.class);
        assertThat(jwt.extractUsername(forged)).isNull();
    }

    @Test
    @DisplayName("a tampered token is refused")
    void tamperedTokenIsRefused() {
        String token = jwt.generateToken(principal(user(1L, Role.TECHNICIAN, "Sam Okafor")));
        // Flip the last character of the signature.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(jwt.extractUsername(tampered)).isNull();
    }

    @Test
    @DisplayName("an expired token is refused")
    void expiredTokenIsRefused() {
        JwtService stale = serviceWith(SECRET, -5);   // issued already expired
        String token = stale.generateToken(principal(user(1L, Role.MANAGER, "Priya Raman")));

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(JwtException.class);
        assertThat(jwt.extractUsername(token)).isNull();
    }

    @Test
    @DisplayName("garbage is refused without throwing at the caller")
    void garbageIsRefusedQuietly() {
        assertThat(jwt.extractUsername("not-a-token")).isNull();
        assertThat(jwt.extractUsername("")).isNull();
        assertThat(jwt.extractUsername("a.b.c")).isNull();
    }

    @Test
    @DisplayName("a secret too short for HS256 fails fast at startup")
    void weakSecretFailsFast() {
        assertThatThrownBy(() -> serviceWith("too-short", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    private static JwtService serviceWith(String secret, long minutes) {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt(secret, minutes),
                new AppProperties.Cors(List.of("http://localhost:5173")));
        return new JwtService(props);
    }
}
