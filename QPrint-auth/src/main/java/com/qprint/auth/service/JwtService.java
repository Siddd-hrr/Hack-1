package com.qprint.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final Key signingKey;
    private final int accessTokenExpirationMinutes;
    private final String issuer;
    private final String audience;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-token-expiration-minutes}") int accessTokenExpirationMinutes,
                      @Value("${jwt.issuer}") String issuer,
                      @Value("${jwt.audience}") String audience) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateAccessToken(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject((String) claims.get("sub"))
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }
}
