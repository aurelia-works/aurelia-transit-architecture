// Station Studio's block swapper, ticket gates and street entrance, checked in a real browser (Enviro's list, 10-20).
// 1. java -cp build/classes/java/main:build/resources/main tools/StationStudio.java   (prints its port)
// 2. node tools/studio_swaps.mjs [port] [shot.png]   (needs the playwright package and its Chromium). Exits 1 on any failure.
import { chromium } from 'playwright';
import fs from 'fs';
const port = process.argv[2] || '25680';
const shot = process.argv[3];
const cache = process.env.HOME + '/Library/Caches/ms-playwright';
const exe = fs.readdirSync(cache).filter(d => d.startsWith('chromium-')).map(d => cache + '/' + d + '/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing').find(p => fs.existsSync(p));
const b = await chromium.launch({ executablePath: exe, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const p = await (await b.newContext({ viewport: { width: 1440, height: 1000 } })).newPage();
const errors = []; p.on('pageerror', e => errors.push(e.message)); p.on('console', m => m.type() === 'error' && errors.push(m.text()));
let fail = 0; const check = (ok, what) => { console.log((ok ? 'PASS ' : 'FAIL ') + what); if (!ok) fail++; };
const planned = async (before) => p.waitForFunction((n) => window.__plans !== n, before, { timeout: 30000 }).catch(() => {});
await p.addInitScript(() => {
  window.__plans = 0;
  const f = window.fetch;
  window.fetch = async (...a) => { const r = await f(...a); if (String(a[0]).includes('api/plan')) { r.clone().json().then(j => { window.__last = j; window.__plans++; }); } return r; };
});
await p.goto(`http://127.0.0.1:${port}/`, { waitUntil: 'networkidle' });
await p.waitForFunction(() => window.__plans > 0, null, { timeout: 30000 });
await p.evaluate(() => { const d = document.getElementById('more'); if (d) d.open = true; });
// a station with a building, so the gates and the door are there
let n = await p.evaluate(() => window.__plans);
await p.fill('#text', '2 track station, station building, 64 long'); await p.dispatchEvent('#text', 'input'); await planned(n);
const mats = await p.$$eval('#swapFrom option', o => o.map(x => x.value));
check(mats.length > 5, `swap list holds the plan's blocks (${mats.length}: ${mats.slice(0, 4).join(', ')}...)`);
check((await p.$$eval('#blockList option', o => o.length)) > 50, 'replacement suggestions are offered');
const from = mats[0];
const count = (j, id) => j.materials.find(m => m.id === id)?.count || 0;
const had = await p.evaluate(() => window.__last);
n = await p.evaluate(() => window.__plans);
await p.selectOption('#swapFrom', from); await p.fill('#swapTo', 'mud bricks'); await p.click('#swapAdd'); await planned(n);
const after = await p.evaluate(() => window.__last);
check(count(after, from) === 0 && count(after, 'minecraft:mud_bricks') >= count(had, from), `${from} is built as mud bricks (${count(had, from)} -> ${count(after, 'minecraft:mud_bricks')})`);
check((await p.textContent('#swapChips')).toLowerCase().includes('mud bricks'), 'the swap shows as a chip: ' + (await p.textContent('#swapChips')));
n = await p.evaluate(() => window.__plans);
await p.fill('#swapTo', 'not a real block'); await p.selectOption('#swapFrom', (await p.$$eval('#swapFrom option', o => o.map(x => x.value)))[1]); await p.click('#swapAdd'); await planned(n);
check((await p.evaluate(() => window.__last.warnings.join(' '))).includes('not a block this game has') && (await p.$$('#swapChips .chip.warn')).length === 1, 'an unknown block is flagged');
n = await p.evaluate(() => window.__plans);
await p.click('#swapChips .chip.warn button'); await planned(n);
check((await p.$$('#swapChips .chip')).length === 1, 'x undoes a swap');
// gates and door
const gates = (j) => j.gates;
n = await p.evaluate(() => window.__plans);
await p.click('#gatesSeg button:nth-child(2)'); await planned(n);
check(gates(await p.evaluate(() => window.__last)) === 'ata', 'Ticket gates: ATA two-way gates');
n = await p.evaluate(() => window.__plans);
await p.click('#gatesSeg button:nth-child(1)'); await planned(n);
check(gates(await p.evaluate(() => window.__last)) === 'mtr', 'Ticket gates: MTR barriers');
const lanes = (j) => count(j, 'mtr:ticket_barrier_entrance_1') + count(j, 'mtr:ticket_barrier_exit_1');
const five = lanes(await p.evaluate(() => window.__last));
n = await p.evaluate(() => window.__plans);
await p.selectOption('#door', '9'); await planned(n);
const nine = await p.evaluate(() => window.__last);
check(lanes(nine) === five + 4 && nine.understood.some(u => u.includes('entrance 9 wide')), `Street entrance 9 wide: ${five} -> ${lanes(nine)} barrier lanes`);
check(nine.buildable, 'still buildable');
if (shot) { await p.screenshot({ path: shot, fullPage: false }); }
check(errors.length === 0, 'no page errors' + (errors.length ? ': ' + errors.join(' | ') : ''));
await b.close();
process.exit(fail ? 1 : 0);
