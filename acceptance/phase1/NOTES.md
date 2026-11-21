# Phase 1 인수 테스트 초안 메모

대상: FR-01, FR-03, FR-04, FR-05. 앱 화면 요구사항 FR-02, FR-06은 사람이 확인하므로 테스트가 없다.

## 옮길 때
- `acceptance_draft/phase1/test_fr*.py` → `acceptance/phase1/`. `acceptance/smoke/`처럼 빈 `acceptance/phase1/__init__.py`가 필요하다
  (테스트가 `from acceptance.conftest import ...`로 하네스의 `token_for`·`User`·`Stack`을 가져온다).
- 가짜 AI 서버는 바꾸지 않았다. Phase 1은 기존 `/ai/clothes/classify`, `/ai/outfits/recommend/closet`과
  `/__control`(down·bad), `/__calls`만 쓴다. 그래서 `acceptance_draft/fake_ai/app.py`와 `test_fake_ai_phase1.py`도 만들지 않았다.

## 요구사항에 이름이 없어 정한 것 (구현은 이 이름을 따른다)

| 요구사항 | 정한 것 |
|---|---|
| FR-01 분류 실패 표시 | 옷 응답 필드 `classificationFailed` (boolean). 성공 분류면 `false` |
| FR-01 미분류 카테고리 | `category: "UNCLASSIFIED"` (ETC와 구분) |
| FR-01 직접 수정 API | `PATCH /api/clothes/{id}` 본문 `{category, color, name}`, 200 |
| FR-01 재분류 API | `POST /api/clothes/{id}/reclassify`, 성공 200 + 옷 응답, AI 다운 503 + `message` |
| FR-04 이력 테이블 컬럼 | `consent_records(user_id, consent_type, version, agreed_at, revoked_at)` |
| FR-05 가입 동의 필드 | 기존 `ageOver14`에 `termsAgreed`, `privacyAgreed` 추가. 셋 다 `true`여야 가입 |
| FR-05 소셜 첫 가입 | 기존 `POST /api/users/me/age-confirmation`이 같은 세 필드를 JSON 본문으로 받는다 |

## 해석·가정
- FR-01 옷 등록 성공은 **201**이다 (요구사항 문구. 지금은 200).
- FR-01 '5xx'는 가짜 AI의 `fail_` 접두어(500)로 확인한다. 타임아웃은 백엔드 기본 읽기 타임아웃이 30초라 인수 테스트에서 빼고,
  단위 테스트로 확인하길 권한다.
- FR-01 미분류일 때 색상 값의 표현(null, 빈 문자열 등)은 정해져 있지 않아 단정하지 않는다. 카테고리와 실패 표시만 본다.
- FR-01 재분류 성공 기대값: 저장된 사진의 파일 이름은 구현이 정하므로, 백엔드가 가짜 AI에 보낸 파일 이름(`/__calls`)을
  가짜 AI 분류 규칙에 넣은 결과와 같은지 본다. 재분류는 분류 API를 정확히 1번 부른다.
- FR-01 미분류 옷만 있는 코디 추천: 400(옷장 비어 있음 등)이든 200이든 5xx만 아니면 된다. 가짜 AI가 받은 옷 목록과 응답 코디에
  미분류 옷 ID가 없어야 한다. 분류된 옷과 섞인 옷장에서는 가짜 AI가 분류된 옷 목록만 정확히 받는다.
- FR-03 비밀값 이름: `SECRET|PASSWORD|TOKEN|API_KEY|ACCESS_KEY|PRIVATE_KEY`가 들어간 환경 변수. 빈 기본값(`${X:}`)은 허용하고,
  값이 있는 기본값만 실패로 본다. 현재 `application.yml:10 ${DB_PASSWORD:fashionapp}`가 걸린다.
  `JWT_SECRET`은 빈 기본값도 안 된다 (`${JWT_SECRET}`이어야 빠졌을 때 기동이 멈춘다).
- FR-03 기동 실패: 하네스 백엔드 이미지(`wearon-acceptance-backend`)를 스택 네트워크(`wearon-acceptance_default`)에서
  JWT_SECRET만 빼고 `docker run`으로 띄운다. 로컬 프로필과 운영 프로필(운영 검사를 통과하는 https 값 포함) 두 경우 모두
  종료 코드가 0이 아니고 로그에 `JWT_SECRET`이 있어야 한다. 180초 안에 끝나지 않으면 실패다.
- FR-04 테스트 사용자(`make_user`)는 SQL로 만들어 이력이 없으므로, 그 사용자의 이력 건수 = 가상 피팅 동의 이력 건수로 본다.
  이력의 종류 값(VTON 등)은 단정하지 않는다.
- FR-04 '응답 형식 유지' = 기존 `UserResponse` 필드가 모두 남아 있다 (새 필드 추가는 허용). 동의 시 `vtonConsentAt`이 값, 철회 시 null.
- FR-05 '약관 동의 전 소셜 계정'은 `age_confirmed_at`이 비어 있는 소셜 사용자다. 허용 범위는 지금 `AgeGateInterceptor`와 같다
  (프로필 조회 200, 옷 목록·상황 추천·가상 피팅 동의는 403). 약관·방침 동의 없이 만 14세 확인만 보내면 상태 코드는 단정하지 않고
  (5xx만 아니면 됨) 여전히 막혀 있어야 한다.
- FR-05 이 기능 이전에 만 14세 확인을 마친 사용자(하네스 `make_user`, 약관 이력 없음)는 계속 API를 쓸 수 있어야 한다.
  그렇지 않으면 다른 모든 인수 테스트가 403으로 깨진다.
- FR-05 가입·소셜 확인 뒤 이력은 3건, 종류 3가지, 철회 0건이다. `/terms` 본문에 '이용약관' 문구가 있어야 한다.
