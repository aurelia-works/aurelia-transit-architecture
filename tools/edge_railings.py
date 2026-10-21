"""Edge railings (Jah, 2026-10-21: "it should be on the end of the block ... there should be a corner piece"): the models and
blockstate parts for railings that stand on a side of their block, flush with the drop they guard.

Each edge model is the block's own centre post with its two arms turned to run along x, moved flush with the north face;
the blockstate turns it to the other sides. A railing with any edge set shows only its edge rails, so the centre post and
arms gain "no edge" conditions. The glass barrier gets an "edge" variant with its panel flush with its front (north) face.

Corner posts (Jah, 2026-10-21, "missing gaps"): where two edge runs meet round an outside corner the rails touch only at a
point; the "corner" state puts a post of the railing's own texture just past the end of its north rail (clockwise: east,
anticlockwise: west), over the gap.

Idempotent: run it again after the centre models change. Does not use generate_assets.py, which wipes hand-made assets.
Usage: python3 tools/edge_railings.py
"""
import copy
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "aurelia_transit_architecture")
MOD = "aurelia_transit_architecture"
SIDES = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
# railing block -> {kind: (post model, arm model)}
RAILINGS = {
    "handrail": {k: ("handrail_post", "handrail_side_" + k) for k in ("handrail", "balustrade", "ramp_rail")},
    "station_fence": {k: ("station_fence_post_" + k, "station_fence_side_" + k) for k in ("platform", "trackside")},
}
NO_EDGE = [{"edge_" + side: "false"} for side, _ in SIDES]
FACE_TURN = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}


def load(path):
    with open(path) as f:
        return json.load(f)


def save(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def model_path(name):
    return os.path.join(ROOT, "models", "block", name + ".json")


def turn(element, quarters):
    """The element turned clockwise (seen from above) by quarter turns about the block's centre: north becomes east."""
    e = copy.deepcopy(element)
    assert "rotation" not in e, "turned elements must be axis-aligned"
    for _ in range(quarters % 4):
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        e["from"], e["to"] = [16 - z1, y0, x0], [16 - z0, y1, x1]
        faces = {}
        for name, face in e["faces"].items():
            face = dict(face)
            if "cullface" in face:
                face["cullface"] = FACE_TURN[face["cullface"]]
            faces[FACE_TURN[name]] = face
        e["faces"] = faces
    return e


def shift_z(element, dz):
    e = copy.deepcopy(element)
    e["from"][2] = round(e["from"][2] + dz, 4)
    e["to"][2] = round(e["to"][2] + dz, 4)
    for name, face in e["faces"].items():
        # a face that now lies on the block's north face culls against it; inner faces never cull
        if name == "north" and e["from"][2] == 0:
            face["cullface"] = "north"
        elif face.get("cullface") in ("north", "south"):
            del face["cullface"]
    return e


def edge_model(post, arm):
    """Post and both arms along x, flush with the north face."""
    p, a = load(model_path(post)), load(model_path(arm))
    elements = [copy.deepcopy(e) for e in p["elements"]] + [turn(e, 1) for e in a["elements"]] + [turn(e, 3) for e in a["elements"]]
    dz = -min(e["from"][2] for e in elements)
    textures = dict(p.get("textures", {}))
    textures.update(a.get("textures", {}))
    return {"parent": "block/block", "textures": textures, "elements": [shift_z(e, dz) for e in elements]}


def corner_model(post, clockwise):
    """A post just past the east end (west, anticlockwise) of a north edge rail, over the gap at an outside corner."""
    p = load(model_path(post))
    def faces(element):
        return {name: {k: v for k, v in face.items() if k not in ("uv", "cullface")} for name, face in element["faces"].items()}

    shaft = p["elements"][0]
    top = shaft["to"][1]
    boxes = [([15.5, 0, 0], [19.5, top, 3.5], faces(shaft))]
    if top < 16:
        cap = p["elements"][1] if len(p["elements"]) > 1 else shaft
        boxes.append(([15, top, -0.5], [20, 16, 4], faces(cap)))
    elements = []
    for lo, hi, f in boxes:
        if not clockwise:
            lo, hi = [16 - hi[0], lo[1], lo[2]], [16 - lo[0], hi[1], hi[2]]
        elements.append({"from": lo, "to": hi, "faces": f})
    return {"parent": "block/block", "textures": dict(p.get("textures", {})), "elements": elements}


def railings():
    for block, kinds in RAILINGS.items():
        path = os.path.join(ROOT, "blockstates", block + ".json")
        state = load(path)
        parts = []
        for part in state["multipart"]:
            model = part["apply"]["model"]
            if model.split("/")[-1].startswith((block + "_edge_", block + "_corner_")):
                continue  # written below
            when = part["when"]
            terms = when["AND"] if "AND" in when else [{k: v} for k, v in when.items()]
            terms = [t for t in terms if not any(k.startswith("edge_") for k in t)] + NO_EDGE
            parts.append({"when": {"AND": terms}, "apply": part["apply"]})
        for kind, (post, arm) in kinds.items():
            name = block + "_edge_" + kind
            save(model_path(name), edge_model(post, arm))
            for side, y in SIDES:
                apply = {"model": MOD + ":block/" + name}
                if y:
                    apply["y"] = y
                parts.append({"when": {"AND": [{"kind": kind}, {"edge_" + side: "true"}]}, "apply": apply})
            for end in ("cw", "ccw"):
                corner = block + "_corner_" + end + "_" + kind
                save(model_path(corner), corner_model(post, end == "cw"))
                for side, y in SIDES:
                    apply = {"model": MOD + ":block/" + corner}
                    if y:
                        apply["y"] = y
                    parts.append({"when": {"AND": [{"kind": kind}, {"corner": side + "_" + end}]}, "apply": apply})
        state["multipart"] = parts
        save(path, state)


def glass_barrier():
    base = load(model_path("glass_barrier"))
    edge = copy.deepcopy(base)
    dz = -min(e["from"][2] for e in edge["elements"])
    edge["elements"] = [shift_z(e, dz) for e in edge["elements"]]
    save(model_path("glass_barrier_edge"), edge)
    variants = {}
    for side, y in SIDES:
        for flag, name in (("false", "glass_barrier"), ("true", "glass_barrier_edge")):
            apply = {"model": MOD + ":block/" + name}
            if y:
                apply["y"] = y
            variants["edge=" + flag + ",facing=" + side] = apply
    save(os.path.join(ROOT, "blockstates", "glass_barrier.json"), {"variants": variants})


if __name__ == "__main__":
    railings()
    glass_barrier()
    print("edge railings written")
