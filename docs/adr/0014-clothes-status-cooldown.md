# 0014. 옷 상태(세탁 중)와 최근 착용 쿨다운 규칙 (FR-27)

- 상태: 채택
- 날짜: 2026-11-22
- 관련: FR-27, #32

## 배경
FR-27은 옷에 세탁 중 상태를 두고, 최근에 입은 옷을 카테고리별 일수 동안 홈 코디 추천 후보에서 빼야 한다.
세탁 중은 설정 일수(기본 3일)가 지나면 배치 없이 조회 때 AVAILABLE로 봐야 하고, 쿨다운 일수는 규칙 파일로 바꿀 수 있어야 한다.
요구사항에는 `status_until`이 시작 시각인지 끝 시각인지, 규칙 파일 형식, '며칠 지났다'의 날짜 기준, 입을 옷이 하나도 없을 때의 응답이 없다.

## 결정
- **저장**: `clothes.status`(VARCHAR, `AVAILABLE`/`LAUNDRY`, nullable — NULL은 AVAILABLE)와 `clothes.status_until`(TIMESTAMP, nullable).
  `status_until`은 **세탁이 끝나는 시각**(바꾼 시각 + `laundryDays`)이다. 바꾼 때의 설정이 그 옷에 그대로 적용되어, 설정을 바꿔도 이미 세탁 중인 옷의 끝 시각이 흔들리지 않는다.
  AVAILABLE로 바꾸면 `status_until`을 비운다. 새 옷은 `@PrePersist`로 AVAILABLE, 기존 행은 `db/phase14_clothes_status.sql`이 백필한다.
- **실효 상태**: LAUNDRY이고 지금이 `status_until` 전이면 LAUNDRY, 아니면 AVAILABLE (종료 시각이 없는 LAUNDRY도 AVAILABLE — 영원히 갇히지 않게).
  옷 응답의 `status`는 이 실효 상태다.
- **쿨다운**: 마지막 착용일 D, 오늘(한국 날짜, FR-10 `WearCalendar`) T에 대해 `T − D < 카테고리 일수`이면 쿨다운. 상의 3일이면 오늘·어제·그제 입은 상의가 빠진다.
  쿨다운은 상태를 바꾸지 않고 `available`만 false로 만든다. 규칙에 없는 카테고리(기타)는 0일.
- **입을 수 있는 옷**(`available`) = 실효 상태 AVAILABLE ∧ 쿨다운 아님 ∧ 미분류 아님. 계산은 `ClothesAvailability` 한 곳(주입된 `Clock` 사용).
- **규칙 파일**: `rules/availability-rules.json` (위치는 `wearon.availability.rules-location`으로 바꿀 수 있음).
  ```json
  {"version": 1, "laundryDays": 3, "cooldownDays": {"TOP": 3, "BOTTOM": 2, "OUTER": 0, "SHOES": 0}}
  ```
  모르는 키·버전·카테고리, 음수 일수, `laundryDays` 누락은 기동 실패(조용히 기본값으로 가지 않음).
- **API**: `PATCH /api/clothes/status {clothesIds[], status}` → `{clothesIds, status, statusUntil}`. ID가 하나라도 내 옷이 아니면 400 `INVALID_STATUS_CLOTHES`이고
  한 트랜잭션에서 아무것도 바꾸지 않는다. 빈 목록·잘못된 ID·모르는 상태는 400 `INVALID_CLOTHES_STATUS_REQUEST`.
- **사용처**: 홈 코디 추천(옷장 기반)만 `available`인 옷을 후보로 쓴다. 분류된 옷이 있는데 모두 입을 수 없으면 AI를 부르지 않고 400 `CLOSET_NONE_AVAILABLE`.
  살까 말까·코디 수 엔진(FR-09)은 옷장 전체를 그대로 본다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| `status_until`에 세탁 시작 시각 저장 | 설정을 바꾸면 기존 세탁 옷에도 바로 반영 | 컬럼 이름(until)과 뜻이 어긋나고, 응답에서 끝 시각을 매번 다시 계산 | 이름 그대로의 뜻이 읽기 쉽다 |
| 기간이 지나면 배치로 AVAILABLE로 되돌림 | 저장 값과 실효 값이 같다 | 스케줄러가 하나 더 늘고, 요구사항이 배치 불필요를 명시 | 조회 때 계산으로 충분 |
| 쿨다운 일수를 `application.yml`에 둠 | 파일이 하나 줄어든다 | 요구사항이 규칙 파일을 요구, 카테고리 맵을 yml 환경 변수로 다루기 불편 | 콤보 규칙(0008)과 같은 방식으로 맞춤 |
| 입을 옷이 없으면 빈 추천 200 | 오류 처리가 줄어든다 | 앱이 "옷장이 비었다"와 "다 세탁 중"을 구분 못 함 | 기존 `CLOSET_ALL_UNCLASSIFIED`와 같은 400 + 코드로 통일 |

## 결과
- 같은 시각·같은 기록이면 결과가 같다(시계 주입). 세탁·쿨다운 기간을 코드 변경 없이 규칙 파일로 조정할 수 있다.
- 쿨다운이 한국 날짜 기준이라 자정(KST)에 풀린다. 시간 단위 쿨다운이 필요해지면 착용 기록에 시각을 저장해야 한다.
- 다음 FR(추천에 규칙 조합 후보를 보내는 FR-28, 되살리기 FR-29)도 `ClothesResponse.available`로 후보를 거른다.
