from fastapi import APIRouter

from app.schemas.embedding import EmbedBatchResponse, EmbedRequest, EmbedResponse
from app.services.embedding_service import embed_texts

router = APIRouter()


@router.post("/ai/embed", response_model=EmbedResponse | EmbedBatchResponse)
async def embed(request: EmbedRequest) -> EmbedResponse | EmbedBatchResponse:
    if request.text is not None:
        vectors = await embed_texts([request.text])
        return EmbedResponse(embedding=vectors[0])
    vectors = await embed_texts(request.texts or [])
    return EmbedBatchResponse(embeddings=vectors)
