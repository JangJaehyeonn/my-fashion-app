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

/**
 * 만 14세 이상 확인과 약관·방침 동의 (FR-05): 아이디 가입은 셋 다 필수, 소셜·기존 사용자는 확인 API에서 셋을 모두 보내기 전까지
 * 대부분의 API 403
 */
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

    /** 값이 null인 항목은 본문에서 뺀다 */
    private static String agreements(String ageOver14, String terms, String privacy) {
        StringBuilder sb = new StringBuilder();
        String[][] pairs = {{"ageOver14", ageOver14}, {"termsAgreed", terms}, {"privacyAgreed", privacy}};
        for (String[] kv : pairs) {
            if (kv[1] != null) sb.append(",\"").append(kv[0]).append("\":").append(kv[1]);
        }
        return sb.toString();
    }

    private static final String ALL_AGREED = "{" + agreements("true", "true", "true").substring(1) + "}";

    private String signupBody(String loginId, String ageOver14) {
        return signupJson(loginId, agreements(ageOver14, "true", "true"));
    }

    private String signupBody(String loginId, String ageOver14, String terms, String privacy) {
        return signupJson(loginId, agreements(ageOver14, terms, privacy));
    }

    private static String signupJson(String loginId, String agreementFields) {
        return "{\"loginId\":\"" + loginId + "\",\"password\":\"secret1234\",\"nickname\":\"테스터\"" + agreementFields + "}";
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

        mockMvc.perform(post("/api/users/me/age-confirmation").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(ALL_AGREED))
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

    @Test
    @DisplayName("아이디 가입: 약관·방침 동의가 없거나 false면 400이고 계정이 만들어지지 않는다")
    void signupRequiresTermsAndPrivacy() throws Exception {
        String id = "trm" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        loginIds.add(id);
        String[][] missing = {{null, "true"}, {"false", "true"}, {"true", null}, {"true", "false"}, {null, null}};
        for (String[] tp : missing) {
            mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody(id, "true", tp[0], tp[1])))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("이용약관과 개인정보처리방침에 동의해 주세요."));
        }
        assertThat(userRepository.findByLoginId(id)).isEmpty();
    }

    @Test
    @DisplayName("소셜 확인: 본문이 없거나 약관·방침·만 14세 중 하나라도 빠지면 400이고 계속 확인 전 범위만 허용된다")
    void socialConfirmationWithoutAllAgreementsKeepsGate() throws Exception {
        String token = unconfirmedSocialUserToken();
        UUID userId = created.get(0);

        mockMvc.perform(post("/api/users/me/age-confirmation").header("Authorization", token))
                .andExpect(status().isBadRequest());
        String[] partialBodies = {
                "{" + agreements("true", "true", null).substring(1) + "}",
                "{" + agreements("true", null, "true").substring(1) + "}",
                "{" + agreements("false", "true", "true").substring(1) + "}",
        };
        for (String body : partialBodies) {
            mockMvc.perform(post("/api/users/me/age-confirmation").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        assertThat(userRepository.findById(userId).orElseThrow().getAgeConfirmedAt()).isNull();
        mockMvc.perform(get("/api/users/me").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/clothes").header("Authorization", token)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("이 기능 이전에 만 14세 확인을 마친 사용자는 약관 이력이 없어도 계속 API를 쓸 수 있다")
    void previouslyConfirmedUserKeepsAccess() throws Exception {
        User user = userRepository.save(User.builder().nickname("기존").provider(User.AuthProvider.google)
                .providerId("age-legacy-" + UUID.randomUUID()).ageConfirmedAt(java.time.LocalDateTime.now()).build());
        created.add(user.getId());
        String token = "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
        mockMvc.perform(get("/api/clothes").header("Authorization", token)).andExpect(status().isOk());
    }
}
