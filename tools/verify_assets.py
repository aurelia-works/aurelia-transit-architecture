#!/usr/bin/env python3
"""
Cross-checks the Java block registry against generated resources. Exits non-zero on any problem.

Checks, for every block registered in ModBlocks.java:
  - blockstate exists, its variant keys use the properties of the Java block class, and all models exist
  - every model's textures resolve to a PNG (or a vanilla/parent-provided texture)
  - item model, loot table, English name and pickaxe tag entry exist
  - recipe results/ingredients in this namespace refer to registered blocks
"""
import json
import re
import sys
from pathlib import Path

MOD = "aurelia_transit_architecture"
ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / MOD
DATA = RES / "data"
REGISTRY = ROOT / "src" / "main" / "java" / "com" / "aureliatransit" / "architecture" / "registry"
JAVA = REGISTRY / "ModBlocks.java"
REGISTRY_FILES = ("ModBlocks.java", "LiveBlocks.java", "InteractiveBlocks.java", "WayfindingBlocks.java", "ElevatedBlocks.java")

EXPECTED_PROPERTIES = {
    "Block": set(),
    "ShapedBlock": set(),
    "FramedGlassBlock": set(),
    "FacingShapedBlock": {"facing"},
    "GlassFacingBlock": {"facing"},
    "AxisShapedBlock": {"axis"},
    "TextSignBlock": {"facing", "left", "right"},
    "SeatBlock": {"facing"},
    "InfoDisplayBlock": {"facing"},
    "ClockBlock": {"facing"},
    "PidsBlock": {"facing", "left", "right", "up", "down"},
    "SpeakerBlock": {"facing"},
    "WayfindingPlateBlock": {"facing"},
    "WayfindingSignBlock": {"facing", "left", "right"},
    "EntrancePylonBlock": {"facing", "half"},
    "HelpPointBlock": {"facing"},
    "BoardingMarkerBlock": {"facing", "marker"},
    # 1.3
    "ViaductColumnBlock": {"style"},
    "ViaductBeamBlock": {"axis", "kind", "concrete"},
    "ViaductBraceBlock": {"facing", "kind"},
    "StationStairBlock": {"facing", "half", "shape"},
    "StairEnclosureBlock": {"facing", "kind"},
    "PlatformWindscreenBlock": {"facing", "kind"},
    "NoiseBarrierBlock": {"facing", "kind"},
    "FareGateBlock": {"facing", "kind"},
    "CardReaderBlock": {"facing", "kind"},
    "CctvCameraBlock": {"facing", "kind"},
    "LiftStatusPanelBlock": {"facing", "left", "right", "status"},
    "PlatformFasciaBlock": {"facing", "kind"},
    "StationFenceBlock": {"kind", "north", "east", "south", "west"},
    "UtilityRunBlock": {"axis", "kind"},
    "HandrailBlock": {"kind", "north", "east", "south", "west"},
    "TactileJunctionBlock": {"facing", "kind"},
    "PlatformEdgeCurveBlock": {"facing", "kind"},
    # 1.4
    "CurvedScreenDoorBlock": {"facing", "kind"},
    "TrainEdgeBlock": {"facing", "part"},
}

problems = []


def problem(msg):
    problems.append(msg)


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        problem(f"missing file {path.relative_to(ROOT)}")
    except json.JSONDecodeError as e:
        problem(f"invalid JSON {path.relative_to(ROOT)}: {e}")
    return None


def registered_blocks():
    source = "\n".join((REGISTRY / f).read_text(encoding="utf-8") for f in REGISTRY_FILES if (REGISTRY / f).is_file())
    # Workstreams add their block classes to EXPECTED_PROPERTIES below.
    # MtrPlatformContract factories build the same block classes, optionally carrying MTR's platform marker.
    pattern = re.compile(r'register\("([a-z0-9_]+)",\s*BlockFamily\.(\w+),\s*RenderKind\.(\w+),\s*(?:new (\w+)|MtrPlatformContract\.(\w+))\(')
    contract = {"surface": "Block", "edge": "FacingShapedBlock", "curve": "PlatformEdgeCurveBlock",
                "screenDoorway": "CurvedScreenDoorBlock", "trainEdge": "TrainEdgeBlock"}
    return [(m.group(1), m.group(2), m.group(3), m.group(4) or contract[m.group(5)]) for m in pattern.finditer(source)]


def check_model(ref, seen):
    if ref in seen:
        return
    seen.add(ref)
    namespace, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    if namespace != MOD:
        return
    m = load(ASSETS / "models" / f"{path}.json")
    if m is None:
        return
    for key, texture in m.get("textures", {}).items():
        if texture.startswith("#"):
            continue
        tns, tpath = texture.split(":", 1) if ":" in texture else ("minecraft", texture)
        if tns == MOD and not (ASSETS / "textures" / f"{tpath}.png").is_file():
            problem(f"{ref}: texture '{key}' -> {texture} has no PNG")
    defined = set(m.get("textures", {}))
    for element in m.get("elements", []):
        for coord in element["from"] + element["to"]:
            if not -16 <= coord <= 32:
                problem(f"{ref}: element coordinate {coord} outside [-16, 32]")
        rotation = element.get("rotation")
        if rotation and rotation["angle"] not in (-45, -22.5, 0, 22.5, 45):
            problem(f"{ref}: illegal rotation angle {rotation['angle']}")
        for face, spec in element["faces"].items():
            var = spec["texture"].lstrip("#")
            if var not in defined:
                problem(f"{ref}: face {face} uses undefined texture variable #{var}")
    if "parent" in m:
        check_model(m["parent"], seen)


def main():
    blocks = registered_blocks()
    if not blocks:
        problem("no blocks parsed from ModBlocks.java")
    ids = {b[0] for b in blocks}
    lang = load(ASSETS / "lang" / "en_us.json") or {}
    tag = load(DATA / "minecraft" / "tags" / "blocks" / "mineable" / "pickaxe.json") or {"values": []}
    seen_models = set()

    for block_id, family, render, cls in blocks:
        state = load(ASSETS / "blockstates" / f"{block_id}.json")
        if state and "multipart" in state:
            # multipart blockstates (connecting railings): check every model and the properties used in conditions
            expected = EXPECTED_PROPERTIES.get(cls)
            if expected is None:
                problem(f"{block_id}: unknown block class {cls}")
            used = set()
            for part in state["multipart"]:
                for apply in part["apply"] if isinstance(part["apply"], list) else [part["apply"]]:
                    check_model(apply["model"], seen_models)
                conditions = part.get("when", {})
                for clause in conditions.get("AND", [conditions]):
                    used |= set(clause)
            if expected is not None and not used <= expected:
                problem(f"{block_id}: multipart conditions use {used - expected} not on {cls}")
            state = None
        if state:
            expected = EXPECTED_PROPERTIES.get(cls)
            if expected is None:
                problem(f"{block_id}: unknown block class {cls}")
            for key, variant in state.get("variants", {}).items():
                props = {kv.split("=")[0] for kv in key.split(",") if kv}
                if expected is not None and not props <= expected:
                    problem(f"{block_id}: variant '{key}' uses properties {props - expected} not on {cls}")
                if expected and cls != "TextSignBlock" and props != expected:
                    problem(f"{block_id}: variant '{key}' does not cover properties {expected}")
                for v in variant if isinstance(variant, list) else [variant]:
                    check_model(v["model"], seen_models)
            if cls in ("FacingShapedBlock", "GlassFacingBlock", "TextSignBlock", "SeatBlock", "InfoDisplayBlock", "ClockBlock", "WayfindingPlateBlock",
                       "WayfindingSignBlock", "EntrancePylonBlock", "HelpPointBlock", "BoardingMarkerBlock", "ViaductBraceBlock", "StationStairBlock",
                       "StairEnclosureBlock", "PlatformWindscreenBlock", "PlatformFasciaBlock", "TactileJunctionBlock", "PlatformEdgeCurveBlock",
                       "NoiseBarrierBlock", "FareGateBlock", "CardReaderBlock", "CctvCameraBlock", "LiftStatusPanelBlock", "CurvedScreenDoorBlock", "TrainEdgeBlock"):
                facings = {kv.split("=")[1] for key in state["variants"] for kv in key.split(",") if kv.startswith("facing=")}
                if facings != {"north", "east", "south", "west"}:
                    problem(f"{block_id}: facings covered {facings}")
        item_model = load(ASSETS / "models" / "item" / f"{block_id}.json")
        if item_model:
            check_model(item_model["parent"], seen_models)
        load(DATA / MOD / "loot_tables" / "blocks" / f"{block_id}.json")
        if f"block.{MOD}.{block_id}" not in lang:
            problem(f"{block_id}: missing English name")
        if f"{MOD}:{block_id}" not in tag["values"]:
            problem(f"{block_id}: not in mineable/pickaxe tag")

    for key in (f"itemGroup.{MOD}.main", f"itemGroup.{MOD}.wayfinding", f"itemGroup.{MOD}.passenger_equipment", f"itemGroup.{MOD}.bus_street"):
        if key not in lang:
            problem(f"missing lang key {key}")
    java_tooltips = set(re.findall(r'TIP \+ "([a-z_]+)"', "\n".join((REGISTRY / f).read_text(encoding="utf-8") for f in REGISTRY_FILES if (REGISTRY / f).is_file())))
    for tip in java_tooltips:
        if f"tooltip.{MOD}.{tip}" not in lang:
            problem(f"missing tooltip lang key {tip}")

    # Stray assets for unregistered ids
    for path in (ASSETS / "blockstates").glob("*.json"):
        if path.stem not in ids:
            problem(f"blockstate for unregistered block {path.stem}")

    recipe_count = 0
    for path in (DATA / MOD / "recipes").glob("*.json"):
        recipe_count += 1
        text = path.read_text(encoding="utf-8")
        for ref in re.findall(rf'"{MOD}:([a-z0-9_]+)"', text):
            if ref not in ids:
                problem(f"recipe {path.name} references unknown {ref}")

    families = {}
    for _, family, _, _ in blocks:
        families[family] = families.get(family, 0) + 1
    print(f"Registered blocks: {len(blocks)} (each with a BlockItem)")
    for family, count in families.items():
        print(f"  {family:<13} {count}")
    print(f"Models checked: {len([m for m in seen_models if m.startswith(MOD)])}, recipes: {recipe_count}")
    if problems:
        print(f"\n{len(problems)} PROBLEM(S):")
        for p in problems:
            print("  - " + p)
        sys.exit(1)
    print("Asset audit: OK")


if __name__ == "__main__":
    main()
