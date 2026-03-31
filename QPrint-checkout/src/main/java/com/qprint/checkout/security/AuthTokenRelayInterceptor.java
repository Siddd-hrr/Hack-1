package com.qprint.checkout.security;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class AuthTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    private static final List<String> CHECKOUT_SERVICE_SCOPES = List.of("orders:write");

    private final ServiceJwtTokenProvider serviceJwtTokenProvider;

    @Override
    public @NonNull ClientHttpResponse intercept(@NonNull HttpRequest request,
                                                 @NonNull byte[] body,
                                                 @NonNull ClientHttpRequestExecution execution)
            throws IOException {
        String token = resolveToken();
        if (!StringUtils.hasText(token)) {
            token = serviceJwtTokenProvider.issueServiceToken(CHECKOUT_SERVICE_SCOPES);
        }
        request.getHeaders().setBearerAuth(Objects.requireNonNull(token, "token"));
        return execution.execute(request, body);
    }

    private String resolveToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object credentials = authentication.getCredentials();
            if (credentials instanceof String token && StringUtils.hasText(token)) {
                return token;
            }
        }

        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            String header = servletRequestAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }

        return null;
    }
}
