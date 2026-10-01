"""Report block-model faces that can z-fight: two elements whose same-facing faces lie in one plane, overlap,
use different textures and are not covered by a third element. Unrotated elements only.
Usage: python3 tools/check_zfighting.py   (exit code 1 if anything is found)"""
import glob, json, os, sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/aurelia_transit_architecture/models/block")
AXIS = {"down": (1, 0), "up": (1, 1), "north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1)}


def texture(model, ref):
    for _ in range(5):
        if not ref or not ref.startswith("#"):
            break
        ref = model.get("textures", {}).get(ref[1:], ref)
    return ref


def findings(model):
    els = [e for e in model.get("elements", []) if "rotation" not in e]
    for i, a in enumerate(els):
        for b in els[i + 1:]:
            for face, (ax, high) in AXIS.items():
                fa, fb = a.get("faces", {}).get(face), b.get("faces", {}).get(face)
                if not fa or not fb or texture(model, fa.get("texture")) == texture(model, fb.get("texture")):
                    continue
                plane = a["to"][ax] if high else a["from"][ax]
                if plane != (b["to"][ax] if high else b["from"][ax]):
                    continue
                other = [k for k in range(3) if k != ax]
                lo = [max(a["from"][k], b["from"][k]) for k in other]
                hi = [min(a["to"][k], b["to"][k]) for k in other]
                if any(h <= l for l, h in zip(lo, hi)):
                    continue
                probe = [0.0, 0.0, 0.0]
                probe[ax] = plane + (0.01 if high else -0.01)
                probe[other[0]], probe[other[1]] = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
                if any(all(e["from"][k] < probe[k] < e["to"][k] for k in range(3)) for e in els):
                    continue
                yield f"{face}@{plane}"


bad = 0
for path in sorted(glob.glob(os.path.join(ROOT, "*.json"))):
    found = sorted(set(findings(json.load(open(path)))))
    if found:
        bad += 1
        print(os.path.basename(path), found)
print(f"z-fighting check: {bad} model(s) flagged")
sys.exit(1 if bad else 0)
