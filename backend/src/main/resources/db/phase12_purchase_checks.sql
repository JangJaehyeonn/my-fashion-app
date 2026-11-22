-- 살까 말까 결정·기록 (FR-12, 2026-11-22)
--
-- 변경 요약: purchase_checks 테이블을 "추가"한다. 판정 한 번이 한 행이다. 사진은 저장하지 않는다:
--           temp_photo_key는 30분짜리 임시 사진(tmp/{user_id}/{id}.ext)이 남아 있는 동안만 값이 있고, 옮기거나 지우면 NULL이 된다.
--           decision은 BOUGHT/SKIPPED/UNDECIDED, verdict는 BUY/THINK/SIMILAR 문자열 (DB 체크 제약 없음, clothes.category와 같은 방식).
--           기존 테이블·데이터는 바꾸지 않는다. 옮겨 올 기존 판정 기록이 없어 백필은 없다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 테이블·인덱스를 만든다. 이 스크립트는 배포 전에 미리 적용하거나
--       확인하고 싶을 때 수동 실행한다. 모두 멱등.
--
-- 롤백: 옛 코드는 이 테이블을 모르므로 그대로 둬도 된다. 옛 코드로 탈퇴한 사용자의 남은 행은 아래로 지운다:
--       DELETE FROM purchase_checks p WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = p.user_id);

CREATE TABLE IF NOT EXISTS purchase_checks (
    id             UUID PRIMARY KEY,
    user_id        UUID,
    name           VARCHAR(255),
    category       VARCHAR(20),
    color          VARCHAR(255),
    price          BIGINT,
    verdict        VARCHAR(10),
    plus           INTEGER,
    decision       VARCHAR(10),
    clothes_id     UUID,
    temp_photo_key VARCHAR(255),
    created_at     TIMESTAMP(6) WITH TIME ZONE
);

-- 이름은 엔티티(PurchaseCheck)와 같아 Hibernate가 다시 만들지 않는다.
CREATE INDEX IF NOT EXISTS idx_purchase_checks_user_created ON purchase_checks (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_purchase_checks_created ON purchase_checks (created_at);

-- 검증: 30분이 지났는데 임시 사진 키가 남은 행 수 (스케줄러가 돌고 있으면 0에 가까워야 함)
SELECT count(*) AS stale_temp_photos FROM purchase_checks
WHERE temp_photo_key IS NOT NULL AND created_at < now() - interval '30 minutes';
