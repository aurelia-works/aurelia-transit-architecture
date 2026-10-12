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

	const fields = ["text", "mode", "layout", "tracks", "length", "look", "mix", "roof", "building", "name"];

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
		$("tracksOut").textContent = $("tracks").value + ($("tracks").value === "1" ? " track" : " tracks");
		$("lengthOut").textContent = $("length").value + " blocks";
		$("count").textContent = $("text").value.length + " / " + options.maxTyped;
	}

	let timer = 0;
	function changed() {
		updateOutputs();
		remember();
		clearTimeout(timer);
		timer = setTimeout(generate, 350);
	}

	async function init() {
		options = await (await fetch("api/options")).json();
		fillSelect($("layout"), options.layouts);
		fillSelect($("look"), options.looks);
		fillSelect($("mix"), options.looks.filter(([v]) => v !== "AUTO"), ["", "Nothing"]);
		fillSelect($("roof"), options.roofs);
		$("tracks").min = options.tracks[0];
		$("tracks").max = options.tracks[1];
		$("tracks").value = 2;
		$("length").min = options.length[0];
		$("length").max = options.length[1];
		$("length").value = options.length[2];
		$("text").maxLength = options.maxTyped;
		$("name").maxLength = options.maxName;
		for (const p of options.presets) {
			$("preset").append(new Option(p.key.replace(/_/g, " "), p.description));
		}
		restore();
		updateOutputs();
		$("where").innerHTML = IN_GAME
			? "Connected to your game. <b>Plan in front of me</b> sends this design to the station wrench."
			: "Running outside the game: design and check here.";
		$("game").hidden = !IN_GAME;
		$("offline").hidden = IN_GAME;
		for (const f of fields) {
			$(f).addEventListener("input", changed);
		}
		$("preset").addEventListener("change", () => {
			if ($("preset").value) {
				$("text").value = $("preset").value;
				$("look").value = "AUTO";
				$("mix").value = "";
				$("roof").value = "AUTO";
				$("layout").value = "AUTO";
				$("preset").value = "";
				changed();
			}
		});
		$("generate").addEventListener("click", generate);
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
	const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
	renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
	renderer.outputEncoding = THREE.sRGBEncoding;
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
		$("banner").textContent = plan.title + " · " + plan.size.blocks.toLocaleString() + " blocks · " + plan.size.width + " × "
			+ plan.size.length + " × " + plan.size.height + " · planned in " + plan.ms + " ms";
		$("raw").textContent = JSON.stringify(Object.assign({}, plan, { cells: "[" + plan.cells.length / 5 + " blocks]", models: "[" + plan.models.length + " models]" }), null, 2);
		if (!showPlan.framed) {
			setView(currentView);
			showPlan.framed = true;
		}
		updateCut();
		draw();
	}

	let currentView = "overview";
	function setView(name) {
		currentView = name;
		for (const b of document.querySelectorAll("[data-view]")) {
			b.setAttribute("aria-pressed", String(b.dataset.view === name));
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
			const p = current.passenger;
			const eye = new THREE.Vector3(p.x + 0.5, 2.6, p.z + 0.5);
			const ahead = new THREE.Vector3(p.tx + 0.5, 2.4, p.tz + 0.5);
			const dir = ahead.clone().sub(eye).normalize();
			view.target.copy(eye).addScaledVector(dir, 12);
			const off = eye.clone().sub(view.target);
			view.radius = off.length();
			view.yaw = Math.atan2(off.x, off.z);
			view.pitch = Math.asin(off.y / view.radius);
		}
		draw();
	}

	function place() {
		const p = Math.max(-Math.PI / 2 + 0.01, Math.min(Math.PI / 2 - 0.001, view.pitch));
		view.pitch = p;
		camera.position.set(
			view.target.x + view.radius * Math.cos(p) * Math.sin(view.yaw),
			view.target.y + view.radius * Math.sin(p),
			view.target.z + view.radius * Math.cos(p) * Math.cos(view.yaw));
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

	function labels() {
		const box = $("labels");
		box.innerHTML = "";
		if (!current || !$("faces").checked) {
			return;
		}
		const w = canvas.clientWidth;
		const h = canvas.clientHeight;
		for (const f of current.faceLabels) {
			const v = new THREE.Vector3(f.x + 0.5, 2.5, f.z + 0.5).project(camera);
			if (v.z > 1 || v.x < -1 || v.x > 1 || v.y < -1 || v.y > 1) {
				continue;
			}
			const d = document.createElement("div");
			d.className = "face-label";
			d.textContent = f.n;
			d.title = "Boarding face " + f.n + " (platform " + f.platform + ")";
			d.style.left = ((v.x + 1) / 2) * w + "px";
			d.style.top = ((1 - v.y) / 2) * h + "px";
			box.append(d);
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
			["Roof", r.roof, b.roof, null],
			["Footbridge", "", b.footbridge ? "yes" : "no", null],
			["Name", "", b.name || "(no name signs)", null],
			["Overall", "", plan.size.width + " wide × " + plan.size.length + " long × " + plan.size.height + " high, " + plan.size.blocks.toLocaleString() + " blocks", null]
		];
		$("reqTable").innerHTML = "<tr><th>Setting</th><th>Requested</th><th>Built</th></tr>" + rows.map(([k, want, got, met]) =>
			"<tr><th>" + esc(k) + "</th><td>" + esc(want) + "</td><td class='" + (met === true ? "ok" : met === false ? "diff" : "") + "'>" + esc(got) + "</td></tr>"
		).join("");

		const chips = (list, cls) => list.length ? "<div class='chips'>" + list.map((t) => "<span class='chip " + (cls || "") + "'>" + esc(t) + "</span>").join("") + "</div>" : "<p class='note'>None</p>";
		$("fromText").innerHTML =
			"<div class='group'><h3>Recognised</h3>" + chips(plan.understood) + "</div>" +
			"<div class='group'><h3>Conflicts and limits</h3>" + chips(plan.warnings, "warn") + "</div>" +
			"<div class='group'><h3>Not understood</h3>" + chips(plan.ignored, "muted") + "</div>" +
			"<div class='group'><h3>Filled in</h3>" + chips(plan.defaulted, "muted") + "</div>" +
			"<p class='note'>The planner read: “" + esc(plan.text) + "”</p>";

		const fails = plan.checks.filter((c) => c.status === "FAIL").length + plan.violations.length;
		const warns = plan.checks.filter((c) => c.status === "WARN").length;
		const verdict = plan.buildable
			? "<p class='verdict good'>Buildable" + (warns ? " · " + warns + " warning" + (warns > 1 ? "s" : "") : "") + "</p>"
			: "<p class='verdict bad'>Cannot be built · " + fails + " problem" + (fails > 1 ? "s" : "") + "</p>";
		const items = plan.violations.map((v) => "<li><span class='st FAIL'>Rule</span><span>" + esc(v.message) + " (x " + v.x + ", y " + v.y + ", z " + v.z + ")</span></li>")
			.concat(plan.checks.map((c) => "<li><span class='st " + c.status + "'>" + c.status.toLowerCase() + "</span><span>" + esc(c.message) + "</span></li>"));
		$("checks").innerHTML = verdict + "<ul class='list'>" + items.join("") + "</ul>";
	}

	init().catch((e) => {
		$("banner").textContent = "Station Studio could not start: " + e;
	});
})();
