package com.java.ecommerce.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class JwtService {

    private final SecurityProperties securityProperties;
    private SecretKey signingKey;

    public JwtService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @PostConstruct
    void initialize() {
        byte[] secretBytes = securityProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("APP_SECURITY_JWT_SECRET must be at least 32 characters.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issueToken(String username, Collection<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(securityProperties.getJwtTtlSeconds());

        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Authentication extractAuthentication(String token) {
        Claims claims = parseClaims(token);
        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new JwtException("Token subject is missing.");
        }

        Object rawRoles = claims.get("roles");
        List<String> roles = rawRoles instanceof Collection<?> collection
                ? collection.stream().filter(Objects::nonNull).map(Object::toString).toList()
                : List.of();

        List<GrantedAuthority> authorities = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        return new UsernamePasswordAuthenticationToken(subject, token, authorities);
    }

    public long getTokenTtlSeconds() {
        return securityProperties.getJwtTtlSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
