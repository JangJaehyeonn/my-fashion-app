# 0005. 동의 이력 저장 형식 (consent_records)

- 상태: 채택
- 날짜: 2026-11-21
- 관련: FR-04, #7

## 배경
지금까지 동의는 `users.age_confirmed_at`, `users.vton_consent_at` 두 컬럼에 마지막 상태만 있었고, 철회하면 값이 지워져
언제 동의·철회했는지 남지 않았다. FR-04는 이용약관·개인정보처리방침·만 14세 이상 확인·가상 피팅 전송 동의를
`consent_records(종류, 버전, 동의 시각, 철회 시각)`에 이력으로 남기고, 철회해도 행을 지우지 않으며, 기존 값은 옮기고,
기존 API 동작은 그대로 두고, 탈퇴 시 이력도 지우라고 한다. DB 변경은 nullable 추가와 멱등 SQL만 허용된다.

## 결정
- 새 테이블 `consent_records(id, user_id, consent_type, version, agreed_at, revoked_at)`를 두고 한 번의 동의 = 한 행으로 저장한다.
  철회는 그 종류의 유효한(철회 시각이 없는) 행에 `revoked_at`만 채우고, 재동의는 새 행을 넣는다. 유효한 행이 있으면 동의 요청을 반복해도 행을 늘리지 않는다.
- 종류는 `ConsentType` enum(`TERMS`, `PRIVACY`, `AGE_OVER_14`, `VTON`)의 이름 문자열, 버전은 enum에 둔 현재 문서 버전 문자열(`"1"`)이다.
  옮겨 온 기존 값은 어떤 문구에 동의했는지 알 수 없으므로 버전을 `legacy`로 둔다.
- `users`의 두 컬럼은 그대로 두고 기존 검사(`AgeGateInterceptor`, `VtonService`)와 응답(`UserResponse`)의 기준으로 계속 쓴다. 이력은 그 옆에 같은 트랜잭션으로 쌓는다.
- 기존 값 이전은 `db/phase9_consent_records.sql`(수동, 멱등)과 같은 SQL을 기동 시 `ConsentBackfill`이 실행한다.
- `user_id`에 FK를 걸지 않는다. 탈퇴는 `AccountDeletionService`가 사용자 행보다 먼저 이력을 지운다.
- 시각은 주입한 `Clock` 빈(`ClockConfig`)으로 정한다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| users 컬럼을 없애고 이력만 기준으로 사용 | 기준 데이터가 하나 | 모든 검사·응답 변경, 롤백 시 옛 코드가 값을 못 읽음 | 기존 API 동작 유지 요구와 롤백 안전성 |
| 철회도 별도 행(이벤트 로그)으로 저장 | 추가만 하는 로그 | 현재 상태 계산이 복잡, 요구사항의 "첫 건에 철회 시각" 형식과 다름 | 요구사항 형식 |
| `user_id` FK(ON DELETE CASCADE) | DB가 삭제 보장 | 기존 운영 DB에 제약 추가 필요, 사용자 행을 직접 지우는 기존 테스트·운영 절차가 깨지거나 이력이 조용히 사라짐 | 삭제를 탈퇴 흐름 한 곳에서 명시적으로 처리 |
| 이전을 첫 요청 때 지연 수행 | 기동 작업 없음 | 요청이 없는 사용자는 영영 이전되지 않음 | 모든 기존 값을 옮겨야 함 |

## 결과
- 동의·철회 시각이 사라지지 않고 남는다. 기존 응답 필드와 403 동작은 바뀌지 않는다.
- 이력과 users 컬럼이 따로 있으므로 둘을 바꾸는 코드는 `UserService`·`LocalAuthService`에서 함께 바꿔야 한다.
- Hibernate가 처음 만든 `consent_type` 컬럼에 enum 값 CHECK 제약을 붙일 수 있다. 종류를 늘릴 때는 `UserSchemaPatch`처럼 제약을 확인하는 멱등 SQL을 같이 둔다.
- 이용약관·개인정보처리방침 동의 기록은 FR-05(가입·소셜 첫 가입 동의 API)에서 `ConsentService.ensureAgreed`로 추가한다.
- 동의 문구가 바뀌면 `ConsentType`의 버전을 올린다. 재동의 요구가 생기면 버전 비교 규칙을 다시 검토한다.
