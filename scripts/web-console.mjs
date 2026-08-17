// Loads the built browser game in headless Chrome and prints everything the console says.
//
// A companion to `web-drive.mjs`, which plays the game and screenshots it. This one only
// asks the question that comes before that: does the page come up at all? A Compose canvas
// has no DOM to assert against, so a wasm module that throws on the way in looks exactly
// like one that is still loading — a black page — and neither the Gradle build nor the
// Pages deploy notices, because both of them succeeded.
//
// Same no-dependency approach as `web-drive.mjs`: Node 22+ ships a WebSocket client, which
// is all the DevTools protocol needs.
//
// Usage — start a headless Chrome with a debugging port, then:
//
//   node scripts/web-console.mjs http://127.0.0.1:8712/ [seconds]
//
// Exits non-zero if anything was thrown, logged as an error, or failed to load, and prints
// the lot either way.
const url = process.argv[2] ?? 'http://127.0.0.1:8712/';
const settleSeconds = Number(process.argv[3] ?? 25);

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

const problems = [];
const lines = [];

// `Runtime.consoleAPICalled` carries console.log and friends; `Runtime.exceptionThrown` is
// the one that matters, because an uncaught Kotlin exception arrives there and nowhere
// else. Failed requests are worth catching too: a 404 on the font or the wasm produces a
// page that is blank for a reason nothing else reports.
ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);
  if (msg.id && pending.has(msg.id)) {
    const { res, rej } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? rej(new Error(JSON.stringify(msg.error))) : res(msg.result);
    return;
  }
  if (msg.method === 'Runtime.consoleAPICalled') {
    const text = msg.params.args.map(describe).join(' ');
    lines.push(`[console.${msg.params.type}] ${text}`);
    if (msg.params.type === 'error') problems.push(text);
  }
  if (msg.method === 'Runtime.exceptionThrown') {
    const d = msg.params.exceptionDetails;
    const text = [
      d.exception?.description ?? d.text,
      ...(d.stackTrace?.callFrames ?? [])
        .slice(0, 12)
        .map((f) => `    at ${f.functionName || '<anonymous>'} (${f.url}:${f.lineNumber})`),
    ].join('\n');
    lines.push(`[uncaught] ${text}`);
    problems.push(text);
  }
  if (msg.method === 'Log.entryAdded') {
    const { level, text, url: from } = msg.params.entry;
    lines.push(`[log.${level}] ${text}${from ? ` (${from})` : ''}`);
    if (level === 'error') problems.push(text);
  }
  if (msg.method === 'Network.loadingFailed') {
    lines.push(`[net] failed: ${msg.params.errorText} (${msg.params.type})`);
    problems.push(`request failed: ${msg.params.errorText}`);
  }
};

function describe(arg) {
  if (arg.unserializableValue) return arg.unserializableValue;
  if ('value' in arg) return typeof arg.value === 'string' ? arg.value : JSON.stringify(arg.value);
  return arg.description ?? arg.type;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
await new Promise((res) => { ws.onopen = res; });

await send('Runtime.enable');
await send('Log.enable');
await send('Network.enable');
await send('Page.enable');
await send('Emulation.setDeviceMetricsOverride', {
  width: 1280, height: 800, deviceScaleFactor: 2, mobile: false,
});
await send('Page.navigate', { url });
await sleep(settleSeconds * 1000);

// What the page ended up as, which distinguishes "threw" from "never finished loading":
// the placeholder is removed by `main()` just before Compose is started, and the canvas is
// appended by Compose itself.
const state = await send('Runtime.evaluate', {
  expression: `JSON.stringify({
    loading: !!document.getElementById('loading'),
    canvases: document.getElementsByTagName('canvas').length,
    canvasSize: (() => { const c = document.querySelector('canvas');
      return c ? [c.width, c.height, c.clientWidth, c.clientHeight] : null; })(),
  })`,
  returnByValue: true,
});

console.log(`--- ${url} after ${settleSeconds}s ---`);
console.log(`page state: ${state.result.value}`);
console.log(lines.length ? lines.join('\n') : '(console said nothing)');

if (problems.length) {
  console.log(`\n--- ${problems.length} problem(s) ---`);
  process.exit(1);
}
const { loading, canvases } = JSON.parse(state.result.value);
if (loading || canvases === 0) {
  console.log('\n--- the page never got as far as drawing ---');
  process.exit(1);
}
console.log('\nthe page came up');
process.exit(0);
