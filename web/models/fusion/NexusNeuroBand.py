#Author-unity-X
#Description-Parametric unity-X sleep headband (mm). Run in Fusion: Utilities > Add-Ins > Scripts and Add-Ins > Run.
#Compatible with Autodesk Fusion (Design workspace).

import math
import traceback

import adsk.core
import adsk.fusion


# --- User parameters (edit these, then re-run or change in Modify > Change Parameters) ---
HEAD_R = 95.0          # head / band major radius
BAND_R = 9.5           # fabric tube radius
BAND_START_DEG = -110  # open rear clasp gap
BAND_END_DEG = 110
POD_RX, POD_RY, POD_RZ = 28.0, 12.0, 14.0
CLASP_RX, CLASP_RY, CLASP_RZ = 18.0, 10.0, 12.0


def _app():
    return adsk.core.Application.get()


def _ensure_param(design: adsk.fusion.Design, name: str, value: float, comment: str):
    params = design.userParameters
    existing = params.itemByName(name)
    expr = f"{value} mm"
    if existing:
        existing.expression = expr
        existing.comment = comment
    else:
        params.add(
            name,
            adsk.core.ValueInput.createByString(expr),
            "mm",
            comment,
        )


def _new_component(root: adsk.fusion.Component, name: str) -> adsk.fusion.Component:
    occ = root.occurrences.addNewComponent(adsk.core.Matrix3D.create())
    occ.component.name = name
    return occ.component


def _revolve_ellipse_y(
    comp: adsk.fusion.Component,
    rx: float,
    ry: float,
    name: str,
) -> adsk.fusion.BRepBody:
    """Create an ellipsoid-ish body by revolving a half-ellipse (XZ plane)."""
    sketches = comp.sketches
    xy = comp.xYConstructionPlane
    sk = sketches.add(xy)
    sk.name = f"{name}_profile"
    lines = sk.sketchCurves.sketchLines
    arcs = sk.sketchCurves.sketchArcs

    # Half ellipse approximated with 3-point arcs + axis
    # Simpler: revolve a rectangle with filleted ends ≈ capsule, then scale Z.
    # Clean approach for Fusion beginners: sphere + non-uniform scale via Extrude/Loft is hard.
    # Use revolve of a half-circle scaled: create ellipse then revolve 360 around Y.

    # Draw ellipse centered at origin, in XY, then revolve around Y
    ellipses = sk.sketchCurves.sketchEllipses
    center = adsk.core.Point3D.create(0, 0, 0)
    major = adsk.core.Point3D.create(rx, 0, 0)
    point = adsk.core.Point3D.create(0, ry, 0)
    ellipses.add(center, major, point)

    # Split is unnecessary if we revolve full ellipse around Y — that creates a torus-like fail.
    # For ellipsoid of revolution around Y: profile must be half-ellipse in X>0.
    # Delete full ellipse and draw half with fit spline or two arcs.

    # Clear sketch and draw half ellipse with lines + spline
    for c in list(sk.sketchCurves):
        c.deleteMe()

    # Vertical axis (revolve axis)
    axis = lines.addByTwoPoints(
        adsk.core.Point3D.create(0, -ry, 0),
        adsk.core.Point3D.create(0, ry, 0),
    )
    axis.isConstruction = True

    # Half ellipse via fit points
    pts = adsk.core.ObjectCollection.create()
    steps = 16
    for i in range(steps + 1):
        t = -math.pi / 2 + math.pi * i / steps
        x = rx * math.cos(t)
        y = ry * math.sin(t)
        if x < 0:
            x = 0
        pts.add(adsk.core.Point3D.create(x, y, 0))
    spline = sk.sketchCurves.sketchFittedSplines.add(pts)

    # Close with axis endpoints already on Y; add lines from ends to axis if needed
    # First/last points are at (0,-ry) and (0,ry) — profile is closed against axis.

    prof = sk.profiles.item(0)
    revolves = comp.features.revolveFeatures
    rev_input = revolves.createInput(
        prof,
        axis,
        adsk.fusion.FeatureOperations.NewBodyFeatureOperation,
    )
    ang = adsk.core.ValueInput.createByReal(math.pi * 2)
    rev_input.setAngleExtent(False, ang)
    feat = revolves.add(rev_input)
    body = feat.bodies.item(0)
    body.name = name
    return body


def _torus_arc_band(comp: adsk.fusion.Component) -> adsk.fusion.BRepBody:
    """Sweep a circle along a circular path arc (forehead band)."""
    sketches = comp.sketches
    xz = comp.xZConstructionPlane
    path_sk = sketches.add(xz)
    path_sk.name = "BandPath"

    # Arc on XZ plane: center origin, radius HEAD_R, from start to end angle
    # In Fusion sketch on XZ: X horizontal, Z vertical in sketch Y? Actually XZ plane:
    # sketch x = model X, sketch y = model Z.
    start_a = math.radians(BAND_START_DEG)
    end_a = math.radians(BAND_END_DEG)
    mid_a = (start_a + end_a) / 2

    p1 = adsk.core.Point3D.create(HEAD_R * math.cos(start_a), HEAD_R * math.sin(start_a), 0)
    p2 = adsk.core.Point3D.create(HEAD_R * math.cos(mid_a), HEAD_R * math.sin(mid_a), 0)
    p3 = adsk.core.Point3D.create(HEAD_R * math.cos(end_a), HEAD_R * math.sin(end_a), 0)
    # On XZ sketch: Point3D(x, y_sketch, 0) maps to (x, 0, y_sketch)
    # So use (x, z, 0) in sketch coords:
    sp1 = adsk.core.Point3D.create(HEAD_R * math.cos(start_a), HEAD_R * math.sin(start_a), 0)
    sp2 = adsk.core.Point3D.create(HEAD_R * math.cos(mid_a), HEAD_R * math.sin(mid_a), 0)
    sp3 = adsk.core.Point3D.create(HEAD_R * math.cos(end_a), HEAD_R * math.sin(end_a), 0)
    arc = path_sk.sketchCurves.sketchArcs.addByThreePoints(sp1, sp2, sp3)

    # Profile plane at start of arc — use plane normal to path
    # Create sketch on plane perpendicular to path at start
    # Easier: use Pipe feature if available (Fusion has Pipe).
    # Pipe: select path, circular section.

    path = adsk.fusion.Path.create(arc, False)
    pipes = comp.features.pipeFeatures
    # Pipe API: PipeFeatureInput
    try:
        pipe_input = pipes.createInput(path, adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
        pipe_input.sectionRadius = adsk.core.ValueInput.createByReal(BAND_R / 10.0)  # cm? Fusion internal = cm
        # Fusion API uses cm as internal unit for ValueInput.createByReal
        pipe_input.sectionRadius = adsk.core.ValueInput.createByReal(BAND_R / 10.0)
        pipe = pipes.add(pipe_input)
        body = pipe.bodies.item(0)
        body.name = "FabricBand"
        # Lift band +8 mm in Y
        bodies = adsk.core.ObjectCollection.create()
        bodies.add(body)
        transform = adsk.core.Matrix3D.create()
        transform.translation = adsk.core.Vector3D.create(0, 0.8, 0)  # 8 mm = 0.8 cm
        move_input = comp.features.moveFeatures.createInput(bodies, transform)
        comp.features.moveFeatures.add(move_input)
        return body
    except Exception:
        # Fallback: revolve torus then cut — if Pipe unavailable
        raise


def _sphere_scaled(
    root: adsk.fusion.Component,
    parent: adsk.fusion.Component,
    name: str,
    rx_mm: float,
    ry_mm: float,
    rz_mm: float,
    cx_mm: float,
    cy_mm: float,
    cz_mm: float,
) -> None:
    """Create unit sphere and nonuniform scale + move (mm → cm)."""
    # Create sphere via revolve of semicircle
    sk = parent.sketches.add(parent.xYConstructionPlane)
    sk.name = f"{name}_sk"
    lines = sk.sketchCurves.sketchLines
    arcs = sk.sketchCurves.sketchArcs
    axis = lines.addByTwoPoints(
        adsk.core.Point3D.create(0, -1, 0),
        adsk.core.Point3D.create(0, 1, 0),
    )
    axis.isConstruction = True
    arcs.addByCenterStartSweep(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(0, -1, 0),
        math.pi,
    )
    # Close with axis — profile for half-disk
    # Add diameter line
    lines.addByTwoPoints(
        adsk.core.Point3D.create(0, -1, 0),
        adsk.core.Point3D.create(0, 1, 0),
    )
    # Prefer full revolve of semicircle
    for c in list(sk.sketchCurves):
        c.deleteMe()
    axis = lines.addByTwoPoints(
        adsk.core.Point3D.create(0, -1, 0),
        adsk.core.Point3D.create(0, 1, 0),
    )
    arcs.addByCenterStartSweep(
        adsk.core.Point3D.create(0, 0, 0),
        adsk.core.Point3D.create(0, -1, 0),
        math.pi,
    )
    prof = None
    for i in range(sk.profiles.count):
        p = sk.profiles.item(i)
        if p.profileType == adsk.fusion.ProfileTypes.RegionProfileType:
            # area check
            props = p.areaProperties()
            if props.area > 0.1:
                prof = p
                break
    if prof is None and sk.profiles.count > 0:
        prof = sk.profiles.item(0)

    rev = parent.features.revolveFeatures
    rev_in = rev.createInput(prof, axis, adsk.fusion.FeatureOperations.NewBodyFeatureOperation)
    rev_in.setAngleExtent(False, adsk.core.ValueInput.createByReal(math.pi * 2))
    feat = rev.add(rev_in)
    body = feat.bodies.item(0)
    body.name = name

    # Non-uniform scale: Scale feature
    coll = adsk.core.ObjectCollection.create()
    coll.add(body)
    scales = parent.features.scaleFeatures
    # Uniform first won't work for ellipsoid — use Scale with point and factors
    # Fusion ScaleFeature supports non-uniform via ScaleFeatureInput.isUniform = False
    pt = adsk.core.Point3D.create(0, 0, 0)
    scale_in = scales.createInput(coll, pt)
    scale_in.isUniform = False
    scale_in.setScaleFactors(
        adsk.core.ValueInput.createByReal(rx_mm / 10.0),  # radius was 1 cm
        adsk.core.ValueInput.createByReal(ry_mm / 10.0),
        adsk.core.ValueInput.createByReal(rz_mm / 10.0),
    )
    scales.add(scale_in)

    # Move to position (mm → cm)
    bodies = adsk.core.ObjectCollection.create()
    bodies.add(body)
    t = adsk.core.Matrix3D.create()
    t.translation = adsk.core.Vector3D.create(cx_mm / 10.0, cy_mm / 10.0, cz_mm / 10.0)
    parent.features.moveFeatures.add(parent.features.moveFeatures.createInput(bodies, t))


def run(context):
    ui = None
    try:
        app = _app()
        ui = app.userInterface
        product = app.activeProduct
        design = adsk.fusion.Design.cast(product)
        if not design:
            ui.messageBox("Önce Design workspace'te yeni bir tasarım aç (File > New Design).")
            return

        design.designType = adsk.fusion.DesignTypes.ParametricDesignType
        root = design.rootComponent
        root.name = "UnityXBand"

        _ensure_param(design, "HeadRadius", HEAD_R, "Band major radius")
        _ensure_param(design, "BandTubeRadius", BAND_R, "Fabric tube radius")
        _ensure_param(design, "PodRX", POD_RX, "Forehead pod X radius")
        _ensure_param(design, "PodRY", POD_RY, "Forehead pod Y radius")
        _ensure_param(design, "PodRZ", POD_RZ, "Forehead pod Z radius")

        # Clear previous generated bodies if re-running in same file is messy —
        # create under a fresh component each run with timestamp-ish name.
        band_comp = _new_component(root, "01_FabricBand")
        pod_comp = _new_component(root, "02_SensorPod")
        pads_comp = _new_component(root, "03_TemplePads")
        clasp_comp = _new_component(root, "04_RearClasp")

        # --- Band via Pipe ---
        try:
            _torus_arc_band(band_comp)
        except Exception as ex:
            ui.messageBox(
                "Bant (Pipe) oluşturulamadı.\n"
                "Manuel: Sketch'te yay çiz → Solid > Pipe.\n\n"
                f"Detay: {ex}"
            )

        # --- Forehead pod ---
        _sphere_scaled(root, pod_comp, "SensorPod", POD_RX, POD_RY, POD_RZ, 0, 10, 95)

        # Accent plate
        sk = pod_comp.sketches.add(pod_comp.xYConstructionPlane)
        # Create box via Extrude
        # Simpler box: two-point rectangle then extrude
        # Position with move after creating at origin
        box_sk = pod_comp.sketches.add(pod_comp.xZConstructionPlane)
        box_sk.name = "PodPlate"
        # On XZ: rectangle 22 x 10 mm centered, then extrude 3 mm in Y
        lines = box_sk.sketchCurves.sketchLines
        hx, hz = 1.1, 0.5  # cm half-sizes (22mm, 10mm)
        lines.addTwoPointRectangle(
            adsk.core.Point3D.create(-hx, -hz, 0),
            adsk.core.Point3D.create(hx, hz, 0),
        )
        if box_sk.profiles.count > 0:
            ext = pod_comp.features.extrudeFeatures
            ext_in = ext.createInput(
                box_sk.profiles.item(0),
                adsk.fusion.FeatureOperations.NewBodyFeatureOperation,
            )
            ext_in.setDistanceExtent(False, adsk.core.ValueInput.createByReal(0.3))  # 3 mm
            plate = ext.add(ext_in)
            plate.bodies.item(0).name = "AccentPlate"
            bodies = adsk.core.ObjectCollection.create()
            bodies.add(plate.bodies.item(0))
            t = adsk.core.Matrix3D.create()
            t.translation = adsk.core.Vector3D.create(0, 1.0, 10.8)  # y=10mm, z=108mm
            pod_comp.features.moveFeatures.add(
                pod_comp.features.moveFeatures.createInput(bodies, t)
            )

        # LED
        _sphere_scaled(root, pod_comp, "StatusLED", 3.2, 2.2, 2.2, 0, 14, 109)

        # Temple pads
        for side, label in ((-1, "LeftPad"), (1, "RightPad")):
            ang = math.radians(side * 55)
            x = HEAD_R * math.cos(ang)
            z = HEAD_R * math.sin(ang)
            _sphere_scaled(root, pads_comp, label, 10, 5, 8, x, 6, z)

        # Rear clasp
        _sphere_scaled(root, clasp_comp, "BatteryClasp", CLASP_RX, CLASP_RY, CLASP_RZ, 0, 6, -92)

        ui.messageBox(
            "unity-X band bileşenleri oluşturuldu.\n\n"
            "Browser: 01_FabricBand / 02_SensorPod / 03_TemplePads / 04_RearClasp\n"
            "Ölçüler: Modify > Change Parameters\n\n"
            "İpucu: Autodesk Assistant'a şunu yaz:\n"
            "\"Select all bodies, add 1 mm fillet on sharp edges. "
            "Propose steps first and wait for confirmation.\""
        )

    except Exception:
        if ui:
            ui.messageBox(f"Hata:\n{traceback.format_exc()}")


def stop(context):
    pass
