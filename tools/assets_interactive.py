"""Asset extension for workstream B (interactive station content). See load_extensions() in generate_assets.py.

Define any of: textures() -> {name: PIL.Image}, blocks() -> {id: (kind, {suffix: model})},
names() -> {id: English name}, lang() -> {key: text}, recipes() -> {name: recipe json},
write_extra(assets_dir, data_dir, write_json) for other files (e.g. sounds.json).
Helpers: ``import generate_assets as g`` then g.el, g.model, g.noisy, g.rect, ...

Adds: clocks (hanging/wall digital, station analog), the wave-to-flat canopy transition, the canopy corner cap,
and the English text for the editors, seating and clock tooltips. Textures shared with V1 blocks (information faces,
clock displays and dial, panel fill) are drawn in generate_assets.draw_textures().
"""
import generate_assets as g

MOD = g.MOD
KEY = f"screen.{MOD}."


def blocks():
    b = {}

    b["hanging_digital_clock"] = ("facing", {"": g.model(
        {"particle": "steel_dark", "frame": "steel_dark", "display": "clock_display", "steel": "steel"}, [
            g.el([0, 4, 6], [16, 12, 10], "#frame", faces={"north": "#display", "south": "#display"}),
            g.el([3.5, 12, 7.5], [4.5, 16, 8.5], "#steel"),
            g.el([11.5, 12, 7.5], [12.5, 16, 8.5], "#steel"),
        ])})
    b["wall_digital_clock"] = ("facing", {"": g.model(
        {"particle": "steel_dark", "frame": "steel_dark", "display": "clock_display_wall"}, [
            g.el([1, 4, 13], [15, 12, 16], "#frame", faces={"north": "#display"}),
        ])})
    b["station_analog_clock"] = ("facing", {"": g.model(
        {"particle": "steel_dark", "frame": "steel_dark", "dial": "clock_dial"}, [
            g.el([1, 1, 13], [15, 15, 16], "#frame", faces={"north": "#dial"}),
        ])})

    b["canopy_wave_flat"] = ("facing", {"": g.canopy_strips(g.WAVE_FLAT)})
    fascia_side = ("#fascia", [0, 5, 16, 11])
    b["canopy_end_cap"] = ("facing", {"": g.model(
        {"particle": "canopy_top", "top": "canopy_top", "under": "canopy_underside", "side": "steel_dark", "fascia": "fascia"}, [
            g.el([0, 0, 1.5], [14.5, 2, 16], "#side", faces={"up": "#top", "down": "#under"}),
            g.el([0, -3, 0], [16, 3, 1.5], "#side", faces={"north": fascia_side, "south": fascia_side}),
            g.el([14.5, -3, 1.5], [16, 3, 16], "#side", faces={"east": fascia_side, "west": fascia_side}),
        ])})
    return b


def names():
    return {
        "hanging_digital_clock": "Hanging Digital Clock",
        "wall_digital_clock": "Wall Digital Clock",
        "station_analog_clock": "Station Analog Clock",
        "canopy_wave_flat": "Wave Canopy Flattening",
        "canopy_end_cap": "Canopy Corner Cap",
    }


def lang():
    text = {
        f"tooltip.{MOD}.sit": "Right-click to sit",
        f"tooltip.{MOD}.clock": "Shows the world time",
        KEY + "edit_sign": "Edit Sign",
        KEY + "edit_info": "Edit Information Text",
        KEY + "heading": "Heading",
        KEY + "name": "Main text",
        KEY + "caption": "Caption",
        KEY + "secondary": "Second line",
        KEY + "platform": "No.",
        KEY + "route": "Route",
        KEY + "align": "Align: %s",
        KEY + "align.left": "Left",
        KEY + "align.center": "Center",
        KEY + "align.right": "Right",
        KEY + "accent": "Accent: %s",
        KEY + "arrow": "Arrow: %s",
        KEY + "arrow.none": "none",
        KEY + "auto_on": "Station name: Auto",
        KEY + "auto_off": "Station name: Manual",
    }
    for accent in ("none", "blue", "green", "yellow", "orange", "red", "purple", "teal", "grey"):
        text[KEY + "accent." + accent] = accent.capitalize()
    return text


def recipes():
    r = {}

    def shapeless(result, ingredients, count=1):
        r[result] = {"type": "minecraft:crafting_shapeless", "category": "redstone",
                     "ingredients": [g.item(i) for i in ingredients],
                     "result": {"item": f"{MOD}:{result}", "count": count}}

    def cut(result, ingredient, count):
        r[f"{result}_from_{ingredient.split(':')[-1]}_stonecutting"] = {
            "type": "minecraft:stonecutting", "ingredient": g.item(ingredient), "result": f"{MOD}:{result}", "count": count}

    shapeless("hanging_digital_clock", ["minecraft:iron_ingot", "minecraft:clock", "minecraft:redstone", "minecraft:chain"])
    shapeless("wall_digital_clock", ["minecraft:iron_ingot", "minecraft:clock", "minecraft:redstone"])
    shapeless("station_analog_clock", ["minecraft:iron_ingot", "minecraft:clock", "minecraft:glass_pane"])
    cut("canopy_wave_flat", "minecraft:iron_ingot", 4)
    cut("canopy_end_cap", "minecraft:iron_ingot", 4)
    return r
