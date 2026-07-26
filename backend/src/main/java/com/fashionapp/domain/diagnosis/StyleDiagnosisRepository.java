package com.fashionapp.domain.diagnosis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StyleDiagnosisRepository extends JpaRepository<StyleDiagnosis, UUID> {
    List<StyleDiagnosis> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    Optional<StyleDiagnosis> findByIdAndUser_Id(UUID id, UUID userId);
}
