package com.agro.authservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final Key secretKey;
    private final Duration accessTtl;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-token.ttl-minutes:60}") long ttlMinutes) {
        byte[] keyBytes = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.UTF_8));
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = Duration.ofMinutes(ttlMinutes);
    }

    public AccessToken generateAccessToken(UUID userId, String email, String role) {
        UUID jti = UUID.randomUUID();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTtl.toMillis());

        String token = Jwts.builder()
                .id(jti.toString())
                .subject(email)
                .claim("role", role)
                .claim("userId", userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();

        return new AccessToken(token, jti, expiry.toInstant());
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((SecretKey) secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (SignatureException e) {
            throw new JwtException("Invalid JWT signature");
        } catch (JwtException e) {
            throw new JwtException("Invalid JWT");
        }
    }

    public void validateToken(String token) {
        parseClaims(token);
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public record AccessToken(String token, UUID jti, java.time.Instant expiresAt) {
    }
}
