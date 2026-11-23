-- 살까 말까 판정 때의 예상 1회 가격 (FR-20, 2026-11-23)
--
-- 변경 요약: purchase_checks에 nullable 컬럼 expected_cost_per_wear(BIGINT, 원)를 "추가"한다.
--           마이 탭의 "예상 1회 N원 → 지금 1회 N원"에서 판정 당시 값을 보여주려고 둔다.
--           이전 판정은 당시 계산값을 다시 만들 수 없으므로(그때의 착용 기록·옷장이 필요) 백필하지 않는다 (NULL = 모름).
--           기존 데이터는 지우거나 바꾸지 않는다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 컬럼을 만든다. 이 스크립트는 배포 전에 미리 적용할 때 수동 실행한다. 모두 멱등.
--
-- 롤백: 옛 코드는 이 컬럼을 모르므로 그대로 둬도 된다.

ALTER TABLE purchase_checks ADD COLUMN IF NOT EXISTS expected_cost_per_wear BIGINT;

-- 백필: 없음 (NULL = 판정 당시 예상값 없음). 검증: 음수 값 수 (0이어야 함)
SELECT count(*) AS negative_expected_cost FROM purchase_checks WHERE expected_cost_per_wear < 0;
