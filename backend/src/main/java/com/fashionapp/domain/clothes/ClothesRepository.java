package com.fashionapp.domain.clothes;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClothesRepository extends JpaRepository<Clothes, UUID> {
    List<Clothes> findByUser_IdOrderByCreatedAtDesc(UUID userId);
    Optional<Clothes> findByIdAndUser_Id(UUID id, UUID userId);
    // FR-10: only the user's own clothes among the ids (ownership check)
    List<Clothes> findByUser_IdAndIdIn(UUID userId, Collection<UUID> ids);

    // FR-08 재분류 백필: 새 속성(fit·material·pattern·season)이 모두 비어 있는 옷을 id 순으로 (keyset 페이지)
    @Query("select e from Clothes e where e.fit is null and e.material is null and e.pattern is null "
            + "and e.season is null and e.id > :afterId order by e.id")
    List<Clothes> findMissingAttributesAfter(@Param("afterId") UUID afterId, Pageable pageable);

    // 회원 탈퇴용 — 행을 지우기 전에 S3에서 지울 이미지 URL을 모아 둠
    @Query("select e.imageUrl from Clothes e where e.user.id = :userId")
    List<String> findImageUrlsByUserId(@Param("userId") UUID userId);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("delete from Clothes e where e.user.id = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
