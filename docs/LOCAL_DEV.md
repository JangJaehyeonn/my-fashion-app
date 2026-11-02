# 로컬 개발 환경 (Android 디버그 빌드 + 로컬 백엔드)

## 앱 빌드 타입별 접속 대상

| 빌드 | `BASE_URL` | `OAUTH2_BASE_URL` | HTTP(cleartext) |
|---|---|---|---|
| debug | `http://localhost:8080/api/` (`adb reverse`로 PC의 8080에 연결) | `http://localhost:8080` | `localhost`, `10.0.2.2`만 허용 (`app/src/debug/res/xml/network_security_config.xml`) |
| release | `https://fashion-app-jh.duckdns.org/api/` | `https://fashion-app-jh.duckdns.org` | 전부 차단 (`app/src/main/res/xml/network_security_config.xml`) |

- `BASE_URL`과 `OAUTH2_BASE_URL`은 `app/build.gradle.kts`의 `buildTypes`에서 `buildConfigField`로 나눠 둡니다. 빌드 시점에 고정되므로, 바꾸면 APK를 다시 빌드해 설치해야 합니다.
- 백엔드 포트는 8080입니다. `application.yml`에 `server.port`가 없어 Spring 기본값을 씁니다.
- 디버그 빌드는 API와 OAuth2 모두 `localhost:8080`을 씁니다. OAuth2 redirect URI에는 IP 주소(`10.0.2.2`)를 등록할 수 없어 `localhost`여야 하기 때문입니다. 기기의 `localhost:8080`을 PC의 8080으로 연결하는 `adb reverse`가 필요합니다.

### adb reverse

```bash
adb devices                       # 에뮬레이터(또는 USB 연결 기기)가 device 상태인지 확인
adb reverse tcp:8080 tcp:8080     # 기기 localhost:8080 → PC localhost:8080
adb reverse --list                # "host-xx tcp:8080 tcp:8080"이 보이면 적용된 것
```
- 에뮬레이터나 adb 서버를 다시 시작하면 사라집니다. 앱에서 로그인 페이지가 "This site can't be reached"이거나 API가 연결 실패하면 가장 먼저 확인하세요.
- 기기가 여러 대면 `adb -s <serial> reverse tcp:8080 tcp:8080`.
- 해제: `adb reverse --remove tcp:8080`.
- USB로 연결한 실기기에서도 같은 디버그 빌드로 동작합니다.

### Google·Kakao 콘솔에 등록할 redirect URI (로컬)

Spring이 `{baseUrl}/login/oauth2/code/{registrationId}` 형식으로 만듭니다. 로컬에서는 `baseUrl`이 `http://localhost:8080`입니다.

| 콘솔 | 위치 | 추가할 값 (정확히 이대로, 끝에 `/` 없음) |
|---|---|---|
| Google Cloud Console | API 및 서비스 → 사용자 인증 정보 → `backend/.env`의 `GOOGLE_CLIENT_ID`와 같은 OAuth 2.0 클라이언트(웹 애플리케이션) → 승인된 리디렉션 URI | `http://localhost:8080/login/oauth2/code/google` |
| Kakao Developers | 내 애플리케이션 → `backend/.env`의 `KAKAO_CLIENT_ID`(REST API 키)인 앱 → 카카오 로그인 → Redirect URI | `http://localhost:8080/login/oauth2/code/kakao` |

- 운영용 `https://fashion-app-jh.duckdns.org/login/oauth2/code/{google,kakao}`는 그대로 두고 **추가**만 합니다.
- Google: 동의 화면이 "테스트" 상태면 로그인할 계정이 테스트 사용자 목록에 있어야 합니다. 반영까지 몇 분 걸릴 수 있습니다.
- 등록이 안 됐을 때 증상: Google은 `400 redirect_uri_mismatch`, Kakao는 `KOE006`.
- 확인 방법: 백엔드를 띄운 뒤 `curl -s -o /dev/null -D - "http://localhost:8080/oauth2/authorization/google?client=app" | grep -i location` → `redirect_uri=http://localhost:8080/login/oauth2/code/google`이 보이면 위 값과 일치하는 것입니다.

## 로컬 백엔드 실행

### 1. 필요한 것
- JDK 17
- PostgreSQL 16: `localhost:5432`, DB·사용자·비밀번호 모두 `fashionapp` (`application.yml` 기본값과 일치)
- Redis 7: `localhost:6379`, 비밀번호 없음

저장소 루트에서 DB와 Redis만 띄웁니다. AI 서버(`ai-server`)는 유료 API를 호출하므로 같이 띄우지 않습니다.
```bash
docker compose up -d postgres redis
```
테이블은 기동할 때 `ddl-auto: update`가 자동으로 만듭니다.

### 2. `backend/.env` (git 추적 안 함)
`spring-dotenv`가 기동 시 이 파일을 읽습니다.

| 변수 | 필수 | 용도 |
|---|---|---|
| `JWT_SECRET` | ✅ | Base64 인코딩된 32바이트 이상 키. 없으면 기동 실패 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | ✅ | 없으면 OAuth2 클라이언트 설정 단계에서 기동 실패 |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | ✅ | 위와 같음 |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `S3_BUCKET` | ✅ | S3 빈 생성에 필요. 옷 등록·사진 조회에 사용 |
| `AWS_REGION` | 선택 | 기본값 `ap-northeast-2` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 선택 | 기본값 `jdbc:postgresql://localhost:5432/fashionapp`, `fashionapp`/`fashionapp` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | 선택 | 기본값 `localhost`, `6379`, 비밀번호 없음 |
| `AI_SERVER_URL` | 선택 | 기본값 `http://localhost:8000` |
| `AI_SERVER_CONNECT_TIMEOUT`, `AI_SERVER_READ_TIMEOUT`, `AI_SERVER_VTON_READ_TIMEOUT` | 선택 | 기본값 `3s`, `30s`, `170s`. 초과 시 504 |
| `OAUTH2_REDIRECT_BASE` | 선택 | 기본값 `fashionapp://app` (앱 딥링크) |

### 3. 실행
```bash
cd backend
./gradlew bootRun
```

## AI 서버 없이 백엔드가 뜨는가

**뜹니다.** 2026-11-02에 `fix/backend-p0` 브랜치에서 AI 서버 없이 `bootRun`을 실행해 4.7초 만에 기동하는 것을 확인했습니다.

- 근거: `AiServerClient`는 요청이 올 때만 `RestClient`로 AI 서버를 호출하고, 기동 시점에 연결하거나 헬스 체크를 하지 않습니다.
- AI 서버가 없을 때 정상 동작하는 기능: 로그인·토큰 갱신, `GET/PUT /api/users/me`, `GET /api/clothes`(옷장 목록), `DELETE /api/clothes/{id}`, `GET /api/diagnosis`(이력)
- 실패하는 기능: 아래 API는 `AI_SERVER_ERROR`(503)를 반환합니다.
  - `GET /api/weather` (캐시가 비어 있을 때)
  - `POST /api/clothes` (등록 시 AI 분류)
  - `POST /api/outfits/recommend/*`
  - `POST /api/vton`, `POST /api/diagnosis`, `POST /api/shopping/recommend`
- 주의: AI 서버를 띄우면 OpenAI와 HF Space를 실제로 호출하므로 비용이 듭니다. LLM 연동은 2026-11-18 이후로 미뤄 두었으니 그때까지는 띄우지 않습니다.
- DB는 기동에 필수입니다(Hikari 연결 실패 시 기동 실패).
- Redis는 기동은 되지만 로그인(리프레시 토큰 저장)과 날씨 캐시를 쓸 때 실패할 것으로 추정합니다. Lettuce는 기동 시 연결하지 않기 때문입니다.

## 백엔드 테스트

```bash
docker compose up -d postgres redis   # 통합 테스트에 필요
cd backend && ./gradlew test
```
- 통합 테스트(`support/IntegrationTest` 상속)는 로컬 postgres·redis를 실제로 사용합니다. 테스트마다 새 사용자를 만들고 스스로 정리합니다.
- 외부 호출은 모두 막혀 있습니다. AI 서버와 S3 업로드는 `@MockBean`으로 대체하고, AI 타임아웃은 MockWebServer로 흉내 냅니다.
- `src/test/resources/application-test.yml`이 `backend/.env`의 실제 키를 더미 값으로 덮어씁니다.
