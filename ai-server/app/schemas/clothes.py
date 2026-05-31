from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ClothesClassifyResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    category: str
    color: str
    pattern: str
    season: str
    style_tag: str
