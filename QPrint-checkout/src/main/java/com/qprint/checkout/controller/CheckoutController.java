package com.qprint.checkout.controller;

import com.qprint.checkout.dto.CheckoutSummaryDto;
import com.qprint.checkout.dto.InitiateCheckoutResponse;
import com.qprint.checkout.model.ApiResponse;
import com.qprint.checkout.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/initiate")
        @PreAuthorize("hasAuthority('SCOPE_checkout:write')")
    public ResponseEntity<ApiResponse<InitiateCheckoutResponse>> initiate(
            @RequestHeader("X-User-Id") String userIdHeader
    ) {
        InitiateCheckoutResponse response = checkoutService.initiate(parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(response, "Checkout initiated"));
    }

    @GetMapping("/{checkoutId}")
    @PreAuthorize("hasAuthority('SCOPE_checkout:read')")
    public ResponseEntity<ApiResponse<CheckoutSummaryDto>> status(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID checkoutId
    ) {
        CheckoutSummaryDto summary = checkoutService.getCheckout(checkoutId, parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(summary, "Checkout fetched"));
    }

    @GetMapping("/status/{razorpayOrderId}")
    @PreAuthorize("hasAuthority('SCOPE_checkout:read')")
    public ResponseEntity<ApiResponse<CheckoutSummaryDto>> statusByRazorpay(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable String razorpayOrderId
    ) {
        CheckoutSummaryDto summary = checkoutService.getCheckoutByRazorpayOrderId(razorpayOrderId, parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(summary, "Checkout fetched"));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String payload
    ) {
        checkoutService.handleWebhook(payload, signature);
        return ResponseEntity.ok("ok");
    }

    private UUID parse(String userIdHeader) {
        return UUID.fromString(userIdHeader.trim());
    }
}
