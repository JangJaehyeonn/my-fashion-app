package com.fashionapp.domain.user;

import com.fashionapp.domain.auth.LoginAttemptLimiter;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.consent.ConsentService;
import com.fashionapp.domain.diagnosis.StyleDiagnosisRepository;
import com.fashionapp.domain.vton.VtonService;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 회원 탈퇴 (DELETE /api/users/me).
 *
 * <p>아이디·비밀번호(local) 사용자도 같은 흐름으로 삭제한다 — login_id가 users 행과 함께 사라지므로 같은 아이디로 다시 가입할 수 있다.
 *
 * <p>삭제 대상: 옷(clothes) 행·사진, 진단 기록(style_diagnoses) 행·사진, 동의 이력(consent_records) 행, 가상 피팅 결과 사진(vton/{userId}/),
 * Redis 리프레시 토큰, 사용자 행. 코디 추천은 저장하지 않고(요청마다 생성) 피팅 결과도 DB 기록이 없어 지울 행은 없다.
 *
 * <p>순서와 실패 처리:
 * <ol>
 *   <li><b>DB (한 트랜잭션)</b> — 이미지 URL을 먼저 모은 뒤 옷 → 진단 → 사용자 순으로 삭제(FK 순서). 하나라도 실패하면
 *       전부 롤백되고 요청은 실패(500) → 사용자는 그대로 남아 다시 시도할 수 있다. 이미지 삭제보다 먼저 하는 이유: DB가 기준 데이터라,
 *       이미지를 먼저 지웠는데 DB가 실패하면 "계정은 남았는데 사진만 사라진" 상태가 되기 때문.</li>
 *   <li><b>Redis 리프레시 토큰</b> — 커밋 후 삭제. 실패해도 요청은 성공 처리: 재발급(/api/auth/refresh)이 사용자 존재를
 *       확인하므로 남은 토큰으로는 새 토큰을 받을 수 없고, 액세스 토큰도 필터가 사용자를 못 찾아 401이 된다.</li>
 *   <li><b>이미지 파일</b> — 커밋 후 best-effort. 실패해도 DB를 되돌리지 않는다(이미 커밋됨, 되살리면 탈퇴 요청이 실패한 셈).
 *       남은 객체는 DB 행이 없어 서버가 더 이상 presigned URL을 발급하지 않으므로 외부에서 접근할 수 없다
 *       (이미 발급된 URL은 최대 7일 뒤 만료). 실패한 키는 로그로 남겨 수동 정리 대상으로 삼는다.</li>
 * </ol>
 * 이미지 삭제가 커밋 뒤라 트랜잭션 밖에서 실행되므로, 느린 저장소 호출 동안 DB 커넥션을 붙잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final ClothesRepository clothesRepository;
    private final StyleDiagnosisRepository styleDiagnosisRepository;
    private final ImageStorage imageStorage;
    private final RedisTemplate<String, String> redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final ConsentService consentService;

    public void deleteAccount(UUID userId) {
        // 1) DB — 실패 시 전부 롤백
        String[] loginId = new String[1];
        List<String> imageUrls = transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            loginId[0] = user.getLoginId();
            List<String> urls = new ArrayList<>(clothesRepository.findImageUrlsByUserId(userId));
            urls.addAll(styleDiagnosisRepository.findImageUrlsByUserId(userId));

            clothesRepository.deleteAllByUserId(userId);
            styleDiagnosisRepository.deleteAllByUserId(userId);
            consentService.deleteAll(userId);
            userRepository.deleteById(userId);
            return urls;
        });

        // 2) Redis 리프레시 토큰 — 실패해도 재발급 시 사용자 존재 확인으로 막힘.
        //    아이디 가입 사용자면 로그인 실패·잠금 기록도 지워, 같은 아이디로 재가입했을 때 이전 기록이 남지 않게 함
        try {
            redisTemplate.delete("refresh:" + userId);
            if (loginId[0] != null) {
                loginAttemptLimiter.clear(loginId[0]);
            }
        } catch (RuntimeException e) {
            log.warn("account.delete userId={} refresh token delete failed: {}", userId, e.getMessage());
        }

        // 3) 이미지 파일 — best-effort
        int failed = 0;
        for (String url : imageUrls) {
            try {
                imageStorage.delete(url);
            } catch (RuntimeException e) {
                failed++;
                log.warn("account.delete userId={} image delete failed url={}: {}", userId, url, e.getMessage());
            }
        }
        String vtonPrefix = VtonService.storageDir(userId) + "/";
        try {
            imageStorage.deleteByPrefix(vtonPrefix);
        } catch (RuntimeException e) {
            failed++;
            log.warn("account.delete userId={} image prefix delete failed prefix={}: {}", userId, vtonPrefix, e.getMessage());
        }
        log.info("account.delete userId={} images={} s3Failures={}", userId, imageUrls.size(), failed);
    }
}
