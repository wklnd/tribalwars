#!/usr/bin/env node
// Logic checks for src/lib/farm.js (the farm assistant's target list, templates and report history).
//   node tools/farm-check.mjs
import assert from 'node:assert/strict'
import { createServer } from 'vite'

const server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, appType: 'custom', logLevel: 'silent' })
const { contextButtons } = await server.ssrLoadModule('/src/lib/map.js')
const { normalizeConfig, DEFAULT_SETTINGS } = await server.ssrLoadModule('/src/lib/farm.js')
const { farmTargets, templateProblem, lastReports, cleanUnits, toBackendUnits, ago, reportDot } = await server.ssrLoadModule('/src/lib/farm.js')
const me = { id: 1, x: 500, y: 500, myVillages: [{ id: 1 }, { id: 2 }] }
const vs = [
  { id: 1, x: 500, y: 500, ownerType: 'PLAYER', points: 100 },
  { id: 2, x: 501, y: 500, ownerType: 'PLAYER', points: 100 },          // own second village: never a target
  { id: 3, x: 503, y: 504, ownerType: 'BARBARIAN', points: 50 },         // distance 5
  { id: 4, x: 510, y: 500, ownerType: 'BARBARIAN', points: 300 },        // distance 10, too many points below
  { id: 5, x: 500, y: 530, ownerType: 'BARBARIAN', points: 50 },         // distance 30 (over the limit)
  { id: 6, x: 502, y: 500, ownerType: 'PLAYER', ownerNpc: true, points: 80 },
  { id: 7, x: 498, y: 500, ownerType: 'PLAYER', ownerNpc: false, points: 80 }, // a real player: never
]
const base = { maxDistance: 25, minPoints: 0, maxPoints: 0, npc: false }
assert.deepEqual(farmTargets(me, vs, base, []).map(v => v.id), [3, 4])
assert.deepEqual(farmTargets(me, vs, { ...base, npc: true }, []).map(v => v.id), [6, 3, 4])
assert.deepEqual(farmTargets(me, vs, { ...base, maxPoints: 100 }, []).map(v => v.id), [3])
assert.deepEqual(farmTargets(me, vs, { ...base, maxDistance: 0 }, [3]).map(v => v.id), [4, 5])   // 0 = unlimited, 3 hidden
assert.equal(templateProblem({}, { spear: 5 }), 'This template is empty')
assert.equal(templateProblem({ spear: 6 }, { spear: 5 }), 'Not enough units at home')
assert.equal(templateProblem({ spear: 5 }, { spear: 5 }), null)
assert.deepEqual(cleanUnits({ spear: '7', sword: 0, bogus: 3, axe: -2, light: 2.9 }), { spear: 7, light: 2 })
assert.deepEqual(toBackendUnits({ spear: 3, spy: 1, knight: 1 }), { SPEAR: 3, SCOUT: 1, PALADIN: 1 })
const rep = (o) => ({ defenderView: false, outcome: 'ATTACKER_WIN', attackerLosses: {}, lootWood: 100, lootClay: 100, lootIron: 100, lootCapacity: 300, ...o })
const m = lastReports([
  rep({ id: 1, defenderVillageId: 9, occurredAt: '2026-09-21T10:00:00Z', wallAfter: 3, lootCapacity: 900 }),
  rep({ id: 2, defenderVillageId: 9, occurredAt: '2026-09-21T11:00:00Z', wallAfter: 2 }),
  rep({ id: 3, defenderVillageId: null, occurredAt: '2026-09-21T12:00:00Z' }),          // old report without ids: skipped
  rep({ id: 4, defenderVillageId: 9, defenderView: true, occurredAt: '2026-09-21T13:00:00Z' }), // somebody else's attack on us
])
assert.equal(m.size, 1)
assert.equal(m.get(9).battle.report.id, 2)     // the newest of the player's own battles
assert.equal(m.get(9).battle.full, true)       // 300 loot of 300 capacity
assert.equal(m.get(9).wall, 2)
assert.equal(reportDot(rep({ attackerLosses: { SPEAR: 1 } })), 'yellow')
assert.equal(reportDot(rep({ outcome: 'DEFENDER_WIN' })), 'red')
assert.equal(ago('2026-09-21T10:00:00Z', Date.parse('2026-09-21T10:30:00Z')), '30 min ago')

// the map's click ring: A/B on abandoned villages (in the original's profile/message slots), on computer players'
// villages only when they are farm targets, never on the player's own villages
const ids = (village, farm) => contextButtons(village, 1, new Set(), farm).map(b => b.id)
const at = (village, farm, id) => { const b = contextButtons(village, 1, new Set(), farm).find(x => x.id === id); return b && [b.dx, b.dy] }
const barb = { id: 9, owner: 0, points: 40 }
const npcV = { id: 8, owner: 2, npc: true, points: 90 }
const human = { id: 7, owner: 2, npc: false, points: 90 }
const mine = { id: 5, owner: 1, points: 90 }
const both = { a: true, b: true, npc: false }
assert.deepEqual(ids(barb, both).filter(i => i.startsWith('mp_farm')), ['mp_farm_a', 'mp_farm_b'])
assert.deepEqual(at(barb, both, 'mp_farm_a'), [20, -30])
assert.deepEqual(at(barb, both, 'mp_farm_b'), [20, 6])
assert.deepEqual(ids(barb, { a: false, b: true, npc: false }).filter(i => i.startsWith('mp_farm')), ['mp_farm_b'])
assert.deepEqual(ids(barb, null).filter(i => i.startsWith('mp_farm')), [])       // no template set
assert.deepEqual(ids(npcV, both).filter(i => i.startsWith('mp_farm')), [])       // npc villages are not targets by default
const npcOn = { a: true, b: true, npc: true }
assert.deepEqual(ids(npcV, npcOn).filter(i => i.startsWith('mp_farm')), ['mp_farm_a', 'mp_farm_b'])
assert.deepEqual(at(npcV, npcOn, 'mp_farm_a'), [-12, -49])                       // the free slot of the noble claim
assert.deepEqual(at(npcV, npcOn, 'mp_farm_b'), [20, 6])                          // where "write message" was
assert.equal(ids(npcV, npcOn).includes('mp_msg'), false)
assert.deepEqual(at(npcV, npcOn, 'mp_profile'), [20, -30])                        // no two buttons share a slot
const slots = contextButtons(npcV, 1, new Set(), npcOn).map(b => b.dx + ',' + b.dy)
assert.equal(new Set(slots).size, slots.length)
assert.deepEqual(ids(human, npcOn).filter(i => i.startsWith('mp_farm')), [])      // real players are never farm targets
assert.deepEqual(ids(mine, npcOn).filter(i => i.startsWith('mp_farm')), [])

// what the server (or an older browser copy) hands back is cleaned into a complete setup
assert.deepEqual(normalizeConfig(null), { templates: { A: {}, B: {}, C: {} }, settings: DEFAULT_SETTINGS, hidden: [] })
assert.deepEqual(
  normalizeConfig({ templates: { A: { spear: '5', bogus: 3 }, D: { axe: 1 } }, settings: { npc: true }, hidden: [3, 'x', 4.5, 8] }),
  { templates: { A: { spear: 5 }, B: {}, C: {} }, settings: { ...DEFAULT_SETTINGS, npc: true }, hidden: [3, 8] })
console.log('farm.js checks passed')
await server.close()
