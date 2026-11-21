# 로컬 개발 환경 (처음부터 끝까지)

AWS(EC2·S3)는 정리했고, 지금은 **PC 하나에서 전부 돌립니다**: Postgres·Redis(Docker) + 백엔드(`bootRun`) + Android 에뮬레이터(디버그 빌드). 이미지는 로컬 디스크에 저장됩니다.
배포(Tencent)는 [`DEPLOY_TODO.md`](DEPLOY_TODO.md)를 보세요.

> ⚠️ **이 PC는 공인 IP입니다.** `0.0.0.0`에 열린 포트는 인터넷에서 보입니다(2026-11-02 Redis 침입 사고, `AUDIT.md` 참고). 아래 모든 서비스는 **`127.0.0.1`에만** 바인딩합니다. `"5432:5432"`처럼 IP 없이 쓰지 마세요.

## 0. 필요한 것
- JDK 17, Docker Desktop, Android SDK(`adb`, `emulator`, AVD `Pixel_8` 등), Android용 Gradle 8.10.2(Android Studio를 쓰면 자동)
- 유료 API(OpenAI·HF)는 쓰지 않습니다. AI 서버는 띄우지 않습니다(아래 "AI 서버 없이" 참고).

## 1. Postgres · Redis 띄우기
Docker Desktop을 켠 뒤, 저장소 루트에서:
```bash
docker compose up -d postgres redis
docker ps --format '{{.Names}} {{.Ports}}'   # 둘 다 127.0.0.1:...로 시작해야 함
```
`docker-compose.yml`의 포트는 `127.0.0.1:5432`, `127.0.0.1:6379`로 고정돼 있습니다. 테이블은 백엔드가 기동할 때 `ddl-auto: update`로 만듭니다. `ai-server`는 `up -d` 대상에 넣지 않습니다.

## 2. `backend/.env`
`backend/.env.example`을 복사해 `backend/.env`로 만들고 채웁니다(git 추적 안 함, 실제 키를 예시 파일에 쓰지 마세요).

| 변수 | 필수 | 용도 |
|---|---|---|
| `JWT_SECRET` | ✅ | Base64 32바이트 이상. `openssl rand -base64 48` |
| `DB_PASSWORD` | ✅ | postgres 비밀번호. 로컬 docker compose는 `POSTGRES_PASSWORD`와 같은 값 |
| `GOOGLE_CLIENT_ID/SECRET`, `KAKAO_CLIENT_ID/SECRET` | ✅ | 없으면 기동 실패. 아이디 로그인만 쓸 거면 아무 문자열이어도 기동됨 |

비밀값(`JWT_SECRET`, `DB_PASSWORD`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_SECRET`)은 설정 파일에 기본값이 없습니다.
하나라도 비어 있으면 로컬·운영 모두 `RequiredSecretsValidator`가 빠진 변수 이름을 알려 주며 기동을 중단합니다.
| `STORAGE_TYPE` | | `local`(기본) / `s3` |
| `STORAGE_LOCAL_ROOT` | | 로컬 이미지 저장 경로. 기본 `~/fashionapp-storage`(프로젝트 밖) |
| `PUBLIC_BASE_URL` | | 이미지 URL에 쓰는 서버 주소. 기본 `http://localhost:8080` |
| `WEATHER_API_KEY` | 날씨용 | OpenWeatherMap 키. 백엔드가 직접 호출(AI 서버 불필요). 없으면 날씨가 503 |
| `DB_URL`, `DB_USERNAME`, `REDIS_*`, `AI_SERVER_*` | | 기본값이 로컬 docker compose |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `OAUTH2_REDIRECT_BASE` | | 로컬 기본값 있음 |

옛 `AWS_ACCESS_KEY`·`AWS_SECRET_KEY`·`S3_BUCKET` 줄은 더 이상 읽지 않으니 지우세요.

### 로컬 이미지 저장 (`storage.type=local`)
- 저장 위치: `STORAGE_LOCAL_ROOT`(기본 `C:\Users\<사용자>\fashionapp-storage`). 구조는 `clothes|diagnosis|vton/<사용자id>/<uuid>.jpg|png|webp|heic`.
- 이미지는 `GET /api/images/...`로만 나가고 **로그인한 본인 것만** 열립니다(남의 것·없는 것은 404, 토큰 없으면 401). 앱은 Coil에 백엔드 주소로만 JWT를 붙이는 로더를 씁니다.
- 회원 탈퇴·옷 삭제 시 파일도 같이 지워지고, 비면 사용자 폴더도 지워집니다.
- 경로를 레포 안으로 바꾸면 `.gitignore`(`/storage/`, `backend/storage/` 등)가 커밋을 막지만, 가급적 프로젝트 밖을 쓰세요.

## 3. 백엔드 실행
```bash
cd backend
SERVER_ADDRESS=127.0.0.1 ./gradlew bootRun
```
- 기동에 약 5초. 로그에 `image storage: local disk at ...`과 `Started FashionAppApplication`이 보이면 정상입니다.
- `SERVER_ADDRESS=127.0.0.1`을 빼면 `0.0.0.0:8080`에 열립니다. 빼지 마세요.
- 백그라운드로 돌릴 때는 `nohup ./gradlew bootRun > log 2>&1 < /dev/null &`처럼 stdin을 막으세요(터미널 입력을 기다리며 멈추는 일이 있었습니다).
- 확인: `netstat -ano | grep :8080`이 `127.0.0.1:8080 LISTENING`이어야 합니다.

## 4. 에뮬레이터 + 디버그 앱
```bash
emulator -avd Pixel_8 -no-snapshot-load        # 부팅 후 adb devices에 "device"로 보일 때까지 대기
cd android
gradle assembleDebug                           # Android Studio를 쓰면 Run으로 대체. 캐시된 Gradle: ~/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle
adb reverse tcp:8080 tcp:8080                  # 기기 localhost:8080 → PC 127.0.0.1:8080
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.fashionapp/.MainActivity
```
- **디버그 빌드**: API·OAuth2 모두 `http://localhost:8080`(`adb reverse` 필수. 에뮬레이터·adb를 재시작하면 사라지니 로그인/API가 안 붙으면 이것부터 다시). HTTP는 디버그에서 `localhost`/`10.0.2.2`만 허용(`app/src/debug/res/xml/network_security_config.xml`).
- **릴리즈 빌드**: 서버 주소를 코드에 넣지 않습니다. `android/local.properties`에 `release.server.url=https://...`(또는 `-PreleaseServerUrl=`)를 주지 않으면 `https://api.example.invalid`가 들어가고 빌드 때 경고가 납니다. 릴리즈는 HTTPS만 허용하므로 **로컬 백엔드와는 붙지 않습니다.** 로컬 E2E는 디버그로 하세요.
- 로그인: 로그인 화면에서 **아이디로 회원가입** 후 같은 아이디·비밀번호로 로그인(소셜 로그인은 콘솔 redirect URI 등록이 필요). 위치 안내 다이얼로그는 "서울 날씨로 볼게요"로 넘기면 됩니다.
- 에뮬레이터 갤러리에 사진 넣기: `MSYS_NO_PATHCONV=1 adb push 사진.jpg /sdcard/Pictures/` (Git Bash는 `/sdcard`를 Windows 경로로 바꿔 버려서 환경변수가 필요).
- `adb`로 화면 조작: `adb shell uiautomator dump`로 좌표를 얻고 `adb shell input tap x y`. 스크린샷은 `adb exec-out screencap -p > a.png`.

### 소셜 로그인 redirect URI (선택)
로컬에서 Google/Kakao를 쓰려면 콘솔에 `http://localhost:8080/login/oauth2/code/google`, `.../kakao`를 등록합니다(IP 주소는 등록 불가라 `localhost`여야 함). 등록 전 증상: Google `redirect_uri_mismatch`, Kakao `KOE006`. 아이디 로그인만 쓰면 필요 없습니다.

## 5. AI 서버 없이 쓸 수 있는 것
AI 서버를 띄우지 않으면 `AiServerClient`가 연결 실패 → `AI_SERVER_ERROR`(503)를 돌려줍니다.
- 동작: 로그인·가입·탈퇴, 프로필, **날씨**(백엔드가 OpenWeatherMap을 직접 호출), 옷장 **목록·삭제**, 진단 **이력** 조회, 이미지 조회
- 실패(503): **옷 등록**(등록 흐름이 AI 분류를 먼저 호출하고 성공해야 저장함), 코디/쇼핑 추천, 가상 피팅, 진단
- 실패 시 이미지 파일은 저장되지 않습니다(분류 먼저, 저장은 그다음).
- 옷 등록 흐름을 AI 없이 확인하려면 **테스트용 스텁**이 필요합니다. 운영 코드에는 가짜 응답을 넣지 않으며, 스텁은 레포 밖 임시 스크립트로 `127.0.0.1:8000`에 `/ai/clothes/classify`만 흉내 냅니다(`AUDIT.md` 5번 참고). 요청 본문이 chunked로 오므로 스텁은 본문을 끝까지 읽어야 합니다.

## 6. 백엔드 테스트
```bash
docker compose up -d postgres redis
cd backend && ./gradlew test
```
- 통합 테스트는 로컬 postgres·redis를 실제로 쓰고, 사용자는 테스트마다 만들고 지웁니다.
- 외부 호출은 모두 막혀 있습니다: AI 서버는 `@MockBean`, 이미지 저장소는 `@MockBean`(공통) 또는 임시 디렉터리(`LocalImageIntegrationTest`, `LocalDiskStorageTest`).
- `src/test/resources/application-test.yml`이 `backend/.env`의 키를 더미로 덮어씁니다.

## 7. 끝내기
```bash
adb shell am force-stop com.fashionapp
# 백엔드: bootRun 터미널에서 Ctrl+C (또는 8080 리스닝 프로세스 종료)
docker compose stop postgres redis       # 데이터는 볼륨에 남음. 지우려면 down -v
```
