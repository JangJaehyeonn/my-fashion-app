package com.fashionapp.domain.user;

import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 만 14세 이상 확인: 아이디 가입은 체크 필수, 소셜·기존 사용자는 확인 전까지 대부분의 API 403 */
class AgeGateIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired RedisTemplate<String, String> redisTemplate;

    private final List<UUID> created = new ArrayList<>();
    private final List<String> loginIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
        loginIds.forEach(l -> userRepository.findByLoginId(l).ifPresent(u -> {
            redisTemplate.delete("refresh:" + u.getId());
            userRepository.delete(u);
        }));
    }

    /** 소셜 첫 가입처럼 확인 전 상태의 사용자 */
    private String unconfirmedSocialUserToken() {
        User user = userRepository.save(User.builder().nickname("소셜").provider(User.AuthProvider.kakao)
                .providerId("age-" + UUID.randomUUID()).build());
        created.add(user.getId());
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    private String signupBody(String loginId, String ageOver14) {
        String age = ageOver14 == null ? "" : ",\"ageOver14\":" + ageOver14;
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"secret1234\",\"nickname\":\"테스터\"" + age + "}";
    }

    @Test
    @DisplayName("아이디 가입: 만 14세 이상 체크가 없거나 false면 400, true면 가입되고 확인 시각이 저장된다")
    void signupRequiresAgeCheck() throws Exception {
        String id = "age" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        loginIds.add(id);
        for (String age : new String[]{null, "false"}) {
            mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(id, age)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("만 14세 이상만 가입할 수 있어요."));
        }
        assertThat(userRepository.findByLoginId(id)).isEmpty();

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(id, "true")))
                .andExpect(status().isOk());
        assertThat(userRepository.findByLoginId(id).orElseThrow().getAgeConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("확인 전 사용자: 프로필 조회는 되지만(ageConfirmedAt 없음) 다른 API는 403, 확인 후에는 통과")
    void unconfirmedUserIsGated() throws Exception {
        String token = unconfirmedSocialUserToken();

        mockMvc.perform(get("/api/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ageConfirmedAt").doesNotExist());
        mockMvc.perform(get("/api/clothes").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("만 14세 이상인지 확인이 필요해요."));
        mockMvc.perform(put("/api/users/me").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"height\":170}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users/me/age-confirmation").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ageConfirmedAt").exists());
        mockMvc.perform(get("/api/clothes").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("만 14세 미만이면 확인 없이 바로 탈퇴할 수 있다")
    void unconfirmedUserCanDeleteAccount() throws Exception {
        String token = unconfirmedSocialUserToken();
        UUID id = created.get(0);
        mockMvc.perform(delete("/api/users/me").header("Authorization", token)).andExpect(status().isOk());
        assertThat(userRepository.findById(id)).isEmpty();
    }
}
