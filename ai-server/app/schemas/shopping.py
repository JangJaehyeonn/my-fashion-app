from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import List, Optional

from app.schemas.outfit import BodyProfile


class ShoppingRecommendRequest(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    budget: int
    situation: str
    body_profile: Optional[BodyProfile] = None


class ShoppingItemSuggestion(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    item: str
    reason: str
    estimated_price: int
    site: str
    search_keyword: str


class ShoppingRecommendResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    items: List[ShoppingItemSuggestion]
    total_estimated_price: int
    usage_tip: str
