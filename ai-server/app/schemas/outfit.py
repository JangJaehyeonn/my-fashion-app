from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import List


class ClothesItem(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    id: str
    category: str
    color: str
    pattern: str
    season: str
    style_tag: str


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
