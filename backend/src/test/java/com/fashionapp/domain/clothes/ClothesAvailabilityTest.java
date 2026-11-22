package com.fashionapp.domain.clothes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-27: laundry period, per-category wear cooldown and the rules file. */
class ClothesAvailabilityTest {

    /** 2026-11-22 12:00 KST. */
    static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22);
    static final LocalDateTime NOW = LocalDateTime.now(FIXED);

    static ClothesAvailability defaultAvailability() {
        return new ClothesAvailability(AvailabilityRules.defaults(), FIXED);
    }

    private final ClothesAvailability availability = defaultAvailability();

    private static Clothes piece(String category, String status, LocalDateTime until) {
        return Clothes.builder().id(UUID.randomUUID()).imageUrl("clothes/x.jpg").category(category)
                .status(status).statusUntil(until).build();
    }

    @Test
    @DisplayName("세탁 중으로 바꾸면 설정 일수(3일) 뒤가 종료 시각이고, 그 전까지 LAUNDRY·입을 수 없음")
    void laundryUntilAndBefore() {
        assertThat(availability.laundryUntil()).isEqualTo(NOW.plusDays(3));
        Clothes laundry = piece("TOP", "LAUNDRY", availability.laundryUntil());

        assertThat(availability.effectiveStatus(laundry)).isEqualTo(ClothesStatus.LAUNDRY);
        assertThat(availability.isAvailable(laundry, null)).isFalse();
    }

    @Test
    @DisplayName("세탁 중으로 바꾼 지 4일이 지나면(종료 시각이 지나면) AVAILABLE로 보고 입을 수 있음")
    void laundryExpires() {
        Clothes fourDaysAgo = piece("TOP", "LAUNDRY", NOW.minusDays(4).plusDays(3));
        assertThat(availability.effectiveStatus(fourDaysAgo)).isEqualTo(ClothesStatus.AVAILABLE);
        assertThat(availability.isAvailable(fourDaysAgo, null)).isTrue();

        Clothes exactlyEnded = piece("TOP", "LAUNDRY", NOW);
        assertThat(availability.effectiveStatus(exactlyEnded)).isEqualTo(ClothesStatus.AVAILABLE);
    }

    @Test
    @DisplayName("상태가 비어 있는 옛 행과 종료 시각 없는 LAUNDRY는 AVAILABLE로 본다")
    void nullStatusIsAvailable() {
        assertThat(availability.effectiveStatus(piece("TOP", null, null))).isEqualTo(ClothesStatus.AVAILABLE);
        assertThat(availability.effectiveStatus(piece("TOP", "LAUNDRY", null))).isEqualTo(ClothesStatus.AVAILABLE);
    }

    @Test
    @DisplayName("상의 쿨다운 3일: 오늘·어제·2일 전 착용은 쿨다운, 3일·4일 전은 아님")
    void topCooldown() {
        Clothes top = piece("TOP", "AVAILABLE", null);
        assertThat(availability.isAvailable(top, TODAY)).isFalse();
        assertThat(availability.isAvailable(top, TODAY.minusDays(1))).isFalse();
        assertThat(availability.isAvailable(top, TODAY.minusDays(2))).isFalse();
        assertThat(availability.isAvailable(top, TODAY.minusDays(3))).isTrue();
        assertThat(availability.isAvailable(top, TODAY.minusDays(4))).isTrue();
    }

    @Test
    @DisplayName("하의 쿨다운 2일, 아우터·신발 0일, 규칙에 없는 기타는 쿨다운 없음")
    void otherCategories() {
        assertThat(availability.onCooldown(ClothesCategory.BOTTOM, TODAY.minusDays(1))).isTrue();
        assertThat(availability.onCooldown(ClothesCategory.BOTTOM, TODAY.minusDays(2))).isFalse();
        assertThat(availability.onCooldown(ClothesCategory.OUTER, TODAY)).isFalse();
        assertThat(availability.onCooldown(ClothesCategory.SHOES, TODAY)).isFalse();
        assertThat(availability.onCooldown(ClothesCategory.ETC, TODAY)).isFalse();
        assertThat(availability.onCooldown(ClothesCategory.TOP, null)).isFalse();
    }

    @Test
    @DisplayName("쿨다운은 상태를 바꾸지 않는다: 어제 입은 상의도 status는 AVAILABLE")
    void cooldownKeepsStatus() {
        Clothes top = piece("TOP", "AVAILABLE", null);
        assertThat(availability.effectiveStatus(top)).isEqualTo(ClothesStatus.AVAILABLE);
        assertThat(availability.isAvailable(top, TODAY.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("미분류 옷은 상태와 무관하게 입을 수 있는 옷이 아니다")
    void unclassifiedIsNotAvailable() {
        assertThat(availability.isAvailable(piece("UNCLASSIFIED", "AVAILABLE", null), null)).isFalse();
    }

    @Test
    @DisplayName("기본 규칙 파일은 요구사항 기본값과 같다")
    void defaultRulesFile() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/rules/availability-rules.json")) {
            assertThat(AvailabilityRulesLoader.load(in)).isEqualTo(AvailabilityRules.defaults());
        }
    }

    @Test
    @DisplayName("규칙 파일: 지원하지 않는 버전·모르는 카테고리·음수 일수·laundryDays 누락은 기동 실패")
    void badRulesFile() {
        assertThatThrownBy(() -> load("{\"version\": 2, \"laundryDays\": 3}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{\"version\": 1, \"laundryDays\": 3, \"cooldownDays\": {\"HAT\": 1}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{\"version\": 1, \"laundryDays\": 3, \"cooldownDays\": {\"TOP\": -1}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> load("{\"version\": 1}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("규칙 파일 값으로 일수를 바꿀 수 있다")
    void customRules() throws IOException {
        AvailabilityRules rules = load("{\"version\": 1, \"laundryDays\": 1, \"cooldownDays\": {\"top\": 5}}");
        ClothesAvailability custom = new ClothesAvailability(rules, FIXED);
        assertThat(custom.laundryUntil()).isEqualTo(NOW.plusDays(1));
        assertThat(custom.onCooldown(ClothesCategory.TOP, TODAY.minusDays(4))).isTrue();
        assertThat(custom.onCooldown(ClothesCategory.BOTTOM, TODAY)).isFalse();
    }

    private static AvailabilityRules load(String json) throws IOException {
        return AvailabilityRulesLoader.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
