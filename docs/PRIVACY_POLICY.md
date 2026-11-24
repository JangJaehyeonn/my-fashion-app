# 개인정보처리방침 — 작성 근거와 공개 전 할 일

**정본(공개되는 문서)** 은 백엔드가 로그인 없이 서빙하는 정적 페이지입니다.

| URL | 파일 |
|---|---|
| `/privacy` | `backend/src/main/resources/legal/privacy.html` |
| `/account-deletion` | `backend/src/main/resources/legal/account-deletion.html` |

- 앱 마이 탭의 "개인정보처리방침"이 서버 주소의 `/privacy`를 브라우저로 엽니다(디버그: `http://localhost:8080/privacy`, 릴리즈: `release.server.url`).
- Play Store 등록 시 개인정보처리방침 URL에 `https://<도메인>/privacy`, 계정 삭제 URL에 `https://<도메인>/account-deletion`을 넣으세요.
- 내용을 고칠 때는 위 HTML만 수정하면 됩니다(이 문서는 근거 목록). `LegalPagesController`가 UTF-8 HTML로 내려주고, 보안 설정은 `/privacy`, `/account-deletion`만 `permitAll`입니다(`SecurityConfig`).

> ⚠️ **초안입니다.** 법적 효력이 있는 문서이므로 공개 전에 아래 "확정해야 할 값"을 채우고 검토하세요(필요하면 전문가 확인).

## 확정해야 할 값 (페이지에 `[ … ]` 표시로 남아 있음)

- [x] 운영자: 장재현
- [ ] 시행일, 문의 이메일 (두 페이지 공통)
- [x] 서버 접속 기록 보관 기간: 3개월로 기재 — ⚠️ **현재 설정은 이를 보장하지 않음** (아래 "로그 보관 실태")
- [ ] 국외 이전(`privacy.html` 5항)의 `[ ]` 값: OpenAI·Hugging Face 법인명·연락처·국가·보유 기간, Tencent Cloud COS 리전, 서비스 서버 리전. 코드로는 확인할 수 없는 값이라 비워 둠
- [ ] 국외 이전 근거 검토: Hugging Face는 앱에서 별도 동의를 받지만(`vton_consent_at`), OpenAI 전송에는 별도 동의 화면이 없음 — 처리방침 공개만으로 충분한지 법령 기준 확인
- [ ] 동의 이력: 철회하면 `vton_consent_at`를 지워 이력이 남지 않음. 동의·철회 이력을 따로 보관해야 하는지 확인
- [ ] 이미지 저장소: 로컬 디스크로 운영하면 5항의 COS 행을 지우고 "서비스 서버"로
- [x] 만 14세 미만: 가입 불가 (가입 시 확인 구현됨)
- [x] 이메일 삭제 요청 처리: 10일 이내
- [ ] Play Console의 "데이터 보안" 설문을 이 문서와 같은 내용으로 작성

## 코드 근거 (문서 내용 ↔ 코드)

| 문서의 주장 | 근거 |
|---|---|
| 수집 항목: 아이디·닉네임·비밀번호 해시, 소셜 ID·이메일·프로필 이미지, 키·몸무게·체형·스타일 | `User.java`(`login_id`, `password_hash`, `provider_id`, `email`, `profile_image_url`, `height`, `weight`, `body_type`, `preferred_style(s)`) |
| Kakao는 이메일을 요청하지 않음 | `application.yml` Kakao scope `profile_nickname`, `profile_image` / Google scope `email`, `profile` |
| 비밀번호는 BCrypt 해시, 5회 실패 시 5분 잠금 | `PasswordConfig`(BCrypt), `LoginAttemptLimiter`(`login-max-failures: 5`, `login-lock-duration: 5m`) |
| 옷·진단·피팅 결과 이미지와 분류/진단 결과 저장 | `Clothes`, `StyleDiagnosis`, `VtonService` → `ImageStorage` (`clothes|diagnosis|vton/<userId>/<uuid>`) |
| 전신 사진은 저장하지 않고 AI 합성에만 사용 | `VtonService.tryOn`: 업로드 원본은 AI 서버로만 전달, 결과 이미지만 저장 |
| OpenAI에 옷·진단 사진, 체형 프로필, 옷 이름·색상, 상황·날씨 전달 | `ai-server`의 `clothes_service`, `diagnosis_service`, `recommend_service`(키·몸무게·체형·스타일이 프롬프트에 들어감) |
| Hugging Face 공개 Space에 전신·옷 사진 전달 | `ai-server/app/services/vton_service.py` (`yisol/IDM-VTON`, `gradio_client`) |
| 위치: 대략적 위치만, 반올림, OpenWeatherMap 전송, 계정과 연결 저장 안 함, 최대 30분 캐시 | 앱 `LocationProvider`(`ACCESS_COARSE_LOCATION`, 소수 둘째 자리 반올림), `WeatherService`(서버 재반올림, Caffeine 10분·Redis 30분), `OpenWeatherClient` |
| 리프레시 토큰 14일, 로그인 실패 기록 5분 | `application.yml`(`refresh-token-expiry`), `AuthTokenService`, `LoginAttemptLimiter` |
| 가상 피팅 동의: 첫 사용 시 동의, 시각 저장, 철회 가능, 미동의면 전송 안 함 | `User.vtonConsentAt`, `POST/DELETE /api/users/me/consents/vton`, `VtonService`(미동의 403, AI 호출 전), 앱 `FittingScreen` 동의 다이얼로그·상시 안내, 마이 탭 철회 (`VtonConsentIntegrationTest`) |
| 만 14세 이상 확인: 아이디 가입·소셜 첫 가입 모두, 시각 저장 | `SignupRequest.ageOver14`, `User.ageConfirmedAt`, `AgeGateInterceptor`, 앱 `AgeGateScreen` (`AgeGateIntegrationTest`) |
| Hugging Face Space 운영자 | `ai-server/app/core/config.py` `hf_vton_space_id = "yisol/IDM-VTON"` |
| 살까 말까 사진 30분 임시 보관, 결정 즉시·30분 뒤 삭제, 사면 옷장 사진으로 이동 | `PurchaseCheck.PHOTO_TTL`(30분), `PurchaseRecordService.finish`·`buy`, `PurchaseTempPhotoCleaner.deleteExpiredPhotos`(1분마다), Redis 임시 속성 `PurchaseDraftStore` |
| 살까 말까 기록: 사진 없이 이름·카테고리·색상·가격·판정·코디 수·결정 | `PurchaseCheck`(`purchase_checks`), `PurchaseRecordResponse` |
| 착용 기록: 옷·날짜, 옷 삭제·탈퇴 시 삭제 | `WearRecord`(`wear_records`), `ClothesService.delete`, `AccountDeletionService` |
| 이벤트 기록: 기능 종류·시각·코드/숫자만, 1년 보관 | `EventType`, `EventRecorder`(코드·숫자 외 문자열 버림), `EventService.deleteExpired`(`RETENTION_YEARS = 1`) |
| OpenAI 이전 항목에 임베딩용 옷 속성 텍스트, 살까 말까 사진·설명용 정보 | `ClothesEmbedder.embeddingText`(종류·색상·핏·소재·패턴·이름), `ai-server/app/services/embedding_service.py`, `PurchaseCheckService`(분류), `PurchaseExplainer.toRequest` |
| 탈퇴 시 계정·옷·진단·피팅 결과·토큰·착용·살까 말까·이벤트 기록 삭제 | `AccountDeletionService` (테스트 `AccountDeletionIntegrationTest`, `LocalImageIntegrationTest`) |
| 이미지는 본인에게만 제공 | `ImageController`(소유자 확인, 남의 이미지 404), S3 모드는 7일 presigned URL |
| 광고·분석 SDK 없음 | `android/app/build.gradle.kts` 의존성에 해당 라이브러리 없음 |
| 통신 HTTPS | 릴리즈 빌드는 cleartext 차단(`network_security_config`), 운영 nginx는 TLS 종료 — 새 서버에서 다시 구성 필요 |

## 앱 안내 문구와의 일치

- [x] 홈 위치 권한 안내: "대략적인 위치(약 1km 범위)를 날씨 조회에만 사용해요. 날씨 제공을 위해 OpenWeatherMap에 전달되며, 계정과 연결해 저장하지 않아요."
- [x] 가상 피팅: 첫 사용 동의 화면 + 피팅 화면 상시 안내 + 마이 탭 철회

## 로그 보관 실태 (2026-11-08 확인) — 처리방침의 "3개월"과 불일치

| 로그 | 내용 | 어디에 남나 | 보관 기간 |
|---|---|---|---|
| nginx 접속 기록 | 접속 IP, 시각, 메서드, 경로(쿼리 제외), 상태, 응답 시간 | `access_log /dev/stdout` → Docker 컨테이너 로그 | **제한 없음** |
| 백엔드 애플리케이션 로그 | userId, 아이디(가입·로그인 실패 시), 옷 등록 구간 시간, 오류 | 콘솔 → Docker 컨테이너 로그 (`logging.file` 설정 없음) | **제한 없음** |
| postgres·redis·ai-server 로그 | 각 서비스 로그 | Docker 컨테이너 로그 | **제한 없음** |

- `docker-compose.prod.yml`에 `logging:` 설정이 없어 Docker 기본 `json-file` 드라이버가 크기·기간 제한 없이 쌓습니다. 컨테이너를 다시 만들 때만 지워지는데, nginx는 배포 때 `restart`만 하므로 사실상 계속 남습니다(백엔드는 이미지가 바뀌어 재생성될 때 지워짐).
- Docker 로그 드라이버는 **기간 기준 삭제를 지원하지 않습니다**(`max-size`/`max-file`은 용량 기준). 3개월을 지키려면 배포 때 아래 중 하나가 필요합니다 — `DEPLOY_TODO.md`에 넣어 둠.
  1. 로그를 `journald` 드라이버로 보내고 호스트 `journald.conf`에 `MaxRetentionSec=3month`
  2. nginx·백엔드 로그를 파일로 남기고 `logrotate`(예: `daily`, `rotate 90`, `maxage 90`)
  3. 최소한 `logging: {driver: json-file, options: {max-size: "20m", max-file: "5"}}`로 용량 상한 + 3개월마다 컨테이너 재생성/로그 삭제 작업
- 지금은 서버가 없어(로컬 전용) 운영 로그가 쌓이는 곳은 없습니다.
