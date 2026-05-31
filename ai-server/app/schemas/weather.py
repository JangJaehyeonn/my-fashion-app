from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class WeatherResponse(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)

    temperature: float
    condition: str
    humidity: int
    wind_speed: float
