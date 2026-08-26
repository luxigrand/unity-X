"""
Read latest consumer vitals from Supabase (written by Android :consumer primary phone).
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


@dataclass
class CloudVitals:
    bpm: Optional[float]
    spo2: Optional[float]
    availability: str
    measured_at: Optional[str]
    user_id: Optional[str] = None


def _read_local_properties() -> dict[str, str]:
    props: dict[str, str] = {}
    path = Path(__file__).resolve().parents[1] / "android" / "local.properties"
    if not path.exists():
        return props
    for line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        props[key.strip()] = val.strip()
    return props


def supabase_config() -> tuple[str, str]:
    props = _read_local_properties()
    url = os.environ.get("SUPABASE_URL") or props.get("SUPABASE_URL", "")
    key = (
        os.environ.get("SUPABASE_ANON_KEY")
        or props.get("SUPABASE_ANON_KEY", "")
    )
    return url.strip(), key.strip()


def fetch_latest_vitals(limit: int = 5) -> list[CloudVitals]:
    """
    Fetch recent vitals_latest rows (anon key + RLS: only works if policy allows;
    for staff dashboard we use service-less anon — typically returns empty unless
    RLS permits. Prefer authenticated staff later.)

    For now: try anon select; if RLS blocks, return [].
    """
    url, key = supabase_config()
    if not url or not key:
        return []

    try:
        from supabase import create_client
    except ImportError:
        return []

    try:
        client = create_client(url, key)
        # Staff view: list latest rows (may be empty under RLS for anon)
        res = (
            client.table("vitals_latest")
            .select("user_id,bpm,spo2,availability,measured_at")
            .order("measured_at", desc=True)
            .limit(limit)
            .execute()
        )
        rows = res.data or []
        out: list[CloudVitals] = []
        for r in rows:
            out.append(
                CloudVitals(
                    bpm=r.get("bpm"),
                    spo2=r.get("spo2"),
                    availability=r.get("availability") or "UNKNOWN",
                    measured_at=r.get("measured_at"),
                    user_id=r.get("user_id"),
                )
            )
        return out
    except Exception:
        return []


def config_status() -> str:
    url, key = supabase_config()
    if not url or not key:
        return "Supabase yapılandırılmamış (android/local.properties)"
    return f"Supabase: {url.replace('https://', '').split('.')[0]}…"
