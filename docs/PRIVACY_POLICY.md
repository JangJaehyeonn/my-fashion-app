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

- [ ] 시행일, 운영자 이름, 문의 이메일 (두 페이지 공통)
- [ ] 서버 접속 기록 보관 기간 (`privacy.html` 2항). 지금 코드에는 로그 보관·삭제 정책이 없음(nginx는 stdout, 백엔드는 콘솔) — 실제 운영에서 정한 뒤 적기
- [ ] 이미지 저장소 제공자 (`privacy.html` 4항): 로컬 디스크로 운영하면 "서비스 서버", COS를 쓰면 Tencent Cloud(국외 이전에 해당). 이전 국가·시점·연락처도 법령 기준으로 확인
- [ ] 만 14세 미만 정책 (`privacy.html` 7항): 코드에는 연령 확인이 없음. 연령 확인을 넣을지, "대상 아님"으로만 적을지 결정
- [ ] 계정 삭제 요청 처리 기간 (`account-deletion.html`, 앱에 접근할 수 없을 때의 이메일 요청)
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
| 탈퇴 시 계정·옷·진단·피팅 결과·토큰 삭제 | `AccountDeletionService` (테스트 `AccountDeletionIntegrationTest`, `LocalImageIntegrationTest`) |
| 이미지는 본인에게만 제공 | `ImageController`(소유자 확인, 남의 이미지 404), S3 모드는 7일 presigned URL |
| 광고·분석 SDK 없음 | `android/app/build.gradle.kts` 의존성에 해당 라이브러리 없음 |
| 통신 HTTPS | 릴리즈 빌드는 cleartext 차단(`network_security_config`), 운영 nginx는 TLS 종료 — 새 서버에서 다시 구성 필요 |

## 앱 안내 문구와의 일치

- [x] 홈 위치 권한 안내: "대략적인 위치(약 1km 범위)를 날씨 조회에만 사용해요. 날씨 제공을 위해 OpenWeatherMap에 전달되며, 계정과 연결해 저장하지 않아요."
- [ ] 가상 피팅 화면에 "전신 사진이 외부 AI 서비스(Hugging Face)로 전송돼요" 안내 추가를 권장(현재 앱 화면에는 없음). 처리방침에는 이미 명시했음
