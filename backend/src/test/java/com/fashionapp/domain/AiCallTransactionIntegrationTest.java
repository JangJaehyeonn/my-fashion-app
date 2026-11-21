package com.fashionapp.domain;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.infra.AiClosetRecommendResponse;
import com.fashionapp.infra.AiClothesClassifyResponse;
import com.fashionapp.infra.AiVtonResponse;
import com.fashionapp.support.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 서버를 기다리는 동안 트랜잭션이 열려 있지 않고 DB 커넥션도 반납돼 있는지 확인.
 * AI 호출 목(mock)이 불리는 순간의 트랜잭션 상태와 Hikari 활성 커넥션 수를 기록해 검증한다.
 */
class AiCallTransactionIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired ClothesRepository clothesRepository;
    @Autowired DataSource dataSource;

    private User user;
    private String token;
    private Boolean txActiveDuringAiCall;
    private Integer activeConnectionsDuringAiCall;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().ageConfirmedAt(java.time.LocalDateTime.now())
                .nickname("트랜잭션테스트")
                .vtonConsentAt(java.time.LocalDateTime.now())
                .provider(User.AuthProvider.google)
                .providerId("tx-test-" + UUID.randomUUID())
                .build());
        token = "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
        given(imageStorage.resolveUrl(anyString())).willReturn("https://presigned");
    }

    @AfterEach
    void tearDown() {
        clothesRepository.deleteAllByUserId(user.getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    @DisplayName("옷 등록: AI 분류 호출 중에는 트랜잭션·DB 커넥션이 없다")
    void clothesClassifyOutsideTransaction() throws Exception {
        AiClothesClassifyResponse classified = new AiClothesClassifyResponse();
        ReflectionTestUtils.setField(classified, "category", "TOP");
        ReflectionTestUtils.setField(classified, "color", "블랙");
        ReflectionTestUtils.setField(classified, "name", "블랙 티셔츠");
        given(aiServerClient.classifyClothes(any())).willAnswer(inv -> {
            recordState();
            return classified;
        });
        given(imageStorage.store(any(), anyString(), any())).willReturn("clothes/x.jpg");

        mockMvc.perform(multipart("/api/clothes").file(image("image")).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("블랙 티셔츠"))
                .andExpect(jsonPath("$.data.classificationFailed").value(false));

        assertNoTransactionDuringAiCall();
    }

    @Test
    @DisplayName("옷장 기반 추천: AI 호출 중에는 트랜잭션·DB 커넥션이 없다")
    void closetRecommendOutsideTransaction() throws Exception {
        clothesRepository.save(Clothes.builder().user(user)
                .imageUrl("clothes/y.jpg").category("TOP").build());
        given(aiServerClient.recommendOutfitsByCloset(any())).willAnswer(inv -> {
            recordState();
            return CompletableFuture.completedFuture(new AiClosetRecommendResponse());
        });

        mockMvc.perform(post("/api/outfits/recommend/closet").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature\":12.5,\"condition\":\"맑음\",\"situation\":\"DAILY\"}"))
                .andExpect(status().isOk());

        assertNoTransactionDuringAiCall();
    }

    @Test
    @DisplayName("가상 피팅: AI 호출 중에는 트랜잭션·DB 커넥션이 없다")
    void vtonOutsideTransaction() throws Exception {
        AiVtonResponse vton = new AiVtonResponse();
        ReflectionTestUtils.setField(vton, "resultImageBase64", "aGVsbG8=");
        given(aiServerClient.virtualTryOn(any(), any(), anyString())).willAnswer(inv -> {
            recordState();
            return CompletableFuture.completedFuture(vton);
        });
        given(imageStorage.storeBytes(any(), anyString(), anyString(), any())).willReturn("vton/x.png");

        mockMvc.perform(multipart("/api/vton").file(image("personImage")).file(image("garmentImage"))
                        .param("garmentDesc", "셔츠").header("Authorization", token))
                .andExpect(status().isOk());

        assertNoTransactionDuringAiCall();
        verify(imageStorage).storeBytes(any(), anyString(), org.mockito.ArgumentMatchers.eq("vton"), org.mockito.ArgumentMatchers.eq(user.getId()));
    }

    @Test
    @DisplayName("FR-01: AI 타임아웃이면 201 + 미분류로 저장된다 (사진은 업로드)")
    void timeoutSavesUnclassified() throws Exception {
        given(aiServerClient.classifyClothes(any())).willThrow(new CustomException(ErrorCode.AI_SERVER_TIMEOUT));
        given(imageStorage.store(any(), anyString(), any())).willReturn("clothes/t.jpg");

        mockMvc.perform(multipart("/api/clothes").file(image("image")).header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.classificationFailed").value(true))
                .andExpect(jsonPath("$.data.category").value("UNCLASSIFIED"));

        verify(imageStorage).store(any(), anyString(), any());
        assertThat(clothesRepository.findImageUrlsByUserId(user.getId())).containsExactly("clothes/t.jpg");
    }

    @Test
    @DisplayName("FR-01: PATCH로 고친 값이 목록에 반영되고, 재분류는 AI 실패 시 503 + 기존 값 유지")
    void patchAndReclassify() throws Exception {
        Clothes saved = clothesRepository.save(Clothes.builder().user(user)
                .imageUrl("clothes/u.jpg").category("UNCLASSIFIED").build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/clothes/" + saved.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"BOTTOM\",\"color\":\"블랙\",\"name\":\"블랙 슬랙스\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classificationFailed").value(false));

        given(imageStorage.readBytes("clothes/u.jpg")).willReturn(java.util.Optional.of(new byte[]{1}));
        given(aiServerClient.classifyClothes(any(byte[].class), anyString(), anyString()))
                .willThrow(new CustomException(ErrorCode.AI_SERVER_ERROR));
        mockMvc.perform(post("/api/clothes/" + saved.getId() + "/reclassify").header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.CLOTHES_RECLASSIFY_UNAVAILABLE.getMessage()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/clothes").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("BOTTOM"))
                .andExpect(jsonPath("$.data[0].color").value("블랙"))
                .andExpect(jsonPath("$.data[0].name").value("블랙 슬랙스"));
    }

    private void recordState() {
        txActiveDuringAiCall = TransactionSynchronizationManager.isActualTransactionActive();
        activeConnectionsDuringAiCall = ((HikariDataSource) dataSource).getHikariPoolMXBean().getActiveConnections();
    }

    private void assertNoTransactionDuringAiCall() {
        assertThat(txActiveDuringAiCall).as("AI 호출 중 트랜잭션 활성 여부").isFalse();
        assertThat(activeConnectionsDuringAiCall).as("AI 호출 중 사용 중인 DB 커넥션 수").isZero();
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile(name, "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
