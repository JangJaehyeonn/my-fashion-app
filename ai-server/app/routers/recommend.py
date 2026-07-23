from fastapi import APIRouter

from app.schemas.outfit import (
    RecommendRequest,
    RecommendResponse,
    SituationRecommendRequest,
    SituationRecommendResponse,
)
from app.services.recommend_service import recommend_outfits, recommend_outfit_by_situation

router = APIRouter()


@router.post("/ai/outfits/recommend", response_model=RecommendResponse, response_model_by_alias=True)
async def recommend_outfit(request: RecommendRequest):
    return await recommend_outfits(request)


@router.post(
    "/ai/outfits/recommend/situation",
    response_model=SituationRecommendResponse,
    response_model_by_alias=True,
)
async def recommend_outfit_situation(request: SituationRecommendRequest):
    return await recommend_outfit_by_situation(request)
