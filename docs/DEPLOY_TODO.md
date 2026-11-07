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
