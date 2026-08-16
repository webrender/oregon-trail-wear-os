// Drives the browser build and screenshots it, so the web port can be checked the way the
// watch is: by looking at every screen.
//
// A Compose canvas has no DOM — there is no element to query, no text to assert on, and
// no selector that means anything. The only thing to inspect is pixels, and the only way
// to get to the screen worth inspecting is to play. Hence a driver rather than a test.
//
// No npm dependencies on purpose: Node 22+ ships a WebSocket client, which is the only
// thing the DevTools protocol needs. Adding Puppeteer for this would mean a package.json,
// a lockfile, and a second Chromium download in a repository that has neither.
//
// Usage — start a headless Chrome with a debugging port pointed at the served build, then:
//
//   node scripts/web-drive.mjs steps.json out-directory
//
// where steps.json is an array of {goto, wait, click:[x,y], wheel:[x,y,dy], key:[code,vk],
// shot:"name"}. Two things learned the hard way:
//
//   - Chrome synthesises *momentum* for a dispatched wheel event, so a large deltaY keeps
//     scrolling after the step returns. Use small deltas and wait between them, or the
//     screenshot catches the list mid-flight and the next step's coordinates are stale.
//   - Scroll position survives between runs of this script, but the viewport override
//     below is re-applied each time. Put a whole sequence in one file rather than
//     computing coordinates from a screenshot taken by a previous run.
import { writeFileSync, readFileSync } from 'node:fs';

const steps = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const outDir = process.argv[3];

const list = await (await fetch('http://127.0.0.1:9222/json/list')).json();
const page = list.find((t) => t.type === 'page');
if (!page) { console.error('no page target'); process.exit(1); }

const ws = new WebSocket(page.webSocketDebuggerUrl);
let nextId = 1;
const pending = new Map();

function send(method, params = {}) {
  const id = nextId++;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((res, rej) => pending.set(id, { res, rej }));
}

ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);
  if (msg.id && pending.has(msg.id)) {
    const { res, rej } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result);
  }
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function click(x, y) {
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y, buttons: 0 });
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1, buttons: 1 });
  await sleep(60);
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1, buttons: 0 });
}

async function wheel(x, y, deltaY) {
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y, buttons: 0 });
  await send('Input.dispatchMouseEvent', { type: 'mouseWheel', x, y, deltaX: 0, deltaY, buttons: 0 });
}

async function key(code, vk) {
  await send('Input.dispatchKeyEvent', { type: 'rawKeyDown', code, key: code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk });
  await sleep(30);
  await send('Input.dispatchKeyEvent', { type: 'keyUp', code, key: code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk });
}

async function shot(name) {
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(`${outDir}/${name}.png`, Buffer.from(data, 'base64'));
  console.log('shot', name);
}

ws.onopen = async () => {
  try {
    await send('Page.enable');
    // A fixed viewport, so that a step's click coordinates mean the same thing on every
    // run. The watch is centred in it: see `WATCH` in the step files.
    await send('Emulation.setDeviceMetricsOverride', {
      width: 900, height: 900, deviceScaleFactor: 1, mobile: false,
    });
    for (const step of steps) {
      if (step.goto) { await send('Page.navigate', { url: step.goto }); }
      if (step.wait) await sleep(step.wait);
      if (step.click) await click(step.click[0], step.click[1]);
      if (step.wheel) await wheel(step.wheel[0], step.wheel[1], step.wheel[2]);
      if (step.key) await key(step.key[0], step.key[1]);
      if (step.shot) await shot(step.shot);
    }
    console.log('DONE');
    process.exit(0);
  } catch (err) {
    console.error('FAILED', err);
    process.exit(1);
  }
};
ws.onerror = (e) => { console.error('WS ERROR', e.message ?? e); process.exit(1); };
