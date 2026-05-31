package com.fashionapp.domain.outfit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutfitRepository extends JpaRepository<Outfit, UUID> {
    List<Outfit> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    Optional<Outfit> findByIdAndUser_Id(UUID id, UUID userId);
}
