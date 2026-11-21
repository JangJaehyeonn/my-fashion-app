package com.fashionapp.domain.image;

import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.infra.AiClothesClassifyResponse;
import com.fashionapp.infra.AiServerClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 진짜 LocalDiskStorage + 진짜 보안 필터 체인으로 이미지 업로드·조회·접근 제어·탈퇴 삭제를 검증한다.
 * (AI 서버만 목 — 유료 호출 방지.) 로컬 postgres·redis 필요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalImageIntegrationTest {

    @TempDir static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("storage.local.root", () -> storageRoot.toString());
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ClothesRepository clothesRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @MockBean AiServerClient aiServerClient;

    User owner;
    User stranger;
    String ownerToken;
    String strangerToken;

    @BeforeEach
    void setUp() {
        owner = newUser("owner");
        stranger = newUser("stranger");
        ownerToken = "Bearer " + jwtTokenProvider.createAccessToken(owner.getId());
        strangerToken = "Bearer " + jwtTokenProvider.createAccessToken(stranger.getId());

        AiClothesClassifyResponse classified = new AiClothesClassifyResponse();
        ReflectionTestUtils.setField(classified, "category", "TOP");
        ReflectionTestUtils.setField(classified, "color", "블랙");
        ReflectionTestUtils.setField(classified, "name", "블랙 티셔츠");
        given(aiServerClient.classifyClothes(any())).willReturn(classified);
    }

    @AfterEach
    void tearDown() {
        for (User u : List.of(owner, stranger)) {
            if (userRepository.existsById(u.getId())) {
                clothesRepository.deleteAllByUserId(u.getId());
                userRepository.deleteById(u.getId());
            }
        }
    }

    private User newUser(String label) {
        return userRepository.save(User.builder().ageConfirmedAt(java.time.LocalDateTime.now()).nickname(label)
                .provider(User.AuthProvider.google).providerId("img-" + label + "-" + UUID.randomUUID()).build());
    }

    private String uploadClothes(String token) throws Exception {
        String body = mockMvc.perform(multipart("/api/clothes")
                        .file(new MockMultipartFile("image", "shirt.jpg", "image/jpeg", JPEG))
                        .header("Authorization", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.imageUrl"); // 로컬 저장소: 백엔드 이미지 API URL
    }

    @Test
    @DisplayName("FR-01 재분류: 로컬 저장소에 저장된 원본 바이트를 그대로 AI 분류에 보낸다")
    void reclassifySendsStoredBytes() throws Exception {
        uploadClothes(ownerToken);
        String id = clothesRepository.findByUser_IdOrderByCreatedAtDesc(owner.getId()).get(0).getId().toString();
        AiClothesClassifyResponse shoes = new AiClothesClassifyResponse();
        ReflectionTestUtils.setField(shoes, "category", "SHOES");
        ReflectionTestUtils.setField(shoes, "color", "화이트");
        ReflectionTestUtils.setField(shoes, "name", "화이트 스니커즈");
        given(aiServerClient.classifyClothes(org.mockito.ArgumentMatchers.eq(JPEG),
                org.mockito.ArgumentMatchers.endsWith(".jpg"), org.mockito.ArgumentMatchers.eq("image/jpeg")))
                .willReturn(shoes);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/clothes/" + id + "/reclassify").header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.category").value("SHOES"));
    }

    private String pathOf(String url) {
        return url.substring(url.indexOf("/api/images/"));
    }

    @Test
    @DisplayName("업로드 → 응답 URL로 본인이 조회: 원본 바이트·Content-Type 그대로, 파일은 UUID 이름으로 저장 경로에 생성")
    void uploadThenOwnerCanFetch() throws Exception {
        String url = uploadClothes(ownerToken);
        assertThat(url).startsWith("http://localhost:8080/api/images/clothes/" + owner.getId() + "/").endsWith(".jpg");

        byte[] fetched = mockMvc.perform(get(pathOf(url)).header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(fetched).isEqualTo(JPEG);
        assertThat(countFiles(storageRoot.resolve("clothes").resolve(owner.getId().toString()))).isEqualTo(1);
    }

    @Test
    @DisplayName("조회: 토큰 없으면 401, 남의 이미지는 404(존재 여부 비공개), 없는 이미지도 404")
    void accessControl() throws Exception {
        String path = pathOf(uploadClothes(ownerToken));

        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header("Authorization", strangerToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/images/clothes/" + owner.getId() + "/" + UUID.randomUUID() + ".jpg")
                .header("Authorization", ownerToken)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("경로 조작(../, 인코딩된 ../, 임의 파일명)은 저장 루트 밖 파일을 읽지 못하고 404/400")
    void pathTraversalIsBlocked() throws Exception {
        Path secret = Files.writeString(storageRoot.getParent().resolve("secret-" + UUID.randomUUID() + ".txt"), "secret");
        try {
            String name = secret.getFileName().toString();
            String base = "/api/images/clothes/" + owner.getId() + "/";
            for (String p : List.of(base + "../../../" + name, base + "..%2F..%2F..%2F" + name,
                    base + "%2e%2e/%2e%2e/" + name, base + name, "/api/images/../" + name)) {
                int code = mockMvc.perform(get(p).header("Authorization", ownerToken)).andReturn().getResponse().getStatus();
                assertThat(code).as(p).isIn(400, 404);
            }
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    @Test
    @DisplayName("회원 탈퇴: 옷 사진 파일이 저장 경로에서 지워지고, 같은 URL은 더 이상 열리지 않는다")
    void accountDeletionRemovesFiles() throws Exception {
        String path = pathOf(uploadClothes(ownerToken));
        uploadClothes(ownerToken);
        Path ownerDir = storageRoot.resolve("clothes").resolve(owner.getId().toString());
        assertThat(countFiles(ownerDir)).isEqualTo(2);

        mockMvc.perform(delete("/api/users/me").header("Authorization", ownerToken)).andExpect(status().isOk());

        assertThat(ownerDir).doesNotExist();
        mockMvc.perform(get(path).header("Authorization", ownerToken)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("옷 삭제: 그 옷의 사진 파일만 지워진다")
    void clothesDeleteRemovesOnlyThatFile() throws Exception {
        uploadClothes(ownerToken);
        uploadClothes(ownerToken);
        Path ownerDir = storageRoot.resolve("clothes").resolve(owner.getId().toString());
        UUID clothesId = clothesRepository.findByUser_IdOrderByCreatedAtDesc(owner.getId()).get(0).getId();

        mockMvc.perform(delete("/api/clothes/" + clothesId).header("Authorization", ownerToken)).andExpect(status().isOk());

        assertThat(countFiles(ownerDir)).isEqualTo(1);
    }

    private long countFiles(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }
}
