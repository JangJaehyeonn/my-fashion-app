package com.fashionapp.domain.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByUserIdAndConsentTypeAndRevokedAtIsNull(UUID userId, ConsentType consentType);

    List<ConsentRecord> findByUserIdOrderByAgreedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from ConsentRecord r where r.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
