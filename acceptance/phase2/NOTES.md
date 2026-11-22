# Phase 2 인수 테스트 초안 메모

대상: FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-27, FR-28, FR-29 (82건).
Phase 2에는 `verify = "human"` 요구사항이 없다.

## 옮길 때
- `acceptance_draft/phase2/test_fr*.py` → `acceptance/phase2/`. 빈 `acceptance/phase2/__init__.py`가 필요하다
  (테스트가 `from acceptance.conftest import ...`로 `make_user`·`Stack`·`COMPOSE`·`_wait_ready`를 가져온다).
- `acceptance_draft/fake_ai/app.py` → `acceptance/fake_ai/app.py`, `acceptance_draft/test_fake_ai_phase2.py` → `acceptance/`.
  점검 테스트는 자기 옆의 `fake_ai/app.py`를 읽으므로 옮긴 뒤에도 그대로 돈다.
- 확인한 것: `FAKE_AI_DIR=acceptance_draft/fake_ai python3 -m pytest -q acceptance/test_fake_ai.py` 3건 통과(기존 규칙 그대로),
  `python3 -m pytest -q acceptance_draft/test_fake_ai_phase2.py` 5건 통과,
  `python3 -m pytest --collect-only -p acceptance.conftest acceptance_draft/phase2` 82건이 표시 검사를 통과해 수집됨.
  구현이 없어 스택 테스트는 돌리지 않았다.

## `acceptance/compose.yml`에 필요한 변경 (FR-28, 사람 승인 필요)
백엔드가 OpenWeatherMap을 직접 부르는데, 하네스에는 키도 가짜 주소도 없다. 백엔드 `backend` 서비스 환경 변수에 아래를 더해야
FR-28 테스트가 돈다 (값은 테스트용 가짜 값).
```
WEATHER_API_KEY: acceptance
WEATHER_BASE_URL: http://fake-ai:8000/owm/data/2.5/weather
WEATHER_FORECAST_URL: http://fake-ai:8000/owm/data/2.5/forecast
```
그래서 구현 쪽 `application.yml`은 `weather.base-url: ${WEATHER_BASE_URL:https://api.openweathermap.org/data/2.5/weather}`,
`weather.forecast-url: ${WEATHER_FORECAST_URL:https://api.openweathermap.org/data/2.5/forecast}`처럼 주소를 환경 변수로 받아야 한다
(비밀값이 아니라 기본값을 둬도 된다). 이 변경이 없으면 예보가 늘 실패해 FR-28의 대부분이 실패하고, 실패 시 대체 경로 테스트만 통과한다.

## 가짜 AI 서버에 추가한 것 (기존 엔드포인트·규칙은 그대로)
자세한 규칙은 `acceptance_draft/fake_ai/app.py` docstring.

| 추가 | 쓰는 요구사항 |
|---|---|
| `POST /__control/paths {"<경로>": "down"\|"bad"\|"normal"}` 경로별 장애 | FR-07·FR-08 임베딩만 다운, FR-11 이유 생성만 실패 |
| `POST /__control/attributes {"enabled": true}` 분류 응답에 fit·material·pattern·season | FR-07, FR-28(소재 린넨·스웨이드·가죽) |
| `POST /ai/embed` (`text`→`embedding`, `texts`→`embeddings`, 1536차원) | FR-07·FR-08·FR-11·FR-13 |
| 임베딩 규칙: `probe` 기준 벡터, `simNN` = 기준과 유사도 정확히 NN/100(정수 성분), 나머지 sha256 고정 벡터 | FR-11·FR-13 |
| `POST /ai/purchase-check/explain` 대표 코디(candidates 앞 3개)와 고정 문장 | FR-11 |
| `POST /__control/purchase {"verdict", "extraPick"}` 판정과 다른 값·목록 밖 조합 흉내 | FR-11 |
| `GET /owm/data/2.5/forecast`, `GET /owm/data/2.5/weather`, `POST /__control/forecast` | FR-28 |

`/__control/reset`은 추가한 상태도 모두 기본값으로 되돌린다. `/owm/*`는 `/ai/*`가 아니라 `/__calls` 기록과 전역 mode에 영향이 없다.

### 바꾸지 않은 기존 응답 (바꾸고 싶었던 이유)
- `/ai/clothes/classify`: 실제 AI 서버는 FR-07부터 네 속성을 늘 보내야 하지만, 기존 점검 테스트가 응답을 정확히
  `{category, color, name}`로 비교하므로 기본 응답은 그대로 두고 `/__control/attributes`로 켜게 했다. 옮긴 뒤 기본값을 켜려면
  `acceptance/test_fake_ai.py`의 `test_classify_by_filename` 기대값도 함께 바꿔야 한다.
- `/ai/outfits/recommend/closet`: FR-28부터 백엔드가 `candidates`(규칙이 만든 유효 조합)를 보내지만, 가짜 AI는 기존 규칙대로
  `clothes` 목록의 첫 상의·하의·신발을 고른다. candidates 중에서 고르게 바꾸면 기존 점검 테스트
  `test_calls_are_recorded_and_closet_pick_is_deterministic` 의미가 바뀌어 그대로 두었다. 그래서 FR-28·FR-29 테스트는
  주로 가짜 AI가 **받은** 요청(`/__calls`)을 본다. FR-29는 revived 조합이 응답에 꼭 있어야 하므로, AI가 그 조합을 고르지 않아도
  백엔드가 넣어야 통과한다 (아래 FR-29).

## 요구사항에 이름이 없어 정한 것 (구현은 이 이름을 따른다)

| 요구사항 | 정한 것 |
|---|---|
| FR-07 속성 값 | fit·material·pattern은 한국어 문자열(예: 슬림, 울, 스트라이프), season은 SPRING/SUMMER/FALL/WINTER. 모르면 null. 옷 응답에 키가 늘 있다 |
| FR-07 속성 수정 | FR-01 `PATCH /api/clothes/{id}`가 fit·material·pattern·season도 받는다 (null·생략은 그대로) |
| FR-07 AI 임베딩 요청 | `POST /ai/embed {"text"}` → `{"embedding": [...]}` (배치는 `texts`/`embeddings`) |
| FR-09 요약 API | `GET /api/clothes/summary` → `data.comboCount` |
| FR-09 옷별 수 | 옷 목록 `comboCount`. 미분류 옷은 0 또는 null |
| FR-10 착용 기록 | `POST /api/wears {clothesIds, date: "YYYY-MM-DD"}` → 200/201. 옷 목록 `wearCount`, `lastWornAt`("YYYY-MM-DD…") |
| FR-10 요약 | `GET /api/wears/summary` → `data.weekCount`, `data.monthCount`, `data.mostWorn{id, name}` (기록 없으면 mostWorn null) |
| FR-10 테이블 | `wear_records(user_id, …)`, 옷·날짜마다 한 행 |
| FR-11 판정 응답 | `data{id, verdict, plus, comboCountBefore, comboCountAfter, similar[{id, name, similarity(0~1), wearCount, lastWornAt, note}], outfits[{clothesIds, label}], headline, reasons[], gap, expectedWears, costPerWear}`. `outfits[].clothesIds`는 옷장 옷 ID만(새 옷은 빠짐) |
| FR-11 옷장 부족 | 400, 본문 `code: "CLOSET_TOO_SMALL"` — 지금 `ApiResponse` 오류에는 code가 없으므로 오류 응답에 `code`(ErrorCode 이름)를 더한다 |
| FR-11 AI 요청 | `POST /ai/purchase-check/explain {item, verdict, plus, comboCountBefore, comboCountAfter, similar[], distribution, candidates[{clothesIds}]}` → `{picks[{clothesIds, label}], headline, reasons[3], gap, similarNotes[{id, note}]}`. 대표 코디와 문장을 한 번에 받는다 |
| FR-12 결정 | `POST /api/purchase-checks/{id}/decision {"decision": "BUY"\|"SKIP"}` → 200. BUY는 만든 옷(`data` 또는 `data.clothes`) |
| FR-12 조회 | `GET /api/purchase-checks/{id}`(남의 것 404), `GET /api/purchase-checks` 최신순 20건 `{id, name, category, color, price, verdict, plus, decision, imageUrl, createdAt}` |
| FR-12 임시 사진 | 저장소 루트 아래 `tmp/`에 checkId가 든 이름(파일이나 폴더). 하네스는 `/tmp/wearon-storage/tmp` |
| FR-13 재판정 | `PATCH /api/purchase-checks/{id}/attributes {category, color, name, fit, material, pattern, season}` (생략은 그대로) → FR-11과 같은 응답 |
| FR-14 이벤트 | `events(user_id, type, props, created_at)`. APP_OPENED는 `POST /api/events/app-open` (본문 없음, 2xx) |
| FR-14 리포트 | `tools/metrics/weekly_report.py`가 환경 변수 `DATABASE_URL`로 붙어 Markdown을 표준 출력에 쓴다. 인자 없이 돌면 이번 주(또는 최근 주) 리포트 |
| FR-27 상태 변경 | `PATCH /api/clothes/status {clothesIds, status}` → 200. 옷 목록 `status`(AVAILABLE/LAUNDRY), `available` |
| FR-28 프로필 | `PUT /api/users/me`에 `commuteOutAt`·`commuteBackAt`("HH:mm"), `travelMode`. 응답에도 같은 이름 |
| FR-28 하루 날씨 | `GET /api/weather/day?lat&lon` → `data{out, day, back: {time:"HH:mm", temperature, precipitationProbability(0~100), windSpeed}, tempRange}` |
| FR-28 추천 | 추천 요청 본문에 `lat`·`lon`. AI 요청에 `candidates[{clothesIds}]`, `appliedRules`(`"TEMP_RANGE"`, `"RAIN"`). 백엔드 응답 `data.appliedRules`도 같은 값 (앱의 "외투를 벗어도…", "비 소식이 있어…" 문구용) |
| FR-29 표시 | 추천 응답 `outfits[].revived`(boolean), `outfits[].revivedClothesId` |

## 규칙 파일 기본값에 대한 가정
숫자를 직접 계산해 단정하려면 기본 규칙이 아래를 만족해야 한다. 구현의 규칙 파일·ADR이 이와 다르면 테스트 기대값을 다시 계산해야 한다.
- 무채색·기본색(화이트·블랙·네이비·그레이·베이지)은 어떤 색과도, 같은 색끼리도 어울린다.
- 예시 충돌 색 조합에 **레드–그린**이 있다 (FR-09 "규칙 파일의 예시 충돌 색 조합", FR-29).
- 속성(계절·스타일·소재)이 null이면 충돌로 보지 않는다.
- FR-28 적정 기온: 소재 '린넨' 상의는 낮 12도에 맞지 않고, '니트'·'면' 상의는 12도에 맞는다.
- 쿨다운 기본값(상의 3, 하의 2, 아우터 0, 신발 0일)과 세탁 3일은 요구사항 값 그대로 쓴다.

## 해석·가정
- **날짜**: 백엔드 시간대가 정해져 있지 않다. 날짜를 넣는 테스트는 다른 사용자로 날짜 없이 착용을 기록해 본 `lastWornAt`을
  '오늘'로 삼는다 (FR-10 `server_today`). 날짜 없이 기록한 `lastWornAt`만 보는 FR-10 첫 테스트는 UTC·KST 오늘 중 하나면 된다.
- **FR-08 기동**: `docker compose restart backend`로 흉내낸다. 하네스가 띄운 스택에서만 돈다
  (`WEARON_ACCEPTANCE_URL`로 붙은 외부 스택은 실패). 백필 대기는 최대 90초. `wearon.backfill.reclassify=true` 경로는
  하네스 설정을 바꿔야 해서 인수 테스트에 없다 — 단위 테스트로 확인하길 권한다.
- **FR-09** 엔진 (3) 가상의 새 옷 +N은 별도 API가 없어 FR-11 판정 응답으로 검증한다.
- **FR-10 요약**: '착용 횟수'가 옷 단위인지 날짜(코디) 단위인지, 주가 월요일 시작인지 일요일 시작인지, '가장 많이 입은 옷'의 기간이
  없어서, 날짜마다 옷 1벌만 기록하고 두 주 정의가 엇갈리는 날짜는 쓰지 않는다. 가장 많이 입은 옷은 어느 기간으로 봐도 같은 옷이 되게 했다.
- **FR-11 유사도**: 사진 이름에 `probe`, 옷 이름에 `simNN`을 넣어 유사도를 정한다 (가짜 AI 임베딩 규칙). 응답 유사도는 ±0.005 안이면 된다.
  '정확히 기준값' 테스트는 코사인이 유리수로 정확히 0.85다. 성분이 정수라 float4 저장에도 오차가 없지만, 저장 전에 벡터를
  정규화하면 부동소수 오차로 0.8499…가 될 수 있다. 정규화하지 않거나 비교 시 작은 허용 오차(예: 1e-6)를 두길 권한다.
- **FR-11 미분류 옷**: 분류 실패로 등록한 뒤 이름만 `… sim99`로 고친다 (카테고리는 미분류 그대로). 이 옷은 유사도가 가장 높아도 빠져야 한다.
- **FR-11 expectedWears**: "그 카테고리 전체 유효 코디 수"를 **판정 전** 옷장에서 그 카테고리 옷이 든 유효 코디 수로 봤다
  (상의면 전체 코디 수). 계산 예: 상의 최근 90일 4회 × (plus 2 ÷ 4) × 365 ÷ 90 = 8.11 → 8, 39000원 ÷ 8 = 4875원.
  '착용 기록 30일 미만'은 첫 착용 기록일부터 오늘까지가 30일 미만이라는 뜻으로 봤다 (테스트는 40일·20일·없음).
  90일 밖(120일 전)과 다른 카테고리의 착용은 합에 넣지 않는다.
- **FR-11 판정 응답의 verdict 무시**: 가짜 AI가 다른 verdict와 그 판정을 부추기는 headline을 보낸다. 테스트는 verdict만 본다
  (headline을 그대로 쓸지 기본 문장으로 바꿀지는 정하지 않음). 정상일 때는 AI의 headline·reasons·gap·note를 그대로 쓴다고 봤다.
- **FR-11** '구간별 처리 시간 로그'는 HTTP로 볼 수 없어 테스트가 없다.
- **FR-12 만료**: 30분을 기다릴 수 없어 `purchase_checks.created_at`을 31분 앞당기고 임시 사진도 지운다(스케줄러가 지운 상태).
  만료 판단을 시각으로 하든 사진 유무로 하든 410이어야 한다. 임시 사진은 `docker compose exec backend find …`로 본다.
- **FR-12** 하루 30회 테스트는 같은 사용자로 30번 판정한 뒤 목록이 최신 20건인지도 함께 본다. 31번째는 429.
- **FR-14 순서**: 핵심 4종(SIGNUP, CLOTHES_ADDED, PURCHASE_CHECKED, PURCHASE_DECIDED)만 골라 `created_at` 순으로 보고,
  옷 5벌의 CLOTHES_ADDED처럼 연속된 같은 종류는 하나로 본다.
- **FR-14 개인정보**: props 텍스트에 옷 이름·닉네임·이미지 경로(`/api/images`, `http`, `.jpg`, `tmp/`)·좌표 값과 키(lat, lon)가 없어야 한다.
- **FR-14 쓰기 실패**: events에 `BEFORE INSERT` 트리거로 예외를 내게 한 뒤 옷 등록을 하고, 끝나면 트리거를 지운다.
- **FR-14 리포트**: 샘플 데이터는 API로 만든다 (events id 생성 방식을 가정하지 않으려고). DB 전체를 읽으므로 숫자는 단정하지 않고,
  Markdown 제목·표와 지표 낱말(가입, 5벌, 살까 말까, BUY, THINK, SIMILAR, SKIP, 착용 기록, 재방문)이 있는지 본다.
  스크립트가 DB 드라이버(psycopg 등)를 쓰면 `acceptance/requirements.txt`에도 넣어야 한다. 1년 보관은 테스트가 없다.
- **FR-15**: `/privacy`의 지금 구조(1항 수집 정보 표, 2항 보유 기간 목록, 5항 OpenAI 표의 '이전 항목' 행)에 항목을 더한다고 봤다.
  '30분'은 이미 날씨 캐시 문장에 있으므로 '살까 말까'와 같은 목록 항목·표 행·문단 안에 있어야 한다.
- **FR-27 세탁 4일**: `clothes.status_until`을 4일 앞당긴다. 값이 '세탁 시작 시각'이든 '끝나는 시각'이든 4일이 지난 상태가 된다.
  LAUNDRY로 바꿀 때 status_until을 비워 두는 구현이면 이 테스트가 실패한다.
- **FR-27 후보**: 요구사항 문구대로 가짜 AI가 받은 `clothes` 목록을 보고, `candidates`가 있으면 거기에도 없어야 한다.
  쿨다운 중인 옷도 `status`는 AVAILABLE이고 `available`만 false라고 봤다.
- **FR-28 예보**: 가짜 예보는 KST 0~9시 칸 = 아침, 12·15시 = 낮, 18·21시 = 저녁 값이라 8·13·19시를 보간하든 가까운 칸을 쓰든
  값이 같다. 출퇴근 시각 테스트는 칸 시각과 같은 06:00·21:00을 써서 방식과 무관하게 6도·21도가 된다.
  일교차는 세 시점 최고−최저로 봤다 (가짜 예보는 하루 전체 최고·최저도 같아 어느 쪽이든 같음). 경계 8도는 적용, 7도는 미적용.
  강수 확률은 세 시점 중 최대가 60% 이상이면 적용으로 봤다 (가짜 예보는 모든 칸이 같다). 50%는 미적용.
  '흰색 신발'은 색이 화이트인 신발, '스웨이드'는 소재가 스웨이드인 옷(신발·아우터 모두)으로 봤고, 흰 상의는 남는다.
- **FR-28 실패 대체**: 예보 500이면 추천은 200이고 기존처럼 요청 본문의 현재 기온(12도)으로 AI를 부르며 TEMP_RANGE가 없다.
- **FR-29**: 오래 안 입은 옷은 등록일 90일 전 + 70일 전 착용 1회로 만든다. 같은 주 두 번째 추천은 바로 이어서 보낸다.
  유효 조합이 없는 경우는 기타 옷(코디에 들지 않음)과 레드–그린 충돌뿐인 레드 상의 두 가지로 본다.
