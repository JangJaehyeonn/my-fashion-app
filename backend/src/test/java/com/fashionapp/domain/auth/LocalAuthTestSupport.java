package com.fashionapp.domain.auth;

import com.fashionapp.domain.user.UserRepository;
import com.fashionapp.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 아이디·비밀번호 인증 통합 테스트 공통: 고유 아이디 생성, 가입·로그인 요청, 사용자·Redis 키 정리 */
abstract class LocalAuthTestSupport extends IntegrationTest {

    static final String PASSWORD = "secret1234";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;

    private final List<String> loginIds = new ArrayList<>();

    @AfterEach
    void cleanUpLocalUsers() {
        for (String loginId : loginIds) {
            userRepository.findByLoginId(loginId).ifPresent(u -> {
                redisTemplate.delete("refresh:" + u.getId());
                userRepository.delete(u);
            });
            redisTemplate.delete(List.of("login:fail:" + loginId, "login:lock:" + loginId));
        }
    }

    /** 테스트마다 겹치지 않는 아이디 (영문 소문자+숫자, 16자) */
    String newLoginId() {
        String id = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        loginIds.add(id);
        return id;
    }

    ResultActions signup(String loginId, String password, String nickname) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json("loginId", loginId, "password", password, "nickname", nickname, "ageOver14", "true")));
    }

    /** 가입하고 응답의 토큰을 돌려줌 [accessToken, refreshToken] */
    String[] signupOk(String loginId) throws Exception {
        String body = signup(loginId, PASSWORD, "테스터").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new String[]{JsonPath.read(body, "$.data.accessToken"), JsonPath.read(body, "$.data.refreshToken")};
    }

    static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(keyValues[i]).append("\":");
            sb.append(keyValues[i + 1] == null ? "null" : "\"" + keyValues[i + 1] + "\"");
        }
        return sb.append('}').toString();
    }
}
