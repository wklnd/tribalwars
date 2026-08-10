#!/usr/bin/env node
// Logic checks for src/lib/live.js (the server-sent-events parser, reconnecting stream and refetch coalescing).
//   node tools/live-check.mjs
import assert from 'node:assert/strict'
import { createServer } from 'vite'

const server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, appType: 'custom', logLevel: 'silent' })
const { createParser, backoff, openLive, runCoalesced, emitLive } = await server.ssrLoadModule('/src/lib/live.js')

// --- parser: chunks may cut anywhere, endings may be LF / CRLF / CR, comments are heartbeats
const got = []
const p = createParser((e) => got.push(e))
for (const chunk of ['event: vil', 'lage\ndata: {"a"', ':1}\n', '\nevent: hello\r', '\ndata: x\r\n\r\n', ': ping\n\n', 'data: one\ndata: two\n\n', 'event: reports\rdata: r\r\r'])
  p.feed(chunk)
assert.deepEqual(got, [
  { event: 'village', data: '{"a":1}' },
  { event: 'hello', data: 'x' },
  { event: 'message', data: 'one\ntwo' },
], 'a lone CR at the very end waits: it may be the first half of a CRLF')
p.feed('event: map\ndata: m')
assert.deepEqual(got.at(-1), { event: 'reports', data: 'r' })
assert.equal(got.length, 4, 'an unfinished event is not delivered')
p.feed('\n\n'); assert.deepEqual(got.at(-1), { event: 'map', data: 'm' })

// --- backoff: 1 s, 2 s, 4 s, 8 s, then 10 s
assert.deepEqual([0, 1, 2, 3, 4, 9].map(backoff), [1000, 2000, 4000, 8000, 10000, 10000])

// --- runCoalesced: overlapping calls collapse into one more run of the newest function
const order = []
const slow = (name) => async () => { order.push('start ' + name); await new Promise((r) => setTimeout(r, 20)); order.push('end ' + name) }
await Promise.all([runCoalesced('k', slow('a')), runCoalesced('k', slow('b')), runCoalesced('k', slow('c')), runCoalesced('other', slow('x'))])
assert.deepEqual(order.filter((o) => / [abc]$/.test(o)), ['start a', 'end a', 'start c', 'end c'])
assert.ok(order.includes('start x'))

// --- openLive against a fake fetch: delivers events, reports the connection state, reconnects after the server closes
const enc = new TextEncoder()
let connects = 0
globalThis.fetch = async (url, opts) => {
  connects++
  assert.equal(url, '/api/events')
  const n = connects
  const body = new ReadableStream({
    start(c) {
      c.enqueue(enc.encode('event: hello\ndata: {}\n\n: ping\n\n'))
      c.enqueue(enc.encode(`event: village\ndata: {"n":${n}}\n\n`))
      if (n === 1) setTimeout(() => c.close(), 30) // the server goes away once
      opts.signal.addEventListener('abort', () => { try { c.error(new Error('aborted')) } catch {} })
    },
  })
  return { ok: true, status: 200, body }
}
const events = []
const states = []
const close = openLive((e) => events.push(e.event + ':' + e.data), (s) => states.push(s))
await new Promise((r) => setTimeout(r, 1300)) // first stream, closed after 30 ms, reconnect after 1 s
close()
await new Promise((r) => setTimeout(r, 50))
assert.ok(connects >= 2, 'reconnected: ' + connects)
assert.deepEqual(events.slice(0, 4), ['hello:{}', 'village:{"n":1}', 'hello:{}', 'village:{"n":2}'])
assert.deepEqual(states.slice(0, 3), [true, false, true])
const seen = connects
await new Promise((r) => setTimeout(r, 1200))
assert.equal(connects, seen, 'no reconnect after close()')

// --- refused stream (not logged in): retries with backoff instead of spinning
let tries = 0
globalThis.fetch = async () => { tries++; return { ok: false, status: 401, body: null } }
const close2 = openLive(() => {}, () => {})
await new Promise((r) => setTimeout(r, 1500))
close2()
assert.ok(tries >= 1 && tries <= 2, 'backs off: ' + tries)

emitLive('village') // (no listeners: must not throw)
await server.close()
console.log('live-check: ok')
