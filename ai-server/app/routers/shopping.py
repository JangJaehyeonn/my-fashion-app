from fastapi import APIRouter

from app.schemas.shopping import ShoppingRecommendRequest, ShoppingRecommendResponse
from app.services.shopping_service import recommend_shopping

router = APIRouter()


@router.post(
    "/ai/shopping/recommend",
    response_model=ShoppingRecommendResponse,
    response_model_by_alias=True,
)
async def recommend_shopping_route(request: ShoppingRecommendRequest):
    return await recommend_shopping(request)
