package com.qprint.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator {

    private final Key key;
    private final String expectedIssuer;
    private final String expectedAudience;

    public JwtValidator(@Value("${jwt.secret}") String secret,
                        @Value("${jwt.issuer}") String expectedIssuer,
                        @Value("${jwt.audience}") String expectedAudience) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
    }

    public Claims validate(String token) {
        Jws<Claims> claimsJws = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        if (claims.getExpiration().before(new Date())) {
            throw new IllegalArgumentException("Token expired");
        }
        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new IllegalArgumentException("Invalid token issuer");
        }
        if (!expectedAudience.equals(claims.getAudience())) {
            throw new IllegalArgumentException("Invalid token audience");
        }
        return claims;
    }
}
