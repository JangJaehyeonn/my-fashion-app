package com.fashionapp.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventLogRepository extends JpaRepository<EventLog, UUID> {

    @Modifying
    @Query(value = "INSERT INTO events (id, user_id, type, props, created_at) "
            + "VALUES (gen_random_uuid(), :userId, :type, CAST(:props AS jsonb), :createdAt)", nativeQuery = true)
    int insert(@Param("userId") UUID userId, @Param("type") String type, @Param("props") String props,
               @Param("createdAt") Instant createdAt);

    boolean existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(UUID userId, String type, Instant from);

    List<EventLog> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from EventLog e where e.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from EventLog e where e.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
