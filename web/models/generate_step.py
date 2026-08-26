"""
Export unity-X headband as STEP (solids) for Autodesk Fusion.

  python generate_step.py

Fusion: File > Open > nexus-neuro-band.step
"""

from __future__ import annotations

import math
from pathlib import Path

import cadquery as cq

OUT = Path(__file__).resolve().parent / "nexus-neuro-band.step"

HEAD_R = 95.0
BAND_R = 9.5
START_DEG = -110.0
END_DEG = 110.0


def ellipsoid_y(rx: float, ry: float) -> cq.Workplane:
    """Solid of revolution (ellipse around Y). rz = rx."""
    pts = []
    for i in range(32):
        a = -math.pi / 2 + math.pi * i / 31
        x = max(rx * math.cos(a), 0.0)
        y = ry * math.sin(a)
        pts.append((x, y))
    return cq.Workplane("XY").polyline(pts).close().revolve(360)


def fabric_band() -> cq.Workplane:
    """Circular pipe along forehead arc."""
    start = math.radians(START_DEG)
    end = math.radians(END_DEG)
    n = 40
    pts = []
    for i in range(n + 1):
        t = start + (end - start) * i / n
        pts.append((HEAD_R * math.cos(t), 8.0, HEAD_R * math.sin(t)))

    path = cq.Workplane("XY").spline(pts)
    # Section at path start, in plane roughly normal to path
    t0 = start
    # Local frame: tangent, then YZ-ish circle
    section = (
        cq.Workplane("YZ")
        .workplane()
        .transformed(
            offset=cq.Vector(pts[0][0], pts[0][1], pts[0][2]),
            rotate=cq.Vector(0, -math.degrees(t0), 0),
        )
        .circle(BAND_R)
    )
    return section.sweep(path, isFrenet=True)


def fabric_band_fallback() -> cq.Workplane:
    """Partial torus (front open band) if sweep fails."""
    torus = cq.Workplane("XY").torus(HEAD_R, BAND_R).translate((0, 8, 0))
    # Remove rear sector with a cutting box behind the head
    cutter = cq.Workplane("XY").box(120, 50, 110).translate((0, 8, -100))
    return torus.cut(cutter)


def build() -> cq.Workplane:
    try:
        band = fabric_band()
    except Exception as ex:
        print("sweep failed, torus fallback:", ex)
        band = fabric_band_fallback()

    pod = ellipsoid_y(28, 12).translate((0, 10, 95))
    # Stretch Z a bit via non-uniform — approximate with scale on solid
    pod = pod.val().scale(1.0)  # identity; rz≈rx is ok for Fusion start
    pod = cq.Workplane(obj=pod) if not isinstance(pod, cq.Workplane) else pod

    plate = cq.Workplane("XY").box(22, 3, 10).translate((0, 10, 108))
    led = ellipsoid_y(3.2, 2.2).translate((0, 14, 109))

    result = band.union(pod).union(plate).union(led)

    for side in (-1.0, 1.0):
        ang = math.radians(side * 55)
        x = HEAD_R * math.cos(ang)
        z = HEAD_R * math.sin(ang)
        pad = ellipsoid_y(10, 5).translate((x, 6, z))
        result = result.union(pad)

    clasp = ellipsoid_y(18, 10).translate((0, 6, -92))
    result = result.union(clasp)
    return result


def main() -> None:
    model = build()
    cq.exporters.export(model, str(OUT))
    print(f"wrote {OUT} ({OUT.stat().st_size / 1024:.1f} KB)")


if __name__ == "__main__":
    main()
