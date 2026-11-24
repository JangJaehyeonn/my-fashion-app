"""FR-24 유사 옷 검색·코디 수 평가 도구.

스택(HTTP)이 아니라 저장소의 evals/ 명령을 직접 돌려 결과 리포트를 본다. 가정 (NOTES.md 참고)
- 실행: 저장소 루트에서 `python3 evals/similarity/run.py --out <리포트 경로>`, `python3 evals/combos/run.py --out <리포트 경로>`.
  인자를 더 주지 않으면 저장소에 함께 둔 샘플 정답 쌍·조합 파일과 저장된 벡터 캐시를 쓴다. 성공하면 종료 코드 0.
- 리포트는 텍스트(마크다운 등) 한 파일이다. 요구사항에 적힌 낱말을 그대로 담는다:
  similarity = 기준값 0.70·0.75·0.80·0.85·0.90·0.95, precision, recall, precision@3, 오판 목록.
  combos = 일치율, 불일치 조합 목록.
- 외부 API 차단 확인: OPENAI_API_KEY 없이, 소켓 연결을 모두 막는 sitecustomize를 PYTHONPATH 앞에 둔 채로 돌린다.
  연결 시도가 기록되면 실패다.
- 결과는 규칙(캐시 벡터·FR-09 규칙)으로만 정해지므로 같은 입력을 두 번 돌리면 리포트가 같다.
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

import pytest

REPO = Path(__file__).resolve().parents[2]
THRESHOLDS = ["0.70", "0.75", "0.80", "0.85", "0.90", "0.95"]

BLOCK_NETWORK = '''
import os
import socket

_LOG = os.environ["WEARON_NET_LOG"]


def _blocked(self, address, *args, **kwargs):
    with open(_LOG, "a", encoding="utf-8") as f:
        f.write(repr(address) + "\\n")
    raise OSError("network blocked in FR-24 acceptance test")


socket.socket.connect = _blocked
socket.socket.connect_ex = _blocked
_orig_getaddrinfo = socket.getaddrinfo


def _getaddrinfo(host, *args, **kwargs):
    with open(_LOG, "a", encoding="utf-8") as f:
        f.write("dns " + repr(host) + "\\n")
    raise OSError("dns blocked in FR-24 acceptance test")


socket.getaddrinfo = _getaddrinfo
'''


def run_eval(tool: str, out: Path, tmp_path: Path) -> tuple[subprocess.CompletedProcess[str], str]:
    guard = tmp_path / "guard"
    guard.mkdir(exist_ok=True)
    (guard / "sitecustomize.py").write_text(BLOCK_NETWORK, encoding="utf-8")
    net_log = tmp_path / f"net-{tool}-{out.stem}.log"
    net_log.write_text("", encoding="utf-8")

    env = {k: v for k, v in os.environ.items() if k not in {"OPENAI_API_KEY", "OPENAI_BASE_URL"}}
    env["PYTHONPATH"] = os.pathsep.join([str(guard), str(REPO), env.get("PYTHONPATH", "")]).rstrip(os.pathsep)
    env["WEARON_NET_LOG"] = str(net_log)

    script = REPO / "evals" / tool / "run.py"
    assert script.is_file(), f"{script.relative_to(REPO)}가 없습니다"
    result = subprocess.run(
        [sys.executable, str(script), "--out", str(out)],
        cwd=REPO, env=env, capture_output=True, text=True, timeout=300,
    )
    return result, net_log.read_text(encoding="utf-8")


def read_report(out: Path) -> str:
    assert out.is_file(), "리포트 파일이 생기지 않았습니다"
    text = out.read_text(encoding="utf-8")
    assert text.strip(), "리포트가 비어 있습니다"
    return text


@pytest.mark.fr("FR-24")
def test_similarity_report_has_table_per_threshold(tmp_path: Path) -> None:
    out = tmp_path / "similarity-report.md"
    result, _ = run_eval("similarity", out, tmp_path)
    assert result.returncode == 0, result.stderr
    report = read_report(out)

    for threshold in THRESHOLDS:
        assert threshold in report, f"기준값 {threshold} 행이 없습니다"
    lowered = report.lower()
    assert "precision" in lowered
    assert "recall" in lowered
    assert "precision@3" in lowered
    assert "오판" in report


@pytest.mark.fr("FR-24")
def test_similarity_without_live_makes_no_external_call(tmp_path: Path) -> None:
    out = tmp_path / "similarity-offline.md"
    result, net_log = run_eval("similarity", out, tmp_path)
    assert result.returncode == 0, result.stderr
    assert net_log == "", f"--live 없이 네트워크 연결을 시도했습니다: {net_log}"
    read_report(out)


@pytest.mark.fr("FR-24")
def test_similarity_report_is_deterministic(tmp_path: Path) -> None:
    first, second = tmp_path / "sim-a.md", tmp_path / "sim-b.md"
    for out in (first, second):
        result, _ = run_eval("similarity", out, tmp_path)
        assert result.returncode == 0, result.stderr
    assert read_report(first) == read_report(second)


@pytest.mark.fr("FR-24")
def test_combos_report_has_agreement_and_mismatches(tmp_path: Path) -> None:
    out = tmp_path / "combos-report.md"
    result, _ = run_eval("combos", out, tmp_path)
    assert result.returncode == 0, result.stderr
    report = read_report(out)

    assert "일치율" in report
    assert "불일치" in report


@pytest.mark.fr("FR-24")
def test_combos_makes_no_external_call(tmp_path: Path) -> None:
    out = tmp_path / "combos-offline.md"
    result, net_log = run_eval("combos", out, tmp_path)
    assert result.returncode == 0, result.stderr
    assert net_log == "", f"combos 평가가 네트워크 연결을 시도했습니다: {net_log}"


@pytest.mark.fr("FR-24")
def test_combos_report_is_deterministic(tmp_path: Path) -> None:
    first, second = tmp_path / "combos-a.md", tmp_path / "combos-b.md"
    for out in (first, second):
        result, _ = run_eval("combos", out, tmp_path)
        assert result.returncode == 0, result.stderr
    assert read_report(first) == read_report(second)
