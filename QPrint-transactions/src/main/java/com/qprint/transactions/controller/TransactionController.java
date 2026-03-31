package com.qprint.transactions.controller;

import com.qprint.transactions.dto.PageResponse;
import com.qprint.transactions.dto.TransactionResponse;
import com.qprint.transactions.model.ApiResponse;
import com.qprint.transactions.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_transactions:read')")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> list(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<TransactionResponse> response = transactionService.list(parse(userIdHeader), page, size);
        return ResponseEntity.ok(ApiResponse.ok(response, "Transactions fetched"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_transactions:read')")
    public ResponseEntity<ApiResponse<TransactionResponse>> get(
            @RequestHeader("X-User-Id") String userIdHeader,
            @PathVariable UUID id
    ) {
        TransactionResponse tx = transactionService.get(id, parse(userIdHeader));
        return ResponseEntity.ok(ApiResponse.ok(tx, "Transaction fetched"));
    }

    private UUID parse(String header) {
        return UUID.fromString(header.trim());
    }
}
