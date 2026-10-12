"""Hand-made block models and textures (1.5 realism pass), checked in as source under tools/models/<block>/.

generate_assets.py wipes models/ and textures/ on every run, so anything made by hand lives in a source folder and is
copied in here. This module runs last, so a source file replaces the generated file of the same name:

  tools/models/<block>/*.png       block textures, written to textures/block/<name>.png (16x16; a texture used by other
                                   blocks changes for them too, so give a block its own texture name when that is unwanted)
  tools/models/<block>/*.json      block models, written to models/block/<name>.json. The name must be a model the
                                   generator already wrote (the block's own model or one of its variants such as
                                   <block>_tactile), so blockstates, ids and properties stay exactly as they are
  tools/models/<block>/*.bbmodel   the Blockbench project the files were exported from (not copied; open it to edit)

Textures painted by script come from tools/paint_realism.py, which writes its PNGs into the same folders. Folders that
belong to items (station_wrench) are handled by their own modules and skipped here.
"""
import json
from pathlib import Path

from PIL import Image

import generate_assets as g

SOURCE = Path(__file__).resolve().parent / "models"
ITEM_FOLDERS = {"station_wrench"}


def folders():
    return [d for d in sorted(SOURCE.iterdir()) if d.is_dir() and d.name not in ITEM_FOLDERS and not d.name.startswith(".")]


def textures():
    t = {}
    for folder in folders():
        for png in sorted(folder.glob("*.png")):
            img = Image.open(png).convert("RGBA")
            if img.size != (16, 16):
                raise SystemExit(f"{png}: block textures are 16x16, got {img.size}")
            if png.stem in t:
                raise SystemExit(f"{png}: texture {png.stem} is also in another tools/models folder")
            t[png.stem] = img
    return t


def write_extra(assets, data, write_json):
    seen = {}
    for folder in folders():
        for src in sorted(folder.glob("*.json")):
            target = assets / "models" / "block" / src.name
            if not target.is_file():
                raise SystemExit(f"{src}: no generated model {src.stem} to replace (block ids, variants and blockstates stay as generated)")
            if src.stem in seen:
                raise SystemExit(f"{src}: model {src.stem} is also in {seen[src.stem]}")
            seen[src.stem] = folder.name
            write_json(target, json.loads(src.read_text(encoding="utf-8")))
