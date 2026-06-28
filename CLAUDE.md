# CLAUDE.md — AI 패션 코디 앱 프로젝트 컨텍스트

> 이 파일은 Claude Code가 프로젝트 맥락을 파악하기 위한 문서입니다.
> 코드 작성 전 반드시 이 파일을 참고하세요.

---

## 프로젝트 개요

**서비스명**: AI 기반 개인 옷장 코디 및 가상 피팅 앱
**목표**: 온라인 쇼핑 반품률 문제를 해결하는 AI 패션 앱
**개발 형태**: 1인 개발 (포트폴리오 + 실서비스 출시 목표)

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| 프론트엔드 | React (웹) |
| 백엔드 | Spring Boot (JWT, REST API) |
| AI 서버 | FastAPI (Python) |
| DB | PostgreSQL + Redis |
| 이미지 스토리지 | AWS S3 |
| 인프라 | AWS EC2 + Docker + GitHub Actions |
| 개발 도구 | Claude Code |
| 인증 | 소셜 로그인 (Google OAuth2, Kakao OAuth2) |
| AI API | OpenAI Vision API (옷 분류), 기상청 API (날씨) |

---

## 서비스 아키텍처

```
[React 클라이언트]
      |
      | HTTPS
      ↓
[Spring Boot — 메인 백엔드]  ──→  [AWS S3] (이미지 저장)
      |              |
      | AI 요청       | DB 읽기/쓰기
      ↓              ↓
[FastAPI — AI 서버]    [PostgreSQL]
      |
      ↓
[외부 API]
  - OpenAI Vision API (옷 분류)
  - 기상청 API (날씨)

[Redis] ← Spring Boot (세션, 날씨 캐싱)
[GitHub Actions] → AWS EC2 (CI/CD 자동 배포)
```

---

## ERD

### users
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID PK | 사용자 ID |
| email | VARCHAR | 이메일 |
| nickname | VARCHAR | 닉네임 |
| profile_image_url | VARCHAR | 프로필 이미지 |
| provider | VARCHAR | 소셜 로그인 종류 (google/kakao) |
| provider_id | VARCHAR | 소셜 로그인 고유 ID |
| created_at | TIMESTAMP | 가입일 |
| updated_at | TIMESTAMP | 수정일 |

> ⚠️ 소셜 로그인 전용이므로 password_hash 없음

### clothes
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID PK | 옷 ID |
| user_id | UUID FK | 소유자 |
| image_url | VARCHAR | S3 이미지 URL |
| category | VARCHAR | 상의/하의/아우터 등 |
| color | VARCHAR | 색상 |
| pattern | VARCHAR | 패턴 |
| season | VARCHAR | 계절 |
| style_tag | VARCHAR | 캐주얼/포멀 등 |
| created_at | TIMESTAMP | 등록일 |

### outfits
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID PK | 코디 ID |
| user_id | UUID FK | 생성자 |
| name | VARCHAR | 코디명 |
| style_tag | VARCHAR | 스타일 태그 |
| weather_condition | VARCHAR | 날씨 조건 |
| created_at | TIMESTAMP | 생성일 |

### outfit_items (코디 ↔ 옷 다대다 중간 테이블)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID PK | ID |
| outfit_id | UUID FK | 코디 ID |
| clothes_id | UUID FK | 옷 ID |

### outfit_calendar
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID PK | ID |
| user_id | UUID FK | 사용자 |
| outfit_id | UUID FK | 착용 코디 |
| worn_date | DATE | 착용 날짜 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 기록일 |

---

## 프로젝트 구조

```
project-root/
├── frontend/                  # React
│   └── src/
│       ├── api/               # axios 요청 (auth.js, clothes.js, outfit.js)
│       ├── components/        # 공통 UI 컴포넌트
│       ├── pages/             # 라우트 페이지 (Login, Wardrobe, Recommend, Calendar, MyPage)
│       ├── hooks/             # 커스텀 훅 (useAuth, useWeather)
│       ├── store/             # 전역 상태 - Zustand
│       └── utils/
│
├── backend/                   # Spring Boot
│   └── src/main/java/com/fashionapp/
│       ├── domain/
│       │   ├── user/          # User.java, UserController, UserService, UserRepository
│       │   ├── clothes/       # Clothes.java, ClothesController, ClothesService, ClothesRepository
│       │   └── outfit/        # Outfit.java, OutfitController, OutfitService, OutfitRepository
│       ├── global/
│       │   ├── config/        # Security, CORS 설정
│       │   ├── jwt/           # JWT 토큰 처리
│       │   └── exception/     # 글로벌 에러 핸들링
│       └── infra/
│           ├── S3Uploader.java
│           └── AiServerClient.java
│
├── ai-server/                 # FastAPI
│   └── app/
│       ├── routers/           # classify.py, recommend.py, weather.py
│       ├── services/          # openai_service.py, weather_service.py, recommend_service.py
│       ├── schemas/           # Pydantic 모델 (clothes.py, outfit.py)
│       ├── core/              # config.py (환경변수)
│       └── main.py
│
├── .github/workflows/         # GitHub Actions CI/CD
├── docker-compose.yml
└── CLAUDE.md
```

---

## API 명세서

### Auth
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/auth/google | 구글 소셜 로그인 |
| POST | /api/auth/kakao | 카카오 소셜 로그인 |
| POST | /api/auth/refresh | JWT 토큰 갱신 |
| POST | /api/auth/logout | 로그아웃 |

### User
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/users/me 🔒 | 내 프로필 조회 |
| PUT | /api/users/me 🔒 | 프로필 수정 |

### Clothes
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/clothes 🔒 | 내 옷장 목록 |
| POST | /api/clothes 🔒 | 옷 업로드 → S3 저장 → AI 분류 |
| GET | /api/clothes/{id} 🔒 | 옷 상세 조회 |
| PUT | /api/clothes/{id} 🔒 | 옷 정보 수정 |
| DELETE | /api/clothes/{id} 🔒 | 옷 삭제 |

### Outfit
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/outfits 🔒 | 내 코디 목록 |
| POST | /api/outfits 🔒 | 코디 저장 |
| DELETE | /api/outfits/{id} 🔒 | 코디 삭제 |

### Calendar
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/calendar 🔒 | 월별 코디 기록 조회 |
| POST | /api/calendar 🔒 | 오늘 코디 기록 저장 |
| DELETE | /api/calendar/{id} 🔒 | 코디 기록 삭제 |

### AI 서버 (FastAPI — Spring Boot 내부 호출)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /ai/clothes/classify | 이미지 → 카테고리/색상/패턴 분류 |
| POST | /ai/outfits/recommend | 날씨 + 옷장 → 코디 추천 |
| GET | /ai/weather | 현재 날씨 조회 (기상청 API) |

> 🔒 = JWT 인증 필요

---

## 화면 구성 (MVP)

| 화면 | 설명 |
|------|------|
| 로그인 | 구글 / 카카오 소셜 로그인 |
| 옷장 | 카테고리 필터 + 옷 목록 + 업로드 버튼 |
| 코디 추천 | 오늘 날씨 카드 + 추천 코디 리스트 |
| 코디 캘린더 | 월별 캘린더 + 날짜별 착용 코디 |
| 마이페이지 | 프로필 + 통계(옷장/코디/착용일) + 설정 메뉴 |

---

## 개발 로드맵

### Phase 1 — MVP (3~4개월)
- [x] 소셜 로그인 (Google, Kakao OAuth2)
- [ ] 옷 사진 업로드 + S3 저장
- [ ] OpenAI Vision API 옷 자동 분류
- [ ] 날씨 기반 코디 추천
- [ ] 코디 저장 + 캘린더
- [ ] 마이페이지

### Phase 2 — VTON (3~6개월)
- [ ] AI 아바타 생성 (Ready Player Me API)
- [ ] 가상 피팅 (OOTDiffusion)
- [ ] 브랜드 제휴 입점

### Phase 3 — 커뮤니티 (추후)
- [ ] 코디 공유 피드
- [ ] 좋아요
- [ ] 댓글 / 팔로우 (반응 보고 추가)

---

## 코드 작성 규칙

- Spring Boot 패키지는 도메인 단위로 묶기 (user/clothes/outfit)
- Controller → Service → Repository 레이어 구조 유지
- 모든 API 응답은 공통 Response 포맷 사용
- 환경변수는 절대 하드코딩 금지 → .env 또는 application.yml 사용
- 소셜 로그인은 Spring Security OAuth2 Client 사용
- React 전역 상태는 Zustand 사용
- AI 서버(FastAPI)는 Spring Boot에서만 내부 호출 (클라이언트 직접 호출 금지)

---

## 개발 일지

### 2026-05-26

**완료한 작업**
- `outfit` 도메인 전체 구현 (백엔드)
  - 엔티티: `Outfit`, `OutfitItem`, `OutfitCalendar`
  - Repository: `OutfitRepository`, `OutfitItemRepository`, `OutfitCalendarRepository`
  - DTO: `OutfitCreateRequest`, `OutfitResponse`, `OutfitCalendarCreateRequest`, `OutfitCalendarResponse`
  - `OutfitService` — 코디 CRUD + 캘린더 CRUD 비즈니스 로직 전체
  - `OutfitController` — `GET/POST/DELETE /api/outfits`
  - `CalendarController` — `GET/POST/DELETE /api/calendar` (year/month 미전달 시 현재 월 기본값)
  - `ErrorCode` — `OUTFIT_NOT_FOUND`, `CALENDAR_NOT_FOUND` 추가

**현재 백엔드 구현 상태**
- `user` 도메인: 완료 (소셜 로그인, JWT, 프로필)
- `clothes` 도메인: 완료 (업로드, S3, AI 분류 연동)
- `outfit` 도메인: 완료 (코디 CRUD, 캘린더 CRUD)

---

**내일 이어서 할 작업**

1. **FastAPI AI 서버 구현** (`ai-server/app/`)
   - `routers/classify.py` — 이미지 → 카테고리/색상/패턴/시즌/스타일 분류
   - `routers/recommend.py` — 날씨 + 옷장 데이터 → 코디 추천
   - `routers/weather.py` — 기상청 API 날씨 조회
   - `services/openai_service.py`, `weather_service.py`, `recommend_service.py`
   - `schemas/clothes.py`, `outfit.py` — Pydantic 모델
   - `core/config.py` — 환경변수 (OpenAI API key, 기상청 API key)
   - `main.py` — FastAPI 앱 진입점

2. **React 프론트엔드 기본 세팅** (`frontend/src/`)
   - `package.json` 의존성 설정 (React, Axios, Zustand, React Router)
   - `api/auth.js`, `api/clothes.js`, `api/outfit.js` — Axios 요청 모듈
   - `store/authStore.js` — Zustand 전역 인증 상태
   - `hooks/useAuth.js`, `hooks/useWeather.js` — 커스텀 훅

3. **React 페이지 구현**
   - `pages/Login.jsx` — 구글/카카오 소셜 로그인 버튼
   - `pages/Wardrobe.jsx` — 옷장 목록 + 업로드
   - `pages/Recommend.jsx` — 날씨 카드 + 추천 코디
   - `pages/Calendar.jsx` — 월별 캘린더
   - `pages/MyPage.jsx` — 프로필 + 통계

---

### 2026-05-28

**완료한 작업**
- 프로젝트 구조 세팅 (디렉토리 구성)
- `CLAUDE.md` 작성 (프로젝트 컨텍스트 문서화)
- PostgreSQL Docker 컨테이너 실행 및 DB 연결 확인
- 구글 / 카카오 OAuth2 키 발급 (Client ID, Secret)
- Spring Boot 서버 실행 확인
- 소셜 로그인 화면 동작 확인 (구글 / 카카오 로그인 버튼 → 리디렉션)

**현재 전체 구현 상태**
- 인프라: PostgreSQL(Docker) 실행 중
- `user` 도메인: 완료 (소셜 로그인, JWT, 프로필)
- `clothes` 도메인: 완료 (업로드, S3, AI 분류 연동)
- `outfit` 도메인: 완료 (코디 CRUD, 캘린더 CRUD)
- AI 서버 (FastAPI): 미구현
- 프론트엔드 (React): 미구현

---

### 2026-05-31

**완료한 작업**

- FastAPI AI 서버 전체 구현 (`ai-server/`)
  - `core/config.py` — pydantic-settings 환경변수 관리 (OPENAI_API_KEY, WEATHER_API_KEY)
  - `schemas/` — `ClothesClassifyResponse`, `RecommendRequest/Response`, `WeatherResponse` (camelCase alias → Spring Boot 호환)
  - `services/openai_service.py` — GPT-4o Vision API로 옷 이미지 분류
  - `services/weather_service.py` — 기상청 단기예보 API (base_time 자동 계산, KST 처리)
  - `services/recommend_service.py` — GPT-4o로 날씨 + 옷장 기반 코디 추천
  - `routers/classify.py` — `POST /ai/clothes/classify`
  - `routers/recommend.py` — `POST /ai/outfits/recommend`
  - `routers/weather.py` — `GET /ai/weather?nx=60&ny=127`
  - `Dockerfile` + `requirements.txt`
- `docker-compose.yml` 작성 (postgres, redis, ai-server 서비스 구성)
- React 프론트엔드 전체 구현 (`frontend/`)
  - Vite + React 18, 개발 서버 프록시 (`/api` → 8080, `/ai` → 8000)
  - `store/authStore.js` — Zustand + localStorage persist (accessToken, refreshToken)
  - `api/client.js` — Axios 인스턴스, 401 자동 토큰 갱신 + 재시도 인터셉터
  - `api/clothes.js`, `api/outfit.js`, `api/auth.js` — API 모듈
  - `hooks/useAuth.js`, `hooks/useWeather.js` — 커스텀 훅
  - `components/PrivateRoute.jsx`, `components/BottomNav.jsx`
  - `pages/Login.jsx` — 구글/카카오 OAuth2 로그인
  - `pages/OAuth2Callback.jsx` — 토큰 수신 후 저장 및 리다이렉트
  - `pages/Wardrobe.jsx` — 카테고리 필터, 그리드, 업로드, 상세 모달
  - `pages/Recommend.jsx` — 날씨 카드, AI 코디 추천, 코디 저장
  - `pages/Calendar.jsx` — 월별 캘린더, 착용 기록 추가/삭제
  - `pages/MyPage.jsx` — 프로필, 통계, 로그아웃

**현재 전체 구현 상태**
- 인프라: PostgreSQL(Docker) 실행 중
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): **완료**
- 프론트엔드 (React): **완료**

---

---

### 2026-05-31 (E2E 테스트)

**완료한 작업**
- Docker Desktop 기동 → PostgreSQL + Redis 컨테이너 정상 실행 확인
- Spring Boot 기동 확인 (4.6초, 포트 8080)
- React Vite 개발 서버 기동 확인 (포트 3000)
- Google / Kakao OAuth2 리다이렉트 동작 확인 (302 → 각 플랫폼 인증 URL)
- Vite 프록시 (`/api` → 8080) 동작 확인
- **버그 발견 및 수정**: 미인증 API 요청이 401 대신 302(OAuth2 리다이렉트) 반환
  - 원인: `SecurityConfig`에 `AuthenticationEntryPoint` 누락
  - 수정: 람다 EntryPoint 추가 → 이제 모든 미인증 API 요청에 `HTTP 401 + JSON` 반환
  - 영향: Axios 인터셉터 토큰 갱신 로직이 정상 작동하게 됨

**미완료 / 확인 필요 항목**
- AI 서버 미기동: Python 미설치, `ai-server/.env` 없음 (OPENAI_API_KEY, WEATHER_API_KEY 필요)
- 옷 업로드 미테스트: AWS 자격증명 `"임시"` 상태 → 실제 S3 키 필요
- OAuth2 로그인 완전 흐름: 브라우저 수동 테스트 필요 (자동화 불가)

**현재 전체 구현 상태**
- 인프라: PostgreSQL + Redis (Docker 실행 중)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 코드 완료, 실행 환경 미완
- 프론트엔드 (React): 완료

---

**내일 이어서 할 작업**

1. **AI 서버 실행 환경 세팅**
   - `ai-server/.env` 파일 생성 (OPENAI_API_KEY, WEATHER_API_KEY 입력)
   - `docker compose up -d ai-server` 로 Docker 기동 (Python 별도 설치 불필요)
   - `/health`, `/ai/weather` 엔드포인트 curl 테스트

2. **브라우저 E2E 수동 테스트**
   - 로그인 → 소셜 로그인 완료 → `/wardrobe` 정상 진입 확인
   - 옷 사진 업로드 → AI 분류 결과 확인 (AWS S3 실제 키 필요)
   - 날씨 카드 + 코디 추천 → 저장 확인
   - 캘린더 착용 기록 추가 확인

3. **Spring Boot 날씨/추천 프록시 엔드포인트 추가** (아키텍처 정합성)
   - 현재 프론트가 AI 서버를 직접 호출 중 → Spring Boot 경유로 변경
   - `GET /api/weather` — `AiServerClient`에서 `/ai/weather` 호출 후 프론트에 반환
   - `POST /api/outfits/recommend` — 사용자 옷장 조회 + AI 서버 추천 호출 통합

4. **AWS 배포 세팅**
   - EC2 인스턴스 생성 및 Docker 설치
   - GitHub Actions CI/CD 워크플로우 작성 (`.github/workflows/deploy.yml`)
   - Nginx 리버스 프록시 설정 (80/443 → Spring Boot:8080, AI 서버:8000)

---

### 2026-06-07

**완료한 작업**

- **Android 앱 (Kotlin + Jetpack Compose) 전체 구현 완료** (`android/`)
  - 나머지 화면 구현: `CalendarScreen.kt`, `MyPageScreen.kt`, `MyPageViewModel.kt`
  - 리소스 파일: `strings.xml`, `themes.xml`, `proguard-rules.pro`
  - 런처 아이콘: `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`, `drawable/ic_launcher_foreground.xml`
  - 총 25개 Kotlin 파일 + 리소스 완성

- **버그 수정 — ic_launcher 빌드 오류**
  - 원인: adaptive icon 배경을 `@drawable/ic_launcher_background`(shape) 참조 → 일부 환경에서 오류
  - 수정: `colors.xml`에 `ic_launcher_background` 색상 추가 → `@color/ic_launcher_background` 참조로 변경

- **버그 수정 — 에뮬레이터 ERR_CONNECTION_REFUSED**
  - 원인: `build.gradle.kts`의 `BASE_URL`, `OAUTH2_BASE_URL`이 `localhost` → 에뮬레이터에서 자기 자신을 가리킴
  - 수정: `localhost` → `10.0.2.2` (에뮬레이터에서 host PC loopback 주소)

- **버그 수정 — 카카오 로그인 KOE205 오류**
  - 원인 1: `application.yml` 카카오 scope에 `account_email`이 남아있었음 (grep 컨텍스트 부족으로 미확인 상태였음)
  - 원인 2: `User.email` 컬럼이 `nullable = false` → email 없으면 DB 저장 실패
  - 수정: scope에서 `account_email` 제거, `User.email` → `nullable = true`

**현재 전체 구현 상태**
- 인프라: PostgreSQL + Redis (Docker)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- **Android 앱 (Kotlin + Jetpack Compose): 완료**

**다음에 할 작업**
1. 카카오 로그인 에뮬레이터 E2E 테스트 완료 확인
2. 옷 업로드 → S3 저장 → AI 분류 흐름 테스트 (AWS S3 실제 키 필요)
3. 날씨 카드 + 코디 추천 → 저장 확인
4. AWS 배포 세팅 (EC2 + GitHub Actions + Nginx)

---

### 2026-06-12

**완료한 작업**

- **버그 수정 — Google/Kakao OAuth2 `redirect_uri_mismatch` 오류**
  - 원인: `OAUTH2_BASE_URL`이 `http://10.0.2.2:8080`으로 설정 → Spring Boot가 redirect_uri를 `http://10.0.2.2:8080/login/oauth2/code/{provider}`로 생성
  - Google/Kakao 콘솔은 IP 주소를 redirect URI로 허용하지 않음 (`localhost`만 허용)
  - 수정: `android/app/build.gradle.kts`의 `OAUTH2_BASE_URL`을 `http://localhost:8080`으로 변경
  - 에뮬레이터 테스트 시 `adb reverse tcp:8080 tcp:8080` 실행 필수
  - Google Console / Kakao Developers에 `http://localhost:8080/login/oauth2/code/{google|kakao}` 등록
  - Google + Kakao 로그인 에뮬레이터 E2E 테스트 **통과**

> 참고: `BASE_URL`(`http://10.0.2.2:8080/api/`)은 Retrofit API 호출용이라 그대로 유지.
> `adb reverse` 없이도 동작. OAUTH2_BASE_URL만 localhost로 변경.

**현재 전체 구현 상태**
- 인프라: PostgreSQL + Redis (Docker)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료 (환경변수 미설정)
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- **소셜 로그인 (Google + Kakao): E2E 테스트 완료**

**다음에 할 작업**
1. AWS S3 버킷 생성 + IAM 키 발급 → `backend/.env` 업데이트
2. AI 서버 `ai-server/.env` 생성 (실제 OpenAI API 키 필요)
3. `docker compose up -d ai-server` 기동 후 옷 업로드 → S3 저장 → AI 분류 E2E 테스트
4. 날씨 카드 + 코디 추천 흐름 테스트
5. AWS 배포 세팅 (EC2 + GitHub Actions + Nginx)

---

### 2026-06-21

**완료한 작업**

- **docker-compose.yml 정리**
  - `postgres` 서비스 및 `postgres_data` 볼륨 제거 (외부 `fashionapp-db` 컨테이너와 충돌)
  - `ai-server`의 `depends_on: postgres` 제거
  - 이제 `docker compose up -d` 로 redis + ai-server만 관리

- **버그 수정 — 옷 업로드 HTTP 400 오류**
  - 원인: `WardrobeViewModel.kt`에서 `"image/*"`를 Content-Type으로 전송
  - `S3Uploader.java`의 허용 목록 (`image/jpeg`, `image/png`, `image/webp`, `image/heic`)에 없어서 `INVALID_IMAGE_FORMAT (400)` 발생
  - 수정: `context.contentResolver.getType(uri)`로 실제 MIME 타입 조회 후 전송
    - 파일: `android/app/src/main/java/com/fashionapp/ui/wardrobe/WardrobeViewModel.kt`

- **버그 수정 — AI 서버 422 Unprocessable Entity (멀티파트 body 비어있음)**
  - 원인: Spring Boot `RestClient`가 JDK HttpClient 기반으로 `Expect: 100-continue` 헤더를 전송
    - uvicorn이 `100 Continue`를 반환하지 않아 FastAPI가 빈 body로 멀티파트 파싱 → `"Field required"` 422 반환
    - 이후 Spring Boot가 body를 뒤늦게 전송 → uvicorn `"Invalid HTTP request received"` 경고
  - 수정: `RestClientConfig.java`에서 `SimpleClientHttpRequestFactory` 사용 (`Expect: 100-continue` 비활성화)
    - 파일: `backend/src/main/java/com/fashionapp/global/config/RestClientConfig.java`

- **버그 수정 — Spring Boot → AI 서버 멀티파트 전송 방식 개선**
  - `AiServerClient.java`의 `classifyClothes` 메서드를 `MultipartBodyBuilder` 방식으로 변경
    - Content-Type 및 filename이 명시적으로 설정되어 FastAPI가 올바르게 파싱 가능
  - 파일: `backend/src/main/java/com/fashionapp/infra/AiServerClient.java`

- **AI 서버 Docker 이미지 재빌드**
  - 컨테이너가 2주 전 빌드된 구 이미지를 사용 중이었음 → 코드 변경 미반영 상태
  - `docker compose build --no-cache ai-server && docker compose up -d ai-server`로 재빌드

**트러블슈팅 — 옷 업로드 E2E 디버깅 과정**

| 단계 | 증상 | 원인 | 수정 |
|------|------|------|------|
| 1 | Android → Spring Boot: HTTP 400 | `image/*` MIME 타입이 S3Uploader 허용 목록에 없음 | `contentResolver.getType(uri)`로 실제 타입 사용 |
| 2 | Spring Boot → AI 서버: HTTP 503 | AI 서버 미실행 상태 | `docker compose up -d ai-server` |
| 3 | AI 서버: HTTP 422 | `Expect: 100-continue`로 인해 FastAPI body가 비어있음 | `SimpleClientHttpRequestFactory`로 교체 |
| 4 | AI 서버: HTTP 500 | OpenAI가 앱 스크린샷 이미지 분류 거부 (`"I'm sorry..."`) | 실제 옷 사진으로 테스트 필요 |

> ⚠️ AI 분류 사용 모델: **GPT-4o Vision API** (`openai_service.py`)
> 테스트 시 에뮬레이터의 갤러리에 실제 옷 사진이 있어야 정상 분류됨

**현재 전체 구현 상태**
- 인프라: PostgreSQL (외부 컨테이너) + Redis + AI 서버 (Docker Compose)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료 + 실행 중
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- 소셜 로그인 (Google + Kakao): E2E 완료
- **옷 업로드 파이프라인**: Android → Spring Boot → S3 → AI 서버 연결 완료 (실제 옷 사진 E2E 미완)

**다음에 할 작업**
1. 실제 옷 사진으로 업로드 E2E 테스트 완료 (에뮬레이터 갤러리에 옷 사진 준비)
2. 날씨 카드 + 코디 추천 흐름 테스트
3. AWS 배포 세팅 (EC2 + GitHub Actions + Nginx)

---

### 2026-06-22

**완료한 작업**

- **버그 수정 — 옷 사진 업로드 후 옷장에 사진 미표시**
  - 원인 1: S3 버킷의 Block Public Access 설정으로 인해 저장된 `image_url` (정적 S3 URL)이 403 Forbidden
  - 수정: `S3Uploader.java`에 `generatePresignedUrl()` 메서드 추가 (`S3Presigner` 사용, 7일 유효)
    - `S3Config.java`에 `S3Presigner` 빈 추가
    - `ClothesService.java`에 `toResponse()` 헬퍼 추가 → `getMyClothes`, `getClothes`, `upload`, `update` 모두 pre-signed URL 반환
    - DB에는 기존 정적 URL 그대로 저장, API 응답 시에만 pre-signed URL로 변환
  - 파일: `backend/src/main/java/com/fashionapp/global/config/S3Config.java`
  - 파일: `backend/src/main/java/com/fashionapp/infra/S3Uploader.java`
  - 파일: `backend/src/main/java/com/fashionapp/domain/clothes/ClothesService.java`

- **버그 수정 — 앱 재시작 시 옷장 빈 화면 (401 이후 갱신 없음)**
  - 원인: `ApiClient.kt`에 토큰 만료(401) 시 자동 갱신 로직 없음 → refresh token으로 재발급 안 됨
  - 수정: `TokenAuthenticator` 추가 (`okhttp3.Authenticator` 구현)
    - 401 수신 시 별도 `OkHttpClient`로 `POST /api/auth/refresh` 직접 호출 (ApiClient 순환 의존 회피)
    - 새 토큰 `TokenDataStore`에 저장 → 원본 요청 새 토큰으로 재시도
    - 무한 루프 방지: `priorResponse` 카운트 ≥ 2이면 null 반환
    - refresh 실패 시 `clearTokens()` 호출
  - 파일: `android/app/src/main/java/com/fashionapp/data/api/ApiClient.kt`

**트러블슈팅 — 옷장 사진 미표시 디버깅 과정**

| 단계 | 증상 | 원인 | 수정 |
|------|------|------|------|
| 1 | 옷장 빈 화면 | `GET /api/clothes` → 401 (토큰 만료) | `TokenAuthenticator` 추가 |
| 2 | API 200 반환, 화면 여전히 빈 화면 | S3 정적 URL → 403 Forbidden (Block Public Access) | `generatePresignedUrl()` 추가 |
| 3 | Pre-signed URL 정상 반환 | — | Coil `AsyncImage`가 pre-signed URL 로드 성공 |

> ⚠️ DB `image_url` 컬럼에는 정적 S3 URL이 저장됨 (`https://{bucket}.s3.{region}.amazonaws.com/{key}`)
> API 응답 시 `extractKey()`로 키 추출 → `S3Presigner`로 7일짜리 서명 URL 생성

**현재 전체 구현 상태**
- 인프라: PostgreSQL (외부 컨테이너) + Redis + AI 서버 (Docker Compose)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료 + 실행 중
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- 소셜 로그인 (Google + Kakao): E2E 완료
- 옷 업로드 파이프라인: **E2E 완료** (Android → S3 → AI 분류 → DB 저장 → 옷장 표시)

**다음에 할 작업**
1. 날씨 카드 + 코디 추천 흐름 테스트
2. AI 서버 디버그 코드 제거 (`ai-server/app/main.py` 미들웨어, `routers/classify.py` 로그)
3. AWS 배포 세팅 (EC2 + GitHub Actions + Nginx)

---

### 2026-06-23

**완료한 작업**

- **버그 수정 — 로그아웃 API `Required request body is missing` 오류**
  - 원인: `AuthController.logout()`이 `@RequestBody Map<String, String>` 필수로 요구 → Android가 body 없이 POST 전송
  - 수정: `@RequestBody(required = false)`로 변경, null 체크 추가
  - 파일: `backend/src/main/java/com/fashionapp/domain/user/AuthController.java`

- **버그 수정 — AI 서버 추천 API 422 오류 예방**
  - 원인: `ClothesItem` Pydantic 스키마의 `category`, `color`, `pattern`, `season`, `style_tag`가 non-nullable `str`
    → DB 옷 데이터에 null 필드가 있으면 FastAPI 422 반환
  - 수정: 해당 필드를 `Optional[str] = None`으로 변경, `recommend_service.py`에서 null → `"미분류"` 처리
  - 파일: `ai-server/app/schemas/outfit.py`, `ai-server/app/services/recommend_service.py`

- **AI 서버 디버그 코드 제거**
  - `ai-server/app/main.py` — HTTP 요청 body 출력 미들웨어 제거
  - `ai-server/app/routers/classify.py` — `logger.info` 헤더/파일명 로그 제거

- **날씨 카드 + 코디 추천 E2E 테스트 완료**
  - Android → `GET /api/weather` → Spring Boot → `GET /ai/weather` → OpenWeatherMap API → 날씨 카드 표시
  - Android → `POST /api/outfits/recommend` → Spring Boot → 옷장 조회 + `POST /ai/outfits/recommend` → GPT-4o → 추천 결과 표시
  - 추천 코디 저장 (`POST /api/outfits`) 정상 동작 확인

- **캘린더 착용 기록 추가 UI 구현**
  - `CalendarScreen`에 착용 기록 추가 기능 없음 → 구현
  - 날짜 선택 시 헤더에 **+** 버튼 추가, 저장된 코디 없으면 비활성화
  - `AddCalendarEntryDialog` 추가: `ExposedDropdownMenuBox`로 코디 선택 + 메모 입력
  - 파일: `android/app/src/main/java/com/fashionapp/ui/calendar/CalendarScreen.kt`

- **버그 수정 — `OutfitCalendarResponse` 구조 불일치**
  - 원인: 서버가 `outfit: OutfitResponse` (중첩 객체) 반환 → Android `OutfitCalendar`에 `outfitId`, `outfitName` 필드가 없어 항상 "코디" 표시
  - 수정: `OutfitCalendarResponse`를 `outfitId: UUID`, `outfitName: String`만 반환하도록 단순화
  - 파일: `backend/src/main/java/com/fashionapp/domain/outfit/OutfitCalendarResponse.java`

- **버그 수정 — `CalendarViewModel.loadOutfits()` 실패 무시**
  - 수정: `.onFailure { _errorMessage.value = "코디 불러오기 실패: ${it.message}" }` 추가
  - 파일: `android/app/src/main/java/com/fashionapp/ui/calendar/CalendarViewModel.kt`

- **버그 수정 — 옷장 카테고리 필터 동작 안 함**
  - 원인: `filteredClothes`가 plain getter → Compose가 `_clothes` 변경을 감지하지 못해 탭 전환 시 필터 미적용
  - 수정: `combine(_clothes, _selectedCategory)`로 `StateFlow` 변환, Screen에서 `collectAsState()`로 수집
  - 파일: `android/app/src/main/java/com/fashionapp/ui/wardrobe/WardrobeViewModel.kt`, `WardrobeScreen.kt`

- **캘린더 E2E 테스트 완료**
  - 날짜 클릭 → 기록 섹션 + **+** 버튼 표시 ✓
  - 코디 선택 → 저장 → 달력 점(●) 표시 ✓
  - 기록 카드에 실제 코디 이름 표시 ✓
  - 삭제 ✓

**트러블슈팅 — 캘린더 삭제 404 원인 추적**
- Spring Boot 로그에 `CustomException` 없음 → Spring Boot에서 발생한 404 아님
- Android Logcat(OkHttp) 확인 결과: `DELETE /api/calendar/{id}` → **200 OK** 정상 동작
- 이전에 보였던 404는 이전 `OutfitCalendarResponse` 구조 문제로 인한 일시적 오류로 추정, 수정 후 사라짐

**현재 전체 구현 상태**
- 인프라: PostgreSQL (외부 컨테이너) + Redis + AI 서버 (Docker Compose)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료 + 실행 중
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- 소셜 로그인 (Google + Kakao): E2E 완료
- 옷 업로드 파이프라인: E2E 완료
- 날씨 카드 + 코디 추천: E2E 완료
- **캘린더 착용 기록**: **E2E 완료**
- **옷장 카테고리 필터**: **수정 완료**

**다음에 할 작업**
1. AWS 배포 세팅 (EC2 + GitHub Actions + Nginx)

