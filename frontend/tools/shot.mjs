#!/usr/bin/env node
// Real screenshot of a page in headless Chromium (Playwright's chrome-headless-shell), via the DevTools protocol.
//   node tools/shot.mjs http://100.104.217.16:5173/#overview /tmp/shots/overview.png [--login] [--world 1] [--wait 2500] [--w 1280] [--h 1000] [--full] [--click selector]
//   node tools/shot.mjs "http://127.0.0.1:8090/world/game.php?village=2&screen=overview" /tmp/shots/real.png --cookies /tmp/twlan_cookies.txt
// --ls k=v   seeds localStorage (repeatable); --eval <js>  runs JS (awaited) after the page settled, before --click
// --login   logs in as the dev account (see tools/auth.mjs) by seeding localStorage before the page loads
// Then open the PNG with the Read tool to look at it.
import { spawn } from 'child_process'
import { readdirSync, readFileSync, writeFileSync, mkdtempSync, existsSync } from 'fs'
import { tmpdir, homedir } from 'os'
import { join } from 'path'
import { devSession } from './auth.mjs'

const args = process.argv.slice(2)
const [url, out] = args
const opt = (n, d) => { const i = args.indexOf('--' + n); return i >= 0 ? args[i + 1] : d }
if (!url || !out) { console.error('usage: node tools/shot.mjs <url> <out.png> [--login] [--world N] [--wait ms] [--w px] [--h px] [--full] [--click selector] [--cookies file]'); process.exit(2) }
const W = Number(opt('w', 1280)), H = Number(opt('h', 1000)), WAIT = Number(opt('wait', 2500)), full = args.includes('--full')

const root = join(homedir(), '.cache/ms-playwright')
const shellDir = readdirSync(root).filter(d => d.startsWith('chromium_headless_shell')).sort().pop()
const bin = join(root, shellDir, 'chrome-headless-shell-linux64/chrome-headless-shell')
const port = 9300 + Math.floor(Math.random() * 500)
const proc = spawn(bin, ['--no-sandbox', '--disable-gpu', `--remote-debugging-port=${port}`, `--user-data-dir=${mkdtempSync(join(tmpdir(), 'shot-'))}`, `--window-size=${W},${H}`, 'about:blank'], { stdio: 'ignore' })
const sleep = (ms) => new Promise(r => setTimeout(r, ms))
let list
for (let i = 0; i < 40; i++) { try { list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json(); if (list.length) break } catch {} await sleep(150) }
const ws = new WebSocket(list.find(t => t.type === 'page').webSocketDebuggerUrl)
await new Promise(r => (ws.onopen = r))
let id = 0; const pending = new Map()
ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pending.has(d.id)) { pending.get(d.id)(d.result ?? d.error); pending.delete(d.id) } }
const send = (method, params = {}) => new Promise(r => { const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params })) })

await send('Page.enable')
await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false })
if (args.includes('--login')) {
  const s = await devSession(Number(opt('world', 1)) || 1)
  if (!s) { console.error('dev login failed'); proc.kill(); process.exit(3) }
  const world = opt('world', '1') === 'none' ? '' : `localStorage.setItem('twlan_world','${s.worldId}');`
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `try{localStorage.setItem('twlan_token','${s.token}');${world}}catch(e){}` })
}
const cookies = opt('cookies')
if (cookies && existsSync(cookies)) {
  const u = new URL(url)
  for (const line of readFileSync(cookies, 'utf8').split('\n')) {
    const p = line.replace(/^#HttpOnly_/, '').split('\t'); if (p.length >= 7) await send('Network.setCookie', { name: p[5], value: p[6].trim(), domain: u.hostname, path: '/' })
  }
}
const lsSeeds = args.flatMap((a, i) => (a === '--ls' ? [args[i + 1]] : []))
if (lsSeeds.length) {
  const src = lsSeeds.map(kv => { const i = kv.indexOf('='); return `localStorage.setItem(${JSON.stringify(kv.slice(0, i))},${JSON.stringify(kv.slice(i + 1))});` }).join('')
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `try{${src}}catch(e){}` })
}
await send('Page.navigate', { url })
await sleep(WAIT)
const evalJs = opt('eval')
if (evalJs) {
  const r = await send('Runtime.evaluate', { expression: evalJs, awaitPromise: true, returnByValue: true })
  if (r?.result?.value !== undefined) console.log('eval ->', JSON.stringify(r.result.value))
  await sleep(700)
}
// --click <css selector>: click that element (once the page has settled) before the screenshot
const clickSel = opt('click')
if (clickSel) {
  await send('Runtime.evaluate', { expression: `document.querySelector(${JSON.stringify(clickSel)})?.click()` })
  await sleep(700)
}
let clip
if (full) { const m = await send('Page.getLayoutMetrics'); clip = { x: 0, y: 0, width: W, height: Math.min(6000, Math.ceil(m.cssContentSize.height)), scale: 1 } }
const shot = await send('Page.captureScreenshot', { format: 'png', ...(clip ? { clip, captureBeyondViewport: true } : {}) })
writeFileSync(out, Buffer.from(shot.data, 'base64'))
console.log('saved', out)
ws.close(); proc.kill(); process.exit(0)
