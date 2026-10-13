// Station Studio page: the controls ask the local server to plan (the game's own planner), then the station is drawn from its
// real block models, one instanced mesh per model and texture.
(function () {
	"use strict";

	const $ = (id) => document.getElementById(id);
	const TOKEN = document.body.dataset.token;
	const IN_GAME = document.body.dataset.ingame === "true";
	const STORE = "ata-station-studio";
	const FACE_SHADE = { up: 1.0, down: 0.5, north: 0.8, south: 0.8, west: 0.6, east: 0.6 };
	const TINT = new THREE.Color(0x7cbd4f);

	let options = null;
	let current = null;
	let invertX = false;
	let invertY = false;

	// ---- controls -------------------------------------------------------------------------------------------------------

	const fields = ["text", "mode", "layout", "tracks", "length", "look", "mix", "roof", "building", "size", "name", "platforms", "bed"];

	function fillSelect(select, pairs, extra) {
		select.innerHTML = "";
		if (extra) {
			select.append(new Option(extra[1], extra[0]));
		}
		for (const [value, label] of pairs) {
			select.append(new Option(label, value));
		}
	}

	function values() {
		const q = {};
		for (const f of fields) {
			q[f] = $(f).value;
		}
		return q;
	}

	function restore() {
		let saved = null;
		try {
			saved = JSON.parse(localStorage.getItem(STORE) || "null");
		} catch (e) {
			saved = null;
		}
		if (!saved) {
			return;
		}
		for (const f of fields) {
			if (saved[f] !== undefined && $(f)) {
				$(f).value = saved[f];
			}
		}
	}

	function remember() {
		try {
			localStorage.setItem(STORE, JSON.stringify(values()));
		} catch (e) {
			// private window or storage blocked: the page still works, it just forgets
		}
	}

	function updateOutputs() {
		if ($("listOn") && $("listOn").checked) {
			// the advanced platform list sets the tracks
			const listed = plats.reduce((n, p) => n + (p.kind.startsWith("island") ? 2 : 1), 0);
			$("tracksOut").textContent = listed + (listed === 1 ? " track" : " tracks");
		} else {
			$("tracksOut").textContent = $("tracks").value + ($("tracks").value === "1" ? " track" : " tracks");
		}
		$("lengthOut").textContent = $("length").value + " blocks";
		$("count").textContent = $("text").value.length + " / " + options.maxTyped;
		syncButtons();
	}

	// ---- buttons over the hidden selects: the selects stay the single source of what is sent --------------------------

	// one real block texture per city (ATA's unless a namespace is given), shown behind the city's picture while it loads
	const LOOK_TEXTURES = {
		UTRECHT: "nlbe_wave_top_x", AMSTERDAM: "nlbe_brick", HAMBURG: "hamburg_clinker_brick", MUNICH: "munich_exposed_concrete",
		FRANKFURT: "frankfurt_sandstone_ashlar", ST_PANCRAS: "nlbe_brick_band_side", ANTWERP: "nlbe_limestone", NEW_YORK: "roma_travertine",
		TOKYO: "minecraft/block/bricks", ZURICH: "minecraft/block/sandstone_top", PARIS: "minecraft/block/calcite",
		MILAN: "minecraft/block/polished_diorite", BERLIN: "blue_tinted_glass", HONG_KONG: "minecraft/block/white_concrete", BEIJING: "grey_tinted_glass"
	};
	const MODE_HELP = {
		through: "Trains stop and carry on through the station.",
		terminus: "The tracks end at buffer stops in front of a big station hall.",
		hub: "A bit of both: the middle tracks end at the station hall, the outer ones carry on through."
	};
	const synced = [];

	function set(id, value) {
		$(id).value = value;
		$(id).dispatchEvent(new Event("input"));
	}

	function press(button, on) {
		button.setAttribute("aria-pressed", String(on));
	}

	/** Segmented buttons for every option of a hidden select. */
	function segmented(selectId, hostId, label) {
		const host = $(hostId);
		host.innerHTML = "";
		for (const option of $(selectId).options) {
			const b = document.createElement("button");
			b.type = "button";
			b.textContent = label ? label(option) : option.text;
			b.addEventListener("click", () => set(selectId, option.value));
			host.append(b);
			synced.push(() => press(b, $(selectId).value === option.value));
		}
	}

	function buildButtons() {
		const looks = $("looks");
		looks.innerHTML = "";
		for (const [value, label] of options.looks) {
			const b = document.createElement("button");
			b.type = "button";
			b.className = "look";
			const sw = document.createElement("i");
			sw.className = "sw";
			if (LOOK_TEXTURES[value]) {
				// the city's own terminus front, rendered by the studio; its main block texture until the picture loads
				const tex = LOOK_TEXTURES[value];
				sw.classList.add("pic");
				sw.style.backgroundImage = "url('thumbs/" + value + ".jpg'), url('tex/" + (tex.includes("/") ? tex : "aurelia_transit_architecture/block/" + tex) + ".png')";
			} else {
				sw.classList.add("custom");
				sw.textContent = "✎";
			}
			const name = document.createElement("span");
			name.textContent = label;
			b.append(sw, name);
			b.title = value === "AUTO" ? "No city: the plain look, or whatever your words describe" : label + " look";
			b.addEventListener("click", () => set("look", value));
			looks.append(b);
			synced.push(() => press(b, $("look").value === value));
		}
		segmented("mode", "modeSeg");
		synced.push(() => { $("modeHelp").textContent = MODE_HELP[$("mode").value] || ""; });
		segmented("layout", "layoutSeg");

		// the building and its size are one choice here: No, Auto, Compact, City, Grand
		const building = $("buildingSeg");
		building.innerHTML = "";
		const choices = [["0", "", "No"]].concat(options.sizes.map(([v, l]) => ["1", v, v === "AUTO" ? "Auto" : l]));
		for (const [has, size, label] of choices) {
			const b = document.createElement("button");
			b.type = "button";
			b.textContent = label;
			b.addEventListener("click", () => {
				if (size) {
					$("size").value = size;
				}
				set("building", has);
			});
			building.append(b);
			synced.push(() => press(b, $("building").value === has && (has === "0" || $("size").value === size)));
		}

		// A hub needs an even number of tracks, at least 4 (two run through, the rest end), so it steps by two.
		const hub = () => $("mode").value === "hub";
		const lowest = () => hub() ? 4 : +$("tracks").min;
		const step = (delta) => {
			const t = $("tracks");
			const next = Math.max(lowest(), Math.min(+t.max, +t.value + delta * (hub() ? 2 : 1)));
			if (String(next) !== t.value) {
				set("tracks", next);
			}
		};
		$("tracksDown").addEventListener("click", () => step(-1));
		$("tracksUp").addEventListener("click", () => step(1));
		synced.push(() => {
			$("tracksDown").disabled = +$("tracks").value <= lowest();
			$("tracksUp").disabled = +$("tracks").value + (hub() ? 2 : 1) > +$("tracks").max;
		});

		const presets = $("presets");
		presets.innerHTML = "";
		for (const p of options.presets) {
			const b = document.createElement("button");
			b.type = "button";
			b.className = "chip";
			b.textContent = p.key.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
			b.title = p.description;
			// the preset sets the buttons (they are always sent, so they would otherwise win over its numbers); the rest of its
			// words, such as the roof and the footbridge, go in the description
			b.addEventListener("click", () => {
				$("text").value = p.text;
				$("look").value = p.look;
				$("mix").value = "";
				$("mode").value = p.mode;
				$("tracks").value = p.tracks;
				$("length").value = p.length;
				$("layout").value = p.layout;
				$("roof").value = "AUTO";
				$("building").value = "1";
				$("size").value = "AUTO";
				$("name").value = p.name;
				changed();
			});
			presets.append(b);
		}
	}

	function syncButtons() {
		for (const sync of synced) {
			sync();
		}
	}

	function showWhere() {
		$("where").innerHTML = IN_GAME ? "Connected to <b>your game</b>" : "Design mode · not connected to a game";
		$("where").classList.toggle("live", IN_GAME);
	}

	let timer = 0;
	function changed() {
		updateOutputs();
		remember();
		clearTimeout(timer);
		timer = setTimeout(generate, 350);
	}

	// ---- advanced: platforms one by one -----------------------------------------------------------------------------------
	// The rows are written into the hidden "platforms" field as words the game's parser reads ("platform 1: island 12 wide;"),
	// so the plan, the wrench and a typed description all agree.

	const MAX_PLATFORMS = 10;
	let plats = [];

	function platWords() {
		if (!$("listOn").checked) {
			return "";
		}
		return plats.map((p, i) => "platform " + (i + 1) + ": " + p.kind.replace("-", " ") + (p.width ? " " + p.width + " wide" : "")
			+ (p.length ? " " + p.length + " long" : "") + (p.roof !== "station" ? " " + p.roof : "") + ";").join(" ");
	}

	function readPlats(text) {
		const out = [];
		const clause = /platform\s+(\d+)\s*:\s*([^;]*);?/gi;
		let m;
		while ((m = clause.exec(text)) && out.length < MAX_PLATFORMS) {
			const w = m[2].toLowerCase();
			const width = /(\d+)\s+wide/.exec(w);
			const length = /(\d+)\s+long/.exec(w);
			out.push({ kind: (/island/.test(w) ? "island" : "side") + (/\bbay\b/.test(w) ? "-bay" : ""), width: width ? +width[1] : 0, length: length ? +length[1] : 0,
				roof: /canopy/.test(w) ? "canopy" : /open/.test(w) ? "open" : "station" });
		}
		return out;
	}

	/** A first list from the buttons above: two side platforms for one or two tracks, islands for more. */
	function defaultPlats() {
		const tracks = +$("tracks").value;
		const islands = $("layout").value === "SIDE" ? 0 : tracks <= 2 && $("layout").value !== "ISLAND" ? 0 : Math.floor(tracks / 2);
		const list = [];
		if (islands === 0) {
			list.push({ kind: "side", width: 0, length: 0, roof: "station" });
			if (tracks >= 2) {
				list.push({ kind: "side", width: 0, length: 0, roof: "station" });
			}
		} else {
			for (let i = 0; i < islands; i++) {
				list.push({ kind: "island", width: 0, length: 0, roof: "station" });
			}
			if (tracks % 2 === 1) {
				list.push({ kind: "side", width: 0, length: 0, roof: "station" });
			}
		}
		return list.slice(0, MAX_PLATFORMS);
	}

	function platSelect(pairs, value, onChange, label) {
		const s = document.createElement("select");
		s.setAttribute("aria-label", label);
		for (const [v, l] of pairs) {
			s.append(new Option(l, v));
		}
		s.value = String(value);
		s.addEventListener("input", () => onChange(s.value));
		return s;
	}

	function renderPlats() {
		const on = $("listOn").checked;
		$("listBody").hidden = !on;
		// the list sets the tracks and the platform kinds, so those controls step aside while it is on
		for (const id of ["tracksDown", "tracksUp", "layoutSeg"]) {
			$(id).classList.toggle("dimmed", on);
		}
		$("platOut").textContent = plats.length + (plats.length === 1 ? " platform" : " platforms");
		$("platDown").disabled = plats.length <= 1;
		$("platUp").disabled = plats.length >= MAX_PLATFORMS;
		const tracks = plats.reduce((n, p) => n + (p.kind.startsWith("island") ? 2 : 1), 0);
		$("platNote").textContent = on ? tracks + (tracks === 1 ? " track" : " tracks") + ". Auto uses the station's width, length and roof."
			+ " A bay's tracks end at buffer stops past its far end (in a terminus every track does)." : "";
		const rows = $("platRows");
		rows.innerHTML = "";
		const widths = [["0", "Auto width"]];
		for (let w = 3; w <= 20; w++) {
			widths.push([String(w), w + " wide"]);
		}
		plats.forEach((p, i) => {
			const row = document.createElement("div");
			row.className = "plat";
			const num = document.createElement("span");
			num.className = "num";
			num.textContent = i + 1;
			const length = document.createElement("input");
			length.type = "number";
			length.min = options.length[0];
			length.max = options.length[1];
			length.placeholder = "Auto length";
			length.setAttribute("aria-label", "Platform " + (i + 1) + " length");
			length.value = p.length || "";
			length.addEventListener("change", () => {
				const n = Math.round(+length.value);
				p.length = length.value === "" || !n ? 0 : Math.min(options.length[1], Math.max(options.length[0], n));
				length.value = p.length || "";
				platsChanged();
			});
			row.append(num,
				platSelect([["side", "Side"], ["island", "Island"], ["side-bay", "Side bay"], ["island-bay", "Island bay"]], p.kind, (v) => { p.kind = v; platsChanged(); }, "Platform " + (i + 1) + " type"),
				platSelect([["station", "Station roof"], ["canopy", "Canopy"], ["open", "Open"]], p.roof, (v) => { p.roof = v; platsChanged(); },
					"Platform " + (i + 1) + " roof"),
				platSelect(widths, p.width, (v) => { p.width = +v; platsChanged(); }, "Platform " + (i + 1) + " width"),
				length);
			rows.append(row);
		});
	}

	function platsChanged() {
		$("platforms").value = platWords();
		renderPlats();
		changed();
	}


	function setupPlatforms() {
		plats = readPlats($("platforms").value);
		$("listOn").checked = plats.length > 0;
		$("listOn").addEventListener("input", () => {
			if ($("listOn").checked && plats.length === 0) {
				plats = defaultPlats();
			}
			platsChanged();
		});
		$("platDown").addEventListener("click", () => {
			plats.pop();
			platsChanged();
		});
		$("platUp").addEventListener("click", () => {
			plats.push({ kind: plats.length ? plats[plats.length - 1].kind : "side", width: 0, length: 0, roof: "station" });
			platsChanged();
		});
		renderPlats();
	}

	async function init() {
		options = await (await fetch("api/options")).json();
		fillSelect($("layout"), options.layouts);
		// "Custom", as on the wrench: no city look, the plain station or whatever the words describe
		options.looks = options.looks.map(([v, l]) => [v, v === "AUTO" ? "Custom" : l]);
		fillSelect($("look"), options.looks);
		fillSelect($("mix"), options.looks.filter(([v]) => v !== "AUTO"), ["", "Nothing"]);
		fillSelect($("roof"), options.roofs);
		fillSelect($("size"), options.sizes);
		$("tracks").min = options.tracks[0];
		$("tracks").max = options.tracks[1];
		$("tracks").value = 2;
		$("length").min = options.length[0];
		$("length").max = options.length[1];
		$("length").value = options.length[2];
		$("text").maxLength = options.maxTyped;
		$("name").maxLength = options.maxName;
		buildButtons();
		restore();
		setupPlatforms();
		updateOutputs();
		showWhere();
		$("game").hidden = !IN_GAME;
		$("offline").hidden = IN_GAME;
		// A terminus is a city's main station: one still at the default size becomes 8 tracks, 160 long (as on the wrench).
		$("mode").addEventListener("input", () => {
			if ($("mode").value !== "through" && $("tracks").value === "2" && $("length").value === String(options.length[2])) {
				$("tracks").value = 8;
				$("length").value = 160;
			}
			if ($("mode").value === "hub") {
				const t = +$("tracks").value;
				$("tracks").value = Math.min(+$("tracks").max - +$("tracks").max % 2, Math.max(4, t + t % 2));
				$("layout").value = "AUTO";
			}
		});
		for (const f of fields) {
			$(f).addEventListener("input", changed);
		}
		if ($("text").value) {
			$("words").open = true;
		}
		for (const b of document.querySelectorAll("[data-send]")) {
			b.addEventListener("click", () => send(b.dataset.send));
		}
		generate();
	}

	async function send(action) {
		$("sent").textContent = "Sending…";
		try {
			const body = new URLSearchParams(values());
			body.set("action", action);
			const r = await fetch("api/send", { method: "POST", body, headers: { "X-Studio-Token": TOKEN } });
			$("sent").textContent = await r.text();
		} catch (e) {
			$("sent").textContent = "The game did not answer. Is it still running?";
		}
	}

	let request = 0;
	async function generate() {
		const mine = ++request;
		$("busy").hidden = false;
		try {
			const r = await fetch("api/plan", { method: "POST", body: new URLSearchParams(values()) });
			if (!r.ok) {
				throw new Error(await r.text());
			}
			const plan = await r.json();
			if (mine !== request) {
				return;
			}
			current = plan;
			showPlan(plan);
			report(plan);
		} catch (e) {
			$("banner").textContent = String(e.message || e);
		} finally {
			if (mine === request) {
				$("busy").hidden = true;
			}
		}
	}

	// ---- 3D view --------------------------------------------------------------------------------------------------------

	const canvas = $("view");
	// Without WebGL (graphics acceleration off, or the browser gave up on the GPU) the page still plans and checks stations;
	// only the 3D view is replaced by a note, through a renderer that draws nothing.
	const renderer = (() => {
		try {
			const r = new THREE.WebGLRenderer({ canvas, antialias: true });
			r.setPixelRatio(Math.min(window.devicePixelRatio, 2));
			r.outputEncoding = THREE.sRGBEncoding;
			return r;
		} catch (e) {
			$("no3d").hidden = false;
			$("stage").classList.add("no-gl");
			return { render() {}, setSize() {}, getPixelRatio: () => 1, clippingPlanes: [] };
		}
	})();
	const scene = new THREE.Scene();
	scene.background = new THREE.Color(0x101317);
	const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 4000);
	const station = new THREE.Group();
	scene.add(station);
	const ground = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ color: 0x23282e }));
	ground.rotation.x = -Math.PI / 2;
	scene.add(ground);

	const view = { target: new THREE.Vector3(), radius: 80, yaw: -Math.PI / 4, pitch: 0.6 };
	let bounds = new THREE.Box3(new THREE.Vector3(-10, -1, -10), new THREE.Vector3(10, 10, 10));

	const loader = new THREE.TextureLoader();
	const textures = new Map();
	const materials = new Map();

	function texture(path) {
		if (!textures.has(path)) {
			const t = loader.load("tex/" + path + ".png", (tex) => {
				const img = tex.image;
				if (img && img.height > img.width) {
					// animated strip: show its first frame
					tex.repeat.set(1, img.width / img.height);
					tex.offset.set(0, 1 - img.width / img.height);
				}
				tex.needsUpdate = true;
				draw();
			});
			t.magFilter = THREE.NearestFilter;
			t.minFilter = THREE.NearestFilter;
			t.generateMipmaps = false;
			t.encoding = THREE.sRGBEncoding;
			textures.set(path, t);
		}
		return textures.get(path);
	}

	function material(path, roleColor) {
		const useTex = $("textures").checked && path;
		const key = useTex ? "t:" + path : "c:" + roleColor;
		if (!materials.has(key)) {
			const glassy = useTex && /glass|window|pane/.test(path);
			const m = new THREE.MeshBasicMaterial({
				vertexColors: true,
				side: THREE.DoubleSide,
				alphaTest: glassy ? 0.02 : 0.1,
				transparent: glassy,
				opacity: 1,
				depthWrite: !glassy
			});
			if (useTex) {
				m.map = texture(path);
			} else {
				m.color = new THREE.Color(roleColor);
			}
			materials.set(key, m);
		}
		return materials.get(key);
	}

	// The four corners of each face as seen from outside: top-left, top-right, bottom-right, bottom-left (matching uv u1v1..u2v2).
	function corners(face, f, t) {
		switch (face) {
			case "north": return [[t[0], t[1], f[2]], [f[0], t[1], f[2]], [f[0], f[1], f[2]], [t[0], f[1], f[2]]];
			case "south": return [[f[0], t[1], t[2]], [t[0], t[1], t[2]], [t[0], f[1], t[2]], [f[0], f[1], t[2]]];
			case "west": return [[f[0], t[1], f[2]], [f[0], t[1], t[2]], [f[0], f[1], t[2]], [f[0], f[1], f[2]]];
			case "east": return [[t[0], t[1], t[2]], [t[0], t[1], f[2]], [t[0], f[1], f[2]], [t[0], f[1], t[2]]];
			case "up": return [[f[0], t[1], f[2]], [t[0], t[1], f[2]], [t[0], t[1], t[2]], [f[0], t[1], t[2]]];
			default: return [[f[0], f[1], t[2]], [t[0], f[1], t[2]], [t[0], f[1], f[2]], [f[0], f[1], f[2]]];
		}
	}

	function defaultUv(face, f, t) {
		switch (face) {
			case "north": return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]];
			case "south": return [f[0], 16 - t[1], t[0], 16 - f[1]];
			case "west": return [f[2], 16 - t[1], t[2], 16 - f[1]];
			case "east": return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]];
			case "up": return [f[0], f[2], t[0], t[2]];
			default: return [f[0], 16 - t[2], t[0], 16 - f[2]];
		}
	}

	const AXES = { x: new THREE.Vector3(1, 0, 0), y: new THREE.Vector3(0, 1, 0), z: new THREE.Vector3(0, 0, 1) };

	// One model's geometry, split by texture: { texturePath: BufferGeometry } in block units (0..1), rotated by its variant.
	function modelGeometry(model) {
		const groups = new Map();
		for (const part of model.parts) {
			const turn = new THREE.Matrix4().makeTranslation(0.5, 0.5, 0.5)
				.multiply(new THREE.Matrix4().makeRotationY(-part.y * Math.PI / 180))
				.multiply(new THREE.Matrix4().makeRotationX(-part.x * Math.PI / 180))
				.multiply(new THREE.Matrix4().makeTranslation(-0.5, -0.5, -0.5));
			for (const el of part.elements) {
				let elementTurn = null;
				if (el.rot) {
					const o = el.rot.origin.length === 3 ? el.rot.origin : [8, 8, 8];
					elementTurn = new THREE.Matrix4().makeTranslation(o[0], o[1], o[2])
						.multiply(new THREE.Matrix4().makeRotationAxis(AXES[el.rot.axis] || AXES.y, el.rot.angle * Math.PI / 180))
						.multiply(new THREE.Matrix4().makeTranslation(-o[0], -o[1], -o[2]));
				}
				for (const face of Object.keys(el.faces)) {
					const fd = el.faces[face];
					const key = fd.t || "";
					if (!groups.has(key)) {
						groups.set(key, { pos: [], uv: [], col: [] });
					}
					const g = groups.get(key);
					const c = corners(face, el.from, el.to);
					const uv = fd.uv && fd.uv.length === 4 ? fd.uv : defaultUv(face, el.from, el.to);
					let uvs = [[uv[0], uv[1]], [uv[2], uv[1]], [uv[2], uv[3]], [uv[0], uv[3]]];
					const k = ((fd.r || 0) / 90) % 4;
					if (k) {
						uvs = [0, 1, 2, 3].map((i) => uvs[(i - k + 4) % 4]);
					}
					const shade = el.shade === false ? 1 : FACE_SHADE[face] || 1;
					const colour = fd.tint !== undefined ? TINT.clone().multiplyScalar(shade) : new THREE.Color(shade, shade, shade);
					const v = c.map((p) => {
						const vec = new THREE.Vector3(p[0], p[1], p[2]);
						if (elementTurn) {
							vec.applyMatrix4(elementTurn);
						}
						return vec.multiplyScalar(1 / 16).applyMatrix4(turn);
					});
					for (const i of [0, 3, 2, 0, 2, 1]) {
						g.pos.push(v[i].x, v[i].y, v[i].z);
						g.uv.push(uvs[i][0] / 16, 1 - uvs[i][1] / 16);
						g.col.push(colour.r, colour.g, colour.b);
					}
				}
			}
		}
		const out = new Map();
		for (const [key, g] of groups) {
			const geo = new THREE.BufferGeometry();
			geo.setAttribute("position", new THREE.Float32BufferAttribute(g.pos, 3));
			geo.setAttribute("uv", new THREE.Float32BufferAttribute(g.uv, 2));
			geo.setAttribute("color", new THREE.Float32BufferAttribute(g.col, 3));
			out.set(key, geo);
		}
		return out;
	}

	function roleColour(role) {
		let h = 0;
		for (const ch of role) {
			h = (h * 31 + ch.charCodeAt(0)) >>> 0;
		}
		return new THREE.Color().setHSL((h % 360) / 360, 0.35, 0.55).getHex();
	}

	let meshes = [];
	function showPlan(plan) {
		for (const m of meshes) {
			station.remove(m);
			m.geometry.dispose();
		}
		meshes = [];
		materials.clear();
		const cells = plan.cells;
		const byModel = new Map();
		bounds = new THREE.Box3();
		for (let i = 0; i < cells.length; i += 5) {
			const model = cells[i + 3];
			if (!byModel.has(model)) {
				byModel.set(model, []);
			}
			byModel.get(model).push(i);
			bounds.expandByPoint(new THREE.Vector3(cells[i], cells[i + 1], cells[i + 2]));
		}
		bounds.max.addScalar(1);
		const matrix = new THREE.Matrix4();
		for (const [index, list] of byModel) {
			const entry = plan.models[index];
			const role = plan.roles[cells[list[0] + 4]];
			for (const [texPath, geo] of modelGeometry(entry.model)) {
				const mesh = new THREE.InstancedMesh(geo, material(texPath, roleColour(role)), list.length);
				list.forEach((at, n) => {
					matrix.makeTranslation(cells[at], cells[at + 1], cells[at + 2]);
					mesh.setMatrixAt(n, matrix);
				});
				mesh.userData = { model: index, cells: list };
				mesh.frustumCulled = false;
				station.add(mesh);
				meshes.push(mesh);
			}
		}
		const size = bounds.getSize(new THREE.Vector3());
		ground.scale.set(size.x + 40, size.z + 40, 1);
		ground.position.set((bounds.min.x + bounds.max.x) / 2, bounds.min.y - 0.02, (bounds.min.z + bounds.max.z) / 2);
		$("banner").innerHTML = "<b>" + esc(plan.title) + "</b>" + plan.size.blocks.toLocaleString() + " blocks · " + plan.size.width + " wide, "
			+ plan.size.length + " long, " + plan.size.height + " high";
		$("raw").textContent = JSON.stringify(Object.assign({}, plan, { cells: "[" + plan.cells.length / 5 + " blocks]", models: "[" + plan.models.length + " models]" }), null, 2);
		// frame the first station, and again when a station grows or shrinks a lot (a preset, many more tracks), so it fits
		const reach = Math.max(size.x, size.z);
		if (!showPlan.framed || reach > showPlan.framed * 1.5 || reach < showPlan.framed / 1.5) {
			setView(currentView);
			showPlan.framed = reach;
		}
		$("headView").hidden = !plan.head;
		if (!plan.head && currentView === "head") {
			setView("overview");
		}
		updateCut();
		draw();
	}

	let currentView = "overview";
	// A face view is named "face:<n>": standing on that boarding face's platform, from the plan's per-face view.
	function setView(name, smooth) {
		const face = /^face:/.test(name) && current ? current.faceLabels.find((f) => "face:" + f.n === name && f.view) : null;
		if (/^face:/.test(name) && !face) {
			name = "overview";
		}
		currentView = name;
		glide = null;
		for (const b of document.querySelectorAll("[data-view]")) {
			b.setAttribute("aria-pressed", String(b.dataset.view === name));
		}
		if (face) {
			const from = { eye: camera.position.clone(), at: view.target.clone() };
			lookFrom(face.view);
			if (smooth && !matchMedia("(prefers-reduced-motion: reduce)").matches) {
				const to = { eye: orbitEye(), at: view.target.clone() };
				glide = { from, to, start: performance.now(), ms: 650 };
				Object.assign(view, viewFrom(from.eye, from.at));
			}
			draw();
			return;
		}
		const c = bounds.getCenter(new THREE.Vector3());
		const size = bounds.getSize(new THREE.Vector3());
		const reach = Math.max(size.x, size.z, size.y * 2);
		view.target.copy(c);
		view.target.y = Math.min(c.y, 6);
		if (name === "overview") {
			Object.assign(view, { yaw: -Math.PI / 4, pitch: 0.55, radius: reach * 1.15 });
		} else if (name === "reverse") {
			Object.assign(view, { yaw: (3 * Math.PI) / 4, pitch: 0.55, radius: reach * 1.15 });
		} else if (name === "street") {
			view.target.y = Math.min(size.y / 3, 8);
			Object.assign(view, { yaw: -Math.PI / 2 - 0.25, pitch: 0.12, radius: Math.max(size.z, size.y * 3) * 0.75 });
		} else if (name === "plan") {
			Object.assign(view, { yaw: 0, pitch: Math.PI / 2 - 0.001, radius: reach * 1.25 });
		} else if (name === "passenger" && current && current.passenger) {
			lookFrom(current.passenger);
		} else if (name === "head" && current && current.head) {
			// in front of a terminus's head building, a little to one side and above, looking at its street front
			const h = current.head;
			const front = new THREE.Vector3(h.x + 0.5, Math.min(size.y * 0.4, 12), h.z + 0.5);
			// far enough back that the whole front fits, however low the building is
			const away = Math.max(h.width * 1.5, size.y * 2.2, 24);
			const side = new THREE.Vector3(-h.dz, 0, h.dx).multiplyScalar(away * 0.25);
			const eye = front.clone().add(new THREE.Vector3(h.dx, 0, h.dz).multiplyScalar(away)).add(side);
			eye.y = Math.min(size.y * 0.55, 18);
			Object.assign(view, viewFrom(eye, front));
		}
		draw();
	}

	// Stand at a passenger's eye height in cell {x, z}, looking towards cell {tx, tz}; the orbit turns about a point ahead.
	function lookFrom(p) {
		const eye = new THREE.Vector3(p.x + 0.5, 2.6, p.z + 0.5);
		const ahead = new THREE.Vector3(p.tx + 0.5, 2.4, p.tz + 0.5);
		const dir = ahead.clone().sub(eye).normalize();
		Object.assign(view, viewFrom(eye, eye.clone().addScaledVector(dir, 12)));
	}

	function viewFrom(eye, at) {
		const off = eye.clone().sub(at);
		const radius = Math.max(off.length(), 0.01);
		return { target: at.clone(), radius, yaw: Math.atan2(off.x, off.z), pitch: Math.asin(off.y / radius) };
	}

	function orbitEye() {
		const p = view.pitch;
		return new THREE.Vector3(
			view.target.x + view.radius * Math.cos(p) * Math.sin(view.yaw),
			view.target.y + view.radius * Math.sin(p),
			view.target.z + view.radius * Math.cos(p) * Math.cos(view.yaw));
	}

	// A short eased camera move into a face view: eye and look point travel in straight lines, so the view turns as it goes.
	let glide = null;
	function stepGlide() {
		if (!glide) {
			return;
		}
		const t = Math.min(1, (performance.now() - glide.start) / glide.ms);
		const k = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
		const eye = glide.from.eye.clone().lerp(glide.to.eye, k);
		const at = glide.from.at.clone().lerp(glide.to.at, k);
		Object.assign(view, viewFrom(eye, at));
		if (t >= 1) {
			Object.assign(view, viewFrom(glide.to.eye, glide.to.at));
			glide = null;
		} else {
			draw();
		}
	}

	function place() {
		view.pitch = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.001, view.pitch));
		camera.position.copy(orbitEye());
		camera.lookAt(view.target);
		camera.near = Math.max(0.05, view.radius / 2000);
		camera.updateProjectionMatrix();
	}

	let pending = false;
	function draw() {
		if (pending) {
			return;
		}
		pending = true;
		requestAnimationFrame(() => {
			pending = false;
			resize();
			stepGlide();
			place();
			renderer.render(scene, camera);
			labels();
		});
	}

	function resize() {
		const w = canvas.clientWidth;
		const h = canvas.clientHeight;
		if (canvas.width !== Math.floor(w * renderer.getPixelRatio()) || canvas.height !== Math.floor(h * renderer.getPixelRatio())) {
			renderer.setSize(w, h, false);
			camera.aspect = w / Math.max(h, 1);
		}
	}

	// The number badges are buttons kept from frame to frame (so hover, focus and a click survive redraws); one jumps to the
	// view from its platform.
	let badges = [];
	function labels() {
		const box = $("labels");
		const faces = current && $("faces").checked ? current.faceLabels : [];
		if (badges.length !== faces.length || badges.some((b, i) => b.face !== faces[i])) {
			box.innerHTML = "";
			badges = faces.map((f) => {
				const b = document.createElement("button");
				b.type = "button";
				b.className = "face-label";
				b.textContent = f.n;
				b.face = f;
				if (f.view) {
					b.title = "View from platform " + f.platform + " (boarding face " + f.n + ")";
					b.addEventListener("click", () => setView("face:" + f.n, true));
				} else {
					b.title = "Boarding face " + f.n + " (platform " + f.platform + ")";
					b.disabled = true;
				}
				box.append(b);
				return b;
			});
		}
		const w = canvas.clientWidth;
		const h = canvas.clientHeight;
		for (const b of badges) {
			const f = b.face;
			const v = new THREE.Vector3(f.x + 0.5, 2.5, f.z + 0.5).project(camera);
			const off = v.z > 1 || v.x < -1 || v.x > 1 || v.y < -1 || v.y > 1;
			b.hidden = off;
			b.style.left = ((v.x + 1) / 2) * w + "px";
			b.style.top = ((1 - v.y) / 2) * h + "px";
			b.setAttribute("aria-pressed", String(currentView === "face:" + f.n));
		}
	}

	// ---- cut ------------------------------------------------------------------------------------------------------------

	function updateCut() {
		const mode = $("cut").value;
		$("cutAt").disabled = mode === "none";
		if (mode === "none") {
			renderer.clippingPlanes = [];
			draw();
			return;
		}
		const t = $("cutAt").value / 100;
		let plane;
		if (mode === "height") {
			plane = new THREE.Plane(new THREE.Vector3(0, -1, 0), bounds.min.y + (bounds.max.y - bounds.min.y) * t);
		} else if (mode === "across") {
			plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), -(bounds.max.z - (bounds.max.z - bounds.min.z) * t));
		} else {
			plane = new THREE.Plane(new THREE.Vector3(-1, 0, 0), bounds.min.x + (bounds.max.x - bounds.min.x) * t);
		}
		renderer.clippingPlanes = [plane];
		draw();
	}

	// ---- mouse ----------------------------------------------------------------------------------------------------------

	let drag = null;
	canvas.addEventListener("contextmenu", (e) => e.preventDefault());
	canvas.addEventListener("pointerdown", (e) => {
		glide = null;
		canvas.setPointerCapture(e.pointerId);
		drag = { x: e.clientX, y: e.clientY, pan: e.shiftKey || e.button === 2, moved: 0 };
	});
	canvas.addEventListener("pointermove", (e) => {
		if (!drag) {
			return;
		}
		const dx = e.clientX - drag.x;
		const dy = e.clientY - drag.y;
		drag.x = e.clientX;
		drag.y = e.clientY;
		drag.moved += Math.abs(dx) + Math.abs(dy);
		if (drag.pan) {
			const scale = view.radius / canvas.clientHeight;
			const right = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 0);
			const up = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 1);
			view.target.addScaledVector(right, -dx * scale).addScaledVector(up, dy * scale);
		} else {
			view.yaw -= dx * 0.006 * (invertX ? -1 : 1);
			view.pitch += dy * 0.006 * (invertY ? -1 : 1);
		}
		draw();
	});
	canvas.addEventListener("pointerup", (e) => {
		if (drag && drag.moved < 4) {
			pick(e);
		}
		drag = null;
	});
	canvas.addEventListener("wheel", (e) => {
		e.preventDefault();
		glide = null;
		view.radius = Math.max(2, Math.min(3000, view.radius * Math.exp(e.deltaY * 0.0012)));
		draw();
	}, { passive: false });

	const ray = new THREE.Raycaster();
	function pick(e) {
		if (!current) {
			return;
		}
		const r = canvas.getBoundingClientRect();
		ray.setFromCamera(new THREE.Vector2(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1), camera);
		const hit = ray.intersectObjects(meshes, false).find((h) => {
			if (!renderer.clippingPlanes.length) {
				return true;
			}
			return renderer.clippingPlanes[0].distanceToPoint(h.point) >= 0;
		});
		const box = $("pick");
		if (!hit) {
			box.hidden = true;
			return;
		}
		const mesh = hit.object;
		const at = mesh.userData.cells[hit.instanceId];
		const c = current.cells;
		box.textContent = current.models[mesh.userData.model].id + " · " + current.roles[c[at + 4]].toLowerCase().replace(/_/g, " ")
			+ " · x " + c[at] + ", y " + c[at + 1] + ", z " + c[at + 2];
		box.hidden = false;
	}

	for (const b of document.querySelectorAll("[data-view]")) {
		b.addEventListener("click", () => setView(b.dataset.view));
	}
	$("reset").addEventListener("click", () => setView("overview"));
	$("invertX").addEventListener("click", () => {
		invertX = !invertX;
		$("invertX").setAttribute("aria-pressed", String(invertX));
	});
	$("invertY").addEventListener("click", () => {
		invertY = !invertY;
		$("invertY").setAttribute("aria-pressed", String(invertY));
	});
	$("cut").addEventListener("change", updateCut);
	$("cutAt").addEventListener("input", updateCut);
	$("faces").addEventListener("change", draw);
	$("textures").addEventListener("change", () => current && showPlan(current));
	window.addEventListener("resize", draw);

	// ---- report ---------------------------------------------------------------------------------------------------------

	function esc(text) {
		return String(text).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
	}

	function report(plan) {
		const r = plan.requested;
		const b = plan.built;
		// the third value says whether the request was met (true / false), or null where there is nothing to compare
		const rows = [
			["Station mode", r.mode, b.mode, r.mode === b.mode],
			["Layout", r.layout, b.layout + " (" + b.platforms + ")", r.layout === "Auto" || b.layout.startsWith(r.layout.toLowerCase())],
			["Tracks", r.tracks, b.tracks + " tracks, " + b.faces + " boarding faces", r.tracks === b.tracks],
			["Length", r.length + " blocks", b.length + " blocks on every face", r.length === b.length],
			["Look", r.look, b.style.replace(/_/g, " ") + " platforms", null],
			["Building", r.building ? "yes" : "no", b.building, r.building === (b.building !== "no building")],
			["Building size", r.building ? r.size : "", b.scale, r.size.startsWith("Auto") || !r.building ? null : r.size.toLowerCase() === b.scale],
			["Roof", r.roof, b.roof, null],
			["Footbridge", "", b.footbridge ? "yes" : "no", null],
			["Name", "", b.name || "(no name signs)", null],
			["Overall", "", plan.size.width + " wide × " + plan.size.length + " long × " + plan.size.height + " high, " + plan.size.blocks.toLocaleString() + " blocks", null]
		];
		$("reqTable").innerHTML = rows.map(([k, want, got, met]) =>
			"<tr><th>" + esc(k) + "</th><td class='" + (met === true ? "ok" : met === false ? "diff" : "") + "'>" + esc(got)
			+ (met === false && want !== "" ? " <span class='chip muted'>asked: " + esc(want) + "</span>" : "") + "</td></tr>"
		).join("");

		const chips = (list, cls) => list.length ? "<div class='chips'>" + list.map((t) => "<span class='chip " + (cls || "") + "'>" + esc(t) + "</span>").join("") + "</div>" : "<p class='note'>None</p>";
		$("fromText").innerHTML =
			"<div class='group'><h3>Recognised</h3>" + chips(plan.understood) + "</div>" +
			"<div class='group'><h3>Conflicts and limits</h3>" + chips(plan.warnings, "warn") + "</div>" +
			"<div class='group'><h3>Not understood</h3>" + chips(plan.ignored, "muted") + "</div>" +
			"<div class='group'><h3>Filled in</h3>" + chips(plan.defaulted, "muted") + "</div>" +
			"<p class='note'>The planner read: “" + esc(plan.text) + "”</p>";

		const problems = plan.violations.map((v) => ({ status: "FAIL", message: v.message + " (x " + v.x + ", y " + v.y + ", z " + v.z + ")" }))
			.concat(plan.checks.filter((c) => c.status !== "PASS"));
		const passed = plan.checks.filter((c) => c.status === "PASS");
		const fails = problems.filter((c) => c.status === "FAIL").length;
		const warns = problems.length - fails;
		const plural = (n, word) => n + " " + word + (n === 1 ? "" : "s");
		const verdict = !plan.buildable
			? ["bad", "!", "Can't build this yet", plural(fails, "problem") + " to fix first"]
			: warns ? ["warned", "!", "Ready to build", plural(warns, "thing") + " worth a look"]
				: ["good", "✓", "Ready to build", "All " + plural(passed.length, "check") + " passed"];
		$("checks").innerHTML = "<div class='verdict " + verdict[0] + "'><span class='icon' aria-hidden='true'>" + verdict[1] + "</span><div><h2>"
			+ verdict[2] + "</h2><p>" + verdict[3] + "</p></div></div>"
			+ (problems.length ? "<ul class='issues'>" + problems.map((c) => "<li class='" + c.status + "'>" + esc(c.message) + "</li>").join("") + "</ul>" : "")
			+ (passed.length ? "<details class='fold'><summary>Passed checks (" + passed.length + ")</summary><ul class='list'>"
				+ passed.map((c) => "<li>" + esc(c.message) + "</li>").join("") + "</ul></details>" : "");
	}

	// ---- reload on change ----------------------------------------------------------------------------------------------
	// The page reloads itself when its files change on disk or the studio restarts (which also brings a new token). The
	// controls are kept in local storage, so a reload keeps them.

	let version = null;
	let lost = false;
	async function watch() {
		try {
			const r = await fetch("api/version", { cache: "no-store" });
			const now = await r.text();
			if (version !== null && now !== version) {
				location.reload();
				return;
			}
			version = now;
			if (lost) {
				lost = false;
				showWhere();
			}
		} catch (e) {
			if (!lost) {
				lost = true;
				$("where").textContent = "Station Studio stopped · this page reloads when it is back";
				$("where").classList.remove("live");
			}
		}
		setTimeout(watch, document.hidden ? 5000 : 1500);
	}

	init().catch((e) => {
		$("banner").textContent = "Station Studio could not start: " + e;
	});
	watch();
})();
