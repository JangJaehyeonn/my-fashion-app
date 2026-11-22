package com.fashionapp.domain.wear;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wears")
@RequiredArgsConstructor
public class WearController {

    private final WearService wearService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WearRecordResponse>> record(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody WearRequest request) {
        return ResponseEntity.ok(ApiResponse.success(wearService.record(userPrincipal.getId(), request)));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WearSummaryResponse>> summary(
            @CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(wearService.summary(userPrincipal.getId())));
    }
}
