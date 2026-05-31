package com.fashionapp.domain.clothes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {
    List<Clothes> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    Optional<Clothes> findByIdAndUser_Id(UUID id, UUID userId);
    void deleteByUser_Id(UUID userId);
}
