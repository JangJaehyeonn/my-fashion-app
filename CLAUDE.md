# Wearon 작업 규칙 (Claude Code용)

이 저장소는 사람 개입을 최소화한 자동 개발 파이프라인으로 운영된다.
Claude Code는 이 문서를 최우선 규칙으로 따른다. 지난 개발 기록은 `docs/devlog.md`에 있다 (읽기 전용 참고 자료).

## 프로젝트 한 줄 요약
잘 사게 도와주는 AI 옷장 앱. 대표 숫자는 **+N 코디**(이 옷을 사면 내 옷장으로 만들 수 있는 코디가 N개 늘어난다).
핵심 기능 세 가지: **살까 말까**(매장에서 찍은 옷을 내 옷장과 비교), **오늘 뭐 입지**(날씨·동선·세탁 상태를 반영한 옷장 코디),
**이 코디 내 옷으로**(출시 후, Phase 5).

## 기술 스택
- 앱: Android, Kotlin, Jetpack Compose (`android/`, 빌드는 `android/gradlew`)
- 백엔드: Spring Boot 3, Java 17, JPA, PostgreSQL(+pgvector), Redis (`backend/`)
- AI 서버: FastAPI, Python 3.11, OpenAI (`ai-server/`). OpenAI 키는 AI 서버에만 있다.
- 인수 테스트: pytest + httpx, 가짜 AI 서버 (`acceptance/`)
- 결정 배경은 `docs/adr/`를 읽고, 새 결정은 ADR로 남긴다.

## 작업 흐름 (이슈 하나 = PR 하나)
1. 이슈 본문의 **요구사항 ID(FR-xx), 인수 기준, 의존 요구사항**을 먼저 읽는다. 의존 이슈가 닫히지 않았으면 작업하지 않고 이슈에 코멘트를 남긴다.
2. 브랜치: `claude/<이슈번호>-<짧은-영문-설명>` (예: `claude/12-combo-count-engine`). 기준 브랜치는 `dev`.
3. 구현과 단위 테스트를 함께 작성한다 (백엔드 `backend/src/test/`, AI 서버 `ai-server/tests/`, 앱 `android/app/src/test/`).
4. 커밋 전 아래 검사를 저장소 루트에서 **한 줄씩** 실행하고 전부 통과시킨다 (`cd`나 `&&`로 묶지 않는다. 허용 목록과 정확히 맞아야 승인 없이 실행된다).
   ```
   python3 scripts/check.py
   ```
   check.py가 차례로 돌리는 명령 (하나씩 따로 돌려도 된다):
   ```
   backend/gradlew -p backend test --no-daemon
   python3 -m pytest -q ai-server/tests
   android/gradlew -p android assembleDebug testDebugUnitTest --no-daemon
   WEARON_ACTIVE_FRS=FR-xx python3 -m pytest -q acceptance
   ```
   `FR-xx`는 이 이슈의 요구사항 ID다. 비워 두면 check.py가 브랜치 이름에서 이슈를 찾아 '완료된 요구사항 + 이 이슈'로 채운다.
   CI는 이미 끝난 요구사항의 인수 테스트까지 함께 돌린다.
5. 커밋 메시지는 Conventional Commits: `feat: ...`, `fix: ...`, `test: ...`, `docs: ...`, `chore: ...`. 본문 끝에 `Refs #<이슈번호>`.
6. PR 대상은 `dev`. 제목은 `feat(FR-09): 코디 수 엔진`. 본문은 PR 템플릿을 채우고 `Closes #<이슈번호>`를 넣는다.
7. PR을 연 뒤 `gh pr merge --auto --squash <PR번호>`로 자동 머지를 예약한다. 다른 방식의 머지는 하지 않는다.
   CI(backend, ai-server, android, acceptance, protected-paths)와 AI 리뷰(`ai-review`, 읽기 전용 Sonnet)가 모두 통과해야 머지된다.

## 절대 하지 말 것 (보호 규칙)
- `acceptance/`, `docs/requirements/`, `docs/design/`, `.github/`, `.claude/`, `CLAUDE.md`, `scripts/`를 수정하지 않는다. 이 경로는 사람 승인으로만 바뀐다.
  인수 테스트에 필요한 가짜 AI 서버 엔드포인트도 `acceptance/fake_ai/`에 있으므로 구현 PR에서 고치지 않는다 (인수 테스트 초안 PR이 함께 추가한다).
- 테스트를 통과시키려고 테스트 기대값을 바꾸거나 JUnit `@Disabled`, pytest `skip`·`xfail`을 붙이지 않는다. 구현을 고친다.
- 요구사항에 없는 기능을 추가하지 않는다. 필요해 보이면 이슈에 제안 코멘트만 남긴다.
- 비밀값·토큰·개인 정보를 코드, 로그, 테스트 샘플에 넣지 않는다. `.env` 파일을 읽거나 커밋하지 않는다.
- 테스트·CI에서 실제 OpenAI 등 외부 AI를 호출하지 않는다 (목 또는 `acceptance/fake_ai`).
- `dev`·`master`에 직접 푸시하지 않는다.

## 설계 원칙
- AI 호출 경로는 앱 → 백엔드 → AI 서버뿐이다. 앱이 AI 서버를 직접 부르지 않는다.
- **판정과 숫자는 규칙으로 정한다.** +N 코디, 유사도 기준, BUY/THINK/SIMILAR 판정, 예상 착용 횟수, 1회당 가격, 일교차 판단은
  백엔드의 결정적 코드(규칙 파일 포함)가 계산한다. LLM은 후보 중 선택과 문장 작성만 한다. LLM이 실패해도 규칙 기반 기본 문장으로 응답한다.
- 같은 입력이면 같은 결과: 규칙 계산에 현재 시각·난수를 직접 쓰지 않는다 (시계는 주입받는다).
- 레이어는 Controller → Service → Repository. 응답은 기존 공통 응답 포맷(`ApiResponse`)을 쓴다.
- DB: 새 테이블·컬럼은 nullable로 추가하고, `backend/src/main/resources/db/`에 멱등 SQL 스크립트(`IF NOT EXISTS`, 백필 포함)를 같이 둔다. 기존 데이터를 지우거나 바꾸는 변경은 하지 않는다.
- 이벤트(`events`) props에 개인정보(이메일, 닉네임, 사진 경로, 자유 입력 문장)를 넣지 않는다.
- 사용자에게 보이는 문구는 한국어. 컨테이너·개발 서버는 `127.0.0.1`에만 바인딩한다.
- 비밀값은 환경 변수로만 받는다. 기본값에 실제 비밀을 넣지 않는다.

## 앱 화면 규칙
- 화면은 `docs/design/ui-spec.md`를 기준으로 구현한다. 테마는 `ui/theme/Theme.kt`의 `WearonColors`·`WearonShapes`.
- 앱 화면 요구사항(`verify = "human"`, 이슈 라벨 `human-verify`)은 인수 테스트 대신 사람이 에뮬레이터로 확인한다.
  그래서 ViewModel 단위 테스트(`android/app/src/test/`)로 상태 변화와 문구 조건을 꼭 검증한다.
- 터치 영역 44dp 이상, 아이콘 버튼에 contentDescription.

## 알아둘 함정 (docs/devlog.md에서 반복된 것)
- `android/.../ui/navigation/`은 파일명과 내용이 반대다: `Navigation.kt` = Route + NavHost, `AppNavigation.kt` = MainViewModel + NavEvent. 파일명만 보고 고치지 말고 내용을 먼저 연다.
- AI 서버 코드를 바꾸면 Docker 이미지를 다시 빌드해야 반영된다 (`docker compose build ai-server`).
- 에뮬레이터에서 OAuth 로그인은 `adb reverse tcp:8080 tcp:8080`이 필요하고 에뮬레이터 재시작 때 풀린다.
- 기존 운영 DB에는 `users.provider` 등에 옛 제약이 남아 있을 수 있다. 값 종류를 늘릴 때 제약을 확인하는 멱등 SQL을 같이 둔다.
- nginx 설정은 단일 파일 바인드 마운트라 `git pull`만으로는 반영되지 않는다 (`restart nginx` 필요).
- 오래 걸리는 API(가상 피팅 등)는 앱 `LongRunningTimeoutInterceptor`에 경로를 등록해야 10초 타임아웃에 안 걸린다.
- `build.gradle`의 `useJUnitPlatform()`이 빠지면 JUnit5 테스트가 0건으로 조용히 통과한다.

## ADR (결정 기록)
새 의존성 추가, 모듈 구조 변경, 저장 형식·규칙 파일 형식 결정 등 "왜 이렇게 했는지" 설명이 필요한 변경은
`docs/adr/NNNN-짧은-제목.md`를 같은 PR에 추가한다. 형식은 `docs/adr/0000-template.md`를 따른다. AI 리뷰가 누락을 FAIL로 본다.

## 막혔을 때
- "불가능하다·사람 판단이 필요하다"로 미루기 전에 쓰는 라이브러리의 소스·공식 문서를 직접 확인한다. 요구사항 범위 안이고 API가 있으면 구현한다.
- 같은 문제로 3번 시도해도 해결되지 않거나, 요구사항이 모순되거나, 보호 경로 수정이 필요하면 작업을 멈춘다.
- 이슈(또는 PR)에 무엇이 막혔는지, 시도한 것, 필요한 결정을 정리해 코멘트하고 `needs-human` 라벨을 붙인다.

## 코드 스타일
- 코드 식별자·주석은 영어, 사용자에게 보이는 메시지·문서는 한국어.
- 함수는 짧게. Kotlin·Java는 기존 패키지 구조(도메인 단위)를 따르고, Python은 타입 힌트를 빠짐없이 쓴다.
