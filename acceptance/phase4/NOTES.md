# Phase 4 인수 테스트 초안 메모

대상: FR-23, FR-24 (17건). 같은 Phase의 앱 화면 요구사항(`verify = "human"`)은 사람이 확인하므로 테스트를 만들지 않았다.

## 옮길 때
- `acceptance_draft/phase4/test_fr23.py`, `test_fr24.py`, 빈 `__init__.py` → `acceptance/phase4/`.
  두 파일은 저장소 루트를 `Path(__file__).parents[2]`로 찾으므로 `acceptance/phase4/`로 옮겨도 그대로 맞다.
- **가짜 AI 서버는 바꾸지 않았다.** FR-23은 엔드포인트를 지우는 일이고 FR-24는 스택 없이 도는 오프라인 도구라 새 AI 엔드포인트가 필요 없다.
  그래서 `acceptance_draft/fake_ai/`와 `acceptance_draft/test_fake_ai_phase4.py`는 만들지 않았다.
  하네스의 `POST /ai/shopping/recommend`(가짜)는 남겨 둔다. 기존 엔드포인트를 지우지 말라는 규칙 때문이고, 백엔드가 더 이상 부르지 않으니 해가 없다.
  하네스를 정리하고 싶으면 사람이 따로 지운다.
- 확인한 것: `python3 -m pytest --collect-only -q -p acceptance.conftest acceptance_draft/phase4`에서 17건이 수집되고 표시 검사(`fr` 마커)를 통과함.
  스택 테스트는 구현이 없어 돌리지 않았다. 스택이 필요 없는 테스트(FR-23 저장소 검사, FR-24 전부)도 이 환경에서는 명령 승인이 나지 않아 실행하지 못했다.

## FR-23 쇼핑 도우미 제거
- `POST /api/shopping/recommend`는 인증된 사용자에게 404 (본문 있음·없음 둘 다). 백엔드가 `/ai/shopping/recommend`를 부르지 않는다.
- '진단 기능은 건드리지 않는다' → `GET /api/diagnosis`가 200, 빈 목록.
  '다른 API 인수 테스트가 모두 통과한다'는 CI가 끝난 요구사항의 인수 테스트를 함께 돌려 확인한다. 여기서는 옷 등록·목록만 간단히 다시 본다.
- '저장소에서 참조가 남지 않는다'는 텍스트 검사다. 표식: `shopping/recommend`, `shopping_recommend`(k6 지표 `latency_ai_shopping_recommend`),
  `ShoppingRecommend`(백엔드·앱·AI 서버 요청·응답 클래스), `recommendShopping`(`AiServerClient`), `ShoppingApi`(앱 Retrofit).
  그리고 쇼핑 도우미 파일(백엔드 `domain/shopping/Shopping{Controller,Service,RecommendRequest}.java`, `ai-server/app/routers/shopping.py`,
  앱 `data/api/ShoppingApi.kt`, `data/repository/ShoppingRepository.kt`)이 없어야 한다. k6(`performance/*.js`)는 따로 한 번 더 본다.
- 검사에서 뺀 곳: `.git`, 빌드 산출물, `acceptance/`, `acceptance_draft/`, `docs/requirements/`, `docs/adr/`(제거 ADR이 옛 경로를 말할 수 있음),
  `docs/devlog.md`(읽기 전용 기록), `docs/AUDIT.md`(특정 시점의 감사 기록). AUDIT.md도 고쳐야 한다고 보면 `EXCLUDED_PATHS`에서 빼면 된다.
- **요구사항과 지금 코드가 다른 점 (사람 확인 권장)**: detail은 "앱 ui/shopping을 지운다"고 하지만, 지금 `ui/shopping/ShoppingScreen`·`ShoppingViewModel`은
  하단 '쇼핑' 탭의 쇼핑 홈(살까 말까 비교, 최근 확인 기록)이다 (`Navigation.kt`의 `Route.SHOPPING`). 쇼핑 도우미 화면은 이미 이 화면으로 바뀌었다고 보고,
  그 두 파일은 지우라고 요구하지 않았다. 앱 쪽 쇼핑 도우미 흔적은 `ShoppingApi`·`ShoppingRepository`·`ShoppingRecommend*` 모델이다.
- `HomeService`/`HomeSummaryResponse`도 `recommendShopping`·`AiShoppingRecommendResponse`를 쓴다. 표식 검사에 걸리므로 구현에서 함께 정리해야 한다
  (홈 요약의 `shoppingRecommendation` 필드도 쇼핑 도우미 API 참조로 본다).
- AI 서버 `recommend_service.py`의 `shopping_suggestions`(상황 추천의 검색 제안)는 쇼핑 도우미가 아니므로 표식에 넣지 않았다.
  쇼핑몰 검색어 생성 프롬프트 모듈(FR-25 재사용)의 이름도 검사하지 않는다.

## FR-24 평가 도구
요구사항에 실행 방법·리포트 형식이 없어 아래 계약을 가정했다. 구현이 다르게 정하면 이 부분만 맞추면 된다.
- 실행: 저장소 루트에서 `python3 evals/similarity/run.py --out <경로>`, `python3 evals/combos/run.py --out <경로>`.
  다른 인자 없이 돌리면 저장소에 둔 샘플 정답 쌍 파일·샘플 조합 파일과 저장된 벡터 캐시를 쓴다 (CI의 '캐시 기반 작은 샘플'). 성공 시 종료 코드 0.
- 리포트는 텍스트 한 파일(마크다운 표 권장). 요구사항 낱말을 그대로 담는지 본다:
  similarity = 기준값 `0.70`~`0.95`(0.05 간격) 각각, `precision`, `recall`, `precision@3`(대소문자 무시), `오판`.
  combos = `일치율`, `불일치`.
  정확한 수치는 샘플 파일을 구현이 만들기 때문에 단정하지 않았다. 대신 같은 입력을 두 번 돌리면 리포트가 같아야 한다(규칙 계산이므로).
  리포트에 실행 시각을 넣으면 이 검사가 깨진다 — 시각을 넣으려면 주입받게 하거나 빼야 한다.
- '--live 없이 외부 API를 호출하지 않는다': `OPENAI_API_KEY`·`OPENAI_BASE_URL`을 지우고, `socket.connect`·`getaddrinfo`를 막아 기록하는
  `sitecustomize.py`를 `PYTHONPATH` 맨 앞에 둔 채 돌린다. 기록이 하나라도 있으면 실패다. combos도 같은 조건으로 확인한다.
  Python 밖(예: gradle로 Java 엔진 실행)의 네트워크는 이 방법으로 막히지 않는다.
- combos가 FR-09 규칙 엔진(백엔드 Java)을 어떻게 쓰는지는 정하지 않았다. 규칙 파일을 같이 읽는 방식이든 다른 방식이든
  위 명령 하나로, 네트워크 없이, 300초 안에 끝나야 한다.
