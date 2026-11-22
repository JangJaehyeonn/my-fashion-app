# Phase 관문: 재현이 할 일

파이프라인이 Phase를 넘길 때 사람이 해야 하는 일은 끝난 Phase의 앱 화면 확인 하나다.
인수 테스트 초안 PR은 CI 스모크와 Sonnet 대조(요구사항 ↔ 테스트)를 통과하면 자동으로 머지되고 바로 구현이 시작된다 (2026-11-22부터).
자동 대조가 FAIL이거나 CI가 실패하면 초안 PR에 needs-human이 붙는다. 그때만 아래 체크리스트로 검토하고 직접 머지한다.
현황판 이슈(`status-board` 라벨)의 "재현이 할 일"에 해야 할 PR과 막힌 항목이 모여 있다.

## Phase가 끝났을 때 (현황판에서 그 Phase가 전부 ✅)
- [ ] 그 Phase의 `human-verify` 이슈를 하나씩 에뮬레이터로 확인했다 (아래 순서)
- [ ] 문제가 있으면 이슈를 다시 열고 무엇이 다른지 코멘트, `ready` 라벨을 붙였다 (feeder가 다시 집는다)
- [ ] 다음 Phase 인수 테스트 PR(`test(acceptance): Phase N …`)을 검토했다
  - [ ] 요구사항의 인수 기준이 빠짐없이 테스트로 옮겨졌다 (`acceptance/phaseN/test_frXX.py` 하나에 FR 하나)
  - [ ] 기대값이 요구사항 문장과 같다 (테스트가 요구사항보다 느슨하거나 엄격하지 않다)
  - [ ] 가짜 AI(`acceptance/fake_ai/app.py`)에 추가된 엔드포인트가 결정적이고, 실제 AI 서버 형식과 같다
  - [ ] `NOTES.md`의 가정이 맞다
  - [ ] CI의 acceptance 잡은 실패해도 된다 (아직 구현 전). 스모크·protected-paths는 통과해야 한다
- [ ] PR을 머지했다 → 그 Phase 이슈가 `ready`가 되고 feeder가 첫 이슈를 집는다

## 에뮬레이터 확인 순서 (human-verify 이슈마다)
1. 로컬에서 dev 최신을 받는다: `git switch dev`, `git pull`
2. 백엔드·AI 서버·DB를 로컬로 띄운다 (docs/devlog.md의 로컬 실행 방법, 컨테이너는 127.0.0.1)
3. `adb reverse tcp:8080 tcp:8080` (에뮬레이터를 재시작했으면 다시)
4. Android Studio에서 debug 빌드 설치
5. 이슈의 인수 기준을 위에서부터 하나씩 해 보고, `docs/design/ui-spec.md`의 해당 화면 항목(순서, 문구, 배지 조건, 44dp 터치 영역)과 대조한다
6. 통과하면 이슈에 "확인 완료" 코멘트. 실패하면 이슈를 다시 열고 스크린샷과 다른 점을 적는다

## 확인용 로컬 Claude Code 프롬프트
에뮬레이터 확인 전에 로컬 Claude Code에 붙여 넣으면 준비와 대조표를 만들어 준다.

```
dev 최신을 받고, docs/phase-gate.md의 "에뮬레이터 확인 순서" 1~4번을 준비해줘 (.env는 읽지 말고, 컨테이너는 127.0.0.1에만).
그다음 Phase <N>의 human-verify 이슈(gh issue list --label phase-<N> --label human-verify --state all)마다
인수 기준과 docs/design/ui-spec.md의 해당 화면 항목을 나란히 놓은 체크리스트를 만들어줘.
화면은 내가 직접 보고 체크할 거니까 코드는 고치지 마.
```

## 처음 한 번 (파이프라인 켜기)
1. 11월 로컬 작업 브랜치를 푸시하고 dev에 머지한다 (파이프라인 설정 PR보다 먼저)
2. 파이프라인 설정 PR(`chore/pipeline-setup`)을 머지한다
3. 저장소 설정: 기본 브랜치 dev, 시크릿 `CLAUDE_CODE_OAUTH_TOKEN`, 변수 `WEARON_MAX_AUTO_PHASE=4`, dev 브랜치 보호 규칙
4. `python3 scripts/create_labels.py`
5. `python3 scripts/create_issues.py --phase 1`
6. `gh workflow run acceptance-tests.yml -f phase=1` → 나온 PR을 위 체크리스트로 검토하고 머지

## Phase 5 (출시 후)
자동으로 시작하지 않는다. 출시하고 지표(주간 리포트, FR-14)를 본 뒤 시작하려면:
`python3 scripts/create_issues.py --phase 5`, `gh workflow run acceptance-tests.yml -f phase=5`
