# 인수 테스트 하네스

요구사항(`docs/requirements/issues.toml`)의 인수 기준을 HTTP로 검증한다. 이 폴더는 보호 경로라 사람 승인으로만 바뀐다.

- 실행: 저장소 루트에서 `WEARON_ACTIVE_FRS=FR-09,FR-11 python3 -m pytest -q acceptance` (Docker 필요)
- 스택: `compose.yml` (postgres+pgvector, redis, 백엔드, 가짜 AI). 포트는 127.0.0.1의 15432·18000·18080.
- 디버깅: `WEARON_ACCEPTANCE_KEEP=1`이면 끝나도 스택을 내리지 않는다. 띄워 둔 스택에 붙으려면 `WEARON_ACCEPTANCE_URL=http://127.0.0.1:18080`.
- 가짜 AI(`fake_ai/app.py`): 실제 AI 서버와 같은 경로·형식. 옷 분류는 업로드 파일 이름(`top_navy_knit.jpg`)으로 정해진다.
  `/__control`로 다운·지연·잘못된 응답을 흉내내고, `/__calls`로 백엔드가 보낸 요청을 확인한다.
- 테스트 규칙: 함수마다 `@pytest.mark.fr("FR-xx")`. 하네스 점검은 `@pytest.mark.smoke`(항상 실행).
- Phase별 테스트는 `acceptance/phase<N>/test_fr<번호>.py`. 인수 테스트 초안 워크플로가 만들고 재현이 PR로 승인한다.
