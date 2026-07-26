package com.fashionapp.domain.diagnosis;

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
@RequestMapping("/api/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StyleDiagnosisResponse>> diagnose(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.diagnose(userPrincipal.getId(), image)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StyleDiagnosisResponse>>> getMyDiagnoses(
            @CurrentUser UserPrincipal userPrincipal) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.getMyDiagnoses(userPrincipal.getId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StyleDiagnosisResponse>> getDiagnosis(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(diagnosisService.getDiagnosis(userPrincipal.getId(), id)));
    }
}
