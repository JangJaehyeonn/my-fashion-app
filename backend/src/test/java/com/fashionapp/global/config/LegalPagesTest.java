package com.fashionapp.global.config;

import com.fashionapp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /terms, /privacy, /account-deletion: 로그인 없이 UTF-8 HTML로 열리고, 다른 경로는 여전히 인증이 필요 */
class LegalPagesTest extends IntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("이용약관·처리방침·계정 삭제 안내는 토큰 없이 200, text/html;charset=UTF-8")
    void pagesOpenWithoutAuth() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("이용약관")))
                .andExpect(content().string(containsString("만 14세")))
                .andExpect(content().string(containsString("[시행일")));
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("개인정보처리방침")))
                .andExpect(content().string(containsString("OpenWeatherMap")));
        mockMvc.perform(get("/account-deletion"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("회원 탈퇴")));
    }

    @Test
    @DisplayName("FR-15: 처리방침에 살까 말까 사진 30분·기록 항목, 착용·이벤트 기록, OpenAI 임베딩 텍스트가 있다")
    void privacyCoversNewFeatures() throws Exception {
        String page = mockMvc.perform(get("/privacy")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> collected = rows(section(page, "수집하는 정보"));
        String purchase = only(collected, "<td>살까 말까</td>");
        assertThat(purchase).contains("30분", "이름", "카테고리", "색상", "가격", "판정", "사진 없이");
        assertThat(only(collected, "<td>착용 기록</td>")).contains("날짜");
        assertThat(only(collected, "<td>이벤트 기록</td>")).contains("이용 지표").doesNotContain("닉네임");

        String retention = section(page, "보유 기간");
        assertThat(retention).contains("<b>살까 말까 사진</b>: 촬영 후 최대 30분", "<b>착용 기록</b>",
                "<b>이벤트 기록</b>: 기록한 날부터 1년");

        String openAi = section(page, "국외 이전").split("<h3>OpenAI</h3>")[1].split("</table>")[0];
        assertThat(only(rows(openAi), "<th>이전 항목</th>")).contains("임베딩용 옷 속성 텍스트");
    }

    @Test
    @DisplayName("계정 삭제 안내에 착용·살까 말까·이벤트 기록이 삭제 대상으로 있다")
    void accountDeletionListsNewRecords() throws Exception {
        mockMvc.perform(get("/account-deletion"))
                .andExpect(content().string(containsString("<li>착용 기록</li>")))
                .andExpect(content().string(containsString("살까 말까 판정 기록")))
                .andExpect(content().string(containsString("이벤트 기록")));
    }

    private static String section(String page, String heading) {
        return Arrays.stream(page.split("(?=<h2>)"))
                .filter(p -> p.startsWith("<h2>") && p.substring(0, p.indexOf("</h2>")).contains(heading))
                .findFirst().orElseThrow(() -> new AssertionError("no section: " + heading));
    }

    private static List<String> rows(String html) {
        return Arrays.stream(html.split("<tr>")).skip(1).map(r -> r.split("</tr>")[0]).toList();
    }

    private static String only(List<String> rows, String marker) {
        List<String> matched = rows.stream().filter(r -> r.contains(marker)).toList();
        assertThat(matched).hasSize(1);
        return matched.get(0);
    }

    @Test
    @DisplayName("원본 경로(/legal/**)는 노출되지 않고 /api 경로는 여전히 401")
    void otherPathsStillProtected() throws Exception {
        mockMvc.perform(get("/legal/privacy.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/legal/terms.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/images/clothes/x/y.jpg")).andExpect(status().isUnauthorized());
    }
}
