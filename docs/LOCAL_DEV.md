# 로컬 개발 환경 (Android 디버그 빌드 + 로컬 백엔드)

## 앱 빌드 타입별 접속 대상

| 빌드 | `BASE_URL` | `OAUTH2_BASE_URL` | HTTP(cleartext) |
|---|---|---|---|
| debug | `http://10.0.2.2:8080/api/` (에뮬레이터 → PC의 localhost) | `http://localhost:8080` | `10.0.2.2`, `localhost`만 허용 (`app/src/debug/res/xml/network_security_config.xml`) |
| release | `https://fashion-app-jh.duckdns.org/api/` | `https://fashion-app-jh.duckdns.org` | 전부 차단 (`app/src/main/res/xml/network_security_config.xml`) |

- `BASE_URL`과 `OAUTH2_BASE_URL`은 `app/build.gradle.kts`의 `buildTypes`에서 `buildConfigField`로 나눠 둡니다. 빌드 시점에 고정되므로, 바꾸면 APK를 다시 빌드해 설치해야 합니다.
- 백엔드 포트는 8080입니다. `application.yml`에 `server.port`가 없어 Spring 기본값을 씁니다.
- OAuth2 로그인은 Google/Kakao 콘솔에 IP 주소를 redirect URI로 등록할 수 없어서 `localhost`를 씁니다. 그래서 에뮬레이터를 켤 때마다 다음 명령이 필요합니다. 에뮬레이터나 adb를 재시작하면 설정이 초기화됩니다.
  ```bash
  adb reverse tcp:8080 tcp:8080
  ```

## 로컬 백엔드 실행

### 1. 필요한 것
- JDK 17
- PostgreSQL 16: `localhost:5432`, DB·사용자·비밀번호 모두 `fashionapp` (`application.yml` 기본값과 일치)
- Redis 7: `localhost:6379`, 비밀번호 없음

Docker를 쓰는 경우의 예시입니다. 2026-11-02 기준 이 PC에서는 Docker를 쓰지 않기로 하고 모두 삭제했습니다.
```bash
docker run -d --name fashionapp-db -p 5432:5432 \
  -e POSTGRES_DB=fashionapp -e POSTGRES_USER=fashionapp -e POSTGRES_PASSWORD=fashionapp postgres:16-alpine
docker run -d --name fashionapp-redis -p 6379:6379 redis:7-alpine
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
| `OAUTH2_REDIRECT_BASE` | 선택 | 기본값 `fashionapp://app` (앱 딥링크) |

### 3. 실행
```bash
cd backend
./gradlew bootRun
```

## AI 서버 없이 백엔드가 뜨는가

**코드 기준으로는 뜹니다. 실제 실행으로는 확인하지 못했습니다.** Docker를 삭제하기로 해서 DB와 Redis를 띄우지 못했습니다(2026-11-02).

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
