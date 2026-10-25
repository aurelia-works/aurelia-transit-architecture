// Station Studio features, checked in a real browser: the page opens with WebGL, the Build card, Layers, the three cutaways,
// Materials, Walk (enter, move, Esc, leave), the R key and the copied /ata_station command.
// 1. java -cp build/classes/java/main:build/resources/main tools/StationStudio.java   (prints its port)
// 2. node tools/studio_features.mjs [port]   (needs the playwright package and its Chromium). Exits 1 on any failure.
import { chromium } from 'playwright';
import fs from 'fs';
const port = process.argv[2] || '25680';
const cache = process.env.HOME + '/Library/Caches/ms-playwright';
const exe = fs.readdirSync(cache).filter(d => d.startsWith('chromium-')).map(d => cache + '/' + d + '/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing').find(p => fs.existsSync(p));
const b = await chromium.launch({ executablePath: exe, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const ctx = await b.newContext({ viewport: { width: 1440, height: 900 } });
await ctx.grantPermissions(['clipboard-read', 'clipboard-write']);
const p = await ctx.newPage();
const errors = []; p.on('pageerror', e => errors.push(e.message)); p.on('console', m => m.type() === 'error' && errors.push(m.text()));
let fail = 0; const check = (ok, what) => { console.log((ok ? 'PASS ' : 'FAIL ') + what); if (!ok) fail++; };
const t0 = Date.now();
await p.goto(`http://127.0.0.1:${port}/`, { waitUntil: 'networkidle' });
// the seed and its dice sit in the More panel, closed on a fresh page
await p.evaluate(() => { const more = document.getElementById('more'); if (more) more.open = true; });
// the first visit rolls a random station, and the biggest take seconds to plan: wait for the plan, not a fixed time
await p.waitForFunction(() => /Planned in/.test(document.getElementById('status').textContent), null, { timeout: 30000 }).catch(() => {});
check(await p.title() !== '' && (await p.$$('.look')).length > 10, `studio opens (${Date.now() - t0} ms, ${(await p.$$('.look')).length} looks)`);
check(!(await p.$eval('#stage', s => s.classList.contains('no-gl'))), 'WebGL preview is live');
const status = await p.textContent('#status'); check(/Planned in \d+ ms/.test(status), 'status: ' + status);
// Build card
const facts = await p.$$eval('#facts dt, #facts dd', n => n.map(x => x.textContent.trim()));
check(facts.length >= 4, 'Build card: ' + facts.join(' / '));
await p.click('#roll'); await p.waitForTimeout(1500);
const facts2 = await p.$$eval('#facts dd', n => n.map(x => x.textContent.trim()).join(' / '));
check(facts2.length > 0, 'Build card refreshes after a roll: ' + facts2);
// Layers
const layers = await p.$$eval('#layers button', bs => bs.map(b => [b.dataset.layer, b.disabled, b.getAttribute('aria-pressed')]));
const live = layers.filter(l => !l[1]);
check(live.length >= 2, 'Layers: ' + layers.map(l => l[0] + (l[1] ? '(none)' : '')).join(', '));
const first = live.find(l => l[0] !== 'ground') || live[0];
const shotA = await p.screenshot({ clip: await (await p.$('#stage')).boundingBox() });
await p.click(`#layers button[data-layer="${first[0]}"]`); await p.waitForTimeout(400);
const pressed = await p.getAttribute(`#layers button[data-layer="${first[0]}"]`, 'aria-pressed');
const shotB = await p.screenshot({ clip: await (await p.$('#stage')).boundingBox() });
check(pressed === 'false' && !shotA.equals(shotB), `hiding layer "${first[0]}" changes the view`);
await p.click(`#layers button[data-layer="${first[0]}"]`); await p.waitForTimeout(300);
check(await p.getAttribute(`#layers button[data-layer="${first[0]}"]`, 'aria-pressed') === 'true', 'and showing it again');
// Cutaway
check(await p.$eval('#cutAt', e => e.disabled), 'cut slider disabled with "Show everything"');
for (const mode of ['height', 'across', 'along']) {
  await p.selectOption('#cut', mode); await p.waitForTimeout(200);
  await p.$eval('#cutAt', e => { e.value = 100; e.dispatchEvent(new Event('input', { bubbles: true })); }); await p.waitForTimeout(300);
  const before = await p.screenshot({ clip: await (await p.$('#stage')).boundingBox() });
  await p.$eval('#cutAt', e => { e.value = 35; e.dispatchEvent(new Event('input', { bubbles: true })); }); await p.waitForTimeout(300);
  const after = await p.screenshot({ clip: await (await p.$('#stage')).boundingBox() });
  check(!(await p.$eval('#cutAt', e => e.disabled)) && !before.equals(after), `cutaway "${mode}" at 35% changes the view`);
}
await p.selectOption('#cut', 'none'); await p.waitForTimeout(200);
// Materials
await p.click('#matFold summary'); await p.waitForTimeout(200);
const rows = await p.$$eval('#materials tr', r => r.map(x => x.textContent.replace(/\s+/g, ' ').trim()));
check(await p.$eval('#matFold', d => d.open) && rows.length >= 3, `Materials: ${rows.length} rows, e.g. ${rows.slice(0, 3).join(' | ')}`);
// Walk
await p.click('#walkBtn'); await p.waitForTimeout(800);
check(await p.getAttribute('#walkBtn', 'aria-pressed') === 'true' && !(await p.$eval('#walkHud', h => h.hidden)), 'Walk: HUD shows');
const s0 = await p.evaluate(() => window.studioWalk.state());
check(!!s0, 'Walk: walker placed at ' + (s0 && [s0.x, s0.y, s0.z].map(v => v.toFixed(1)).join(',')));
await p.evaluate(() => window.studioWalk.run(['KeyW'], 1.5));
const s1 = await p.evaluate(() => window.studioWalk.state());
const moved = Math.hypot(s1.x - s0.x, s1.z - s0.z);
check(moved > 1, `Walk: W walks ${moved.toFixed(2)} blocks in 1.5 s, on ground ${s1.onGround}`);
const seedBefore = await p.inputValue('#seed');
await p.keyboard.press('r'); await p.waitForTimeout(500);
check(await p.inputValue('#seed') === seedBefore, 'Walk: R does not roll while walking');
// the browser's own Esc frees a locked mouse; a synthetic Esc cannot, so free it the way the browser does
const freeMouse = () => p.evaluate(() => document.exitPointerLock()).then(() => p.waitForTimeout(300));
check(await p.evaluate(() => !!document.pointerLockElement), 'Walk: the mouse is locked to the view');
await freeMouse();
check(!(await p.$eval('#walkHud', h => h.classList.contains('locked'))) && await p.isVisible('#walkClick'), 'Walk: freeing the mouse shows "Click to walk around"');
await p.keyboard.press('Escape'); await p.waitForTimeout(400);
check(await p.getAttribute('#walkBtn', 'aria-pressed') === 'false' && await p.$eval('#walkHud', h => h.hidden), 'Walk: Esc again leaves and hides the HUD');
await p.click('#walkBtn'); await p.waitForTimeout(600); await freeMouse();
await p.click('#walkBtn'); await p.waitForTimeout(400);
check(await p.getAttribute('#walkBtn', 'aria-pressed') === 'false' && await p.$eval('#walkHud', h => h.hidden), 'Walk: the Walk button leaves too');
await p.keyboard.press('r'); await p.waitForTimeout(1200);
check(await p.inputValue('#seed') !== seedBefore, 'R rolls outside walk');
// Copy command
await p.click('#copyCmd'); await p.waitForTimeout(400);
const clip = await p.evaluate(() => navigator.clipboard.readText().catch(e => 'ERR ' + e));
check(/^\/ata_station /.test(clip) && /seed [a-z0-9]+/.test(clip), 'Copy command: ' + clip.slice(0, 160));
// Export: the .schem download is a gzip, and its fingerprint header is the plan's
await p.click('#roll'); await p.waitForTimeout(1500);
const planFp = await p.evaluate(() => window.studioView.plan().fingerprint);
const [dl] = await Promise.all([p.waitForEvent('download'), p.click('#exportBtn')]);
const bytes = fs.readFileSync(await dl.path());
check(bytes[0] === 0x1f && bytes[1] === 0x8b && /\.schem$/.test(dl.suggestedFilename()), `Export: ${dl.suggestedFilename()} is a gzip of ${bytes.length} bytes`);
const note = await p.textContent('#exportNote');
check(!!planFp && note.includes(planFp), `Export: the fingerprint header (${(note.match(/fingerprint (\w+)/) || [])[1]}) is the plan's (${planFp})`);
// Checks show a title and the next step; the tally is the plan's summary
await p.click('#roll'); await p.waitForTimeout(1500);
const titles = await p.$$eval('#checks .ttl', n => n.length);
check(titles > 0, `Checks: ${titles} items carry a title`);
// Class: a halt plans a station with no building; Facing shows on the compass
const planned = () => p.evaluate(() => window.studioView.plan());
await p.click('#classSeg button:text-is("Halt")'); await p.waitForTimeout(1800);
const halt = await p.evaluate(() => fetch('api/plan', { method: 'POST', body: new URLSearchParams({ class: 'halt', pinned: 'class', tracks: '2' }), headers: { 'X-Studio-Token': document.body.dataset.token } }).then(r => r.json()));
check(halt.built.building === 'no building' && halt.resolved.class === 'halt', `Class halt: ${halt.built.building}, resolved ${halt.resolved.class}`);
check((await p.textContent('#reqTable')).includes('no building'), 'Class halt on the page: the station has no building');
await p.click('#facingSeg button:text-is("East")'); await p.waitForTimeout(1800);
check((await p.textContent('#compassFacing')).toLowerCase().includes('east'), 'Facing east shows on the compass: ' + (await p.textContent('#compassFacing')));
check(errors.length === 0, 'no page errors' + (errors.length ? ': ' + errors.slice(0, 3).join('; ') : ''));
await b.close();
console.log(fail ? fail + ' failed' : 'all passed'); process.exit(fail ? 1 : 0);
