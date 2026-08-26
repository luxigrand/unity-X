"""
unity-X — wearable forehead band CAD (mm)
================================================
Produces a Fusion-ready multi-body STEP assembly + per-part STL.

  python design_nexus_band.py

Output folder: web/models/nexus_band/
"""

from __future__ import annotations

import math
from pathlib import Path

import cadquery as cq

OUT = Path(__file__).resolve().parent / "nexus_band"
OUT.mkdir(parents=True, exist_ok=True)

# --- Product parameters (adult forehead band) ---
HEAD_R = 95.0          # major radius of band centerline
BAND_W = 18.0          # band width (along head surface tangent / "height" of strap)
BAND_T = 6.0           # band thickness (radial)
ARC_START = -105.0     # degrees (rear-left open)
ARC_END = 105.0        # degrees (rear-right open)

POD_W = 52.0           # forehead module width (X)
POD_H = 28.0           # height (Y)
POD_D = 18.0           # depth (Z outward)
WALL = 2.0
LID_H = 3.0

CLASP_W = 42.0
CLASP_H = 28.0
CLASP_D = 22.0


def _deg_pts(r: float, y: float, a0: float, a1: float, n: int = 48):
    pts = []
    for i in range(n + 1):
        t = math.radians(a0 + (a1 - a0) * i / n)
        pts.append((r * math.cos(t), y, r * math.sin(t)))
    return pts


def part_band_spine() -> cq.Workplane:
    """
    Structural C-spine: rounded rectangular cross-section swept along forehead arc.
    Sits against the head; fabric sleeve wraps around this in production.
    """
    # Cross-section: rounded rectangle in local Y (up) / radial-ish
    # Use a simple stadium profile in YZ then sweep with Frenet
    pts = _deg_pts(HEAD_R, 0, ARC_START, ARC_END, 56)
    path = cq.Workplane("XY").spline(pts)

    # Build section at start point, oriented with Frenet-friendly plane
    a0 = math.radians(ARC_START)
    # Local: Y = up, radial outward from head center
    # Start with circle-ish rounded rect extruded conceptually via sweep of 2D profile
    hw, ht = BAND_W / 2, BAND_T / 2
    section = (
        cq.Workplane("YZ")
        .transformed(
            offset=cq.Vector(pts[0][0], pts[0][1], pts[0][2]),
            rotate=cq.Vector(0, -math.degrees(a0), 0),
        )
        .rect(BAND_W, BAND_T)
        .offset2D(1.5, kind="arc")  # soft outer corners on profile? offset grows — skip
    )
    # Cleaner: fillet after sweep on a rect sweep
    section = (
        cq.Workplane("YZ")
        .transformed(
            offset=cq.Vector(pts[0][0], pts[0][1], pts[0][2]),
            rotate=cq.Vector(0, -math.degrees(a0), 0),
        )
        .rect(BAND_W, BAND_T)
    )
    spine = section.sweep(path, isFrenet=True)
    # Soften outer edges
    try:
        spine = spine.edges().fillet(1.2)
    except Exception:
        pass
    return spine


def part_band_spine_robust() -> cq.Workplane:
    """
    More reliable spine: loft / union of capsules along arc (discrete segments),
    then optionally smooth. Used if sweep fails.
    """
    a0, a1 = ARC_START, ARC_END
    n = 24
    bodies = []
    for i in range(n):
        t0 = math.radians(a0 + (a1 - a0) * i / n)
        t1 = math.radians(a0 + (a1 - a0) * (i + 1) / n)
        p0 = cq.Vector(HEAD_R * math.cos(t0), 0, HEAD_R * math.sin(t0))
        p1 = cq.Vector(HEAD_R * math.cos(t1), 0, HEAD_R * math.sin(t1))
        mid = (p0 + p1) * 0.5
        length = (p1 - p0).Length + 0.4
        # Box oriented along chord
        direction = (p1 - p0).normalized()
        # Approximate with cylinder along chord + box for width
        cyl = (
            cq.Workplane("XY")
            .transformed(offset=mid)
            .box(length, BAND_W, BAND_T)
        )
        # Rotate box so X aligns with direction — use workplane
        # Simpler approach: sphere chain
        sph = cq.Workplane("XY").transformed(offset=p0).sphere(BAND_T * 0.55)
        bodies.append(sph)
        sph2 = cq.Workplane("XY").transformed(offset=p1).sphere(BAND_T * 0.55)
        bodies.append(sph2)

    # Better robust method: partial torus with rectangular profile via revolve cut
    # Create full torus then cut rear wedge
    major, minor = HEAD_R, BAND_T / 2
    # Use CQ torus then intersect with angular wedge — hard.
    # Use revolve of offset rectangle:
    # Profile at x=HEAD_R: rectangle BAND_T deep (radial), BAND_W tall
    profile = (
        cq.Workplane("XY")
        .center(HEAD_R, 0)
        .rect(BAND_T, BAND_W)
    )
    # Revolve only through arc angle around Y
    angle = ARC_END - ARC_START
    # CQ revolve is around Y by default for XY sketches — but center should be Y axis
    # Move profile: rect centered at (HEAD_R, 0) with size (BAND_T, BAND_W)
    spine = (
        cq.Workplane("XY")
        .moveTo(HEAD_R - BAND_T / 2, -BAND_W / 2)
        .rect(BAND_T, BAND_W, centered=False)
        .revolve(angle, (0, 0, 0), (0, 1, 0))
    )
    # Rotate so arc is centered on +Z (forehead forward)
    # Current revolve starts at +X and goes toward -Z or +Z depending on direction.
    # Rotate around Y by ARC_START so forehead sits at +Z
    spine = spine.rotate((0, 0, 0), (0, 1, 0), ARC_START)
    # Fillet
    try:
        spine = spine.edges(">X or <X").fillet(1.0)
    except Exception:
        try:
            spine = spine.edges().fillet(0.8)
        except Exception:
            pass
    return spine


def part_sensor_pod_base() -> cq.Workplane:
    """
    Lower housing: sits on forehead (+Z outward). Internal cavity for PCB + battery cell.
    Snap ledge for lid.
    """
    # Outer shell — soft rectangular with big fillets
    outer = (
        cq.Workplane("XY")
        .box(POD_W, POD_H, POD_D)
        .edges("|Z").fillet(6)
        .edges("#Z").fillet(3)
    )
    # Hollow
    inner = (
        cq.Workplane("XY")
        .box(POD_W - 2 * WALL, POD_H - 2 * WALL, POD_D - WALL)
        .translate((0, 0, WALL / 2))
        .edges("|Z").fillet(4)
    )
    shell = outer.cut(inner)

    # Lid recess ledge (step around rim)
    ledge = (
        cq.Workplane("XY")
        .box(POD_W - WALL, POD_H - WALL, LID_H + 0.2)
        .translate((0, 0, POD_D / 2 - LID_H / 2))
        .edges("|Z").fillet(5)
    )
    shell = shell.cut(ledge)

    # Cable exits left/right (slot for flex PCB / wires to band)
    for side in (-1, 1):
        slot = (
            cq.Workplane("XY")
            .box(6, 5, 8)
            .translate((side * (POD_W / 2 - 1), 0, -2))
        )
        shell = shell.cut(slot)

    # Bottom skin contact wells (3 electrode windows — recessed)
    for x in (-14, 0, 14):
        well = (
            cq.Workplane("XY")
            .cylinder(2.5, 4.5)
            .translate((x, -POD_H / 2 + 4, -POD_D / 2 + 1))
            .rotate((0, 0, 0), (1, 0, 0), 90)
        )
        # Simpler: cut from bottom face
        hole = (
            cq.Workplane("XY")
            .workplane(offset=-POD_D / 2)
            .center(x, -2)
            .circle(3.5)
            .extrude(WALL + 0.5)
        )
        shell = shell.cut(hole)

    # Mount bosses (4x M2)
    bosses = []
    for x, y in ((-18, -8), (18, -8), (-18, 8), (18, 8)):
        boss = (
            cq.Workplane("XY")
            .workplane(offset=-POD_D / 2 + WALL)
            .center(x, y)
            .circle(3.2)
            .extrude(POD_D - WALL - LID_H - 1)
            .faces(">Z").workplane()
            .hole(1.8, depth=8)
        )
        bosses.append(boss)
    for b in bosses:
        shell = shell.union(b)

    # Position on forehead (band front)
    shell = shell.translate((0, 4, HEAD_R + POD_D / 2 - 2))
    return shell


def part_sensor_pod_lid() -> cq.Workplane:
    """Top lid with LED window + subtle brand plateau."""
    lid = (
        cq.Workplane("XY")
        .box(POD_W - WALL - 0.4, POD_H - WALL - 0.4, LID_H)
        .edges("|Z")
        .fillet(5)
    )
    try:
        lid = lid.edges("#Z").fillet(1.0)
    except Exception:
        pass

    # LED through-hole
    lid = (
        lid.faces(">Z")
        .workplane(centerOption="CenterOfMass")
        .center(0, 6)
        .hole(5.6)
    )

    # Shallow decorative ring (outer) via cut
    ring = (
        cq.Workplane("XY")
        .workplane(offset=LID_H / 2 + 0.01)
        .center(0, 6)
        .circle(4.4)
        .circle(3.6)
        .extrude(-0.7)
    )
    lid = lid.cut(ring)

    # Brand plateau
    plate = (
        cq.Workplane("XY")
        .box(16, 5, 0.6)
        .edges("|Z")
        .fillet(1.5)
        .translate((0, -4, LID_H / 2))
    )
    lid = lid.union(plate)

    lid = lid.translate((0, 4, HEAD_R + POD_D - LID_H / 2 - 2))
    return lid


def part_temple_pad(side: float) -> cq.Workplane:
    """Soft contact pad at temple (±side). Rounded pillow shape."""
    ang = math.radians(side * 58)
    x = (HEAD_R + 3) * math.cos(ang)
    z = (HEAD_R + 3) * math.sin(ang)

    pad = (
        cq.Workplane("XY")
        .box(18, 12, 14)
        .edges()
        .fillet(4.5)
    )
    # Orient along band tangent & place
    pad = pad.rotate((0, 0, 0), (0, 1, 0), math.degrees(ang))
    pad = pad.translate((x, 0, z))
    return pad


def part_rear_clasp() -> cq.Workplane:
    """
    Rear battery / clasp module. USB-C cutout, hinged strap slots.
    """
    body = (
        cq.Workplane("XY")
        .box(CLASP_W, CLASP_H, CLASP_D)
        .edges("|Z").fillet(5)
        .edges("#Z").fillet(2.5)
    )
    # Battery cavity (18650 short / pouch approx)
    cavity = (
        cq.Workplane("XY")
        .box(CLASP_W - 2 * WALL, CLASP_H - 2 * WALL, CLASP_D - WALL - 1)
        .translate((0, 0, WALL / 2))
        .edges("|Z").fillet(3)
    )
    body = body.cut(cavity)

    # USB-C opening (rear face, -Z)
    usb = (
        cq.Workplane("XY")
        .box(9.2, 3.4, 8)
        .edges("|Z").fillet(1.2)
        .translate((0, -4, -CLASP_D / 2 + 2))
    )
    body = body.cut(usb)

    # Strap slots left/right
    for side in (-1, 1):
        slot = (
            cq.Workplane("XY")
            .box(4, CLASP_H - 8, 10)
            .translate((side * (CLASP_W / 2 - 2), 0, 2))
        )
        body = body.cut(slot)

    # Lid screw bosses
    for x, y in ((-14, -8), (14, -8), (-14, 8), (14, 8)):
        boss = (
            cq.Workplane("XY")
            .workplane(offset=-CLASP_D / 2 + WALL)
            .center(x, y)
            .circle(3)
            .extrude(CLASP_D - WALL - 3)
            .faces(">Z").workplane()
            .hole(1.8, 6)
        )
        body = body.union(boss)

    # Place at rear of head
    body = body.translate((0, 2, -(HEAD_R + CLASP_D / 2 - 4)))
    return body


def part_rear_clasp_lid() -> cq.Workplane:
    lid = (
        cq.Workplane("XY")
        .box(CLASP_W - WALL - 0.4, CLASP_H - WALL - 0.4, 2.4)
        .edges("|Z").fillet(4)
        .edges("#Z").fillet(0.8)
        .translate((0, 2, -(HEAD_R - 2)))
    )
    return lid


def try_fillet(wp: cq.Workplane, r: float) -> cq.Workplane:
    try:
        return wp.edges().fillet(r)
    except Exception:
        return wp


def build_assembly() -> cq.Assembly:
    asm = cq.Assembly(name="UnityX_Headband")

    # Band spine
    try:
        band = part_band_spine()
    except Exception as ex:
        print("sweep spine failed:", ex)
        band = part_band_spine_robust()
    asm.add(band, name="01_BandSpine", color=cq.Color(0.45, 0.50, 0.88, 1))

    pod = part_sensor_pod_base()
    asm.add(pod, name="02_SensorPod_Base", color=cq.Color(0.30, 0.34, 0.48, 1))

    lid = part_sensor_pod_lid()
    asm.add(lid, name="03_SensorPod_Lid", color=cq.Color(0.55, 0.60, 0.92, 1))

    for side, name in ((-1.0, "04_TemplePad_L"), (1.0, "05_TemplePad_R")):
        asm.add(part_temple_pad(side), name=name, color=cq.Color(0.40, 0.44, 0.70, 1))

    clasp = part_rear_clasp()
    asm.add(clasp, name="06_RearClasp_Base", color=cq.Color(0.22, 0.24, 0.30, 1))

    clasp_lid = part_rear_clasp_lid()
    asm.add(clasp_lid, name="07_RearClasp_Lid", color=cq.Color(0.28, 0.30, 0.36, 1))

    return asm


def export_part(wp: cq.Workplane | cq.Shape, stem: str) -> None:
    stl = OUT / f"{stem}.stl"
    step = OUT / f"{stem}.step"
    cq.exporters.export(wp, str(stl))
    cq.exporters.export(wp, str(step))
    print(f"  {stem}: {stl.stat().st_size/1024:.0f} KB stl")


def main() -> None:
    print("Building unity-X headband CAD…")
    asm = build_assembly()

    step_path = OUT / "UnityX_Headband.step"
    asm.save(str(step_path))
    print(f"Assembly STEP: {step_path} ({step_path.stat().st_size/1024:.1f} KB)")

    # Also export each solid as STL for print / MeshMixer
    print("Per-part exports:")
    # Re-build individuals for clean export (assembly locators already applied)
    try:
        band = part_band_spine()
    except Exception:
        band = part_band_spine_robust()
    export_part(band, "01_BandSpine")
    export_part(part_sensor_pod_base(), "02_SensorPod_Base")
    export_part(part_sensor_pod_lid(), "03_SensorPod_Lid")
    export_part(part_temple_pad(-1), "04_TemplePad_L")
    export_part(part_temple_pad(1), "05_TemplePad_R")
    export_part(part_rear_clasp(), "06_RearClasp_Base")
    export_part(part_rear_clasp_lid(), "07_RearClasp_Lid")

    # Combined preview STL
    combined = band
    for p in (
        part_sensor_pod_base(),
        part_sensor_pod_lid(),
        part_temple_pad(-1),
        part_temple_pad(1),
        part_rear_clasp(),
        part_rear_clasp_lid(),
    ):
        combined = combined.union(p)
    cq.exporters.export(combined, str(OUT / "00_Preview_Combined.stl"))
    cq.exporters.export(combined, str(OUT / "00_Preview_Combined.step"))
    print(f"Preview: {OUT / '00_Preview_Combined.stl'}")
    print("Done.")


if __name__ == "__main__":
    main()
