-- 선호 스타일 다중 선택 (2026-10-02)
--
-- 변경 요약: users 테이블에 preferred_styles(쉼표 구분 enum 이름, 예: 'CASUAL,MINIMAL') 컬럼을 "추가"한다.
--           기존 preferred_style 컬럼은 변경·삭제하지 않는다 (옛 앱 빌드와 롤백 호환 — 새 코드가 첫 번째 값을 계속 같이 기록).
--
-- 의미: preferred_styles IS NULL  → 아직 이전 전. 코드가 preferred_style 값으로 폴백해서 읽음
--       preferred_styles = ''     → 사용자가 모든 스타일을 명시적으로 해제
--
-- 적용: 서버가 Hibernate ddl-auto: update 로 기동 시 컬럼을 자동 추가하므로 이 스크립트가 필수는 아니다.
--       배포 전에 컬럼을 미리 만들어 두거나 백필을 먼저 확인하고 싶을 때 수동 실행한다. 두 문장 모두 멱등.

ALTER TABLE users ADD COLUMN IF NOT EXISTS preferred_styles VARCHAR(255);

-- 기존 단일 값을 새 컬럼으로 복사 (선택 — 코드가 NULL이면 preferred_style로 폴백하므로 안 해도 동작은 같음)
UPDATE users
SET preferred_styles = preferred_style
WHERE preferred_styles IS NULL
  AND preferred_style IS NOT NULL;

-- 검증: 0이어야 한다 (선택한 스타일이 있는데 새 컬럼이 비어 있는 행)
-- SELECT count(*) FROM users WHERE preferred_style IS NOT NULL AND preferred_styles IS NULL;

-- 롤백: 새 코드를 되돌려도 기존 컬럼은 그대로라 데이터 손실 없음 (다중 선택 중 첫 번째 값만 남음).
--       새 컬럼을 지우려면 (필요할 때만):  ALTER TABLE users DROP COLUMN preferred_styles;
