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
