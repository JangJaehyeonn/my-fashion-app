package com.fashionapp.domain.shopping;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import com.fashionapp.infra.AiShoppingRecommendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
public class ShoppingController {

    private final ShoppingService shoppingService;

    @PostMapping("/recommend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AiShoppingRecommendResponse>> recommend(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody ShoppingRecommendRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                shoppingService.recommend(userPrincipal.getId(), request)
        ));
    }
}
