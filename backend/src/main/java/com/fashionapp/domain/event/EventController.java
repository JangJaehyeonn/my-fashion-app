package com.fashionapp.domain.event;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.common.ApiResponse;
import com.fashionapp.global.jwt.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /** FR-14 APP_OPENED: no body, always 200 (recording is best-effort). */
    @PostMapping("/app-open")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> appOpen(@CurrentUser UserPrincipal userPrincipal) {
        eventService.appOpened(userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
