package com.fashionapp.domain.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Enumerated(EnumType.STRING)
    private PreferredStyle preferredStyle;

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
