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

	const fields = ["text", "mode", "layout", "tracks", "length", "look", "mix", "roof", "building", "size", "name", "platforms", "bed", "esc", "walls", "trav", "class", "era", "facing", "operator"];

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
		// the controls the player changed: the server only prepends these to a typed description, so the rest stays the words'
		q.pinned = [...pinnedNow()].join(",");
		// the generator seed: the dice's, or the one kept while Details is locked
		q.seed = seeds.detail;
		return q;
	}

	// The controls that hold their value against typed words, a class or an era (which fill only what is open). The dice pins what
	// it rolls, but with a class or an era chosen those rolled settings are open again: the class decides them.
	function pinnedNow() {
		const pins = $("class").value !== "auto" || $("era").value !== "auto" ? new Set([...touched].filter((f) => !rolled.has(f))) : new Set(touched);
		// a locked group is the player's choice, whoever set it: the class or the words must not move it
		for (const [group, ids] of Object.entries(GROUPS)) {
			if (seeds.locks[group]) {
				ids.forEach((id) => pins.add(id));
			}
		}
		return pins;
	}

	function restore() {
		let saved = null;
		try {
			saved = JSON.parse(localStorage.getItem(STORE) || "null");
		} catch (e) {
			saved = null;
		}
		if (!saved) {
			return false;
		}
		if (saved.seeds) {
			Object.assign(seeds, saved.seeds);
			seeds.locks = Object.assign({}, saved.seeds.locks);
			// there was a Details lock before Look took the planner's seed
			delete seeds.locks.details;
		}
		for (const f of fields) {
			if (saved[f] !== undefined && $(f)) {
				$(f).value = saved[f];
				// a saved value the page no longer offers (or an older page without this control) falls back to the first option
				if ($(f).tagName === "SELECT" && $(f).selectedIndex < 0) {
					$(f).selectedIndex = 0;
				}
			}
		}
		// only the controls the player had changed stay pinned: every select has a value, so having one says nothing
		// what the player or the dice set (saved whole: "pinned" is filtered by the class and the locks)
		for (const f of saved.touched || (saved.pinned || "").split(",")) {
			if (fields.includes(f) && f !== "text") {
				touched.add(f);
			}
		}
		for (const f of saved.rolled || []) {
			if (touched.has(f)) {
				rolled.add(f);
			}
		}
		return true;
	}

	function remember() {
		try {
			localStorage.setItem(STORE, JSON.stringify(Object.assign(values(), { seeds, rolled: [...rolled], touched: [...touched] })));
		} catch (e) {
			// private window or storage blocked: the page still works, it just forgets
		}
	}

	function updateOutputs() {
		if ($("listOn") && $("listOn").checked) {
			// the advanced platform list sets the tracks
			const listed = plats.reduce((n, p) => n + (p.kind.startsWith("island") ? 2 : 1), 0);
			// keep the stepper on the list's own count, so + and - start from what is shown
			$("tracks").value = Math.max(+$("tracks").min, Math.min(+$("tracks").max, listed));
			$("tracksOut").textContent = listed + (listed === 1 ? " track" : " tracks");
		} else {
			$("tracksOut").textContent = $("tracks").value + ($("tracks").value === "1" ? " track" : " tracks");
		}
		$("tracks").disabled = $("listOn").checked;
		$("lengthOut").textContent = $("length").value + " blocks";
		$("lengthNote").textContent = lengthNote(+$("length").value);
		for (const id of ["tracks", "length", "cutAt"]) {
			const r = $(id);
			r.style.setProperty("--fill", ((r.value - r.min) / Math.max(1, r.max - r.min)) * 100 + "%");
		}
		$("count").textContent = $("text").value.length + " / " + options.maxTyped;
		syncButtons();
	}

	// ---- buttons over the hidden selects: the selects stay the single source of what is sent --------------------------

	// one real block texture per city (ATA's unless a namespace is given), shown behind the city's picture while it loads
	const LOOK_TEXTURES = {
		UTRECHT: "nlbe_wave_top_x", AMSTERDAM: "nlbe_brick", HAMBURG: "hamburg_clinker_brick", MUNICH: "munich_exposed_concrete",
		FRANKFURT: "frankfurt_sandstone_ashlar", ST_PANCRAS: "nlbe_brick_band_side", ANTWERP: "nlbe_limestone", NEW_YORK: "roma_travertine",
		TOKYO: "minecraft/block/bricks", ZURICH: "minecraft/block/sandstone_top", PARIS: "minecraft/block/calcite",
		MILAN: "minecraft/block/polished_diorite", BERLIN: "blue_tinted_glass", HONG_KONG: "minecraft/block/white_concrete", BEIJING: "grey_tinted_glass",
		BANGKOK: "minecraft/block/sandstone_top", KUALA_LUMPUR: "minecraft/block/quartz_block_side", MUMBAI: "frankfurt_sandstone_ashlar"
	};
	const MODE_HELP = {
		through: "Trains stop and carry on through the station.",
		terminus: "The tracks end at buffer stops in front of a big station hall.",
		hub: "A bit of both: the middle tracks end at the station hall, the outer ones carry on through."
	};
	const synced = [];
	// the controls the player changed: a typed description overrides everything else, but must agree with these
	const touched = new Set();
	// the touched controls that the dice set rather than the player
	const rolled = new Set();

	function set(id, value) {
		$(id).value = value;
		// with the platform list on, the tracks, platforms and type buttons still work: they rebuild the list to match
		if ((id === "tracks" || id === "layout" || id === "mode") && $("listOn") && $("listOn").checked) {
			rebuildPlats();
		}
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

		// A hub needs an even number of tracks, at least 4 (two run through, the rest end), so its slider steps by two from 4.
		synced.push(() => {
			const t = $("tracks");
			const hub = $("mode").value === "hub";
			// changing min before step re-snaps the value to the old step from the new min (6 became 7), so keep it across both
			const value = t.value;
			t.step = 1;
			t.min = hub ? 4 : options.tracks[0];
			t.step = hub ? 2 : 1;
			t.value = value;
		});
		segmented("operator", "operatorSeg");
		segmented("class", "classSeg");
		segmented("era", "eraSeg");
		segmented("facing", "facingSeg");
		segmented("walls", "wallsSeg");
		segmented("bed", "bedSeg");
		$("travSwitch").addEventListener("click", () => set("trav", $("trav").value ? "" : "1"));
		synced.push(() => $("travSwitch").setAttribute("aria-checked", String($("trav").value === "1")));

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
				// the preset's words already carry its look, type, tracks and length, so only its name (which they leave out)
				// is pinned: the words stay authoritative and nothing is said twice
				touched.clear();
				rolled.clear();
				touched.add("name");
				// the preset replaced the rolled settings: the seed alone no longer makes this station
				if (!seeds.edited) {
					seeds.edited = true;
					showSeed();
				}
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
		$("where").innerHTML = IN_GAME ? "Connected to <b>your game</b>" : "Design mode, no game";
		$("where").classList.toggle("live", IN_GAME);
	}

	/** The sidebar's player card and the version: the Minecraft name and face when the game opened the studio. */
	function showProfile() {
		const player = document.body.dataset.player;
		const uuid = document.body.dataset.uuid;
		const version = (document.body.dataset.version || "").replace(/^\{\{.*\}\}$/, "");
		$("ver").textContent = version ? "v" + version.replace(/\+.*$/, "") : "dev";
		const avatar = $("avatar");
		if (player && !/^\{\{/.test(player)) {
			$("mcName").textContent = player;
			$("mcBadge").textContent = "In game";
			$("mcBadge").classList.add("live");
			avatar.textContent = player.charAt(0).toUpperCase();
			if (/^[0-9a-f]{32}$/i.test(uuid)) {
				// the player's face from their public skin; the initial stays when it does not load (offline)
				const img = new Image();
				img.onload = () => {
					avatar.textContent = "";
					avatar.style.backgroundImage = "url('" + img.src + "')";
				};
				img.src = "https://mc-heads.net/avatar/" + uuid + "/72";
			}
		} else {
			avatar.innerHTML = "<svg viewBox='0 0 16 16' width='20' height='20'><rect x='3' y='2' width='10' height='9' rx='1' fill='none' stroke='currentColor' stroke-width='1.3'/><path d='M5 14h6M8 11v3' stroke='currentColor' stroke-width='1.3'/></svg>";
		}
	}

	/** What the length slider sets: every boarding edge, one block a metre. */
	function lengthNote(length) {
		return "Every boarding edge is " + length + " m long";
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
		// a hub is islands only (its outer tracks run through on both sides of the outer islands)
		const islands = $("mode").value === "hub" ? Math.floor(tracks / 2)
			: $("layout").value === "SIDE" ? 0 : tracks <= 2 && $("layout").value !== "ISLAND" ? 0 : Math.floor(tracks / 2);
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

	/** The list for the buttons' tracks, platforms and type, keeping each row's width, length and roof where a row stays. */
	function rebuildPlats() {
		const fresh = defaultPlats();
		fresh.forEach((p, i) => {
			if (plats[i]) {
				p.width = plats[i].width;
				p.length = plats[i].length;
				p.roof = plats[i].roof;
			}
		});
		plats = fresh;
		$("platforms").value = platWords();
		renderPlats();
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

	// ---- seed --------------------------------------------------------------------------------------------------------------
	// One seed, two jobs. The page rolls every setting that is not locked from it (Look, Shape, Building), so typing a seed
	// gives the same settings again; and it goes to the planner as the words "seed k7f2ra", which vary what the settings
	// leave open (canopy profile, floors, roof material, platform colours, benches). Each part of the station belongs to one
	// lock: Look keeps the city and the planner's seed (its choices are part of how the station looks), Shape the type, tracks,
	// length and platforms, Building whether there is one, its size and roof form, the stairs, travelators and track bed.

	const SEED_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
	const SEED_LENGTH = 6;
	const seeds = { value: "", detail: "", edited: false, locks: {} };
	let rolling = false;
	const reduceMotion = () => matchMedia("(prefers-reduced-motion: reduce)").matches;

	function freshSeed() {
		const bytes = new Uint32Array(SEED_LENGTH);
		crypto.getRandomValues(bytes);
		return Array.from(bytes, (n) => SEED_CHARS[n % SEED_CHARS.length]).join("");
	}

	/** A repeatable random stream for one part of the settings (xmur3 hash into mulberry32), so locking one never moves the others. */
	function stream(seed, part) {
		let h = 1779033703 ^ (seed + ":" + part).length;
		for (const ch of seed + ":" + part) {
			h = Math.imul(h ^ ch.charCodeAt(0), 3432918353);
			h = (h << 13) | (h >>> 19);
		}
		let a = h >>> 0;
		return () => {
			a = (a + 0x6D2B79F5) >>> 0;
			let t = a;
			t = Math.imul(t ^ (t >>> 15), t | 1);
			t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
			return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
		};
	}

	const choose = (rand, list) => list[Math.floor(rand() * list.length)];
	const clamp = (n, lo, hi) => Math.max(lo, Math.min(hi, n));

	const GROUPS = { look: ["look", "mix", "era"], shape: ["mode", "tracks", "length", "layout"], building: ["building", "size", "roof", "esc", "walls", "trav", "bed", "class"] };

	/**
	 * A locked Building keeps what the station shows, not the word "Auto": an Auto roof or size follows the city, which an
	 * unlocked Look re-rolls, so they become the roof and size the last plan was built with.
	 */
	function holdBuilding() {
		if (!current || !current.resolved) {
			return;
		}
		if ($("roof").value === "AUTO" || $("roof").value === "CANOPIES") {
			$("roof").value = current.resolved.roof;
			touched.add("roof");
		}
		if ($("size").value === "AUTO" && current.resolved.size !== "AUTO") {
			$("size").value = current.resolved.size;
			touched.add("size");
		}
	}

	/** Sets every unlocked setting from the seed. Writes the hidden selects directly; the caller plans once afterwards. */
	function rollSettings(seed) {
		if (!seeds.locks.look) {
			const r = stream(seed, "look");
			const cities = options.looks.map(([v]) => v).filter((v) => v !== "AUTO");
			const look = choose(r, cities);
			$("look").value = look;
			$("mix").value = r() < 0.3 ? choose(r, cities.filter((v) => v !== look)) : "";
			// its own stream, so the rolls made before the era existed come out the same; most seeds leave the era open
			const e = stream(seed, "era");
			$("era").value = e() < 0.7 ? "auto" : choose(e, options.eras.map(([v]) => v).filter((v) => v !== "auto"));
		}
		if (!seeds.locks.shape) {
			const r = stream(seed, "shape");
			const p = r();
			// a terminus or hub always has its head building, so a Building locked to none keeps the rolls to through stations
			const noBuilding = seeds.locks.building && $("building").value === "0";
			const mode = p < 0.55 || noBuilding ? "through" : p < 0.85 ? "terminus" : "hub";
			const [lo, hi] = options.tracks;
			const tracks = mode === "hub" ? choose(r, [4, 6, 8]) : mode === "terminus" ? choose(r, [3, 4, 6, 8]) : choose(r, [1, 2, 2, 3, 4, 4, 6]);
			const length = choose(r, mode === "through" ? [32, 48, 64, 64, 80, 96, 120] : [80, 96, 120, 140, 160]);
			$("mode").value = mode;
			// free the slider from a hub's 4-and-up, steps of two, before it takes the new count
			$("tracks").step = 1;
			$("tracks").min = lo;
			$("tracks").value = clamp(tracks, lo, hi);
			$("length").value = clamp(length, options.length[0], options.length[1]);
			$("layout").value = mode === "hub" ? "AUTO" : tracks === 1 ? choose(r, ["AUTO", "SIDE"]) : choose(r, ["AUTO", "AUTO", "SIDE", "ISLAND"]);
		}
		if (!seeds.locks.building) {
			const r = stream(seed, "building");
			$("building").value = $("mode").value !== "through" || r() < 0.75 ? "1" : "0";
			$("size").value = choose(r, options.sizes.map(([v]) => v));
			$("roof").value = r() < 0.4 ? "AUTO" : choose(r, options.roofs.map(([v]) => v).filter((v) => v !== "AUTO" && v !== "OPEN"));
			$("esc").value = choose(r, ["", "", "1", "reverse", "both"]);
			$("walls").value = r() < 0.25 ? "1" : "";
			$("trav").value = r() < 0.25 ? "1" : "";
			$("bed").value = choose(r, ["", "gravel", "andesite"]);
			// the class, from its own stream: a terminus or hub is a city or grand station, and a Building locked to none can only
			// be a halt, whose class the words would otherwise contradict
			const c = stream(seed, "class");
			const classes = options.classes.map(([v]) => v).filter((v) => v !== "auto");
			const open = c() < 0.7;
			const picked = choose(c, $("mode").value !== "through" ? ["city", "grand"] : classes);
			$("class").value = open ? "auto" : picked;
		}
		if (seeds.locks.building && $("building").value === "0" && $("class").value !== "auto" && $("class").value !== "halt") {
			// held: a locked "no building" cannot be a bigger class
			$("class").value = "auto";
		}
		if (seeds.locks.building) {
			holdBuilding();
		}
		// What the dice set is pinned like a hand-set control: the server drops every unpinned control as soon as there are any
		// typed words (the stairs, track bed and travelator choices count as words), so unpinned rolled settings would be lost.
		// Where typed words set the same thing, the words win (the server drops those pins).
		for (const [group, ids] of Object.entries(GROUPS)) {
			if (!seeds.locks[group]) {
				ids.forEach((id) => {
					touched.add(id);
					rolled.add(id);
				});
			} else {
				// a locked group is the player's choice
				ids.forEach((id) => rolled.delete(id));
			}
		}
		if ($("listOn").checked && !seeds.locks.shape) {
			rebuildPlats();
		}
	}

	/** Rolls the settings from a seed and plans the station; the letters flip like a departure board while it builds. */
	function applySeed(seed) {
		seeds.value = seed;
		// the planner's seed follows the dice unless Look is locked (its canopy, floor and roof-deck choices are the look)
		if (!seeds.locks.look || !seeds.detail) {
			seeds.detail = seed;
		}
		rolling = true;
		rollSettings(seed);
		rolling = false;
		seeds.edited = false;
		$("seed").value = seed;
		flip(seed);
		showSeed();
		updateOutputs();
		remember();
		clearTimeout(timer);
		generate();
	}

	function roll() {
		applySeed(freshSeed());
	}

	function renderFlaps(text) {
		const box = $("flaps");
		if (box.children.length !== SEED_LENGTH) {
			box.innerHTML = "";
			for (let i = 0; i < SEED_LENGTH; i++) {
				const cell = document.createElement("span");
				cell.className = "flap";
				box.append(cell);
			}
		}
		[...box.children].forEach((cell, i) => {
			cell.textContent = text[i] || "";
			cell.classList.toggle("empty", !text[i]);
		});
	}

	// each cell runs through a few letters before it lands, left to right, like a split-flap board settling
	let flipping = [];
	function flip(seed) {
		flipping.forEach(clearTimeout);
		flipping = [];
		renderFlaps(seed);
		if (reduceMotion()) {
			return;
		}
		[...$("flaps").children].forEach((cell, i) => {
			const turns = 3 + i * 2;
			for (let k = 0; k <= turns; k++) {
				flipping.push(setTimeout(() => {
					cell.textContent = k === turns ? seed[i] : SEED_CHARS[Math.floor(Math.random() * SEED_CHARS.length)];
					cell.classList.remove("turn");
					void cell.offsetWidth;
					cell.classList.add("turn");
				}, k * 60));
			}
		});
	}

	function showSeed() {
		$("seedBox").classList.toggle("edited", seeds.edited);
		for (const b of document.querySelectorAll("[data-lock]")) {
			b.setAttribute("aria-pressed", String(!!seeds.locks[b.dataset.lock]));
		}
		const note = $("seedNote");
		note.classList.toggle("seed-note-edited", seeds.edited);
		note.textContent = seeds.edited ? "Changed since the roll, so this seed alone no longer makes this station."
			: seeds.locks.look && seeds.detail !== seeds.value ? "The look is kept from seed " + seeds.detail + "."
				: "The same seed makes the same station. Press R to roll.";
	}

	function setupSeed() {
		const input = $("seed");
		input.addEventListener("input", () => {
			input.value = input.value.toLowerCase().replace(/[^a-z0-9]/g, "").slice(0, SEED_LENGTH);
			renderFlaps(input.value);
		});
		const commit = () => {
			if (input.value && input.value !== seeds.value) {
				applySeed(input.value);
			} else {
				input.value = seeds.value;
				renderFlaps(seeds.value);
			}
		};
		input.addEventListener("change", commit);
		input.addEventListener("keydown", (e) => {
			if (e.key === "Enter") {
				commit();
				input.blur();
			} else if (e.key === "Escape") {
				input.value = seeds.value;
				renderFlaps(seeds.value);
				input.blur();
			}
		});
		$("roll").addEventListener("click", roll);
		$("copySeed").addEventListener("click", () => copy(seeds.value, $("seedNote"), "Seed " + seeds.value + " copied."));
		for (const b of document.querySelectorAll("[data-lock]")) {
			b.addEventListener("click", () => {
				seeds.locks[b.dataset.lock] = !seeds.locks[b.dataset.lock];
				if (b.dataset.lock === "building" && seeds.locks.building) {
					holdBuilding();
					updateOutputs();
				}
				if (b.dataset.lock === "shape" && seeds.locks.shape && current && $("layout").value === "AUTO" && $("mode").value !== "hub") {
					// an Auto platform layout follows the look, the class and the building, which a roll changes: keep the one on show
					const shown = /island/i.test(current.built.layout) ? "ISLAND" : /side/i.test(current.built.layout) ? "SIDE" : "AUTO";
					$("layout").value = shown;
					updateOutputs();
				}
				if (!seeds.locks.look && seeds.detail !== seeds.value) {
					// unlocking Look brings the planner back to the seed on show
					seeds.detail = seeds.value;
					changed();
				}
				showSeed();
				remember();
			});
		}
		document.addEventListener("keydown", (e) => {
			const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName) || document.activeElement.isContentEditable;
			// R is the dice, but not while walking the station (the keys are the walker's then)
			if (!typing && !walk && !e.metaKey && !e.ctrlKey && !e.altKey && (e.key === "r" || e.key === "R")) {
				e.preventDefault();
				roll();
			}
		});
		input.value = seeds.value;
		renderFlaps(seeds.value);
		showSeed();
	}

	/** Copies text and says so in the note for a moment. */
	async function copy(text, note, said) {
		const before = note.textContent;
		try {
			await navigator.clipboard.writeText(text);
			note.textContent = said;
		} catch (e) {
			note.textContent = "Copy did not work here. Select the text and copy it yourself.";
		}
		setTimeout(() => {
			if (note.textContent === said) {
				note.textContent = before;
				if (note === $("seedNote")) {
					showSeed();
				}
			}
		}, 1800);
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
		fillSelect($("class"), options.classes);
		fillSelect($("era"), options.eras);
		fillSelect($("facing"), options.facings);
		$("tracks").min = options.tracks[0];
		$("tracks").max = options.tracks[1];
		$("tracks").value = 2;
		$("length").min = options.length[0];
		$("length").max = options.length[1];
		$("length").value = options.length[2];
		$("text").maxLength = options.maxTyped;
		$("name").maxLength = options.maxName;
		buildButtons();
		const returning = restore();
		setupPlatforms();
		showWhere();
		showProfile();
		// a first visit starts from a rolled station; a returning one keeps its settings and seed
		if (!returning || !seeds.value) {
			seeds.value = freshSeed();
			seeds.detail = seeds.value;
			rolling = true;
			rollSettings(seeds.value);
			rolling = false;
		}
		setupSeed();
		setupLayers();
		flip(seeds.value);
		updateOutputs();
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
			$(f).addEventListener("input", () => {
				touched.add(f);
				if (!rolling) {
					rolled.delete(f);
				}
				// a setting changed by hand after the roll: the seed alone no longer makes this station (the name is not rolled)
				if (!rolling && f !== "name" && f !== "text" && !seeds.edited) {
					seeds.edited = true;
					showSeed();
				}
				changed();
			});
		}
		$("copyCmd").addEventListener("click", () => {
			if (!current) {
				return;
			}
			const command = "/ata_station plan " + current.text;
			copy(command, $("cmdNote"), command.length > 256
				? "Copied, but it is " + command.length + " characters and Minecraft chat takes 256. Shorten the words or use the wrench."
				: "Copied. Paste it in chat in your world, then /ata_station confirm to build.");
		});
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

	// With typed words, the controls the player has not changed give way to the words: show what the plan came to in them
	// (type, tracks, length, platforms, building), without a new plan and without pinning them.
	function followWords(plan) {
		if ((!$("text").value.trim() && $("class").value === "auto" && $("era").value === "auto") || $("listOn").checked) {
			return;
		}
		// what the dice rolled is not the player's choice: typed words move it
		const pins = new Set([...pinnedNow()].filter((f) => !rolled.has(f) || seeds.locks.look && f === "look"));
		const b = plan.built;
		const want = {
			mode: /^terminus/i.test(b.mode) ? "terminus" : /^hub/i.test(b.mode) ? "hub" : "through",
			tracks: String(b.tracks),
			length: String(b.length),
			building: /^no building/i.test(b.building) ? "0" : "1"
		};
		if ($("layout").value !== "AUTO") {
			want.layout = /island/i.test(b.layout) ? "ISLAND" : "SIDE";
		}
		if (plan.wordsLook) {
			want.look = plan.wordsLook;
			want.mix = "";
		}
		let moved = false;
		for (const [f, v] of Object.entries(want)) {
			const range = f === "tracks" ? options.tracks : f === "length" ? options.length : null;
			const inRange = !range || (+v >= range[0] && +v <= range[1]);
			if (!pins.has(f) && $(f).value !== v && inRange) {
				$(f).value = v;
				moved = true;
			}
		}
		if (moved) {
			updateOutputs();
			remember();
		}
	}

	// Typed words beat the settings above: the server leaves out each pinned setting the words set themselves and says which.
	function showClash(plan) {
		const won = (plan && plan.wordsWon) || [];
		$("clash").hidden = won.length === 0;
		$("clashText").textContent = won.length ? "Your words set " + won.join(", ") + ", so they win over those settings above." : "";
	}

	let request = 0;
	// set while the last plan request failed, so the page can ask again when the studio answers
	let planFailed = false;
	function setSendable(on) {
		$("report").classList.toggle("stale", !on);
		for (const b of document.querySelectorAll("#copyCmd, #exportBtn, [data-send=plan], [data-send=build]")) {
			b.disabled = !on;
		}
	}
	async function generate() {
		const mine = ++request;
		$("busy").hidden = false;
		try {
			const r = await fetch("api/plan", { method: "POST", body: new URLSearchParams(values()), headers: { "X-Studio-Token": TOKEN } });
			if (!r.ok) {
				throw new Error(await r.text());
			}
			const plan = await r.json();
			if (mine !== request) {
				return;
			}
			planFailed = false;
			setSendable(true);
			current = plan;
			showPlan(plan);
			report(plan);
			followWords(plan);
			showClash(plan);
		} catch (e) {
			// an older request failing says nothing about the newest one
			if (mine === request) {
				planFailed = true;
				// the station on show is the last one that planned, not what the controls say: say so and keep it from being built
				setSendable(false);
				const why = e instanceof TypeError ? "The studio is not answering." : String(e.message || e).replace(/<[^>]*>/g, "").trim().slice(0, 160);
				$("status").textContent = "Could not plan this: " + why + (e instanceof TypeError ? " It will try again when it is back." : " Change a setting to try again.");
			}
		} finally {
			if (mine === request) {
				$("busy").hidden = true;
			}
		}
	}

	// Download the station as a .schem: the same parameters as the plan, then the file saved under the name the server gave it.
	$("exportBtn").addEventListener("click", async () => {
		const note = $("exportNote");
		note.textContent = "Writing the file…";
		$("exportBtn").disabled = true;
		try {
			const r = await fetch("api/export", { method: "POST", body: new URLSearchParams(values()), headers: { "X-Studio-Token": TOKEN } });
			if (!r.ok) {
				let why = "";
				try {
					why = (await r.json()).error;
				} catch (e) {
					why = "";
				}
				throw new Error(why || "The studio could not write the file (" + r.status + ").");
			}
			const name = /filename="?([^";]+)"?/.exec(r.headers.get("Content-Disposition") || "");
			const blob = await r.blob();
			const a = document.createElement("a");
			a.href = URL.createObjectURL(blob);
			a.download = name ? name[1] : "station.schem";
			document.body.append(a);
			a.click();
			a.remove();
			setTimeout(() => URL.revokeObjectURL(a.href), 4000);
			note.textContent = "Saved " + a.download + " (" + NUMBER.format(+r.headers.get("X-ATA-Blocks") || 0) + " blocks, fingerprint " + (r.headers.get("X-ATA-Fingerprint") || "") + "). Paste with //paste, then /ata_station verify paste.";
		} catch (e) {
			note.textContent = e instanceof TypeError ? "The studio is not answering." : String(e.message || e);
		} finally {
			$("exportBtn").disabled = planFailed;
		}
	});

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
			// nothing to point the camera at: the view buttons would do nothing, so they say why they are off
			for (const b of document.querySelectorAll(".viewbar button, .viewbar input")) {
				b.disabled = true;
				b.title = "Needs the 3D preview";
			}
			return { render() {}, setSize() {}, getPixelRatio: () => 1, clippingPlanes: [] };
		}
	})();
	// the GPU can take the drawing surface away (a driver reset, too many tabs): keep the page usable and draw again when it is back
	canvas.addEventListener("webglcontextlost", (e) => {
		e.preventDefault();
		$("status").textContent = "The 3D view was reset by your graphics driver. It will come back by itself.";
	});
	canvas.addEventListener("webglcontextrestored", () => {
		if (current) {
			$("status").textContent = "Planned in " + current.ms + " ms · " + NUMBER.format(current.size.blocks) + " blocks · WebGL";
		}
		draw();
	});
	const scene = new THREE.Scene();
	const NIGHT = new THREE.Color(0x101317);
	const SKY = new THREE.Color(0xa9bfd3);
	scene.background = NIGHT;
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

	// the ground under the station; walking, it reaches past the fog so the street never ends at a cliff
	function sizeGround() {
		const size = bounds.getSize(new THREE.Vector3());
		const margin = walk ? 1200 : 40;
		ground.scale.set(size.x + margin, size.z + margin, 1);
		ground.position.set((bounds.min.x + bounds.max.x) / 2, bounds.min.y - 0.02, (bounds.min.z + bounds.max.z) / 2);
		ground.material.color.set(walk ? 0x5b6168 : 0x23282e);
	}

	// The Layers buttons: every block role belongs to one layer, which the view can hide.
	const LAYERS = [
		["platforms", "Platforms", ["PLATFORM_SURFACE", "PLATFORM_EDGE", "RAMP_LOWER", "RAMP_UPPER"]],
		["track", "Track", ["RAIL_NODE", "TRACK_BED", "BUFFER_STOP"]],
		["structure", "Structure", ["COLUMN", "COLUMN_CAP", "BEAM", "RIB", "BRIDGE_PIER", "FOUNDATION"]],
		["roof", "Roof", ["ROOF", "ROOF_EDGE", "ROOF_LIGHT"]],
		["building", "Building", ["WALL", "WINDOW", "FLOOR", "LIGHT_PANEL", "BUILDING_ROOF"]],
		["access", "Stairs, lifts", ["STAIR", "ESCALATOR", "LIFT", "BRIDGE_DECK"]],
		["furniture", "Furniture", ["BENCH", "BIN", "LAMP", "GATE", "BARRIER", "RAILING"]],
		["signs", "Signs, screens", ["NAME_SIGN", "FIXTURE"]],
		["ground", "Ground", []]
	];
	const LAYER_OF = {};
	for (const [key, , roles] of LAYERS) {
		for (const role of roles) {
			LAYER_OF[role] = key;
		}
	}
	const hiddenLayers = new Set();

	function setupLayers() {
		const box = $("layers");
		for (const [key, label] of LAYERS) {
			const b = document.createElement("button");
			b.type = "button";
			b.textContent = label;
			b.dataset.layer = key;
			b.setAttribute("aria-pressed", "true");
			b.title = "Show or hide " + label.toLowerCase();
			b.addEventListener("click", () => {
				if (hiddenLayers.has(key)) {
					hiddenLayers.delete(key);
				} else {
					hiddenLayers.add(key);
				}
				showLayers();
			});
			box.append(b);
		}
	}

	function showLayers() {
		const present = new Set(meshes.map((m) => m.userData.layer).concat("ground"));
		for (const b of $("layers").children) {
			b.setAttribute("aria-pressed", String(!hiddenLayers.has(b.dataset.layer)));
			b.disabled = !present.has(b.dataset.layer);
		}
		for (const m of meshes) {
			m.visible = !hiddenLayers.has(m.userData.layer);
		}
		ground.visible = !hiddenLayers.has("ground");
		draw();
	}

	const NUMBER = new Intl.NumberFormat("en");
	function blockName(id) {
		const name = id.replace(/^[a-z_]+:/, "").replace(/_/g, " ");
		return name.charAt(0).toUpperCase() + name.slice(1) + (id.startsWith("minecraft:") ? "" : id.startsWith("mtr:") ? " (MTR)" : "");
	}

	/** The Build card, the status lines and the materials list. */
	function showFacts(plan) {
		const facts = [["Size", plan.size.width + " × " + plan.size.length + " × " + plan.size.height], ["Blocks", NUMBER.format(plan.size.blocks)],
			["Tracks", plan.built.tracks + " · " + plan.built.faces + " faces"], ["Planned", plan.ms + " ms"]];
		$("facts").innerHTML = facts.map(([k, v]) => "<div><dt>" + esc(k) + "</dt><dd>" + esc(v) + "</dd></div>").join("");
		$("banner").innerHTML = "<b>" + esc(plan.title) + "</b>" + esc(plan.built.roof.charAt(0).toUpperCase() + plan.built.roof.slice(1)) + " · "
			+ esc(plan.built.building === "no building" ? "no building" : plan.built.building + (plan.built.scale !== "-" ? ", " + plan.built.scale : ""))
			+ (plan.built.footbridge ? " · footbridge" : "") + (plan.built.name ? " · " + esc(plan.built.name) : "");
		$("status").textContent = "Planned in " + plan.ms + " ms · " + NUMBER.format(plan.size.blocks) + " blocks · " + ($("stage").classList.contains("no-gl") ? "no WebGL" : "WebGL");
		$("topStatus").textContent = (plan.seed ? "seed " + plan.seed + " · " : "") + plan.title;
		$("tracksNote").textContent = $("listOn").checked ? "Set by the platform list" : plan.built.platforms + (plan.built.platforms === 1 ? " platform, " : " platforms, ")
			+ plan.built.faces + (plan.built.faces === 1 ? " boarding face" : " boarding faces");
		const counts = new Map();
		for (let i = 3; i < plan.cells.length; i += 5) {
			const id = plan.models[plan.cells[i]].id;
			counts.set(id, (counts.get(id) || 0) + 1);
		}
		const rows = [...counts].sort((a, b) => b[1] - a[1]);
		const stacks = (n) => n < 64 ? String(n) : Math.floor(n / 64) + " × 64" + (n % 64 ? " + " + (n % 64) : "");
		$("materials").innerHTML = "<tr><th>Block</th><th class='num'>Count</th><th class='num'>Stacks</th></tr>"
			+ rows.map(([id, n]) => "<tr><td title='" + esc(id) + "'>" + esc(blockName(id)) + "</td><td>" + NUMBER.format(n) + "</td><td>" + stacks(n) + "</td></tr>").join("");
		$("matFold").querySelector("summary").textContent = "Block counts and stacks (" + rows.length + " kinds)";
	}

	let meshes = [];
	/** Fare gate paddles by cell, so walk mode can swing each one open and shut as the game's gate does. */
	let paddleAt = new Map();
	function showPlan(plan) {
		for (const m of meshes) {
			station.remove(m);
			m.geometry.dispose();
		}
		meshes = [];
		paddleAt = new Map();
		materials.clear();
		const cells = plan.cells;
		const byModel = new Map();
		bounds = new THREE.Box3();
		for (let i = 0; i < cells.length; i += 5) {
			// one group per model and layer, so a block used in two layers hides with each
			const key = cells[i + 3] + "|" + (LAYER_OF[plan.roles[cells[i + 4]]] || "structure");
			if (!byModel.has(key)) {
				byModel.set(key, []);
			}
			byModel.get(key).push(i);
			bounds.expandByPoint(new THREE.Vector3(cells[i], cells[i + 1], cells[i + 2]));
		}
		bounds.max.addScalar(1);
		const matrix = new THREE.Matrix4();
		for (const [key, list] of byModel) {
			const index = +key.split("|")[0];
			const layer = key.split("|")[1];
			const entry = plan.models[index];
			const role = plan.roles[cells[list[0] + 4]];
			for (const [texPath, geo] of modelGeometry(entry.model)) {
				const mesh = new THREE.InstancedMesh(geo, material(texPath, roleColour(role)), list.length);
				list.forEach((at, n) => {
					matrix.makeTranslation(cells[at], cells[at + 1], cells[at + 2]);
					mesh.setMatrixAt(n, matrix);
				});
				// a fare gate's paddle is its own glass mesh: remember it so walk mode can slide it in and out of the cabinet
				if (entry.id === "aurelia_transit_architecture:fare_gate" && /glass_clear/.test(texPath)) {
					geo.computeBoundingBox();
					const bb = geo.boundingBox;
					const ex = bb.max.x - bb.min.x;
					const ez = bb.max.z - bb.min.z;
					const axis = ex >= ez ? "x" : "z";
					const centre = bb.getCenter(new THREE.Vector3());
					// the cabinet is on the far side of the block centre from the paddle; retract by the paddle's own length
					const dir = centre[axis] < 0.5 ? -1 : 1;
					const move = axis === "x" ? ex : ez;
					list.forEach((at, n) => paddleAt.set(cellKey(cells[at], cells[at + 1], cells[at + 2]), { mesh, n: n, axis, dir, move }));
				}
				mesh.userData = { model: index, cells: list, layer };
				mesh.frustumCulled = false;
				station.add(mesh);
				meshes.push(mesh);
			}
		}
		const size = bounds.getSize(new THREE.Vector3());
		sizeGround();
		showFacts(plan);
		$("raw").textContent = JSON.stringify(Object.assign({}, plan, { cells: "[" + plan.cells.length / 5 + " blocks]", models: "[" + plan.models.length + " models]" }), null, 2);
		if (walk) {
			walk.world = walkWorld(plan);
			sizeGround();
		}
		// frame the first station, and again when a station grows or shrinks a lot (a preset, many more tracks), so it fits
		const reach = Math.max(size.x, size.z);
		if (!walk && (!showPlan.framed || reach > showPlan.framed * 1.5 || reach < showPlan.framed / 1.5)) {
			setView(currentView);
			showPlan.framed = reach;
		}
		// a terminus faces the street with its head building: the Head view replaces the side-on Street view, which would
		// only show the edge of the platforms
		$("headView").hidden = !plan.head;
		document.querySelector('[data-view="street"]').hidden = !!plan.head;
		if (walk) {
			// keep walking
		} else if (!plan.head && currentView === "head") {
			setView("overview");
		} else if (plan.head && currentView === "street") {
			setView("head");
		}
		updateCut();
		showLayers();
		// the compass reads the new plan's street and facing
		draw();
	}

	let currentView = "overview";
	// A face view is named "face:<n>": standing on that boarding face's platform, from the plan's per-face view.
	function setView(name, smooth) {
		const face = /^face:/.test(name) && current ? current.faceLabels.find((f) => "face:" + f.n === name && f.view) : null;
		if (/^face:/.test(name) && !face) {
			name = "overview";
		}
		leaveWalk();
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
			// a floor plan reads with the street at the bottom, like an architect's entrance: turned so
			Object.assign(view, { yaw: streetYaw(), pitch: Math.PI / 2 - 0.001, radius: reach * 1.25 });
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
		if (/^(overview|reverse|street|plan)$/.test(name)) {
			fitView();
		}
		draw();
	}

	// The plan's own frame has no compass north (the station is built to face the way its player looks), but it does say where
	// the street is: the direction, on the ground, from the middle of the station to the street point.
	function streetVector() {
		if (!current || !current.street) {
			return null;
		}
		const c = bounds.getCenter(new THREE.Vector3());
		const x = current.street.x + 0.5 - c.x;
		const z = current.street.z + 0.5 - c.z;
		const len = Math.hypot(x, z);
		return len < 0.5 ? null : { x: x / len, z: z / len };
	}

	// the camera yaw that puts the street at the bottom of the screen (the eye sits on the street side, looking away from it)
	function streetYaw() {
		const d = streetVector();
		// with a chosen facing the station is drawn on the map's frame (north is -z, east +x): north up, as plans are read
		return d && !(current && current.facing && current.facing !== "auto") ? Math.atan2(d.x, d.z) : 0;
	}

	// The compass dial: its arrow points at the street as the camera sees the ground (up on the dial is where the camera looks).
	function updateCompass() {
		const d = streetVector();
		const north = !!current && !!current.facing && current.facing !== "auto";
		const show = (!!d || north) && !walk && !$("stage").classList.contains("no-gl");
		$("compass").hidden = !show;
		if (!show) {
			return;
		}
		$("compassArrow").style.display = d ? "" : "none";
		// forward on the ground is (-sin yaw, -cos yaw), right of it (cos yaw, -sin yaw)
		const sin = Math.sin(view.yaw);
		const cos = Math.cos(view.yaw);
		const ahead = d ? -d.x * sin - d.z * cos : 0;
		const right = d ? d.x * cos - d.z * sin : 0;
		const deg = (Math.atan2(right, ahead) * 180) / Math.PI;
		// north is -z once a facing is chosen (the street really is on that side); the N rides the dial as the camera turns
		$("compassN").style.display = north ? "" : "none";
		if (north) {
			$("compassN").setAttribute("x", (Math.sin(view.yaw) * 26.5).toFixed(1));
			$("compassN").setAttribute("y", (-Math.cos(view.yaw) * 26.5).toFixed(1));
		}
		$("compassFacing").textContent = current.facing && current.facing !== "auto" ? "Faces " + current.facing : "";
		$("compassArrow").setAttribute("transform", "rotate(" + deg.toFixed(1) + ")");
	}

	// Frame the whole station: from the angle already set, back the camera off (or in) and slide it sideways until the eight
	// corners of the station's box fill the view with a margin, whatever the station's size or the view's shape.
	function fitView() {
		if (bounds.isEmpty()) {
			return;
		}
		resize();
		const corners = [];
		for (const x of [bounds.min.x, bounds.max.x]) {
			for (const y of [bounds.min.y, bounds.max.y]) {
				for (const z of [bounds.min.z, bounds.max.z]) {
					corners.push(new THREE.Vector3(x, y, z));
				}
			}
		}
		const tan = Math.tan((45 * Math.PI) / 360);
		for (let i = 0; i < 40; i++) {
			place();
			camera.updateMatrixWorld();
			let x0 = 9, x1 = -9, y0 = 9, y1 = -9, behind = false;
			for (const c of corners) {
				const v = c.clone().project(camera);
				behind = behind || v.z > 1;
				x0 = Math.min(x0, v.x); x1 = Math.max(x1, v.x); y0 = Math.min(y0, v.y); y1 = Math.max(y1, v.y);
			}
			if (behind) {
				view.radius *= 1.6;
				continue;
			}
			const half = view.radius * tan;
			const right = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 0);
			const up = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 1);
			view.target.addScaledVector(right, ((x0 + x1) / 2) * half * camera.aspect).addScaledVector(up, ((y0 + y1) / 2) * half);
			const k = Math.max((x1 - x0) / 2, (y1 - y0) / 2) / 0.9;
			view.radius = Math.max(4, Math.min(3000, view.radius * k));
			if (Math.abs(k - 1) < 0.01 && Math.abs(x0 + x1) < 0.02 && Math.abs(y0 + y1) < 0.02) {
				break;
			}
		}
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
		if (walk) {
			placeWalk();
			return;
		}
		camera.fov = 45;
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
			frame();
		});
	}

	function frame() {
		resize();
		stepGlide();
		place();
		renderer.render(scene, camera);
		labels();
		updateCompass();
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
		$("cutAt").style.setProperty("--fill", $("cutAt").value + "%");
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
		if (walk) {
			// walking: a click takes the mouse; without pointer lock, dragging turns the head
			lockMouse();
			drag = { x: e.clientX, y: e.clientY, walk: true, moved: 0 };
			canvas.setPointerCapture(e.pointerId);
			return;
		}
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
		if (drag.walk) {
			if (walk && document.pointerLockElement !== canvas) {
				look(dx * 1.6, dy * 1.6);
			}
			return;
		}
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
		if (drag && !drag.walk && drag.moved < 4) {
			pick(e);
		}
		drag = null;
	});
	canvas.addEventListener("wheel", (e) => {
		e.preventDefault();
		if (walk) {
			return;
		}
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
		const hit = ray.intersectObjects(meshes.filter((m) => m.visible), false).find((h) => {
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

	// ---- walk audio ----------------------------------------------------------------------------------------------------
	// Spoken announcements (Web Speech) plus short cues (Web Audio): a fare gate beep, a lift chime. Optional, and started
	// on the Walk click because browsers only allow sound after a user gesture.
	const AUDIO = { enabled: true, volume: 0.9 };
	let audioCtx = null;
	let voice = null;

	function audioReady() {
		try {
			if (!audioCtx) {
				const Ctx = window.AudioContext || window.webkitAudioContext;
				if (Ctx) {
					audioCtx = new Ctx();
				}
			}
			if (audioCtx && audioCtx.state === "suspended") {
				audioCtx.resume();
			}
		} catch (e) {
			audioCtx = null;
		}
	}

	function pickVoice() {
		const voices = (window.speechSynthesis && speechSynthesis.getVoices()) || [];
		for (const re of [/en[-_]GB/i, /Daniel/i, /Serena/i, /Google UK English/i, /\bKate\b/i, /^en/i]) {
			const found = voices.find((v) => re.test(v.name) || re.test(v.lang));
			if (found) {
				return found;
			}
		}
		return voices[0] || null;
	}

	/** Speak a line through the browser's own voice (the game uses Piper clips; the Studio has no audio files of its own). */
	function speak(text) {
		if (!AUDIO.enabled || !text || !window.speechSynthesis) {
			return;
		}
		audioReady();
		try {
			speechSynthesis.cancel();
			const line = new SpeechSynthesisUtterance(text);
			if (!voice) {
				voice = pickVoice();
			}
			if (voice) {
				line.voice = voice;
			}
			line.volume = AUDIO.volume;
			line.rate = 0.95;
			speak.last = text;
		} catch (e) {
			// no speech available: stay silent
		}
	}

	/** A short synthesised cue; {@code at} is an offset in seconds. */
	function tone(freq, ms, at, type, gain) {
		if (!AUDIO.enabled || !audioCtx) {
			return;
		}
		const t0 = audioCtx.currentTime + (at || 0);
		const osc = audioCtx.createOscillator();
		const g = audioCtx.createGain();
		osc.type = type || "sine";
		osc.frequency.value = freq;
		g.gain.setValueAtTime(0.0001, t0);
		g.gain.linearRampToValueAtTime((gain || 0.1) * AUDIO.volume, t0 + 0.015);
		g.gain.exponentialRampToValueAtTime(0.0001, t0 + ms / 1000);
		osc.connect(g).connect(audioCtx.destination);
		osc.start(t0);
		osc.stop(t0 + ms / 1000 + 0.03);
	}

	function cueGate() {
		tone(880, 80, 0, "square", 0.07);
		tone(1320, 90, 0.07, "square", 0.07);
	}

	function cueLift() {
		tone(660, 150, 0, "sine", 0.09);
		tone(990, 200, 0.15, "sine", 0.09);
	}

	/** A platform departure announcement, as the Studio cannot read the game's live service data. */
	function announce() {
		speak("Attention please. The next train is approaching. Please stand clear of the platform edge.");
		flash("Announcement");
	}

	if (window.speechSynthesis && speechSynthesis.addEventListener) {
		speechSynthesis.addEventListener("voiceschanged", () => {
			voice = pickVoice();
		});
	}

	// ---- walk -----------------------------------------------------------------------------------------------------------
	// First person through the planned station, as a player would walk it in the game: a 0.6 × 1.8 body that collides with
	// every block's model boxes, steps up half a block (stairs and ramps), falls and jumps, rides escalators and takes the
	// lifts. Fare gates work as in the game: shut and 1.5 high, they open when you walk into the paddles (either way) and
	// close once you are past them. Lift doors let you through, as a lift car would.

	const BODY = { half: 0.3, height: 1.8, eye: 1.62, step: 0.6, gravity: 32, jump: 8.9, walk: 4.3, run: 7, fly: 11, flyRun: 24 };
	/** The walk keys are Minecraft's own: the player's bindings when the studio is opened from the game, the defaults otherwise. */
	const KEYS = { forward: "KeyW", back: "KeyS", left: "KeyA", right: "KeyD", jump: "Space", sneak: "ShiftLeft", sprint: "ControlLeft" };
	const KEY_LABELS = { Space: "Space", ShiftLeft: "Shift", ShiftRight: "Right Shift", ControlLeft: "Ctrl", ControlRight: "Right Ctrl", AltLeft: "Alt",
		AltRight: "Right Alt", ArrowUp: "↑", ArrowDown: "↓", ArrowLeft: "←", ArrowRight: "→", Tab: "Tab", CapsLock: "Caps" };
	const keyLabel = (code) => KEY_LABELS[code] || code.replace(/^Key|^Digit/, "");
	const kbd = (action) => "<kbd>" + keyLabel(KEYS[action]) + "</kbd>";
	function showKeyHelp() {
		const el = document.getElementById("walkKeysHelp");
		if (el) {
			el.innerHTML = kbd("forward") + kbd("left") + kbd("back") + kbd("right") + " walk · " + kbd("sprint") + " or double-tap " + kbd("forward")
				+ " sprint · " + kbd("sneak") + " sneak · " + kbd("jump") + " jump, double-tap to fly (" + kbd("jump") + " up, " + kbd("sneak") + " down)";
		}
	}
	fetch("api/keys").then((r) => r.json()).then((k) => {
		Object.assign(KEYS, k);
		showKeyHelp();
	}).catch(() => {});
	const NO_COLLIDE = new Set(["RAIL_NODE"]);
	/** Roles that stand fence-high in the game (1.5 blocks of collision) although their model is one block tall. */
	const FENCE_ROLES = new Set(["RAILING", "BARRIER"]);
	/** The game's fare gate collision, facing north (FareGateBlock): the cabinet, and the paddle while shut. */
	const GATE_CABINET = [0, 0, 1 / 16, 4 / 16, 1.5, 15 / 16];
	const GATE_PADDLE = [4 / 16, 0, 7 / 16, 1, 1.5, 9 / 16];
	/** An end cabinet's collision: a solid panel across the whole cell (FareGateBlock END_PANEL), so no end is a free gap. */
	const GATE_END = [0, 0, 4 / 16, 1, 1.5, 12 / 16];
	/** As in the game: the centre this close to the paddle line is walking into them, this far has cleared them (the body's
	 * near side past the paddle); a gate left alone shuts after 2 s. Clear is further out than at, or a body between the two
	 * would open and shut the gate every frame. */
	const GATE_AT = 0.45;
	const GATE_CLEAR = 0.6;
	/** Seconds for a paddle to slide fully in or out. */
	const GATE_SLIDE_S = 0.35;
	const GATE_CLOSE_MS = 2000;
	const ESCALATOR_SPEED = 2.2;
	/** Falling further than this takes you back to where you last stood. */
	const FALL = 4.5;
	let walk = null;

	const cellKey = (x, y, z) => ((x + 4096) * 1024 + (y + 512)) * 8192 + (z + 4096);

	/** A model's collision boxes in block units, after its variant's turn: [x0, y0, z0, x1, y1, z1] (element tilts left out). */
	function modelBoxes(model) {
		const out = [];
		for (const part of model.parts) {
			const turn = new THREE.Matrix4().makeTranslation(0.5, 0.5, 0.5)
				.multiply(new THREE.Matrix4().makeRotationY(-part.y * Math.PI / 180))
				.multiply(new THREE.Matrix4().makeRotationX(-part.x * Math.PI / 180))
				.multiply(new THREE.Matrix4().makeTranslation(-0.5, -0.5, -0.5));
			for (const el of part.elements) {
				const box = new THREE.Box3();
				for (const cx of [el.from[0], el.to[0]]) {
					for (const cy of [el.from[1], el.to[1]]) {
						for (const cz of [el.from[2], el.to[2]]) {
							box.expandByPoint(new THREE.Vector3(cx / 16, cy / 16, cz / 16).applyMatrix4(turn));
						}
					}
				}
				// flat decals and hairline trims are not something to bump into
				if (Math.min(box.max.x - box.min.x, box.max.y - box.min.y, box.max.z - box.min.z) < 0.02) {
					continue;
				}
				out.push([box.min.x, box.min.y, box.min.z, box.max.x, box.max.y, box.max.z]);
			}
		}
		return out;
	}

	/** The walkable world of a plan: boxes by cell, escalator steps and their climbing direction, and the lifts. */
	function walkWorld(plan) {
		const shapes = plan.models.map((e) => modelBoxes(e.model));
		const grid = new Map();
		const add = (x, y, z, box) => {
			const k = cellKey(x, y, z);
			if (!grid.has(k)) {
				grid.set(k, []);
			}
			grid.get(k).push(box);
		};
		const cells = plan.cells;
		const steps = new Map();
		const liftStops = new Map();
		const gates = [];
		for (let i = 0; i < cells.length; i += 5) {
			const x = cells[i];
			const y = cells[i + 1];
			const z = cells[i + 2];
			const entry = plan.models[cells[i + 3]];
			const role = plan.roles[cells[i + 4]];
			if (NO_COLLIDE.has(role) || /lift_door/.test(entry.id)) {
				continue;
			}
			if (role === "GATE") {
				const turnY = (((entry.model.parts[0] || { y: 0 }).y % 360) + 360) % 360;
				const gate = { x, y, z, f: [[0, -1], [1, 0], [0, 1], [-1, 0]][Math.round(turnY / 90) % 4], open: false, leftAt: 0, slide: 0,
					paddle: paddleAt.get(cellKey(x, y, z)) || null };
				const turn = new THREE.Matrix4().makeTranslation(0.5, 0, 0.5)
					.multiply(new THREE.Matrix4().makeRotationY(-turnY * Math.PI / 180))
					.multiply(new THREE.Matrix4().makeTranslation(-0.5, 0, -0.5));
				const end = /kind=end/.test(entry.state || "");
				for (const [b, paddle] of end ? [[GATE_END, false]] : [[GATE_CABINET, false], [GATE_PADDLE, true]]) {
					const box = new THREE.Box3().setFromPoints([new THREE.Vector3(b[0], b[1], b[2]), new THREE.Vector3(b[3], b[4], b[5])]
						.map((v) => v.applyMatrix4(turn)));
					add(x, y, z, { a: [x + box.min.x, y + box.min.y, z + box.min.z, x + box.max.x, y + box.max.y, z + box.max.z], esc: 0,
						gate: paddle ? gate : null });
				}
				if (!end) {
					gates.push(gate);
				}
				continue;
			}
			// a railing or a glass barrier stands fence-high in the game though its model is one block tall: no jumping over
			if (FENCE_ROLES.has(role)) {
				add(x, y, z, { a: [x, y, z, x + 1, y + 1.5, z + 1], esc: 0 });
				continue;
			}
			if (/lift_track_floor/.test(entry.id)) {
				const k = x + "," + z;
				if (!liftStops.has(k)) {
					// the track's plate faces the glass it is fixed to; the car reaches away from it
					const turn = (((entry.model.parts[0] || { y: 0 }).y % 360) + 360) % 360;
					liftStops.set(k, { x, z, stops: [], f: [[0, -1], [1, 0], [0, 1], [-1, 0]][Math.round(turn / 90) % 4] });
				}
				liftStops.get(k).stops.push(y);
			}
			const step = /escalator_step/.test(entry.id);
			if (step) {
				const turn = (((entry.model.parts[0] || { y: 0 }).y % 360) + 360) % 360;
				steps.set(cellKey(x, y, z), { x, y, z, f: [[0, -1], [1, 0], [0, 1], [-1, 0]][Math.round(turn / 90) % 4], dir: null });
			}
			for (const b of shapes[cells[i + 3]]) {
				add(x, y, z, { a: [x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5]], esc: step ? cellKey(x, y, z) : 0 });
			}
		}
		// Which way each step moves comes from the plan (MTR's step model looks the same both ways along its axis)
		const moving = plan.moving || [];
		for (let i = 0; i < moving.length; i += 5) {
			const s = steps.get(cellKey(moving[i], moving[i + 1], moving[i + 2]));
			if (s) {
				s.dir = [moving[i + 3], moving[i + 4]];
			}
		}
		for (const s of steps.values()) {
			if (!s.dir) {
				s.dir = s.f;
			}
		}
		// Each lift car is the track's own cell and the open cells of its shaft beside it, away from the glass the track is
		// fixed to. The game's car carries you; here every stop gets a car floor, so the shaft never swallows you.
		const occupied = (x, y, z) => grid.has(cellKey(x, y, z));
		const lifts = [];
		for (const lift of liftStops.values()) {
			lift.stops.sort((a, b) => a - b);
			const at = lift.stops[0];
			lift.car = [];
			const [fx, fz] = lift.f;
			for (let away = 0; away <= 1; away++) {
				for (let side = -1; side <= 1; side++) {
					const cx = lift.x - fx * away - fz * side;
					const cz = lift.z - fz * away + fx * side;
					if ((away === 0 && side === 0) || (!occupied(cx, at, cz) && !occupied(cx, at + 1, cz))) {
						lift.car.push([cx, cz]);
					}
				}
			}
			for (const y of lift.stops) {
				for (const [cx, cz] of lift.car) {
					add(cx, y - 1, cz, { a: [cx, y - 0.0625, cz, cx + 1, y, cz + 1], esc: 0 });
				}
			}
			lifts.push(lift);
		}
		return { grid, steps, lifts, gates, ground: bounds.min.y };
	}

	/** Opens a shut gate you walk into and shuts it once you are clear of the paddles, as the game does. */
	function updateGates(world) {
		const p = walk.pos;
		const box = bodyBox(p);
		const now = performance.now();
		for (const g of world.gates) {
			if (Math.abs(g.x + 0.5 - p.x) > 2 || Math.abs(g.z + 0.5 - p.z) > 2 || Math.abs(g.y - p.y) > 2) {
				if (g.open && now - g.leftAt > GATE_CLOSE_MS) {
					g.open = false;
				}
				continue;
			}
			const inCell = box[0] < g.x + 1 && box[3] > g.x && box[2] < g.z + 1 && box[5] > g.z && box[1] < g.y + 1.5 && box[4] > g.y;
			const off = Math.abs((p.x - g.x - 0.5) * g.f[0] + (p.z - g.z - 0.5) * g.f[1]);
			if (!inCell) {
				if (g.open && now - g.leftAt > GATE_CLOSE_MS) {
					g.open = false;
				}
				continue;
			}
			g.leftAt = now;
			if (!g.open && off < GATE_AT) {
				g.open = true;
			} else if (g.open && off > GATE_CLEAR) {
				g.open = false;
			}
		}
	}

	function bodyBox(p) {
		return [p.x - BODY.half, p.y, p.z - BODY.half, p.x + BODY.half, p.y + BODY.height, p.z + BODY.half];
	}

	// ---- fare gate paddle animation ----
	// The gate the game draws is a body plus a glass paddle that slides into the cabinet when the gate opens (its open
	// model drops the paddle). Here the paddle is a mesh of its own, so walking into a gate slides it in and it slides
	// back out once you are clear, matching the collision the game already applies.
	const _gateMat = new THREE.Matrix4();
	function gateInstanceMatrix(gate) {
		const p = gate.paddle;
		// eased in and out, so the paddle starts and stops gently instead of snapping
		const s = gate.slide * gate.slide * (3 - 2 * gate.slide);
		const dx = p.axis === "x" ? p.dir * p.move * s : 0;
		const dz = p.axis === "z" ? p.dir * p.move * s : 0;
		return _gateMat.makeTranslation(gate.x + dx, gate.y, gate.z + dz);
	}

	function animateGates(dt) {
		if (!walk || !walk.world) {
			return;
		}
		// nothing to do between a gate opening and shutting: skip the per-frame matrix work entirely
		let animating = false;
		for (const g of walk.world.gates) {
			if (g.paddle && g.slide !== (g.open ? 1 : 0)) {
				animating = true;
				break;
			}
		}
		if (!animating) {
			return;
		}
		const dirty = new Set();
		for (const g of walk.world.gates) {
			if (!g.paddle) {
				continue;
			}
			// a steady slide (eased in gateInstanceMatrix), reversing smoothly from wherever it is if the gate changes its mind
			const target = g.open ? 1 : 0;
			const stepBy = dt / GATE_SLIDE_S;
			g.slide = target > g.slide ? Math.min(target, g.slide + stepBy) : Math.max(target, g.slide - stepBy);
			g.paddle.mesh.setMatrixAt(g.paddle.n, gateInstanceMatrix(g));
			dirty.add(g.paddle.mesh);
		}
		for (const m of dirty) {
			m.instanceMatrix.needsUpdate = true;
		}
	}

	/** Every box in the cells around a region (models may reach a little past their own cell), and the ground. */
	function boxesNear(world, r) {
		const out = [{ a: [-1e6, world.ground - 4, -1e6, 1e6, world.ground, 1e6], esc: 0 }];
		for (let x = Math.floor(r[0]) - 1; x <= Math.floor(r[3]) + 1; x++) {
			for (let y = Math.floor(r[1]) - 1; y <= Math.floor(r[4]) + 1; y++) {
				for (let z = Math.floor(r[2]) - 1; z <= Math.floor(r[5]) + 1; z++) {
					const list = world.grid.get(cellKey(x, y, z));
					if (list) {
						for (const b of list) {
							if (!(b.gate && b.gate.open)) {
								out.push(b);
							}
						}
					}
				}
			}
		}
		return out;
	}

	const EPS = 1e-7;
	/** How far the box can move along one axis (0 x, 1 y, 2 z) before it hits one of the boxes. */
	function clip(box, boxes, axis, d) {
		const o1 = (axis + 1) % 3;
		const o2 = (axis + 2) % 3;
		for (const { a } of boxes) {
			if (a[o1] >= box[o1 + 3] - EPS || a[o1 + 3] <= box[o1] + EPS || a[o2] >= box[o2 + 3] - EPS || a[o2 + 3] <= box[o2] + EPS) {
				continue;
			}
			if (d > 0 && a[axis] >= box[axis + 3] - EPS) {
				d = Math.min(d, a[axis] - box[axis + 3]);
			} else if (d < 0 && a[axis + 3] <= box[axis] + EPS) {
				d = Math.max(d, a[axis + 3] - box[axis]);
			}
		}
		return d;
	}

	function shift(box, axis, d) {
		box[axis] += d;
		box[axis + 3] += d;
	}

	/** Moves y, then x and z, as the game does; returns what actually moved. */
	function slide(box, boxes, dx, dy, dz) {
		const my = clip(box, boxes, 1, dy);
		shift(box, 1, my);
		const mx = clip(box, boxes, 0, dx);
		shift(box, 0, mx);
		const mz = clip(box, boxes, 2, dz);
		shift(box, 2, mz);
		return [mx, my, mz];
	}

	function moveBody(world, dx, dy, dz) {
		const p = walk.pos;
		const box = bodyBox(p);
		const reach = [Math.min(box[0], box[0] + dx), Math.min(box[1], box[1] + dy) - 1.2, Math.min(box[2], box[2] + dz),
			Math.max(box[3], box[3] + dx), Math.max(box[4], box[4] + dy) + 1.2, Math.max(box[5], box[5] + dz)];
		const boxes = boxesNear(world, reach);
		if (walk.fly && walk.ghost) {
			p.x += dx;
			p.y += dy;
			p.z += dz;
			return;
		}
		const start = box.slice();
		const moved = slide(box, boxes, dx, dy, dz);
		const landed = dy < 0 && moved[1] > dy + EPS;
		const blocked = Math.abs(moved[0] - dx) > EPS || Math.abs(moved[2] - dz) > EPS;
		// step up onto a stair, a kerb or a ramp; an escalator step is a whole block, so next to one the step is a block
		if (blocked && (walk.onGround || landed) && !walk.fly) {
			// the next escalator step: its top a block above the feet, right where the body is heading
			const ahead = [start[0] + Math.min(0, dx) - 0.05, start[2] + Math.min(0, dz) - 0.05, start[3] + Math.max(0, dx) + 0.05, start[5] + Math.max(0, dz) + 0.05];
			const tall = boxes.some((b) => b.esc && b.a[4] > start[1] + BODY.step && b.a[4] <= start[1] + 1.05
				&& b.a[0] < ahead[2] && b.a[3] > ahead[0] && b.a[2] < ahead[3] && b.a[5] > ahead[1]) ? 1.05 : BODY.step;
			const up = start.slice();
			const rise = clip(up, boxes, 1, tall);
			shift(up, 1, rise);
			const mx = clip(up, boxes, 0, dx);
			shift(up, 0, mx);
			const mz = clip(up, boxes, 2, dz);
			shift(up, 2, mz);
			const drop = clip(up, boxes, 1, -rise);
			shift(up, 1, drop);
			if (mx * mx + mz * mz > moved[0] * moved[0] + moved[2] * moved[2] + EPS) {
				const climbed = up[1] - box[1];
				box.splice(0, 6, ...up);
				if (climbed > 0.2) {
					walk.lag -= climbed;
				}
			}
		}
		walk.onGround = dy < 0 && box[1] - start[1] > dy + EPS;
		if (walk.onGround || (dy > 0 && box[1] - start[1] < dy - EPS)) {
			walk.vel.y = 0;
		}
		p.x = (box[0] + box[3]) / 2;
		p.y = box[1];
		p.z = (box[2] + box[5]) / 2;
	}

	/** The escalator step under the feet, if any. */
	function stepUnderfoot(world) {
		const box = bodyBox(walk.pos);
		for (const b of boxesNear(world, [box[0], box[1] - 0.1, box[2], box[3], box[1], box[5]])) {
			if (b.esc && Math.abs(b.a[4] - box[1]) < 0.06 && b.a[0] < box[3] && b.a[3] > box[0] && b.a[2] < box[5] && b.a[5] > box[2]) {
				return world.steps.get(b.esc);
			}
		}
		return null;
	}

	/** The lift whose car the walker stands in, and the stop it is at. */
	function liftHere(world) {
		const cx = Math.floor(walk.pos.x);
		const cz = Math.floor(walk.pos.z);
		for (const lift of world.lifts) {
			if (!lift.car.some(([x, z]) => x === cx && z === cz)) {
				continue;
			}
			const at = lift.stops.findIndex((y) => Math.abs(walk.pos.y - y) < 0.7);
			if (at >= 0) {
				return { lift, at };
			}
		}
		return null;
	}

	function floorName(lift, i) {
		return lift.stops.length === 2 ? (i === 0 ? "the lower floor" : "the upper floor") : i === 0 ? "the ground floor" : "floor " + i;
	}

	const keys = new Set();
	/** A walk key is held: either side of a key pair counts (Left and Right Shift). */
	const down = (action) => keys.has(KEYS[action]) || keys.has(KEYS[action].replace("Left", "Right"));
	let lastJumpTap = 0;
	let lastForwardTap = 0;
	let sprintLatch = false;

	function stepWalk(dt) {
		const world = walk.world;
		if (walk.ride) {
			const r = walk.ride;
			r.t = Math.min(1, r.t + dt / r.ms * 1000);
			const k = r.t < 0.5 ? 2 * r.t * r.t : 1 - Math.pow(-2 * r.t + 2, 2) / 2;
			walk.pos.y = r.from + (r.to - r.from) * k;
			if (r.t >= 1) {
				walk.pos.y = r.to;
				walk.ride = null;
				walk.vel.set(0, 0, 0);
			}
			walk.lag *= Math.exp(-dt * 12);
			return;
		}
		const forward = new THREE.Vector3(-Math.sin(walk.yaw), 0, -Math.cos(walk.yaw));
		const right = new THREE.Vector3(Math.cos(walk.yaw), 0, -Math.sin(walk.yaw));
		const wish = new THREE.Vector3();
		if (down("forward") || keys.has("ArrowUp")) wish.add(forward);
		if (down("back") || keys.has("ArrowDown")) wish.sub(forward);
		if (down("right") || keys.has("ArrowRight")) wish.add(right);
		if (down("left") || keys.has("ArrowLeft")) wish.sub(right);
		if (wish.lengthSq() > 0) {
			wish.normalize();
		}
		const running = down("sprint") || sprintLatch;
		if (walk.fly) {
			const speed = running ? BODY.flyRun : BODY.fly;
			const up = (down("jump") ? 1 : 0) - (down("sneak") ? 1 : 0);
			const target = wish.multiplyScalar(speed).add(new THREE.Vector3(0, up * speed * 0.7, 0));
			walk.vel.lerp(target, 1 - Math.exp(-dt * 8));
		} else {
			const speed = running ? BODY.run : down("sneak") ? BODY.walk * 0.35 : BODY.walk;
			const grip = walk.onGround ? 14 : 2.5;
			const k = 1 - Math.exp(-dt * grip);
			walk.vel.x += (wish.x * speed - walk.vel.x) * k;
			walk.vel.z += (wish.z * speed - walk.vel.z) * k;
			walk.vel.y = Math.max(-60, walk.vel.y - BODY.gravity * dt);
			if (walk.onGround && down("jump")) {
				walk.vel.y = BODY.jump;
				walk.onGround = false;
			}
		}
		let carry = [0, 0];
		const step = walk.onGround && !walk.fly ? stepUnderfoot(world) : null;
		if (step) {
			carry = [step.dir[0] * ESCALATOR_SPEED, step.dir[1] * ESCALATOR_SPEED];
		}
		updateGates(world);
		// small steps, so a fast fall or a run never passes through a slab
		const n = Math.max(1, Math.ceil(Math.max(Math.abs(walk.vel.x), Math.abs(walk.vel.y), Math.abs(walk.vel.z)) * dt / 0.25));
		for (let i = 0; i < n; i++) {
			moveBody(world, (walk.vel.x + carry[0]) * dt / n, walk.vel.y * dt / n, (walk.vel.z + carry[1]) * dt / n);
		}
		walk.lag *= Math.exp(-dt * 12);
		// a fall of more than a few blocks (off a footbridge, a roof or the edge of the world) puts you back where you last stood
		if (walk.onGround && !step) {
			walk.safe.copy(walk.pos);
			walk.safeYaw = walk.yaw;
		}
		if (!walk.fly && (walk.pos.y < walk.safe.y - FALL || walk.pos.y < world.ground - 1)) {
			walk.pos.copy(walk.safe);
			walk.vel.set(0, 0, 0);
			walk.lag = 0;
			flash("Back where you were");
		}
		showTip(world);
	}

	/** A short message in the tip line for a moment. */
	function flash(text) {
		walk.note = text;
		walk.noteUntil = performance.now() + 1800;
	}

	function showTip(world) {
		const here = walk.fly ? null : liftHere(world);
		let tip = "";
		if (performance.now() < walk.noteUntil) {
			tip = walk.note;
		} else if (walk.ride) {
			tip = "Lift going to " + floorName(walk.ride.lift, walk.ride.stop);
		} else if (here) {
			const next = here.at + 1 < here.lift.stops.length ? here.at + 1 : here.at - 1;
			tip = "In the lift · E to go to " + floorName(here.lift, next);
		} else if (walk.fly) {
			tip = walk.ghost ? "Flying through walls · G to stop" : "Flying · double-tap " + keyLabel(KEYS.jump) + " to land · G to fly through walls";
		}
		if ($("walkTip").textContent !== tip) {
			$("walkTip").textContent = tip;
		}
	}

	function takeLift() {
		const here = liftHere(walk.world);
		if (!here || walk.ride) {
			return;
		}
		const next = here.at + 1 < here.lift.stops.length ? here.at + 1 : here.at - 1;
		if (next < 0) {
			return;
		}
		const from = here.lift.stops[here.at];
		const to = here.lift.stops[next];
		walk.ride = { lift: here.lift, stop: next, from: walk.pos.y, to, t: 0, ms: 1600 + Math.abs(to - from) * 250 };
	}

	/** Stand on the floor at a start spot ({x, z, tx, tz}), looking the way it says; the feet land on whatever is below. */
	function startAt(spot) {
		walk.start = spot;
		const world = walk.world;
		walk.pos.set(spot.x + 0.5, Math.max(bounds.max.y + 2, 4), spot.z + 0.5);
		// drop to the floor: the highest surface below eye height of a passenger standing on the platform (y = 1)
		const top = spot.y !== undefined ? spot.y : 3;
		walk.pos.y = top;
		for (let guard = 0; guard < 12 && boxesNear(world, bodyBox(walk.pos)).some(({ a }) => overlaps(a, bodyBox(walk.pos))); guard++) {
			walk.pos.y += 1;
		}
		walk.vel.set(0, 0, 0);
		walk.onGround = false;
		walk.ride = null;
		walk.lag = 0;
		walk.safe.copy(walk.pos);
		walk.yaw = Math.atan2(-(spot.tx - spot.x), -(spot.tz - spot.z));
		walk.pitch = 0;
	}

	function overlaps(a, b) {
		return a[0] < b[3] - EPS && a[3] > b[0] + EPS && a[1] < b[4] - EPS && a[4] > b[1] + EPS && a[2] < b[5] - EPS && a[5] > b[2] + EPS;
	}

	function spotFor(which) {
		if (!current) {
			return null;
		}
		if (which === "street" && current.street) {
			return Object.assign({ y: 0.5 }, current.street);
		}
		if (current.passenger) {
			return Object.assign({ y: 1 }, current.passenger);
		}
		const c = bounds.getCenter(new THREE.Vector3());
		return { x: Math.floor(c.x), z: Math.floor(bounds.max.z) + 6, tx: Math.floor(c.x), tz: Math.floor(c.z), y: 0 };
	}

	function enterWalk(which) {
		if (!current || $("stage").classList.contains("no-gl")) {
			return;
		}
		glide = null;
		currentView = "walk";
		for (const b of document.querySelectorAll("[data-view]")) {
			b.setAttribute("aria-pressed", "false");
		}
		walk = { pos: new THREE.Vector3(), vel: new THREE.Vector3(), yaw: 0, pitch: 0, onGround: false, fly: false, ghost: false, lag: 0,
			ride: null, world: walkWorld(current), last: performance.now(), start: null, safe: new THREE.Vector3(), note: "", noteUntil: 0 };
		startAt(spotFor(which || (current.street ? "street" : "platform")));
		$("walkBtn").setAttribute("aria-pressed", "true");
		$("walkHud").hidden = false;
		$("stage").classList.add("walking");
		$("pick").hidden = true;
		scene.background = SKY;
		scene.fog = new THREE.Fog(SKY, 80, 420);
		sizeGround();
		lockMouse();
		requestAnimationFrame(walkLoop);
	}

	function leaveWalk() {
		if (!walk) {
			return;
		}
		walk = null;
		keys.clear();
		if (document.pointerLockElement === canvas) {
			document.exitPointerLock();
		}
		$("walkBtn").setAttribute("aria-pressed", "false");
		$("walkHud").hidden = true;
		$("stage").classList.remove("walking");
		scene.background = NIGHT;
		scene.fog = null;
		sizeGround();
	}

	function lockMouse() {
		if (canvas.requestPointerLock) {
			try {
				const p = canvas.requestPointerLock();
				if (p && p.catch) {
					p.catch(() => {});
				}
			} catch (e) {
				// no pointer lock (an embedded view): dragging still turns the view
			}
		}
	}

	function walkLoop(now) {
		if (!walk) {
			return;
		}
		// a slow frame still moves at walking speed (the move is split into small steps), up to a tenth of a second
		const dt = Math.min(0.1, Math.max(0, (now - walk.last) / 1000));
		walk.last = now;
		stepWalk(dt);
		animateGates(dt);
		frame();
		requestAnimationFrame(walkLoop);
	}

	function placeWalk() {
		camera.position.set(walk.pos.x, walk.pos.y + BODY.eye + walk.lag, walk.pos.z);
		camera.rotation.set(walk.pitch, walk.yaw, 0, "YXZ");
		camera.fov = 70;
		camera.near = 0.05;
		camera.updateProjectionMatrix();
	}

	function look(dx, dy) {
		walk.yaw -= dx * 0.0025 * (invertX ? -1 : 1);
		walk.pitch = Math.max(-1.55, Math.min(1.55, walk.pitch - dy * 0.0025 * (invertY ? -1 : 1)));
	}

	document.addEventListener("pointerlockchange", () => {
		$("walkHud").classList.toggle("locked", document.pointerLockElement === canvas);
		if (document.pointerLockElement !== canvas) {
			keys.clear();
		}
	});
	document.addEventListener("mousemove", (e) => {
		if (walk && document.pointerLockElement === canvas) {
			look(e.movementX, e.movementY);
		}
	});
	const typing = (e) => /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName) || e.target.isContentEditable;
	window.addEventListener("keydown", (e) => {
		if (!walk || typing(e)) {
			return;
		}
		if (e.code === "Escape" && document.pointerLockElement !== canvas) {
			leaveWalk();
			return;
		}
		keys.add(e.code);
		// as in the game: a double tap of jump starts and stops flying, a double tap of forward sprints
		const now = performance.now();
		if (e.code === KEYS.jump && !e.repeat) {
			if (now - lastJumpTap < 300) {
				walk.fly = !walk.fly;
				walk.ghost = walk.ghost && walk.fly;
				walk.vel.set(0, 0, 0);
				lastJumpTap = 0;
			} else {
				lastJumpTap = now;
			}
		}
		if (e.code === KEYS.forward && !e.repeat) {
			sprintLatch = sprintLatch || now - lastForwardTap < 300;
			lastForwardTap = now;
		}
		if (e.code === "KeyG" && walk.fly) {
			walk.ghost = !walk.ghost;
		} else if (e.code === "KeyE") {
			takeLift();
		} else if (e.code === "Digit1") {
			startAt(spotFor("platform"));
		} else if (e.code === "Digit2") {
			startAt(spotFor("street"));
		}
		if (Object.values(KEYS).includes(e.code) || /^(Space|Arrow|Key[EG]$|Digit[12]$)/.test(e.code)) {
			e.preventDefault();
		}
	});
	window.addEventListener("keyup", (e) => {
		keys.delete(e.code);
		if (e.code === KEYS.forward) {
			sprintLatch = false;
		}
	});
	window.addEventListener("blur", () => {
		keys.clear();
		sprintLatch = false;
	});
	$("walkBtn").addEventListener("click", () => (walk ? leaveWalk() : enterWalk()));
	$("compass").addEventListener("click", () => setView("plan", true));
	$("walkClick").addEventListener("click", lockMouse);
	// read-only camera state, for automated framing checks: where the station's bounding box lands on screen (-1..1 both ways)
	window.studioView = {
		extent: () => {
			place();
			camera.updateMatrixWorld();
			let x0 = 9, x1 = -9, y0 = 9, y1 = -9, behind = false;
			for (const x of [bounds.min.x, bounds.max.x]) {
				for (const y of [bounds.min.y, bounds.max.y]) {
					for (const z of [bounds.min.z, bounds.max.z]) {
						const v = new THREE.Vector3(x, y, z).project(camera);
						behind = behind || v.z > 1;
						x0 = Math.min(x0, v.x); x1 = Math.max(x1, v.x); y0 = Math.min(y0, v.y); y1 = Math.max(y1, v.y);
					}
				}
			}
			return { x0, x1, y0, y1, behind, view: currentView, aspect: camera.aspect };
		},
		// what is on show: the plan the page is drawing, and what the renderer holds (for leak checks)
		plan: () => current && { text: current.text, seed: current.seed, blocks: current.size.blocks, fingerprint: current.fingerprint },
		stats: () => ({ meshes: meshes.length, textures: textures.size, geometries: renderer.info ? renderer.info.memory.geometries : 0, gpuTextures: renderer.info ? renderer.info.memory.textures : 0, programs: renderer.info && renderer.info.programs ? renderer.info.programs.length : 0 }),
		// n renders in a row from a turning camera, each one finished on the GPU: the milliseconds each took
		bench: (n) => {
			const gl = renderer.getContext && renderer.getContext();
			const px = new Uint8Array(4);
			const ms = [];
			for (let i = 0; i < n; i++) {
				view.yaw += 0.05;
				const t0 = performance.now();
				place();
				renderer.render(scene, camera);
				if (gl) {
					gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
				}
				ms.push(performance.now() - t0);
			}
			draw();
			return ms;
		},
		name: () => currentView,
		yaw: () => view.yaw,
		// the compass arrow's angle (degrees clockwise from up) and whether it shows
		compass: () => ({ shown: !$("compass").hidden, deg: $("compassArrow").getAttribute("transform") })
	};
	// read-only state and a teleport, for automated walk checks
	window.studioWalk = {
		state: () => walk && { x: walk.pos.x, y: walk.pos.y, z: walk.pos.z, yaw: walk.yaw, onGround: walk.onGround, fly: walk.fly,
			riding: !!walk.ride, tip: $("walkTip").textContent, feet: [...walk.world.steps.values()].filter((t) => !walk.world.steps.has(cellKey(t.x - t.dir[0], t.y - 1, t.z - t.dir[1]))).map((t) => ({ x: t.x, y: t.y, z: t.z, dir: t.dir })), lifts: walk.world.lifts.map((l) => ({ x: l.x, z: l.z, stops: l.stops, car: l.car })) },
		face: (yaw, pitch) => {
			walk.yaw = yaw;
			walk.pitch = pitch || 0;
		},
		boxes: (r) => boxesNear(walk.world, r).filter(({ a }) => overlaps(a, r)).map((b) => b.a.map((v) => +v.toFixed(3)).concat(b.esc ? ["esc"] : [])),
		// hold these keys for this many seconds of game time, simulated at 60 steps a second
		run: (held, seconds) => {
			keys.clear();
			held.forEach((k) => keys.add(k));
			for (let t = 0; t < seconds; t += 1 / 60) {
				stepWalk(1 / 60);
			}
			keys.clear();
		},
		key: (code) => window.dispatchEvent(new KeyboardEvent("keydown", { code })),
		go: (x, y, z) => {
			walk.pos.set(x, y, z);
			walk.safe.set(x, y, z);
			walk.vel.set(0, 0, 0);
			walk.noteUntil = 0;
		}
	};


	// ---- report ---------------------------------------------------------------------------------------------------------

	function esc(text) {
		return String(text).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
	}

	function report(plan) {
		const r = plan.requested;
		const b = plan.built;
		// with platforms one by one the list sets the layout and the tracks (up to 20, past the slider's 10), and a listed
		// platform longer than the slider makes the station that long
		const listed = $("listOn") && $("listOn").checked;
		// the third value says whether the request was met (true / false), or null where there is nothing to compare
		const rows = [
			["Station mode", r.mode, b.mode, r.mode === b.mode],
			["Layout", r.layout, b.layout + " (" + b.platforms + ")", listed ? null : r.layout === "Auto" || b.layout.startsWith(r.layout.toLowerCase())],
			["Tracks", r.tracks, b.tracks + " tracks, " + b.faces + " boarding faces", listed ? null : r.tracks === b.tracks],
			["Length", r.length + " blocks", b.length + " blocks on every face", r.length === b.length || (listed && b.length > r.length ? null : false)],
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
			"<div class='read-group'><h3>Recognised</h3>" + chips(plan.understood) + "</div>" +
			"<div class='read-group'><h3>Conflicts and limits</h3>" + chips(plan.warnings, "warn") + "</div>" +
			"<div class='read-group'><h3>Not understood</h3>" + chips(plan.ignored, "muted") + "</div>" +
			"<div class='read-group'><h3>Filled in</h3>" + chips(plan.defaulted, "muted") + "</div>" +
			"<p class='note'>The planner read: “" + esc(plan.text) + "”</p>";

		// problems first, then the passed checks, as one list of ticks
		const problems = plan.violations.map((v) => ({ status: "FAIL", message: v.message + " (x " + v.x + ", y " + v.y + ", z " + v.z + ")" }))
			.concat(plan.checks.filter((c) => c.status !== "PASS"));
		const passed = plan.checks.filter((c) => c.status === "PASS");
		const fails = problems.filter((c) => c.status === "FAIL").length;
		const warns = problems.length - fails;
		// the plan's own tally when it gives one (the same numbers, counted over the checks)
		const tally = plan.summary ? plan.summary.pass + " passed, " + plan.summary.warn + " to look at, " + plan.summary.fail + " failed" : "";
		const plural = (n, word) => n + " " + word + (n === 1 ? "" : "s");
		const verdict = !plan.buildable
			? ["bad", "!", "Can't build this yet", plural(fails, "problem") + " to fix first"]
			: warns ? ["warned", "!", "Ready to build", plural(warns, "thing") + " worth a look" + (tally ? " · " + tally : "")]
				: ["good", "✓", "Ready to build", "All " + plural(passed.length, "check") + " passed"];
		const all = problems.concat(passed);
		const shown = report.open ? all : all.slice(0, Math.max(problems.length, 8));
		$("checks").innerHTML = "<div class='verdict " + verdict[0] + "'><span class='icon' aria-hidden='true'>" + verdict[1] + "</span><div><h3>"
			+ verdict[2] + "</h3><p>" + verdict[3] + "</p></div></div>"
			+ "<ul class='checks'>" + shown.map((c) => "<li class='" + c.status + "'><span class='sr'>" + (c.status === "PASS" ? "Passed: " : c.status === "FAIL" ? "Failed: " : "Warning: ")
				+ "</span><div>" + (c.title ? "<b class='ttl'>" + esc(c.title) + "</b> " : "") + esc(c.message)
				+ (c.fix ? "<span class='fix'>Next: " + esc(c.fix) + "</span>" : "") + "</div></li>").join("") + "</ul>"
			+ (all.length > shown.length ? "<button type='button' class='more' id='moreChecks'>Show all " + all.length + " checks</button>"
				: report.open && all.length > 8 ? "<button type='button' class='more' id='moreChecks'>Show fewer</button>" : "");
		const more = $("moreChecks");
		if (more) {
			more.addEventListener("click", () => {
				report.open = !report.open;
				report(plan);
				$("moreChecks") && $("moreChecks").focus();
			});
		}
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
			if (planFailed) {
				generate();
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
