package com.fashionapp.domain.user;

import com.fashionapp.global.exception.CustomException;
import com.fashionapp.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 통합 테스트 (MockMvc + 실제 GlobalExceptionHandler, 서비스 계층은 Mockito로 대체).
 * 컨트롤러 계층만 검증 대상이라 실제 SecurityFilterChain/JWT 필터는 비활성화하고,
 * @CurrentUser(=@AuthenticationPrincipal)가 읽을 인증 정보는 SecurityContextHolder에 직접 주입한다.
 * (필터가 꺼져 있으면 SecurityMockMvcRequestPostProcessors.authentication(...)이 기대하는
 * SecurityContextHolderFilter가 돌지 않아 principal이 null로 풀려서, 그 우회로 직접 주입 방식을 사용)
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    // SecurityConfig(@EnableWebSecurity)를 이 슬라이스에 그대로 가져오면 OAuth2/JWT 관련 빈까지
    // 줄줄이 목(mock)으로 채워야 해서, @CurrentUser(=@AuthenticationPrincipal) 해석에 필요한
    // 리졸버만 최소로 등록한다.
    @TestConfiguration
    static class AuthenticationPrincipalTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .nickname("테스터")
                .provider(User.AuthProvider.google)
                .providerId("google-1")
                .build();
        UserPrincipal principal = UserPrincipal.create(user);
        Authentication authToken = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증된 사용자가 내 프로필을 조회하면 200과 프로필 정보를 반환한다")
    void getMyProfile_returnsProfile() throws Exception {
        // given
        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .nickname("테스터")
                .provider("google")
                .build();
        given(userService.getMyProfile(userId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("테스터"));
    }

    @Test
    @DisplayName("체형/취향 프로필을 수정하면 200과 수정된 프로필을 반환한다")
    void updateProfile_returnsUpdatedProfile() throws Exception {
        // given
        UserResponse response = UserResponse.builder()
                .id(userId)
                .nickname("새닉네임")
                .bodyType("SLIM")
                .preferredStyle("CASUAL")
                .build();
        given(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class))).willReturn(response);

        String requestBody = """
                {
                  "nickname": "새닉네임",
                  "height": 175,
                  "weight": 68,
                  "bodyType": "SLIM",
                  "preferredStyle": "CASUAL"
                }
                """;

        // when & then
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                .andExpect(jsonPath("$.data.bodyType").value("SLIM"))
                .andExpect(jsonPath("$.data.preferredStyle").value("CASUAL"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 404와 에러 메시지를 반환한다")
    void getMyProfile_returns404_whenUserNotFound() throws Exception {
        // given
        given(userService.getMyProfile(userId)).willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("유효하지 않은 체형/스타일 값으로 수정 요청하면 400과 에러 메시지를 반환한다")
    void updateProfile_returns400_whenBodyProfileInvalid() throws Exception {
        // given
        given(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class)))
                .willThrow(new CustomException(ErrorCode.INVALID_BODY_PROFILE));

        String requestBody = """
                {
                  "nickname": "테스터",
                  "bodyType": "NOT_A_VALID_TYPE"
                }
                """;

        // when & then
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_BODY_PROFILE.getMessage()));
    }
}
