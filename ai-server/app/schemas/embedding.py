from pydantic import BaseModel, Field, model_validator

MAX_TEXT_LENGTH = 2000
MAX_BATCH_SIZE = 100


class EmbedRequest(BaseModel):
    """Exactly one of `text` (single) or `texts` (batch)."""

    text: str | None = Field(default=None, min_length=1, max_length=MAX_TEXT_LENGTH)
    texts: list[str] | None = Field(default=None, min_length=1, max_length=MAX_BATCH_SIZE)

    @model_validator(mode="after")
    def exactly_one(self) -> "EmbedRequest":
        if (self.text is None) == (self.texts is None):
            raise ValueError("text 또는 texts 중 하나만 보내 주세요.")
        if self.texts is not None and any(not t or len(t) > MAX_TEXT_LENGTH for t in self.texts):
            raise ValueError(f"texts의 각 항목은 1~{MAX_TEXT_LENGTH}자여야 합니다.")
        return self


class EmbedResponse(BaseModel):
    embedding: list[float]


class EmbedBatchResponse(BaseModel):
    embeddings: list[list[float]]
