package com.fashionapp.domain.wear;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FR-10: one piece of clothing worn on one day. At most one row per (clothes, day).
 *
 * <p>user_id and clothes_id have no FK on purpose (see docs/adr/0009-wear-records.md): ClothesService.delete and
 * AccountDeletionService delete the rows explicitly. Rows are inserted with native SQL (WearRecordRepository.insertIgnoringDuplicate).
 */
@Entity
@Table(name = "wear_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_wear_records_clothes_day", columnNames = {"clothes_id", "worn_on"}),
        indexes = @Index(name = "idx_wear_records_user_day", columnList = "user_id, worn_on"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WearRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "clothes_id")
    private UUID clothesId;

    @Column(name = "worn_on")
    private LocalDate wornOn;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
