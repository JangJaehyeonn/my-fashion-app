package com.fashionapp.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
// login_id의 unique 제약은 @Column(unique = true)로 생성 (ddl-auto: update가 새 컬럼과 함께 추가)
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_provider_id", columnList = "provider_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = true)
    private String email;

    private String nickname;

    private String profileImageUrl;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    // OAuth: 공급자의 사용자 ID. LOCAL(아이디·비밀번호): loginId와 같은 값
    @Column(nullable = false)
    private String providerId;

    // 아이디·비밀번호 가입 사용자만 값이 있음 (OAuth 사용자는 null)
    @Column(name = "login_id", unique = true, length = 20)
    private String loginId;

    // BCrypt 해시. OAuth 사용자는 null. 응답·로그에 절대 포함하지 않는다 (UserResponse에 없음)
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    private Integer height;

    private Integer weight;

    @Enumerated(EnumType.STRING)
    private BodyType bodyType;

    // 레거시 단일 선호 스타일. 다중 선택(preferredStyles) 도입 이후에도 옛 앱·롤백 호환을 위해 첫 번째 값을 계속 같이 기록한다.
    @Enumerated(EnumType.STRING)
    private PreferredStyle preferredStyle;

    // 다중 선택 선호 스타일 — enum 이름을 쉼표로 이어 붙인 값(예: "CASUAL,MINIMAL").
    // null = 아직 이전되지 않음(preferredStyle로 폴백), "" = 사용자가 명시적으로 모두 해제
    @Column(name = "preferred_styles")
    private String preferredStyles;

    // "만 14세 이상입니다" 확인 시각. null이면 미확인 — 확인 전에는 대부분의 API가 403 (AgeGateInterceptor)
    @Column(name = "age_confirmed_at")
    private LocalDateTime ageConfirmedAt;

    // 가상 피팅 사진 전송(Hugging Face 공개 서비스) 동의 시각. null이면 미동의 — 피팅 API가 403으로 막는다
    @Column(name = "vton_consent_at")
    private LocalDateTime vtonConsentAt;

    // FR-29: Korean day a long-unworn piece was last put first in a closet recommendation. null = never
    @Column(name = "last_revived_on")
    private LocalDate lastRevivedOn;

    // FR-28: usual going-out / coming-back times (Korean time) and how the user travels. null = not set
    @Column(name = "commute_out_at")
    private LocalTime commuteOutAt;

    @Column(name = "commute_back_at")
    private LocalTime commuteBackAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", length = 20)
    private TravelMode travelMode;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // DB에 이름 그대로 저장됨. 기존 값(google, kakao)이 OAuth registrationId라 소문자 — 같은 규칙으로 local
    public enum AuthProvider {
        google, kakao, local
    }

    /** 아이디·비밀번호 가입 사용자 */
    public static User createLocal(String loginId, String passwordHash, String nickname) {
        return User.builder()
                .provider(AuthProvider.local)
                .providerId(loginId)
                .loginId(loginId)
                .passwordHash(passwordHash)
                .nickname(nickname)
                .build();
    }

    public void confirmAge(LocalDateTime at) {
        if (this.ageConfirmedAt == null) {
            this.ageConfirmedAt = at;
        }
    }

    public boolean isAgeConfirmed() {
        return ageConfirmedAt != null;
    }

    public void grantVtonConsent(LocalDateTime at) {
        this.vtonConsentAt = at;
    }

    public void revokeVtonConsent() {
        this.vtonConsentAt = null;
    }

    public boolean hasVtonConsent() {
        return vtonConsentAt != null;
    }

    public boolean isLocal() {
        return provider == AuthProvider.local;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** FR-28: replaces the commute profile as a whole (null clears a value). */
    public void updateCommute(LocalTime outAt, LocalTime backAt, TravelMode mode) {
        this.commuteOutAt = outAt;
        this.commuteBackAt = backAt;
        this.travelMode = mode;
    }

    public enum TravelMode {
        WALK, TRANSIT, CAR
    }

    public enum BodyType {
        SLIM, NORMAL, MUSCULAR, CHUBBY
    }

    public enum PreferredStyle {
        CASUAL, FORMAL, SPORTY, STREET, VINTAGE, MINIMAL
    }

    /** 선호 스타일 목록. 다중 선택 값이 있으면 그것을, 아직 이전 전이면 레거시 단일 값을 돌려준다. */
    public List<PreferredStyle> getPreferredStyleList() {
        if (preferredStyles == null) {
            return preferredStyle != null ? List.of(preferredStyle) : List.of();
        }
        List<PreferredStyle> result = new ArrayList<>();
        for (String token : preferredStyles.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) continue;
            try {
                result.add(PreferredStyle.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // 더 이상 존재하지 않는 enum 값은 무시
            }
        }
        return result;
    }

    public List<String> getPreferredStyleNames() {
        return getPreferredStyleList().stream().map(Enum::name).toList();
    }

    /** 다중 선택 저장. 레거시 단일 컬럼에도 첫 값을 함께 기록해 옛 클라이언트/롤백과 호환한다. */
    public void updatePreferredStyles(List<PreferredStyle> styles) {
        this.preferredStyles = styles.stream().map(Enum::name).collect(Collectors.joining(","));
        this.preferredStyle = styles.isEmpty() ? null : styles.get(0);
    }

    // 주의: 로그인(OAuth) 시 기존 값을 그대로 다시 넣는 용도로도 쓰이므로 preferredStyles는 건드리지 않는다.
    public User update(String nickname, String profileImageUrl,
                        Integer height, Integer weight,
                        BodyType bodyType, PreferredStyle preferredStyle) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.height = height;
        this.weight = weight;
        this.bodyType = bodyType;
        this.preferredStyle = preferredStyle;
        return this;
    }
}
