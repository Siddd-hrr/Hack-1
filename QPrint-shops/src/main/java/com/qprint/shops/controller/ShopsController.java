package com.qprint.shops.controller;

import com.qprint.shops.dto.ShopDto;
import com.qprint.shops.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shops")
public class ShopsController {

    @GetMapping("/nearby")
        @PreAuthorize("hasAuthority('SCOPE_shops:read')")
    public ResponseEntity<ApiResponse<List<ShopDto>>> nearby(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        List<ShopDto> shops = List.of(
                new ShopDto(
                        "SHOP_001",
                        "Campus Print Zone",
                        "Near Gate 2, University Road",
                        20,
                        true,
                        "50m",
                        4.5,
                        1200
                )
        );
        return ResponseEntity.ok(ApiResponse.ok(shops, "Nearby shops fetched"));
    }
}
