-- 착용 기록 (FR-10, 2026-11-22)
--
-- 변경 요약: wear_records 테이블을 "추가"한다. 옷 한 벌이 하루에 한 행이다 (같은 날 같은 옷은 (clothes_id, worn_on) 유일 제약으로 한 번만).
--           기존 테이블·데이터는 바꾸지 않는다. 기존 데이터에서 옮겨 올 착용 기록이 없어 백필은 없다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 테이블·제약·인덱스를 만든다. 이 스크립트는 배포 전에 미리 적용하거나
--       확인하고 싶을 때 수동 실행한다. 모두 멱등.
--
-- 롤백: 옛 코드는 이 테이블을 모르므로 그대로 둬도 된다. 옛 코드로 탈퇴했거나 옷을 지운 경우 남은 행은 아래로 지운다:
--       DELETE FROM wear_records r WHERE NOT EXISTS (SELECT 1 FROM clothes c WHERE c.id = r.clothes_id);

CREATE TABLE IF NOT EXISTS wear_records (
    id         UUID PRIMARY KEY,
    user_id    UUID,
    clothes_id UUID,
    worn_on    DATE,
    created_at TIMESTAMP(6)
);

-- ON CONFLICT (clothes_id, worn_on) DO NOTHING 이 이 유일 제약을 쓴다. 이름은 엔티티(WearRecord)와 같아 Hibernate가 다시 만들지 않는다.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_wear_records_clothes_day') THEN
        ALTER TABLE wear_records ADD CONSTRAINT uk_wear_records_clothes_day UNIQUE (clothes_id, worn_on);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_wear_records_user_day ON wear_records (user_id, worn_on);

-- 검증: 옷이 없는 착용 기록 수 (0이어야 함)
SELECT count(*) AS orphan_wear_records FROM wear_records r
WHERE NOT EXISTS (SELECT 1 FROM clothes c WHERE c.id = r.clothes_id);
