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
// a plan has come in, none is on its way, and nothing was sent or answered for longer than the page's 350 ms debounce
const planned = async (before) => p.waitForFunction((n) => window.__plans !== n && window.__inflight === 0 && performance.now() - window.__busy > 800,
  before, { timeout: 30000, polling: 100 }).catch(() => {});
await p.addInitScript(() => {
  window.__plans = 0; window.__inflight = 0; window.__busy = 0;
  const f = window.fetch;
  // only the newest request's answer counts, as on the page (generate drops an older one that comes back late): taking
  // every answer let an overtaken plan stand in for the swap's or the door's (10-25)
  let sent = 0;
  window.fetch = async (...a) => {
    if (!String(a[0]).includes('api/plan')) { return f(...a); }
    const mine = ++sent; window.__inflight++; window.__busy = performance.now();
    try {
      const r = await f(...a);
      const j = await r.clone().json();
      if (mine === sent) { window.__last = j; window.__plans++; }
      return r;
    } finally { window.__inflight--; window.__busy = performance.now(); }
  };
});
await p.goto(`http://127.0.0.1:${port}/`, { waitUntil: 'networkidle' });
// the seed and its dice sit in the More panel, closed on a fresh page
await p.evaluate(() => { const more = document.getElementById('more'); if (more) more.open = true; });
await p.waitForFunction(() => window.__plans > 0, null, { timeout: 30000 });
await p.evaluate(() => { const d = document.getElementById('more'); if (d) d.open = true; });
// a through station with a building, so the gates and the door are there: its side building's bank has a lane per door cell
// (a terminus head has fixed banks of five, and the page may roll a terminus)
let n = await p.evaluate(() => window.__plans);
if (await p.inputValue('#mode') !== 'through') { await p.click('#modeSeg button:nth-child(1)'); await planned(n); n = await p.evaluate(() => window.__plans); }
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
// barrier lanes from every cell, as the Build card counts: materials lists only the commonest kinds for the swapper, and a
// rolled station with many kinds dropped the few barriers off it (10-25: 4 -> 9 and 0 -> 5 failures)
const lanes = (j) => { let k = 0; for (let i = 3; i < j.cells.length; i += 5) { if (/^mtr:ticket_barrier_(entrance|exit)_1$/.test(j.models[j.cells[i]].id)) k++; } return k; };
// the page's rolled size is kept: its door is 5 wide, 7 at a grand station
const five = lanes(await p.evaluate(() => window.__last));
n = await p.evaluate(() => window.__plans);
await p.selectOption('#door', '9'); await planned(n);
const nine = await p.evaluate(() => window.__last);
check((five === 5 || five === 7) && lanes(nine) === 9 && nine.understood.some(u => u.includes('entrance 9 wide')), `Street entrance 9 wide: ${five} -> ${lanes(nine)} barrier lanes`);
check(nine.buildable, 'still buildable');
if (shot) { await p.screenshot({ path: shot, fullPage: false }); }
check(errors.length === 0, 'no page errors' + (errors.length ? ': ' + errors.join(' | ') : ''));
await b.close();
process.exit(fail ? 1 : 0);
