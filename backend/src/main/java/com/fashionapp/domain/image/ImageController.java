package com.fashionapp.domain.image;

import com.fashionapp.domain.user.UserPrincipal;
import com.fashionapp.global.jwt.CurrentUser;
import com.fashionapp.infra.ImageKeys;
import com.fashionapp.infra.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * 로컬 디스크 저장소(storage.type=local)의 이미지를 내려준다. GET /api/images/{category}/{ownerId}/{file}
 *
 * <p>인증 필수, 그리고 키의 소유자 id가 로그인한 사용자와 같을 때만 응답한다. 남의 이미지·없는 이미지·형식이
 * 틀린 경로는 모두 같은 404 — 존재 여부를 알려주지 않는다. 경로는 정규식으로 키 형식을 통과해야만
 * 파일 시스템에 닿는다(../ 불가).
 */
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class ImageController {

    private final ImageStorage storage;

    @GetMapping("/{category}/{ownerId}/{filename:.+}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> get(@CurrentUser UserPrincipal principal,
                                        @PathVariable String category,
                                        @PathVariable String ownerId,
                                        @PathVariable String filename) {
        String key = category + "/" + ownerId + "/" + filename;
        UUID owner = ImageKeys.ownerOf(key);
        if (owner == null || !owner.equals(principal.getId())) {
            return ResponseEntity.notFound().build();
        }
        return storage.load(key)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(ImageKeys.contentTypeOf(key)))
                        .header("X-Content-Type-Options", "nosniff")
                        // 파일명이 UUID라 내용이 바뀌지 않는다. 인증이 필요한 응답이므로 공유 캐시는 금지
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
