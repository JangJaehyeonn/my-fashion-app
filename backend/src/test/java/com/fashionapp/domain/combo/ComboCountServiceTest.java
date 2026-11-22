package com.fashionapp.domain.combo;

import com.fashionapp.domain.clothes.Clothes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** FR-09 per-user cache: reuse while the closet is unchanged, recompute after any change. */
class ComboCountServiceTest {

    private final ComboEngine engine = spy(ComboTestSupport.defaultEngine());
    private final ComboCountService service = new ComboCountService(engine);
    private final UUID userId = UUID.randomUUID();

    private static Clothes clothes(String category, String color) {
        return Clothes.builder().id(UUID.randomUUID()).imageUrl("x").category(category).color(color).build();
    }

    private List<Clothes> closet() {
        return new ArrayList<>(List.of(clothes("TOP", "화이트"), clothes("TOP", "네이비"),
                clothes("BOTTOM", "블랙"), clothes("BOTTOM", "베이지"), clothes("SHOES", "화이트")));
    }

    @Test
    @DisplayName("같은 옷장이면 캐시를 쓰고 결과가 같다")
    void sameClosetUsesCache() {
        List<Clothes> closet = closet();

        ComboStats first = service.statsFor(userId, closet);
        List<Clothes> reversed = new ArrayList<>(closet);
        java.util.Collections.reverse(reversed);
        ComboStats second = service.statsFor(userId, reversed);

        assertThat(second).isEqualTo(first);
        assertThat(first.total()).isEqualTo(4);
        verify(engine, times(1)).count(anyList());
    }

    @Test
    @DisplayName("옷을 추가·삭제·수정하면 다시 계산한다")
    void changedClosetRecomputes() {
        List<Clothes> closet = closet();
        assertThat(service.statsFor(userId, closet).total()).isEqualTo(4);

        closet.add(clothes("TOP", "그레이"));
        assertThat(service.statsFor(userId, closet).total()).isEqualTo(6);

        closet.remove(closet.size() - 1);
        assertThat(service.statsFor(userId, closet).total()).isEqualTo(4);

        closet.get(3).editByUser(com.fashionapp.domain.clothes.ClothesCategory.TOP, null, null);
        assertThat(service.statsFor(userId, closet).total()).isEqualTo(3);

        closet.get(2).editByUser(null, "그린", null);
        closet.get(0).editByUser(null, "레드", null);
        assertThat(service.statsFor(userId, closet).total()).isEqualTo(2);
        verify(engine, times(5)).count(anyList());
    }

    @Test
    @DisplayName("캐시는 사용자별이고 evict 후에는 다시 계산한다")
    void cacheIsPerUser() {
        List<Clothes> closet = closet();
        service.statsFor(userId, closet);
        service.statsFor(UUID.randomUUID(), List.of());
        service.evict(userId);
        service.statsFor(userId, closet);

        verify(engine, times(3)).count(anyList());
    }

    @Test
    @DisplayName("가상의 새 옷 +N은 옷장 기준으로 계산한다")
    void additionFor() {
        ComboAddition addition = service.additionFor(closet(),
                new ComboItem(null, com.fashionapp.domain.clothes.ClothesCategory.TOP, "그레이", null, null, null, null));

        assertThat(addition.plus()).isEqualTo(2);
        assertThat(addition.after()).isEqualTo(6);
    }
}
