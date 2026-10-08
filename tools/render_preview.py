#!/usr/bin/env python3
"""
Offline model preview renderer: draws every block model of the mod as a small isometric picture, with no GPU and no game.

Resolves blockstates (variants and multipart) and model parent chains. Vanilla models and textures are read straight from
the Minecraft 1.20.1 client jar that Fabric Loom has cached under ~/.gradle/caches/fabric-loom/ (nothing is copied into
the repo); `mtr:` parents come from the MTR jar in the Gradle cache. Needs Pillow only.

Supported: element from/to, per-face UVs and rotation, element rotation (origin, axis, angle, rescale), blockstate x/y
rotation, uvlock (top and bottom faces), translucent textures, simple directional shading, painter's algorithm.

Output goes to build/previews/ (git-ignored):
  tab_<id>.png            one contact sheet per creative tab, each block in its default state
  orientation_<tab>.png   every block with a facing or axis property: all 4 facings (or 3 axes) in iso and plan view, with an
                          arrow for the direction the player looks while placing it
  panes.png               glass pane connection states (post, one side, corner, T, cross)
  tiled_walls.png         3x3 walls of every full-cube glass and station material

Usage: python3 tools/render_preview.py [--tab <id>] [--block <id>] [--changed]
       --changed  only blocks whose generated files differ from `git merge-base HEAD release/1.3.1`
"""
import argparse
import glob
import io
import json
import math
import re
import subprocess
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

MOD = "aurelia_transit_architecture"
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / MOD
REGISTRY = ROOT / "src" / "main" / "java" / "com" / "aureliatransit" / "architecture" / "registry"
OUT = ROOT / "build" / "previews"
BASE_BRANCH = "release/1.3.1"
HORIZONTAL = ["north", "east", "south", "west"]
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}

# ---------------------------------------------------------------------------------------------------------------------
# Asset lookup: this mod's resources first, then the vanilla client jar, then the MTR jar
# ---------------------------------------------------------------------------------------------------------------------


def find_jar(patterns):
    home = Path.home()
    for pattern in patterns:
        hits = sorted(glob.glob(str(home / pattern), recursive=True))
        if hits:
            return Path(hits[0])
    return None


class Assets:
    def __init__(self):
        self.vanilla = find_jar([".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar", ".gradle/caches/fabric-loom/**/minecraft-client*.jar",
                                 ".gradle/caches/fabric-loom/**/minecraft-merged*.jar"])
        self.mtr = find_jar([".gradle/caches/modules-2/files-2.1/maven.modrinth/minecraft-transit-railway/FABRIC-*/**/*.jar"])
        self.zips = {}
        for name, jar in (("minecraft", self.vanilla), ("mtr", self.mtr)):
            if jar:
                self.zips[name] = zipfile.ZipFile(jar)
        if not self.vanilla:
            print("warning: Minecraft client jar not found under ~/.gradle/caches/fabric-loom; vanilla parents will be missing", file=sys.stderr)
        self.model_cache, self.texture_cache = {}, {}

    @staticmethod
    def split(ref):
        return ref.split(":", 1) if ":" in ref else ("minecraft", ref)

    def read(self, ns, rel):
        """Bytes of assets/<ns>/<rel> from the repo (own namespace) or a jar; None if absent."""
        if ns == MOD:
            path = ASSETS / rel
            if path.is_file():
                return path.read_bytes()
            return None
        archive = self.zips.get(ns)
        if archive:
            try:
                return archive.read(f"assets/{ns}/{rel}")
            except KeyError:
                return None
        return None

    def model(self, ref):
        if ref in self.model_cache:
            return self.model_cache[ref]
        ns, path = self.split(ref)
        data = self.read(ns, f"models/{path}.json")
        model = json.loads(data) if data else None
        self.model_cache[ref] = model
        return model

    def texture(self, ref):
        ns, path = self.split(ref)
        key = f"{ns}:{path}"
        if key in self.texture_cache:
            return self.texture_cache[key]
        data = self.read(ns, f"textures/{path}.png")
        if data is None:
            image = missing_texture()
        else:
            image = Image.open(io.BytesIO(data)).convert("RGBA")
            if image.height > image.width and image.height % image.width == 0:  # animated strip: first frame
                image = image.crop((0, 0, image.width, image.width))
        self.texture_cache[key] = image
        return image


def missing_texture():
    image = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    ImageDraw.Draw(image).rectangle((0, 0, 7, 7), fill=(0, 0, 0, 255))
    ImageDraw.Draw(image).rectangle((8, 8, 15, 15), fill=(0, 0, 0, 255))
    return image


ASSET = Assets()

# ---------------------------------------------------------------------------------------------------------------------
# Model resolution
# ---------------------------------------------------------------------------------------------------------------------


def resolve_model(ref):
    """Flattened model: {'elements': [...], 'textures': {...}} following the parent chain (child wins)."""
    chain, seen = [], set()
    current = ref
    while current and current not in seen:
        seen.add(current)
        model = ASSET.model(current)
        if model is None:
            if not current.startswith("builtin/") and not current.endswith("builtin/generated"):
                print(f"  warning: model {current} not found", file=sys.stderr)
            break
        chain.append(model)
        current = model.get("parent")
    textures, elements = {}, None
    for model in reversed(chain):
        textures.update(model.get("textures", {}))
    for model in chain:
        if "elements" in model:
            elements = model["elements"]
            break
    return {"elements": elements or [], "textures": textures}


def resolve_texture(textures, ref):
    for _ in range(12):
        if ref is None:
            return None
        if ref.startswith("#"):
            ref = textures.get(ref[1:])
        else:
            return ref
    return None


def parse_key(key):
    return dict(kv.split("=", 1) for kv in key.split(",") if kv)


def condition_matches(when, state):
    if "OR" in when:
        return any(condition_matches(c, state) for c in when["OR"])
    if "AND" in when:
        return all(condition_matches(c, state) for c in when["AND"])
    for prop, wanted in when.items():
        if str(state.get(prop)) not in str(wanted).split("|"):
            return False
    return True


def blockstate_models(blockstate, state):
    """[(model_ref, x, y, uvlock)] for a property dict."""
    result = []
    if "variants" in blockstate:
        best, best_score = None, -1
        for key, variant in blockstate["variants"].items():
            wanted = parse_key(key)
            if all(state.get(k) == v for k, v in wanted.items()) and len(wanted) > best_score:
                best, best_score = variant, len(wanted)
        if best is not None:
            variant = best[0] if isinstance(best, list) else best
            result.append((variant["model"], variant.get("x", 0), variant.get("y", 0), variant.get("uvlock", False)))
    for part in blockstate.get("multipart", []):
        if "when" not in part or condition_matches(part["when"], state):
            variant = part["apply"][0] if isinstance(part["apply"], list) else part["apply"]
            result.append((variant["model"], variant.get("x", 0), variant.get("y", 0), variant.get("uvlock", False)))
    return result


def state_domains(blockstate):
    """Property -> set of values mentioned in a blockstate."""
    domains = {}
    if "variants" in blockstate:
        for key in blockstate["variants"]:
            for prop, value in parse_key(key).items():
                domains.setdefault(prop, set()).add(value)

    def walk(when):
        for prop, wanted in when.items():
            if prop in ("OR", "AND"):
                for sub in wanted:
                    walk(sub)
            else:
                domains.setdefault(prop, set()).update(str(wanted).split("|"))

    for part in blockstate.get("multipart", []):
        if "when" in part:
            walk(part["when"])
    return domains


def default_state(domains):
    state = {}
    for prop, values in domains.items():
        for preferred in ("north", "false", "y", "none", "0", "1"):
            if preferred in values:
                state[prop] = preferred
                break
        else:
            state[prop] = sorted(values)[0]
    return state


# ---------------------------------------------------------------------------------------------------------------------
# Geometry
# ---------------------------------------------------------------------------------------------------------------------

# Face corners (top-left, top-right, bottom-right, bottom-left as seen from outside) given as picks from (x1,y1,z1,x2,y2,z2).
FACE_CORNERS = {
    "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    "west": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    "east": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    "down": [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}
FACE_NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def default_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {"north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1], "west": [z1, 16 - y2, z2, 16 - y1],
            "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1], "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1]}[face]


def rot_axis(p, axis, deg):
    """Right-handed rotation of p about an axis through the origin."""
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p
    if axis == "x":
        return (x, y * c - z * s, y * s + z * c)
    if axis == "y":
        return (x * c + z * s, y, -x * s + z * c)
    return (x * c - y * s, x * s + y * c, z)


def sub(a, b):
    return tuple(i - j for i, j in zip(a, b))


def add(a, b):
    return tuple(i + j for i, j in zip(a, b))


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(i * j for i, j in zip(a, b))


def norm(a):
    length = math.sqrt(dot(a, a)) or 1
    return tuple(i / length for i in a)


class Quad:
    __slots__ = ("corners", "uvs", "texture", "shade", "double")

    def __init__(self, corners, uvs, texture, shade, double):
        self.corners, self.uvs, self.texture, self.shade, self.double = corners, uvs, texture, shade, double


def shade_for(normal):
    nx, ny, nz = normal
    return nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * (1.0 if ny > 0 else 0.5)


def model_quads(model_ref, rx=0, ry=0, uvlock=False, offset=(0, 0, 0)):
    """World-space quads (block units 0..16, y up, z south) of a model after blockstate rotation."""
    model = resolve_model(model_ref)
    quads = []
    for element in model["elements"]:
        f, t = element["from"], element["to"]
        rotation = element.get("rotation")
        flat = [t[i] == f[i] for i in range(3)]
        for face, spec in element.get("faces", {}).items():
            texture_ref = resolve_texture(model["textures"], spec.get("texture"))
            if texture_ref is None:
                continue
            texture = ASSET.texture(texture_ref)
            corners = []
            for pick in FACE_CORNERS[face]:
                p = tuple((t if pick[i] else f)[i] for i in range(3))
                if rotation:
                    origin = tuple(rotation.get("origin", [8, 8, 8]))
                    q = rot_axis(sub(p, origin), rotation["axis"], rotation["angle"])
                    if rotation.get("rescale"):
                        k = 1 / math.cos(math.radians(abs(rotation["angle"])))
                        q = tuple(q[i] * (1 if "xyz"[i] == rotation["axis"] else k) for i in range(3))
                    p = add(q, origin)
                p = sub(p, (8, 8, 8))
                p = rot_axis(p, "x", -rx)
                p = rot_axis(p, "y", -ry)
                corners.append(add(add(p, (8, 8, 8)), offset))
            uv = list(spec.get("uv") or default_uv(face, f, t))
            quarter = int(spec.get("rotation", 0) / 90)
            if uvlock and rx == 0 and ry and face in ("up", "down"):
                quarter += int(((-ry if face == "up" else ry) % 360) / 90)
            u1, v1, u2, v2 = uv
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            uvs = [uvs[(i + quarter) % 4] for i in range(4)]
            normal = norm(cross(sub(corners[1], corners[0]), sub(corners[3], corners[0])))
            normal = tuple(-c for c in normal)  # corner order is clockwise seen from outside
            quads.append(Quad(corners, uvs, texture, shade_for(normal), any(flat)))
    return quads


# ---------------------------------------------------------------------------------------------------------------------
# Rasteriser: affine-mapped textured parallelograms, painter's algorithm
# ---------------------------------------------------------------------------------------------------------------------

class Camera:
    def __init__(self, forward, up_hint, scale, centre=(8, 8, 8), eye_flip=True):
        self.f = norm(forward)
        self.r = norm(cross(self.f, up_hint))
        self.u = cross(self.r, self.f)
        self.scale, self.centre = scale, centre

    def project(self, p):
        q = sub(p, self.centre)
        return (dot(q, self.r) * self.scale, -dot(q, self.u) * self.scale, dot(q, self.f))


def iso_camera(scale, forward=(1, -0.7, 1)):
    """Looks from the north-west and above: north faces on the left, west faces on the right."""
    return Camera(forward, (0, 1, 0), scale)


def top_camera(scale):
    return Camera((0, -1, 0), (0, 0, -1), scale)


def end_camera(scale, look):
    """Elevation looking along the direction the player looks (shows the profile of a ridge or vault)."""
    forward = {"north": (0, 0, -1), "west": (-1, 0, 0), "south": (0, 0, 1), "east": (1, 0, 0)}[look]
    return Camera((forward[0], -0.22, forward[2]), (0, 1, 0), scale)  # slightly from above so thin roof panels show their slope


def side_camera(scale, look):
    """Elevation with the direction the player looks pointing to the right of the picture."""
    forward = {"north": (-1, 0, 0), "west": (0, 0, -1), "south": (1, 0, 0), "east": (0, 0, 1)}[look]
    return Camera(forward, (0, 1, 0), scale)


def render_quads(quads, camera, size, supersample=2, backdrop=None):
    """RGBA image of `size` px; the camera centre maps to the middle."""
    big = size * supersample
    canvas = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    mid = big / 2
    prepared = []
    for quad in quads:
        pts = []
        for c in quad.corners:
            x, y, depth = camera.project(c)
            pts.append((mid + x * supersample, mid + y * supersample, depth))
        normal = tuple(-c for c in norm(cross(sub(quad.corners[1], quad.corners[0]), sub(quad.corners[3], quad.corners[0]))))
        facing = dot(normal, camera.f)
        if facing >= -1e-6 and not quad.double:
            continue
        prepared.append((sum(p[2] for p in pts) / 4, pts, quad))
    prepared.sort(key=lambda item: -item[0])  # far first
    for _, pts, quad in prepared:
        draw_quad(canvas, pts, quad)
    return canvas.resize((size, size), Image.LANCZOS)


def draw_quad(canvas, pts, quad):
    a, b, d = pts[0], pts[1], pts[3]
    e1 = (b[0] - a[0], b[1] - a[1])
    e2 = (d[0] - a[0], d[1] - a[1])
    det = e1[0] * e2[1] - e1[1] * e2[0]
    if abs(det) < 1e-3:
        return
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    x0, y0 = max(0, int(math.floor(min(xs)))), max(0, int(math.floor(min(ys))))
    x1, y1 = min(canvas.width, int(math.ceil(max(xs)))), min(canvas.height, int(math.ceil(max(ys))))
    if x1 <= x0 or y1 <= y0:
        return
    # screen -> (s, t) in the parallelogram
    inv = ((e2[1] / det, -e2[0] / det), (-e1[1] / det, e1[0] / det))
    tex = quad.texture
    tw = tex.width / 16.0
    (ua, va), (ub, vb), _, (ud, vd) = quad.uvs
    # texture px = (uv_a + s*(uv_b-uv_a) + t*(uv_d-uv_a)) * tw
    du_s, dv_s = (ub - ua), (vb - va)
    du_t, dv_t = (ud - ua), (vd - va)

    # s = inv00*(X-ax) + inv01*(Y-ay); t = inv10*(X-ax) + inv11*(Y-ay)
    # PIL AFFINE: output pixel (x, y) -> input (A x + B y + C, D x + E y + F); sample at pixel centres.
    def coefs(U0, Us, Ut):
        # U = (U0 + s*Us + t*Ut) * tw ;  s,t linear in screen
        A = (Us * inv[0][0] + Ut * inv[1][0]) * tw
        B = (Us * inv[0][1] + Ut * inv[1][1]) * tw
        C = (U0 * tw) - A * a[0] - B * a[1]
        return A, B, C

    A, B, C = coefs(ua, du_s, du_t)
    D, E, F = coefs(va, dv_s, dv_t)
    # shift origin to the bounding box, sample from pixel centres
    # PIL evaluates the mapping at output pixel centres itself, so only the bounding-box origin needs shifting
    layer = tex.transform((x1 - x0, y1 - y0), Image.AFFINE, (A, B, C + A * x0 + B * y0, D, E, F + D * x0 + E * y0), Image.NEAREST)
    mask = Image.new("L", layer.size, 0)
    ImageDraw.Draw(mask).polygon([(p[0] - x0, p[1] - y0) for p in pts], fill=255)
    alpha = ImageChops.multiply(layer.getchannel("A"), mask)
    shade = quad.shade
    rgb = Image.merge("RGB", [band.point(lambda v: min(255, int(v * shade))) for band in layer.split()[:3]])
    layer = Image.merge("RGBA", (*rgb.split(), alpha))
    canvas.alpha_composite(layer, (x0, y0))


# ---------------------------------------------------------------------------------------------------------------------
# Registry knowledge parsed from the Java source (like tools/verify_assets.py)
# ---------------------------------------------------------------------------------------------------------------------

def parse_registry():
    """[{'id', 'family', 'tab', 'placement', 'tips'}] in creative-tab order."""
    family_tab = {}
    text = (REGISTRY / "BlockFamily.java").read_text(encoding="utf-8")
    order = []
    for name, tab in re.findall(r"^\s*([A-Z_]+)\(Tab\.([A-Z_]+)\)", text, re.M):
        family_tab[name] = tab
        order.append(name)
    tabs = dict(re.findall(r'^\s*([A-Z_]+)\("([a-z_]+)"\)', text, re.M))
    blocks = []
    for path in sorted(REGISTRY.glob("*Blocks*.java")):
        source = path.read_text(encoding="utf-8")
        for m in re.finditer(r'register\("([a-z0-9_]+)",\s*BlockFamily\.(\w+),', source):
            end = source.find(";\n", m.end())
            body = source[m.end():end]
            if "AWAY_FROM_PLAYER" in body:
                placement = "away"
            elif "TOWARD_PLAYER" in body:
                placement = "toward"
            else:
                placement = None
            tips = re.findall(r'TIP \+ "([a-z_]+)"', body) + re.findall(r"\bTIP_([A-Z_]+)\b", body)
            blocks.append({"id": m.group(1), "family": m.group(2), "tab": tabs[family_tab[m.group(2)]], "placement": placement,
                           "tips": [t.lower() for t in tips], "order": order.index(m.group(2)), "file": path.name})
    for block in blocks:
        if block["placement"] is None:
            if "faces_you" in block["tips"]:
                block["placement"] = "toward"
            elif "points_away" in block["tips"] or "wall_mounted" in block["tips"]:
                block["placement"] = "away"
    blocks_sorted = sorted(blocks, key=lambda b: b["order"])  # stable: registration order inside a family
    return blocks_sorted


def changed_files():
    """Repo-relative paths that differ from the merge base with the release branch (including uncommitted edits)."""
    try:
        base = subprocess.check_output(["git", "merge-base", "HEAD", BASE_BRANCH], cwd=ROOT, text=True).strip()
        out = subprocess.check_output(["git", "diff", "--name-only", base, "--", "src/main/resources"], cwd=ROOT, text=True)
        untracked = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "--", "src/main/resources"], cwd=ROOT, text=True)
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"warning: cannot compute --changed ({e}); rendering everything", file=sys.stderr)
        return None
    return set(out.split()) | set(untracked.split())


# ---------------------------------------------------------------------------------------------------------------------
# Block rendering
# ---------------------------------------------------------------------------------------------------------------------

class BlockInfo:
    def __init__(self, meta):
        self.meta = meta
        self.id = meta["id"]
        self.state_file = ASSETS / "blockstates" / f"{self.id}.json"
        self.blockstate = json.loads(self.state_file.read_text(encoding="utf-8")) if self.state_file.is_file() else None
        self.domains = state_domains(self.blockstate) if self.blockstate else {}
        self.default = default_state(self.domains)
        self.files = set()

    def quads(self, state, offset=(0, 0, 0)):
        if not self.blockstate:
            return []
        quads = []
        for ref, rx, ry, uvlock in blockstate_models(self.blockstate, state):
            quads += model_quads(ref, rx, ry, uvlock, offset)
        return quads

    def is_full_cube(self):
        if not self.blockstate:
            return False
        models = blockstate_models(self.blockstate, self.default)
        if len(models) != 1:
            return False
        m = resolve_model(models[0][0])
        els = m["elements"]
        return len(els) == 1 and els[0]["from"] == [0, 0, 0] and els[0]["to"] == [16, 16, 16] and "rotation" not in els[0] \
            and len(els[0].get("faces", {})) == 6

    def collect_files(self):
        """Repo files (blockstate, models, textures) this block depends on, over all its variants and multipart models."""
        if not self.blockstate:
            return
        self.files.add(self.state_file.relative_to(ROOT).as_posix())
        refs = set()
        for variant in self.blockstate.get("variants", {}).values():
            refs |= {v["model"] for v in (variant if isinstance(variant, list) else [variant])}
        for part in self.blockstate.get("multipart", []):
            refs |= {v["model"] for v in (part["apply"] if isinstance(part["apply"], list) else [part["apply"]])}
        for ref in refs:
            current, seen = ref, set()
            while current and current not in seen:
                seen.add(current)
                ns, path = Assets.split(current)
                model = ASSET.model(current)
                if ns == MOD:
                    self.files.add(f"src/main/resources/assets/{MOD}/models/{path}.json")
                if model is None:
                    break
                for texture in model.get("textures", {}).values():
                    tns, tpath = Assets.split(texture)
                    if not texture.startswith("#") and tns == MOD:
                        self.files.add(f"src/main/resources/assets/{MOD}/textures/{tpath}.png")
                current = model.get("parent")

    @property
    def has_facing(self):
        return "facing" in self.domains and set(self.domains["facing"]) >= set(HORIZONTAL)

    @property
    def has_axis(self):
        return "axis" in self.domains


CELL = 150
FONT = ImageFont.load_default()
BG = (58, 62, 70, 255)
CELL_BG = (72, 77, 87, 255)


def tint_backdrop(img):
    bg = Image.new("RGBA", img.size, CELL_BG)
    bg.alpha_composite(img)
    return bg


def iso_image(quads, size=CELL):
    return tint_backdrop(render_quads(quads, iso_camera(size / 29.0), size))


def plan_image(quads, size=CELL // 2 + 10):
    return tint_backdrop(render_quads(quads, top_camera(size / 17.0), size))


def put_label(draw, xy, text, fill=(230, 230, 230, 255)):
    draw.text(xy, text, fill=fill, font=FONT)


def sheet(cells, columns, title):
    """cells: [(image, label)] -> contact sheet image."""
    if not cells:
        return None
    pad, label_h = 8, 24
    cw = max(c[0].width for c in cells)
    ch = max(c[0].height for c in cells) + label_h
    rows = (len(cells) + columns - 1) // columns
    out = Image.new("RGBA", (columns * (cw + pad) + pad, rows * (ch + pad) + pad + 22), BG)
    draw = ImageDraw.Draw(out)
    put_label(draw, (pad, 5), title)
    for i, (img, label) in enumerate(cells):
        x = pad + (i % columns) * (cw + pad)
        y = 22 + pad + (i // columns) * (ch + pad)
        out.alpha_composite(img, (x, y))
        width = max(8, int(cw / 6))
        put_label(draw, (x + 2, y + img.height + 1), label[:width])
        if len(label) > width:
            put_label(draw, (x + 2, y + img.height + 11), label[width:2 * width])
    return out


def draw_arrow(img, direction, label):
    """Arrow in a plan view (north up) pointing along a compass direction; label under it."""
    draw = ImageDraw.Draw(img)
    cx, cy = img.width / 2, img.height / 2
    vx, vy = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[direction]
    length = img.width * 0.42
    tx, ty = cx + vx * length, cy + vy * length
    sx, sy = cx - vx * length * 0.2, cy - vy * length * 0.2
    colour = (255, 214, 64, 255)
    draw.line((sx, sy, tx, ty), fill=colour, width=2)
    px, py = -vy, vx
    draw.polygon([(tx, ty), (tx - vx * 7 + px * 4, ty - vy * 7 + py * 4), (tx - vx * 7 - px * 4, ty - vy * 7 - py * 4)], fill=colour)
    put_label(draw, (2, 2), "N", (170, 200, 255, 255))
    put_label(draw, (2, img.height - 12), label, colour)


def look_direction(info, facing):
    """Compass direction the player looks while placing a block that ends up with `facing`."""
    placement = info.meta["placement"]
    if placement == "away":
        return facing
    return OPPOSITE[facing]  # toward-player is also the default for blocks that do not say


# ---------------------------------------------------------------------------------------------------------------------
# Sheets
# ---------------------------------------------------------------------------------------------------------------------

def tab_sheets(infos, only_tab):
    by_tab = {}
    for info in infos:
        by_tab.setdefault(info.meta["tab"], []).append(info)
    written = []
    for tab, blocks in by_tab.items():
        if only_tab and tab != only_tab:
            continue
        cells = [(iso_image(b.quads(b.default)), b.id) for b in blocks]
        image = sheet(cells, 8, f"tab {tab}: {len(blocks)} blocks, default state (camera from north-west: north face left, west face right)")
        path = OUT / f"tab_{tab}.png"
        image.convert("RGB").save(path)
        written.append(path)
    return written


def orientation_sheets(infos, only_tab):
    by_tab = {}
    for info in infos:
        if info.has_facing or info.has_axis:
            by_tab.setdefault(info.meta["tab"], []).append(info)
    written = []
    for tab, blocks in by_tab.items():
        if only_tab and tab != only_tab:
            continue
        rows = []
        for info in blocks:
            cells = []
            if info.has_facing:
                for facing in HORIZONTAL:
                    state = dict(info.default, facing=facing)
                    quads = info.quads(state)
                    plan = plan_image(quads)
                    draw_arrow(plan, look_direction(info, facing), "look " + look_direction(info, facing))
                    cells.append((iso_image(quads, 120), f"facing={facing}"))
                    cells.append((plan, ""))
                    side = tint_backdrop(render_quads(quads, side_camera(100 / 22.0, look_direction(info, facing)), 100))
                    ImageDraw.Draw(side).text((2, 2), "side, look ->", fill=(255, 214, 64, 255), font=FONT)
                    cells.append((side, ""))
                    end = tint_backdrop(render_quads(quads, end_camera(100 / 22.0, look_direction(info, facing)), 100))
                    ImageDraw.Draw(end).text((2, 2), "end, look in", fill=(255, 214, 64, 255), font=FONT)
                    cells.append((end, ""))
            else:
                for axis in sorted(info.domains["axis"]):
                    quads = info.quads(dict(info.default, axis=axis))
                    cells.append((iso_image(quads, 120), f"axis={axis}"))
                    cells.append((plan_image(quads), ""))
            rows.append((info, cells))
        # one sheet row per block: label column, then up to 8 cells
        cw, ch, pad = 120, 134, 6
        width = 190 + max(sum(c[0].width + pad for c in cells) for _, cells in rows)
        out = Image.new("RGBA", (width, 24 + len(rows) * (ch + pad)), BG)
        draw = ImageDraw.Draw(out)
        put_label(draw, (pad, 5), f"tab {tab}: orientation (iso from north-west + plan view, north up; arrow = direction the player looks when placing)")
        for r, (info, cells) in enumerate(rows):
            y = 24 + r * (ch + pad)
            put_label(draw, (pad, y + 4), info.id[:30])
            kind = "toward player" if info.meta["placement"] != "away" else "away from player"
            put_label(draw, (pad, y + 18), kind if info.has_facing else "axis", (255, 214, 64, 255))
            x = 190
            for img, label in cells:
                out.alpha_composite(img, (x, y))
                put_label(draw, (x + 2, y + img.height + 1), label)
                x += img.width + pad
        path = OUT / f"orientation_{tab}.png"
        out.convert("RGB").save(path)
        written.append(path)
    return written


PANE_STATES = [("post", ()), ("1 side", ("north",)), ("corner", ("north", "east")), ("T", ("north", "east", "west")), ("cross", HORIZONTAL)]


def pane_sheet(infos):
    panes = [i for i in infos if i.blockstate and "multipart" in i.blockstate and {"north", "east", "south", "west"} <= set(i.domains)
             and "pane" in i.id]
    if not panes:
        return None
    cells = []
    for info in panes:
        for label, sides in PANE_STATES:
            state = dict(info.default)
            for side in HORIZONTAL:
                values = info.domains[side]
                on = "true" if "true" in values else sorted(values - {"none"})[0]
                off = "false" if "false" in values else "none"
                state[side] = on if side in sides else off
            cells.append((iso_image(info.quads(state), 110), f"{info.id[:14]} {label}"))
    image = sheet(cells, 10, f"glass pane connection states ({len(panes)} panes): post, 1 side, corner, T, cross")
    path = OUT / "panes.png"
    image.convert("RGB").save(path)
    return path


def wall_quads(info, camera_kind):
    quads = []
    for gx in range(3):
        for gy in range(3):
            quads += info.quads(info.default, (gx * 16, gy * 16, 0))
    return quads


def tiled_sheet(infos):
    cubes = [i for i in infos if i.meta["tab"] in ("glass", "stations", "metro") and i.is_full_cube()]
    if not cubes:
        return None
    cells = []
    for info in cubes:
        quads = wall_quads(info, "front")
        centre = (24, 24, 0)
        front = tint_backdrop(render_quads(quads, Camera((0, 0, 1), (0, 1, 0), 2.6, centre), 130))
        iso = tint_backdrop(render_quads(quads, Camera((1, -0.7, 1), (0, 1, 0), 2.1, centre), 130))
        both = Image.new("RGBA", (264, 130), CELL_BG)
        both.alpha_composite(front, (0, 0))
        both.alpha_composite(iso, (134, 0))
        cells.append((both, info.id))
    image = sheet(cells, 4, f"3x3 tiled walls: {len(cubes)} full-cube glass, station and metro materials (front view | from north-west)")
    path = OUT / "tiled_walls.png"
    image.convert("RGB").save(path)
    return path


# ---------------------------------------------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--tab", help="creative tab id (main, wayfinding, passenger_equipment, bus_street, glass, stations, metro)")
    parser.add_argument("--block", help="a single block id")
    parser.add_argument("--changed", action="store_true", help=f"only blocks whose generated files differ from the merge base with {BASE_BRANCH}")
    args = parser.parse_args()

    metas = parse_registry()
    if not metas:
        sys.exit("no blocks parsed from the registry")
    known_tabs = {m["tab"] for m in metas}
    if args.tab and args.tab not in known_tabs:
        sys.exit(f"unknown tab '{args.tab}'; choose from {sorted(known_tabs)}")
    if args.block:
        metas = [m for m in metas if m["id"] == args.block.replace(MOD + ":", "")]
        if not metas:
            sys.exit(f"unknown block '{args.block}'")
    if args.tab:
        metas = [m for m in metas if m["tab"] == args.tab]
    infos = [BlockInfo(m) for m in metas]
    for info in infos:  # record dependencies for --changed
        info.collect_files()
    if args.changed:
        diff = changed_files()
        if diff is not None:
            infos = [i for i in infos if i.files & diff]
        print(f"--changed: {len(infos)} block(s) differ from the merge base with {BASE_BRANCH}")
    if not infos:
        print("nothing to render")
        return

    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()
    written = tab_sheets(infos, args.tab)
    written += orientation_sheets(infos, args.tab)
    for extra in (pane_sheet(infos), tiled_sheet(infos)):
        if extra:
            written.append(extra)
    print(f"Rendered {len(infos)} block(s) with {ASSET.vanilla.name if ASSET.vanilla else 'no vanilla jar'}:")
    for path in written:
        print("  " + str(path.relative_to(ROOT)))


if __name__ == "__main__":
    main()
