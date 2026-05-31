package com.fashionapp.domain.outfit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutfitCalendarRepository extends JpaRepository<OutfitCalendar, UUID> {
    List<OutfitCalendar> findByUser_IdAndWornDateBetweenOrderByWornDateAsc(UUID userId, LocalDate start, LocalDate end);
    Optional<OutfitCalendar> findByIdAndUser_Id(UUID id, UUID userId);
}
