from fastapi import APIRouter, File, Form, HTTPException, UploadFile

from app.schemas.vton import VtonResponse
from app.services.vton_service import virtual_try_on

router = APIRouter()

_ALLOWED_TYPES = {"image/jpeg", "image/png", "image/webp", "image/gif"}
_MAX_SIZE = 10 * 1024 * 1024  # 10MB


@router.post("/ai/vton", response_model=VtonResponse, response_model_by_alias=True)
async def try_on(
    person_image: UploadFile = File(...),
    garment_image: UploadFile = File(...),
    garment_desc: str = Form(""),
):
    for image in (person_image, garment_image):
        if image.content_type not in _ALLOWED_TYPES:
            raise HTTPException(status_code=400, detail="지원하지 않는 이미지 형식입니다. (jpeg, png, webp, gif만 허용)")

    person_bytes = await person_image.read()
    garment_bytes = await garment_image.read()
    if len(person_bytes) > _MAX_SIZE or len(garment_bytes) > _MAX_SIZE:
        raise HTTPException(status_code=400, detail="이미지 크기는 10MB 이하여야 합니다.")

    try:
        result_base64 = await virtual_try_on(person_bytes, garment_bytes, garment_desc)
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"가상 피팅 생성에 실패했습니다: {e}")

    return VtonResponse(result_image_base64=result_base64)
