from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from typing import List


class SimilarStyleSuggestion(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    style_tag: str
    description: str


class DiagnosisResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    score: int
    feedback: str
    similar_styles: List[SimilarStyleSuggestion]
