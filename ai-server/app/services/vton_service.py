import asyncio
import base64
import logging
import tempfile
from pathlib import Path

from gradio_client import Client, handle_file

from app.core.config import settings

# uvicorn이 핸들러를 붙여둔 로거를 써야 docker logs에 그대로 찍힘
logger = logging.getLogger("uvicorn.error")

_client: Client | None = None


def _get_client() -> Client:
    global _client
    if _client is None:
        try:
            # gradio_client 2.x부터 인자명이 hf_token → token 으로 변경됨
            _client = Client(settings.hf_vton_space_id, token=settings.hf_api_token)
        except Exception:
            # Space 이름 오류, 토큰 무효, Space가 sleeping/삭제된 경우 등
            logger.exception("HF Space 클라이언트 생성 실패 (space=%s)", settings.hf_vton_space_id)
            raise
    return _client


def _run_tryon(person_path: str, garment_path: str, garment_desc: str) -> str:
    client = _get_client()
    try:
        result = client.predict(
            dict={"background": handle_file(person_path), "layers": [], "composite": None},
            garm_img=handle_file(garment_path),
            garment_des=garment_desc,
            is_checked=True,
            is_checked_crop=False,
            denoise_steps=30,
            seed=42,
            api_name="/tryon",
        )
    except Exception:
        # 큐 초과/GPU 할당량 초과, /tryon 시그니처 변경, Space 런타임 에러 등
        logger.exception(
            "HF Space /tryon 호출 실패 (space=%s, garment_desc=%r)",
            settings.hf_vton_space_id,
            garment_desc,
        )
        raise
    # result is (tryon_image_path, masked_image_path); we only need the try-on result.
    return result[0]


async def virtual_try_on(person_bytes: bytes, garment_bytes: bytes, garment_desc: str) -> str:
    with tempfile.TemporaryDirectory() as tmp_dir:
        person_path = Path(tmp_dir) / "person.jpg"
        garment_path = Path(tmp_dir) / "garment.jpg"
        person_path.write_bytes(person_bytes)
        garment_path.write_bytes(garment_bytes)

        result_path = await asyncio.to_thread(
            _run_tryon, str(person_path), str(garment_path), garment_desc
        )

        return base64.b64encode(Path(result_path).read_bytes()).decode("utf-8")
