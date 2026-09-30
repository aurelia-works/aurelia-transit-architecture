"""Asset extension for workstream A (live transit systems). See load_extensions() in generate_assets.py.

Define any of: textures() -> {name: PIL.Image}, blocks() -> {id: (kind, {suffix: model})},
names() -> {id: English name}, lang() -> {key: text}, recipes() -> {name: recipe json},
write_extra(assets_dir, data_dir, write_json) for other files (e.g. sounds.json).
Helpers: ``import generate_assets as g`` then g.el, g.model, g.noisy, g.rect, ...
"""
