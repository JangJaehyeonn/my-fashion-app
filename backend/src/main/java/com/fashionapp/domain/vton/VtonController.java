package com.fashionapp.domain.vton;

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
@RequestMapping("/api/vton")
@RequiredArgsConstructor
public class VtonController {

    private final VtonService vtonService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VtonResponse>> tryOn(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam("personImage") MultipartFile personImage,
            @RequestParam("garmentImage") MultipartFile garmentImage,
            @RequestParam(value = "garmentDesc", required = false, defaultValue = "") String garmentDesc) {
        return ResponseEntity.ok(ApiResponse.success(
                vtonService.tryOn(userPrincipal.getId(), personImage, garmentImage, garmentDesc)
        ));
    }}
