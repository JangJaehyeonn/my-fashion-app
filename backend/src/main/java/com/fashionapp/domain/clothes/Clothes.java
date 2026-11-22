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

    // FR-07 attributes (nullable, null = unknown). The embedding vector(1536) column is not mapped here:
    // it is written with native SQL by ClothesEmbeddingStore (see docs/adr/0006-clothes-embedding-pgvector.md).
    @Column(length = 50)
    private String fit;

    @Column(length = 50)
    private String material;

    @Column(length = 50)
    private String pattern;

    // ClothesSeason name
    @Column(length = 10)
    private String season;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // FR-27 ClothesStatus name (nullable; null = AVAILABLE for rows before FR-27). Read through ClothesAvailability.
    @Column(length = 20)
    private String status;

    // FR-27: for LAUNDRY, the moment it counts as AVAILABLE again. Null for AVAILABLE.
    private LocalDateTime statusUntil;

    @PrePersist
    void defaultStatus() {
        if (status == null) {
            status = ClothesStatus.AVAILABLE.name();
        }
    }

    /** FR-27 status change. {@code until} is kept only for LAUNDRY. */
    public void changeStatus(ClothesStatus newStatus, LocalDateTime until) {
        this.status = newStatus.name();
        this.statusUntil = newStatus == ClothesStatus.LAUNDRY ? until : null;
    }

    /** Overwrites category, color, name and the FR-07 attributes with an AI classification result. */
    public void applyClassification(ClothesClassification classification) {
        this.category = classification.category().name();
        this.color = classification.color();
        this.name = classification.name();
        this.fit = classification.fit();
        this.material = classification.material();
        this.pattern = classification.pattern();
        this.season = classification.season() == null ? null : classification.season().name();
    }

    /**
     * FR-08 reclassify backfill: an unclassified item takes the whole classification; otherwise only empty fields are
     * filled so values the user (or an earlier classification) set are kept.
     */
    public void fillMissingFrom(ClothesClassification classification) {
        if (ClothesCategory.from(category) == ClothesCategory.UNCLASSIFIED) {
            applyClassification(classification);
            return;
        }
        color = color == null ? classification.color() : color;
        name = name == null ? classification.name() : name;
        fit = fit == null ? classification.fit() : fit;
        material = material == null ? classification.material() : material;
        pattern = pattern == null ? classification.pattern() : pattern;
        if (season == null && classification.season() != null) {
            season = classification.season().name();
        }
    }

    /** User edit of the FR-07 attributes: null arguments keep the current value. */
    public void editAttributesByUser(String fit, String material, String pattern, ClothesSeason season) {
        if (fit != null) {
            this.fit = fit;
        }
        if (material != null) {
            this.material = material;
        }
        if (pattern != null) {
            this.pattern = pattern;
        }
        if (season != null) {
            this.season = season.name();
        }
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
