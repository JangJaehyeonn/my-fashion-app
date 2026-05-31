package com.fashionapp.domain.outfit;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/outfits")
@RequiredArgsConstructor
public class OutfitController {

    private final OutfitService outfitService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OutfitResponse>>> getMyOutfits(
            @CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(outfitService.getMyOutfits(userPrincipal.getId())));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OutfitResponse>> createOutfit(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody OutfitCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(outfitService.createOutfit(userPrincipal.getId(), request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteOutfit(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id) {
        outfitService.deleteOutfit(userPrincipal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
