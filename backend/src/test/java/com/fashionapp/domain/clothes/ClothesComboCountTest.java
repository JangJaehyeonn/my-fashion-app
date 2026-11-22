package com.fashionapp.domain.clothes;

import com.fashionapp.domain.combo.ComboTestSupport;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.infra.AiServerClient;
import com.fashionapp.infra.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** FR-09: comboCount in GET /api/clothes and the total in GET /api/clothes/summary. */
@ExtendWith(MockitoExtension.class)
class ClothesComboCountTest {

    @Mock private ClothesRepository clothesRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiServerClient aiServerClient;
    @Mock private ImageStorage imageStorage;
    @Mock private ClothesEmbedder clothesEmbedder;

    private ClothesService clothesService;
    private final UUID userId = UUID.randomUUID();

    private static Clothes clothes(String category, String color) {
        return Clothes.builder().id(UUID.randomUUID()).imageUrl("clothes/x.jpg").category(category).color(color).build();
    }

    @BeforeEach
    void setUp() {
        clothesService = new ClothesService(clothesRepository, userRepository, new ClothesClassifier(aiServerClient),
                imageStorage, clothesEmbedder, ComboTestSupport.defaultService());
    }

    @Test
    @DisplayName("옷 목록의 각 옷 comboCount는 그 옷이 든 유효 코디 수, 미분류는 0이고 요약은 전체 코디 수")
    void listAndSummary() {
        Clothes redTop = clothes("TOP", "레드");
        Clothes whiteTop = clothes("TOP", "화이트");
        Clothes greenBottom = clothes("BOTTOM", "그린");
        Clothes blackBottom = clothes("BOTTOM", "블랙");
        Clothes shoes = clothes("SHOES", "블랙");
        Clothes unclassified = clothes("UNCLASSIFIED", null);
        given(clothesRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .willReturn(List.of(redTop, whiteTop, greenBottom, blackBottom, shoes, unclassified));

        Map<UUID, Integer> counts = clothesService.getMyClothes(userId).stream()
                .collect(Collectors.toMap(ClothesResponse::getId, ClothesResponse::getComboCount));

        assertThat(counts).containsEntry(redTop.getId(), 1)
                .containsEntry(whiteTop.getId(), 2)
                .containsEntry(greenBottom.getId(), 1)
                .containsEntry(blackBottom.getId(), 2)
                .containsEntry(shoes.getId(), 3)
                .containsEntry(unclassified.getId(), 0);
        assertThat(clothesService.getSummary(userId).comboCount()).isEqualTo(3);
    }
}
