package com.fashionapp.domain.event;

import com.fashionapp.domain.clothes.Clothes;
import com.fashionapp.domain.clothes.ClothesRepository;
import com.fashionapp.domain.user.User;
import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.domain.wear.WearRecordRepository;
import com.fashionapp.global.jwt.JwtTokenProvider;
import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR-14 over real DB and security: after-commit writes, app-open once a day, write failures, account deletion. */
class EventIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository userRepository;
    @Autowired ClothesRepository clothesRepository;
    @Autowired WearRecordRepository wearRecordRepository;
    @Autowired EventLogRepository eventLogRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().ageConfirmedAt(java.time.LocalDateTime.now())
                .nickname("이벤트기록").provider(User.AuthProvider.google)
                .providerId("event-" + UUID.randomUUID()).build());
        token = jwtTokenProvider.createAccessToken(user.getId());
    }

    @AfterEach
    void tearDown() {
        unblockEvents();
        eventLogRepository.deleteAllByUserId(user.getId());
        wearRecordRepository.deleteAllByUserId(user.getId());
        clothesRepository.deleteAllByUserId(user.getId());
        if (userRepository.existsById(user.getId())) userRepository.deleteById(user.getId());
    }

    private UUID clothes(String name) {
        return clothesRepository.save(Clothes.builder().user(user).imageUrl("clothes/" + UUID.randomUUID() + ".jpg")
                .category("TOP").color("네이비").name(name).build()).getId();
    }

    private ResultActions wear(UUID id) throws Exception {
        return mockMvc.perform(post("/api/wears").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"clothesIds\":[\"" + id + "\"]}"));
    }

    private List<EventLog> events() {
        return eventLogRepository.findByUserIdOrderByCreatedAtAsc(user.getId());
    }

    private void blockEvents() {
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION wearon_test_block_events() RETURNS trigger AS $$ "
                + "BEGIN RAISE EXCEPTION 'events blocked by test'; END; $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS wearon_test_block_events ON events");
        jdbcTemplate.execute("CREATE TRIGGER wearon_test_block_events BEFORE INSERT ON events "
                + "FOR EACH ROW EXECUTE FUNCTION wearon_test_block_events()");
    }

    private void unblockEvents() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS wearon_test_block_events ON events");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS wearon_test_block_events()");
    }

    @Test
    @DisplayName("착용 기록은 커밋 뒤 WEAR_LOGGED로 남고 props에는 개수만 있다")
    void wearLogIsRecordedAfterCommitWithCountsOnly() throws Exception {
        UUID id = clothes("네이비 니트");

        wear(id).andExpect(status().isOk());

        List<EventLog> events = events();
        assertThat(events).extracting(EventLog::getType).containsExactly("WEAR_LOGGED");
        String props = events.get(0).getProps();
        assertThat(props).contains("\"pieces\"", "\"inserted\"").doesNotContain(id.toString(), "니트");
    }

    @Test
    @DisplayName("앱 열기는 같은 날 여러 번 보내도 APP_OPENED 1건")
    void appOpenIsRecordedOncePerDay() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/events/app-open").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        assertThat(events()).extracting(EventLog::getType).containsExactly("APP_OPENED");
    }

    @Test
    @DisplayName("앱 열기는 로그인이 필요하다")
    void appOpenRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/events/app-open")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("events에 쓸 수 없어도 착용 기록과 앱 열기 요청은 성공한다")
    void requestsSucceedWhenEventsCannotBeWritten() throws Exception {
        UUID id = clothes("셔츠");
        blockEvents();

        wear(id).andExpect(status().isOk());
        mockMvc.perform(post("/api/events/app-open").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(wearRecordRepository.countByUserId(user.getId())).isEqualTo(1);
        assertThat(eventLogRepository.countByUserId(user.getId())).isZero();
    }

    @Test
    @DisplayName("탈퇴하면 그 사용자의 이벤트가 0건이 된다")
    void accountDeletionRemovesEvents() throws Exception {
        wear(clothes("슬랙스")).andExpect(status().isOk());
        mockMvc.perform(post("/api/events/app-open").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(eventLogRepository.countByUserId(user.getId())).isEqualTo(2);

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(eventLogRepository.countByUserId(user.getId())).isZero();
    }
}
