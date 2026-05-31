from fastapi import APIRouter

from app.schemas.outfit import RecommendRequest, RecommendResponse
from app.services.recommend_service import recommend_outfits

router = APIRouter()


@router.post("/ai/outfits/recommend", response_model=RecommendResponse, response_model_by_alias=True)
async def recommend_outfit(request: RecommendRequest):
    return await recommend_outfits(request)
