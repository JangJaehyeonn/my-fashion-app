## 요약
<!-- 무엇을 왜 바꿨는지 2~3줄 -->

Closes #

## 요구사항
- 요구사항 ID: FR-
- 인수 기준 충족 여부 (이슈의 체크리스트를 그대로 옮겨 체크하고, 근거 파일:줄을 적는다)
  - [ ]

## 쓴 기술과 선택 이유
<!-- 새 라이브러리·구조·규칙 파일 결정이 있으면 docs/adr/ 문서 링크 -->

## 검증
- [ ] `python3 scripts/check.py` (아래를 한 번에 실행)
- [ ] `backend/gradlew -p backend test --no-daemon`
- [ ] `python3 -m pytest -q ai-server/tests`
- [ ] `android/gradlew -p android assembleDebug testDebugUnitTest --no-daemon`
- [ ] `WEARON_ACTIVE_FRS=FR-xx python3 -m pytest -q acceptance`
- [ ] 앱 화면이면: `docs/design/ui-spec.md`와 대조한 점 (사람 확인은 `human-verify` 이슈에서)

## 보호 경로
- [ ] `acceptance/`, `docs/requirements/`, `docs/design/`, `.github/`, `.claude/`, `CLAUDE.md`, `scripts/`를 수정하지 않았다
