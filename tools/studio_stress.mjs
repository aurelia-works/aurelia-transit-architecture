// Station Studio stress test, in a real browser: plan latency as the page sees it, frame time on the largest station, rapid
// input (the dice spammed, sliders dragged fast: an older /api/plan answer must never replace a newer one), memory and GPU
// resources after many rerolls, console errors and failed requests.
// 1. java -cp build/classes/java/main:build/resources/main tools/StationStudio.java   (prints its port)
// 2. node tools/studio_stress.mjs [port] [rerolls]   (needs the playwright package and its Chromium). Exits 1 on a failure.
// On a Mac the page renders through the real GPU (Metal); set STUDIO_SOFTWARE_GL=1 (or run elsewhere) for software GL, which
// is 50 to 400 times slower and only a ceiling.
import { chromium } from 'playwright';
import fs from 'fs';
const [port = '25680', rerollsArg = '50'] = process.argv.slice(2);
const REROLLS = +rerollsArg;
const cache = process.env.HOME + '/Library/Caches/ms-playwright';
const exe = fs.existsSync(cache) ? fs.readdirSync(cache).filter(d => d.startsWith('chromium-'))
	.map(d => cache + '/' + d + '/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing').find(p => fs.existsSync(p)) : undefined;
const browser = await chromium.launch({ executablePath: exe, args: [...(process.platform === 'darwin' && !process.env.STUDIO_SOFTWARE_GL ? ['--use-angle=metal', '--ignore-gpu-blocklist'] : ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']), '--enable-precise-memory-info', '--js-flags=--expose-gc'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
const failed = [];
const answers = [];
let seq = 0;
const order = new Map();
page.on('request', r => r.url().endsWith('/api/plan') && order.set(r, ++seq));
page.on('pageerror', e => errors.push(e.message));
page.on('console', m => m.type() === 'error' && errors.push(m.text()));
page.on('requestfailed', r => failed.push(r.method() + ' ' + r.url() + ' ' + (r.failure() || {}).errorText));
page.on('response', async r => {
	if (r.status() >= 400) {
		failed.push(r.status() + ' ' + r.url());
	}
	if (r.url().endsWith('/api/plan')) {
		try {
			answers.push({ seq: order.get(r.request()), sent: Object.fromEntries(new URLSearchParams(r.request().postData())), plan: await r.json(), at: Date.now() });
		} catch (e) {
			// a reply cut off by a newer request
		}
	}
});
let fail = 0;
const check = (ok, what) => {
	console.log((ok ? 'PASS ' : 'FAIL ') + what);
	if (!ok) {
		fail++;
	}
};
const set = (id, v) => page.evaluate(([id, v]) => {
	const e = document.getElementById(id);
	e.value = v;
	e.dispatchEvent(new Event('input', { bubbles: true }));
}, [id, v]);
const idle = async () => {
	await page.waitForTimeout(600);
	await page.waitForFunction(() => document.getElementById('busy').hidden, null, { timeout: 120000 });
	await page.waitForTimeout(150);
};
const heap = async (cdp) => {
	await cdp.send('HeapProfiler.collectGarbage');
	const m = (await cdp.send('Performance.getMetrics')).metrics;
	return Math.round(m.find(x => x.name === 'JSHeapUsedSize').value / 1048576);
};
const pct = (a, q) => a.slice().sort((x, y) => x - y)[Math.min(a.length - 1, Math.floor(a.length * q))];

await page.goto(`http://127.0.0.1:${port}/`, { waitUntil: 'networkidle' });
await idle();
const cdp = await page.context().newCDPSession(page);
await cdp.send('Performance.enable');

// 1. plan latency as the page sees it: from the request leaving to the new station drawn and the status line updated
await page.evaluate(() => {
	window.__stamps = [];
	new MutationObserver(() => window.__stamps.push(performance.now())).observe(document.getElementById('status'), { childList: true, characterData: true, subtree: true });
});
const lat = [];
for (const [mode, tracks, length] of [['through', 2, 64], ['through', 10, 300], ['terminus', 10, 300], ['hub', 10, 300], ['through', 1, 16]]) {
	await set('mode', mode);
	await set('tracks', tracks);
	await set('length', length);
	await idle();
	const a = answers[answers.length - 1];
	const seen = await page.evaluate(() => {
		const req = performance.getEntriesByType('resource').filter(e => e.name.endsWith('/api/plan')).pop();
		const stamp = window.__stamps[window.__stamps.length - 1];
		return { net: req.responseEnd - req.startTime, drawn: stamp - req.startTime };
	});
	lat.push({ what: `${mode} ${tracks}x${length}`, server: a.plan.ms, blocks: a.plan.size.blocks, net: Math.round(seen.net), drawn: Math.round(seen.drawn) });
}
console.log('plan latency (server = planner only; answer = request to reply; shown = request to station drawn and status updated):');
for (const l of lat) {
	console.log(`  ${l.what.padEnd(18)} ${String(l.blocks).padStart(7)} blocks  server ${String(l.server).padStart(5)} ms  answer ${String(l.net).padStart(5)} ms  shown ${String(l.drawn).padStart(5)} ms`);
}
check(lat.every(l => l.drawn < 8000), 'every station is on screen within 8 s of the request');

// 2. frame time on the largest station: renders from a turning camera, each finished on the GPU
await set('mode', 'terminus'); await set('tracks', 10); await set('length', 300); await idle();
const gpuName = await page.evaluate(() => { const g = document.createElement('canvas').getContext('webgl'); const e = g && g.getExtension('WEBGL_debug_renderer_info'); return e ? g.getParameter(e.UNMASKED_RENDERER_WEBGL) : 'unknown GL'; });
const frames = (await page.evaluate(() => window.studioView.bench(24))).slice(2);
const big = answers[answers.length - 1].plan.size.blocks;
console.log(`frame time on the largest station (${big} blocks): median ${pct(frames, 0.5).toFixed(1)} ms, p95 ${pct(frames, 0.95).toFixed(1)} ms, worst ${Math.max(...frames).toFixed(1)} ms over ${frames.length} renders on ${gpuName}`);

// 3. rapid input: the dice spammed, then sliders dragged fast; the page must end on the newest answer
async function endsOnNewest(label) {
	await idle();
	const shown = await page.evaluate(() => window.studioView.plan());
	// the newest request the page sent is the last one whose answer arrived last in request order
	const newestSent = answers.reduce((a, b) => (b.seq > a.seq ? b : a));
	check(shown && shown.text === newestSent.plan.text, `${label}: the page shows the newest request's station ("${shown && shown.text.slice(0, 40)}")`);
}
let before = answers.length;
for (let i = 0; i < 20; i++) {
	await page.keyboard.press('r');
	await page.waitForTimeout(25);
}
await endsOnNewest('20 rolls at 25 ms');
before = answers.length;
for (let i = 0; i <= 30; i++) {
	await set('tracks', 1 + (i % 10));
	await set('length', 16 + i * 9);
	await page.waitForTimeout(60);
}
await endsOnNewest('slider drag, 31 steps at 60 ms');
const sent = answers.reduce((a, b) => (b.seq > a.seq ? b : a)).sent;
const ui = await page.evaluate(() => ({ tracks: document.getElementById('tracks').value, length: document.getElementById('length').value }));
check(String(sent.tracks) === ui.tracks && String(sent.length) === ui.length, `the last request carries the final slider values (tracks ${ui.tracks}, length ${ui.length})`);
// a slow older answer racing a fast newer one (held in the network layer)
let held = 0;
await page.route('**/api/plan', async route => {
	const mine = ++held;
	if (mine === 1) {
		await new Promise(r => setTimeout(r, 2500));
	}
	await route.continue();
});
await set('tracks', 3); await page.waitForTimeout(450); await set('tracks', 6);
await page.waitForTimeout(4000);
await page.unroute('**/api/plan');
await endsOnNewest('older answer held 2.5 s behind a newer one');
check((await page.evaluate(() => document.getElementById('tracks').value)) === '6' && (await page.evaluate(() => window.studioView.plan())).text.includes('6 track'), 'and it is the 6 track station');

// 4. memory and GPU resources over many rerolls
await set('mode', 'through'); await set('tracks', 4); await set('length', 120); await idle();
const h0 = await heap(cdp);
const samples = [];
const gpu = [];
for (let i = 0; i < REROLLS; i++) {
	await page.click('#roll');
	await idle();
	// draw once and read what the renderer holds, so every sample is taken in the same state
	await page.evaluate(() => window.studioView.bench(1));
	if ((i + 1) % 10 === 0) {
		samples.push(await heap(cdp));
		gpu.push(await page.evaluate(() => window.studioView.stats()));
	}
}
const h1 = await heap(cdp);
console.log(`after ${REROLLS} rerolls: JS heap ${h0} MB -> ${h1} MB (every 10: ${samples.join(', ')}); renderer geometries every 10: ${gpu.map(g => g.geometries).join(', ')}; GPU textures: ${gpu.map(g => g.gpuTextures).join(', ')}; programs: ${gpu.map(g => g.programs).join(', ')}`);
check(h1 < h0 * 2 + 60, 'the JS heap does not grow without bound over the rerolls');
// the renderer holds the geometries of the station on show and no more (stations differ in size, so compare each sample with its own
// mesh count); textures are a cache, bounded by how many different blocks have been drawn
check(gpu.every(g => g.geometries <= g.meshes * 1.5 + 50), 'renderer geometries stay with the station on show, not piling up over the rerolls');
check(gpu[gpu.length - 1].gpuTextures <= gpu[0].gpuTextures + 150, 'GPU textures stay a bounded cache over the rerolls');

check(errors.length === 0, 'no console errors' + (errors.length ? ': ' + [...new Set(errors)].slice(0, 5).join(' | ') : ''));
check(failed.length === 0, 'no failed requests' + (failed.length ? ': ' + [...new Set(failed)].slice(0, 5).join(' | ') : ''));
await browser.close();
console.log(fail ? `${fail} failed` : 'all passed');
process.exit(fail ? 1 : 0);
