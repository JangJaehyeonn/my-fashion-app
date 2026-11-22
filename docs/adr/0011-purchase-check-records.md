# 0011. 살까 말까 기록·임시 사진·하루 제한 저장 방식 (FR-12)

- 상태: 채택
- 날짜: 2026-11-22
- 관련: FR-12, #28

## 배경
FR-12는 판정용 사진을 저장소 `tmp/`에 checkId로 30분 두고 스케줄러로 지우며, `POST /api/purchase-checks/{id}/decision {BUY|SKIP}`로
BUY면 그 사진과 **분석한 속성·임베딩 그대로** 옷을 만들고(재업로드·재분류 없음), SKIP이면 사진을 바로 지우라고 한다. 만료면 410,
기록은 `purchase_checks`(사진 없음)에 남기고 최근 20건을 주며, 탈퇴 시 기록과 임시 사진을 지우고, 판정은 하루 30회(Redis)로 제한한다.
요구사항에는 임시 사진의 키 형식, 기록 컬럼(name·category·color 등)에 없는 fit·material·pattern·season과 임베딩을 BUY까지 어디에 둘지,
만료 판단 방식, '하루'의 기준, 동시 결정 처리가 없다. DB 변경은 nullable 추가와 멱등 SQL만 허용된다.

## 결정
- **임시 사진 키**: `tmp/{userId}/{checkId}.{ext}` (`ImageKeys.tempKey`). 기존 키 규칙에 `tmp` 카테고리를 더해 소유자 검사·prefix 삭제를
  그대로 쓴다. 저장소에 정해진 키로 쓰는 `ImageStorage.storeBytesAt`를 더했다 (local·S3 둘 다).
- **기록**: `purchase_checks(id, user_id, name, category, color, price, verdict, plus, decision, clothes_id, created_at, temp_photo_key)`.
  사진 자체나 영구 사진 경로는 없다. `temp_photo_key`는 임시 사진이 남아 있는 동안만 값이 있고(최대 30분), 옮기거나 지우면 NULL이다.
  스케줄러가 지울 사진을 찾고, 지우기에 실패한 사진을 다시 지우기 위한 컬럼이다. 문자열 값은 DB 체크 제약 없이 저장한다(옛 제약 함정 회피).
  `user_id`·`clothes_id`에 FK가 없고 탈퇴가 같은 트랜잭션에서 지운다(ADR 0009와 같은 방식).
- **BUY용 초안**: 기록 컬럼에 없는 fit·material·pattern·season과 임베딩은 Redis `purchase:draft:{checkId}`(JSON, 40분)에 둔다.
  BUY는 이 값으로 옷을 만들고 임베딩을 그대로 저장한다. 초안이 없으면(Redis 재시작 등) 기록의 이름·카테고리·색으로 만들고 임베딩만 다시 계산한다 — 분류는 다시 부르지 않는다.
- **만료**: 주입한 시계 기준 `created_at + 30분`이 지났거나 `temp_photo_key`가 비었거나, BUY 때 사진 파일을 읽을 수 없으면 410.
  스케줄러(`PurchaseTempPhotoCleaner`, 1분 간격)는 30분 지난 행의 사진과 초안을 지우고 키를 비운다. decision은 UNDECIDED 그대로다.
- **동시 결정**: `UPDATE … WHERE decision = 'UNDECIDED'`로 먼저 차지한 요청만 진행한다. 이미 결정된 판정은 409. BUY 도중 실패하면 UNDECIDED로 되돌린다.
- **하루 제한**: Redis `purchase:daily:{userId}:{한국 날짜}` INCR(2일 만료). 31번째부터 429. 날짜는 `WearCalendar`(Asia/Seoul, 주입 시계)와 같다.
  옷장 부족·입력 오류는 세지 않고 AI 호출 직전에 센다. Redis가 죽으면 막지 않는다(비용 보호용이지 보안 경계가 아님). 한도는 `wearon.purchase-check.daily-limit`.
- **응답**: 결정은 `data{check, clothes}`(SKIP이면 clothes null), 목록·단건은 `{id, name, category, color, price, verdict, plus, decision, clothesId, imageUrl, createdAt}`.
  `imageUrl`은 BOUGHT이고 그 옷이 아직 있을 때만 옷 사진 URL이다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| 속성·임베딩을 purchase_checks 컬럼(vector 포함)에 저장 | Redis가 없어도 BUY가 그대로 됨 | 요구사항의 기록 컬럼 밖 데이터가 영구히 남음, vector 컬럼 네이티브 SQL 추가 | 30분만 필요한 값이라 TTL 저장소가 맞음 |
| 임시 사진 키를 기록에 두지 않고 확장자별로 지우기 | 컬럼 하나 덜 씀 | 스케줄러가 매번 확장자 4개를 지워 봐야 하고 이미 지운 행을 구분할 수 없음 | 정리 대상 조회가 단순해짐 |
| Redis 키 TTL만으로 만료 판단 | 스케줄러 없이 초안 만료 | 저장소 사진은 Redis가 지우지 못함, Redis 재시작 시 만료가 아닌데 410 | 사진 삭제가 요구사항 |
| 판정 요청이 성공했을 때만 세기 | 실패한 요청이 한도를 쓰지 않음 | AI 실패를 반복하면 한도 없이 AI를 부름 | AI 비용 보호가 목적 |
| decision을 JPA enum으로 매핑 | 타입 안전 | Hibernate가 체크 제약을 만들어 값 추가 때 옛 제약 문제 | CLAUDE.md 함정 |

## 결과
- BUY는 분류 API를 부르지 않고, 초안이 있으면 임베딩 API도 부르지 않는다. 코디 수 캐시는 BUY 직후 다시 계산된다.
- 임시 사진은 길어야 30분 + 스케줄러 간격(1분) 남는다. 저장소 삭제가 실패하면 키가 남아 다음 실행에서 다시 지운다.
- FR-13(재판정)은 같은 기록·초안을 갱신하면 된다. 하루 제한을 사용자 등급별로 바꾸려면 한도 계산만 바꾼다.
