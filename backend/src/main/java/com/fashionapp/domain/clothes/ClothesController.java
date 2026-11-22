package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClothesResponse>> register(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam("image") MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(clothesService.register(userPrincipal.getId(), image)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ClothesResponse>>> getMyClothes(
            @CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.getMyClothes(userPrincipal.getId())));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClosetSummaryResponse>> getSummary(
            @CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.getSummary(userPrincipal.getId())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClothesResponse>> update(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id,
            @RequestBody ClothesUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.update(userPrincipal.getId(), id, request)));
    }

    @PostMapping("/{id}/reclassify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ClothesResponse>> reclassify(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(clothesService.reclassify(userPrincipal.getId(), id)));
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
