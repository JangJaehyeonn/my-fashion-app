from fastapi import APIRouter, File, HTTPException, UploadFile

from app.schemas.diagnosis import DiagnosisResponse
from app.services.diagnosis_service import diagnose_outfit_image

router = APIRouter()

_ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp", "image/gif"}
_MAX_SIZE = 10 * 1024 * 1024  # 10MB


@router.post("/ai/diagnosis", response_model=DiagnosisResponse, response_model_by_alias=True)
async def diagnose(image: UploadFile = File(...)):
    if image.content_type not in _ALLOWED_TYPES:
        raise HTTPException(status_code=400, detail="지원하지 않는 이미지 형식입니다. (jpeg, png, webp, gif만 허용)")

    image_bytes = await image.read()
    if len(image_bytes) > _MAX_SIZE:
        raise HTTPException(status_code=400, detail="이미지 크기는 10MB 이하여야 합니다.")

    return await diagnose_outfit_image(image_bytes, image.content_type)
