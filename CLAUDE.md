# CLAUDE.md — AI 패션 코디 앱 프로젝트 컨텍스트

> 이 파일은 Claude Code가 프로젝트 맥락을 파악하기 위한 문서입니다.
> 코드 작성 전 반드시 이 파일을 참고하세요.

---

## 프로젝트 개요

> ⚠️ **2026-07-26 전면 방향 전환**: 기존 "개인 옷장 관리 + 가상 피팅" 방향에서
> **"패션에 무지한 사람들을 위한 AI 스타일리스트"** 방향으로 피봇. 상세 배경은
> 아래 [개발 일지 2026-07-26] 참고.
>
> ⚠️ **2026-07-26 (追加 3) 쇼핑 연동 방식 확정**: 네이버쇼핑 API 연동 계획을 폐기하고,
> 실제 상품 검색·링크 없이 **AI가 생성한 텍스트로 "무신사/지그재그 등에서 ○○ 검색해보세요"
> 형태의 검색 제안만 제공**하는 방식으로 통일 (오늘의 코디 추천 · 쇼핑 도우미 둘 다 동일).
> 외부 쇼핑 API 키 발급/연동 부담 없이 기존 OpenAI GPT-4o 프롬프트 엔지니어링만으로 구현.

**서비스명**: AI 스타일리스트 앱
**목표**: 옷에 대한 지식이 없어도 날씨/상황/체형에 맞는 코디를 추천받고,
자신의 코디를 진단받고, 필요한 옷을 실제로 구매까지 이어갈 수 있는 AI 패션 앱
**개발 형태**: 1인 개발 (포트폴리오 + 실서비스 출시 목표)

**핵심 기능 3가지**
1. **오늘의 코디 추천** — 날씨(자동) + 상황 + 체형/취향 → AI 텍스트 코디 추천 + "무신사/지그재그에서 ○○ 검색해보세요" 형태의 쇼핑몰 검색 제안 (실제 상품 API 연동 아님, AI가 생성하는 텍스트)
2. **내 옷 진단** — 코디 사진 촬영 → AI 코디 점수(0~100) + 개선 제안 + 비슷한 스타일 추천
3. **쇼핑 도우미** — 예산 + 상황 + 체형 입력 → AI가 구체적인 아이템 텍스트 추천 + 쇼핑몰 검색 제안 + "이것만 사면 N가지 코디 가능" 활용법 제시

**유지하는 기존 자산**: 소셜 로그인(Google/Kakao), 체형·취향 프로필, 날씨 API 연동,
OpenAI Vision 연동 기반, Spring Boot 백엔드 구조, EC2/Docker/CI·CD 인프라

**제거 대상**: 옷장 등록/관리(`clothes` 도메인), 코디 캘린더, 옷 사진 → 카테고리/색상 자동 분류

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| 프론트엔드 | React (웹, 미사용) |
| 백엔드 | Spring Boot (JWT, REST API) |
| AI 서버 | FastAPI (Python) |
| DB | PostgreSQL + Redis |
| 이미지 스토리지 | AWS S3 (코디 진단용 사진 임시 저장) |
| 인프라 | AWS EC2 + Docker + GitHub Actions |
| 개발 도구 | Claude Code |
| 인증 | 소셜 로그인 (Google OAuth2, Kakao OAuth2) |
| AI API | OpenAI Vision API (코디 사진 진단, gpt-4o), OpenAI GPT-4o-mini (텍스트 — 코디/쇼핑 추천 및 검색 제안 생성), 기상청 API (날씨) |

---

## 서비스 아키텍처

```
[Android 클라이언트]
      |
      | HTTPS
      ↓
[Spring Boot — 메인 백엔드]  ──→  [AWS S3] (코디 진단 사진 임시 저장)
      |              |
      | AI 요청       | DB 읽기/쓰기
      ↓              ↓
[FastAPI — AI 서버]    [PostgreSQL]
      |
      ↓
[외부 API]
  - OpenAI Vision API (코디 사진 진단: 점수 + 개선 제안)
  - OpenAI (텍스트) (오늘의 코디 추천 + 쇼핑몰 검색 제안, 쇼핑 도우미 아이템 추천)
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
| height | INTEGER | 키 (cm) |
| weight | INTEGER | 몸무게 (kg) |
| body_type | VARCHAR | 체형 (SLIM/NORMAL/MUSCULAR/CHUBBY) |
| preferred_style | VARCHAR | 선호 스타일 (CASUAL/FORMAL/SPORTY/STREET/VINTAGE/MINIMAL) |
| created_at | TIMESTAMP | 가입일 |
| updated_at | TIMESTAMP | 수정일 |

> ⚠️ 소셜 로그인 전용이므로 password_hash 없음

> ⚠️ `clothes`, `outfits`, `outfit_items`, `outfit_calendar` 테이블은 제거 대상
> (옷장 등록/코디 저장/캘린더 기능 자체를 없애므로). 마이그레이션 시 `DROP TABLE`.

### outfit_recommendations (오늘의 코디 추천 기록 — 조회 전용, 재추천/이력용)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID PK | ID |
| user_id | UUID FK | 사용자 |
| situation | VARCHAR | 상황 (출근/데이트/운동/여행/면접/일상 등, 자유 텍스트) |
| weather_condition | VARCHAR | 추천 시점 날씨 |
| recommendation_text | TEXT | AI 추천 코디 텍스트 |
| created_at | TIMESTAMP | 생성일 |

> 실제 옷 소유 여부와 무관한 텍스트 추천이라 `clothes`/`outfit_items`처럼 다대다로
> 묶을 대상이 없음. 쇼핑몰 검색 제안도 실제 상품 데이터가 아니라 AI가 그때그때 생성하는
> 텍스트이므로 별도 저장/캐싱 없이 매 요청마다 새로 생성.

### style_diagnoses (내 옷 진단 기록)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID PK | ID |
| user_id | UUID FK | 사용자 |
| image_url | VARCHAR | 진단한 코디 사진 S3 URL (pre-signed로 응답) |
| score | INTEGER | AI 코디 점수 (0~100) |
| feedback | TEXT | 개선 제안 텍스트 |
| similar_styles | TEXT | 비슷한 스타일 추천 목록 (JSON 직렬화된 `[{styleTag, description}]`) |
| created_at | TIMESTAMP | 진단일 |

> Phase 2 구현 시 최초 계획에 없던 `similar_styles` 컬럼을 추가함 — GET 이력 조회 시에도
> "비슷한 스타일 추천"을 동일하게 보여주기 위해 저장이 필요했음 (JSON 텍스트 컬럼, Jackson `ObjectMapper`로 직렬화/역직렬화)

---

## 프로젝트 구조

> ⚠️ 아래는 피봇 이후 목표 구조. `clothes` 도메인/화면, `calendar` 화면은 삭제 대상이며
> `wardrobe` 화면은 없어지고 `recommend`(오늘의 코디), `diagnosis`(옷 진단),
> `shopping`(쇼핑 도우미) 3개 화면이 하단 탭의 중심이 된다. React 프론트엔드는 실서비스
> 미사용(Android 전용 운영)이라 이번 피봇 반영 대상에서 제외 — 필요해지면 그때 갱신.

```
project-root/
├── frontend/                  # React (웹, 미사용 — 이번 피봇 반영 안 함)
│   └── src/ ...
│
├── android/                   # Kotlin + Jetpack Compose (실서비스 클라이언트)
│   └── app/src/main/java/com/fashionapp/
│       └── ui/
│           ├── login/         # 소셜 로그인
│           ├── mypage/        # 프로필, 체형/취향 설정
│           ├── recommend/     # 오늘의 코디 추천 (날씨+상황+체형 → 텍스트+쇼핑몰 검색 제안)
│           ├── diagnosis/     # 내 옷 진단 (사진 → 점수+피드백) — 신규
│           └── shopping/      # 쇼핑 도우미 (예산+상황+체형 → 상품 추천) — 신규
│
├── backend/                   # Spring Boot
│   └── src/main/java/com/fashionapp/
│       ├── domain/
│       │   ├── user/          # User.java, UserController, UserService, UserRepository
│       │   ├── weather/       # 날씨 프록시
│       │   ├── recommend/     # 오늘의 코디 추천 (기존 outfit 도메인에서 정리)
│       │   ├── diagnosis/     # 내 옷 진단 — 신규
│       │   └── shopping/      # 쇼핑 도우미 — 신규
│       ├── global/
│       │   ├── config/        # Security, CORS 설정
│       │   ├── jwt/           # JWT 토큰 처리
│       │   └── exception/     # 글로벌 에러 핸들링
│       └── infra/
│           ├── S3Uploader.java        # 진단 사진 임시 저장용으로 존속
│           └── AiServerClient.java
│
├── ai-server/                 # FastAPI (쇼핑몰 검색 제안도 외부 API 없이 OpenAI 프롬프트로 생성)
│   └── app/
│       ├── routers/           # diagnosis.py(신규), recommend.py, shopping.py(신규), weather.py
│       ├── services/          # weather_service.py, recommend_service.py, diagnosis_service.py(신규), shopping_service.py(신규)
│       ├── schemas/            # Pydantic 모델 (outfit.py, diagnosis.py(신규), shopping.py(신규))
│       ├── core/              # config.py (환경변수 — OPENAI_API_KEY, WEATHER_API_KEY)
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

> ⚠️ 아래는 피봇 이후 목표 API. `/api/clothes/*`, `/api/outfits`(CRUD), `/api/calendar/*`는
> 제거 대상. `/api/outfits/recommend/situation`은 이미 구현되어 있던 기능이라
> `/api/recommend/today`로 정리하며 재사용, 여기에 쇼핑몰 검색 제안(AI 텍스트, 외부 상품 API 없음)만 추가.

### Recommend (오늘의 코디 추천)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/recommend/today 🔒 | 날씨(자동) + 상황 + 체형/취향 프로필 → AI 코디 텍스트 + 쇼핑몰 검색 제안(무신사/지그재그 등, AI 텍스트) |
| GET | /api/recommend/history 🔒 | 내 추천 이력 조회 (선택 구현) |

### Diagnosis (내 옷 진단) — 신규
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/diagnosis 🔒 | 코디 사진 업로드 → S3 저장 → AI 점수(0~100) + 개선 제안 + 비슷한 스타일 추천 |
| GET | /api/diagnosis 🔒 | 내 진단 이력 목록 |
| GET | /api/diagnosis/{id} 🔒 | 진단 상세 조회 |

### Shopping (쇼핑 도우미) — 신규
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/shopping/recommend 🔒 | 예산 + 상황 + 체형/취향 → AI 아이템 텍스트 추천 + 쇼핑몰 검색 제안 + 활용법("이것만 사면 N가지 코디") |

### AI 서버 (FastAPI — Spring Boot 내부 호출)
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /ai/recommend/today | 날씨 + 상황 + 체형 프로필 → 코디 텍스트 추천 + 쇼핑몰 검색 제안 |
| POST | /ai/diagnosis | 코디 이미지 → 점수 + 개선 제안 (OpenAI Vision) |
| POST | /ai/shopping/recommend | 예산 + 상황 + 체형 → AI 아이템 조합 추천 + 쇼핑몰 검색 제안 텍스트 생성 |
| GET | /ai/weather | 현재 날씨 조회 (기상청 API) |

> 🔒 = JWT 인증 필요

---

## 화면 구성 (피봇 이후, 하단 탭 3+1구조)

| 화면 | 설명 |
|------|------|
| 로그인 | 구글 / 카카오 소셜 로그인 |
| 오늘의 코디 | 날씨 카드 + 상황 선택 + AI 코디 추천 텍스트 + 쇼핑몰 검색 제안(무신사/지그재그 등) |
| 내 옷 진단 | 코디 사진 촬영/업로드 + AI 점수(0~100) + 개선 제안 + 비슷한 스타일 |
| 쇼핑 도우미 | 예산 + 상황 입력 + AI 추천 아이템 + 쇼핑몰 검색 제안 + 활용법("이것만 사면 N가지 코디") |
| 마이페이지 | 프로필 + 체형/취향 설정 + 진단 이력 + 설정 메뉴 |

> 기존 `옷장`, `코디 캘린더` 화면은 제거.

---

## 개발 로드맵 (2026-07-26 피봇 이후)

### Phase 0 — 기존 자산 정리
- [x] 백엔드 `clothes` 도메인 삭제, `outfit` 도메인 정리(캘린더·옷장기반 추천 제거, 상황 기반 추천만 존속)
- [x] AI 서버 `classify.py`/`schemas/clothes.py`/`openai_service.py` 삭제 (호출부가 사라져 완전히 죽은 코드였음 — 진단 기능은 Phase 2에서 새 프롬프트로 신규 작성)
- [x] Android `ui/wardrobe`, `ui/calendar` 화면·ViewModel·API 모듈 삭제
- [x] DB 마이그레이션: `clothes`, `outfits`, `outfit_items`, `outfit_calendar` 테이블 제거
  → `backend/src/main/resources/db/phase0_cleanup.sql` 로컬 DB(`fashionapp-db` 컨테이너)에 실행 완료 (2026-07-26). **EC2 운영 DB에는 아직 미실행**

### Phase 1 — 오늘의 코디 추천 고도화 (기존 상황 기반 추천 재사용, 가장 빠른 완성 가능)
- [x] 날씨 + 상황 + 체형 프로필 → AI 코디 텍스트 추천 (기존 구현 재사용)
- [x] AI 서버 프롬프트에 쇼핑몰 검색 제안 항목 추가 (외부 API 없이 GPT-4o가 "무신사/지그재그 등에서 ○○ 검색해보세요" 형태 텍스트 생성)
- [x] Android `recommend` 화면에 쇼핑 검색 제안 카드 추가 (옷장 기반 탭은 이미 Phase 0에서 제거됨)

### Phase 2 — 내 옷 진단 (신규)
- [x] 백엔드 `diagnosis` 도메인 신설 (사진 업로드 → S3 → AI 서버 호출 → 점수/피드백/유사스타일 저장)
- [x] AI 서버 `routers/diagnosis.py` — OpenAI Vision으로 코디 사진 점수화 + 개선 제안 + 유사 스타일 추천 프롬프트 설계
- [x] Android `diagnosis` 화면 (갤러리 업로드 + 결과 카드 + 이력 목록)

### Phase 3 — 쇼핑 도우미 (신규)
- [x] AI 서버 `routers/shopping.py` — 예산/상황/체형 → GPT-4o로 구체적 아이템 조합 + 쇼핑몰 검색 제안 + "이것만 사면 N가지 코디" 활용법 텍스트 생성 (외부 상품 API 없음)
- [x] 백엔드 `shopping` 도메인 (프록시 + 요청 검증)
- [x] Android `shopping` 화면 (예산 입력 + 상황 선택 + 추천 카드 리스트)

### Phase 4 — 통합 마무리
- [x] Android 하단 네비게이션 재구성 (오늘의 코디 / 옷 진단 / 쇼핑 / 마이페이지)
- [x] 전체 E2E 테스트 + k6 성능 측정 재실행 (2026-07-27 실행 완료, 결과는 개발 일지 2026-07-27 (追加) 참고)
- [ ] EC2 운영 DB에도 `phase0_cleanup.sql` 적용 (로컬은 2026-07-26 완료, EC2는 아직)

### Phase 5 — 후속 (추후)
- [ ] 커뮤니티(코디 공유 피드, 좋아요, 댓글) — 우선순위 낮음, 3가지 핵심 기능 안정화 후 검토

---

## 코드 작성 규칙

- Spring Boot 패키지는 도메인 단위로 묶기 (user/weather/recommend/diagnosis/shopping)
- Controller → Service → Repository 레이어 구조 유지
- 모든 API 응답은 공통 Response 포맷 사용
- 환경변수는 절대 하드코딩 금지 → .env 또는 application.yml 사용
- 소셜 로그인은 Spring Security OAuth2 Client 사용
- React 전역 상태는 Zustand 사용 (단, React 웹 버전은 미사용 상태 — 우선순위 낮음)
- AI 서버(FastAPI)는 Spring Boot에서만 내부 호출 (클라이언트 직접 호출 금지)
- 쇼핑몰 검색 제안(무신사/지그재그 등)은 외부 상품 검색 API를 연동하지 않고, AI 서버의 OpenAI GPT-4o-mini 프롬프트가 텍스트로 직접 생성 (실제 상품 데이터/링크 아님 — 사용자에게도 "AI 추천"임을 전제로 노출)

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

---

### 2026-06-28

**완료한 작업**

- **AWS EC2 배포 완료**
  - Ubuntu 24.04 t3.small 인스턴스 생성
  - Docker + Git 설치
  - `docker-compose.prod.yml`로 전체 서비스 배포 (PostgreSQL, Redis, Spring Boot, FastAPI, Nginx)
  - 서비스 헬스체크 적용 (postgres `pg_isready`, redis `redis-cli ping`)
  - `backend/Dockerfile` 추가 (multi-stage: Gradle 빌드 → JRE 실행)

- **GitHub Actions CI/CD 구축**
  - `.github/workflows/deploy.yml` 작성
  - `dev` 브랜치 push → EC2 SSH 접속 → `git pull` → `docker compose up -d --build` 자동 실행
  - Secrets: `EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_KEY` 등록 완료

- **DuckDNS 무료 도메인 연결**
  - `fashion-app-jh.duckdns.org` → EC2 퍼블릭 IP 연결
  - Google / Kakao OAuth2 Redirect URI 추가 등록:
    - `http://fashion-app-jh.duckdns.org/login/oauth2/code/google`
    - `http://fashion-app-jh.duckdns.org/login/oauth2/code/kakao`

- **Android 앱 실서버 연결**
  - `BASE_URL` → `http://fashion-app-jh.duckdns.org/api/`
  - `OAUTH2_BASE_URL` → `http://fashion-app-jh.duckdns.org`
  - `network_security_config.xml`에 `fashion-app-jh.duckdns.org` HTTP 허용 추가
  - 실서버에서 로그인, 날씨, 코디 추천 정상 동작 확인

**현재 전체 구현 상태**
- 인프라: PostgreSQL + Redis + AI 서버 + Nginx (EC2 Docker Compose)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료 + 실행 중
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- 소셜 로그인 (Google + Kakao): E2E 완료
- 옷 업로드 파이프라인: E2E 완료
- 날씨 카드 + 코디 추천: E2E 완료
- 캘린더 착용 기록: E2E 완료
- **EC2 배포 + CI/CD**: **완료**

**다음에 할 작업**
1. 플레이스토어 등록
2. 성능 측정 (포트폴리오용)
3. VTON 가상 피팅 (Phase 2)

---

### 2026-07-08

**완료한 작업**

- **k6 성능 측정 스크립트 작성** (`performance/`)
  - `k6-latency.js` — API 응답 시간 측정 (1 VU, 20회 반복, 엔드포인트별 Trend 메트릭)
    - 측정 대상: `GET /users/me`, `/clothes`, `/outfits`, `/calendar`, `/weather`
    - 임계값: DB 읽기 p95 < 500ms, 날씨 p95 < 1,000ms
  - `k6-load.js` — 부하 테스트 (최대 50 VUs, 총 5분)
    - 단계: 워밍업(1 VU) → 증가(20 VUs) → 유지(2분) → 피크(50 VUs) → 쿨다운
    - 임계값: p95 < 500ms, p99 < 1,000ms, 오류율 < 1%
    - 대상: DB 읽기 엔드포인트 (AI 엔드포인트 제외 — GPT-4o 비용)
  - `k6-ai.js` — AI 엔드포인트 단독 측정 (5회 제한)
    - 측정 대상: `GET /weather`, `POST /outfits/recommend`
    - 임계값: 추천 p95 < 10,000ms (GPT-4o 포함), 날씨 p95 < 3,000ms

**실행 방법**
```bash
# 1. k6 설치 (Windows)
winget install k6

# 2. JWT 토큰: Android 앱 로그인 후 Logcat에서 accessToken 복사

# 3. 순서대로 실행
k6 run -e JWT_TOKEN=<토큰> performance/k6-latency.js
k6 run -e JWT_TOKEN=<토큰> performance/k6-ai.js
k6 run -e JWT_TOKEN=<토큰> performance/k6-load.js
```

**현재 전체 구현 상태**
- 인프라: PostgreSQL + Redis + AI 서버 + Nginx (EC2 Docker Compose)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료 + 실행 중
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- 소셜 로그인 (Google + Kakao): E2E 완료
- 옷 업로드 파이프라인: E2E 완료
- 날씨 카드 + 코디 추천: E2E 완료
- 캘린더 착용 기록: E2E 완료
- EC2 배포 + CI/CD: 완료
- **성능 측정 스크립트**: **작성 완료 (미실행)**

**다음에 할 작업**
1. k6 설치 후 JWT 토큰 준비 → 성능 측정 실행 및 결과 정리
2. 플레이스토어 등록
3. VTON 가상 피팅 (Phase 2)

---

### 2026-07-13

**완료한 작업**

- **디버그 로그 정리**
  - `ai-server/app/services/openai_service.py` — OpenAI 응답 디버깅용 `print()` 제거 (finish_reason/content 출력)
  - TODO/FIXME 주석: 전체 프로젝트 grep 결과 없음 (정리할 항목 없었음)

- **사용하지 않는 코드 제거** (전체 코드베이스 grep으로 교차 참조 확인 후 삭제)
  - 백엔드: `OutfitItemRepository` (미사용 인터페이스, 파일 삭제), `UserRepository.findByEmail`, `ClothesRepository.deleteByUser_Id`
  - Android: `ClothesApi.getClothesById`, `ClothesApi/Repository.updateClothes` (+ 연쇄적으로 미사용이 된 `ClothesUpdateRequest` 모델), `OutfitApi/Repository.deleteOutfit`, `CalendarViewModel.isLoading` (선언·갱신만 되고 `CalendarScreen`에서 구독 안 함), `WardrobeViewModel.clothes` (Screen은 `filteredClothes`만 사용)
  - 프론트엔드(React, 미사용 웹 버전): `api/clothes.js`의 `updateClothes`, `api/outfit.js`의 `deleteOutfit`
  - 백엔드의 `ClothesController`/`OutfitController` 쪽 실제 REST 엔드포인트(PUT/DELETE)는 유지 — 클라이언트가 안 쓸 뿐 살아있는 공개 API라 삭제 대상 아님

- **빌드 검증 완료**
  - 백엔드: `./gradlew compileJava` — BUILD SUCCESSFUL
  - 프론트엔드: `npm run build` (Vite) — 성공
  - Android: `gradle compileDebugKotlin` (프로젝트에 `gradlew` 래퍼가 없어 `~/.gradle/wrapper/dists`에 캐시된 Gradle 8.10.2를 직접 사용) — BUILD SUCCESSFUL, 기존 deprecation 경고 2건 외 이상 없음
  - AI 서버: 로컬 Python/Docker 미가동 상태라 직접 실행 검증은 못함 (제거한 코드는 단순 `print()` 한 줄이라 리스크 낮음)

> ⚠️ Android 프로젝트에 `gradlew`/`gradlew.bat`가 커밋되어 있지 않음 — CLI 빌드 시 Android Studio가 아니라면 시스템 Gradle이나 캐시된 배포판을 직접 지정해야 함

**현재 전체 구현 상태**
- 인프라: PostgreSQL + Redis + AI 서버 + Nginx (EC2 Docker Compose)
- `user` 도메인: 완료
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료
- AI 서버 (FastAPI): 완료 + 실행 중
- 프론트엔드 (React): 완료 (웹 버전, 미사용)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- 소셜 로그인 (Google + Kakao): E2E 완료
- 옷 업로드 파이프라인: E2E 완료
- 날씨 카드 + 코디 추천: E2E 완료
- 캘린더 착용 기록: E2E 완료
- EC2 배포 + CI/CD: 완료
- 성능 측정 스크립트: 작성 완료 (미실행)
- **코드 정리 (디버그 로그/TODO/미사용 코드)**: **완료**

**다음에 할 작업**
1. k6 설치 후 JWT 토큰 준비 → 성능 측정 실행 및 결과 정리
2. 플레이스토어 등록
3. VTON 가상 피팅 (Phase 2)

---

### 2026-07-23

**완료한 작업**

- **사용자 체형/취향 프로필 추가**
  - `User.java`에 `height`, `weight`, `bodyType`(enum: SLIM/NORMAL/MUSCULAR/CHUBBY), `preferredStyle`(enum: CASUAL/FORMAL/SPORTY/STREET/VINTAGE/MINIMAL) 필드 추가 — `ddl-auto: update`로 컬럼 자동 생성
  - `UpdateProfileRequest`/`UserResponse`에 새 필드 반영, `UserService.updateProfile`에서 enum 파싱 실패 시 `INVALID_BODY_PROFILE(400)` 반환
  - `CustomOAuth2UserService.saveOrUpdate`가 재로그인 시 기존 체형/취향 값을 덮어쓰지 않도록 `user.update(...)` 호출에 기존 값 전달하도록 수정
  - Android: `UserProfile`/`UpdateProfileRequest` 모델 확장, `AuthApi.updateProfile` (`PUT /api/users/me`) 신규 추가 (기존엔 Android에 프로필 수정 호출 자체가 없었음), 마이페이지에 "체형·취향 설정" 화면(`BodyProfileScreen`) 신규 추가

- **옷장 없이 상황 기반 AI 코디 추천 기능 추가**
  - 기존 `/api/outfits/recommend`(옷장 보유 옷 목록 기반)와 별개로 `POST /api/outfits/recommend/situation` 신규 추가
  - 사용자의 체형/취향 프로필 + 날씨 + 상황(출근/데이트/운동/여행/면접/일상)만으로 GPT-4o가 구체적인 코디 텍스트(품목 조합)를 추천 — 실제 옷 ID가 없어 `outfits`/`outfit_items`에는 저장하지 않음(조회 전용)
  - Spring Boot: `SituationRecommendRequest`, `AiSituationRecommendRequest/Response` DTO, `OutfitService.recommendBySituation`, `AiServerClient.recommendOutfitsBySituation` 추가
  - FastAPI: `schemas/outfit.py`에 `BodyProfile`/`SituationRecommendRequest`/`SituationRecommendResponse` 추가, `recommend_service.recommend_outfit_by_situation` 추가 (기존 옷장 기반 프롬프트와 별도의 프롬프트 템플릿 사용), `routers/recommend.py`에 라우트 추가
  - Android: `RecommendScreen`에 "옷장 기반"/"상황 기반" 탭 추가 — 상황 기반 탭은 날씨 카드 공유 + 상황 선택 드롭다운 + 추천 결과 카드(저장 버튼 없음)

- **상황(situation) 값은 백엔드/AI 서버 양쪽 모두 자유 텍스트로 유지**
  - 기존 `category`/`styleTag`처럼 백엔드에 별도 enum·검증을 두지 않고 문자열 그대로 통과시킴
  - Android UI에서만 `Situation` enum으로 드롭다운 선택지를 고정

**빌드 검증**
- 백엔드: `./gradlew compileJava` — BUILD SUCCESSFUL
- Android: 캐시된 Gradle 8.10.2로 `compileDebugKotlin` — BUILD SUCCESSFUL
- AI 서버: 로컬 Python/Docker 미가동 상태라 실행 검증 불가 — 스키마 필드명이 Java DTO와 camelCase로 정확히 매칭되는지 코드 리뷰로만 확인
- 에뮬레이터 E2E(체형 설정 저장 → 상황 탭 추천받기)는 미실시 — 다음 작업으로 이월

**현재 전체 구현 상태**
- 인프라: PostgreSQL + Redis + AI 서버 + Nginx (EC2 Docker Compose)
- `user` 도메인: 완료 (체형/취향 프로필 포함)
- `clothes` 도메인: 완료
- `outfit` 도메인: 완료 (옷장 기반 + 상황 기반 추천)
- AI 서버 (FastAPI): 완료 (실행 검증 미완)
- 프론트엔드 (React): 완료 (웹 버전, 미사용, 체형/상황 기반 기능 미반영)
- Android 앱 (Kotlin + Jetpack Compose): 완료
- **체형/취향 프로필 설정**: **코드 완료, 에뮬레이터 E2E 미실시**
- **옷장 없는 상황 기반 AI 추천**: **코드 완료, 에뮬레이터 E2E 미실시**

**다음에 할 작업**
1. AI 서버 기동 후 `/ai/outfits/recommend/situation` curl 테스트, 에뮬레이터에서 체형 설정 저장 + 상황 기반 추천 E2E 테스트
2. k6 설치 후 JWT 토큰 준비 → 성능 측정 실행 및 결과 정리
3. 플레이스토어 등록
4. VTON 가상 피팅 (Phase 2)

---

### 2026-07-23 (追加) — BASE_URL 빌드 타입 분리

**버그 수정 — 체형/취향 설정 저장이 항상 실패함**
- 원인: `android/app/build.gradle.kts`의 `BASE_URL`/`OAUTH2_BASE_URL`이 `defaultConfig`에 EC2 도메인(`fashion-app-jh.duckdns.org`)으로 고정되어 있어, 디버그 빌드(에뮬레이터)로 실행해도 항상 EC2 운영 서버로 요청이 나감 → 로컬에서 막 추가한 체형/취향 저장 API가 EC2에는 아직 배포되지 않아 저장이 실패
- 수정: `buildTypes.debug`/`buildTypes.release`에 각각 `buildConfigField`를 분리
  - debug: `BASE_URL = http://10.0.2.2:8080/api/`, `OAUTH2_BASE_URL = http://localhost:8080` (OAuth2 redirect_uri는 Google/Kakao 콘솔에 IP 등록이 안 되므로 localhost 유지 — 에뮬레이터에서 `adb reverse tcp:8080 tcp:8080` 필요, 2026-06-12 항목과 동일 제약)
  - release: 기존 EC2 duckdns 도메인 그대로 유지
- `ApiClient.kt`/`LoginScreen.kt`는 이미 `BuildConfig.BASE_URL`/`BuildConfig.OAUTH2_BASE_URL`을 참조하고 있어 코드 변경 없이 반영됨
- ⚠️ `BuildConfig` 필드는 빌드 시점에 고정되므로, 에뮬레이터에서 로컬 백엔드로 테스트하려면 디버그 APK를 다시 빌드/재설치해야 함 (Hot reload로는 반영 안 됨)

**빌드 검증**
- `gradle compileDebugKotlin compileReleaseKotlin` — 둘 다 BUILD SUCCESSFUL

---

### 2026-07-23 (追加 2) — 날씨 재시도 버튼 + AI 서버 배포 갭 수정

**버그 수정 — 날씨 로딩 실패 시 영구적으로 복구 불가능**
- 증상: 추천 화면에 "날씨 정보를 불러올 수 없습니다." 표시, 옷장 기반/상황 기반 두 탭의 "추천받기" 버튼이 계속 비활성화
- 원인: `RecommendViewModel`이 `init` 블록에서 날씨를 딱 한 번만 요청함. Compose Navigation이 하단 탭 전환 시에도 `추천` 화면의 ViewModel을 그대로 유지하기 때문에, 한 번 실패하면(예: 재빌드 직후 로컬 서버가 아직 안 떴을 때) 앱을 완전히 종료했다 켜기 전까지는 재시도할 방법이 없었음
- 확인: 실제 JWT로 `curl http://localhost:8080/api/weather` 직접 호출 시 정상 200 응답 — 서버 자체는 문제 없었음, 클라이언트에 재시도 수단이 없는 게 문제였음
- 수정: `RecommendViewModel.loadWeatherAndClothes()`를 공개 함수 `loadWeather()`로 분리해 재호출 가능하게 만들고, `RecommendScreen`의 날씨 카드 실패 상태에 "다시 시도" 버튼 추가
  - 파일: `android/app/src/main/java/com/fashionapp/ui/recommend/RecommendViewModel.kt`, `RecommendScreen.kt`

**버그 수정 — 상황 기반 추천 호출 시 404 Not Found**
- 원인: `docker-compose.yml`의 `ai-server` 서비스는 볼륨 마운트 없이 `build: ./ai-server`로 이미지를 굽는 방식이라, 오늘 추가한 `POST /ai/outfits/recommend/situation` 라우트가 소스 코드에는 있어도 **4주 전에 빌드된 컨테이너 이미지**에는 반영되어 있지 않았음 (2026-06-21 항목과 동일한 유형의 재발 이슈)
- 수정: `docker compose build ai-server && docker compose up -d ai-server`로 이미지 재빌드 및 컨테이너 재생성
- 겸사겸사 로컬 백엔드(포트 8080)도 콘솔 로그를 볼 수 없는 별도 프로세스로 떠 있던 것을 내려받고 `./gradlew bootRun`으로 다시 띄워 로그를 파일로 확보 → 디버깅에 사용
- **검증**: 실제 JWT로 전체 체인을 직접 호출해 확인
  - `POST http://localhost:8000/ai/outfits/recommend/situation` (AI 서버 단독) → 200, GPT-4o 실제 추천 결과 반환
  - `POST http://localhost:8080/api/outfits/recommend/situation` (Spring Boot → AI 서버) → 200, 저장된 체형/취향 프로필 반영된 추천 결과 반환
- ⚠️ **재발 방지 메모**: `ai-server`는 코드만 고쳐서는 반영되지 않음. FastAPI 코드를 수정할 때마다 반드시 `docker compose build ai-server && docker compose up -d ai-server`로 재배포할 것 (dev 편의를 위해 볼륨 마운트 + `--reload`로 바꾸는 것도 고려해볼 만함, 아직 미적용)

**남은 검증**
- 에뮬레이터에서 실제 버튼 탭으로 조작하는 UI E2E는 이번에도 진행 못함 (이 환경에서 `adb shell input tap`이 앱에 입력을 전달하지 못함 — FAB 버튼 탭도 반응 없음, 툴링 한계로 판단). curl로 서버 체인은 확인했으니, 에뮬레이터에서 실제 탭으로 "다시 시도" 버튼과 상황 기반 추천이 되는지는 사용자 확인 필요

**현재 전체 구현 상태 (업데이트)**
- **체형/취향 프로필 설정**: 코드 완료 + 저장 API 실동작 확인(로그로 height/weight/bodyType/preferredStyle 저장 확인됨)
- **옷장 없는 상황 기반 AI 추천**: 코드 완료 + curl로 서버 체인 E2E 확인 완료, 에뮬레이터 탭 조작 확인은 미완
- AI 서버: 로컬 Docker Compose로 재빌드 후 정상 기동 중

**다음에 할 작업**
1. 에뮬레이터에서 직접 "다시 시도"/상황 기반 추천받기 버튼 탭하여 눈으로 확인
2. (선택) `ai-server`에 볼륨 마운트 + `--reload` 적용해 코드 수정 시 재빌드 없이 반영되도록 개선
3. k6 설치 후 JWT 토큰 준비 → 성능 측정 실행 및 결과 정리
4. 플레이스토어 등록
5. VTON 가상 피팅 (Phase 2)

---

### 2026-07-26 — 서비스 방향 전면 피봇 결정

**결정 내용**
- "개인 옷장 관리 + 가상 피팅" 방향에서 **"패션에 무지한 사람들을 위한 AI 스타일리스트"** 방향으로 전환
- 옷장 등록/관리(`clothes` 도메인)와 코디 캘린더는 실사용 시나리오상 진입장벽이 높다고 판단해 제거 결정
  (사용자가 자기 옷을 일일이 사진 찍어 등록해야 추천을 받을 수 있는 구조 자체가 목표 타겟인 "패션 무지" 사용자에게 부담)
- 신규 핵심 기능 3가지로 재편: **오늘의 코디 추천**(날씨+상황+체형 → 텍스트 추천 + 네이버쇼핑 링크),
  **내 옷 진단**(사진 → AI 점수/피드백), **쇼핑 도우미**(예산+상황 → 실제 구매 가능 상품 추천)
- 유지: 소셜 로그인, 체형/취향 프로필, 날씨 API, Spring Boot 백엔드 구조, EC2/Docker/CI-CD 인프라
  → 기존에 만들어둔 "옷장 없는 상황 기반 추천"(`/api/outfits/recommend/situation`, 2026-07-23 추가)이
  새 핵심 기능 1번의 뼈대와 거의 동일해 가장 먼저 살릴 수 있는 자산으로 확인

**CLAUDE.md 갱신**
- 프로젝트 개요/기술 스택/아키텍처/ERD/프로젝트 구조/API 명세/화면 구성/로드맵 섹션 전체를
  새 방향에 맞게 재작성 (기술 스택에 네이버쇼핑 검색 API 추가)
- 개발 로드맵을 Phase 0(기존 자산 정리) → Phase 1(오늘의 코디 고도화, 기존 코드 재사용) →
  Phase 2(내 옷 진단 신규) → Phase 3(쇼핑 도우미 신규) → Phase 4(Android 통합+배포) 순으로 재설계
  — 재사용 가능한 것부터 먼저 완성해 리스크를 낮추는 순서로 배치
- 과거 개발 일지(2026-05-26 ~ 2026-07-23)는 피봇 이전 기록이므로 그대로 보존 (당시엔 유효했던 결정이었음)

**아직 코드에는 반영 안 됨** — 이번 세션은 문서(CLAUDE.md)와 개발 순서 계획까지만 진행, 실제 도메인 삭제/신규 구현은 다음 세션부터 Phase 0부터 순서대로 진행 예정

**현재 전체 구현 상태**
- 방향 전환 반영한 코드 변경: 없음 (다음 작업)
- 기존 코드베이스(옷장/캘린더 포함)는 그대로 살아있는 상태

**다음에 할 작업**
1. Phase 0 — `clothes` 도메인 삭제, `outfit` 도메인에서 캘린더/옷장기반 추천 제거, Android `wardrobe`/`calendar` 화면 삭제, DB 테이블 정리
2. Phase 1 — 네이버쇼핑 API 키 발급 + 오늘의 코디 추천에 상품 링크 연동
3. Phase 2 — 내 옷 진단 신규 구현
4. Phase 3 — 쇼핑 도우미 신규 구현

---

### 2026-07-26 (追加) — Phase 0: 옷장/캘린더 코드 정리

**완료한 작업**

- **백엔드 — `clothes` 도메인 삭제, `outfit` 도메인을 상황 기반 추천만 남도록 정리**
  - `domain/clothes/` 디렉터리 전체 삭제 (`Clothes`, `ClothesController/Service/Repository`, `ClothesResponse`, `ClothesUpdateRequest`)
  - `domain/outfit/`에서 캘린더·옷장기반 추천·코디 CRUD 관련 파일 삭제: `CalendarController`, `Outfit`, `OutfitItem`, `OutfitCalendar`, `OutfitCreateRequest`, `OutfitResponse`, `OutfitRepository`, `OutfitCalendarRepository`, `OutfitCalendarCreateRequest`, `OutfitCalendarResponse`, `OutfitRecommendRequest`
  - `OutfitController`/`OutfitService`를 `POST /api/outfits/recommend/situation` 하나만 남도록 재작성 (엔드포인트 경로는 이번엔 유지 — 리네이밍은 Phase 1에서 네이버쇼핑 연동과 함께 검토)
  - `infra/AiServerClient`에서 `recommendOutfits`(옷장 기반), `classifyClothes` 메서드 제거, 이제 `getWeather`/`recommendOutfitsBySituation`만 존재
  - `infra/AiRecommendRequest`, `AiRecommendResponse`, `AiClassifyResponse` DTO 삭제
  - `ErrorCode`에서 `CLOTHES_NOT_FOUND`, `OUTFIT_NOT_FOUND`, `CALENDAR_NOT_FOUND` 제거 (`INVALID_IMAGE_FORMAT`/`IMAGE_UPLOAD_FAILED`는 Phase 2 옷 진단 사진 업로드에 재사용 예정이라 유지)
  - `S3Uploader`는 그대로 유지 (Phase 2에서 진단 사진 임시 저장용으로 재사용 예정, dirName 파라미터라 clothes에 종속적이지 않음)
  - `./gradlew compileJava` — BUILD SUCCESSFUL

- **AI 서버(FastAPI) — 옷 분류·옷장 기반 추천 라우트 제거**
  - `routers/classify.py`, `schemas/clothes.py`, `services/openai_service.py` 삭제 (Spring Boot 쪽 호출부가 사라지며 완전히 죽은 코드가 됨 — 내 옷 진단은 Phase 2에서 새 프롬프트/스키마로 별도 작성 예정이라 재사용하지 않고 삭제)
  - `main.py`에서 `classify` 라우터 등록 제거
  - `routers/recommend.py`, `schemas/outfit.py`, `services/recommend_service.py`에서 옷장 기반 추천(`RecommendRequest/Response`, `ClothesItem`, `recommend_outfits`, `/ai/outfits/recommend`) 제거, 상황 기반 추천만 존속
  - ⚠️ 로컬에 Python/Docker Desktop이 꺼져 있어 실행 검증은 못함 (2026-07-13 항목과 동일한 제약) — 코드 리뷰 + import 정합성 확인으로 대체. **다음 실행 시 반드시 `docker compose build ai-server && docker compose up -d ai-server`로 재빌드할 것** (2026-06-21, 2026-07-23 항목과 동일한 재발 이슈 방지)

- **Android — `wardrobe`/`calendar` 화면 및 관련 코드 삭제**
  - `ui/wardrobe/`, `ui/calendar/` 디렉터리, `data/api/ClothesApi.kt`, `data/repository/ClothesRepository.kt` 삭제
  - `data/model/Models.kt`에서 `Clothes`, `Outfit`, `OutfitItem`, `OutfitCreateRequest`, `OutfitCalendar`, `CalendarCreateRequest`, `RecommendRequest/Response`, `RecommendedOutfit` 제거 (상황 기반 모델만 존속)
  - `OutfitApi`/`OutfitRepository`를 `recommendBySituation`/`getWeather`만 남도록 축소, `AppModule`에서 `provideClothesApi` 제거
  - `RecommendScreen`/`RecommendViewModel`에서 "옷장 기반"/"상황 기반" 탭 구조를 없애고 상황 기반 추천을 화면의 유일한 플로우로 변경 (탭 UI, `WardrobeRecommendTab`, `RecommendCard` 등 제거)
  - `BottomNavBar`를 "추천"/"마이" 2개 탭으로 축소 (옷 진단/쇼핑 탭은 각 기능 구현 시 Phase 2·3에서 추가 예정)
  - `gradle compileDebugKotlin` (캐시된 8.10.2) — BUILD SUCCESSFUL, 기존 deprecation 경고 2건 외 이상 없음

**트러블슈팅 — Navigation.kt / AppNavigation.kt 파일 역할 착각**
- 실제로는 `Navigation.kt`에 `Route` object와 `AppNavigation()` 컴포저블이 있고, `AppNavigation.kt`에 `MainViewModel`/`NavEvent`가 있는 구조 (파일명과 내용이 서로 반대)
- 처음에 이를 반대로 착각해 `AppNavigation.kt`를 Route 정리 내용으로 덮어써서 `MainViewModel`/`NavEvent` 클래스가 순간적으로 사라짐 → git이 `AppNavigation.kt`만 modified로 표시하고 `Navigation.kt`는 그대로인 것을 보고 발견
- `git checkout -- AppNavigation.kt`로 원본 복구 후, 실제 Route/AppNavigation 내용을 담고 있던 `Navigation.kt` 쪽에 옷장/캘린더 제거 편집을 다시 적용
- ⚠️ **재발 방지 메모**: 이 프로젝트의 `ui/navigation/`은 `Navigation.kt` = Route + NavHost, `AppNavigation.kt` = MainViewModel + NavEvent 로 파일명과 내용이 직관과 반대로 매칭되어 있음. 다음에 이 폴더를 건드릴 땐 파일명만 보고 넘겨짚지 말고 반드시 내용을 먼저 확인할 것

**DB 마이그레이션 (로컬 실행 완료)**
- `backend/src/main/resources/db/phase0_cleanup.sql` 작성 — `outfit_calendar`, `outfit_items`, `outfits`, `clothes` 테이블 DROP
- `ddl-auto: update`는 테이블을 자동으로 지우지 않으므로 수동 실행 필요. 로컬 DB는 `docker-compose.yml`에 없는 별도 컨테이너 `fashionapp-db`(postgres:16)로 떠 있었음 — Docker Desktop이 꺼져 있어 먼저 기동 후 `docker start fashionapp-db`로 컨테이너 기동
- 실행 전 각 테이블 row count 확인 (`clothes`=1, `outfits`=1, `outfit_items`=1, `outfit_calendar`=0 — 전부 이전 E2E 테스트용 더미 데이터였고 실사용자 데이터 없음을 확인 후 진행)
- `docker cp` + `docker exec ... psql -f`로 스크립트 실행 → 4개 테이블 모두 `DROP TABLE` 성공, `\dt` 결과 `users` 테이블 하나만 남은 것 확인
- ⚠️ **EC2 운영 DB에는 아직 미실행** — 배포 시(Phase 4) EC2 DB 컨테이너에도 동일 스크립트 적용 필요

**현재 전체 구현 상태**
- 백엔드: `clothes` 도메인 삭제 완료, `outfit` 도메인은 상황 기반 추천만 존속, 컴파일 성공
- AI 서버: 옷 분류/옷장기반 추천 라우트 삭제 완료 (실행 검증은 다음 Docker 기동 시 필요)
- Android: `wardrobe`/`calendar` 화면 삭제, 하단 탭 2개로 축소, 컴파일 성공
- DB: **로컬 DB 테이블 정리 완료** (`users`만 존속), EC2는 미반영

**다음에 할 작업**
1. AI 서버 Docker 재빌드 후 `/ai/outfits/recommend/situation` 동작 재확인 (라우터 정리 후 첫 실행 검증)
2. Phase 1 — 네이버쇼핑 API 키 발급 + 오늘의 코디 추천에 상품 링크 연동
3. EC2 배포 시점에 `phase0_cleanup.sql`을 운영 DB에도 적용

---

### 2026-07-26 (追加 3) — 쇼핑 연동 방식 확정: 네이버쇼핑 API → AI 텍스트 추천

**결정 내용**
- 네이버쇼핑 API 연동 계획을 폐기. 오늘의 코디 추천(Phase 1)과 쇼핑 도우미(Phase 3) 둘 다
  실제 상품 검색/링크 없이 **AI(OpenAI GPT-4o, 기존 사용 중이던 모델 그대로 유지)가
  "무신사/지그재그 등에서 ○○ 검색해보세요" 형태의 텍스트를 직접 생성**하는 방식으로 통일
- 사용자 확인 결과: (1) Phase 1도 Phase 3와 동일한 AI 텍스트 방식으로 통일, (2) LLM은 기존 OpenAI GPT-4o 유지 (Anthropic Claude API로 전환하지 않음 — "Claude AI가 추천"은 AI가 직접 텍스트를 생성한다는 의미였음, 실제 모델 교체 요청이 아니었음)
- 이유: 네이버쇼핑 API 키 발급·연동 부담 없이 기존 OpenAI 프롬프트 엔지니어링만으로 두 기능 모두 구현 가능. 실제 상품 링크가 아니므로 재고/가격 정합성을 신경 쓸 필요도 없음
- CLAUDE.md의 기술 스택/아키텍처/ERD/프로젝트 구조/API 명세/화면 구성/로드맵에서 네이버쇼핑 관련 서술 전부 제거·수정 (과거 개발 일지 항목은 당시 결정을 그대로 보존)

**다음에 할 작업**
1. Phase 1 구현 — AI 서버 상황 기반 추천 프롬프트에 쇼핑몰 검색 제안 필드 추가, 백엔드/Android DTO·화면 반영
2. Phase 2 — 내 옷 진단 신규 구현
3. Phase 3 — 쇼핑 도우미 신규 구현 (AI 텍스트 방식)

---

### 2026-07-26 (追加 4) — Phase 1: 오늘의 코디 추천에 쇼핑몰 검색 제안 추가

**완료한 작업**

- **AI 서버 — 상황 기반 추천 프롬프트에 쇼핑 검색 제안 필드 추가**
  - `schemas/outfit.py`에 `ShoppingSuggestion`(item/site/search_keyword) 추가, `SituationOutfitSuggestion`에 `shopping_suggestions: List[ShoppingSuggestion]` 필드 추가 (camelCase alias 자동 적용)
  - `services/recommend_service.py`의 `_SITUATION_PROMPT_TEMPLATE`에 규칙 추가: description에 포함된 아이템 중 2~3개만 골라 무신사/지그재그/에이블리/W컨셉 중 어울리는 곳 + 검색 키워드 제안, 특별한 이유가 없으면 무신사·지그재그 우선
  - `recommend_outfit_by_situation` 파싱 로직에서 `shopping_suggestions` 배열을 `ShoppingSuggestion` 리스트로 변환

- **백엔드 — DTO에 쇼핑 제안 필드 추가**
  - `infra/AiSituationRecommendResponse.SituationOutfitSuggestion`에 `shoppingSuggestions: List<ShoppingSuggestion>` 필드, `ShoppingSuggestion`(item/site/searchKeyword) 중첩 클래스 추가
  - `OutfitController`/`OutfitService`는 그대로 통과시키는 구조라 별도 수정 불필요
  - `./gradlew compileJava` — BUILD SUCCESSFUL

- **Android — 모델·화면에 쇼핑 제안 반영**
  - `data/model/Models.kt`에 `ShoppingSuggestion(item, site, searchKeyword)` 추가, `SituationOutfitSuggestion`에 `shoppingSuggestions: List<ShoppingSuggestion> = emptyList()` 필드 추가
  - `RecommendScreen.kt`의 `SituationRecommendCard`에 "🛍️ {site}에서 '{searchKeyword}' 검색해보세요" 형태로 쇼핑 제안 표시 (있을 때만 노출)
  - `compileDebugKotlin` — BUILD SUCCESSFUL

- **실행 검증**
  - Docker Desktop 기동 → `docker compose build ai-server` (재빌드 필수, 2026-06-21/07-23 항목과 동일 이유) → `docker compose up -d ai-server`
  - `POST http://localhost:8000/ai/outfits/recommend/situation` 실제 호출로 GPT-4o가 `shoppingSuggestions`(예: `{"item":"화이트 슬림핏 셔츠","site":"무신사","searchKeyword":"남성 화이트 셔츠 슬림핏"}`)를 정상 생성하는 것 확인
  - `./gradlew bootRun`으로 로컬 Spring Boot 기동 확인(정상 기동, DB 연결 성공) — 단, JWT 발급에는 에뮬레이터 로그인이 필요해 Spring Boot → AI 서버 전체 체인은 이번엔 직접 호출하지 못함. AI 서버 응답 필드명이 백엔드 DTO와 정확히 일치하고 `OutfitController`/`OutfitService`가 값을 그대로 통과시키는 구조라 코드 리뷰로 정합성 확인, 검증 후 백엔드 프로세스는 종료

**현재 전체 구현 상태**
- Phase 1 (오늘의 코디 추천 + 쇼핑몰 검색 제안): **완료** — AI 서버 실제 응답으로 검증, 백엔드/Android 컴파일 성공
- Phase 2 (내 옷 진단), Phase 3 (쇼핑 도우미): 미착수

**다음에 할 작업**
1. 에뮬레이터에서 실제 로그인 후 "오늘의 코디" 화면에서 쇼핑 제안 카드가 보이는지 눈으로 확인 (Spring Boot 경유 전체 체인 E2E)
2. Phase 2 — 내 옷 진단 신규 구현 (백엔드 `diagnosis` 도메인, AI 서버 진단 라우터, Android 화면)
3. Phase 3 — 쇼핑 도우미 신규 구현 (AI 텍스트 방식, 예산/상황/체형 → 아이템 추천 + 쇼핑몰 검색 제안)

---

### 2026-07-26 (追加 5) — Phase 2: 내 옷 진단 신규 구현

**완료한 작업**

- **백엔드 — `diagnosis` 도메인 신설**
  - `StyleDiagnosis` 엔티티(`style_diagnoses` 테이블: image_url/score/feedback/similar_styles/created_at), `StyleDiagnosisRepository`
  - `SimilarStyleSuggestion`(styleTag/description), `StyleDiagnosisResponse` DTO
  - `DiagnosisController` — `POST/GET /api/diagnosis`, `GET /api/diagnosis/{id}`
  - `DiagnosisService` — S3 업로드(`S3Uploader` 재사용) → AI 서버 진단 호출 → `similarStyles`는 Jackson `ObjectMapper`로 JSON 직렬화해 TEXT 컬럼에 저장, 조회 시 역직렬화해 응답
  - `infra/AiDiagnosisResponse` 추가, `AiServerClient.diagnoseOutfit()` 추가 (기존에 지웠던 멀티파트 업로드 패턴을 진단용으로 재사용)
  - `ErrorCode.DIAGNOSIS_NOT_FOUND` 추가
  - `./gradlew compileJava` — BUILD SUCCESSFUL

- **AI 서버 — 진단 라우터/서비스 신규 작성**
  - `schemas/diagnosis.py`(`SimilarStyleSuggestion`, `DiagnosisResponse`), `services/diagnosis_service.py`(OpenAI Vision 호출, 새 진단 전용 프롬프트 — Phase 0에서 지운 옛 옷 분류용 `openai_service.py`를 재사용하지 않고 새로 작성), `routers/diagnosis.py`(`POST /ai/diagnosis`, 이미지 형식/크기 검증은 기존 classify 라우터와 동일한 방식)
  - 프롬프트에 "사진에 옷이 명확히 안 보이면 낮은 점수 + 이유 설명" 규칙 명시 — 2026-06-21에 겪었던 "OpenAI가 스크린샷을 거부해 빈 응답 반환" 문제의 재발 방지
  - `main.py`에 `diagnosis` 라우터 등록

- **Android — 진단 화면 신규 구현**
  - `data/model/Models.kt`에 `SimilarStyleSuggestion`, `StyleDiagnosis` 추가
  - `DiagnosisApi`/`DiagnosisRepository`/`AppModule` 추가
  - `DiagnosisViewModel` — 갤러리에서 고른 이미지의 실제 MIME 타입을 `contentResolver.getType()`으로 읽어 멀티파트 전송 (2026-06-21 옷 업로드 400 오류와 동일한 함정 재사용 방지)
  - `DiagnosisScreen` — 사진 선택 버튼 → 점수 배지(원형) + 피드백 + 비슷한 스타일 카드 + 지난 진단 이력 리스트
  - `Navigation.kt`에 `Route.DIAGNOSIS` 추가, `BottomNavBar`에 "진단" 탭 추가(추천/진단/마이 3탭 구성)
  - `compileDebugKotlin` — BUILD SUCCESSFUL

- **실행 검증**
  - `docker compose build ai-server && docker compose up -d ai-server` 재빌드·재기동
  - `POST http://localhost:8000/ai/diagnosis`를 저장소 내 스크린샷 파일로 실제 호출 → `{"score":10,"feedback":"사진에 명확한 코디가 보이지 않아...","similarStyles":[...]}` 정상 반환 확인 (실제 옷 사진이 아니어도 스키마/파싱/graceful degradation 모두 정상 동작 확인, 실제 옷 사진으로 점수가 높게 나오는지는 미검증)

**현재 전체 구현 상태**
- Phase 0 (기존 자산 정리), Phase 1 (오늘의 코디 + 쇼핑 제안): 완료
- Phase 2 (내 옷 진단): **완료** — AI 서버 실제 응답 검증, 백엔드/Android 컴파일 성공. 에뮬레이터 실제 탭 조작 E2E와 실제 옷 사진 진단 결과는 미확인
- Phase 3 (쇼핑 도우미): 미착수 — 사용자 요청으로 이번 세션은 여기서 중단

**다음에 할 작업**
1. 에뮬레이터에서 실제 갤러리 사진으로 진단 버튼 탭 → 결과/이력 화면 눈으로 확인
2. Phase 3 — 쇼핑 도우미 신규 구현 (예산/상황/체형 → AI 텍스트 아이템 추천 + 쇼핑몰 검색 제안 + "이것만 사면 N가지 코디" 활용법)
3. EC2 배포 반영 시 `phase0_cleanup.sql` 적용 + `.env`는 그대로(네이버쇼핑 키 불필요, 신규 환경변수 없음)

---

### 2026-07-26 (追加 6) — Phase 3: 쇼핑 도우미 신규 구현

**완료한 작업**

- **AI 서버 — 쇼핑 추천 스키마/서비스/라우터 신규 작성**
  - `schemas/shopping.py` — `ShoppingRecommendRequest`(budget/situation/body_profile, `outfit.py`의 `BodyProfile` 재사용), `ShoppingItemSuggestion`(item/reason/estimated_price/site/search_keyword), `ShoppingRecommendResponse`(items/total_estimated_price/usage_tip)
  - `services/shopping_service.py` — 예산+상황+체형 프로필로 GPT-4o에 아이템 조합(2~5개, 예산 110% 이내) + 쇼핑몰 검색 제안 + "이것만 사면 N가지 코디" 활용법(`usage_tip`)을 생성하는 프롬프트. `recommend_service.py`의 `client`/`_parse_json`/`_BODY_TYPE_LABELS`/`_STYLE_LABELS`를 그대로 import해 재사용 (동일 라벨/파싱 로직 중복 방지)
  - `routers/shopping.py` — `POST /ai/shopping/recommend`, `main.py`에 라우터 등록

- **백엔드 — `shopping` 도메인 신설**
  - `domain/shopping/ShoppingController`(`POST /api/shopping/recommend`), `ShoppingService`(로그인 사용자의 체형/취향 프로필 조회 후 AI 서버 호출), `ShoppingRecommendRequest`(budget/situation, situation 기존 패턴대로 자유 텍스트)
  - `infra/AiShoppingRecommendRequest`/`AiShoppingRecommendResponse` DTO 추가, `AiServerClient.recommendShopping()` 추가 (기존 `recommendOutfitsBySituation`과 동일한 JSON POST 패턴)
  - 별도 저장/이력 없음 (조회 전용, `outfit_recommendations`와 마찬가지로 실시간 생성이라 캐싱 안 함) — 신규 `ErrorCode` 불필요, 기존 `AI_SERVER_ERROR`/`USER_NOT_FOUND` 재사용
  - `./gradlew compileJava` — BUILD SUCCESSFUL

- **Android — 쇼핑 도우미 화면 신규 구현**
  - `data/model/Models.kt`에 `ShoppingRecommendRequest`(budget/situation), `ShoppingItemSuggestion`, `ShoppingRecommendResponse` 추가
  - `ShoppingApi`/`ShoppingRepository`/`AppModule.provideShoppingApi` 추가
  - `ShoppingViewModel` — 예산 입력(숫자만 필터링) + 상황 선택(기존 `Situation` enum 재사용) + 추천 결과 상태 관리
  - `ShoppingScreen` — 예산 입력창 + 상황 드롭다운 + 추천 버튼 + 총 예상 금액/활용법 카드 + 아이템별 카드(이름/가격/이유/쇼핑몰 검색 제안)
  - `Navigation.kt`에 `Route.SHOPPING` 추가, `BottomNavBar`에 "쇼핑" 탭 추가 (추천/진단/쇼핑/마이 4탭 구성)
  - `compileDebugKotlin` — BUILD SUCCESSFUL (기존과 동일한 `menuAnchor()` deprecation 경고 1건 외 이상 없음)

- **실행 검증**
  - `docker compose build ai-server && docker compose up -d ai-server` 재빌드·재기동 (2026-06-21 이후 반복된 재발 이슈 방지 원칙 그대로 적용)
  - `POST http://localhost:8000/ai/shopping/recommend`를 budget=150000, situation=WORK, bodyProfile 포함으로 실제 호출 → `items`(네이비 셔츠/치노 팬츠/화이트 스니커즈, 각 site+searchKeyword 포함) + `totalEstimatedPrice: 140000` + `usageTip`("총 3가지 코디가 가능해요") 정상 반환 확인, 컨테이너 로그에 200 OK만 기록됨
  - Spring Boot 경유 전체 체인(에뮬레이터 로그인 필요)과 에뮬레이터 실제 탭 조작은 이번에도 미실시 — AI 서버 응답 필드명이 백엔드 DTO(`AiShoppingRecommendResponse`)와 정확히 일치하고 컨트롤러/서비스가 값을 그대로 통과시키는 구조라 코드 리뷰로 정합성 확인

**현재 전체 구현 상태**
- Phase 0~3 (기존 자산 정리 / 오늘의 코디+쇼핑 제안 / 내 옷 진단 / 쇼핑 도우미): **전부 완료** — AI 서버 실제 응답 검증, 백엔드/Android 컴파일 성공
- 남은 미검증 항목: 에뮬레이터 실제 탭 조작 E2E(진단·쇼핑 도우미 둘 다), Spring Boot 경유 전체 체인 curl 검증(쇼핑 도우미)

**다음에 할 작업**
1. Phase 4 — Android 하단 네비게이션은 이미 4탭으로 재구성 완료, 남은 건 전체 E2E 테스트 + k6 성능 측정 스크립트 갱신(엔드포인트 변경 반영) + EC2 운영 DB에 `phase0_cleanup.sql` 적용
2. 에뮬레이터에서 실제 탭 조작으로 진단/쇼핑 도우미 눈으로 확인
3. 플레이스토어 등록 준비

---

### 2026-07-27 — Phase 4: 에뮬레이터 E2E 완료, k6 스크립트 갱신, EC2 DB 정리 착수

**완료한 작업**

- **에뮬레이터 실제 탭 조작 E2E 완료**
  - 진단·쇼핑 도우미 화면 모두 사용자가 직접 에뮬레이터에서 눈으로 확인 완료 (이전까지 미해결이던 툴링 한계 항목 — 이번엔 사용자가 직접 수행)

- **k6 성능 스크립트 갱신** (`performance/`) — Phase 0~3 피봇으로 삭제된 엔드포인트 반영 안 된 상태였음
  - `k6-latency.js`: 삭제된 `GET /api/clothes`, `/api/outfits`, `/api/calendar` 제거 → `GET /api/users/me`, `GET /api/weather`, `GET /api/diagnosis`(이력)로 교체. `options`에 연결되지 않아 실제로는 실행되지 않던 죽은 코드 `recommendTest()` 함수 제거
  - `k6-load.js`: 동일하게 DB 읽기 대상 엔드포인트 목록을 `/users/me`, `/weather`, `/diagnosis`로 교체
  - `k6-ai.js`: `/api/weather`가 더 이상 받지 않는 `nx`/`ny` 쿼리 파라미터 제거, 옷장 기반 `POST /outfits/recommend` → 실제 존재하는 `POST /outfits/recommend/situation`(body: temperature/condition/situation)으로 교체, `POST /shopping/recommend` 측정 신규 추가. 응답이 `ApiResponse` 래퍼(`{success, data, message}`) 구조인 것을 `WeatherController`/`ApiResponse.java` 코드 확인 후 `body.data?.temperature`로 반영
  - 코디 진단(`POST /api/diagnosis`)은 이미지 멀티파트 업로드가 필요해 세 스크립트 모두 측정 대상에서 제외 (주석으로 명시), `GET /api/diagnosis`(이력 조회)만 DB 읽기 테스트에 포함
  - ⚠️ 아직 미실행 — 실제 JWT 토큰으로 `k6 run` 필요

- **EC2 운영 DB `phase0_cleanup.sql` 적용 — 착수했으나 이 세션(Claude Code)에서는 직접 실행 불가로 사용자에게 인계**
  - `~/.ssh/known_hosts`에 `3.35.9.48`(EC2 퍼블릭 IP, `fashion-app-jh.duckdns.org`) 접속 이력 확인, `Downloads/fashion-app-key.pem` 키 확인
  - `ssh ubuntu@3.35.9.48`, `curl http://fashion-app-jh.duckdns.org` 모두 타임아웃 — 이 Claude Code 세션의 네트워크 환경(샌드박스 해제 후에도 동일)에서는 EC2로 아웃바운드 연결 자체가 안 되는 것으로 판단, EC2/보안그룹 자체 문제인지는 미확인
  - 사용자가 직접 SSH 접속해서 진행하기로 결정 — 절차 안내: (1) `docker ps`로 postgres 컨테이너명 확인 (2) `clothes`/`outfits`/`outfit_items`/`outfit_calendar` row count 확인 (3) `pg_dump`로 해당 4개 테이블 백업 (4) `git pull` 후 `docker cp`+`psql -f`로 `phase0_cleanup.sql` 실행 (5) `\dt`로 `users` 테이블만 남았는지 확인
  - ⚠️ **아직 미완료** — row count 확인 결과와 스크립트 실행 결과 모두 사용자로부터 회신 대기 중

**현재 전체 구현 상태**
- Phase 0~3: 전부 완료, 에뮬레이터 탭 조작 E2E까지 이번에 완료
- k6 성능 측정 스크립트: 엔드포인트 갱신 완료, 실행은 아직 (JWT 토큰 필요)
- EC2 운영 DB `phase0_cleanup.sql`: **미완료** — SSH가 이 세션 환경에서 안 열려 사용자에게 절차 인계, 결과 대기 중

**다음에 할 작업**
1. 사용자가 EC2에 직접 SSH 접속해 `phase0_cleanup.sql` 백업+적용 결과 회신 → row count/적용 결과 확인 후 이 문서에 반영
2. k6 스크립트 실제 JWT 토큰으로 실행 (`k6-latency.js` → `k6-ai.js` → `k6-load.js` 순), 결과 정리
3. 플레이스토어 등록 준비

---

### 2026-07-27 (追加) — k6 성능 측정 실행 결과

**완료한 작업**

- **k6 성능 측정 3종 실제 JWT 토큰으로 실행 완료**
  - `k6-latency.js` — DB 읽기 API(`/users/me`, `/diagnosis` 등) 평균 응답시간 **18ms**, 날씨 API(`/weather`) 평균 응답시간 **235ms**
  - `k6-ai.js` — AI 코디 추천(`POST /outfits/recommend/situation`, GPT-4o-mini) 평균 응답시간 **5.6s**, AI 쇼핑 추천(`POST /shopping/recommend`) 평균 응답시간 **4.4s**
  - `k6-load.js` — 동시 사용자 50명 부하 테스트: 평균 응답시간 **93ms**, 처리량 **43.8 req/s**, 오류율 **0%**, 5분간 총 **13,148회** 요청 처리
  - 결과를 `README.md`의 "성능 측정" 섹션에도 반영

> ⚠️ AI 추천 엔드포인트는 GPT-4o가 아니라 **GPT-4o-mini**로 측정됨 — `recommend_service.py`/`shopping_service.py`에 사용 중인 실제 모델명 확인 필요 (기술 스택 문서상 GPT-4o로 기재된 부분과 실제 코드의 모델 설정이 일치하는지는 이번 세션에서 코드로 재확인하지 않음, 다음 작업으로 이월)

**현재 전체 구현 상태**
- k6 성능 측정: **실행 완료**, 결과 CLAUDE.md/README.md 반영 완료
- EC2 운영 DB `phase0_cleanup.sql`: 여전히 미완료 — 사용자 회신 대기 중

**다음에 할 작업**
1. `ai-server`에서 실제 사용 중인 모델명이 GPT-4o인지 GPT-4o-mini인지 코드로 재확인 후 CLAUDE.md 기술 스택 서술과 일치시키기
2. 사용자가 EC2에 직접 SSH 접속해 `phase0_cleanup.sql` 백업+적용 결과 회신 → row count/적용 결과 확인 후 이 문서에 반영
3. 플레이스토어 등록 준비

---

### 2026-07-27 (追加 2) — 텍스트 추천 모델 GPT-4o-mini로 통일

**완료한 작업**

- 코드 확인 결과 `recommend_service.py`/`shopping_service.py`/`diagnosis_service.py` 모두 `model="gpt-4o"`로 하드코딩되어 있었음 (k6 결과에 "(GPT-4o-mini)"로 표기했던 건 실제 코드와 불일치 — 사용자 확인 후 "코드를 gpt-4o-mini로 맞춘다"로 결정)
- `services/recommend_service.py`, `services/shopping_service.py`의 `model="gpt-4o"` → `"gpt-4o-mini"`로 변경 (오늘의 코디 추천 · 쇼핑 도우미, 텍스트 생성 전용)
- `diagnosis_service.py`(코디 사진 진단, OpenAI Vision)는 이번 변경 대상에서 제외 — k6로 측정한 대상이 아니었고, 기술 스택 문서에서도 Vision API와 텍스트 추천 모델을 원래부터 별도 항목으로 구분해왔음
- CLAUDE.md 기술 스택 표, 코드 작성 규칙에서 텍스트 추천/쇼핑 제안 관련 "GPT-4o" 표기를 "GPT-4o-mini"로 정정 (진단용 Vision API 표기는 `gpt-4o` 그대로 유지)

> ⚠️ 코드/문서 미실행 검증: 로컬 Docker AI 서버 재빌드·재기동 및 실제 호출 테스트는 이번 세션에서 진행하지 않음 — 다음 실행 시 `docker compose build ai-server && docker compose up -d ai-server` 필요 (2026-06-21 이후 반복된 재발 이슈와 동일한 절차)

**현재 전체 구현 상태**
- 텍스트 추천(오늘의 코디/쇼핑 도우미) 모델: 코드 `gpt-4o-mini`로 변경 완료, Docker 재빌드 후 실행 검증은 아직
- 코디 사진 진단 모델: `gpt-4o`(Vision) 그대로 유지

**다음에 할 작업**
1. `docker compose build ai-server && docker compose up -d ai-server`로 재배포 후 `/ai/outfits/recommend/situation`, `/ai/shopping/recommend` 실제 호출로 gpt-4o-mini 정상 동작 확인
2. 사용자가 EC2에 직접 SSH 접속해 `phase0_cleanup.sql` 백업+적용 결과 회신 → row count/적용 결과 확인 후 이 문서에 반영
3. 플레이스토어 등록 준비

---

### 2026-07-28 — 백엔드 성능/안정성 개선 (User 인덱스, AI 서버 비동기 호출, 2단계 캐싱, HikariCP, JVM 튜닝, 테스트 코드)

**완료한 작업**

- **User 엔티티 인덱스 추가**
  - `@Table(indexes = {...})`로 `idx_users_email`(email), `idx_users_provider_id`(provider_id) 추가 (`User.java`)
  - `ddl-auto: update`라 다음 서버 기동 시 자동 반영, 별도 마이그레이션 스크립트 불필요

- **AI 서버 호출 비동기화 + 코디/쇼핑 추천 병렬 호출용 신규 엔드포인트**
  - `AiServerClient.recommendOutfitsBySituation`/`recommendShopping`을 `CompletableFuture` 반환으로 전환, 전용 `Executor` 빈(`AsyncConfig`, 8-thread fixed pool)에서 실행
  - `OutfitService`/`ShoppingService`는 `.join()`으로 기존 동기 동작을 그대로 유지하되, `CompletionException`을 언랩해 내부 `CustomException`이 `GlobalExceptionHandler`에서 기존과 동일하게(예: `AI_SERVER_ERROR` → 503) 처리되도록 함
  - 기존엔 코디 추천과 쇼핑 추천을 동시에 호출하는 지점이 아예 없었음 — 신규 `domain/home`(`HomeController`/`HomeService`/`HomeSummaryRequest`/`HomeSummaryResponse`) 추가, `POST /api/home/summary`가 두 AI 호출을 `CompletableFuture.allOf`로 병렬 실행해 지연시간을 `outfit+shopping` 합산이 아니라 `max(outfit, shopping)`으로 단축
  - ⚠️ Android 쪽 홈 화면 연동은 이번엔 범위 밖 — 엔드포인트만 신설, 화면 설계/구현은 미착수

- **`k6-ai.js`의 `BASE_URL` 환경변수화**
  - `__ENV.BASE_URL`(기본값 `http://localhost:8080`)로 변경, EC2 측정 시엔 `-e BASE_URL=http://fashion-app-jh.duckdns.org`로 전환 가능 (`k6-latency.js`/`k6-load.js`는 이번 범위 밖이라 그대로 둠)

- **HikariCP 커넥션 풀 설정 (`application.yml`)**
  - t3.small(vCPU 2개, RAM 2GB)에 postgres/redis/ai-server/nginx가 함께 떠 있는 제약을 감안해 `maximum-pool-size: 10`, `minimum-idle: 5`, `connection-timeout: 30000`, `idle-timeout: 600000`, `max-lifetime: 1800000`으로 보수적으로 설정, 각 값 옆에 의미를 주석으로 명시

- **Caffeine 로컬 캐시 + Redis 2단계 날씨 캐싱 (신규 `WeatherService`)**
  - 1단계: Caffeine 로컬 캐시(`expireAfterWrite` 10분, `maximumSize(1)` — 날씨는 위치 구분 없는 앱 전체 공유 값 하나뿐이라)
  - 2단계: 기존 `RedisTemplate<String, String>` 빈 재사용, `DiagnosisService`와 동일하게 `ObjectMapper`로 JSON 직렬화/역직렬화, TTL 30분(로컬 10분보다 길게 잡아 재시작 직후에도 외부 API 재호출 없이 버티도록 함)
  - 3단계: 둘 다 미스일 때만 외부 API(`AiServerClient.getWeather()`) 호출 후 두 캐시 모두 채움
  - `WeatherController`가 `AiServerClient`를 직접 부르던 것에서 `WeatherService`를 거치도록 변경
  - `build.gradle`에 `com.github.ben-manes.caffeine:caffeine:3.1.8` 추가 (spring-boot-starter-cache/`@EnableCaching`은 쓰지 않음 — L1→L2→원본 순서의 수동 3단계 조회를 표현하기엔 `@Cacheable` 추상화보다 직접 구현이 더 명확했음)

- **JUnit5 + Mockito 테스트 코드 14건 신규 작성**
  - `WeatherServiceTest`(4) — 로컬 캐시 히트, 로컬 미스+Redis 히트, 둘 다 미스, Redis 값 손상 시 폴백
  - `OutfitServiceTest`(3), `ShoppingServiceTest`(3) — AI 서버 정상 응답 + 요청 페이로드 캡처 검증, 사용자 없음(`USER_NOT_FOUND`), `CompletionException` 언랩 검증(비동기 전환 회귀 방지용)
  - `UserControllerTest`(4) — `@WebMvcTest` + MockMvc, 실제 `GlobalExceptionHandler` 경유로 200/404/400 검증
  - ⚠️ **중요 발견**: `build.gradle`에 `tasks.named('test') { useJUnitPlatform() }`가 아예 없었음 — Gradle 기본 JUnit4 러너가 JUnit5(Jupiter) 테스트를 인식하지 못해 지금까지 작성된 적 있었다면 전부 "0건 통과"로 조용히 무시됐을 상황이었음 (실제로 스크래치 테스트로 재현·확인). 이번에 추가하고 나서야 14건이 실제로 실행/통과되는 것 확인
  - `UserControllerTest`는 실제 `SecurityConfig`(OAuth2/JWT 관련 빈 5개 추가로 필요)를 끌어오는 대신, `AuthenticationPrincipalArgumentResolver`만 최소로 등록하고 `SecurityContextHolder`에 인증 정보를 직접 주입하는 방식 사용 — 컨트롤러 로직/예외 매핑/JSON 응답은 검증하지만 실제 JWT 필터 체인이나 `@PreAuthorize` 강제 자체는 검증 범위 밖으로 의도적으로 좁힘

- **`docker-compose.prod.yml`에 Spring Boot 컨테이너 JVM 옵션 추가**
  - `backend/Dockerfile`의 `ENTRYPOINT`가 exec form(`["java","-jar","app.jar"]`)이라 셸 변수 확장이 안 되는 점을 고려해, JVM이 기동 시 직접 읽는 `JAVA_TOOL_OPTIONS` 환경변수로 전달(Dockerfile 수정 불필요)
  - t3.small 2GB를 다른 컨테이너들과 나눠 쓰는 전제로 `-Xms256m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k -XX:+ExitOnOutOfMemoryError` 설정
  - G1(기본값) 대신 SerialGC를 고른 이유: vCPU 2개뿐인 환경에서 G1의 백그라운드 GC 스레드가 애플리케이션과 CPU를 두고 경쟁하고, 힙이 512MB로 작아 G1의 리전 기반 최적화 이점도 크지 않기 때문
  - `docker compose config`로 문법 검증 완료 (단, 이 명령이 `.env` 실값을 그대로 풀어서 출력하길래 결과 로그는 바로 삭제)

- **`docker stats`로 실제 메모리 사용량 확인 시도 — 미완료**
  - 이 세션 환경에서 EC2(`fashion-app-jh.duckdns.org:22`) 아웃바운드 연결이 안 되는 것을 재확인 (2026-07-27 기록과 동일 증상, 여전히 유효함)
  - 로컬은 Spring Boot가 컨테이너가 아니라 `./gradlew bootRun`으로 직접 뜨는 구조라 `docker-compose.prod.yml`의 JVM 옵션과 애초에 무관 (`docker ps`로 로컬엔 `ai-server`/`redis`/`postgres`만 떠 있음을 확인)
  - 위 변경사항 전부 로컬 컴파일/테스트 통과 확인 후 커밋까지는 완료됨(아래 커밋 해시 참고) — **EC2 배포는 아직 안 된 상태**라 지금 접속되더라도 이전 설정(JVM 옵션 없음)만 보였을 것

**커밋 이력** (이번 세션 변경분, 세션 밖에서 사용자가 직접 커밋한 것으로 추정 — 대화 중 Claude가 커밋을 실행한 적은 없음)
- `739a009` feat: async AI server calls + index optimization (User 인덱스, AsyncConfig, AiServerClient, OutfitService/ShoppingService, home 도메인 신설, k6-ai.js — 이 커밋엔 이 세션과 무관한 Android/README/스크린샷 변경도 함께 포함되어 있었음)
- `464f992` feat: add Caffeine + Redis 2-tier weather cache
- `8a76b48` test: add JUnit5 unit tests for WeatherService, OutfitService, ShoppingService (UserControllerTest 포함)
- `d2d00e5` feat: add JVM tuning options for t3.small

**현재 전체 구현 상태**
- User 인덱스, AI 서버 비동기 호출 + 홈 요약 병렬 엔드포인트, HikariCP 설정, Caffeine+Redis 2단계 날씨 캐싱, 백엔드 단위/통합 테스트 14건, Spring Boot JVM 튜닝: **전부 로컬 컴파일/테스트 통과 + 커밋 완료**
- EC2 배포: **미완료** (다음 `git pull` + `docker compose -f docker-compose.prod.yml up -d --build` 필요)
- EC2 운영 DB `phase0_cleanup.sql` 적용: 여전히 미완료 (2026-07-27부터 이월, 사용자 회신 대기 중)
- 텍스트 추천 모델 `gpt-4o-mini` 전환 후 Docker 재빌드 실행 검증: 여전히 미완료 (2026-07-27 (追加 2)부터 이월)

**다음에 할 작업**
1. EC2에 배포 후 `docker stats`로 spring-boot 컨테이너 실제 메모리 사용량 확인, 필요시 `-Xmx` 재조정
2. `docker compose build ai-server && docker compose up -d ai-server`로 재배포 후 gpt-4o-mini 정상 동작 확인 (이월)
3. 사용자가 EC2에 직접 SSH 접속해 `phase0_cleanup.sql` 백업+적용 결과 회신 (이월)
4. `POST /api/home/summary`를 실제로 사용할 Android 홈 화면 설계/구현 여부 결정
5. 플레이스토어 등록 준비

---

### 2026-07-31 — README 최종 정리, EC2 DuckDNS 자동 갱신, GitHub Secrets 자동화 검토(보류)

**완료한 작업**

- **README.md 최종 정리** — 2026-07-28 백엔드 성능/안정성 작업 내용을 문서에 반영
  - **성능 최적화 섹션 신규 추가**: 인덱스 최적화(`EXPLAIN ANALYZE`), CompletableFuture 비동기 처리(응답시간 25~32% 개선), Caffeine+Redis 2단계 캐싱(97% 개선, 캐시 히트 시 7ms), HikariCP 튜닝, JVM 튜닝(`docker stats` 실측 281MB 안정)을 표로 정리
  - **테스트 섹션 신규 추가**: JUnit5+Mockito 14건(`WeatherServiceTest`/`OutfitServiceTest`/`ShoppingServiceTest`/`UserControllerTest`) 및 `useJUnitPlatform()` 누락 발견 사실 명시
  - **k6 성능 측정 결과 최신화**: 날씨 API 235ms→**7ms**(캐시 히트), AI 코디 추천 5.6s→**3.83s**, AI 쇼핑 추천 4.4s→**2.96s**로 교체, "캐싱/비동기 최적화 적용 후 측정"이라고 명시
  - 기술 스택 표에 캐싱(Caffeine+Redis), CompletableFuture, JUnit5/Mockito 행 추가
  - **부수적으로 발견해 같이 수정**: 개발 로드맵 섹션이 피봇 이전(React 프론트엔드, 옷장, 캘린더, EC2 미배포) 내용 그대로 남아 있어 README 다른 섹션과 모순되던 것을 확인 → 현재 상태(Phase 0~3 핵심기능 완료, Phase 4 성능개선 진행중, 잔여 TODO 2건) 기준으로 재작성. 요청 범위 밖이었으나 방치 시 문서 신뢰도가 떨어진다고 판단해 함께 정리

- **EC2 부팅 시 DuckDNS IP 자동 갱신 — `infra/duckdns/` 신규 작성**
  - `update-duckdns.sh` — `DUCKDNS_DOMAIN`/`DUCKDNS_TOKEN` 환경변수로 DuckDNS update API 호출
  - `duckdns-update.service` — systemd oneshot 유닛, `After=network-online.target`으로 매 부팅 시 1회 실행
  - `duckdns.env.example`(플레이스홀더만) + `README.md`(설치 절차)
  - ⚠️ **보안**: 사용자가 대화 중 실제 DuckDNS 토큰을 평문으로 전달했으나, 레포에 커밋되는 파일에는 절대 하드코딩하지 않음 — 스크립트는 `/etc/duckdns/duckdns.env`(EC2에서 직접 생성, git 추적 대상 아님)에서 토큰을 읽도록 설계. 실제 토큰 값은 채팅 응답에만 안내(SSH로 직접 실행할 명령어 형태)하고 파일로는 남기지 않음
  - 이 세션 환경은 2026-07-27부터 EC2로 아웃바운드 연결이 안 되는 상태라 Claude가 직접 실행/검증 불가 — 사용자가 SSH로 직접 설치·실행
  - **사용자가 EC2에 직접 설치·실행 완료** (2026-07-31) — `duckdns-update.service` 정상 동작 확인

- **GitHub Secrets `EC2_HOST` 자동 업데이트 요청 — 검토 후 더 단순한 대안으로 대체, 스크립트는 작성 안 함**
  - 사용자가 "EC2 부팅 시 PAT로 GitHub API 호출해 `EC2_HOST` secret을 자동 갱신"을 요청했으나, secrets 쓰기 권한이 있는 PAT를 EC2 인스턴스에 상시 저장해야 하는 구조라 인스턴스가 탈취될 경우 CI/CD 시크릿 전체(`EC2_SSH_KEY` 등)가 위험해지는 점을 지적
  - 대안 제시: 방금 만든 DuckDNS 자동 갱신이 이미 `fashion-app-jh.duckdns.org`를 최신 IP로 유지하므로, `EC2_HOST` secret 값을 **IP 대신 이 도메인으로 1회만 수동 변경**하면 이후로는 IP가 바뀌어도 아무것도 안 해도 됨(`deploy.yml`은 `host: ${{ secrets.EC2_HOST }}`만 참조하므로 도메인이든 IP든 코드 변경 불필요)
  - 사용자가 이 대안을 선택 → GitHub 웹 UI(Settings → Secrets and variables → Actions) 절차와 `gh secret set EC2_HOST --body "fashion-app-jh.duckdns.org"` 명령만 안내, `infra/github-secrets/`는 만들지 않음
  - **사용자가 GitHub 저장소 Settings에서 `EC2_HOST`를 `fashion-app-jh.duckdns.org`로 변경 완료** (2026-07-31)

**현재 전체 구현 상태**
- README.md: 성능 최적화/테스트 섹션 추가, k6 결과 최신화, 로드맵 섹션 현재 상태로 정정 — **완료**
- EC2 DuckDNS 자동 갱신(`infra/duckdns/`): 코드/문서 작성 + **EC2 설치·실행까지 완료** (2026-07-31)
- GitHub Secrets `EC2_HOST`: PAT 자동화 대신 도메인으로 1회 수동 교체하는 방식으로 결정, **실제 변경까지 완료** (2026-07-31)

**다음에 할 작업**
1. EC2에 배포 후 `docker stats`로 spring-boot 컨테이너 실제 메모리 사용량 확인, 필요시 `-Xmx` 재조정 (이월)
2. `docker compose build ai-server && docker compose up -d ai-server`로 재배포 후 gpt-4o-mini 정상 동작 확인 (이월)
3. 사용자가 EC2에 직접 SSH 접속해 `phase0_cleanup.sql` 백업+적용 결과 회신 (이월)
4. `POST /api/home/summary`를 실제로 사용할 Android 홈 화면 설계/구현 여부 결정 (이월)
5. 플레이스토어 등록 준비 (이월)

