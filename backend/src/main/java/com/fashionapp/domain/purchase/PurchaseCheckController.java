package com.fashionapp.domain.purchase;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/purchase-checks")
@RequiredArgsConstructor
public class PurchaseCheckController {

    private final PurchaseCheckService purchaseCheckService;

    /** FR-11: compare a photographed piece with the closet. price (won) is optional. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PurchaseCheckResponse>> check(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "price", required = false) String price) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseCheckService.check(userPrincipal.getId(), image, price)));
    }
}
