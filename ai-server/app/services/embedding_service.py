from app.services.recommend_service import client

EMBEDDING_MODEL = "text-embedding-3-small"
EMBEDDING_DIMENSIONS = 1536


async def embed_texts(texts: list[str]) -> list[list[float]]:
    """Embeds texts with OpenAI, keeping the input order. Raises ValueError on a malformed response."""
    response = await client.embeddings.create(model=EMBEDDING_MODEL, input=texts)
    ordered = sorted(response.data, key=lambda item: item.index)
    vectors = [list(item.embedding) for item in ordered]
    if len(vectors) != len(texts) or any(len(v) != EMBEDDING_DIMENSIONS for v in vectors):
        raise ValueError("OpenAI returned an unexpected embedding shape")
    return vectors
