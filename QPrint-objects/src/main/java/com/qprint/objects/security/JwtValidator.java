package com.qprint.objects.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    public Collection<? extends GrantedAuthority> extractAuthorities(Claims claims) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        String role = claims.get("role", String.class);
        if (StringUtils.hasText(role)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase(Locale.ROOT)));
        }

        Object scopes = claims.get("scopes");
        if (scopes instanceof Collection<?> scopeCollection) {
            for (Object scope : scopeCollection) {
                if (scope != null && StringUtils.hasText(scope.toString())) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.toString().trim()));
                }
            }
        } else if (scopes instanceof String scopeString && StringUtils.hasText(scopeString)) {
            for (String scope : scopeString.split("[ ,]+")) {
                if (StringUtils.hasText(scope)) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.trim()));
                }
            }
        }

        return authorities;
    }
}


