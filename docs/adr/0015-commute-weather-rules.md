# 0015. 동선 기온과 일교차·비 코디 규칙 (FR-28)

- 상태: 채택
- 날짜: 2026-11-22
- 관련: FR-28, #33

## 배경
FR-28은 나갈 때·낮·돌아올 때 기온을 OpenWeatherMap 3시간 예보로 구하고, 일교차가 크거나 비가 올 것 같으면
코디 후보를 규칙으로 좁혀 AI에는 결과 후보와 이유만 넘기라고 한다. 요구사항에는 칸 사이 시각을 어떻게 읽을지,
'일교차'·'강수 확률'을 어느 시점 값으로 볼지, 규칙 파일 형식, 프로필 API에서 값을 지우는 방법, AI가 후보 밖을 고를 때의 처리가 없다.

## 결정
- **저장**: `users.commute_out_at`, `users.commute_back_at`(TIME, nullable), `users.travel_mode`(VARCHAR, WALK/TRANSIT/CAR, nullable).
  멱등 스크립트 `db/phase15_commute_profile.sql`. NULL이 '설정 안 함'이라 백필 값이 없다. 이동 수단은 이번에는 저장만 한다.
- **프로필 API**: `PUT /api/users/me`에 `commuteOutAt`·`commuteBackAt`("HH:mm")·`travelMode`. 필드가 없으면(null) 그대로 두고, 빈 문자열이면 지운다
  (이 필드를 모르는 옛 앱이 프로필을 저장해도 값이 지워지지 않게). 잘못된 값은 400 `INVALID_COMMUTE_PROFILE`. 응답도 같은 이름·형식.
- **예보**: 백엔드가 `weather.forecast-url`(`WEATHER_FORECAST_URL`, 기본 OpenWeatherMap `/data/2.5/forecast`)을 직접 부른다.
  현재 날씨 주소도 `WEATHER_BASE_URL`로 바꿀 수 있게 했다 (비밀값이 아니라 기본값을 둔다). 캐시는 현재 날씨와 같은 구조
  (좌표 소수 둘째 자리 반올림, 로컬 10분 → Redis 30분, 키 `weather:forecast:<lat>:<lon>`).
- **세 시점**: 오늘(한국 날짜, 주입된 `Clock`) 나갈 때(없으면 08:00)·낮(늘 13:00)·돌아올 때(없으면 19:00).
  칸 사이 시각은 앞뒤 칸을 **선형 보간**하고, 예보 범위 밖이면 가장 가까운 끝 칸을 쓴다. 기온·풍속은 소수 한 자리, 강수 확률은 0~100 정수.
  `GET /api/weather/day?lat&lon` → `{out, day, back: {time, temperature, precipitationProbability, windSpeed}, tempRange}`.
  `tempRange` = 세 시점 기온의 최고 − 최저.
- **규칙 파일** `rules/weather-rules.json` (위치는 `wearon.weather.rules-location`으로 바꿀 수 있음):
  ```json
  {"version": 1, "tempRangeThreshold": 8, "rainProbabilityThreshold": 60,
   "rainExcludedMaterials": ["스웨이드"], "rainExcludedShoeColors": ["화이트"],
   "thickness": {"default": "MEDIUM", "materials": {"린넨": "THIN", "면": "MEDIUM", "니트": "THICK", "...": "..."}},
   "comfortRanges": {"TOP": {"THIN": [20, 40], "MEDIUM": [8, 28], "THICK": [-15, 18]}, "...": {}}}
  ```
  두께는 소재로 추정하고(모르는 소재·소재 없음은 `default`), 적정 기온은 카테고리 × 두께의 닫힌 구간이다.
  모르는 키·버전·카테고리·두께, `[min, max]`가 아닌 범위, min > max는 기동 실패.
- **규칙 적용** (`WeatherOutfitPlanner`, 결정적):
  - RAIN: 세 시점 강수 확률의 **최대**가 기준 이상이면, 스웨이드 소재(모든 카테고리)와 흰색 신발을 AI에 보내는 옷 목록과 후보에서 뺀다.
  - TEMP_RANGE: `tempRange`가 기준 이상(경계 포함)이면, 코디 수 엔진(FR-09)의 유효 조합 중 **상의가 낮 기온의 적정 범위에 드는 조합만** 후보로 둔다.
    엔진 조합은 상의·하의·신발이 먼저 유효해야 아우터가 붙으므로 '아우터를 빼도 유효'는 늘 성립한다.
    남는 조합이 없으면 규칙을 적용하지 않는다(빈 추천보다 유효 조합 추천이 낫다).
  - 후보는 엔진 순서로 최대 30개. 쿨다운·세탁 중(FR-27)으로 입을 수 없는 옷은 그 전에 이미 빠져 있다.
- **AI와 응답**: AI 요청에 `candidates[{clothesIds}]`와 `appliedRules`(`"TEMP_RANGE"`, `"RAIN"`)를 더한다. AI 서버는 후보를 코드로 프롬프트에 넣고 이유를 덧붙인다.
  백엔드는 후보와 옷 구성이 정확히 같은 AI 코디만 받아들이고, 하나도 없으면 앞 후보 3개를 규칙 기본 문장으로 돌려준다.
  응답 `data.appliedRules`에 적용한 규칙을 넣는다 (앱 문구용).
- **실패·옛 앱**: 추천 요청에 `lat`·`lon`이 없거나 예보가 실패하면(어떤 예외든) 후보·규칙 없이 기존 현재 날씨 기반 추천을 한다. 500을 내지 않는다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| 가장 가까운 칸 값 사용 | 계산이 단순 | 06:30처럼 칸 중간이면 1.5시간 떨어진 값을 그대로 씀 | 보간이 같은 비용으로 더 정확 |
| 일교차를 하루 전체 최고·최저로 | 동선과 무관하게 안정적 | 밤 기온이 일교차를 키워 집에 있는 시간까지 반영됨 | 요구사항이 동선(세 시점) 기준 |
| 소재별 적정 기온을 바로 적는 표 | 파일이 짧다 | 소재 × 카테고리 조합이 많아지고 새 소재마다 범위를 다 적어야 함 | 두께 3단계로 묶어 표를 작게 유지 |
| AI가 후보 밖을 고르면 그대로 통과 | AI 문장을 늘 쓴다 | 규칙이 판정한다는 원칙이 깨짐 | 판정은 규칙, LLM은 선택·문장만 |
| 프로필 PUT에서 null을 '지움'으로 | 다른 필드와 같은 의미 | 이 필드를 모르는 옛 앱이 저장할 때마다 출퇴근 시각이 지워짐 | 없음 = 유지, 빈 문자열 = 지움 |

## 결과
- 같은 예보·시각·옷장이면 같은 후보가 나온다. 기준값과 적정 기온은 코드 변경 없이 규칙 파일로 조정한다.
- 이동 수단(travel_mode)은 아직 규칙에 쓰지 않는다. 도보일 때 비 기준을 낮추는 식의 규칙이 필요해지면 규칙 파일에 항목을 더한다.
- 실제 OpenWeatherMap 예보는 '지금'부터 시작해, 이미 지난 시점(예: 오후에 본 08:00)은 첫 칸 값으로 읽힌다.
- 인수 테스트 하네스는 `WEATHER_API_KEY`·`WEATHER_BASE_URL`·`WEATHER_FORECAST_URL`을 가짜 서버로 지정해야 예보 경로를 탄다 (`acceptance/phase2/NOTES.md`).
