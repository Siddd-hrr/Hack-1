package com.qprint.otp.controller;

import com.qprint.otp.dto.GenerateOtpRequest;
import com.qprint.otp.dto.OtpVerificationResult;
import com.qprint.otp.dto.VerifyOtpRequest;
import com.qprint.otp.model.ApiResponse;
import com.qprint.otp.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyAuthority('SCOPE_otp:write', 'ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<Map<String, String>>> generate(@Valid @RequestBody GenerateOtpRequest request) {
        String otp = otpService.generate(request.orderId(), request.userId());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("otp", otp), "OTP generated"));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyAuthority('SCOPE_otp:write', 'ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<OtpVerificationResult>> verify(@Valid @RequestBody VerifyOtpRequest request) {
        OtpVerificationResult result = otpService.verify(request.orderId(), request.otp());
        return ResponseEntity.ok(ApiResponse.ok(result, result.message()));
    }
}
