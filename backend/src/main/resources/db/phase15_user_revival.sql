-- 안 입는 옷 살리기 마지막 노출일 (FR-29, 2026-11-22)
--
-- 변경 요약: users에 nullable 컬럼 last_revived_on(DATE, 한국 날짜)을 "추가"한다.
--           NULL은 '아직 노출한 적 없음'이라 백필이 필요 없다 (기존 사용자는 다음 추천에서 처음 노출 대상이 된다).
--           기존 데이터는 지우거나 바꾸지 않는다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 컬럼을 만든다. 이 스크립트는 배포 전에 미리 적용할 때 수동 실행한다. 멱등.
--
-- 롤백: 옛 코드는 이 컬럼을 모르므로 그대로 둬도 된다.

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_revived_on DATE;

-- 백필: 없음 (NULL = 노출 기록 없음이 올바른 초기값)

-- 검증: 미래 날짜로 기록된 행 수 (0이어야 함)
SELECT count(*) AS future_revived FROM users WHERE last_revived_on > CURRENT_DATE + 1;
