from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import List, Optional


class WeatherInfo(BaseModel):
    temperature: float
    condition: str


class BodyProfile(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    height: Optional[int] = None
    weight: Optional[int] = None
    body_type: Optional[str] = None
    preferred_style: Optional[str] = None


class SituationRecommendRequest(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    weather: WeatherInfo
    situation: str
    body_profile: Optional[BodyProfile] = None


class ShoppingSuggestion(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    item: str
    site: str
    search_keyword: str


class SituationOutfitSuggestion(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    description: str
    reason: str
    style_tag: str
    shopping_suggestions: List[ShoppingSuggestion] = []


class SituationRecommendResponse(BaseModel):
    outfits: List[SituationOutfitSuggestion]
