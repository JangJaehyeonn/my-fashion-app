package com.fashionapp.domain.outfit;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final OutfitService outfitService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<OutfitCalendarResponse>>> getMonthlyCalendar(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int resolvedYear = (year != null) ? year : now.getYear();
        int resolvedMonth = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(ApiResponse.success(
                outfitService.getMonthlyCalendar(userPrincipal.getId(), resolvedYear, resolvedMonth)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OutfitCalendarResponse>> addCalendarEntry(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody OutfitCalendarCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                outfitService.addCalendarEntry(userPrincipal.getId(), request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteCalendarEntry(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable UUID id) {
        outfitService.deleteCalendarEntry(userPrincipal.getId(), id);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
