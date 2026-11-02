# Wearon 코드 점검 (AUDIT)

- 점검일: 2026-11-02
- 대상 커밋: `6af4b79` (`feat/vton-tryon`). `origin/dev`(`fc19b15`)보다 3커밋 앞서 있고, 차이는 AI 서버 모델 설정·비교 스크립트(`ai-server/` 한정)뿐
- 범위: 코드 읽기와 로컬 빌드·테스트까지. 코드 수정은 하지 않음
- 표기: **[확인]** 코드나 실행으로 확인한 사실 / **[추정]** 확인하지 못한 추론

---

## 1. 한 줄 요약

Android 앱 기준으로 핵심 흐름(로그인 → 옷장 등록·AI 분류 → 옷장 기반 코디 추천 → 가상 피팅 → 마이)은 끝까지 연결돼 있고 빌드·테스트도 모두 통과합니다. 다만 **Play Store 출시를 막는 문제가 남아 있습니다.** 회원 탈퇴와 개인정보처리방침이 없고, 릴리즈 빌드가 토큰을 로그로 출력하며, AI 서버 호출에 타임아웃이 없습니다. React 웹은 사실상 고장 난 상태입니다.

---

## 처리 현황 (2026-11-02, 브랜치 `fix/app-local`, Android만 수정, push 안 함)

`origin/dev`(`fc19b15`)에서 브랜치를 땄습니다. 로컬 `dev`는 `origin/dev`보다 34커밋 뒤처져 있어(옷장 도입 이전) 기준에서 제외했습니다. 각 커밋마다 `assembleDebug`와 `assembleRelease`가 모두 성공했습니다. 에뮬레이터 실행 확인은 하지 못했습니다(로컬 백엔드를 띄울 수 없음, 아래 참고).

| 커밋 | 항목 | 관련 이슈 | 상태 |
|---|---|---|---|
| `aba8184` | HTTP 허용을 디버그 소스셋에만 두고 릴리즈는 차단. `docs/LOCAL_DEV.md` 추가 | — | ✅ 앱 완료 |
| `3fdbbb6` | 릴리즈 빌드는 HTTP 로그 NONE, 디버그만 BODY. `Authorization` 헤더 가림. 그 밖의 `Log`/`println`은 원래 없었음 | H-1 | ✅ 완료 |
| `e2dadaf` | 로그아웃 요청 본문에 refreshToken 포함 | M-2 | ✅ 앱 완료. 서버는 원래 본문의 토큰을 폐기하므로 이 수정만으로 폐기가 동작함(운영 확인 필요) |
| `9192c88` | 마이 탭 하단에 "회원 탈퇴" 버튼과 확인 다이얼로그. `DELETE /api/users/me` 호출. 서버가 성공을 응답할 때만 로컬 토큰 삭제 | H-2 | 🟡 앱만 완료. 백엔드 API 필요 |
| `9cb4e6e` | 홈 날씨에 기기 위치 사용. 목적을 설명하는 안내 다이얼로그 뒤에 `ACCESS_COARSE_LOCATION` 요청. 거부하거나 위치를 못 얻으면 서울. 좌표는 소수 둘째 자리로 반올림 | 3장 2-a | 🟡 앱만 완료. 서버가 좌표를 무시하므로 지금은 여전히 서울 날씨이고, 카드에는 "내 위치"로 표시됨 → 출시 전에 백엔드 반영 필요 |

로컬 환경 메모 (2026-11-02):
- Docker Desktop은 사용자 요청으로 컨테이너·이미지·볼륨을 전부 삭제했습니다. 그래서 로컬 백엔드 실행 검증은 생략했습니다.
- LLM(OpenAI·HF) 연동 작업은 2026-11-18 이후로 미뤘습니다. 그 전까지 AI 서버는 띄우지 않습니다.

### 백엔드가 같이 바뀌어야 끝나는 항목

| 항목 | 앱 쪽 상태 | 백엔드에서 할 일 | 관련 파일 |
|---|---|---|---|
| 로그아웃 시 리프레시 토큰 폐기 (M-2) | ✅ 본문에 토큰 전송 | 폐기 로직은 이미 있음(`AuthController.logout`). 남은 일: ① 운영에서 폐기되는지 확인 ② 리프레시 토큰을 Bearer로 쓰지 못하게 `typ` 클레임 구분 (M-1) ③ 기기별 키 (L-10, 선택) | `AuthController`, `JwtTokenProvider`, `JwtAuthenticationFilter` |
| 딥링크 1회용 코드 교환 (H-7) | ❌ 미착수. 서버 방식이 정해져야 앱을 바꿀 수 있음 | 성공 핸들러가 토큰 대신 짧은 수명(예: 60초)의 1회용 code를 리다이렉트에 담기 → `POST /api/auth/token`(code → 토큰) 신설. 실패 핸들러도 앱 스킴으로 (M-9) | `OAuth2AuthenticationSuccessHandler`, `OAuth2AuthenticationFailureHandler`, `AuthController` / 앱: `MainViewModel.handleDeepLink`, `AuthApi` |
| 회원 탈퇴 API (H-2) | ✅ UI와 호출부 완료 (`AuthApi.deleteAccount`에 TODO) | `DELETE /api/users/me`: 사용자의 clothes·style_diagnoses 행, S3 사진(clothes·diagnosis·vton), Redis `refresh:{id}` 삭제 후 users 행 삭제. 지금은 같은 경로에 GET·PUT만 있어 405가 예외 핸들러에서 500으로 바뀜 → 앱에는 "탈퇴 실패"로 표시됨 | `UserController`, `UserService`, `ClothesRepository`, `StyleDiagnosisRepository`, `S3Uploader` |
| AI 서버 호출 타임아웃 (H-4) | 해당 없음 (앱 OkHttp는 기본 10초, VTON만 180초) | `RestClientConfig`에 연결 5초 / 읽기 30초(VTON 180초) 설정, `AsyncConfig` 풀 상한, AI 호출을 트랜잭션 밖으로 (H-5) | `RestClientConfig`, `AsyncConfig`, `AiServerClient`, 각 Service |
| 위치 기반 날씨 | ✅ `GET /api/weather?lat=&lon=` 전송 (`OutfitApi`에 TODO) | `WeatherController`가 lat/lon을 받아 `AiServerClient.getWeather`가 `/ai/weather?lat=&lon=`로 전달. 캐시 키를 좌표별로(예: `weather:37.57,126.98`), Caffeine `maximumSize(1)` 상향. 좌표는 로그나 DB에 남기지 않기(앱 안내 문구가 "저장하지 않아요") | `WeatherController`, `WeatherService`, `AiServerClient` |

### 앱이 호출하는 API 점검 (5번, 수정 안 함)

| 앱 호출 | 위치 | 백엔드 | 비고 |
|---|---|---|---|
| `DELETE /api/users/me` | `AuthApi.deleteAccount` | ❌ 없음 | 이번에 추가한 호출. 백엔드 작업 대기 |
| `GET /api/weather?lat=&lon=` | `OutfitApi.getWeather` | ⚠️ 경로는 있으나 파라미터 무시 | 백엔드 작업 대기 |
| `POST /api/diagnosis`, `GET /api/diagnosis`, `GET /api/diagnosis/{id}` | `DiagnosisApi` | ✅ | 화면 진입 경로 없음 (L-11) |
| `POST /api/shopping/recommend` | `ShoppingApi` | ✅ | 화면 진입 경로 없음 (L-11) |
| `auth/refresh`, `auth/logout`, `users/me`(GET·PUT), `clothes`(POST·GET·DELETE), `outfits/recommend/closet`, `vton` | 각 Api | ✅ | 정상 |
| `/oauth2/authorization/{google,kakao}` | `LoginScreen` | ✅ (Spring Security) | |
| 외부 쇼핑몰 페이지 | `ProductPageRepository` | 해당 없음 | 설계상 앱이 직접 요청(SSRF 방지) |

- 웹 전용 API를 앱이 호출하는 곳은 없습니다. 웹(`frontend/`)이 부르는 `/outfits`, `/calendar`, `/outfits/recommend`는 앱에서 쓰지 않습니다.
- 반대로 백엔드에는 있지만 앱이 호출하지 않는 API: `POST /api/home/summary`, `POST /api/outfits/recommend/situation`(k6만 사용).

---

## 2. 구조 요약

| 모듈 | 위치 | 진입점 | 주요 설정 | 상태 |
|---|---|---|---|---|
| Android (Kotlin, Compose, Hilt) | `android/` | `MainActivity.kt` → `ui/navigation/Navigation.kt`(`AppNavigation`) | `app/build.gradle.kts`, `AndroidManifest.xml`, `res/xml/network_security_config.xml`, `keystore.properties`(gitignore) | 실서비스 클라이언트 |
| 백엔드 (Spring Boot 3, Java 17) | `backend/` | `FashionAppApplication.java` | `src/main/resources/application.yml`, `backend/.env`(gitignore) | 운영 중 |
| AI 서버 (FastAPI) | `ai-server/` | `app/main.py` | `app/core/config.py`, `ai-server/.env`(gitignore), `requirements.txt` | 운영 중 |
| 웹 (React 18 + Vite) | `frontend/` | `src/main.jsx` | `vite.config.js` | 2026-07-13 이후 갱신 없음. 백엔드와 맞지 않음 |
| 인프라 | `docker-compose.prod.yml`, `nginx/nginx.conf`, `infra/certbot/`, `infra/duckdns/` | — | — | EC2 단일 서버, HTTPS(Let's Encrypt) |
| CI/CD | `.github/workflows/deploy.yml`, `pr-review.yml` | `dev` 브랜치에 push하면 EC2에 SSH로 배포 | GitHub Secrets | 테스트를 돌리는 CI 없음 |
| 성능 테스트 | `performance/k6-*.js` | — | — | 스크립트 3개 |

**호출 흐름 [확인]**: Android → nginx(443) → Spring `/api/**` → `AiServerClient`(RestClient) → FastAPI `/ai/**` → OpenAI / OpenWeatherMap / HF Space `yisol/IDM-VTON`. AI 서버는 compose 내부망에서만 접근 가능하고 포트가 외부에 열려 있지 않음.

**API 대응 [확인]**: Android Retrofit 경로(`data/api/*.kt`)와 Spring 컨트롤러 경로는 모두 일치합니다. FastAPI 라우트 8개(`/ai/clothes/classify`, `/ai/diagnosis`, `/ai/outfits/recommend/{situation,closet}`, `/ai/shopping/recommend`, `/ai/vton`, `/ai/weather`, `/health`)는 `AiServerClient`의 호출과 일치합니다.

---

## 3. 기능 구현 현황

### 기획 기능

| # | 기능 | 상태 | 근거 (파일 / 클래스·함수) | 연결 범위 |
|---|---|---|---|---|
| 1 | 옷 사진 업로드 + AI 자동 분류 | **완료** | Android `ui/closet/ClosetViewModel`(촬영·갤러리 다중·URL 등록, `ImageCompressor.compress`), `ClothesApi` → `ClothesController`/`ClothesService.register` → `AiServerClient.classifyClothes` → `routers/clothes.py` / `clothes_service.classify_clothes_image`(gpt-4o-mini, `detail: low`) → S3 → `Clothes` 엔티티 | Android ✅ · 백엔드 ✅ · AI ✅ · 웹 ⚠️ (`Wardrobe.jsx`는 옛 응답 스키마 기준, 확인 못 함) |
| 2 | 날씨 기반 코디 추천 | **완료** (옷장 기반) | `ui/home/HomeViewModel` → `OutfitApi.recommendByCloset` → `OutfitService.recommendByCloset` → `recommend_service.recommend_outfit_by_closet`. 날씨는 `WeatherService`(Caffeine + Redis) → `weather_service.get_current_weather` | Android ✅ · 백엔드 ✅ · AI ✅ · 웹 ❌ (`api/outfit.js`가 없는 `/outfits/recommend`를 호출) |
| 2-a | └ 날씨 소스 | **기획과 다름** | 기상청이 아니라 **OpenWeatherMap**이고, 위치는 서울시청 좌표로 고정(`weather_service.py`). 사용자 위치는 반영하지 않음 | — |
| 3 | 코디 캘린더 | **미구현** (제거됨) | 백엔드에 `/api/calendar`가 없고 Android에도 화면이 없음. `frontend/src/pages/Calendar.jsx`만 남아 존재하지 않는 API를 호출 | 웹 화면만 남음 |
| 4 | VTON 가상 피팅 | **완료** (외부 의존 큼) | `ui/fitting/FittingScreen`·`FittingViewModel`, `ProductPageRepository`(쇼핑몰 URL에서 og:image 추출) → `VtonApi` → `VtonController`/`VtonService` → `AiServerClient.virtualTryOn` → `vton_service.virtual_try_on`(gradio_client로 **공개 HF Space** 호출) → S3 결과 저장 | Android ✅ · 백엔드 ✅ · AI ✅. 자체 모델이 아니라 제3자 공개 Space에 의존 |
| 5 | Play Store 출시 | **부분** | 릴리즈 서명 설정(`signingConfigs.release`)과 HTTPS 전환은 완료. 회원 탈퇴·개인정보처리방침·AAB 빌드는 없음 (P0 참고) | — |
| 6 | k6 성능 테스트 | **부분** | `performance/k6-latency.js`, `k6-load.js`, `k6-ai.js`. 현재 핵심 기능(`/api/clothes`, `/outfits/recommend/closet`, `/vton`)은 측정 대상에 없음. `k6-latency/load`의 `BASE_URL`은 `http://`로 하드코딩돼 있어 308 리다이렉트 비용이 함께 측정됨 | — |

### 탭

| 탭 | 상태 | 근거 | 비고 |
|---|---|---|---|
| Home | 완료 | `ui/home/HomeScreen`, `HomeViewModel`, `LookCard` | 옷장이 비면 안내 후 옷장 탭으로 이동 |
| Closet | 완료 | `ui/closet/ClosetScreen`, `ClosetViewModel`, `UrlRegisterDialog` | 등록·분류·카테고리 탭·삭제 |
| Fitting | 완료 | `ui/fitting/*` | VTON 업로드는 압축하지 않음 (문제점 M-5) |
| My | 완료 (탈퇴 없음) | `ui/mypage/MyPageScreen`, `BodyProfileScreen` | 로그아웃만 있고 **회원 탈퇴 없음** |

### 기획 외 코드 (존속하지만 진입 경로 없음) [확인]

| 항목 | 근거 |
|---|---|
| 내 옷 진단 | `ui/diagnosis/*`. 라우트는 등록돼 있지만 탭에도 다른 화면에도 진입 경로가 없음. 백엔드 `DiagnosisController`는 살아 있음 |
| 쇼핑 도우미 | `ui/shopping/*`. 진입 경로가 없어서, 이 화면에서만 들어가는 `ui/vton/VtonScreen`(`Route.VTON`)까지 도달 불가 |
| 홈 요약 API | `HomeController` `/api/home/summary`. Android에서 호출하는 곳 없음 |
| 상황 기반 추천 | `/api/outfits/recommend/situation`. Android에서는 호출하지 않고 k6만 사용 |

---

## 4. 문제점 목록 (심각도순)

### 상 (출시 차단 / 보안)

| ID | 위치 | 문제 | 수정 방향 |
|---|---|---|---|
| H-1 ✅ | `android/.../data/api/ApiClient.kt` (`HttpLoggingInterceptor.Level.BODY`) | **릴리즈 빌드에서도** 요청·응답 본문을 logcat에 출력함. 그래서 `Authorization` 헤더, 리프레시 응답의 토큰, presigned URL, 멀티파트 이미지 바이트까지 로그에 남음. 큰 이미지를 올릴 때 메모리 부담도 있음 [확인] | `if (BuildConfig.DEBUG)`일 때만 추가하고, `redactHeader("Authorization")` 적용 |
| H-2 🟡(앱 UI만) | 백엔드·Android 전체 | **회원 탈퇴(계정 삭제) 기능 없음.** `UserController`에는 GET·PUT `/me`만 있음 [확인]. Play는 계정을 만드는 앱에 앱 안 삭제 경로와 웹 삭제 URL을 요구함 | `DELETE /api/users/me` 추가: clothes·diagnosis·S3·Redis 토큰까지 삭제. 마이 화면에 버튼과 웹 안내 페이지 추가 |
| H-3 | 앱·레포 전체 | **개인정보처리방침이 없음** (코드에서 `privacy`·`개인정보` 검색 결과 0건) [확인]. 전신 사진, 키·몸무게, 옷 사진을 수집하고 OpenAI와 HF Space(제3자)로 보내는데도 고지가 없음 | 방침 페이지를 호스팅하고 앱과 Play Console에 링크. 데이터 보안 양식에 사진·신체 정보와 제3자 전송을 기재 |
| H-4 | `backend/.../RestClientConfig.java` | `SimpleClientHttpRequestFactory`에 **connect·read 타임아웃이 없음** [확인]. AI 서버나 HF Space가 멈추면 요청 스레드가 무한 대기함. 비동기 호출도 고정 8스레드 풀(`AsyncConfig`)이라 VTON 몇 건만 걸려도 추천 API까지 막힘 | 연결 5초 / 읽기(일반 30초, VTON 180초)를 설정하고 엔드포인트별로 RestClient 분리. 풀 크기와 큐 상한도 명시 |
| H-5 | `backend/.../VtonService.tryOn`, `ClothesService.register`, `DiagnosisService.diagnose`, `OutfitService` | `@Transactional` 안에서 AI 호출(수 초~최대 180초)을 기다림 → 그동안 DB 커넥션을 붙잡고 있음 (Hikari 최대 10개) [확인]. 동시 VTON 몇 건으로 커넥션 풀이 고갈될 수 있음 [추정] | 사용자 조회만 짧은 트랜잭션으로 하고 AI 호출은 트랜잭션 밖에서. VTON은 `readOnly` 트랜잭션 자체를 제거 |
| H-6 | `ai-server/.../vton_service.py` (`yisol/IDM-VTON`) | ① IDM-VTON 모델 라이선스가 **CC BY-NC-SA 4.0(비상업)** 으로 알려져 있음 [추정, 확인 필요]. ② 공개 Space라 대기열·ZeroGPU 할당량을 모든 사용자가 EC2 IP 하나로 공유함 [추정]. ③ Space 소유자가 내리거나 시그니처를 바꾸면 즉시 장애 | 라이선스 확인. 상용 출시라면 상용 가능한 VTON API나 자체 호스팅 검토. 최소한 장애 시 안내 문구 |
| H-7 | `MainViewModel.handleDeepLink`, `OAuth2AuthenticationSuccessHandler` | 토큰을 커스텀 스킴 `fashionapp://app/oauth2/callback?accessToken=…&refreshToken=…`의 쿼리로 전달함 [확인]. ① 다른 앱이 같은 스킴을 등록하면 토큰을 가로챌 수 있음. ② 아무 웹페이지나 이 링크로 공격자 토큰을 주입할 수 있음(로그인 CSRF). 앱은 state 검증 없이 그대로 저장 | 일회용 코드를 발급하고 앱이 POST로 교환하는 방식(+PKCE·state). 또는 App Links(https + `assetlinks.json`) |

### 중

| ID | 위치 | 문제 | 수정 방향 |
|---|---|---|---|
| M-1 | `JwtTokenProvider`, `JwtAuthenticationFilter` | 액세스 토큰과 리프레시 토큰의 구조가 같음(subject만 있고 type 클레임 없음). 그래서 **리프레시 토큰(14일)을 API 인증용 Bearer로 쓸 수 있음** [확인] | `typ` 클레임을 추가하고 필터에서 access만 허용 |
| M-2 ✅(앱) | `AuthRepository.logout` / `AuthApi.logout()` ↔ `AuthController.logout` | Android가 본문 없이 로그아웃을 호출하는데, 서버는 본문에 refreshToken이 있을 때만 Redis에서 삭제함. **로그아웃해도 리프레시 토큰이 14일간 유효** [확인] | 앱이 refreshToken을 본문에 담아 보내거나, 서버가 인증 사용자 기준으로 삭제 |
| M-3 | `ApiClient.kt` `TokenAuthenticator` | 401이 동시에 여러 건 오면 각각 리프레시를 시도함. 서버가 리프레시 토큰을 교체(rotation)하므로 두 번째 시도는 실패하고 `clearTokens()`로 **강제 로그아웃** [추정, 코드상 동기화 없음] | `synchronized` 또는 Mutex로 단일 갱신. 갱신 후 토큰이 바뀌었으면 재사용 |
| M-4 | `GlobalExceptionHandler` | `CustomException`과 `Exception`만 처리함. 업로드 초과(`MaxUploadSizeExceededException`, nginx 20MB > Spring 10MB), 잘못된 JSON, 누락된 파트, 없는 경로(`NoResourceFoundException`)가 전부 **500**으로 응답됨 [확인] | 400·404·413 핸들러 추가 |
| M-5 | `ui/fitting/FittingViewModel.uriToPart`, `ui/diagnosis` | 피팅 사진을 **압축하지 않고** 원본 그대로 올림(옷장만 `ImageCompressor` 사용) [확인]. 고해상도 사진이면 10MB 제한에 걸려 500이 남. HEIC 같은 형식은 Spring은 허용하지만 FastAPI(`_ALLOWED_TYPES`)는 거부하므로 503 | 옷장과 같은 `ImageCompressor` 적용. 백엔드와 AI 서버의 허용 MIME 목록 통일 |
| M-6 | `.github/workflows/deploy.yml` | `set -e`/`script_stop`이 없어서 `git pull`이 실패해도 예전 코드로 재빌드하고 ✓로 표시됨. **배포 전 빌드·테스트 단계가 없음**. t3.small에서 직접 빌드 | `script_stop: true`. `./gradlew test`를 돌리는 job을 먼저 실행하고 `needs`로 연결. 장기적으로는 이미지를 CI에서 빌드 |
| M-7 | 백엔드 전체 | AI 엔드포인트(OpenAI·HF 비용 발생)에 **사용자별 호출 제한(rate limit)이 없음** [확인]. 토큰 하나로 비용 폭주가 가능 | nginx `limit_req` 또는 Redis 기반 사용자별 쿼터 |
| M-8 | `DiagnosisService`, `VtonService`, `S3Uploader` | 진단 사진(전신)과 VTON 결과가 S3에 **무기한 보관**됨(문서에는 "임시 저장"). presigned URL 유효기간 7일 [확인] | S3 Lifecycle 규칙(예: 30일). presigned URL 유효기간 단축 |
| M-9 | `OAuth2AuthenticationFailureHandler` | 로그인 실패 시 앱 스킴이 아니라 `app.frontend-url`(기본값 `http://localhost:3000`)로 리다이렉트함. 운영 `.env`의 `FRONTEND_URL` 값은 확인 못 함 → Android 사용자는 브라우저에서 막힐 것으로 추정 [추정] | 실패도 `oauth2-redirect-base`(앱 스킴)로 보내고 앱에서 에러 표시 |
| M-10 | `android/app/build.gradle.kts` | `targetSdk = 35`. Play는 매년 8월 31일부터 신규 앱에 최신 API 수준을 요구하므로 지금(2026-11)은 **36 이상이 필요할 가능성이 높음** [추정, 정책 확인 필요]. `applicationId = "com.fashionapp"`은 지나치게 일반적이라 이미 사용 중일 수 있고, 출시 후에는 바꿀 수 없음 [추정] | targetSdk 36으로 올리고 회귀 테스트. 패키지명 확정(예: `com.<본인도메인>.wearon`) |
| M-11 | `AndroidManifest.xml` `allowBackup="true"` | 토큰이 든 DataStore가 기기 백업에 포함됨 [확인] | `dataExtractionRules`·`fullBackupContent`에서 `datastore/` 제외 |
| M-12 | `frontend/` 전체 | `api/outfit.js`가 존재하지 않는 `/outfits`, `/calendar`, `/outfits/recommend`를 호출함. OAuth 성공 리다이렉트도 앱 스킴이라 **웹 로그인이 불가능**할 것으로 추정 [호출 경로는 확인, 로그인은 추정]. 빌드는 성공 | 웹을 유지할지 결정. 유지하지 않는다면 레포에서 제거하거나 archived로 명시 |

### 하

| ID | 위치 | 문제 | 수정 방향 |
|---|---|---|---|
| L-1 | `SecurityConfig.corsConfigurationSource` | `allowedOriginPatterns("*")`와 `allowCredentials(true)`를 함께 사용. 쿠키를 쓰지 않아 실제 위험은 낮음 | 웹을 쓰지 않으면 CORS를 끄거나 도메인 명시 |
| L-2 | `S3Uploader.upload` | 클라이언트가 보낸 Content-Type만 검사하고 파일 시그니처는 보지 않음. S3 키에 `getOriginalFilename()`이 그대로 들어감. `SdkException`은 잡지 않아 500이 됨 | 매직 바이트 검사, 파일명 제거(UUID + 확장자), SDK 예외 변환 |
| L-3 | `DiagnosisService.diagnose` | S3 업로드를 AI 호출보다 먼저 해서, AI가 실패하면 고아 파일이 남음(옷장은 순서를 이미 고쳤음). `getSimilarStyles()`가 null이면 NPE | 옷장과 같은 순서로 바꾸고 null 방어 |
| L-4 | `VtonService` | `getResultImageBase64()`가 null이면 NPE → 500 | null 검사 후 `AI_SERVER_ERROR` |
| L-5 | `WeatherService.getFromRedis` | Redis 장애 시 예외가 그대로 전파됨(외부 API로 폴백하지 않음) | try/catch로 폴백 |
| L-6 | `docker-compose.prod.yml` | Postgres 비밀번호 `fashionapp`이 평문 하드코딩. 외부 포트는 열려 있지 않아 위험은 낮음 | `.env`로 이동 |
| L-7 | `backend/Dockerfile`, `ai-server/Dockerfile` | `.dockerignore`가 없어서 `COPY . .` 때 `.env`가 빌드 레이어에 포함됨. 최종 이미지는 jar만 복사하므로 영향은 제한적. 두 이미지 모두 root로 실행 | `.dockerignore` 추가, non-root 사용자로 실행 |
| L-8 | `ai-server/.env.example` | `WEATHER_API_KEY=your_kma_api_key`. 실제로는 OpenWeatherMap 키라 오해 소지 | 주석 수정 |
| L-9 | `backend/src/main/resources/db/phase0_cleanup.sql` | `clothes` 테이블을 DROP하는 스크립트가 jar 리소스에 포함됨. 지금 실행하면 새 옷장 데이터가 삭제됨(파일 안에 경고 있음) | 리소스 밖으로 옮기거나 삭제 |
| L-10 | 단일 리프레시 토큰 키 `refresh:{userId}` | 기기 두 대에서 로그인하면 먼저 로그인한 기기가 갱신 시 로그아웃됨 [확인: 키가 사용자당 하나] | 기기별 키 사용 |
| L-11 | 진입 불가 코드 | `ui/diagnosis`, `ui/shopping`, `ui/vton`, `HomeController`, `/recommend/situation`(k6 전용) | 피팅 탭에 통합할지 결정한 뒤 정리 |
| L-12 | 빌드 경고 | `ShoppingScreen.kt:97` `menuAnchor()` deprecated, `VtonScreen.kt:138` `Icons.Filled.ArrowBack` deprecated | 대체 API로 교체 |
| L-13 | `isMinifyEnabled = false` | 릴리즈 APK 12.6MB, 난독화 없음 | R8을 켜고 keep 규칙 검증 (`proguard-rules.pro`는 이미 있음) |

- 하드코딩 키: 현재 추적 중인 파일에서는 **발견 안 됨** [확인, AWS·OpenAI·HF·Google 패턴 grep].
- 다만 `backend/.env`가 커밋 `1e45a9c`~`82a62f1`에 포함됐다가 `a68afab`에서 삭제됐고, 히스토리에는 남아 있음(Google·Kakao secret, JWT, AWS 키) [확인]. CLAUDE.md에는 키를 교체했다고 적혀 있으나 이번에 검증하지는 않음.
- TODO / FIXME: 0건 [확인].

### 테스트 현황

| 모듈 | 테스트 | 커버리지 수준 |
|---|---|---|
| 백엔드 | JUnit5 7개 클래스, **34건 전부 통과** (Clothes 6, Outfit 6, Shopping 3, UserController 4, UserPreferredStyles 8, UserPrincipal 3, Weather 4) | 서비스 단위 위주. Diagnosis·Vton·Home·AuthController·JWT·SecurityConfig·S3Uploader 테스트 없음 |
| AI 서버 | 없음 | 0 |
| Android | 없음 (`src/test` 디렉터리 없음) | 0 |
| 웹 | 없음 | 0 |
| CI | 테스트를 실행하는 워크플로 없음 | — |

---

## 5. 해야 할 작업

### P0 — Play Store 출시 전 필수

| 작업 | 난이도 | 관련 파일 |
|---|---|---|
| ✅ 릴리즈 빌드에서 HTTP 본문 로그 제거 (H-1) — `3fdbbb6` | S | `android/.../data/api/ApiClient.kt` |
| 🟡 회원 탈퇴 API·화면과 웹 삭제 안내 페이지 (H-2) — 앱 UI `9192c88`, API·웹 페이지 남음 | M | `UserController`, `UserService`, `ClothesRepository`, `StyleDiagnosisRepository`, `S3Uploader`, `ui/mypage/*` |
| 개인정보처리방침 작성·호스팅, 앱에 링크, 데이터 보안 양식 작성 (H-3) | M | 신규 페이지, `ui/mypage/MyPageScreen.kt`, Play Console |
| IDM-VTON 라이선스 확인 → 상업 사용이 불가하면 대체 방안 결정 (H-6) | S(확인) / L(교체) | `ai-server/app/services/vton_service.py` |
| targetSdk 정책 확인 후 36으로 상향, 패키지명 확정 (M-10) | M | `android/app/build.gradle.kts` |
| OAuth 토큰 전달 방식 개선 (일회용 코드 교환 또는 App Links) (H-7) | M | `OAuth2AuthenticationSuccessHandler`, `AuthController`, `MainViewModel`, `AndroidManifest.xml` |
| 🟡 리프레시 토큰을 Bearer로 못 쓰게 하기, 로그아웃 시 무효화 (M-1, M-2) — M-2 앱 `e2dadaf`, M-1 남음 | S | `JwtTokenProvider`, `JwtAuthenticationFilter`, `AuthApi.kt`, `AuthRepository.kt` |
| AI 생성 이미지(VTON) 신고 기능 — Play의 AI 생성 콘텐츠 정책 해당 여부 확인 [추정] | S~M | `ui/fitting/FittingScreen.kt` |
| `bundleRelease`(AAB) 빌드와 Play App Signing 설정 | S | `android/app/build.gradle.kts` |
| `allowBackup`에서 토큰 제외 (M-11) | S | `AndroidManifest.xml`, `res/xml/` |

### P1 — 핵심 기능 안정화·미완성분

| 작업 | 난이도 | 관련 파일 |
|---|---|---|
| RestClient 타임아웃과 스레드 풀 상한 (H-4) | S | `RestClientConfig.java`, `AsyncConfig.java`, `AiServerClient.java` |
| AI 호출을 트랜잭션 밖으로 이동 (H-5) | S | `VtonService`, `ClothesService`, `DiagnosisService`, `OutfitService`, `HomeService` |
| 피팅 업로드 이미지 압축, 허용 MIME 통일 (M-5) | S | `FittingViewModel.kt`, `S3Uploader.java`, `ai-server/app/routers/*.py` |
| 예외 핸들러 보강(400·404·413) (M-4) | S | `GlobalExceptionHandler.java` |
| 토큰 갱신 동시성 처리 (M-3) | S | `ApiClient.kt` |
| OAuth 실패 시 앱으로 복귀 (M-9) | S | `OAuth2AuthenticationFailureHandler.java` |
| AI 엔드포인트 사용자별 rate limit (M-7) | M | `nginx/nginx.conf` 또는 Spring 필터 |
| 진단 사진·VTON 결과 S3 보관 기한 (M-8) | S | S3 Lifecycle, `S3Uploader.generatePresignedUrl` |
| 🟡 날씨를 사용자 위치 기반으로 (현재 서울 고정), 기상청 전환 여부 결정 — 앱 `9cb4e6e`, 백엔드 남음 | M | `weather_service.py`, `WeatherService.java`, `HomeViewModel.kt` |
| 코디 캘린더: 기획대로 구현할지 공식 폐기할지 결정 | L(구현) | — |
| 배포 워크플로에 실패 중단과 테스트 선행 추가 (M-6) | S | `.github/workflows/deploy.yml` |

### P2 — 품질 개선

| 작업 | 난이도 | 관련 파일 |
|---|---|---|
| 백엔드 테스트 보강 (Diagnosis, Vton, Auth/JWT, Security, 예외 핸들러) | M | `backend/src/test/...` |
| AI 서버 pytest 도입 (JSON 파싱·코드 치환·카테고리 폴백) | M | `ai-server/app/services/*` |
| Android ViewModel 단위 테스트 (Closet, Fitting, `ProductPageRepository` 파싱) | M | `android/app/src/test/` (신규) |
| PR마다 빌드·테스트를 돌리는 CI 워크플로 | S | `.github/workflows/` |
| k6: BASE_URL을 환경변수 + https로, 옷장·홈 추천·VTON 시나리오 추가 | S | `performance/*.js` |
| 진입 불가 코드 정리 (진단·쇼핑·`ui/vton`·`/home/summary`) (L-11) | S | 해당 파일 |
| React 웹 처리 결정 (제거 또는 현행화) (M-12) | S(제거) / L(현행화) | `frontend/` |
| R8 활성화 (L-13), deprecated API 교체 (L-12) | S | `build.gradle.kts`, `ShoppingScreen.kt`, `VtonScreen.kt` |
| 하 등급 나머지 (L-1~L-10) | S | 표 참고 |

---

## 6. 확인 못 한 것 / 질문

### 실행 결과

| 항목 | 결과 |
|---|---|
| 백엔드 `./gradlew test` | ✅ BUILD SUCCESSFUL, 34/34 통과 |
| Android `assembleDebug assembleRelease` (Gradle 8.10.2) | ✅ BUILD SUCCESSFUL, 경고 2건 (L-12). 릴리즈 태스크는 up-to-date라 APK가 10/02 빌드분으로 남아 있음 → 소스가 그 이후 바뀌지 않았다는 뜻 |
| AI 서버 | ✅ 임시 venv에 의존성 설치, `compileall` 통과, 앱 import와 `/health` 200, 라우트 8개 확인. 실제 OpenAI·HF 호출은 하지 않음(비용·키 문제) |
| 웹 `npm run build` | ✅ 빌드 성공. 하지만 API가 맞지 않아 실제 동작은 안 될 것으로 판단 |
| Docker 이미지 빌드 | ❌ 확인 못 함. 로컬 Docker Desktop이 꺼져 있음 |
| 에뮬레이터 E2E, AAB 빌드 | ❌ 이번엔 하지 않음 (릴리즈 E2E 이력은 CLAUDE.md 2026-10-01·10-02 기록만 있음) |
| EC2 운영 상태, GitHub Actions 최근 실행 결과, 운영 `.env` 값 | ❌ 확인 못 함 (서버 접속과 시크릿 열람은 범위 밖) |
| 유출됐던 키의 실제 교체 여부 | ❌ 확인 못 함 (사용자 보고에만 의존) |

### 질문

1. **React 웹은 앞으로도 유지하나요?** 기획 스택에는 있지만 코드는 2026-07 이후 방치돼 있고 백엔드와 맞지 않습니다. 제거할지 현행화할지에 따라 P2 범위가 크게 달라집니다.
2. **코디 캘린더**는 다시 만드나요, 아니면 기획에서 뺄까요? 지금은 코드가 없습니다.
3. **날씨는 기상청이 기획**인가요? 코드는 OpenWeatherMap이고 서울로 고정돼 있습니다.
4. VTON을 **상업 출시에 그대로 쓸 계획**인가요? (라이선스·공개 Space 의존 문제)
5. 운영 `.env`의 `FRONTEND_URL` 값이 무엇인가요? (M-9 판단에 필요)
6. Play 출시 패키지명을 `com.fashionapp`로 갈 건가요? 출시 후에는 바꿀 수 없습니다.
7. 진단·쇼핑 화면은 피팅 탭에 통합할 예정인가요, 아니면 삭제할까요?
