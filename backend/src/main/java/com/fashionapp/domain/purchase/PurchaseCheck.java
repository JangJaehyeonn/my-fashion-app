package com.fashionapp.domain.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-12: one purchase check (살까 말까) and its decision. No photo is kept here: tempPhotoKey points at the temporary
 * photo only while it exists (at most 30 minutes) and is cleared when the photo is moved or deleted.
 *
 * <p>Enum-like values are plain strings (no DB check constraint, like clothes.category). user_id and clothes_id have
 * no FK (same as wear_records, ADR 0009): AccountDeletionService deletes the rows explicitly.
 */
@Entity
@Table(name = "purchase_checks", indexes = {
        @Index(name = "idx_purchase_checks_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_purchase_checks_created", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseCheck {

    /** How long the temporary photo (and so a BUY/SKIP decision) stays available. */
    public static final Duration PHOTO_TTL = Duration.ofMinutes(30);

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String name;

    @Column(length = 20)
    private String category;

    private String color;

    private Long price;

    @Column(length = 10)
    private String verdict;

    private Integer plus;

    /** FR-20: cost per wear estimated at check time (null when unknown); kept so the my tab can compare it later. */
    @Column(name = "expected_cost_per_wear")
    private Long expectedCostPerWear;

    @Column(length = 10)
    private String decision;

    @Column(name = "clothes_id")
    private UUID clothesId;

    @Column(name = "temp_photo_key")
    private String tempPhotoKey;

    @Column(name = "created_at")
    private Instant createdAt;

    PurchaseCheck(UUID id, UUID userId, PurchaseCheckResponse.Item item, PurchaseVerdict verdict, int plus,
                  Long price, Long expectedCostPerWear, String tempPhotoKey, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.name = item.name();
        this.category = item.category().name();
        this.color = item.color();
        this.price = price;
        this.expectedCostPerWear = expectedCostPerWear;
        this.verdict = verdict.name();
        this.plus = plus;
        this.decision = PurchaseDecision.UNDECIDED.name();
        this.tempPhotoKey = tempPhotoKey;
        this.createdAt = createdAt;
    }

    public PurchaseDecision decisionValue() {
        return decision == null ? PurchaseDecision.UNDECIDED : PurchaseDecision.valueOf(decision);
    }

    /** The photo is gone once it was cleaned up or 30 minutes have passed since the check. */
    public boolean isExpired(Instant now) {
        return tempPhotoKey == null || createdAt == null || !now.isBefore(createdAt.plus(PHOTO_TTL));
    }

    void markBought(UUID clothesId) {
        this.decision = PurchaseDecision.BOUGHT.name();
        this.clothesId = clothesId;
    }

    void markSkipped() {
        this.decision = PurchaseDecision.SKIPPED.name();
    }

    void photoRemoved() {
        this.tempPhotoKey = null;
    }
}
