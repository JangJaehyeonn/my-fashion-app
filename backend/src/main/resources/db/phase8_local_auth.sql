-- 아이디·비밀번호 가입 (2026-11-02)
--
-- 변경 요약: users에 login_id(unique, nullable), password_hash(nullable) 컬럼을 "추가"하고,
--           provider CHECK 제약에 'local'을 허용한다. 기존 OAuth 사용자 행은 두 컬럼이 NULL로 남으며 변경되지 않는다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 두 컬럼과 unique 제약을 추가하고, UserSchemaPatch가 provider 제약을 고친다.
--       그래서 이 스크립트가 필수는 아니다. 배포 전에 미리 적용하거나 결과를 확인하고 싶을 때 수동 실행한다. 모두 멱등.
--
-- 롤백: 새 코드를 되돌려도 추가된 컬럼은 NULL이라 옛 코드가 무시한다. 'local' 사용자 행이 있으면 옛 코드의 enum에
--       없는 값이라 해당 행 조회 시 오류가 나므로, 롤백 전에 local 사용자 처리 방침을 정해야 한다.

ALTER TABLE users ADD COLUMN IF NOT EXISTS login_id VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'users'::regclass AND contype = 'u'
                   AND pg_get_constraintdef(oid) = 'UNIQUE (login_id)') THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_login_id UNIQUE (login_id);
    END IF;
END $$;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_provider_check;
ALTER TABLE users ADD CONSTRAINT users_provider_check CHECK (provider IN ('google', 'kakao', 'local'));

-- 검증: 기존 OAuth 사용자는 login_id·password_hash가 모두 NULL이어야 한다 (0이어야 함)
SELECT count(*) AS oauth_rows_with_password FROM users WHERE provider <> 'local' AND (login_id IS NOT NULL OR password_hash IS NOT NULL);
