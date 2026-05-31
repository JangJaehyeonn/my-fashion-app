package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clothes")
@RequiredArgsConstructor
public class ClothesController {

    private final ClothesService clothesService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ClothesResponse>>> getMyClothes(
            @CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.getMyClothes(userPrincipal.getId())));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClothesResponse>> upload(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.upload(userPrincipal.getId(), image)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClothesResponse>> getClothes(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.getClothes(userPrincipal.getId(), id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClothesResponse>> update(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id,
            @RequestBody ClothesUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.update(userPrincipal.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> delete(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id) {
        clothesService.delete(userPrincipal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
