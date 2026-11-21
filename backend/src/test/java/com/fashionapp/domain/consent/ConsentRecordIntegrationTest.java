package com.fashionapp.domain.consent;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-04 동의 이력: 실제 DB로 동의·철회·재동의 이력, 만 14세 확인 기록, 탈퇴 시 삭제, 기존 값 백필을 확인 */
class ConsentRecordIntegrationTest extends IntegrationTest {

    private static final String ALL_AGREED = "{\"ageOver14\":true,\"termsAgreed\":true,\"privacyAgreed\":true}";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ConsentRecordRepository consentRecordRepository;
    @Autowired ConsentBackfill consentBackfill;
    @Autowired JwtTokenProvider jwtTokenProvider;

    private final List<UUID> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        createdUsers.clear();
    }

    @AfterEach
    void tearDown() {
        for (UUID id : createdUsers) {
            consentRecordRepository.deleteAllByUserId(id);
            if (userRepository.existsById(id)) userRepository.deleteById(id);
        }
    }

    private User saveUser(LocalDateTime ageConfirmedAt, LocalDateTime vtonConsentAt) {
        User user = userRepository.save(User.builder().ageConfirmedAt(ageConfirmedAt).vtonConsentAt(vtonConsentAt)
                .nickname("이력테스트").provider(User.AuthProvider.google)
                .providerId("consent-record-" + UUID.randomUUID()).build());
        createdUsers.add(user.getId());
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
    }

    @Test
    @DisplayName("가상 피팅 동의 → 철회 → 재동의: 이력 2건, 첫 건에만 철회 시각")
    void grantRevokeGrantKeepsTwoRecords() throws Exception {
        User user = saveUser(LocalDateTime.now(), null);
        String token = bearer(user);

        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/me/consents/vton").header("Authorization", token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vtonConsentAt").exists());

        List<ConsentRecord> records = consentRecordRepository.findByUserIdOrderByAgreedAtAsc(user.getId());
        assertThat(records).hasSize(2);
        assertThat(records).allMatch(r -> r.getConsentType() == ConsentType.VTON && r.getVersion() != null);
        assertThat(records.get(0).getRevokedAt()).isNotNull();
        assertThat(records.get(1).getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("만 14세 확인·약관 동의: 응답 형식은 그대로이고 이력 3건(약관·방침·만 14세)이 남는다 (다시 확인해도 3건)")
    void ageConfirmationIsRecorded() throws Exception {
        User user = saveUser(null, null);
        String token = bearer(user);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/users/me/age-confirmation").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(ALL_AGREED))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(user.getId().toString()))
                    .andExpect(jsonPath("$.data.ageConfirmedAt").exists());
        }

        List<ConsentRecord> records = consentRecordRepository.findByUserIdOrderByAgreedAtAsc(user.getId());
        assertThat(records).hasSize(3).allMatch(ConsentRecord::isActive)
                .extracting(ConsentRecord::getConsentType)
                .containsExactlyInAnyOrder(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE_OVER_14);
        assertThat(records).filteredOn(r -> r.getConsentType() == ConsentType.AGE_OVER_14).singleElement().satisfies(r -> {
            assertThat(r.getAgreedAt().truncatedTo(ChronoUnit.MILLIS)).isEqualTo(
                    userRepository.findById(user.getId()).orElseThrow().getAgeConfirmedAt().truncatedTo(ChronoUnit.MILLIS));
        });
    }

    @Test
    @DisplayName("아이디 가입은 약관·방침·만 14세 확인 이력 3건을 남긴다")
    void localSignupRecordsAgeConsent() throws Exception {
        String loginId = "cr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"" + loginId + "\",\"password\":\"Passw0rd!x\",\"nickname\":\"가입이력\","
                                + ALL_AGREED.substring(1)))
                .andExpect(status().isOk());
        User user = userRepository.findByLoginId(loginId).orElseThrow();
        createdUsers.add(user.getId());

        assertThat(consentRecordRepository.findByUserIdOrderByAgreedAtAsc(user.getId()))
                .extracting(ConsentRecord::getConsentType)
                .containsExactlyInAnyOrder(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE_OVER_14);
    }

    @Test
    @DisplayName("회원 탈퇴 후 그 사용자의 이력은 0건, 다른 사용자 이력은 남는다")
    void accountDeletionRemovesRecords() throws Exception {
        User user = saveUser(LocalDateTime.now(), null);
        User other = saveUser(LocalDateTime.now(), null);
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", bearer(user))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/me/consents/vton").header("Authorization", bearer(user))).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", bearer(other))).andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/me").header("Authorization", bearer(user))).andExpect(status().isOk());

        assertThat(consentRecordRepository.countByUserId(user.getId())).isZero();
        assertThat(consentRecordRepository.countByUserId(other.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("백필: 기존 age_confirmed_at·vton_consent_at을 legacy 이력으로 옮기고, 다시 실행해도 중복되지 않는다")
    void backfillCopiesLegacyValuesOnce() {
        LocalDateTime ageAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        LocalDateTime vtonAt = LocalDateTime.of(2026, 2, 3, 4, 5, 6);
        User user = saveUser(ageAt, vtonAt);

        consentBackfill.run(null);
        consentBackfill.run(null);

        List<ConsentRecord> records = consentRecordRepository.findByUserIdOrderByAgreedAtAsc(user.getId());
        assertThat(records).hasSize(2);
        assertThat(records).extracting(ConsentRecord::getConsentType)
                .containsExactly(ConsentType.AGE_OVER_14, ConsentType.VTON);
        assertThat(records).extracting(ConsentRecord::getAgreedAt).containsExactly(ageAt, vtonAt);
        assertThat(records).allMatch(r -> ConsentType.LEGACY_VERSION.equals(r.getVersion()) && r.isActive());
        // users 컬럼 값은 그대로
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getAgeConfirmedAt()).isEqualTo(ageAt);
        assertThat(reloaded.getVtonConsentAt()).isEqualTo(vtonAt);
    }
}
