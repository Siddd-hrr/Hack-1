package com.qprint.objects.controller;

import com.qprint.objects.dto.PrintObjectResponse;
import com.qprint.objects.model.ApiResponse;
import com.qprint.objects.service.ObjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/objects")
@RequiredArgsConstructor
public class ObjectsController {

    private final ObjectService objectService;

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    @PreAuthorize("hasAuthority('SCOPE_objects:write')")
    public ResponseEntity<ApiResponse<PrintObjectResponse>> upload(
            @RequestHeader("X-User-Id") String userId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("preferences") String preferences
    ) throws Exception {
        PrintObjectResponse response = objectService.upload(userId, file, preferences);
        return ResponseEntity.status(201).body(ApiResponse.ok(response, "Uploaded and priced"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_objects:read')")
    public ResponseEntity<ApiResponse<PrintObjectResponse>> getOne(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") String userId
    ) {
        PrintObjectResponse response = objectService.get(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(response, "Print object fetched"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_objects:write')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") String userId
    ) throws Exception {
        objectService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Print object deleted"));
    }
}
