# 0013. 이벤트 기록 저장 방식과 주간 리포트 도구 (FR-14)

- 상태: 채택
- 날짜: 2026-11-22
- 관련: FR-14, #30

## 배경
FR-14는 핵심 행동 8종(SIGNUP, CLOTHES_ADDED, OUTFIT_RECOMMENDED, WEAR_LOGGED, PURCHASE_CHECKED, PURCHASE_DECIDED, VTON_USED,
APP_OPENED)을 `events(user_id, type, props JSON, created_at)`에 남기고, `tools/metrics/weekly_report.py`가 DB를 읽어 Markdown 리포트를
만들라고 한다. 이벤트 기록이 실패해도 본 요청은 성공해야 하고, props에 사진·이름·위치가 없어야 하며, 보관 1년·탈퇴 시 삭제다.
요구사항에는 쓰기 시점(트랜잭션 안/밖), props 형식, 리포트의 DB 접속 방식, '주'와 코호트 기준이 없다.
DB 변경은 nullable 추가와 멱등 SQL만 허용되고, 새 의존성은 ADR이 필요하다.

## 결정
- **테이블**: `events(id uuid, user_id uuid, type varchar(30), props jsonb, created_at timestamptz)`, 인덱스 (user_id, created_at)·(created_at).
  `user_id`에 FK가 없고 탈퇴가 같은 트랜잭션에서 지운다(ADR 0009·0011과 같은 방식). type은 체크 제약 없는 문자열(옛 제약 함정 회피).
  스크립트는 `backend/src/main/resources/db/phase13_events.sql`.
- **쓰기 (`EventRecorder`)**: 호출한 곳에 트랜잭션이 있으면 **커밋 뒤**(`afterCommit`)에, 없으면 바로 쓴다. 쓰기는 늘 별도 트랜잭션
  (`REQUIRES_NEW`)이고 모든 예외를 잡아 로그(type·userId·예외 이름)만 남긴다. 그래서 events에 쓸 수 없어도 본 트랜잭션이 aborted 상태가
  되거나 롤백되지 않고, 롤백된 행동은 이벤트로 남지 않는다. 동기 호출이라 같은 사용자의 이벤트 순서가 요청 순서와 같다.
- **props**: 숫자·참거짓·대문자 코드(`[A-Z][A-Z0-9_]{0,39}`)만 남기고 나머지 문자열·중첩 값은 `EventRecorder`가 버린다.
  옷 이름·닉네임·URL·자유 입력(상황 문장 등)은 코드 형식이 아니라 실수로 넘겨도 저장되지 않는다. 좌표는 어떤 이벤트에도 넘기지 않는다.
  | type | props |
  | --- | --- |
  | SIGNUP | method(LOCAL·GOOGLE·KAKAO) |
  | CLOTHES_ADDED | category, source(PHOTO·PURCHASE) |
  | OUTFIT_RECOMMENDED | mode(CLOSET·SITUATION), outfits(옷장 추천 수) |
  | WEAR_LOGGED | pieces, inserted |
  | PURCHASE_CHECKED | verdict, plus, priced (FR-13 재판정은 새 판정이 아니라 남기지 않음) |
  | PURCHASE_DECIDED | decision(BUY·SKIP), verdict |
  | VTON_USED | 없음 |
  | APP_OPENED | 없음. `POST /api/events/app-open`, 같은 한국 날짜에 두 번째부터는 남기지 않음 |
- **보관**: `EventService.deleteExpired`가 매일 04:30(KST, `wearon.events.retention-cron`) 주입 시계 기준 1년보다 오래된 행을 지운다.
- **리포트**: 파이썬 표준 라이브러리 + PostgreSQL 클라이언트 `psql`(`--csv`)로 읽는다. 새 파이썬 의존성이 없다.
  `DATABASE_URL`을 PG* 환경 변수로 풀어 넘겨 비밀번호가 명령줄에 보이지 않는다. 주는 한국 시각 월요일 시작, 인자 없으면 오늘이 든 주(`--week`로 지정).
  코호트 지표는 그 주 가입자를 가입 후 7일 동안 본다: 옷 5벌 = 가입 시각부터 7일 안의 CLOTHES_ADDED 5건 이상,
  7일 재방문 = 가입 다음 날부터 7일째 날 안에 어떤 이벤트든 있음. 착용 기록 사용자 비율의 분모는 그 주에 이벤트가 있는 사용자다.
  판정별 BUY·SKIP 비율은 그 판정의 PURCHASE_DECIDED 중 비율이다(이벤트만으로 계산되게 PURCHASE_DECIDED에 verdict를 넣음).

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| 본 트랜잭션 안에서 바로 insert | 단순 | PostgreSQL은 문장 하나가 실패하면 트랜잭션 전체가 aborted, 본 요청 실패 | 요구사항 위반 |
| `@Async`·큐로 비동기 기록 | 응답 지연 없음 | 순서 보장이 없고 테스트·종료 처리가 복잡 | insert 한 번이라 동기로 충분 |
| 리포트에 psycopg 의존성 | 파이썬만으로 접속 | 인수 테스트·CI 의존성(`acceptance/requirements.txt`, 보호 경로) 변경 필요 | psql은 개발 환경·CI 러너에 이미 있음 |
| 리포트를 백엔드 API로 제공 | 접속 정보 불필요 | 요구사항은 DB를 읽는 스크립트, 관리자 인증 등 범위 밖 기능 필요 | 요구사항 밖 |
| props를 컬럼별로 분리 | 타입 안전 | 이벤트마다 컬럼이 늘어남 | 요구사항이 JSON |

## 결과
- 이벤트 기록은 본 요청을 실패시키지 않는다. 대신 DB 장애 동안의 이벤트는 잃는다(재시도 없음) — 지표용이라 감수한다.
- 리포트를 돌리는 곳에는 `psql`이 있어야 한다. 데이터가 커지면 SQL 집계로 옮기거나 읽기 전용 복제본을 쓴다.
- 새 이벤트 종류를 더할 때 props 값은 코드·숫자로 설계해야 저장된다.
