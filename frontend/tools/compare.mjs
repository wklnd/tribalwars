#!/usr/bin/env node
// Structural diff between the ORIGINAL game's rendered page and our React screen.
//
//   node tools/compare.mjs --real "screen=main" --ours "building:HEADQUARTERS"
//   node tools/compare.mjs --real "screen=overview_villages&mode=prod" --ours "overview_villages:prod" --max 80
//   node tools/compare.mjs --scope page --real "screen=overview" --ours overview      (whole page chrome)
//   node tools/compare.mjs --real-file /tmp/saved.html --ours place                  (use a saved real HTML file)
//
// Needs: original game on 127.0.0.1:8090 with session cookie in /tmp/twlan_cookies.txt
//        (see tools/start_original.sh), our backend on :8080.
// Text is normalised: digits -> #, village/player names masked, whitespace collapsed,
// hrefs / handlers / scripts ignored. Skeletons are written to /tmp/compare/.
import { createRequire } from 'module'
import { execFileSync } from 'child_process'
import { readFileSync, writeFileSync, mkdirSync } from 'fs'
import { JSDOM } from 'jsdom'
import { devSession, seedStorage } from './auth.mjs'

const args = process.argv.slice(2)
const opt = (n, d) => { const i = args.indexOf('--' + n); return i >= 0 ? args[i + 1] : d }
const realQuery = opt('real'), ours = opt('ours', 'overview'), scope = opt('scope', 'content')
const realFile = opt('real-file'), max = Number(opt('max', 120)), name = opt('name', (realQuery || realFile || 'x').replace(/[^a-z0-9]+/gi, '_'))
if (!realQuery && !realFile) { console.error('need --real "screen=..." or --real-file'); process.exit(2) }

const KEEP_ATTR = { td: ['colspan', 'rowspan', 'width', 'align', 'valign'], th: ['colspan', 'rowspan', 'width', 'align', 'valign'], table: ['width', 'cellspacing', 'cellpadding', 'align'], col: ['width'], img: ['width', 'height'], input: ['type', 'name', 'size', 'checked'], form: ['method'], area: ['shape', 'coords'], select: ['name'], textarea: ['name', 'rows', 'cols'] }
const SKIP = new Set(['script', 'style', 'noscript'])
const maskText = (t) => t.replace(/\s+/g, ' ').trim()
  .replace(/Nilsen(&#039;|')s village/g, 'VILLAGE').replace(/\bHome\b/g, 'VILLAGE')
  .replace(/\bNilsen\b/g, 'PLAYER').replace(/\bPlayer\b/g, 'PLAYER')
  .replace(/\d+/g, '#')
function skeleton(root) {
  const out = []
  const walk = (el, depth) => {
    for (const n of el.childNodes) {
      if (n.nodeType === 3) { const t = maskText(n.textContent); if (t) out.push('  '.repeat(depth) + '"' + t + '"'); continue }
      if (n.nodeType !== 1) continue
      const tag = n.tagName.toLowerCase()
      if (SKIP.has(tag)) continue
      let s = tag
      if (n.id) s += '#' + n.id.replace(/\d+/g, '#')
      if (n.getAttribute('class')) s += '.' + n.getAttribute('class').trim().split(/\s+/).join('.')
      const extra = []
      for (const a of (KEEP_ATTR[tag] || [])) if (n.hasAttribute(a)) extra.push(a + '=' + n.getAttribute(a).replace(/\d+/g, '#'))
      if (tag === 'img' && n.getAttribute('src')) extra.push('src=' + n.getAttribute('src').split('/').pop().split('?')[0])
      const st = n.getAttribute('style')
      if (st && st.replace(/[\s;]/g, '') && !/display:none/.test(st.replace(/\s/g, ''))) extra.push('style=' + st.replace(/\s+/g, '').replace(/;$/, '').toLowerCase().replace(/\d+/g, '#'))
      if (st && /display:\s*none/.test(st)) extra.push('style=display:none')
      if (extra.length) s += ' [' + extra.join(' ') + ']'
      out.push('  '.repeat(depth) + '<' + s + '>')
      walk(n, depth + 1)
    }
  }
  walk(root, 0)
  return out
}
const pick = (doc) => scope === 'page' ? doc.body : doc.getElementById('content_value')

// ---- real page ----
let realHtml
if (realFile) realHtml = readFileSync(realFile, 'utf8')
else realHtml = execFileSync('curl', ['-s', '-b', '/tmp/twlan_cookies.txt', `http://127.0.0.1:8090/world/game.php?village=2&${realQuery}`], { maxBuffer: 50e6 }).toString('utf8')
const realDoc = new JSDOM(realHtml).window.document
if (!pick(realDoc)) { console.error('real page has no ' + (scope === 'page' ? 'body' : '#content_value') + ' (session expired or blocked?). First 200 chars:\n' + realHtml.slice(0, 200)); process.exit(3) }
const realSk = skeleton(pick(realDoc))

// ---- our page ----
const dom = new JSDOM('<!doctype html><body id="ds_body" class=" scrollableMenu"><div id="root"></div></body>', { url: 'http://localhost:5173/#' + encodeURIComponent(ours).replace(/%3A/g, ':'), pretendToBeVisual: true })
const session = await devSession()
seedStorage(dom.window, session)
for (const k of ['window', 'document', 'navigator', 'HTMLElement', 'Node', 'MutationObserver', 'localStorage']) {
  try { globalThis[k] = dom.window[k] } catch { Object.defineProperty(globalThis, k, { value: dom.window[k], configurable: true }) }
}
globalThis.IS_REACT_ACT_ENVIRONMENT = true
const realFetch = globalThis.fetch
globalThis.fetch = (u, o) => realFetch(String(u).startsWith('/') ? (process.env.TWLAN_BACKEND || 'http://localhost:8080') + u : u, o)
const { createServer } = await import('vite')
const vite = await createServer({ root: process.cwd(), server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
const React = await import('react')
const { createRoot } = await import('react-dom/client')
const App = (await vite.ssrLoadModule('/src/App.jsx')).default
const errors = []
console.error = (...a) => errors.push(a.map(String).join(' ').slice(0, 300))
const root = createRoot(dom.window.document.getElementById('root'))
await React.act(async () => { root.render(React.createElement(App)) })
for (let i = 0; i < 40 && !dom.window.document.getElementById('content_value'); i++) await React.act(async () => { await new Promise(r => setTimeout(r, 100)) })
await React.act(async () => { await new Promise(r => setTimeout(r, 150)) })
const oursEl = scope === 'page' ? dom.window.document.getElementById('root') : pick(dom.window.document)
if (!oursEl) { console.error('our page did not render (backend down?)'); await vite.close(); process.exit(4) }
const oursSk = skeleton(oursEl)

mkdirSync('/tmp/compare', { recursive: true })
writeFileSync(`/tmp/compare/${name}.real.txt`, realSk.join('\n') + '\n')
writeFileSync(`/tmp/compare/${name}.ours.txt`, oursSk.join('\n') + '\n')
let diffOut = ''
try { execFileSync('diff', ['-u', '--label', 'REAL', '--label', 'OURS', `/tmp/compare/${name}.real.txt`, `/tmp/compare/${name}.ours.txt`], { maxBuffer: 50e6 }) } catch (e) { diffOut = e.stdout ? e.stdout.toString() : '' }
const dl = diffOut.split('\n')
const changed = dl.filter(l => /^[+-][^+-]/.test(l)).length
console.log(`REAL ${realSk.length} lines | OURS ${oursSk.length} lines | differing lines: ${changed}   (files: /tmp/compare/${name}.{real,ours}.txt)`)
if (errors.length) console.log('React warnings/errors:', errors.slice(0, 5))
if (changed === 0) console.log('IDENTICAL after normalisation')
else console.log(dl.slice(0, max).join('\n') + (dl.length > max ? `\n... (${dl.length - max} more diff lines; raise --max)` : ''))
await vite.close(); process.exit(0)
