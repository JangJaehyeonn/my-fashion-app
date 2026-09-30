from typing import Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

ClothesCategory = Literal["TOP", "BOTTOM", "SHOES", "OUTER", "ETC"]


class ClothesClassifyResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    category: ClothesCategory
    color: str
    name: str
