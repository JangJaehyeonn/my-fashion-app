-- 이벤트 기록 (FR-14, 2026-11-22)
--
-- 변경 요약: events 테이블을 "추가"한다. 핵심 행동 한 번이 한 행이다 (type: SIGNUP, CLOTHES_ADDED, OUTFIT_RECOMMENDED,
--           WEAR_LOGGED, PURCHASE_CHECKED, PURCHASE_DECIDED, VTON_USED, APP_OPENED — DB 체크 제약 없음, 문자열).
--           props는 jsonb이고 코드·숫자·참거짓만 담는다 (사진·이름·위치 없음, EventRecorder가 걸러냄).
--           기존 테이블·데이터는 바꾸지 않는다. 과거 행동을 되살릴 원본이 없어 백필은 없다.
--
-- 적용: 서버가 기동할 때 ddl-auto: update가 테이블·인덱스를 만든다. 이 스크립트는 배포 전에 미리 적용하거나
--       확인하고 싶을 때 수동 실행한다. 모두 멱등.
--
-- 보관: 1년. EventService.deleteExpired가 매일 04:30(한국 시각)에 1년보다 오래된 행을 지운다. 탈퇴 시 그 사용자 행을 지운다.
--
-- 롤백: 옛 코드는 이 테이블을 모르므로 그대로 둬도 된다. 옛 코드로 탈퇴한 사용자의 남은 행은 아래로 지운다:
--       DELETE FROM events e WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = e.user_id);

CREATE TABLE IF NOT EXISTS events (
    id         UUID PRIMARY KEY,
    user_id    UUID,
    type       VARCHAR(30),
    props      JSONB,
    created_at TIMESTAMP(6) WITH TIME ZONE
);

-- 이름은 엔티티(EventLog)와 같아 Hibernate가 다시 만들지 않는다.
CREATE INDEX IF NOT EXISTS idx_events_user_created ON events (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_events_created ON events (created_at);

-- 검증: 탈퇴한 사용자의 남은 이벤트 수 (0이어야 함), 1년보다 오래된 이벤트 수 (스케줄러가 돌면 0)
SELECT count(*) AS orphan_events FROM events e WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = e.user_id);
SELECT count(*) AS expired_events FROM events WHERE created_at < now() - interval '1 year';
