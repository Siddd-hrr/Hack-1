package com.qprint.orders.controller;

import com.qprint.orders.dto.CreateOrderRequest;
import com.qprint.orders.dto.OrderResponse;
import com.qprint.orders.dto.UpdateOrderStatusRequest;
import com.qprint.orders.model.ApiResponse;
import com.qprint.orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:write')")
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @RequestHeader("X-User-Id") String userIdHeader,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.create(parse(userIdHeader), request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Order created"));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> list(@RequestHeader("X-User-Id") String userIdHeader) {
        List<OrderResponse> orders = orderService.list(parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(orders, "Orders fetched"));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listActive(@RequestHeader("X-User-Id") String userIdHeader) {
        List<OrderResponse> orders = orderService.listActive(parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(orders, "Active orders fetched"));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public ResponseEntity<ApiResponse<OrderResponse>> get(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID orderId
    ) {
        OrderResponse order = orderService.get(orderId, parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(order, "Order fetched"));
    }

    @GetMapping("/active/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public ResponseEntity<ApiResponse<OrderResponse>> getActive(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID orderId
    ) {
        OrderResponse order = orderService.getActive(orderId, parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(order, "Active order fetched"));
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyAuthority('SCOPE_orders:manage', 'ROLE_SHOP', 'ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderResponse order = orderService.updateStatus(orderId, request.status());
        return ResponseEntity.ok(ApiResponse.ok(order, "Order status updated"));
    }

    private UUID parse(String userIdHeader) {
        return UUID.fromString(userIdHeader.trim());
    }
}
