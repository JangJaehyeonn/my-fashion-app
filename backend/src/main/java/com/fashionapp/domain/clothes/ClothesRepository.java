package com.fashionapp.domain.clothes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {
    List<Clothes> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    Optional<Clothes> findByIdAndUser_Id(UUID id, UUID userId);

    // 회원 탈퇴용 — 행을 지우기 전에 S3에서 지울 이미지 URL을 모아 둠
    @Query("select e.imageUrl from Clothes e where e.user.id = :userId")
    List<String> findImageUrlsByUserId(@Param("userId") UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from Clothes e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
