package com.fashionapp.domain.outfit;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import com.fashionapp.infra.AiSituationRecommendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/outfits")
@RequiredArgsConstructor
public class OutfitController {

    private final OutfitService outfitService;

    @PostMapping("/recommend/situation")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AiSituationRecommendResponse>> recommendBySituation(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody SituationRecommendRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                outfitService.recommendBySituation(userPrincipal.getId(), request)
        ));
    }
}
