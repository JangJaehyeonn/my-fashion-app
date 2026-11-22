import json
import logging

from fastapi import APIRouter, HTTPException

from app.schemas.purchase import PurchaseExplainRequest, PurchaseExplainResponse
from app.services.purchase_service import explain_purchase

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/ai/purchase-check/explain", response_model=PurchaseExplainResponse)
async def purchase_explain(request: PurchaseExplainRequest) -> PurchaseExplainResponse:
    """FR-11: picks up to 3 of the rule-made candidate combos and writes the sentences. Never decides the verdict."""
    try:
        return await explain_purchase(request)
    except (ValueError, json.JSONDecodeError) as exc:
        # The backend falls back to rule-based sentences on any non-2xx response
        logger.warning("purchase explain produced unusable output: %s", type(exc).__name__)
        raise HTTPException(status_code=502, detail="AI 응답을 해석하지 못했어요.") from exc
