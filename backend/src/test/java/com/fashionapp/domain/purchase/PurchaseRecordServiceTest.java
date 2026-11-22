package com.fashionapp.domain.purchase;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesCategory;
import com.fashionapp.domain.clothes.ClothesClassification;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.clothes.ClothesResponse;
import com.fashionapp.domain.clothes.ClothesSeason;
import com.fashionapp.domain.clothes.ClothesService;
import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import com.fashionapp.infra.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** FR-12 purchase check records and decisions; storage, Redis draft and DB are mocked. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseRecordServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    private static final PurchaseCheckResponse.Item ITEM = new PurchaseCheckResponse.Item(ClothesCategory.TOP,
            "블랙", "블랙 hoodie", "오버핏", "면", "무지", "WINTER");

    @Mock private PurchaseCheckRepository repository;
    @Mock private PurchaseDraftStore draftStore;
    @Mock private ImageStorage imageStorage;
    @Mock private ClothesService clothesService;
    @Mock private ClothesRepository clothesRepository;

    private final UUID userId = UUID.randomUUID();
    private PurchaseRecordService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseRecordService(repository, draftStore, imageStorage, clothesService, clothesRepository,
                CLOCK);
        given(imageStorage.storeBytesAt(anyString(), any(), anyString())).willAnswer(inv -> inv.getArgument(0));
        given(imageStorage.resolveUrl(anyString())).willAnswer(inv -> "http://img/" + inv.getArgument(0));
        given(repository.claimUndecided(any(), anyString())).willReturn(1);
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    private PurchaseCheck stored(Instant createdAt) {
        UUID id = UUID.randomUUID();
        PurchaseCheck check = new PurchaseCheck(id, userId, ITEM, PurchaseVerdict.THINK, 4, 39000L,
                "tmp/" + userId + "/" + id + ".jpg", createdAt);
        given(repository.findByIdAndUserId(id, userId)).willReturn(Optional.of(check));
        given(imageStorage.readBytes(check.getTempPhotoKey())).willReturn(Optional.of(JPEG));
        return check;
    }

    @Test
    @DisplayName("판정하면 사진을 tmp/{userId}/{checkId}.jpg에 두고 UNDECIDED 기록과 속성·임베딩 초안을 남긴다")
    void openStoresTempPhotoAndRecord() {
        float[] vector = {1f, 2f};
        UUID checkId = service.open(userId, JPEG, "image/jpeg", ITEM, PurchaseVerdict.BUY, 5, null, vector);

        ArgumentCaptor<PurchaseCheck> saved = ArgumentCaptor.forClass(PurchaseCheck.class);
        verify(repository).save(saved.capture());
        PurchaseCheck check = saved.getValue();
        assertThat(check.getId()).isEqualTo(checkId);
        assertThat(check.getTempPhotoKey()).isEqualTo("tmp/" + userId + "/" + checkId + ".jpg");
        assertThat(check.decisionValue()).isEqualTo(PurchaseDecision.UNDECIDED);
        assertThat(check.getCategory()).isEqualTo("TOP");
        assertThat(check.getPlus()).isEqualTo(5);
        assertThat(check.getPrice()).isNull();
        assertThat(check.getCreatedAt()).isEqualTo(NOW);
        verify(imageStorage).storeBytesAt(check.getTempPhotoKey(), JPEG, "image/jpeg");
        verify(draftStore).save(checkId, new PurchaseDraftStore.Draft("오버핏", "면", "무지", "WINTER", vector));
    }

    @Test
    @DisplayName("기록 저장이 실패하면 임시 사진을 지운다")
    void openDeletesPhotoWhenRecordFails() {
        given(repository.save(any())).willThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.open(userId, JPEG, "image/jpeg", ITEM, PurchaseVerdict.BUY, 5, null, null))
                .isInstanceOf(IllegalStateException.class);
        verify(imageStorage).delete(org.mockito.ArgumentMatchers.startsWith("tmp/" + userId + "/"));
    }

    @Test
    @DisplayName("BUY: 임시 사진을 옷 사진으로 옮기고 분석한 속성·임베딩 그대로 옷을 만든 뒤 임시 사진을 지운다 (재분류 없음)")
    void buyCreatesClothesFromDraft() {
        PurchaseCheck check = stored(NOW.minus(Duration.ofMinutes(10)));
        float[] vector = {0.5f};
        given(draftStore.find(check.getId()))
                .willReturn(Optional.of(new PurchaseDraftStore.Draft("오버핏", "면", "무지", "WINTER", vector)));
        given(imageStorage.storeBytes(JPEG, "image/jpeg", "clothes", userId)).willReturn("clothes/new.jpg");
        UUID clothesId = UUID.randomUUID();
        given(clothesService.registerPurchased(eq(userId), eq("clothes/new.jpg"), any(), eq(vector)))
                .willReturn(ClothesResponse.builder().id(clothesId).category(ClothesCategory.TOP).build());
        Clothes created = Clothes.builder().id(clothesId).imageUrl("clothes/new.jpg").category("TOP").build();
        given(clothesRepository.findByUser_IdAndIdIn(eq(userId), any())).willReturn(List.of(created));
        String tempKey = check.getTempPhotoKey();

        PurchaseDecisionResponse response = service.decide(userId, check.getId(), PurchaseDecisionRequest.Choice.BUY);

        ArgumentCaptor<ClothesClassification> classification = ArgumentCaptor.forClass(ClothesClassification.class);
        verify(clothesService).registerPurchased(eq(userId), eq("clothes/new.jpg"), classification.capture(), eq(vector));
        assertThat(classification.getValue()).isEqualTo(new ClothesClassification(ClothesCategory.TOP, "블랙",
                "블랙 hoodie", "오버핏", "면", "무지", ClothesSeason.WINTER));
        verify(imageStorage).delete(tempKey);
        verify(draftStore).delete(List.of(check.getId()));
        assertThat(response.clothes().getId()).isEqualTo(clothesId);
        assertThat(response.check().decision()).isEqualTo(PurchaseDecision.BOUGHT);
        assertThat(response.check().clothesId()).isEqualTo(clothesId);
        assertThat(response.check().imageUrl()).isEqualTo("http://img/clothes/new.jpg");
        assertThat(check.getTempPhotoKey()).isNull();
    }

    @Test
    @DisplayName("BUY: Redis 초안이 없으면 기록의 이름·카테고리·색으로 만들고 임베딩은 새로 계산하게 null을 넘긴다")
    void buyWithoutDraftUsesRecord() {
        PurchaseCheck check = stored(NOW.minus(Duration.ofMinutes(1)));
        given(draftStore.find(check.getId())).willReturn(Optional.empty());
        given(imageStorage.storeBytes(any(), anyString(), anyString(), any())).willReturn("clothes/new.jpg");
        given(clothesService.registerPurchased(any(), anyString(), any(), any()))
                .willReturn(ClothesResponse.builder().id(UUID.randomUUID()).build());

        service.decide(userId, check.getId(), PurchaseDecisionRequest.Choice.BUY);

        verify(clothesService).registerPurchased(userId, "clothes/new.jpg",
                new ClothesClassification(ClothesCategory.TOP, "블랙", "블랙 hoodie"), null);
    }

    @Test
    @DisplayName("BUY 중 옷 만들기가 실패하면 결정을 UNDECIDED로 되돌리고 임시 사진은 남긴다")
    void buyFailureReleasesClaim() {
        PurchaseCheck check = stored(NOW.minus(Duration.ofMinutes(1)));
        given(imageStorage.storeBytes(any(), anyString(), anyString(), any()))
                .willThrow(new CustomException(ErrorCode.IMAGE_UPLOAD_FAILED));

        assertThatThrownBy(() -> service.decide(userId, check.getId(), PurchaseDecisionRequest.Choice.BUY))
                .isInstanceOf(CustomException.class);
        verify(repository).releaseClaim(check.getId(), "BOUGHT");
        verify(imageStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("SKIP: 임시 사진을 바로 지우고 SKIPPED로 기록하며 옷은 만들지 않는다")
    void skipDeletesPhoto() {
        PurchaseCheck check = stored(NOW.minus(Duration.ofMinutes(29)));
        String tempKey = check.getTempPhotoKey();

        PurchaseDecisionResponse response = service.decide(userId, check.getId(), PurchaseDecisionRequest.Choice.SKIP);

        verify(imageStorage).delete(tempKey);
        assertThat(response.clothes()).isNull();
        assertThat(response.check().decision()).isEqualTo(PurchaseDecision.SKIPPED);
        assertThat(response.check().imageUrl()).isNull();
        assertThat(check.getTempPhotoKey()).isNull();
        verify(clothesService, never()).registerPurchased(any(), any(), any(), any());
    }

    @Test
    @DisplayName("SKIP 때 사진 삭제가 실패하면 키를 남겨 스케줄러가 다시 지우게 한다")
    void skipKeepsKeyWhenDeleteFails() {
        PurchaseCheck check = stored(NOW.minus(Duration.ofMinutes(1)));
        willThrow(new IllegalStateException("io")).given(imageStorage).delete(anyString());

        service.decide(userId, check.getId(), PurchaseDecisionRequest.Choice.SKIP);

        assertThat(check.decisionValue()).isEqualTo(PurchaseDecision.SKIPPED);
        assertThat(check.getTempPhotoKey()).isNotNull();
    }

    @Test
    @DisplayName("판정 후 30분이 지나면 BUY·SKIP 모두 410이고 아무것도 바꾸지 않는다")
    void expiredIsGone() {
        PurchaseCheck check = stored(NOW.minus(PurchaseCheck.PHOTO_TTL));
        for (PurchaseDecisionRequest.Choice choice : PurchaseDecisionRequest.Choice.values()) {
            assertError(() -> service.decide(userId, check.getId(), choice), ErrorCode.PURCHASE_CHECK_EXPIRED);
        }
        verify(repository, never()).claimUndecided(any(), anyString());
    }

    @Test
    @DisplayName("스케줄러가 사진을 지운 기록(키 없음)이나 사진 파일이 없는 BUY도 410")
    void photoGoneIsGone() {
        PurchaseCheck cleaned = stored(NOW.minus(Duration.ofMinutes(5)));
        cleaned.photoRemoved();
        assertError(() -> service.decide(userId, cleaned.getId(), PurchaseDecisionRequest.Choice.SKIP),
                ErrorCode.PURCHASE_CHECK_EXPIRED);

        PurchaseCheck missingFile = stored(NOW.minus(Duration.ofMinutes(5)));
        given(imageStorage.readBytes(missingFile.getTempPhotoKey())).willReturn(Optional.empty());
        assertError(() -> service.decide(userId, missingFile.getId(), PurchaseDecisionRequest.Choice.BUY),
                ErrorCode.PURCHASE_CHECK_EXPIRED);
        verify(clothesService, never()).registerPurchased(any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 사용자의 checkId는 조회·결정 모두 404")
    void otherUsersCheckIsNotFound() {
        PurchaseCheck check = stored(NOW);
        UUID other = UUID.randomUUID();
        given(repository.findByIdAndUserId(check.getId(), other)).willReturn(Optional.empty());

        assertError(() -> service.get(other, check.getId()), ErrorCode.PURCHASE_CHECK_NOT_FOUND);
        assertError(() -> service.decide(other, check.getId(), PurchaseDecisionRequest.Choice.BUY),
                ErrorCode.PURCHASE_CHECK_NOT_FOUND);
        verify(imageStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("이미 결정했거나 동시에 다른 결정이 먼저 이기면 409")
    void alreadyDecidedIsConflict() {
        PurchaseCheck check = stored(NOW);
        given(repository.claimUndecided(check.getId(), "SKIPPED")).willReturn(0);
        assertError(() -> service.decide(userId, check.getId(), PurchaseDecisionRequest.Choice.SKIP),
                ErrorCode.PURCHASE_ALREADY_DECIDED);

        check.markSkipped();
        assertError(() -> service.decide(userId, check.getId(), PurchaseDecisionRequest.Choice.BUY),
                ErrorCode.PURCHASE_ALREADY_DECIDED);
    }

    @Test
    @DisplayName("목록: 최신 20건을 요청하고 BUY 건만 옷 사진 URL이 있다 (나중에 옷을 지웠으면 없음)")
    void historyHasPhotoOnlyForBought() {
        PurchaseCheck bought = stored(NOW);
        UUID clothesId = UUID.randomUUID();
        bought.markBought(clothesId);
        PurchaseCheck deletedClothes = stored(NOW.minusSeconds(1));
        deletedClothes.markBought(UUID.randomUUID());
        PurchaseCheck undecided = stored(NOW.minusSeconds(2));
        given(repository.findByUserIdOrderByCreatedAtDescIdDesc(eq(userId), any()))
                .willReturn(List.of(bought, deletedClothes, undecided));
        given(clothesRepository.findByUser_IdAndIdIn(eq(userId), any())).willReturn(List.of(
                Clothes.builder().id(clothesId).imageUrl("clothes/a.jpg").category("TOP").build()));

        List<PurchaseRecordResponse> history = service.history(userId);

        verify(repository).findByUserIdOrderByCreatedAtDescIdDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(history).extracting(PurchaseRecordResponse::id)
                .containsExactly(bought.getId(), deletedClothes.getId(), undecided.getId());
        assertThat(history).extracting(PurchaseRecordResponse::imageUrl)
                .containsExactly("http://img/clothes/a.jpg", null, null);
        assertThat(history.get(2).decision()).isEqualTo(PurchaseDecision.UNDECIDED);
        assertThat(history.get(0).plus()).isEqualTo(4);
        assertThat(history.get(0).verdict()).isEqualTo(PurchaseVerdict.THINK);
        assertThat(history.get(0).price()).isEqualTo(39000L);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
