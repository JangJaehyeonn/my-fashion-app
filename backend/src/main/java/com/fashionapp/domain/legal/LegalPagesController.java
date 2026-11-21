package com.fashionapp.domain.legal;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 로그인 없이 열리는 법적 고지 페이지(이용약관, 개인정보처리방침, 계정 삭제 안내). 앱의 약관·처리방침 링크와 Play Store의
 * 개인정보처리방침·계정 삭제 URL로 쓴다. 정본은 classpath의 legal/*.html. 보안 설정에서 이 세 경로만 permitAll.
 *
 * <p>정적 리소스 대신 컨트롤러로 내려주는 이유: Content-Type에 charset=UTF-8을 정확히 붙이면서도
 * 인코딩 강제 설정(이미지 응답에 charset이 붙음)을 전역으로 켜지 않기 위함.
 */
@RestController
public class LegalPagesController {

    private static final MediaType HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final String terms = read("legal/terms.html");
    private final String privacy = read("legal/privacy.html");
    private final String accountDeletion = read("legal/account-deletion.html");

    @GetMapping("/terms")
    public ResponseEntity<String> terms() {
        return page(terms);
    }

    @GetMapping("/privacy")
    public ResponseEntity<String> privacy() {
        return page(privacy);
    }

    @GetMapping("/account-deletion")
    public ResponseEntity<String> accountDeletion() {
        return page(accountDeletion);
    }

    private static ResponseEntity<String> page(String html) {
        return ResponseEntity.ok().contentType(HTML_UTF8)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(html);
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("legal page missing: " + path, e);
        }
    }
}
