-- 옷 상태(세탁 중)와 상태 종료 시각 (FR-27, 2026-11-22)
--
-- 변경 요약: clothes에 nullable 컬럼 status(AVAILABLE/LAUNDRY), status_until(LAUNDRY가 다시 AVAILABLE로 보이는 시각)을 "추가"한다.
--           기존 행은 status = 'AVAILABLE'로 백필한다 (코드는 NULL도 AVAILABLE로 읽으므로 백필 전에도 동작은 같다).
--           세탁 기간이 지났는지는 조회 때 계산하므로 배치·추가 백필은 없다. 기존 데이터는 지우거나 바꾸지 않는다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 컬럼을 만든다. 이 스크립트는 배포 전에 미리 적용하거나 백필할 때 수동 실행한다. 모두 멱등.
--
-- 롤백: 옛 코드는 두 컬럼을 모르므로 그대로 둬도 된다.

ALTER TABLE clothes ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE clothes ADD COLUMN IF NOT EXISTS status_until TIMESTAMP(6);

UPDATE clothes SET status = 'AVAILABLE' WHERE status IS NULL;

-- 검증: 알 수 없는 상태 값 수 (0이어야 함)
SELECT count(*) AS unknown_status FROM clothes WHERE status NOT IN ('AVAILABLE', 'LAUNDRY');
