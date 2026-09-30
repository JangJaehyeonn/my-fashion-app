from fastapi import APIRouter

from app.schemas.outfit import (
    ClosetRecommendRequest,
    ClosetRecommendResponse,
    SituationRecommendRequest,
    SituationRecommendResponse,
)
from app.services.recommend_service import recommend_outfit_by_closet, recommend_outfit_by_situation

router = APIRouter()


@router.post(
    "/ai/outfits/recommend/situation",
    response_model=SituationRecommendResponse,
    response_model_by_alias=True,
)
async def recommend_outfit_situation(request: SituationRecommendRequest):
    return await recommend_outfit_by_situation(request)


@router.post(
    "/ai/outfits/recommend/closet",
    response_model=ClosetRecommendResponse,
    response_model_by_alias=True,
)
async def recommend_outfit_closet(request: ClosetRecommendRequest):
    return await recommend_outfit_by_closet(request)
