#!/usr/bin/env python3
"""
Station fittings from Jah's hub play-test (2026-10-20): the lattice roof truss (the girder's states with a truss model), the
drop rod screens hang from, the hall pendant lamp, the wall speaker's bracket arm for columns and poles, and the ticket vending machine.

Writes only these blocks' files (blockstates, models, item models, textures, loot, recipes) and adds their lang keys and
pickaxe tag entries; run from the repo root: python3 tools/assets_fittings.py
"""
import json
import os

from PIL import Image, ImageDraw

MOD = "aurelia_transit_architecture"
ASSETS = f"src/main/resources/assets/{MOD}"
DATA = f"src/main/resources/data/{MOD}"
KINDS = ["steel", "white", "blue", "green"]


def write(path, data):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w") as f:
		json.dump(data, f, indent=2)
		f.write("\n")


def uv(face, a, b):
	"""Vanilla's default UV for a face of the box a..b, clamped to the texture."""
	x0, y0, z0 = a
	x1, y1, z1 = b
	c = lambda n: max(0.0, min(16.0, n))
	if face in ("north", "south"):
		r = [x0, 16 - y1, x1, 16 - y0] if face == "south" else [16 - x1, 16 - y1, 16 - x0, 16 - y0]
	elif face in ("east", "west"):
		r = [16 - z1, 16 - y1, 16 - z0, 16 - y0] if face == "east" else [z0, 16 - y1, z1, 16 - y0]
	else:
		r = [x0, z0, x1, z1] if face == "up" else [x0, 16 - z1, x1, 16 - z0]
	r = [c(n) for n in r]
	# a face clamped to nothing still needs some texture
	if r[0] == r[2]:
		r[2] = min(16.0, r[0] + 1)
	if r[1] == r[3]:
		r[3] = min(16.0, r[1] + 1)
	return r


def box(a, b, tex, faces=None, rotation=None, cull=None):
	"""An element from a to b; tex is one texture or a dict per face. faces limits the faces drawn."""
	out = {"from": list(a), "to": list(b), "faces": {}}
	for face in faces or ("down", "up", "north", "south", "west", "east"):
		t = tex.get(face) if isinstance(tex, dict) else tex
		if t is None:
			continue
		if isinstance(t, tuple):
			out["faces"][face] = {"uv": t[1], "texture": t[0]}
		else:
			out["faces"][face] = {"uv": uv(face, a, b), "texture": t}
		if cull and face in cull:
			out["faces"][face]["cullface"] = face
	if rotation:
		out["rotation"] = rotation
	return out


def model(textures, elements, parent="block/block", ao=True):
	m = {"parent": parent, "textures": textures, "elements": elements}
	if not ao:
		m["ambientocclusion"] = False
	return m


# ---- roof truss ---------------------------------------------------------------------------------------------------------
# A Warren truss a block deep: a top chord 6 wide, a bottom chord 4 wide and a diagonal in every arm from the bottom at the
# node up to the top at the block face, so a run of blocks zigzags. The node has a post; up/down is a lattice column.

def truss_node():
	return [box((7, 2, 7), (9, 13, 9), "#s"), box((5, 13, 5), (11, 16, 11), "#s"), box((6, 0, 6), (10, 2, 10), "#s")]


def truss_arm():
	# north arm, z from the face (0) to the node; the diagonal is a bar 15 long turned 45 degrees about x round the arm's
	# middle, its north end up (vanilla turns positive angles right-handed about +x)
	return [
		box((5, 13, 0), (11, 16, 5), "#s", faces=("down", "up", "west", "east", "north"), cull=("north",)),
		box((6, 0, 0), (10, 2, 6), "#s", faces=("down", "up", "west", "east", "north"), cull=("north",)),
		box((7, 6.75, -3.5), (9, 8.25, 11.5), "#s", rotation={"origin": [8, 7.5, 4], "axis": "x", "angle": 45}),
	]


def truss_vertical():
	rods = []
	for x in (5, 9.5):
		for z in (5, 9.5):
			rods.append(box((x, 0, z), (x + 1.5, 16, z + 1.5), "#s", faces=("north", "south", "west", "east")))
	rods.append(box((5, 7.25, 5), (11, 8.75, 11), "#s", faces=("down", "up", "north", "south", "west", "east")))
	return rods


def roof_truss():
	with open(f"{ASSETS}/blockstates/station_girder.json") as f:
		state = json.load(f)
	write(f"{ASSETS}/blockstates/roof_truss.json", json.loads(json.dumps(state).replace("station_girder", "roof_truss")))
	for kind in KINDS:
		tex = {"particle": f"{MOD}:block/girder_{kind}", "s": f"{MOD}:block/girder_{kind}"}
		write(f"{ASSETS}/models/block/roof_truss_node_{kind}.json", model(tex, truss_node(), ao=False))
		write(f"{ASSETS}/models/block/roof_truss_arm_{kind}.json", model(tex, truss_arm(), ao=False))
		write(f"{ASSETS}/models/block/roof_truss_vertical_{kind}.json", model(tex, truss_vertical(), ao=False))
	# the item: one block of truss along z, chords through, the post and a diagonal each side of it
	tex = {"particle": f"{MOD}:block/girder_steel", "s": f"{MOD}:block/girder_steel"}
	diagonal = truss_arm()[2]
	mirror = json.loads(json.dumps(diagonal))
	mirror["from"] = [diagonal["from"][0], diagonal["from"][1], 16 - diagonal["to"][2]]
	mirror["to"] = [diagonal["to"][0], diagonal["to"][1], 16 - diagonal["from"][2]]
	mirror["rotation"] = {"origin": [8, 7.5, 12], "axis": "x", "angle": -45}
	elements = [box((5, 13, 0), (11, 16, 16), "#s"), box((6, 0, 0), (10, 2, 16), "#s"), box((7, 2, 7), (9, 13, 9), "#s"), diagonal, mirror]
	m = model(tex, elements, ao=False)
	m["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}}
	write(f"{ASSETS}/models/block/roof_truss.json", m)
	write(f"{ASSETS}/models/item/roof_truss.json", {"parent": f"{MOD}:block/roof_truss"})


# ---- drop rod -----------------------------------------------------------------------------------------------------------

def drop_rod():
	tex = {"particle": f"{MOD}:block/girder_steel", "s": f"{MOD}:block/girder_steel"}
	write(f"{ASSETS}/models/block/drop_rod.json", model(tex, [box((7, 0, 7), (9, 16, 9), "#s", cull=("down", "up"))], ao=False))
	# over a hanging screen the rod splits into hangers onto the stubs on the screen's top (x 3-4 and 12-13 on a north-facing
	# one); the end block of a wide screen has only its outer stub, so the rod there has only that hanger. Turned like the
	# screen's own model for its facing.
	body = f"{MOD}:block/live_body"
	low = box((3, 0, 7.5), (4, 16, 8.5), "#b", cull=("down", "up"))
	high = box((12, 0, 7.5), (13, 16, 8.5), "#b", cull=("down", "up"))
	for name, elements in (("drop_rod_hanger", [low, high]), ("drop_rod_hanger_low", [low]), ("drop_rod_hanger_high", [high])):
		write(f"{ASSETS}/models/block/{name}.json", model({"particle": body, "b": body}, elements, ao=False))
	turns = {"north": 0, "east": 90, "south": 180, "west": 270}
	models = {"none": "drop_rod", "both": "drop_rod_hanger", "low": "drop_rod_hanger_low", "high": "drop_rod_hanger_high"}
	variants = {}
	for f, y in turns.items():
		for hanger, name in models.items():
			v = {"model": f"{MOD}:block/{name}"}
			if y and hanger != "none":
				v["y"] = y
			variants[f"facing={f},hanger={hanger}"] = v
	write(f"{ASSETS}/blockstates/drop_rod.json", {"variants": variants})
	write(f"{ASSETS}/models/item/drop_rod.json", {"parent": f"{MOD}:block/drop_rod"})


# ---- wall speaker on a post ---------------------------------------------------------------------------------------------

def wall_speaker():
	with open(f"{ASSETS}/models/block/wall_speaker.json") as f:
		speaker = json.load(f)
	post = json.loads(json.dumps(speaker))
	# an arm from the speaker's back to 6 px into the next cell, past a square column's face (4 in) or a round one's (4.5)
	# and to a sign pole; a clamp plate where it meets the speaker
	post["elements"].append(box((7, 7, 16), (9, 9, 22), "#body", faces=("down", "up", "west", "east", "south")))
	post["elements"].append(box((6, 6, 16), (10, 10, 17), "#body", faces=("down", "up", "west", "east", "south")))
	write(f"{ASSETS}/models/block/wall_speaker_post.json", post)
	turns = {"north": 0, "east": 90, "south": 180, "west": 270}
	variants = {}
	for facing, y in turns.items():
		for p in ("false", "true"):
			v = {"model": f"{MOD}:block/wall_speaker" + ("_post" if p == "true" else "")}
			if y:
				v["y"] = y
			variants[f"facing={facing},post={p}"] = v
	write(f"{ASSETS}/blockstates/wall_speaker.json", {"variants": variants})


# ---- ticket vending machine ---------------------------------------------------------------------------------------------

def textures():
	t = f"{ASSETS}/textures/block"
	body = Image.new("RGBA", (16, 16), (58, 62, 68, 255))
	d = ImageDraw.Draw(body)
	d.rectangle((0, 0, 15, 0), fill=(76, 81, 88, 255))
	d.rectangle((0, 15, 15, 15), fill=(40, 43, 48, 255))
	d.line((0, 7, 15, 7), fill=(48, 52, 57, 255))
	body.save(f"{t}/tvm_body.png")
	screen = Image.new("RGBA", (16, 16), (18, 64, 120, 255))
	d = ImageDraw.Draw(screen)
	d.rectangle((0, 0, 15, 2), fill=(240, 244, 248, 255))
	d.rectangle((1, 1, 6, 1), fill=(18, 64, 120, 255))
	for i, y in enumerate((5, 8, 11)):
		d.rectangle((1, y, 14, y + 1), fill=(64, 140, 220, 255) if i else (250, 200, 40, 255))
	d.rectangle((1, 14, 6, 14), fill=(120, 200, 120, 255))
	d.rectangle((9, 14, 14, 14), fill=(220, 90, 80, 255))
	screen.save(f"{t}/tvm_screen.png")
	header = Image.new("RGBA", (16, 16), (236, 238, 240, 255))
	d = ImageDraw.Draw(header)
	# a ticket pictogram on a blue square at the left, then a strip of light
	d.rectangle((1, 2, 6, 13), fill=(20, 80, 160, 255))
	d.rectangle((2, 6, 5, 9), fill=(255, 255, 255, 255))
	d.point((3, 7), fill=(20, 80, 160, 255))
	d.rectangle((8, 7, 14, 8), fill=(170, 176, 182, 255))
	header.save(f"{t}/tvm_header.png")
	pad = Image.new("RGBA", (16, 16), (34, 36, 40, 255))
	d = ImageDraw.Draw(pad)
	# keys on the left (the face is wider than tall, so the 16 x 16 is squeezed upright), the card reader on the right
	for x in (1, 3, 5):
		for y in (2, 7, 12):
			d.rectangle((x, y, x, y + 2), fill=(190, 196, 202, 255))
	d.rectangle((9, 1, 14, 14), fill=(24, 26, 29, 255))
	d.rectangle((10, 2, 13, 4), fill=(90, 170, 90, 255))
	d.rectangle((10, 8, 13, 9), fill=(6, 6, 8, 255))
	pad.save(f"{t}/tvm_keypad.png")
	slot = Image.new("RGBA", (16, 16), (24, 26, 29, 255))
	d = ImageDraw.Draw(slot)
	d.rectangle((2, 7, 13, 8), fill=(6, 6, 8, 255))
	d.rectangle((0, 0, 15, 1), fill=(90, 160, 90, 255))
	slot.save(f"{t}/tvm_slot.png")


def ticket_vending_machine():
	"""
	Two blocks tall, like a real machine (Jah 10-20: realistic, and its hitbox up to the top): the lower half has the ledge,
	card reader and keypad and the ticket tray, the upper half the big touch screen under a hood and the lit header.
	"""
	textures()
	b = f"{MOD}:block/tvm_body"
	tex = {"particle": b, "body": b, "screen": f"{MOD}:block/tvm_screen", "header": f"{MOD}:block/tvm_header",
		"pad": f"{MOD}:block/tvm_keypad", "slot": f"{MOD}:block/tvm_slot"}
	full = (0, 0, 16, 16)
	sides = {"west": "#body", "east": "#body", "up": "#body", "down": "#body"}
	lower = [
		# plinth, then the cabinet (front at z 4)
		box((1, 0, 5), (15, 1.5, 16), "#body", cull=("down", "south")),
		box((1, 1.5, 4), (15, 16, 16), "#body", cull=("south", "up")),
		# the ledge to rest a wallet on, the card reader and keypad above it, the ticket and change tray near the floor
		box((1.5, 10, 2), (14.5, 11, 4), "#body"),
		box((3, 11.5, 3.5), (13, 15.5, 4), dict(sides, north=("#pad", list(full)))),
		box((3.5, 3, 3), (12.5, 6.5, 4), dict(sides, north=("#slot", [0, 4, 16, 12]))),
	]
	upper = [
		box((1, 0, 4), (15, 12, 16), "#body", cull=("south", "down")),
		# the touch screen in a bezel, a hood over it either side, the lit header a little proud of the front
		box((2.5, 1.5, 3.5), (13.5, 10.5, 4), dict(sides, north=("#screen", list(full)))),
		box((1, 1, 2.5), (2, 11, 4), "#body"),
		box((14, 1, 2.5), (15, 11, 4), "#body"),
		box((1, 11, 2.5), (15, 14.5, 16), {"down": "#body", "up": "#body", "south": "#body", "west": "#body", "east": "#body",
			"north": ("#header", list(full))}),
	]
	write(f"{ASSETS}/models/block/ticket_vending_machine.json", model(tex, lower))
	write(f"{ASSETS}/models/block/ticket_vending_machine_upper.json", model(tex, upper))
	turns = {"north": 0, "east": 90, "south": 180, "west": 270}
	variants = {}
	for half, name in (("lower", "ticket_vending_machine"), ("upper", "ticket_vending_machine_upper")):
		for f, y in turns.items():
			variants[f"facing={f},half={half}"] = {"model": f"{MOD}:block/{name}", "y": y} if y else {"model": f"{MOD}:block/{name}"}
	write(f"{ASSETS}/blockstates/ticket_vending_machine.json", {"variants": variants})
	# the item: the whole machine in four boxes, shrunk to fit a slot
	item = model(tex, [
		box((1, 0, 4), (15, 30.5, 16), "#body"),
		box((2.5, 17.5, 3.5), (13.5, 26.5, 4), dict(sides, north=("#screen", list(full)))),
		box((1, 27, 2.5), (15, 30.5, 16), dict(sides, north=("#header", list(full)), south="#body")),
		box((3, 11.5, 3.5), (13, 15.5, 4), dict(sides, north=("#pad", list(full)))),
	])
	item["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
		"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
		"fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
		"thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [0.3, 0.3, 0.3]},
		"firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]}}
	write(f"{ASSETS}/models/item/ticket_vending_machine.json", item)


# ---- hall pendant lamp -------------------------------------------------------------------------------------------------

def hall_pendant_lamp():
	"""
	The lamps hung from a glass hall's trusses (Jah 10-20): a collar for the drop rod above, a dark green enamel shade
	stepping out to its rim, and the lit diffuser under it. Light level 15 from the block; the model only shows where.
	"""
	t = f"{ASSETS}/textures/block"
	shade = Image.new("RGBA", (16, 16), (38, 70, 58, 255))
	d = ImageDraw.Draw(shade)
	d.rectangle((0, 0, 15, 1), fill=(52, 92, 76, 255))
	d.rectangle((0, 14, 15, 15), fill=(28, 52, 43, 255))
	shade.save(f"{t}/pendant_shade.png")
	glow = Image.new("RGBA", (16, 16), (255, 246, 222, 255))
	d = ImageDraw.Draw(glow)
	d.rectangle((0, 0, 15, 15), outline=(255, 226, 170, 255))
	glow.save(f"{t}/pendant_glow.png")
	s, g = f"{MOD}:block/pendant_shade", f"{MOD}:block/pendant_glow"
	tex = {"particle": s, "s": s, "g": g, "r": f"{MOD}:block/girder_steel"}
	elements = [
		box((7, 13, 7), (9, 16, 9), "#r", faces=("north", "south", "west", "east", "up"), cull=("up",)),
		box((5.5, 11, 5.5), (10.5, 13, 10.5), "#s"),
		box((3, 8, 3), (13, 11, 13), {"north": "#s", "south": "#s", "west": "#s", "east": "#s", "up": "#s", "down": "#g"}),
		box((4.5, 7.5, 4.5), (11.5, 8, 11.5), {"north": "#g", "south": "#g", "west": "#g", "east": "#g", "down": "#g"}),
	]
	write(f"{ASSETS}/models/block/hall_pendant_lamp.json", model(tex, elements, ao=False))
	write(f"{ASSETS}/blockstates/hall_pendant_lamp.json", {"variants": {"": {"model": f"{MOD}:block/hall_pendant_lamp"}}})
	write(f"{ASSETS}/models/item/hall_pendant_lamp.json", {"parent": f"{MOD}:block/hall_pendant_lamp"})


# ---- loot, recipes, lang, tags -----------------------------------------------------------------------------------------

NEW = ["roof_truss", "drop_rod", "ticket_vending_machine", "hall_pendant_lamp"]


def data():
	for block in NEW:
		entry = {"type": "minecraft:item", "name": f"{MOD}:{block}"}
		if block == "ticket_vending_machine":
			# two blocks tall: only the lower half drops the machine
			entry["conditions"] = [{"condition": "minecraft:block_state_property", "block": f"{MOD}:{block}", "properties": {"half": "lower"}}]
		write(f"{DATA}/loot_tables/blocks/{block}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
			"entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
	recipes = {
		"roof_truss": (["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_bars"], 4),
		"drop_rod": (["minecraft:iron_ingot", "minecraft:chain"], 8),
		"ticket_vending_machine": (["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:redstone", "minecraft:glass_pane",
			"minecraft:paper"], 1),
		"hall_pendant_lamp": (["minecraft:iron_ingot", "minecraft:glowstone_dust", "minecraft:green_dye"], 2),
	}
	for block, (items, count) in recipes.items():
		write(f"{DATA}/recipes/{block}.json", {"type": "minecraft:crafting_shapeless", "category": "building",
			"ingredients": [{"item": i} for i in items], "result": {"item": f"{MOD}:{block}", "count": count}})
	path = f"src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json"
	with open(path) as f:
		tag = json.load(f)
	for block in NEW:
		if f"{MOD}:{block}" not in tag["values"]:
			tag["values"].append(f"{MOD}:{block}")
	write(path, tag)
	path = f"{ASSETS}/lang/en_us.json"
	with open(path) as f:
		lang = json.load(f)
	lang.update({
		f"block.{MOD}.roof_truss": "Roof Truss",
		f"block.{MOD}.drop_rod": "Drop Rod",
		f"block.{MOD}.ticket_vending_machine": "Ticket Vending Machine",
		f"tooltip.{MOD}.drop_rod": "Hangs screens and signs from the roof",
		f"tooltip.{MOD}.ticket_vending_machine": "Sells MTR tickets and tops up your balance",
		f"block.{MOD}.hall_pendant_lamp": "Hall Pendant Lamp",
		f"tooltip.{MOD}.hall_pendant_lamp": "Hang it under a drop rod from the roof",
	})
	with open(path, "w") as f:
		json.dump(lang, f, indent=2, ensure_ascii=False)
		f.write("\n")


if __name__ == "__main__":
	roof_truss()
	drop_rod()
	wall_speaker()
	ticket_vending_machine()
	hall_pendant_lamp()
	data()
