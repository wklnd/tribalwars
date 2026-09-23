// Data + helpers for the village overview / page chrome.
// All building data below is taken from the original game's own config
// (TWLan-linux64/htdocs/config/worlds/world/buildings.json) and language file (en.json).

// Order == the original's `$this->world->buildings->getAll()` (church / church_f are disabled in this world).
// `type` is the backend building type (null = the backend has no such building; it is shown at `fixedLevel`).
export const SCENE = [
  { id: "main", name: "Village Headquarters", type: "HEADQUARTERS", screen: "building:HEADQUARTERS",
    shape: "373,187,417,129,407,72,329,65,306,99,311,150",
    tiers: { 1: "1", 5: "2", 15: "3" }, misc: { p_main_flag: { 1: "mainflag1.gif", 5: "mainflag2.gif", 15: "mainflag3.gif" } }, active: {},
    pop: [5, 1.17] },
  { id: "barracks", name: "Barracks", type: "BARRACKS", screen: "barracks",
    shape: "392,289,444,313,506,283,481,235,442,216,392,252",
    tiers: { 1: "1", 5: "2", 20: "3" }, misc: {}, active: {}, pop: [7, 1.17] },
  { id: "stable", name: "Stable", type: "STABLE", screen: "stable",
    shape: "64,241,70,265,150,307,189,289,184,232,99,202",
    tiers: { 1: "1", 5: "2", 10: "3" }, misc: {}, active: {}, pop: [8, 1.17] },
  { id: "garage", name: "Workshop", type: "WORKSHOP", screen: "garage",
    shape: "284,358,362,361,402,321,369,283,346,278,291,320",
    tiers: { 1: "1", 5: "2", 10: "3" }, misc: {}, active: {}, pop: [8, 1.17] },
  { id: "snob", name: "Academy", type: "ACADEMY", screen: "snob",
    shape: "206,149,257,125,229,60,185,80,156,111",
    tiers: { 1: "1" }, misc: {}, active: {}, pop: [80, 1.17] },
  { id: "smith", name: "Smithy", type: "SMITHY", screen: "smith",
    shape: "174,335,222,361,271,342,283,301,216,262",
    tiers: { 1: "1", 5: "2", 15: "3" }, misc: {}, active: {}, pop: [20, 1.17] },
  { id: "place", name: "Rally point", type: "RALLY_POINT", fixedLevel: 1, screen: "place",
    shape: "315,271,379,275,401,229,375,206,343,207",
    tiers: { 1: "1" }, misc: {}, active: {}, pop: [0, 1.17] },
  { id: "statue", name: "Statue", type: "STATUE", screen: "statue",
    shape: "277,231,256,265,266,285,292,287,306,266",
    tiers: { 1: "1" }, misc: {}, active: {}, pop: [10, 1.17] },
  { id: "market", name: "Market", type: "MARKET", screen: "market",
    shape: "214,149,234,228,313,230,330,169,273,122",
    tiers: { 1: "1", 5: "2", 20: "3" }, misc: {}, active: {}, pop: [20, 1.17] },
  { id: "wood", name: "Timber camp", type: "TIMBER_CAMP", screen: "building:TIMBER_CAMP",
    shape: "472,379,523,417,583,373,528,330",
    tiers: { 1: "1", 10: "2", 20: "3" }, misc: {}, active: {}, pop: [5, 1.155] },
  { id: "stone", name: "Clay pit", type: "CLAY_PIT", screen: "building:CLAY_PIT",
    shape: "34,300,0,349,15,399,67,417,91,402,92,341",
    tiers: { 1: "1", 10: "2", 20: "3" }, misc: {}, active: {}, pop: [10, 1.14] },
  { id: "iron", name: "Iron mine", type: "IRON_MINE", screen: "building:IRON_MINE",
    shape: "0,55,45,90,93,58,89,6,39,9",
    tiers: { 1: "1", 10: "2", 20: "3" }, misc: {}, active: {}, pop: [10, 1.17] },
  { id: "farm", name: "Farm", type: "FARM", screen: "building:FARM",
    shape: "456,0,477,41,526,75,583,88,597,18,597,0",
    tiers: { 10: "2", 20: "3" }, misc: { p_farm_field: { 20: "farm3_field.png" } }, active: { npc_farmer: "farmer.gif" }, pop: [0, 1] },
  { id: "storage", name: "Warehouse", type: "WAREHOUSE", screen: "building:WAREHOUSE",
    shape: "96,192,153,218,195,215,193,148,133,121",
    tiers: { 10: "2", 20: "3" }, misc: {}, active: {}, pop: [0, 1.15] },
  { id: "hide", name: "Hiding place", type: "HIDING_PLACE", fixedLevel: 1, screen: "hide",
    shape: "241,80,261,113,294,93,268,63",
    tiers: { 1: "1" }, misc: {}, active: {}, pop: [2, 1.17] },
  { id: "wall", name: "Wall", type: "WALL", screen: "building:WALL",
    shape: "464,12,444,70,534,112,566,49",
    tiers: { 1: "1", 5: "2", 15: "3" }, misc: {}, active: {}, pop: [5, 1.17] },
];

// highest key <= level
function tierOf(map, level) {
  let out = null;
  for (const k of Object.keys(map).map(Number).sort((a, b) => a - b)) if (level >= k) out = map[k];
  return out;
}

// While something is being built the original swaps the tiers that have an animated variant to their .gif
// (observed on the real overview with a queued order; place1.gif exists but is not used).
const ANIMATED = {
  main: [1, 2, 3], wood: [1, 2, 3], stone: [1, 2, 3], iron: [1, 2, 3], farm: [2, 3], garage: [1, 2, 3], stable: [1, 2, 3], snob: [1],
};

// Mirrors `$building->getVisual($village)`: [[className, file], ...] (file relative to graphic/visual/)
export function visualImages(b, level, animated = false, queued = 0) {
  const out = [];
  // a not yet built building with an order in the queue shows its construction site (<id>0.gif)
  if (level <= 0) return queued > 0 ? [["p_" + b.id, `${b.id}0.gif`]] : out;
  for (const [cls, map] of Object.entries(b.misc)) {
    const f = tierOf(map, level);
    if (f) out.push([cls, f]);
  }
  const t = tierOf(b.tiers, level);
  if (t) out.push(["p_" + b.id, `${b.id}${t}.${animated && ANIMATED[b.id]?.includes(Number(t)) ? "gif" : "png"}`]);
  for (const [cls, file] of Object.entries(b.active)) out.push([cls, file]);
  return out;
}

export function bigImageFile(b, level) {
  return `${b.id}${tierOf(b.tiers, level) ?? "1"}.png`;
}

// { id: {level, queued} } incl. buildings the backend does not know about
export function sceneLevels(village) {
  const out = {};
  for (const b of SCENE) {
    const info = b.type ? (village.buildings ?? []).find((x) => x.type === b.type) : null;
    const level = info ? info.level : (b.fixedLevel ?? 0);
    const queued = b.type ? (village.buildQueue ?? []).filter((q) => q.type === b.type).length : 0;
    out[b.id] = { level, queued, info };
  }
  return out;
}

// `Text::absInt`: "+3" for a positive difference, "" for 0
export const absInt = (n) => (n > 0 ? " +" + n : "");

// `Text::formatInt(n, '.')` with the original's `<span class="grey">.</span>` delimiter -> array of nodes
export function greyInt(n, keyPrefix = "g") {
  const s = String(Math.round(n));
  const parts = [];
  for (let i = s.length; i > 0; i -= 3) parts.unshift(s.slice(Math.max(0, i - 3), i));
  const out = [];
  parts.forEach((p, i) => {
    if (i > 0) out.push({ grey: true, key: keyPrefix + i });
    out.push(p);
  });
  return out;
}

// production widget: world.prodToSeconds/prodToMinutes (defaults match the original's 600000/10000)
export function prodUnit(perHour, prodToSeconds = 600000, prodToMinutes = 10000) {
  if (perHour >= prodToSeconds) return { unit: "second", value: perHour / 3600 };
  if (perHour >= prodToMinutes) return { unit: "minute", value: perHour / 60 };
  return { unit: "hour", value: perHour };
}

// population cost of raising `b` to `nextLevel` (buildCosts.population base * factor^(level-1))
export function popCost(b, nextLevel) {
  return Math.round(b.pop[0] * b.pop[1] ** (nextLevel - 1));
}

const lsGet = (k, d) => {
  try {
    const v = window.localStorage.getItem(k);
    return v == null ? d : (JSON.parse(v) ?? d);
  } catch {
    return d;
  }
};
const lsSet = (k, v) => {
  try {
    window.localStorage.setItem(k, JSON.stringify(v));
  } catch {
    /* ignore */
  }
};
export { lsGet, lsSet };

// `TWLan::isNight` (nightStart 22:00 / nightEnd 08:00)
// (the server tells whether it is night in the world — `village.night`; this is only the fallback)
export function isNight(d = new Date()) {
  const h = d.getHours();
  return h >= 22 || h < 8;
}

// Default widget layout of the original (`$order`): the widgets that render nothing are not part of the page.
export const DEFAULT_ORDER = {
  leftcolumn: ["summary", "outgoingUnits", "incomingUnits"],
  rightcolumn: ["newbie", "prod", "units", "loyalty", "belief", "flags", "buildqueue", "groups", "notes"],
};

export const WIDGET_TITLES = {
  summary: "Buildings",
  outgoingUnits: "Your troops",
  incomingUnits: "Incoming troops",
  newbie: "Beginners protection",
  prod: "Production",
  units: "Units",
  loyalty: "loyalty",
  belief: "Belief",
  flags: "Flags",
  buildqueue: "Building queue",
  groups: "Group association",
  notes: "Notes",
};
