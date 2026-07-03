package com.company.core.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JWT 토큰 발급/검증 유틸
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final Key key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:kleannara-tms-secret-key-must-be-32-bytes!!}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.key          = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /** 토큰 생성 */
    public String createToken(String username, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 토큰에서 사용자명 추출 */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** 토큰에서 권한 추출 */
    public String getRole(String token) {
        return (String) parseClaims(token).get("role");
    }

    /** 토큰 유효성 검증 */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JwtTokenProvider] Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
