"""FR-11 살까 말까 대표 코디·이유 문장 요청/응답 (camelCase)."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

MAX_CANDIDATES = 50
MAX_SIMILAR = 3


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class PurchaseItem(CamelModel):
    category: str
    color: str | None = None
    name: str | None = None
    fit: str | None = None
    material: str | None = None
    pattern: str | None = None
    season: str | None = None


class SimilarClothes(CamelModel):
    id: str
    name: str | None = None
    similarity: float
    wear_count: int = 0
    last_worn_at: str | None = None


class Candidate(CamelModel):
    clothes_ids: list[str] = Field(default_factory=list)
    # Short "카테고리 색 이름" per closet piece, same order as clothes_ids (for the prompt only)
    labels: list[str] = Field(default_factory=list)


class PurchaseExplainRequest(CamelModel):
    item: PurchaseItem
    verdict: Literal["BUY", "THINK", "SIMILAR"]
    plus: int
    combo_count_before: int
    combo_count_after: int
    similar: list[SimilarClothes] = Field(default_factory=list, max_length=MAX_SIMILAR)
    distribution: dict[str, int] = Field(default_factory=dict)
    candidates: list[Candidate] = Field(default_factory=list, max_length=MAX_CANDIDATES)


class Pick(CamelModel):
    clothes_ids: list[str]
    label: str


class SimilarNote(CamelModel):
    id: str
    note: str


class PurchaseExplainResponse(CamelModel):
    picks: list[Pick]
    headline: str
    reasons: list[str]
    gap: str | None = None
    similar_notes: list[SimilarNote] = Field(default_factory=list)
