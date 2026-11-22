package com.fashionapp.domain.wear;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-10 over real DB and security: POST /api/wears, clothes list wear fields, summary, deletion cleanup. */
class WearIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired ClothesRepository clothesRepository;
    @Autowired WearRecordRepository wearRecordRepository;
    @Autowired Clock clock;

    private User user;
    private User other;
    private String token;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        user = userRepository.save(newUser("착용기록"));
        other = userRepository.save(newUser("남의옷장"));
        token = jwtTokenProvider.createAccessToken(user.getId());
        today = WearCalendar.today(clock);
    }

    @AfterEach
    void tearDown() {
        for (User u : new User[]{user, other}) {
            wearRecordRepository.deleteAllByUserId(u.getId());
            clothesRepository.deleteAllByUserId(u.getId());
            if (userRepository.existsById(u.getId())) userRepository.deleteById(u.getId());
        }
    }

    private UUID clothes(User owner, String name) {
        return clothesRepository.save(Clothes.builder().user(owner).imageUrl("clothes/" + UUID.randomUUID() + ".jpg")
                .category("TOP").color("네이비").name(name).build()).getId();
    }

    private ResultActions wear(String body) throws Exception {
        return mockMvc.perform(post("/api/wears").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String ids(UUID... ids) {
        StringBuilder sb = new StringBuilder("{\"clothesIds\":[");
        for (int i = 0; i < ids.length; i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(ids[i]).append('"');
        }
        return sb.append("]").toString();
    }

    @Test
    @DisplayName("코디 3벌을 기록하면 옷마다 wearCount 1, lastWornAt 오늘이고 같은 날 다시 기록해도 1회")
    void recordsOncePerPiecePerDay() throws Exception {
        UUID a = clothes(user, "니트");
        UUID b = clothes(user, "슬랙스");
        UUID c = clothes(user, "스니커즈");
        UUID untouched = clothes(user, "셔츠");

        wear(ids(a, b, c) + "}").andExpect(status().isOk()).andExpect(jsonPath("$.data.recorded").value(3));
        wear(ids(a) + ",\"date\":\"" + today + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recorded").value(0));

        mockMvc.perform(get("/api/clothes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + a + "')].wearCount").value(1))
                .andExpect(jsonPath("$.data[?(@.id=='" + c + "')].lastWornAt").value(today.toString()))
                .andExpect(jsonPath("$.data[?(@.id=='" + untouched + "')].wearCount").value(0))
                .andExpect(jsonPath("$.data[?(@.id=='" + untouched + "')].lastWornAt").value(contains(nullValue())));
        assertThat(wearRecordRepository.countByUserId(user.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("남의 옷 ID가 섞이면 400이고 아무것도 기록되지 않는다")
    void otherUsersClothesRejected() throws Exception {
        UUID mine = clothes(user, "니트");
        UUID theirs = clothes(other, "슬랙스");

        wear(ids(mine, theirs) + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(wearRecordRepository.countByUserId(user.getId())).isZero();
        assertThat(wearRecordRepository.countByUserId(other.getId())).isZero();
    }

    @Test
    @DisplayName("요약은 기록과 맞고, 옷을 지우면 그 옷의 기록도, 탈퇴하면 전체 기록이 지워진다")
    void summaryAndCleanup() throws Exception {
        UUID a = clothes(user, "니트");
        UUID b = clothes(user, "슬랙스");
        wear(ids(a) + "}").andExpect(status().isOk());
        wear(ids(b) + ",\"date\":\"" + today.minusYears(1) + "\"}").andExpect(status().isOk());
        wear(ids(b) + ",\"date\":\"" + today.minusYears(2) + "\"}").andExpect(status().isOk());

        mockMvc.perform(get("/api/wears/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekCount").value(1))
                .andExpect(jsonPath("$.data.monthCount").value(1))
                .andExpect(jsonPath("$.data.mostWorn.id").value(b.toString()))
                .andExpect(jsonPath("$.data.mostWorn.name").value("슬랙스"));

        mockMvc.perform(delete("/api/clothes/" + b).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(wearRecordRepository.countByUserId(user.getId())).isEqualTo(1);

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(wearRecordRepository.countByUserId(user.getId())).isZero();
    }

    private static User newUser(String nickname) {
        return User.builder().ageConfirmedAt(java.time.LocalDateTime.now())
                .nickname(nickname)
                .provider(User.AuthProvider.kakao)
                .providerId("wear-test-" + UUID.randomUUID())
                .build();
    }
}
