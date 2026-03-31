package com.qprint.cart.controller;

import com.qprint.cart.dto.AddItemRequest;
import com.qprint.cart.dto.CartSummaryDto;
import com.qprint.cart.model.ApiResponse;
import com.qprint.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_cart:read')")
    public ResponseEntity<ApiResponse<CartSummaryDto>> getCart(@RequestHeader("X-User-Id") String userIdHeader) {
        CartSummaryDto summary = cartService.getCart(parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(summary, "Cart fetched"));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAuthority('SCOPE_cart:read')")
    public ResponseEntity<ApiResponse<Integer>> count(@RequestHeader("X-User-Id") String userIdHeader) {
        int count = cartService.count(parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(count, "Count fetched"));
    }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('SCOPE_cart:write')")
    public ResponseEntity<ApiResponse<CartSummaryDto>> add(
            @RequestHeader("X-User-Id") String userIdHeader,
            @Valid @RequestBody AddItemRequest request
    ) {
        CartSummaryDto summary = cartService.addItem(parse(userIdHeader), request);
        return ResponseEntity.ok(ApiResponse.ok(summary, "Item added"));
    }

    @DeleteMapping("/item/{objectId}")
    @PreAuthorize("hasAuthority('SCOPE_cart:write')")
    public ResponseEntity<ApiResponse<CartSummaryDto>> delete(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID objectId
    ) {
        CartSummaryDto summary = cartService.removeItem(parse(userIdHeader), objectId);
        return ResponseEntity.ok(ApiResponse.ok(summary, "Item removed"));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('SCOPE_cart:write')")
    public ResponseEntity<ApiResponse<Void>> clear(@RequestHeader("X-User-Id") String userIdHeader) {
        cartService.clear(parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(null, "Cart cleared"));
    }

    private UUID parse(String userIdHeader) {
        return UUID.fromString(userIdHeader.trim());
    }
}
