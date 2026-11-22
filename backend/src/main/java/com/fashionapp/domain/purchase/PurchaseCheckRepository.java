package com.fashionapp.domain.purchase;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseCheckRepository extends JpaRepository<PurchaseCheck, UUID> {

    Optional<PurchaseCheck> findByIdAndUserId(UUID id, UUID userId);

    /** FR-12 history: newest first (ties by id so the order is stable). */
    List<PurchaseCheck> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    /** Scheduler: checks whose temporary photo is still there although its 30 minutes are over. */
    @Query("select p from PurchaseCheck p where p.tempPhotoKey is not null and p.createdAt < :cutoff order by p.createdAt")
    List<PurchaseCheck> findWithTempPhotoCreatedBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    /**
     * Moves an undecided check to {@code decision} only if nobody decided it yet, so two concurrent decisions cannot
     * both run (e.g. two BUYs creating two clothes). Returns rows changed (0 or 1).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PurchaseCheck p set p.decision = :decision where p.id = :id and p.decision = 'UNDECIDED'")
    int claimUndecided(@Param("id") UUID id, @Param("decision") String decision);

    /** Undo of {@link #claimUndecided} when the decision could not be completed. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PurchaseCheck p set p.decision = 'UNDECIDED' where p.id = :id and p.decision = :decision")
    int releaseClaim(@Param("id") UUID id, @Param("decision") String decision);

    /** Account deletion: ids of the checks whose temporary photo / draft may still exist. */
    @Query("select p.id from PurchaseCheck p where p.userId = :userId and p.tempPhotoKey is not null")
    List<UUID> findIdsWithTempPhoto(@Param("userId") UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from PurchaseCheck p where p.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
