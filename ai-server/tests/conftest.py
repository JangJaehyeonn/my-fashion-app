"""AI 서버 단위 테스트 공통 설정. 실제 OpenAI를 부르지 않도록 가짜 키를 넣고, 호출은 테스트마다 목으로 바꾼다."""

from __future__ import annotations

import os
import sys
from pathlib import Path

# 테스트 전용 가짜 값 (실제 비밀값 아님). app.core.config가 import 시점에 읽는다.
os.environ.setdefault("OPENAI_API_KEY", "test-not-a-real-key")
os.environ.setdefault("WEATHER_API_KEY", "test-not-a-real-key")

AI_SERVER_ROOT = Path(__file__).resolve().parent.parent
if str(AI_SERVER_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_SERVER_ROOT))
