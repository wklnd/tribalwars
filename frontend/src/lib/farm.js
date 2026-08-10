/* Farm assistant helpers: premade armies ("templates" A, B, C), the target filters and which villages are worth a raid.
   The original TWLan has no such page. The setup is one object per world, {templates, settings, hidden}: it is read from
   the browser's localStorage (so the map's click menu can use it synchronously) and mirrored to the server
   (/api/farm), which is how it follows you to another browser. Sending itself goes through the normal attack command. */
import { api } from "../api";
import { ID_TO_TYPE, UNIT_LIST } from "./military";

export const TEMPLATE_KEYS = ["A", "B", "C"];
export const DEFAULT_SETTINGS = { maxDistance: 25, minPoints: 0, maxPoints: 0, npc: false }; // 0 = no limit

const configKey = (worldId) => `twlan.farm.config.${worldId ?? "x"}`;
const legacyKey = (name, worldId) => `twlan.farm.${name}.${worldId ?? "x"}`; // the first version kept three separate keys
function read(k, fallback) {
  try {
    return JSON.parse(window.localStorage.getItem(k)) ?? fallback;
  } catch {
    return fallback;
  }
}
function write(k, value) {
  try {
    window.localStorage.setItem(k, JSON.stringify(value));
  } catch {
    /* storage unavailable: the choice just isn't remembered here (the server copy still is) */
  }
}

/** { spear: 5, ... }: only known units with a whole number above zero */
export function cleanUnits(units) {
  const out = {};
  for (const u of UNIT_LIST) {
    const n = Math.floor(Number(units?.[u.id]));
    if (n > 0) out[u.id] = n;
  }
  return out;
}

/** Whatever was stored (or came from the server) -> a complete, clean setup. */
export function normalizeConfig(raw) {
  const hidden = Array.isArray(raw?.hidden) ? raw.hidden.filter((id) => Number.isInteger(id)) : [];
  return {
    templates: Object.fromEntries(TEMPLATE_KEYS.map((n) => [n, cleanUnits(raw?.templates?.[n])])),
    settings: { ...DEFAULT_SETTINGS, ...(raw?.settings ?? {}) },
    hidden,
  };
}

function readConfig(worldId) {
  const stored = read(configKey(worldId), null);
  if (stored) return normalizeConfig(stored);
  return normalizeConfig({
    templates: read(legacyKey("templates", worldId), {}),
    settings: read(legacyKey("settings", worldId), {}),
    hidden: read(legacyKey("hidden", worldId), []),
  });
}

const hasData = (c) =>
  TEMPLATE_KEYS.some((n) => Object.keys(c.templates[n]).length > 0) || c.hidden.length > 0
  || Object.keys(DEFAULT_SETTINGS).some((k) => c.settings[k] !== DEFAULT_SETTINGS[k]);

// ---- mirroring to the server ----
let pushTimer = null;
let changedAt = 0; // bumped by every local edit, so a slow server answer never overwrites a newer edit
const listeners = new Set();

function push(worldId) {
  clearTimeout(pushTimer);
  pushTimer = setTimeout(() => {
    api.saveFarm(JSON.stringify(readConfig(worldId))).catch(() => {
      /* offline or the backend is older: the setup stays in this browser */
    });
  }, 500);
}

function update(worldId, patch) {
  write(configKey(worldId), { ...readConfig(worldId), ...patch });
  changedAt++;
  push(worldId);
}

/** Called when the server's copy replaced the local one (the Farm Assistant page reloads its state). */
export function onFarmSynced(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/**
 * Pulls the setup of `worldId` from the server into the browser; when the server has none yet, this browser's setup
 * (if any) is uploaded, so the first device seeds it.
 */
export async function syncFarmConfig(worldId) {
  const before = changedAt;
  let server;
  try {
    server = await api.farm();
  } catch {
    return;
  }
  if (changedAt !== before) return; // edited while waiting: that edit is already on its way to the server
  if (server?.config) {
    try {
      write(configKey(worldId), normalizeConfig(JSON.parse(server.config)));
      listeners.forEach((fn) => fn());
    } catch {
      /* a copy this version cannot read: keep the local one */
    }
  } else if (hasData(readConfig(worldId))) {
    push(worldId);
  }
}

export const loadTemplates = (worldId) => readConfig(worldId).templates;
export const saveTemplates = (worldId, templates) => update(worldId, { templates });
export const loadSettings = (worldId) => readConfig(worldId).settings;
export const saveSettings = (worldId, settings) => update(worldId, { settings });
export const loadHidden = (worldId) => readConfig(worldId).hidden;
export const saveHidden = (worldId, hidden) => update(worldId, { hidden });

/** template ({unitId: n}) -> the backend's {SPEAR: n} */
export function toBackendUnits(template) {
  return Object.fromEntries(Object.entries(template).map(([id, n]) => [ID_TO_TYPE[id], n]));
}

/** null when the template can be sent from `home` ({unitId: n}), else the reason */
export function templateProblem(template, home) {
  const ids = Object.keys(template);
  if (ids.length === 0) return "This template is empty";
  const short = ids.find((id) => (home[id] ?? 0) < template[id]);
  return short ? "Not enough units at home" : null;
}

/**
 * Villages worth a raid from `village`, nearest first: barbarian villages, plus villages of computer players when
 * `npc` is set. Never the player's own, hidden ones or ones outside the distance / points limits.
 */
export function farmTargets(village, villages, settings, hidden) {
  const own = new Set([village.id, ...(village.myVillages ?? []).map((v) => v.id)]);
  const hide = new Set(hidden);
  return villages
    .filter((v) => !own.has(v.id) && !hide.has(v.id) && (v.ownerType === "BARBARIAN" || (settings.npc && v.ownerNpc)))
    .map((v) => ({ ...v, distance: Math.hypot(v.x - village.x, v.y - village.y) }))
    .filter((v) => (settings.maxDistance > 0 ? v.distance <= settings.maxDistance : true)
      && v.points >= (settings.minPoints || 0) && (!settings.maxPoints || v.points <= settings.maxPoints))
    .sort((a, b) => a.distance - b.distance || a.id - b.id);
}

// the colour of a report's dot in the report list (green: won without losses, yellow: won with losses, red: lost)
export function reportDot(r) {
  if (r.outcome !== "ATTACKER_WIN") return "red";
  const lost = Object.values(r.attackerLosses ?? {}).reduce((a, b) => a + b, 0);
  return lost > 0 ? "yellow" : "green";
}

/**
 * What the player's own reports say about each village: the latest battle ({report, loot, full, wall}) and the
 * latest scouting. Reports from before village ids were recorded cannot be matched and are skipped.
 */
export function lastReports(reports) {
  const out = new Map();
  for (const r of [...reports].sort((a, b) => new Date(a.occurredAt) - new Date(b.occurredAt))) {
    if (r.defenderView || r.defenderVillageId == null) continue;
    const entry = out.get(r.defenderVillageId) ?? {};
    if (r.spy) {
      entry.spy = r;
      if (r.spy.level >= 2 && r.spy.buildings?.WALL != null) entry.wall = r.spy.buildings.WALL;
    } else {
      const loot = Math.round(r.lootWood + r.lootClay + r.lootIron);
      entry.battle = { report: r, loot, full: r.lootCapacity != null && r.lootCapacity > 0 && loot >= r.lootCapacity };
      if (r.wallAfter != null) entry.wall = r.wallAfter;
    }
    out.set(r.defenderVillageId, entry);
  }
  return out;
}

/** "5 min ago", "3 h ago", "2 d ago" */
export function ago(time, now = Date.now()) {
  const s = Math.max(0, Math.floor((now - new Date(time).getTime()) / 1000));
  if (s < 90) return "just now";
  if (s < 5400) return `${Math.round(s / 60)} min ago`;
  if (s < 129600) return `${Math.round(s / 3600)} h ago`;
  return `${Math.round(s / 86400)} d ago`;
}
