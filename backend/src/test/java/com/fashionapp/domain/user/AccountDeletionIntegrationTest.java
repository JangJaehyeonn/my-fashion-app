package com.fashionapp.domain.user;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.diagnosis.StyleDiagnosis;
import com.fashionapp.domain.diagnosis.StyleDiagnosisRepository;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.infra.ImageStorage;
import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원 탈퇴: 실제 DB·Redis·필터 체인으로 데이터 삭제와 기존 토큰 무효화를 검증 (S3 삭제는 목) */
class AccountDeletionIntegrationTest extends IntegrationTest {

    private static final String CLOTHES_URL_1 = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/clothes/a.jpg";
    private static final String CLOTHES_URL_2 = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/clothes/b.jpg";
    private static final String DIAGNOSIS_URL = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/diagnosis/c.jpg";

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired ClothesRepository clothesRepository;
    @Autowired StyleDiagnosisRepository styleDiagnosisRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    @MockBean ImageStorage imageStorage;

    private UUID userId;
    private String accessToken;
    private String refreshToken;
    private User otherUser;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(newUser("탈퇴테스트"));
        userId = user.getId();
        clothesRepository.save(Clothes.builder().user(user).imageUrl(CLOTHES_URL_1).category("TOP").build());
        clothesRepository.save(Clothes.builder().user(user).imageUrl(CLOTHES_URL_2).category("BOTTOM").build());
        styleDiagnosisRepository.save(StyleDiagnosis.builder().user(user).imageUrl(DIAGNOSIS_URL).score(80).build());

        accessToken = jwtTokenProvider.createAccessToken(userId);
        refreshToken = jwtTokenProvider.createRefreshToken(userId);
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, 1, TimeUnit.MINUTES);

        // 다른 사용자의 데이터는 그대로 남아야 함
        otherUser = userRepository.save(newUser("다른사용자"));
        clothesRepository.save(Clothes.builder().user(otherUser).imageUrl("https://test-bucket.s3.ap-northeast-2.amazonaws.com/clothes/other.jpg").category("TOP").build());
    }

    @AfterEach
    void tearDown() {
        for (UUID id : new UUID[]{userId, otherUser.getId()}) {
            clothesRepository.deleteAllByUserId(id);
            styleDiagnosisRepository.deleteAllByUserId(id);
            if (userRepository.existsById(id)) userRepository.deleteById(id);
            redisTemplate.delete("refresh:" + id);
        }
    }

    @Test
    @DisplayName("탈퇴하면 옷·진단·사용자·리프레시 토큰이 0건이 되고 S3 이미지 삭제를 요청한다")
    void deletesAllUserData() throws Exception {
        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(clothesRepository.findImageUrlsByUserId(userId)).isEmpty();
        assertThat(styleDiagnosisRepository.findImageUrlsByUserId(userId)).isEmpty();
        assertThat(redisTemplate.hasKey("refresh:" + userId)).isFalse();

        verify(imageStorage).delete(CLOTHES_URL_1);
        verify(imageStorage).delete(CLOTHES_URL_2);
        verify(imageStorage).delete(DIAGNOSIS_URL);
        verify(imageStorage).deleteByPrefix("vton/" + userId + "/");

        // 다른 사용자 데이터는 유지
        assertThat(userRepository.existsById(otherUser.getId())).isTrue();
        assertThat(clothesRepository.findImageUrlsByUserId(otherUser.getId())).hasSize(1);
    }

    @Test
    @DisplayName("탈퇴 후 기존 액세스 토큰으로 API를 부르면 401, 기존 리프레시 토큰으로 재발급도 401")
    void oldTokensRejectedAfterDeletion() throws Exception {
        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Redis 토큰이 남아 있어도(삭제 실패 가정) 탈퇴한 사용자는 재발급 불가")
    void refreshRejectedForDeletedUserEvenIfTokenRemains() throws Exception {
        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, 1, TimeUnit.MINUTES);

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("S3 삭제가 실패해도 탈퇴는 성공하고 DB 삭제는 유지된다")
    void s3FailureDoesNotRollback() throws Exception {
        willThrow(new RuntimeException("S3 down")).given(imageStorage).delete(anyString());
        willThrow(new RuntimeException("S3 down")).given(imageStorage).deleteByPrefix(anyString());

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(clothesRepository.findImageUrlsByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("인증 없이 탈퇴 요청하면 401")
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(delete("/api/users/me")).andExpect(status().isUnauthorized());
        assertThat(userRepository.existsById(userId)).isTrue();
    }

    private static User newUser(String nickname) {
        return User.builder()
                .nickname(nickname)
                .provider(User.AuthProvider.kakao)
                .providerId("delete-test-" + UUID.randomUUID())
                .build();
    }
}
