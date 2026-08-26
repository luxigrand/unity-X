"""
Generate a showcase 3D model of the unity-X sleep / dream headband.

Outputs (mm):
  - nexus-neuro-band.stl  (binary STL — 3D viewers / print prep)
  - nexus-neuro-band.obj  (+ .mtl) — Blender / online viewers
"""

from __future__ import annotations

import math
import struct
from pathlib import Path

import numpy as np

OUT_DIR = Path(__file__).resolve().parent


# ---------------------------------------------------------------------------
# Mesh helpers
# ---------------------------------------------------------------------------

def _norm(v: np.ndarray) -> np.ndarray:
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def _tri_normal(a, b, c) -> np.ndarray:
    return _norm(np.cross(b - a, c - a))


class Mesh:
    def __init__(self) -> None:
        self.tris: list[tuple[np.ndarray, np.ndarray, np.ndarray]] = []

    def add_tri(self, a, b, c) -> None:
        self.tris.append((np.asarray(a, float), np.asarray(b, float), np.asarray(c, float)))

    def add_quad(self, a, b, c, d) -> None:
        self.add_tri(a, b, c)
        self.add_tri(a, c, d)

    def extend(self, other: "Mesh") -> None:
        self.tris.extend(other.tris)

    def transform(self, mat: np.ndarray) -> "Mesh":
        """Apply 4x4 transform to a copy."""
        out = Mesh()
        for a, b, c in self.tris:
            out.add_tri(_xf(a, mat), _xf(b, mat), _xf(c, mat))
        return out


def _xf(p: np.ndarray, mat: np.ndarray) -> np.ndarray:
    h = np.array([p[0], p[1], p[2], 1.0])
    r = mat @ h
    return r[:3]


def translate(x, y, z) -> np.ndarray:
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scale(sx, sy=None, sz=None) -> np.ndarray:
    sy = sx if sy is None else sy
    sz = sx if sz is None else sz
    m = np.eye(4)
    m[0, 0], m[1, 1], m[2, 2] = sx, sy, sz
    return m


def rotate_y(deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 2] = c, s
    m[2, 0], m[2, 2] = -s, c
    return m


def rotate_x(deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[1, 1], m[1, 2] = c, -s
    m[2, 1], m[2, 2] = s, c
    return m


def rotate_z(deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 1] = c, -s
    m[1, 0], m[1, 1] = s, c
    return m


# ---------------------------------------------------------------------------
# Primitives
# ---------------------------------------------------------------------------

def box(sx: float, sy: float, sz: float, rounded: bool = False) -> Mesh:
    """Axis-aligned box centered at origin. Sizes are full extents."""
    hx, hy, hz = sx / 2, sy / 2, sz / 2
    # 8 corners
    p = [
        np.array([-hx, -hy, -hz]),
        np.array([hx, -hy, -hz]),
        np.array([hx, hy, -hz]),
        np.array([-hx, hy, -hz]),
        np.array([-hx, -hy, hz]),
        np.array([hx, -hy, hz]),
        np.array([hx, hy, hz]),
        np.array([-hx, hy, hz]),
    ]
    faces = [
        (0, 1, 2, 3),  # -Z
        (4, 7, 6, 5),  # +Z
        (0, 4, 5, 1),  # -Y
        (3, 2, 6, 7),  # +Y
        (0, 3, 7, 4),  # -X
        (1, 5, 6, 2),  # +X
    ]
    m = Mesh()
    for a, b, c, d in faces:
        m.add_quad(p[a], p[b], p[c], p[d])
    return m


def cylinder(radius: float, height: float, segments: int = 32, capped: bool = True) -> Mesh:
    """Cylinder along Y, centered at origin."""
    m = Mesh()
    hy = height / 2
    ring_bot = []
    ring_top = []
    for i in range(segments):
        a = 2 * math.pi * i / segments
        x, z = radius * math.cos(a), radius * math.sin(a)
        ring_bot.append(np.array([x, -hy, z]))
        ring_top.append(np.array([x, hy, z]))
    for i in range(segments):
        j = (i + 1) % segments
        m.add_quad(ring_bot[i], ring_bot[j], ring_top[j], ring_top[i])
    if capped:
        bot_c = np.array([0.0, -hy, 0.0])
        top_c = np.array([0.0, hy, 0.0])
        for i in range(segments):
            j = (i + 1) % segments
            m.add_tri(bot_c, ring_bot[j], ring_bot[i])
            m.add_tri(top_c, ring_top[i], ring_top[j])
    return m


def torus_arc(
    major_r: float,
    minor_r: float,
    start_deg: float,
    end_deg: float,
    major_seg: int = 64,
    minor_seg: int = 16,
) -> Mesh:
    """
    Torus arc in the XZ plane (band around a head).
    major_r = head radius, minor_r = band tube radius.
    """
    m = Mesh()
    start, end = math.radians(start_deg), math.radians(end_deg)
    rings: list[list[np.ndarray]] = []
    for i in range(major_seg + 1):
        t = start + (end - start) * i / major_seg
        cx = major_r * math.cos(t)
        cz = major_r * math.sin(t)
        ring = []
        for j in range(minor_seg):
            u = 2 * math.pi * j / minor_seg
            # Local frame: radial outward in XZ, up = Y
            ox = math.cos(t) * math.cos(u) * minor_r
            oy = math.sin(u) * minor_r
            oz = math.sin(t) * math.cos(u) * minor_r
            ring.append(np.array([cx + ox, oy, cz + oz]))
        rings.append(ring)

    for i in range(major_seg):
        for j in range(minor_seg):
            j2 = (j + 1) % minor_seg
            a, b = rings[i][j], rings[i][j2]
            c, d = rings[i + 1][j2], rings[i + 1][j]
            m.add_quad(a, b, c, d)

    # Cap ends
    for ring, outward in ((rings[0], -1), (rings[-1], 1)):
        center = np.mean(ring, axis=0)
        for j in range(minor_seg):
            j2 = (j + 1) % minor_seg
            if outward > 0:
                m.add_tri(center, ring[j], ring[j2])
            else:
                m.add_tri(center, ring[j2], ring[j])
    return m


def rounded_capsule(length: float, radius: float, segments: int = 24) -> Mesh:
    """Horizontal capsule along X (sensor bar)."""
    m = Mesh()
    # Cylinder body along X
    body = cylinder(radius, length - 2 * radius, segments)
    # Rotate cylinder from Y to X
    body = body.transform(rotate_z(90))
    m.extend(body)
    # End spheres approximated as hemispheres via cylinder caps — use UV spheres half
    for side in (-1, 1):
        cx = side * (length / 2 - radius)
        hemi = _hemisphere(radius, segments // 2, segments)
        hemi = hemi.transform(rotate_z(90 if side > 0 else -90))
        hemi = hemi.transform(translate(cx, 0, 0))
        m.extend(hemi)
    return m


def _hemisphere(radius: float, stacks: int, slices: int) -> Mesh:
    """Hemisphere: flat on -Y, dome toward +Y."""
    m = Mesh()
    pts: list[list[np.ndarray]] = []
    for i in range(stacks + 1):
        v = (math.pi / 2) * i / stacks  # 0..90 deg
        y = radius * math.sin(v)
        r = radius * math.cos(v)
        row = []
        for j in range(slices):
            a = 2 * math.pi * j / slices
            row.append(np.array([r * math.cos(a), y, r * math.sin(a)]))
        pts.append(row)
    for i in range(stacks):
        for j in range(slices):
            j2 = (j + 1) % slices
            m.add_quad(pts[i][j], pts[i][j2], pts[i + 1][j2], pts[i + 1][j])
    # Flat base
    base_c = np.array([0.0, 0.0, 0.0])
    for j in range(slices):
        j2 = (j + 1) % slices
        m.add_tri(base_c, pts[0][j2], pts[0][j])
    return m


def ellipsoid(rx: float, ry: float, rz: float, stacks: int = 16, slices: int = 24) -> Mesh:
    m = Mesh()
    pts: list[list[np.ndarray]] = []
    for i in range(stacks + 1):
        v = math.pi * i / stacks
        row = []
        for j in range(slices):
            u = 2 * math.pi * j / slices
            row.append(
                np.array(
                    [
                        rx * math.sin(v) * math.cos(u),
                        ry * math.cos(v),
                        rz * math.sin(v) * math.sin(u),
                    ]
                )
            )
        pts.append(row)
    for i in range(stacks):
        for j in range(slices):
            j2 = (j + 1) % slices
            m.add_quad(pts[i][j], pts[i][j2], pts[i + 1][j2], pts[i + 1][j])
    return m


# ---------------------------------------------------------------------------
# Device assembly — unity-X headband (mm)
# ---------------------------------------------------------------------------

def build_nexus_band() -> Mesh:
    """
    Approximate adult head circumference band:
      major radius ~ 95 mm, tube ~ soft fabric, frontal sensor pod.
    """
    device = Mesh()

    # Soft fabric band (rear open for stretch / clasp feel — front 220°)
    band = torus_arc(
        major_r=95.0,
        minor_r=9.5,
        start_deg=-110,
        end_deg=110,
        major_seg=72,
        minor_seg=18,
    )
    # Lift slightly so it sits like a forehead band
    band = band.transform(translate(0, 8, 0))
    device.extend(band)

    # Inner softer secondary tube (slightly thinner) for fabric depth
    inner = torus_arc(
        major_r=93.5,
        minor_r=6.0,
        start_deg=-105,
        end_deg=105,
        major_seg=64,
        minor_seg=14,
    )
    inner = inner.transform(translate(0, 8, 2))
    device.extend(inner)

    # Central sensor / stim module (forehead pod)
    pod = ellipsoid(28, 12, 14, stacks=18, slices=28)
    pod = pod.transform(translate(0, 10, 95))
    device.extend(pod)

    # Flat accent plate on pod front
    plate = box(22, 3, 10)
    plate = plate.transform(translate(0, 10, 108))
    device.extend(plate)

    # Status LED lens
    led = ellipsoid(3.2, 2.2, 2.2, stacks=10, slices=16)
    led = led.transform(translate(0, 14, 109))
    device.extend(led)

    # Left / right electrode pads (temple contacts)
    for side in (-1, 1):
        pad = ellipsoid(10, 5, 8, stacks=12, slices=20)
        # Place near temples on the arc
        ang = math.radians(side * 55)
        x = 95 * math.cos(ang)
        z = 95 * math.sin(ang)
        pad = pad.transform(translate(x, 6, z))
        device.extend(pad)

        # Small contact disc
        disc = cylinder(4.5, 2.5, 20)
        disc = disc.transform(rotate_x(90))
        disc = disc.transform(translate(x * 0.92, 4, z * 0.92))
        device.extend(disc)

    # Rear clasp / battery puck
    clasp = ellipsoid(18, 10, 12, stacks=14, slices=22)
    clasp = clasp.transform(translate(0, 6, -92))
    device.extend(clasp)

    # Tiny USB-C style port hint on clasp
    port = box(8, 3, 2.5)
    port = port.transform(translate(0, 2, -103))
    device.extend(port)

    # Thin wire channel ridges (decorative) on left/right of pod
    for side in (-1, 1):
        ridge = cylinder(1.8, 36, 12)
        ridge = ridge.transform(rotate_z(90))
        ridge = ridge.transform(rotate_y(side * 18))
        ridge = ridge.transform(translate(side * 22, 10, 88))
        device.extend(ridge)

    return device


# ---------------------------------------------------------------------------
# Exporters
# ---------------------------------------------------------------------------

def write_stl_binary(mesh: Mesh, path: Path) -> None:
    n = len(mesh.tris)
    with path.open("wb") as f:
        header = b"unity-X headband showcase model" + b"\0" * 80
        f.write(header[:80])
        f.write(struct.pack("<I", n))
        for a, b, c in mesh.tris:
            nx, ny, nz = _tri_normal(a, b, c)
            f.write(struct.pack("<3f", nx, ny, nz))
            f.write(struct.pack("<3f", *a))
            f.write(struct.pack("<3f", *b))
            f.write(struct.pack("<3f", *c))
            f.write(struct.pack("<H", 0))


def write_obj(mesh: Mesh, path: Path, mtl_name: str = "nexus_band.mtl") -> None:
    # Deduplicate vertices roughly
    verts: list[tuple[float, float, float]] = []
    index: dict[tuple[float, float, float], int] = {}
    faces: list[tuple[int, int, int]] = []

    def vid(p: np.ndarray) -> int:
        key = (round(float(p[0]), 4), round(float(p[1]), 4), round(float(p[2]), 4))
        if key not in index:
            index[key] = len(verts) + 1
            verts.append(key)
        return index[key]

    for a, b, c in mesh.tris:
        faces.append((vid(a), vid(b), vid(c)))

    with path.open("w", encoding="utf-8") as f:
        f.write("# unity-X — headband showcase\n")
        f.write(f"mtllib {mtl_name}\n")
        f.write("o UnityXBand\n")
        for x, y, z in verts:
            f.write(f"v {x:.4f} {y:.4f} {z:.4f}\n")
        f.write("usemtl SoftBand\n")
        f.write("s off\n")
        for i, j, k in faces:
            f.write(f"f {i} {j} {k}\n")

    mtl_path = path.with_name(mtl_name)
    with mtl_path.open("w", encoding="utf-8") as f:
        f.write("newmtl SoftBand\n")
        f.write("Kd 0.42 0.48 0.88\n")
        f.write("Ka 0.12 0.14 0.22\n")
        f.write("Ks 0.25 0.25 0.30\n")
        f.write("Ns 40\n")
        f.write("d 1.0\n")


def main() -> None:
    mesh = build_nexus_band()
    stl_path = OUT_DIR / "nexus-neuro-band.stl"
    obj_path = OUT_DIR / "nexus-neuro-band.obj"
    write_stl_binary(mesh, stl_path)
    write_obj(mesh, obj_path)
    print(f"triangles: {len(mesh.tris)}")
    print(f"wrote {stl_path} ({stl_path.stat().st_size / 1024:.1f} KB)")
    print(f"wrote {obj_path} ({obj_path.stat().st_size / 1024:.1f} KB)")
    print(f"wrote {OUT_DIR / 'nexus_band.mtl'}")


if __name__ == "__main__":
    main()
