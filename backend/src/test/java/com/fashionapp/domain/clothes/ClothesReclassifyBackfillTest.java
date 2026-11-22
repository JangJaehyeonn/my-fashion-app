package com.fashionapp.domain.clothes;

import com.fashionapp.infra.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** FR-08: opt-in reclassify backfill (wearon.backfill.reclassify, default false). */
@ExtendWith(MockitoExtension.class)
class ClothesReclassifyBackfillTest {

    @Mock private ClothesRepository clothesRepository;
    @Mock private ClothesClassifier clothesClassifier;
    @Mock private ImageStorage imageStorage;
    @Mock private ClothesEmbedder clothesEmbedder;

    private ClothesReclassifyBackfill backfill;

    @BeforeEach
    void setUp() {
        backfill = new ClothesReclassifyBackfill(clothesRepository, clothesClassifier, imageStorage, clothesEmbedder,
                Runnable::run);
    }

    private static Clothes clothes(UUID id, String category, String color, String name) {
        return Clothes.builder().id(id).imageUrl("clothes/u/" + id + ".jpg")
                .category(category).color(color).name(name).build();
    }

    private static final ClothesClassification CLASSIFIED = new ClothesClassification(ClothesCategory.TOP, "네이비",
            "네이비 울 니트", "슬림", "울", "스트라이프", ClothesSeason.WINTER);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ClothesRepository.class, () -> mock(ClothesRepository.class))
            .withBean(ClothesClassifier.class, () -> mock(ClothesClassifier.class))
            .withBean(ImageStorage.class, () -> mock(ImageStorage.class))
            .withBean(ClothesEmbedder.class, () -> mock(ClothesEmbedder.class))
            .withUserConfiguration(ClothesReclassifyBackfill.class);

    @Test
    @DisplayName("기본 설정(값 없음)에서는 재분류 백필 빈이 만들어지지 않는다")
    void disabledByDefault() {
        contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(ClothesReclassifyBackfill.class));
    }

    @Test
    @DisplayName("wearon.backfill.reclassify=false면 재분류 백필 빈이 없다")
    void disabledWhenFalse() {
        contextRunner.withPropertyValues("wearon.backfill.reclassify=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ClothesReclassifyBackfill.class));
    }

    @Test
    @DisplayName("wearon.backfill.reclassify=true면 재분류 백필 빈이 만들어진다")
    void enabledWhenTrue() {
        contextRunner.withPropertyValues("wearon.backfill.reclassify=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ClothesReclassifyBackfill.class));
    }

    @Test
    @DisplayName("새 속성이 빈 옷을 다시 분류해 빈 속성만 채우고 임베딩을 다시 계산한다")
    void backfill_fillsMissingAttributes() {
        UUID id = UUID.randomUUID();
        Clothes stored = clothes(id, "TOP", "블랙", "내가 고친 이름");
        given(clothesRepository.findMissingAttributesAfter(any(), any(Pageable.class)))
                .willReturn(List.of(stored)).willReturn(List.of());
        given(imageStorage.readBytes(anyString())).willReturn(Optional.of(new byte[]{1}));
        given(clothesClassifier.classify(any(byte[].class), eq(id + ".jpg"), anyString()))
                .willReturn(Optional.of(CLASSIFIED));
        given(clothesRepository.findById(id)).willReturn(Optional.of(stored));
        given(clothesRepository.save(stored)).willReturn(stored);

        assertThat(backfill.backfill()).isEqualTo(1);

        assertThat(stored.getColor()).isEqualTo("블랙");
        assertThat(stored.getName()).isEqualTo("내가 고친 이름");
        assertThat(stored.getFit()).isEqualTo("슬림");
        assertThat(stored.getMaterial()).isEqualTo("울");
        assertThat(stored.getPattern()).isEqualTo("스트라이프");
        assertThat(stored.getSeason()).isEqualTo("WINTER");
        verify(clothesEmbedder).refresh(stored);
    }

    @Test
    @DisplayName("미분류 옷은 분류 결과 전체를 받는다")
    void backfill_unclassifiedTakesWholeClassification() {
        Clothes unclassified = clothes(UUID.randomUUID(), "UNCLASSIFIED", null, null);

        unclassified.fillMissingFrom(CLASSIFIED);

        assertThat(unclassified.getCategory()).isEqualTo("TOP");
        assertThat(unclassified.getColor()).isEqualTo("네이비");
        assertThat(unclassified.getName()).isEqualTo("네이비 울 니트");
    }

    @Test
    @DisplayName("분류가 연속 3번 실패하면 멈추고 옷은 그대로다")
    void backfill_stopsAfterConsecutiveFailures() {
        List<Clothes> page = List.of(
                clothes(UUID.randomUUID(), "TOP", "블랙", "a"),
                clothes(UUID.randomUUID(), "TOP", "블랙", "b"),
                clothes(UUID.randomUUID(), "TOP", "블랙", "c"),
                clothes(UUID.randomUUID(), "TOP", "블랙", "d"));
        given(clothesRepository.findMissingAttributesAfter(any(), any(Pageable.class))).willReturn(page);
        given(imageStorage.readBytes(anyString())).willReturn(Optional.of(new byte[]{1}));
        given(clothesClassifier.classify(any(byte[].class), anyString(), anyString())).willReturn(Optional.empty());

        assertThat(backfill.backfill()).isZero();

        verify(clothesClassifier, times(3)).classify(any(byte[].class), anyString(), anyString());
        verify(clothesRepository, never()).save(any());
        verify(clothesEmbedder, never()).refresh(any());
    }

    @Test
    @DisplayName("사진이 없는 옷은 건너뛰고 분류 API를 부르지 않는다")
    void backfill_skipsMissingImage() {
        given(clothesRepository.findMissingAttributesAfter(any(), any(Pageable.class)))
                .willReturn(List.of(clothes(UUID.randomUUID(), "TOP", "블랙", "a"))).willReturn(List.of());
        given(imageStorage.readBytes(anyString())).willReturn(Optional.empty());

        assertThat(backfill.backfill()).isZero();

        verify(clothesClassifier, never()).classify(any(byte[].class), anyString(), anyString());
    }
}
