package com.fashionapp.domain.clothes;

import com.fashionapp.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clothes", indexes = {
        @Index(name = "idx_clothes_user_id", columnList = "user_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Clothes {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String imageUrl;

    // ClothesCategory 이름을 문자열로 저장 (ClothesCategory.from 참고)
    private String category;

    private String color;

    private String name;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** Overwrites category, color and name with an AI classification result. */
    public void applyClassification(ClothesClassification classification) {
        this.category = classification.category().name();
        this.color = classification.color();
        this.name = classification.name();
    }

    /** User edit: null arguments keep the current value. */
    public void editByUser(ClothesCategory category, String color, String name) {
        if (category != null) {
            this.category = category.name();
        }
        if (color != null) {
            this.color = color;
        }
        if (name != null) {
            this.name = name;
        }
    }
}
