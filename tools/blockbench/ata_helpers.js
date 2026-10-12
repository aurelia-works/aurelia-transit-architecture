// Blockbench helpers for the ATA realism pass. Load inside Blockbench (desktop) with:
//   eval(requireNativeModule('fs').readFileSync('<repo>/tools/blockbench/ata_helpers.js', 'utf8'))
// then, in a new Java Block project:
//   ataLoad('structural_beam')            opens the generated model (run generate_assets.py first, so textures resolve)
//   ataExport('structural_beam')          writes tools/models/<folder>/<model>.json and the .bbmodel next to it
// ataExport(folder, model) exports a variant model (e.g. 'sbahn_platform_edge', 'sbahn_platform_edge') into a block's folder.
(function () {
	const fs = requireNativeModule('fs');
	const ROOT = '/Users/Boon/Downloads/ATA-blocks-realism';
	const NS = 'aurelia_transit_architecture';
	const RES = ROOT + '/src/main/resources/assets/' + NS;
	window.ATA_ROOT = ROOT;

	window.ataLoad = function (model) {
		const path = RES + '/models/block/' + model + '.json';
		const json = JSON.parse(fs.readFileSync(path, 'utf8'));
		// start from an empty project, so loading a second model into the same tab does not stack them
		Outliner.root.slice().forEach(n => n.remove());
		Texture.all.slice().forEach(t => t.remove(true));
		Codecs.java_block.parse(json, path);
		Project.name = 'ata_' + model;
		Canvas.updateAll();
		return { elements: Cube.all.length, textures: Texture.all.map(t => t.name) };
	};

	// Texture references as the generator writes them: "aurelia_transit_architecture:block/<name>", keys kept from the model.
	function textureRef(tex) {
		const name = (tex.name || '').replace(/\.png$/, '');
		return NS + ':block/' + name;
	}

	window.ataCompile = function (sourceModel) {
		const compiled = Codecs.java_block.compile({ raw: true });
		const json = typeof compiled === 'string' ? JSON.parse(compiled) : compiled;
		// keep the texture variable names (#side, #top...) of the generated model where the file names match
		const keys = {};
		if (sourceModel && sourceModel.textures) {
			for (const [k, v] of Object.entries(sourceModel.textures)) keys[v] = k;
		}
		const rename = {};
		const textures = {};
		for (const [k, v] of Object.entries(json.textures || {})) {
			const tex = Texture.all.find(t => String(t.id) === k || t.name.replace(/\.png$/, '') === String(v).split('/').pop());
			const ref = tex ? textureRef(tex) : (String(v).includes(':') ? v : NS + ':block/' + String(v).split('/').pop());
			const key = k === 'particle' ? 'particle' : (keys[ref] || (tex ? tex.name.replace(/\.png$/, '') : k));
			rename['#' + k] = '#' + key;
			textures[key] = ref;
		}
		if (!textures.particle) textures.particle = Object.values(textures)[0];
		const out = { parent: 'block/block', textures: textures };
		if (json.ambientocclusion === false) out.ambientocclusion = false;
		out.elements = (json.elements || []).map(e => {
			const el = { from: e.from, to: e.to };
			if (e.rotation) el.rotation = e.rotation;
			if (e.shade === false) el.shade = false;
			el.faces = {};
			for (const [f, face] of Object.entries(e.faces || {})) {
				const nf = { uv: face.uv, texture: rename[face.texture] || face.texture };
				if (face.rotation) nf.rotation = face.rotation;
				if (face.cullface) nf.cullface = face.cullface;
				if (face.tintindex !== undefined) nf.tintindex = face.tintindex;
				el.faces[f] = nf;
			}
			return el;
		});
		if (json.display) out.display = json.display;
		return out;
	};

	// The project texture for a generated texture name, loaded from the generated assets when it is not in the project yet.
	window.ataTexture = function (name) {
		let tex = Texture.all.find(t => t.name.replace(/\.png$/, '') === name);
		if (!tex) tex = new Texture().fromPath(RES + '/textures/block/' + name + '.png').add(false);
		return tex;
	};

	// Points the given faces of a cube (by name or index) at a texture: ataFace(0, ['north'], 'sbahn_platform_edge_face').
	window.ataFace = function (cube, faces, name) {
		const c = typeof cube === 'number' ? Cube.all[cube] : Cube.all.find(e => e.name === cube);
		const tex = ataTexture(name);
		Undo.initEdit({ elements: [c], textures: Texture.all });
		for (const f of faces) c.faces[f].texture = tex.uuid;
		Undo.finishEdit('ATA face texture');
		Canvas.updateAll();
		return c.name;
	};

	window.ataExport = function (folder, model) {
		model = model || folder;
		const dir = ROOT + '/tools/models/' + folder;
		if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
		let source = null;
		try { source = JSON.parse(fs.readFileSync(RES + '/models/block/' + model + '.json', 'utf8')); } catch (e) { }
		const out = window.ataCompile(source);
		fs.writeFileSync(dir + '/' + model + '.json', JSON.stringify(out, null, 2) + '\n');
		fs.writeFileSync(dir + '/' + model + '.bbmodel', Codecs.project.compile({ compressed: false, absolute_paths: false }));
		return { path: dir + '/' + model + '.json', elements: out.elements.length, textures: out.textures };
	};
})();
