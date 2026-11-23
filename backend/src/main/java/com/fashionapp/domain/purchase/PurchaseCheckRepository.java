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

    /**
     * FR-13: replaces the analysed attributes and the judgement of a check that is still undecided, so a decision made
     * while re-judging is never overwritten. Returns rows changed (0 or 1).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update PurchaseCheck p set p.name = :name, p.category = :category, p.color = :color, "
            + "p.verdict = :verdict, p.plus = :plus, p.expectedCostPerWear = :expectedCostPerWear "
            + "where p.id = :id and p.decision = 'UNDECIDED'")
    int updateUndecided(@Param("id") UUID id, @Param("name") String name, @Param("category") String category,
                        @Param("color") String color, @Param("verdict") String verdict, @Param("plus") int plus,
                        @Param("expectedCostPerWear") Long expectedCostPerWear);

    /** FR-20: checks made since {@code from}. */
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant from);

    /** FR-20: combos added by pieces bought from checks made since {@code from} (0 without any). */
    @Query("select coalesce(sum(p.plus), 0) from PurchaseCheck p where p.userId = :userId "
            + "and p.decision = 'BOUGHT' and p.createdAt >= :from")
    long sumBoughtPlusSince(@Param("userId") UUID userId, @Param("from") Instant from);

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
