package com.qprint.orders.controller;

import com.qprint.orders.dto.OrderResponse;
import com.qprint.orders.dto.UpdateOrderStatusRequest;
import com.qprint.orders.model.ApiResponse;
import com.qprint.orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyAuthority('SCOPE_orders:manage', 'ROLE_SERVICE')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatusInternal(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        OrderResponse order = orderService.updateStatus(orderId, request.status());
        return ResponseEntity.ok(ApiResponse.ok(order, "Order status updated"));
    }
}
