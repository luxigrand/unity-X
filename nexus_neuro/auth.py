"""
Personnel login and role-based access control for Nexus Neuro.

Roles:
    ADMIN     — Manual mode only (full manual device control)
    PERSONNEL — Auto and AI Co-Pilot modes only
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


@dataclass(frozen=True)
class UserAccount:
    national_id: str
    password_hash: str
    role: UserRole
    display_name: str


def _hash_password(password: str) -> str:
    """SHA-256 hash — avoids storing plain-text passwords in memory at rest."""
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


# Registered users (credentials provided at setup)
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
}


def authenticate(national_id: str, password: str) -> Optional[UserAccount]:
    """
    Validate kimlik numarası + şifre.

    Returns:
        UserAccount on success, None on failure.
    """
    user = _USERS.get(national_id.strip())
    if user is None:
        return None
    if user.password_hash != _hash_password(password):
        return None
    return user


def allowed_modes(role: UserRole) -> list[ControlMode]:
    """Return control modes this role may use."""
    if role is UserRole.ADMIN:
        return [ControlMode.MANUAL]
    return [ControlMode.AUTO, ControlMode.COPILOT]


def default_mode(role: UserRole) -> ControlMode:
    """Initial control mode after login."""
    return allowed_modes(role)[0]


def can_access_manual_controls(role: UserRole) -> bool:
    return role is UserRole.ADMIN


def role_label(role: UserRole) -> str:
    return role.value
