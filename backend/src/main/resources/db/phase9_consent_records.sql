-- 동의 이력 저장 (FR-04, 2026-11-21)
--
-- 변경 요약: consent_records 테이블을 "추가"하고, 기존 users.age_confirmed_at·users.vton_consent_at 값을 이력으로 복사한다.
--           users의 기존 컬럼·값은 바꾸지 않는다 (기존 API와 검사는 계속 그 컬럼을 본다).
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 테이블을 만들고, ConsentBackfill이 아래 백필을 실행한다.
--       그래서 이 스크립트가 필수는 아니다. 배포 전에 미리 적용하거나 결과를 확인하고 싶을 때 수동 실행한다. 모두 멱등.
--
-- 롤백: 옛 코드는 이 테이블을 모르므로 그대로 둬도 된다. 옛 코드로 탈퇴한 사용자의 이력은 남으므로, 롤백 기간에
--       탈퇴가 있었다면 users에 없는 user_id 행을 지운다: DELETE FROM consent_records r WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = r.user_id);

CREATE TABLE IF NOT EXISTS consent_records (
    id           UUID PRIMARY KEY,
    user_id      UUID,
    consent_type VARCHAR(30),
    version      VARCHAR(30),
    agreed_at    TIMESTAMP(6),
    revoked_at   TIMESTAMP(6)
);

CREATE INDEX IF NOT EXISTS idx_consent_records_user_type ON consent_records (user_id, consent_type);

-- 백필: 같은 종류의 유효한(철회되지 않은) 이력이 없는 사용자만 넣으므로 여러 번 실행해도 중복되지 않는다.
INSERT INTO consent_records (id, user_id, consent_type, version, agreed_at, revoked_at)
SELECT gen_random_uuid(), u.id, 'AGE_OVER_14', 'legacy', u.age_confirmed_at, NULL
FROM users u
WHERE u.age_confirmed_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM consent_records r
                  WHERE r.user_id = u.id AND r.consent_type = 'AGE_OVER_14' AND r.revoked_at IS NULL);

INSERT INTO consent_records (id, user_id, consent_type, version, agreed_at, revoked_at)
SELECT gen_random_uuid(), u.id, 'VTON', 'legacy', u.vton_consent_at, NULL
FROM users u
WHERE u.vton_consent_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM consent_records r
                  WHERE r.user_id = u.id AND r.consent_type = 'VTON' AND r.revoked_at IS NULL);

-- 검증: 이력 없이 동의 값만 있는 사용자 수 (0이어야 함)
SELECT count(*) AS users_without_vton_record FROM users u
WHERE u.vton_consent_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM consent_records r WHERE r.user_id = u.id AND r.consent_type = 'VTON' AND r.revoked_at IS NULL);
