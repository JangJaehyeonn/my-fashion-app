package com.fashionapp.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
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

    @Column(nullable = false)
    private String providerId;

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

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum AuthProvider {
        google, kakao
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
