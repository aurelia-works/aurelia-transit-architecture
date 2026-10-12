"""Station wrench item (station generator tool): English names and the 3D item model.

Same extension interface as the other assets_*.py modules. The model is made in Blockbench and checked in as source under
tools/models/station_wrench/ (station_wrench.json plus its textures); this module copies it into the generated assets, because
generate_assets.py wipes models/ and textures/ on every run. Without that export no model is written, and verify_assets.py
fails, so the wrench cannot ship with a placeholder.
"""
import json
import shutil
from pathlib import Path

import generate_assets as g

MOD = g.MOD
TIP = f"tooltip.{MOD}.station_wrench"
SCREEN = f"screen.{MOD}.station_wrench."
PRESETS = {"utrecht": "Utrecht", "amsterdam": "Amsterdam", "st_pancras": "St Pancras", "antwerp": "Antwerp", "frankfurt": "Frankfurt",
           "hamburg": "Hamburg", "munich": "Munich", "grand_central": "Grand Central"}
SOURCE = Path(__file__).resolve().parent / "models" / "station_wrench"


def lang():
    return {
        f"item.{MOD}.station_wrench": "Station Wrench",
        TIP + ".edit": "Right-click the air: open the station menu",
        TIP + ".plan": "Right-click a block: place the station there",
        TIP + ".build": "Sneak + right-click a block: build it",
        TIP + ".cancel": "Left-click: cancel",
        TIP + ".ops": "Operators only",
        SCREEN + "title": "Station Wrench",
        SCREEN + "description": "Describe the station",
        SCREEN + "example": "e.g. two track dutch station, island platform, 64 long",
        SCREEN + "hint": "Right-click a block with the wrench to plan there",
        SCREEN + "plan": "Plan in front of me",
        SCREEN + "preview": "Show preview",
        SCREEN + "build": "Build",
        SCREEN + "build_replace": "Build, replace terrain",
        SCREEN + "build_replace.tip": "Also changes the terrain the plan listed",
        SCREEN + "cancel": "Cancel",
        SCREEN + "undo": "Undo last build",
        SCREEN + "step_look": "1. Pick a city look, then the size and roof",
        SCREEN + "step_place": "2. Place it, check the outline, then Build it!",
        SCREEN + "look.tip": "The station building, platforms and roof of this city",
        SCREEN + "mix": "Mix with",
        SCREEN + "mix.tip": "Takes the train shed (or the platforms) from a second city",
        SCREEN + "none": "Nothing",
        SCREEN + "type": "Type",
        SCREEN + "through": "Through",
        SCREEN + "terminus": "Terminus",
        SCREEN + "tracks": "%s tracks",
        SCREEN + "platforms": "Platforms",
        SCREEN + "length": "Length",
        SCREEN + "roof": "Roof",
        SCREEN + "roof.tip": "Auto uses the city's own roof. A train shed is one big roof over all the tracks",
        SCREEN + "building": "Building",
        SCREEN + "building.terminus": "A terminus always has its head building",
        SCREEN + "name": "Station name",
        SCREEN + "place": "Place in front of me",
        SCREEN + "place.tip": "Shows the outline in front of you. Or right-click the ground with the wrench",
        SCREEN + "build_it": "Build it!",
        SCREEN + "build_it.tip": "Builds the placed outline",
        SCREEN + "cancel_plan": "Cancel plan",
        SCREEN + "advanced": "Advanced...",
        SCREEN + "advanced.tip": "Type your own description, e.g. american station, utrecht and hamburg combined",
        SCREEN + "simple": "Back to buttons",
        SCREEN + "simple.tip": "The buttons make their own description; your typed text stays here",
    } | {SCREEN + f"preset.{k}": n for k, n in PRESETS.items()}


def write_extra(assets, data, write_json):
    model = SOURCE / "station_wrench.json"
    if not model.is_file():
        return
    write_json(assets / "models" / "item" / "station_wrench.json", json.loads(model.read_text(encoding="utf-8")))
    textures = assets / "textures" / "item"
    textures.mkdir(parents=True, exist_ok=True)
    for png in sorted(SOURCE.glob("*.png")):
        shutil.copyfile(png, textures / png.name)
