from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import List, Optional


class ClothesItem(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    id: str
    category: Optional[str] = None
    color: Optional[str] = None
    pattern: Optional[str] = None
    season: Optional[str] = None
    style_tag: Optional[str] = None


class WeatherInfo(BaseModel):
    temperature: float
    condition: str


class RecommendRequest(BaseModel):
    weather: WeatherInfo
    clothes: List[ClothesItem]


class RecommendedOutfit(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    clothes_ids: List[str]
    reason: str
    style_tag: str


class RecommendResponse(BaseModel):
    outfits: List[RecommendedOutfit]


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


class SituationOutfitSuggestion(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    description: str
    reason: str
    style_tag: str


class SituationRecommendResponse(BaseModel):
    outfits: List[SituationOutfitSuggestion]
