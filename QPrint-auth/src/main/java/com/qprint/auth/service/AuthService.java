package com.qprint.auth.service;

import com.qprint.auth.dto.AuthDtos.*;
import com.qprint.auth.entity.EmailVerificationCode;
import com.qprint.auth.entity.PasswordResetToken;
import com.qprint.auth.entity.RefreshToken;
import com.qprint.auth.entity.User;
import com.qprint.auth.model.ApiResponse;
import com.qprint.auth.repository.EmailVerificationCodeRepository;
import com.qprint.auth.repository.PasswordResetTokenRepository;
import com.qprint.auth.repository.RefreshTokenRepository;
import com.qprint.auth.repository.UserRepository;
import com.qprint.auth.util.OtpGenerator;
import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final List<String> STUDENT_SCOPES = List.of(
            "objects:read",
            "objects:write",
            "cart:read",
            "cart:write",
            "checkout:read",
            "checkout:write",
            "orders:read",
            "orders:write",
            "transactions:read",
            "shops:read"
    );

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationCodeRepository verificationCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final KafkaPublisher kafkaPublisher;
    private final MailService mailService;
    private final OtpGenerator otpGenerator;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiration-days}")
    private int refreshTokenDays;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.cookie.same-site:Lax}")
    private String cookieSameSite;

    @Transactional
    public ApiResponse<AuthResponse> register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setAccountStatus(User.AccountStatus.PENDING);
        userRepository.save(user);

        mailService.sendHtml(user.getEmail(), "QPrint account created", Templates.accountCreatedEmail(user.getFirstName()));
        sendVerificationCode(user, true);
        kafkaPublisher.publish("user.registered", Map.of("userId", user.getId(), "email", user.getEmail()));
        return ApiResponse.ok(new AuthResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), "STUDENT", null, false),
                "Registration successful. Please check your email for the verification code.");
    }

    @Transactional
    public ApiResponse<Void> verifyEmail(VerifyEmailRequest request) {
        UUID userId = Objects.requireNonNull(request.userId(), "userId is required");
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Optional<EmailVerificationCode> latest = verificationCodeRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (latest.isEmpty()) throw new IllegalArgumentException("No verification code found");
        EmailVerificationCode code = latest.get();
        if (code.isUsed()) throw new IllegalArgumentException("Code already used");
        if (code.getExpiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("Code expired. Please request a new one.");
        if (!code.getCode().equals(request.code())) throw new IllegalArgumentException("Invalid code");

        code.setUsed(true);
        user.setEmailVerified(true);
        user.setAccountStatus(User.AccountStatus.ACTIVE);
        verificationCodeRepository.save(code);
        userRepository.save(user);
        kafkaPublisher.publish("user.verified", Map.of("userId", user.getId()));
        mailService.sendHtml(user.getEmail(), "Your QPrint account is now active! 🎉", Templates.verifiedEmail(user.getFirstName()));
        return ApiResponse.ok(null, "Email verified successfully. You can now log in.");
    }

    @Transactional
    public ApiResponse<Void> resendVerification(ResendVerificationRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.isEmailVerified()) {
            return ApiResponse.ok(null, "Email already verified");
        }
        try {
            String key = "resend:" + user.getEmail();
            Long count = redisTemplate.opsForValue().increment(key);
            java.time.Duration resendTtl = java.time.Duration.ofHours(1);
            redisTemplate.expire(key, Objects.requireNonNull(resendTtl, "resendTtl"));
            if (count != null && count > 3) {
                throw new IllegalArgumentException("Too many resend attempts. Try later.");
            }
        } catch (Exception ex) {
            log.warn("Resend rate-limit check failed for {}", user.getEmail(), ex);
        }
        sendVerificationCode(user, false);
        return ApiResponse.ok(null, "New verification code sent.");
    }

    @Transactional
    public com.qprint.auth.model.AuthResult<AuthResponse> login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        if (!user.isEmailVerified()) {
            ApiResponse<AuthResponse> resp = ApiResponse.ok(
                new AuthResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), "STUDENT", null, true),
                "Please verify your email first."
            );
            return new com.qprint.auth.model.AuthResult<>(resp, null);
        }
        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new BadCredentialsException("Account not active");
        }
        String accessToken = issueAccessToken(user);
        ResponseCookie refreshCookie = issueRefreshToken(user);
        kafkaPublisher.publish("user.login", Map.of("userId", user.getId()));
        ApiResponse<AuthResponse> resp = new ApiResponse<>(true, new AuthResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), "STUDENT", accessToken, false), "Login successful", Instant.now());
        return new com.qprint.auth.model.AuthResult<>(resp, refreshCookie);
    }

    public String issueAccessToken(User user) {
        Map<String, Object> claims = Map.of(
                "sub", user.getId().toString(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "role", "STUDENT",
                "scopes", STUDENT_SCOPES
        );
        return jwtService.generateAccessToken(claims);
    }

    @Transactional
    public ResponseCookie issueRefreshToken(User user) {
        String rawToken = Objects.requireNonNull(UUID.randomUUID().toString(), "refresh token");
        String hashed = sha256(rawToken);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hashed);
        token.setExpiresAt(Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS));
        refreshTokenRepository.deleteByUserOrExpired(user, Instant.now());
        refreshTokenRepository.save(token);
        java.time.Duration refreshTtl = java.time.Duration.ofDays(refreshTokenDays);
        return ResponseCookie.from("refreshToken", rawToken)
                .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Objects.requireNonNull(refreshTtl, "refreshTtl"))
                .build();
    }

    @Transactional
    public com.qprint.auth.model.AuthResult<AuthResponse> refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("No refresh token");
        }
        String hashed = sha256(rawToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashed)
            .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new IllegalArgumentException("Refresh token expired");
        }
        User user = token.getUser();
        String accessToken = issueAccessToken(user);
        ResponseCookie newRefresh = issueRefreshToken(user);
        ApiResponse<AuthResponse> resp = new ApiResponse<>(true, new AuthResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), "STUDENT", accessToken, false), "Refreshed", Instant.now());
        return new com.qprint.auth.model.AuthResult<>(resp, newRefresh);
    }

    @Transactional
    public ApiResponse<Void> logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            String hashed = sha256(rawToken);
            refreshTokenRepository.findByTokenHash(hashed).ifPresent(refreshTokenRepository::delete);
        }
        return ApiResponse.ok(null, "Logged out");
    }

    @Transactional
    public ApiResponse<Void> forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email().toLowerCase()).ifPresent(user -> {
            String raw = UUID.randomUUID().toString();
            String hashed = sha256(raw);
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(hashed);
            token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            passwordResetTokenRepository.deleteByUserOrExpired(user, Instant.now());
            passwordResetTokenRepository.save(token);
            String link = frontendBaseUrl + "/reset-password?token=" + raw;
            mailService.sendHtml(user.getEmail(), "Reset your QPrint password", Templates.resetEmail(user.getFirstName(), link));
        });
        return ApiResponse.ok(null, "If that email is registered, a reset link has been sent.");
    }

    @Transactional
    public ApiResponse<Void> resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        String hashed = sha256(request.token());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hashed)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token expired or used");
        }
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        token.setUsed(true);
        userRepository.save(user);
        passwordResetTokenRepository.save(token);
        mailService.sendHtml(user.getEmail(), "Your QPrint password has been reset", Templates.passwordChangedEmail(user.getFirstName()));
        return ApiResponse.ok(null, "Password reset successfully");
    }

    public ApiResponse<Map<String, Object>> me(User user) {
        Map<String, Object> profile = Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "role", "STUDENT",
                "createdAt", user.getCreatedAt()
        );
        return ApiResponse.ok(profile, "Profile fetched");
    }

    public ApiResponse<Map<String, Object>> updateProfile(User user, ProfileUpdateRequest request) {
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        userRepository.save(user);
        return me(user);
    }

    public ApiResponse<Void> changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password incorrect");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return ApiResponse.ok(null, "Password changed");
    }

    private void sendVerificationCode(User user, boolean includeWelcome) {
        verificationCodeRepository.invalidateUnused(user);
        String code = otpGenerator.sixDigit();
        EmailVerificationCode ev = new EmailVerificationCode();
        ev.setUser(user);
        ev.setCode(code);
        ev.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        verificationCodeRepository.save(ev);
        log.info("DEV OTP for {}: {}", user.getEmail(), code);
        if (includeWelcome) {
            mailService.sendHtml(user.getEmail(), "Welcome to QPrint", Templates.welcomeEmail(user.getFirstName(), code));
        }
        mailService.sendHtml(user.getEmail(), "Your Verification Code", Templates.verificationCodeEmail(code));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash value", e);
        }
    }

    public User resolveUserFromClaims(Claims claims) {
        UUID id = Objects.requireNonNull(jwtService.extractUserId(claims), "userId is required");
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
