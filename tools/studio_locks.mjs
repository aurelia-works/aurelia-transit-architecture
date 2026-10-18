// Station Studio's seed locks, checked on the planned station (the /api/plan answers), not on the controls: a lock passed
// a controls-only check while the station still changed under it (a locked "Auto" roof followed the newly rolled city).
// 1. java -cp build/classes/java/main:build/resources/main tools/StationStudio.java   (prints its port: the first free from 25680)
// 2. node tools/studio_locks.mjs [port] [rolls]   (needs the playwright package and its Chromium; port defaults to 25680)
// Each lock alone, rolled several times: what it keeps must not change in any plan, and something it does not keep must.
// Exits 1 on any failure.
import { chromium } from 'playwright';
import fs from 'fs';
const [port = '25680', rollsArg = '6'] = process.argv.slice(2);
const ROLLS = +rollsArg;
const cache = process.env.HOME + '/Library/Caches/ms-playwright';
const exe = fs.existsSync(cache) ? fs.readdirSync(cache).filter(d => d.startsWith('chromium-'))
	.map(d => cache + '/' + d + '/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing').find(p => fs.existsSync(p)) : undefined;
const browser = await chromium.launch({ executablePath: exe });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const plans = [];
page.on('response', async r => {
	if (r.url().endsWith('/api/plan') && r.ok()) {
		try {
			plans.push({ plan: await r.json(), sent: Object.fromEntries(new URLSearchParams(r.request().postData())) });
		} catch (e) {
			// a plan replaced by a newer one while it was read
		}
	}
});
const errors = [];
page.on('pageerror', e => errors.push(e.message));

/** What each lock keeps, read from one plan answer and the request that asked for it. */
const KEEPS = {
	// the city and the mix: the hall and platform style; and the planner seed's look choices (canopy, shed, floors, roof deck)
	look: ({ plan, sent }) => [sent.look, sent.mix, plan.built.style, plan.seed, sent.era].join(' | '),
	shape: ({ plan }) => [plan.built.mode, plan.built.tracks, plan.built.length, plan.built.layout].join(' | '),
	// a building or none, its size and roof form, the stairs, stairwells, travelators and track bed
	building: ({ plan, sent }) => [plan.built.building === 'no building', plan.built.scale, plan.built.roof, sent.esc, sent.walls, sent.trav, sent.bed, sent.class].join(' | ')
};

async function rollOnce() {
	const before = plans.length;
	await page.click('#roll');
	for (let t = 0; t < 200 && plans.length === before; t++) {
		await page.waitForTimeout(50);
	}
	await page.waitForTimeout(300);
	return plans[plans.length - 1];
}

async function setLocks(on) {
	for (const b of await page.$$('[data-lock]')) {
		const want = on.includes(await b.getAttribute('data-lock'));
		if ((await b.getAttribute('aria-pressed')) !== String(want)) {
			await b.click();
		}
	}
	await page.waitForTimeout(400);
}

let failed = 0;
function check(ok, what) {
	console.log((ok ? 'PASS ' : 'FAIL ') + what);
	if (!ok) {
		failed++;
	}
}

await page.goto(`http://127.0.0.1:${port}/`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
const locks = await page.$$eval('[data-lock]', bs => bs.map(b => b.dataset.lock));
for (const lock of Object.keys(KEEPS)) {
	if (!locks.includes(lock)) {
		check(false, `the page has a ${lock} lock`);
		continue;
	}
	await setLocks([]);
	await rollOnce();
	await setLocks([lock]);
	const first = await rollOnce();
	const kept = new Set([KEEPS[lock](first)]);
	const others = Object.keys(KEEPS).filter(k => k !== lock);
	const free = Object.fromEntries(others.map(k => [k, new Set([KEEPS[k](first)])]));
	for (let i = 1; i < ROLLS; i++) {
		const p = await rollOnce();
		kept.add(KEEPS[lock](p));
		others.forEach(k => free[k].add(KEEPS[k](p)));
	}
	check(kept.size === 1, `${lock} locked: the station keeps ${lock} over ${ROLLS} rolls` + (kept.size === 1 ? '' : ': ' + [...kept].join('  //  ')));
	if (lock === 'look') {
		// whether there is a hall is Building's; what a hall is made of, whenever there is one, is Look's
		const halls = new Set(plans.slice(plans.indexOf(first)).map(p => p.plan.built.building).filter(b => b && b !== 'no building'));
		check(halls.size <= 1, 'look locked: every hall is built the same' + (halls.size <= 1 ? '' : ': ' + [...halls].join('  //  ')));
	}
	check(others.some(k => free[k].size > 1), `${lock} locked: the unlocked parts still change`);
}

// Building locked to no building: a roll must not bring a terminus or hub, which always have one
await setLocks([]);
for (let i = 0; i < 20; i++) {
	const p = await rollOnce();
	if (p.plan.built.building === 'no building') {
		break;
	}
}
if (plans[plans.length - 1].plan.built.building === 'no building') {
	await setLocks(['building']);
	let built = 0;
	for (let i = 0; i < ROLLS; i++) {
		const p = await rollOnce();
		built += p.plan.built.building === 'no building' ? 0 : 1;
	}
	check(built === 0, `building locked to none: no roll brings a building (${built} of ${ROLLS} did)`);
} else {
	check(false, 'found a station without a building to lock');
}

// a lock survives a reload, and a typed seed respects it
await setLocks(['shape']);
const shape = KEEPS.shape(plans[plans.length - 1]);
await page.reload({ waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
check((await page.getAttribute('[data-lock="shape"]', 'aria-pressed')) === 'true', 'the shape lock survives a reload');
check(KEEPS.shape(await rollOnce()) === shape, 'after a reload a roll still keeps the shape');
const before = plans.length;
await page.fill('#seed', 'abc123');
await page.press('#seed', 'Enter');
for (let t = 0; t < 200 && plans.length === before; t++) {
	await page.waitForTimeout(50);
}
await page.waitForTimeout(300);
check(KEEPS.shape(plans[plans.length - 1]) === shape, 'a typed seed keeps the locked shape');
// a locked group always goes to the server as pinned, even when a chosen class or era opens the dice's settings and after a
// reload (it once came back empty, and the class then decided the shape)
const pins = (plans[plans.length - 1].sent.pinned || '').split(',');
check(['mode', 'tracks', 'length', 'layout'].every((f) => pins.includes(f)), 'the locked shape is sent as pinned: ' + pins.join(','));
await setLocks([]);

check(errors.length === 0, 'no page errors' + (errors.length ? ': ' + errors.join('; ') : ''));
await browser.close();
console.log(failed ? `${failed} failed` : 'all passed');
process.exit(failed ? 1 : 0);
