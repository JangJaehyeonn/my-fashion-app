package com.fashionapp.domain.outfit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutfitItemRepository extends JpaRepository<OutfitItem, UUID> {
}
