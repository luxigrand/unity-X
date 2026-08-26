"""
Personnel login and role-based access control for unity-X.

Aligned with Android `:app` (personnel / admin APK).
End-user accounts live in Supabase (`:consumer` app).

Roles:
    ADMIN      — Manual + Auto + AI Co-Pilot (full device control in Manual)
    PERSONNEL  — Auto + AI Co-Pilot
    PRESENTER  — Manual + Auto + AI Co-Pilot (demo / sunum)
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from enum import Enum
from typing import Optional

from nexus_neuro.models import ControlMode


class UserRole(str, Enum):
    """Application access level."""

    ADMIN = "Administrator"
    PERSONNEL = "Personel"
    PRESENTER = "Sunum"


@dataclass(frozen=True)
class UserAccount:
    national_id: str
    password_hash: str
    role: UserRole
    display_name: str


def _hash_password(password: str) -> str:
    """SHA-256 hash — avoids storing plain-text passwords in memory at rest."""
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


_USERS: dict[str, UserAccount] = {
    "57019027696": UserAccount(
        national_id="57019027696",
        password_hash=_hash_password("15041212.k"),
        role=UserRole.ADMIN,
        display_name="Administrator",
    ),
    "5433307329": UserAccount(
        national_id="5433307329",
        password_hash=_hash_password("1599511324"),
        role=UserRole.PERSONNEL,
        display_name="Personel",
    ),
    "159951": UserAccount(
        national_id="159951",
        password_hash=_hash_password("1324"),
        role=UserRole.PRESENTER,
        display_name="Sunum",
    ),
}


def authenticate(national_id: str, password: str) -> Optional[UserAccount]:
    """Validate kimlik numarası + şifre."""
    user = _USERS.get(national_id.strip())
    if user is None:
        return None
    if user.password_hash != _hash_password(password):
        return None
    return user


def allowed_modes(role: UserRole) -> list[ControlMode]:
    """Return control modes this role may use."""
    if role in (UserRole.ADMIN, UserRole.PRESENTER):
        return [ControlMode.MANUAL, ControlMode.AUTO, ControlMode.COPILOT]
    return [ControlMode.AUTO, ControlMode.COPILOT]


def default_mode(role: UserRole) -> ControlMode:
    """Initial control mode after login."""
    if role is UserRole.ADMIN:
        return ControlMode.MANUAL
    if role is UserRole.PRESENTER:
        return ControlMode.COPILOT
    return ControlMode.AUTO


def can_access_manual_controls(role: UserRole) -> bool:
    return role in (UserRole.ADMIN, UserRole.PRESENTER)


def role_label(role: UserRole) -> str:
    return role.value
