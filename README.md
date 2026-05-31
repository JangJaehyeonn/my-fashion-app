# 👗 AI 패션 코디 앱

> AI 기반 개인 옷장 관리 및 코디 추천 서비스  
> 온라인 쇼핑 반품률 문제를 해결하는 스마트 패션 앱

---

## 목차

- [서비스 소개](#서비스-소개)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [ERD](#erd)
- [주요 기능](#주요-기능)
- [API 명세](#api-명세)
- [프로젝트 구조](#프로젝트-구조)
- [실행 방법](#실행-방법)
- [개발 로드맵](#개발-로드맵)

---

## 서비스 소개

옷장을 촬영해서 올리면 AI가 자동으로 분류하고, 오늘 날씨에 맞는 코디를 추천해주는 개인 스타일링 앱입니다.  
구글 / 카카오 소셜 로그인으로 간편하게 시작할 수 있으며, 착용 기록을 캘린더로 관리할 수 있습니다.

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| 프론트엔드 | React 18 + Vite |
| 백엔드 | Spring Boot (JWT, REST API) |
| AI 서버 | FastAPI (Python) |
| DB | PostgreSQL + Redis |
| 이미지 스토리지 | AWS S3 |
| 인프라 | AWS EC2 + Docker + GitHub Actions |
| 인증 | Google OAuth2, Kakao OAuth2 |
| AI API | OpenAI Vision API (GPT-4o), 기상청 API |
| 상태 관리 | Zustand |
| HTTP 클라이언트 | Axios |

---

## 아키텍처

```
[React 클라이언트]
      │
      │ HTTPS
      ▼
[Spring Boot — 메인 백엔드]  ──▶  [AWS S3] (이미지 저장)
      │              │
      │ AI 요청       │ DB 읽기/쓰기
      ▼              ▼
[FastAPI — AI 서버]    [PostgreSQL]
      │
      ▼
[외부 API]
  - OpenAI Vision API (옷 분류 / 코디 추천)
  - 기상청 API (날씨)

[Redis] ◀── Spring Boot (세션, 날씨 캐싱)
[GitHub Actions] ──▶ AWS EC2 (CI/CD 자동 배포)
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

> 소셜 로그인 전용이므로 password_hash 없음

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

### outfit_items
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

## 주요 기능

| 화면 | 설명 |
|------|------|
| 로그인 | 구글 / 카카오 소셜 로그인 |
| 옷장 | 카테고리 필터 + 옷 목록 + 사진 업로드 + 상세 모달 |
| 코디 추천 | 오늘 날씨 카드 + AI 코디 추천 + 코디 저장 |
| 코디 캘린더 | 월별 캘린더 + 날짜별 착용 코디 기록 |
| 마이페이지 | 프로필 + 통계(옷장/코디/착용일) + 로그아웃 |

---

## API 명세

### Auth

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /api/auth/google | 구글 소셜 로그인 |
| POST | /api/auth/kakao | 카카오 소셜 로그인 |
| POST | /api/auth/refresh | JWT 토큰 갱신 |
| POST | /api/auth/logout | 로그아웃 |

### User 🔒

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/users/me | 내 프로필 조회 |
| PUT | /api/users/me | 프로필 수정 |

### Clothes 🔒

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/clothes | 내 옷장 목록 |
| POST | /api/clothes | 옷 업로드 → S3 저장 → AI 분류 |
| GET | /api/clothes/{id} | 옷 상세 조회 |
| PUT | /api/clothes/{id} | 옷 정보 수정 |
| DELETE | /api/clothes/{id} | 옷 삭제 |

### Outfit 🔒

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/outfits | 내 코디 목록 |
| POST | /api/outfits | 코디 저장 |
| DELETE | /api/outfits/{id} | 코디 삭제 |

### Calendar 🔒

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | /api/calendar | 월별 코디 기록 조회 |
| POST | /api/calendar | 오늘 코디 기록 저장 |
| DELETE | /api/calendar/{id} | 코디 기록 삭제 |

### AI 서버 (Spring Boot 내부 호출 전용)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | /ai/clothes/classify | 이미지 → 카테고리/색상/패턴 분류 |
| POST | /ai/outfits/recommend | 날씨 + 옷장 → 코디 추천 |
| GET | /ai/weather | 현재 날씨 조회 (기상청 API) |

> 🔒 = JWT 인증 필요

---

## 프로젝트 구조

```
my-fashion-app/
├── frontend/                  # React 18 + Vite
│   └── src/
│       ├── api/               # Axios 요청 모듈 (auth.js, clothes.js, outfit.js)
│       ├── components/        # 공통 UI 컴포넌트 (BottomNav, PrivateRoute)
│       ├── pages/             # 라우트 페이지
│       ├── hooks/             # 커스텀 훅 (useAuth, useWeather)
│       ├── store/             # Zustand 전역 상태 (authStore)
│       └── utils/
│
├── backend/                   # Spring Boot
│   └── src/main/java/com/fashionapp/
│       ├── domain/
│       │   ├── user/
│       │   ├── clothes/
│       │   └── outfit/
│       ├── global/
│       │   ├── config/        # Security, CORS 설정
│       │   ├── jwt/           # JWT 처리
│       │   └── exception/     # 글로벌 에러 핸들링
│       └── infra/
│           ├── S3Uploader.java
│           └── AiServerClient.java
│
├── ai-server/                 # FastAPI (Python)
│   └── app/
│       ├── routers/           # classify.py, recommend.py, weather.py
│       ├── services/          # openai_service.py, weather_service.py, recommend_service.py
│       ├── schemas/           # Pydantic 모델
│       ├── core/              # config.py (환경변수)
│       └── main.py
│
├── .github/workflows/         # GitHub Actions CI/CD
├── docker-compose.yml
└── CLAUDE.md
```

---

## 실행 방법

### 사전 요구사항

- Docker Desktop
- Java 17+
- Node.js 18+

### 1. 환경변수 설정

**`backend/src/main/resources/application.yml`**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: YOUR_GOOGLE_CLIENT_ID
            client-secret: YOUR_GOOGLE_CLIENT_SECRET
          kakao:
            client-id: YOUR_KAKAO_CLIENT_ID
            client-secret: YOUR_KAKAO_CLIENT_SECRET

cloud:
  aws:
    credentials:
      access-key: YOUR_AWS_ACCESS_KEY
      secret-key: YOUR_AWS_SECRET_KEY
    s3:
      bucket: YOUR_S3_BUCKET_NAME
```

**`ai-server/.env`**
```env
OPENAI_API_KEY=your_openai_api_key
WEATHER_API_KEY=your_kma_api_key
```

### 2. Docker (PostgreSQL + Redis + AI 서버)

```bash
docker compose up -d
```

### 3. Spring Boot 백엔드

```bash
cd backend
./gradlew bootRun
# http://localhost:8080
```

### 4. React 프론트엔드

```bash
cd frontend
npm install
npm run dev
# http://localhost:3000
```

---

## 개발 로드맵

### Phase 1 — MVP ✅ (진행 중)

- [x] 소셜 로그인 (Google, Kakao OAuth2)
- [x] JWT 인증 / 토큰 갱신
- [x] 옷 사진 업로드 + S3 저장
- [x] OpenAI Vision API 옷 자동 분류
- [x] 날씨 기반 코디 추천 (GPT-4o + 기상청 API)
- [x] 코디 저장 + 캘린더
- [x] 마이페이지
- [x] FastAPI AI 서버 구현
- [x] React 프론트엔드 구현
- [ ] AWS EC2 배포 + GitHub Actions CI/CD

### Phase 2 — 가상 피팅 (예정)

- [ ] AI 아바타 생성 (Ready Player Me API)
- [ ] 가상 피팅 (OOTDiffusion)
- [ ] 브랜드 제휴 입점

### Phase 3 — 커뮤니티 (추후)

- [ ] 코디 공유 피드
- [ ] 좋아요 / 댓글 / 팔로우

---

## 라이선스

MIT License
