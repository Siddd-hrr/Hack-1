package com.qprint.checkout.security;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServiceJwtTokenProvider {

    private final Key signingKey;
    private final String issuer;
    private final String audience;
    private final String serviceName;

    public ServiceJwtTokenProvider(@Value("${jwt.secret}") String secret,
                                   @Value("${jwt.issuer}") String issuer,
                                   @Value("${jwt.audience}") String audience,
                                   @Value("${spring.application.name:qprint-checkout}") String serviceName) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.serviceName = serviceName;
    }

    public String issueServiceToken(Collection<String> scopes) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "SERVICE");
        claims.put("scopes", scopes == null ? List.of() : scopes);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("service:" + serviceName)
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(3, ChronoUnit.MINUTES)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
