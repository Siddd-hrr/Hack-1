package com.qprint.otp.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtValidator.validate(token);
            String subject = claims.getSubject();
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    subject, token, jwtValidator.extractAuthorities(claims));
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            HttpServletRequest requestForChain = request;
            if (isUuid(subject)) {
                MutableHeaderHttpServletRequest mutableRequest = new MutableHeaderHttpServletRequest(request);
                mutableRequest.putHeader("X-User-Id", subject);
                requestForChain = mutableRequest;
            }
            filterChain.doFilter(requestForChain, response);
            return;
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static final class MutableHeaderHttpServletRequest extends HttpServletRequestWrapper {

        private final Map<String, List<String>> customHeaders = new LinkedHashMap<>();

        private MutableHeaderHttpServletRequest(HttpServletRequest request) {
            super(request);
        }

        private void putHeader(String name, String value) {
            customHeaders.put(name, new ArrayList<>(List.of(value)));
        }

        @Override
        public String getHeader(String name) {
            List<String> values = customHeaders.get(name);
            if (values != null && !values.isEmpty()) {
                return values.get(0);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> values = customHeaders.get(name);
            if (values != null) {
                return Collections.enumeration(values);
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(customHeaders.keySet());
            Enumeration<String> headerNames = super.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                names.add(headerNames.nextElement());
            }
            return Collections.enumeration(names);
        }
    }
}


