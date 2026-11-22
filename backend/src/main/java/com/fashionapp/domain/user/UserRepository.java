package com.fashionapp.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByProviderAndProviderId(User.AuthProvider provider, String providerId);

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    /** FR-29: records the day a revived piece was shown, touching only that column. */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastRevivedOn = :day WHERE u.id = :userId")
    int markRevived(@Param("userId") UUID userId, @Param("day") LocalDate day);
}
