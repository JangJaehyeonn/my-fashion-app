from typing import Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

ClothesCategory = Literal["TOP", "BOTTOM", "SHOES", "OUTER", "ETC"]
ClothesSeason = Literal["SPRING", "SUMMER", "FALL", "WINTER"]


class ClothesClassifyResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    category: ClothesCategory
    color: str
    name: str
    # FR-07 attributes. None (JSON null) means unknown; the keys are always present in the response.
    fit: str | None = None
    material: str | None = None
    pattern: str | None = None
    season: ClothesSeason | None = None
