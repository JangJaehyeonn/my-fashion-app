package com.fashionapp.domain.vton;

import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.infra.AiVtonResponse;
import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 가상 피팅 사진 전송 동의: 미동의면 403(외부 전송 없음), 동의 시각 저장, 철회 가능 */
class VtonConsentIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    User user;
    String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().ageConfirmedAt(java.time.LocalDateTime.now()).nickname("동의테스트").provider(User.AuthProvider.google)
                .providerId("consent-" + UUID.randomUUID()).build());
        token = "Bearer " + jwtTokenProvider.createAccessToken(user.getId());
        AiVtonResponse vton = new AiVtonResponse();
        ReflectionTestUtils.setField(vton, "resultImageBase64", java.util.Base64.getEncoder().encodeToString(PNG));
        given(aiServerClient.virtualTryOn(any(), any(), anyString())).willReturn(CompletableFuture.completedFuture(vton));
        given(imageStorage.storeBytes(any(), anyString(), anyString(), any())).willReturn("vton/x.png");
        given(imageStorage.resolveUrl(anyString())).willReturn("https://img");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(user.getId());
    }

    private org.springframework.test.web.servlet.ResultActions tryOn() throws Exception {
        return mockMvc.perform(multipart("/api/vton")
                .file(new MockMultipartFile("personImage", "p.png", "image/png", PNG))
                .file(new MockMultipartFile("garmentImage", "g.png", "image/png", PNG))
                .header("Authorization", token));
    }

    @Test
    @DisplayName("동의 전에는 403이고 AI 서버(외부 전송)를 호출하지 않는다")
    void blockedWithoutConsent() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", token))
                .andExpect(jsonPath("$.data.vtonConsentAt").doesNotExist());
        tryOn().andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("가상 피팅을 쓰려면 사진 전송에 동의해 주세요."));
        verify(aiServerClient, never()).virtualTryOn(any(), any(), anyString());
    }

    @Test
    @DisplayName("동의하면 시각이 저장되고 피팅이 되며, 철회하면 다시 403")
    void grantThenRevoke() throws Exception {
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vtonConsentAt").exists());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVtonConsentAt()).isNotNull();
        tryOn().andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/me/consents/vton").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vtonConsentAt").doesNotExist());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVtonConsentAt()).isNull();
        tryOn().andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("다시 동의해도 최초 동의 시각은 바뀌지 않는다")
    void regrantKeepsFirstTimestamp() throws Exception {
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", token)).andExpect(status().isOk());
        var first = userRepository.findById(user.getId()).orElseThrow().getVtonConsentAt();
        mockMvc.perform(post("/api/users/me/consents/vton").header("Authorization", token)).andExpect(status().isOk());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVtonConsentAt()).isEqualTo(first);
    }
}
