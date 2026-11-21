# 0001. 구현은 Opus, 리뷰는 Sonnet, 검증은 CI와 HTTP 인수 테스트, 사람은 Phase 관문만

- 상태: 채택
- 날짜: 2026-11-21
- 관련: docmill ADR 0003 (같은 구조의 원본)

## 배경
Wearon은 1인 개발이고, 출시(Phase 1~4)까지 남은 요구사항이 26개다. 사람이 PR을 하나씩 리뷰·머지하면 그게 병목이 된다.
docmill에서 쓰는 자동 파이프라인(요구사항 → 이슈 → Claude Code 구현 → CI → 리뷰 → 자동 머지)을 가져오되, Wearon의 조건에 맞게 바꿔야 했다.
- 구독 토큰(`CLAUDE_CODE_OAUTH_TOKEN`)만 쓰고 유료 API 키는 쓰지 않는다. 다른 회사 AI 도구도 쓰지 않는다.
- 백엔드(Spring Boot)·AI 서버(FastAPI)·앱(Android) 세 스택이 있고, 앱 화면은 자동으로 검증하기 어렵다.
- 기본 브랜치는 `dev`다 (`master`는 배포 이력용).

## 결정
- 요구사항(`docs/requirements/issues.toml`) → GitHub 이슈 → **Opus**(`claude-opus-5-5`)가 구현 → PR(dev 대상) → 자동 머지 예약.
- **검증은 CI**: backend(gradle test), ai-server(pytest), android(assembleDebug + testDebugUnitTest), acceptance(HTTP 인수 테스트), protected-paths(보호 경로·skip 금지·FR 제목).
- **리뷰는 ai-review 잡**: CI가 통과한 뒤 **Sonnet**(`claude-sonnet-5-5`)이 읽기 전용으로 리뷰하고 커밋 상태 `ai-review`를 남긴다.
  인수 기준마다 근거 `파일:줄`이 없으면 FAIL. FAIL이면 Opus가 고친다(review-fix, 최대 3회). CI 실패·충돌도 Opus가 고친다(각 3회).
- 인수 테스트는 HTTP 수준(pytest + httpx)이고, AI 서버는 **결정적 가짜 서버**(`acceptance/fake_ai`)로 대신한다. 실제 OpenAI는 테스트에서 부르지 않는다.
- 앱 화면 요구사항(`verify = "human"`)은 인수 테스트 대신 사람이 에뮬레이터로 확인한다. 대신 ViewModel 단위 테스트를 필수로 하고, AI 리뷰가 `docs/design/ui-spec.md`와 대조한다.
- 보호 경로(`acceptance/`, `docs/requirements/`, `docs/design/`, `.github/`, `.claude/`, `CLAUDE.md`, `scripts/`)는 CODEOWNERS + CI 가드 + Claude 권한 거부로 지킨다.
- 사람의 관문은 Phase마다 두 가지: 인수 테스트 초안 PR 승인, 앱 화면 확인(`docs/phase-gate.md`). Phase 5(출시 후)는 자동으로 시작하지 않는다(`WEARON_MAX_AUTO_PHASE=4`).

## docmill에서 바꾼 점과 이유
| 항목 | docmill | Wearon | 이유 |
| --- | --- | --- | --- |
| 구현 모델 | Sonnet 기본, `opus` 라벨·3번째 시도만 Opus | 항상 Opus | 세 스택을 오가는 변경이 많아 첫 시도 성공률을 우선. 라벨 조건식을 없애 단순하게 |
| 리뷰 | CodeRabbit (외부) | ai-review (Sonnet, 같은 구독) | 다른 회사 도구를 쓰지 않기로 함. CodeRabbit은 리뷰·재리뷰 대기와 스레드 해결 처리 때문에 PR당 정체가 길었다 |
| 리뷰 결과 전달 | CodeRabbit 승인 | 커밋 상태 + 숨은 마커(`<!-- ai-review:PASS:<sha> -->`) | 같은 SHA 한 번만 리뷰, 마커가 없으면 error로 두고 사람에게 넘김(자동 수정 안 돎) |
| 리뷰 → 수정 연결 | workflow_run | ai-review가 `review-fix.yml`을 PR 번호로 직접 실행 | workflow_run으로 시작한 실행은 브랜치가 기본 브랜치로 바뀌어 다음 workflow_run에 PR 브랜치를 넘길 수 없다 |
| 인수 테스트 | MCP 도구 호출 | HTTP + 가짜 AI 서버(docker compose) | Wearon은 웹 API 서비스다 |
| Stop 훅 | `scripts/check.py` | 없음 | Gradle 빌드가 느려 로컬 작업이 매번 막힌다. CI가 최종 관문 |
| 대상 브랜치 | main | dev | 저장소 관례 |
| 릴리스 | release.yml | 없음 | 앱 출시는 Play Console에서 사람이 한다 |

## 같은 회사 모델의 자기 채점 위험과 완화책
구현(Opus)과 리뷰(Sonnet)가 같은 회사 모델이라 같은 맹점을 공유할 수 있다. 그래서 리뷰 판단에만 기대지 않는다.
- **합격 기준은 사람이 승인한 인수 테스트**다. 리뷰는 그 위의 두 번째 관문이다.
- 리뷰 프롬프트는 의견이 아니라 **증거**를 요구한다: 기준마다 `파일:줄`. 근거가 없으면 FAIL.
- 리뷰 잡은 읽기 전용(Read/Grep, `gh pr view/diff`, 코멘트 API)이고 파일 수정·푸시·머지 권한이 없다.
- 판정·숫자는 규칙 코드가 정하고 LLM은 문장만 쓴다(CLAUDE.md 설계 원칙). 리뷰는 이 원칙 위반을 FAIL로 본다.
- 보호 경로 가드와 skip 금지는 CI(결정적 스크립트)가 막는다. 모델 판단에 맡기지 않는다.
- 앱 화면은 사람이 본다.

## 검토한 대안
| 대안 | 장점 | 단점 | 버린 이유 |
| --- | --- | --- | --- |
| CodeRabbit 유지 | 다른 회사 모델이라 시각이 다름 | 느림, 외부 도구 | 사용자 결정(다른 회사 도구 미사용) |
| 모든 PR을 사람이 승인 | 안전 | 병목 | 자동화 목표와 반대 |
| 리뷰 없이 CI만 | 빠름 | 요구사항 밖 기능·규칙 위반을 못 잡음 | 인수 테스트가 못 보는 영역이 있다 |
| 앱 화면도 자동 UI 테스트 | 사람 손이 덜 감 | 에뮬레이터 CI가 느리고 불안정 | 출시 전 화면 수가 적어 사람 확인이 싸다 |

## 결과
- 사람은 Phase마다 인수 테스트 PR 하나를 승인하고 앱 화면을 확인한다.
- 인수 테스트의 품질이 전체 품질을 좌우하므로, 테스트 PR 승인은 꼼꼼히 해야 한다.
- 다시 볼 조건: ai-review가 놓친 결함이 dev에 두 번 이상 들어오면 리뷰 기준을 강화하거나 사람 승인을 일부 PR에 되돌린다.
