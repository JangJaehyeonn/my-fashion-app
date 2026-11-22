package com.fashionapp.domain.wear;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WearRecordRepository extends JpaRepository<WearRecord, UUID> {

    /** Same clothes on the same day is stored once: a duplicate is silently ignored. Returns rows inserted (0 or 1). */
    @Modifying
    @Query(value = "INSERT INTO wear_records (id, user_id, clothes_id, worn_on, created_at) "
            + "VALUES (gen_random_uuid(), :userId, :clothesId, :wornOn, :createdAt) "
            + "ON CONFLICT (clothes_id, worn_on) DO NOTHING", nativeQuery = true)
    int insertIgnoringDuplicate(@Param("userId") UUID userId, @Param("clothesId") UUID clothesId,
                                @Param("wornOn") LocalDate wornOn, @Param("createdAt") LocalDateTime createdAt);

    @Query("select new com.fashionapp.domain.wear.WearStat(r.clothesId, count(r), max(r.wornOn)) "
            + "from WearRecord r where r.userId = :userId group by r.clothesId")
    List<WearStat> findStatsByUserId(@Param("userId") UUID userId);

    /** FR-11: per-piece stats limited to days in [from, to]. */
    @Query("select new com.fashionapp.domain.wear.WearStat(r.clothesId, count(r), max(r.wornOn)) "
            + "from WearRecord r where r.userId = :userId and r.wornOn between :from and :to group by r.clothesId")
    List<WearStat> findStatsByUserIdBetween(@Param("userId") UUID userId, @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** FR-11: the earliest day the user recorded a wear, or null with no records. */
    @Query("select min(r.wornOn) from WearRecord r where r.userId = :userId")
    LocalDate findFirstWornOn(@Param("userId") UUID userId);

    long countByUserIdAndWornOnBetween(UUID userId, LocalDate from, LocalDate to);

    long countByUserId(UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from WearRecord r where r.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from WearRecord r where r.clothesId = :clothesId")
    int deleteAllByClothesId(@Param("clothesId") UUID clothesId);
}
