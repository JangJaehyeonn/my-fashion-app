-- 출퇴근 시각과 이동 수단 (FR-28, 2026-11-23)
--
-- 변경 요약: users에 nullable 컬럼 commute_out_at(TIME), commute_back_at(TIME), travel_mode(WALK/TRANSIT/CAR)를 "추가"한다.
--           값이 없으면(NULL) 하루 날씨는 8시·13시·19시를 쓰므로 백필할 값이 없다 (NULL 그대로가 올바른 기본값).
--           기존 데이터는 지우거나 바꾸지 않는다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 컬럼을 만든다. 이 스크립트는 배포 전에 미리 적용할 때 수동 실행한다. 모두 멱등.
--
-- 롤백: 옛 코드는 세 컬럼을 모르므로 그대로 둬도 된다.

ALTER TABLE users ADD COLUMN IF NOT EXISTS commute_out_at TIME;
ALTER TABLE users ADD COLUMN IF NOT EXISTS commute_back_at TIME;
ALTER TABLE users ADD COLUMN IF NOT EXISTS travel_mode VARCHAR(20);

-- 백필: 없음 (NULL = 설정 안 함). 검증: 알 수 없는 이동 수단 값 수 (0이어야 함)
SELECT count(*) AS unknown_travel_mode FROM users WHERE travel_mode NOT IN ('WALK', 'TRANSIT', 'CAR');
