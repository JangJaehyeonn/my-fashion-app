package com.fashionapp.domain.combo;

import com.fashionapp.domain.clothes.ClothesCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.fashionapp.domain.clothes.ClothesCategory.BOTTOM;
import static com.fashionapp.domain.clothes.ClothesCategory.ETC;
import static com.fashionapp.domain.clothes.ClothesCategory.OUTER;
import static com.fashionapp.domain.clothes.ClothesCategory.SHOES;
import static com.fashionapp.domain.clothes.ClothesCategory.TOP;
import static com.fashionapp.domain.clothes.ClothesCategory.UNCLASSIFIED;
import static org.assertj.core.api.Assertions.assertThat;

/** FR-09 combo count engine with the default rules file. */
class ComboEngineTest {

    private final ComboEngine engine = ComboTestSupport.defaultEngine();

    private static ComboItem item(ClothesCategory category, String color) {
        return new ComboItem(UUID.randomUUID(), category, color, null, null, null, null);
    }

    private static ComboItem item(ClothesCategory category, String color, String season, String pattern) {
        return new ComboItem(UUID.randomUUID(), category, color, season, pattern, null, null);
    }

    private final ComboItem top1 = item(TOP, "화이트");
    private final ComboItem top2 = item(TOP, "네이비");
    private final ComboItem bottom1 = item(BOTTOM, "블랙");
    private final ComboItem bottom2 = item(BOTTOM, "베이지");
    private final ComboItem shoes1 = item(SHOES, "화이트");

    private List<ComboItem> base() {
        return new ArrayList<>(List.of(top1, top2, bottom1, bottom2, shoes1));
    }

    @Test
    @DisplayName("상의 2·하의 2·신발 1이 모두 어울리면 전체 4, 옷별 수는 그 옷이 든 코디 수")
    void twoTopsTwoBottomsOneShoesMakeFour() {
        ComboStats stats = engine.count(base());

        assertThat(stats.total()).isEqualTo(4);
        assertThat(stats.countOf(top1.id())).isEqualTo(2);
        assertThat(stats.countOf(bottom2.id())).isEqualTo(2);
        assertThat(stats.countOf(shoes1.id())).isEqualTo(4);
    }

    @Test
    @DisplayName("아우터 1벌을 더하면 아우터 없이 4 + 아우터 포함 4 = 8")
    void oneOuterDoublesToEight() {
        List<ComboItem> closet = base();
        ComboItem outer = item(OUTER, "그레이");
        closet.add(outer);

        ComboStats stats = engine.count(closet);

        assertThat(stats.total()).isEqualTo(8);
        assertThat(stats.countOf(outer.id())).isEqualTo(4);
        assertThat(stats.countOf(top1.id())).isEqualTo(4);
        assertThat(stats.countOf(shoes1.id())).isEqualTo(8);
    }

    @Test
    @DisplayName("규칙 파일의 예시 충돌 색(레드–그린) 조합은 세지 않는다")
    void conflictingColorsAreNotCounted() {
        ComboItem red = item(TOP, "레드");
        ComboItem white = item(TOP, "화이트");
        ComboItem green = item(BOTTOM, "그린");
        ComboItem black = item(BOTTOM, "블랙");
        ComboItem shoes = item(SHOES, "블랙");

        ComboStats stats = engine.count(List.of(red, white, green, black, shoes));

        assertThat(stats.total()).isEqualTo(3);
        assertThat(stats.countOf(red.id())).isEqualTo(1);
        assertThat(stats.countOf(green.id())).isEqualTo(1);
        assertThat(stats.countOf(white.id())).isEqualTo(2);
        assertThat(stats.countOf(shoes.id())).isEqualTo(3);
    }

    @Test
    @DisplayName("충돌은 상의·하의뿐 아니라 신발·아우터와의 모든 쌍에 적용된다")
    void conflictAppliesToEveryPair() {
        ComboItem redShoes = item(SHOES, "레드");
        ComboItem greenOuter = item(OUTER, "그린");
        ComboStats stats = engine.count(List.of(top1, bottom1, redShoes, greenOuter));

        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.countOf(greenOuter.id())).isZero();
    }

    @Test
    @DisplayName("계절 충돌(SUMMER–WINTER)과 패턴 충돌(체크–스트라이프)은 세지 않고, null 속성은 충돌이 아니다")
    void seasonAndStyleConflicts() {
        ComboItem summerTop = item(TOP, "화이트", "SUMMER", null);
        ComboItem winterBottom = item(BOTTOM, "블랙", "WINTER", null);
        ComboItem checkTop = item(TOP, "화이트", null, "체크");
        ComboItem stripeBottom = item(BOTTOM, "블랙", null, "스트라이프");
        ComboItem shoes = item(SHOES, "블랙");

        assertThat(engine.count(List.of(summerTop, winterBottom, shoes)).total()).isZero();
        assertThat(engine.count(List.of(checkTop, stripeBottom, shoes)).total()).isZero();
        assertThat(engine.count(List.of(summerTop, stripeBottom, shoes)).total()).isEqualTo(1);
    }

    @Test
    @DisplayName("무채색은 원색 충돌 쌍과 무관하게 어떤 색과도, 같은 색끼리도 어울린다")
    void neutralAndSameColorsAlwaysMatch() {
        ComboItem redTop = item(TOP, "레드");
        ComboItem redBottom = item(BOTTOM, "레드");
        ComboItem greenShoes = item(SHOES, "그린");
        ComboItem whiteShoes = item(SHOES, "화이트");

        ComboStats stats = engine.count(List.of(redTop, redBottom, greenShoes, whiteShoes));

        assertThat(stats.total()).isEqualTo(1);
        assertThat(stats.countOf(whiteShoes.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("미분류·기타 옷은 어떤 수에도 포함되지 않는다")
    void unclassifiedAndEtcAreExcluded() {
        List<ComboItem> closet = base();
        ComboItem unclassified = item(UNCLASSIFIED, null);
        ComboItem etc = item(ETC, "블랙");
        closet.add(unclassified);
        closet.add(etc);

        ComboStats stats = engine.count(closet);

        assertThat(stats.total()).isEqualTo(4);
        assertThat(stats.countOf(unclassified.id())).isZero();
        assertThat(stats.countOf(etc.id())).isZero();
    }

    @Test
    @DisplayName("상의 3·하의 2·신발 2·아우터 1이면 전체 24, 옷별 수는 카테고리마다 정해진 값")
    void perClothesCountsMatchValidCombos() {
        ComboItem top3 = item(TOP, "그레이");
        ComboItem shoes2 = item(SHOES, "블랙");
        ComboItem outer = item(OUTER, "네이비");
        ComboStats stats = engine.count(List.of(top1, top2, top3, bottom1, bottom2, shoes1, shoes2, outer));

        assertThat(stats.total()).isEqualTo(24);
        assertThat(stats.countOf(top3.id())).isEqualTo(8);
        assertThat(stats.countOf(bottom1.id())).isEqualTo(12);
        assertThat(stats.countOf(shoes2.id())).isEqualTo(12);
        assertThat(stats.countOf(outer.id())).isEqualTo(12);
    }

    @Test
    @DisplayName("가상의 새 옷(속성만)을 넣으면 늘어나는 코디 수(+N)와 새 옷이 든 유효 조합 목록을 준다")
    void additionOfHypotheticalPiece() {
        ComboItem candidate = new ComboItem(null, TOP, "레드", null, null, null, null);
        List<ComboItem> closet = base();
        closet.add(item(BOTTOM, "그린"));

        ComboAddition addition = engine.addition(closet, candidate);

        // 하의 3벌 중 그린은 레드와 충돌 → 2개
        assertThat(addition.plus()).isEqualTo(2);
        assertThat(addition.before()).isEqualTo(6);
        assertThat(addition.after()).isEqualTo(8);
        assertThat(addition.combos()).hasSize(2)
                .allSatisfy(combo -> assertThat(combo.top()).isSameAs(candidate))
                .extracting(Combo::bottom).containsExactlyInAnyOrder(bottom1, bottom2);
        assertThat(engine.count(closet).total()).isEqualTo(6);
    }

    @Test
    @DisplayName("코디에 들지 않는 가상의 옷(기타)은 +0이다")
    void additionOfEtcIsZero() {
        ComboAddition addition = engine.addition(base(), new ComboItem(null, ETC, "블랙", null, null, null, null));

        assertThat(addition.plus()).isZero();
        assertThat(addition.combos()).isEmpty();
        assertThat(addition.after()).isEqualTo(addition.before());
    }

    @Test
    @DisplayName("같은 옷장이면 입력 순서와 무관하게 결과가 같다")
    void deterministic() {
        List<ComboItem> closet = base();
        closet.add(item(OUTER, "그레이"));
        closet.add(item(TOP, "레드"));
        List<ComboItem> shuffled = new ArrayList<>(closet);
        Collections.reverse(shuffled);
        ComboItem candidate = new ComboItem(null, BOTTOM, "그린", null, null, null, null);

        assertThat(engine.count(shuffled)).isEqualTo(engine.count(closet));
        assertThat(engine.addition(shuffled, candidate)).isEqualTo(engine.addition(closet, candidate));
    }
}
