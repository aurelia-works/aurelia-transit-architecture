// Renders the "Pick a city" pictures in Station Studio (src/main/resources/studio/thumbs/<LOOK>.jpg).
// 1. java -cp build/classes/java/main:build/resources/main tools/StationStudio.java 25680
// 2. node tools/studio_thumbs.mjs <tmpDir>        (needs the playwright package and its Chromium)
// 3. python3 tools/studio_thumbs_crop.py <tmpDir> src/main/resources/studio/thumbs
// Each picture is the look's city-size terminus front from the Head view, without the track badges and buttons.
import { chromium } from 'playwright';
import fs from 'fs';
const [out] = process.argv.slice(2);
const cache = process.env.HOME + '/Library/Caches/ms-playwright';
const exe = fs.existsSync(cache) ? fs.readdirSync(cache).filter(d => d.startsWith('chromium-'))
	.map(d => cache + '/' + d + '/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing').find(p => fs.existsSync(p)) : undefined;
const browser = await chromium.launch({ executablePath: exe, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
await page.goto('http://127.0.0.1:25680/');
await page.waitForTimeout(2500);
const looks = await page.$$eval('#look option', options => options.map(o => o.value).filter(v => v && v !== 'AUTO' && v !== 'CUSTOM'));
for (const look of looks) {
	await page.evaluate(look => {
		const set = (id, v) => { document.getElementById(id).value = v; };
		set('look', look); set('mix', ''); set('mode', 'terminus'); set('tracks', '6'); set('length', '96'); set('size', 'CITY');
		set('building', '1'); set('roof', 'AUTO'); set('name', ''); set('text', '');
		document.getElementById('name').dispatchEvent(new Event('input'));
	}, look);
	await page.waitForTimeout(4000);
	await page.$eval('[data-view="head"]', b => b.click());
	await page.waitForTimeout(1500);
	await page.addStyleTag({ content: '#labels, .overlay { display: none !important; }' });
	await page.locator('#view').screenshot({ path: `${out}/${look}.png` });
	console.log(look);
}
await browser.close();
