# 배포 TODO (Tencent 서버 + 새 도메인)

지금은 **로컬 전용**입니다. AWS(EC2·IAM·S3)는 모두 정리됐고, 아래 항목은 Tencent 서버와 도메인이 준비되면 채웁니다.
(이 문서는 작업이 진행되면서 항목별로 채워집니다.)

## AI 서버가 이미지를 받는 방식 (코드 확인 결과)

- **AI 서버는 이미지를 URL로 가져가지 않고, 백엔드가 멀티파트 바이트로 보내 줍니다.**
  - `AiServerClient.classifyClothes / diagnoseOutfit / virtualTryOn`이 업로드된 `MultipartFile`의 바이트를 `/ai/clothes/classify`, `/ai/diagnosis`, `/ai/vton`에 `UploadFile`로 전달합니다.
  - AI 서버(`ai-server/app/routers/*.py`)는 `UploadFile`만 받고, OpenAI에는 `data:` base64로 넘깁니다. 이미지 URL을 받아 다운로드하는 코드는 없습니다.
  - 가상 피팅 결과도 AI 서버가 base64로 돌려주면 백엔드가 `ImageStorage.storeBytes`로 저장합니다.
- **따라서 로컬 저장소(`storage.type=local`)로 바꿔도, 나중에 COS(`storage.type=s3`)로 바꿔도 AI 서버는 수정할 필요가 없습니다.** AI 서버에는 저장소 자격 증명도, 저장소로 가는 네트워크 경로도 필요 없습니다.
- 주의할 점 하나: 백엔드는 `image/heic`를 허용하지만 AI 서버의 `_ALLOWED_TYPES`(`routers/clothes.py` 등)에는 없어, HEIC는 백엔드 검증을 통과해도 AI 단계에서 거절됩니다(이전부터 있던 차이). 앱이 업로드 전에 JPEG로 압축하므로 실제로는 닿지 않지만, 정리하려면 한쪽을 맞추세요.

## 정리 대상 (지금은 건드리지 않음)

- AI 서버의 `GET /ai/weather`(`ai-server/app/routers/weather.py`, `services/weather_service.py`, `schemas/weather.py`)와 `WEATHER_API_KEY` 설정(`core/config.py`의 `weather_api_key`, `ai-server/.env(.example)`): 날씨를 백엔드가 직접 호출하게 바꾼 뒤로 아무도 호출하지 않습니다. 배포 전에 삭제하거나, 유지한다면 `weather_api_key`를 필수에서 선택으로 낮추세요(지금은 키가 없으면 AI 서버 기동이 실패합니다).

## GitHub Actions

- `deploy.yml` (EC2 배포): **삭제하지 않고 비활성화**했습니다. `push` 트리거를 없애고 `workflow_dispatch`(수동 실행)만 남겼습니다. 복구할 때는 접속 대상 호스트·사용자·키 시크릿과 `cd ~/my-fashion-app` 이후 스크립트를 Tencent 서버 기준으로 바꾸고, `on:`에 `push: branches: [dev]`를 다시 넣으세요.
- `ci.yml` (백엔드 빌드·테스트): 새로 추가했습니다. 이전에는 빌드·테스트 전용 잡이 없었습니다(`deploy.yml`과 PR 리뷰만 있었음). postgres·redis 서비스 컨테이너를 띄우고 `./gradlew build`를 실행합니다. 시크릿은 필요 없습니다. **GitHub에서의 실제 실행은 아직 확인하지 못했고**, 같은 명령을 로컬에서 통과시킨 상태입니다. (`backend/gradlew`가 실행 권한 없이 커밋돼 있어서 `+x`로 고쳤습니다.)
- `pr-review.yml`: 그대로입니다. `ANTHROPIC_API_KEY`로 PR마다 Claude API를 호출하는 유료 워크플로입니다. 비용이 걱정되면 이 워크플로도 비활성화하세요.

### 더 이상 쓰지 않는 시크릿 (GitHub Settings → Secrets and variables → Actions에서 삭제 대상)

| 이름 | 용도 |
|---|---|
| `EC2_HOST` | EC2 접속 주소 (DuckDNS 도메인) |
| `EC2_USERNAME` | EC2 SSH 사용자 |
| `EC2_SSH_KEY` | EC2 SSH 개인 키 |

- 확인 시점(2026-11-08, `gh secret list`)에 위 세 개가 **아직 남아 있었습니다.** 삭제하셨다면 반영이 안 된 것이니 다시 확인해 주세요.
- AWS 액세스 키·S3 버킷 이름은 Actions 시크릿으로 등록된 적이 없고(목록에 없음) `backend/.env`에만 있었습니다. 로컬 `backend/.env`에 남은 `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `S3_BUCKET` 줄은 이제 어디서도 읽지 않으니 지우세요.
- `ANTHROPIC_API_KEY`는 AWS와 무관합니다 (`pr-review.yml`이 사용 중).

## 서버·도메인이 준비되면 채울 값과 할 일

### 1) Tencent 서버
- [ ] 서버 생성, Docker 설치, 방화벽(보안 그룹)은 80/443만 개방. Postgres·Redis·AI 서버 포트는 외부에 열지 않기(`127.0.0.1`/내부 네트워크만 — 로컬 Redis 침입 사고 재발 방지).
- [ ] `docker-compose.prod.yml`을 새 서버 기준으로 점검(메모리 설정은 t3.small 기준이었음), `backend/.env`를 서버에서 직접 작성(git에 올리지 않기).
- [ ] 백엔드 환경변수: `SPRING_PROFILES_ACTIVE=prod`와 `PUBLIC_BASE_URL`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `STORAGE_TYPE`. prod 프로필은 이 값들이 비었거나 `localhost`/`http`면 **기동을 중단**하고 빠진 변수를 알려 줍니다(`backend/.env.example` 참고).

### 2) 이미지 저장소 (COS)
- [ ] 버킷 생성(비공개). 앱은 presigned URL로 읽으므로 공개 읽기를 열지 않기.
- [ ] `STORAGE_TYPE=s3`, `S3_ENDPOINT=https://cos.<region>.myqcloud.com`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PATH_STYLE`(COS는 보통 `false`, 호환 문제가 있으면 `true`로 시도). 코드는 `S3CompatibleStorage`가 이미 처리합니다.
- [ ] **COS 호환성은 실제로 확인하지 못했습니다.** presigned URL 조회·삭제·prefix 삭제(탈퇴)를 COS에서 한 번씩 시험하세요. 이 저장소 구현은 AWS SDK v2의 `endpointOverride`를 씁니다.
- [ ] 로컬에서 쌓은 이미지를 옮길 필요가 있으면 `~/fashionapp-storage`의 키 구조(`category/userId/uuid.ext`)를 그대로 버킷에 올리면 DB의 키와 맞습니다.
- [ ] 이전 AWS 시절 DB 행은 정적 S3 URL이 저장돼 있을 수 있습니다. 코드는 URL에서 키를 뽑아 쓰지만(`ImageKeys.toKey`) 옛 버킷은 사라졌으니 해당 이미지는 열리지 않습니다. 새 DB로 시작하면 해당 없음.

### 3) 도메인·HTTPS
- [ ] 도메인 구입·DNS 연결, 인증서(`infra/certbot/issue-cert.sh <도메인>`). `nginx/nginx.conf`의 `YOUR_DOMAIN`(3곳)을 실제 도메인으로 교체. DuckDNS(`infra/duckdns`)는 더 이상 쓰지 않으므로 필요 없으면 삭제하세요.
- [ ] 노출된 적 있는 DuckDNS 토큰(`infra/duckdns/README.md`에 평문으로 커밋돼 있었음 — 이 브랜치에서 placeholder로 바꿨지만 git 기록에는 남음)은 DuckDNS에서 재발급하거나 계정을 정리하세요.

### 4) OAuth 콘솔
- [ ] Google Cloud Console: 승인된 리디렉션 URI에 `https://<새 도메인>/login/oauth2/code/google` 추가.
- [ ] Kakao Developers: Redirect URI에 `https://<새 도메인>/login/oauth2/code/kakao` 추가.
- [ ] 이전 도메인 URI는 제거. (Spring은 `forward-headers-strategy: framework`로 nginx의 `X-Forwarded-*`를 신뢰해 `https` redirect_uri를 만듭니다.)

### 5) 앱 릴리즈 주소
- [ ] `android/local.properties`에 `release.server.url=https://<새 도메인>`(또는 빌드 시 `-PreleaseServerUrl=...`). 미설정이면 `https://api.example.invalid`가 들어가고 경고가 납니다. 릴리즈는 HTTPS만 허용.

### 6) GitHub Actions 배포 잡 복구
- [ ] `deploy.yml`에 `push: branches: [dev]` 트리거를 되살리고, 접속 정보(시크릿 이름 `EC2_*`를 새 이름으로 바꾸는 것도 방법)와 `cd ~/my-fashion-app` 이후 스크립트를 새 서버 기준으로 수정.
- [ ] nginx.conf는 단일 파일 바인드 마운트라 바뀐 뒤에는 컨테이너 재시작이 필요합니다(이 점은 기존 스크립트에 이미 반영돼 있음).
- [ ] `ci.yml`은 그대로 동작합니다(시크릿 불필요).

### 7) AI 서버 이미지 전달 방식
- 위 "AI 서버가 이미지를 받는 방식" 참고 — **변경 불필요**. 백엔드가 멀티파트 바이트로 보내므로 COS로 옮겨도 AI 서버는 저장소를 몰라도 됩니다. 필요한 것은 AI 서버 `.env`(`OPENAI_API_KEY`, 선택 `HF_API_TOKEN`)뿐. 날씨는 이제 백엔드가 직접 호출하므로 `WEATHER_API_KEY`는 **백엔드** `.env`에 둡니다(AI 서버의 `/ai/weather`는 더 이상 쓰이지 않음).
- AI 서버는 백엔드 컨테이너와 같은 내부 네트워크에서만 접근되게 하고 외부 포트는 열지 마세요. LLM 연동은 2026-11-18 이후 재개 예정.
- (선택) 백엔드는 `image/heic`를 허용하지만 AI 서버는 거절하는 차이를 맞추세요.

## 개인정보처리방침 공개

- [ ] `backend/src/main/resources/legal/*.html`의 `[ … ]` 자리(시행일·운영자·문의 이메일·보관 기간·저장소 제공자·14세 정책)를 채우세요. 목록과 근거는 `docs/PRIVACY_POLICY.md`.
- [ ] 배포 후 `https://<도메인>/privacy`, `/account-deletion`이 **로그인 없이** 열리는지 확인하고, Play Console의 개인정보처리방침 URL·계정 삭제 URL에 입력하세요(nginx는 모든 경로를 백엔드로 프록시하므로 별도 설정 불필요).
- [ ] 이미지 저장소를 COS로 바꾸면 처리방침 4·5항의 `[Tencent Cloud COS, 리전]`을 실제 리전으로 채우세요. 로컬 디스크로 운영하면 해당 행을 지우세요.
- [ ] ⚠️ **로그 3개월 보관을 실제로 구현**: 처리방침에 "접속 기록 3개월"이라고 적었지만 `docker-compose.prod.yml`에는 로그 설정이 없어 기한 없이 쌓입니다. journald(`MaxRetentionSec=3month`)나 logrotate(`maxage 90`)로 기간을 강제하세요. 자세한 내용은 `docs/PRIVACY_POLICY.md` "로그 보관 실태".
