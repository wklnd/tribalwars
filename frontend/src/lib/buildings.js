import { useRef } from "react";
import { useNow } from "./ui";

/* Per-building metadata for the screens that mirror the original's
   screen=main / screen=<building> pages. Names, descriptions, requirements and
   cost curves are lifted from the original world config
   (htdocs/config/worlds/world/buildings.json) and its rendered
   BuildingMain.buildings JSON. `type` is our backend BuildingType (null = the
   backend does not model that building). */

export const B = {
  main: {
    name: "Village Headquarters",
    text: "In the village headquarters you can construct new buildings or upgrade existing ones. The higher the level of your headquarters, the faster the constructions will be finished. As soon as your Village Headquarters are upgraded to level 15, you will be able to demolish buildings in this village.",
    max: 30, start: 5, type: "HEADQUARTERS",
    req: {},
    wood: [90, 1.26], stone: [80, 1.275], iron: [70, 1.26],
    pop: [5, 1.17], time: 900,
    tiers: [[1, 1], [5, 2], [15, 3]],
  },
  barracks: {
    name: "Barracks",
    text: "In the barracks you can recruit infantry. The higher its level the faster the recruitment of troops will be finished.",
    max: 25, start: 0, type: "BARRACKS",
    req: { main: 3 },
    wood: [200, 1.26], stone: [170, 1.28], iron: [90, 1.26],
    pop: [7, 1.17], time: 1800,
    tiers: [[0, 1], [1, 1], [5, 2], [20, 3]],
  },
  stable: {
    name: "Stable",
    text: "In the stables you can recruit cavalry. The higher its level the faster the recruitment of the troops will be finished.",
    max: 20, start: 0, type: "STABLE",
    req: { main: 10, barracks: 5, smith: 5 },
    wood: [270, 1.26], stone: [240, 1.28], iron: [260, 1.26],
    pop: [8, 1.17], time: 6000,
    tiers: [[0, 1], [1, 1], [5, 2], [10, 3]],
  },
  garage: {
    name: "Workshop",
    text: "In the workshop you can produce rams and catapults. The higher its level the faster the recruitment will be finished.",
    max: 15, start: 0, type: "WORKSHOP",
    req: { main: 10, smith: 10 },
    wood: [300, 1.26], stone: [240, 1.28], iron: [260, 1.26],
    pop: [8, 1.17], time: 6000,
    tiers: [[0, 1], [1, 1], [5, 2], [10, 3]],
  },
  snob: {
    name: "Academy",
    text: "In the academy you can educate noblemen. They will help you conquer other villages.",
    max: 1, start: 0, type: "ACADEMY",
    req: { main: 20, smith: 20, market: 10 },
    wood: [15000, 2], stone: [25000, 2], iron: [10000, 2],
    pop: [80, 1.17], time: 586800,
    tiers: [[0, 1], [1, 1]],
  },
  smith: {
    name: "Smithy",
    text: "In the smithy you can research and improve weapons. Upgrading the smithy allows the research of better weapons and decreases the research time.",
    max: 20, start: 0, type: "SMITHY",
    req: { main: 5, barracks: 1 },
    wood: [220, 1.26], stone: [180, 1.275], iron: [240, 1.26],
    pop: [20, 1.17], time: 6000,
    tiers: [[0, 1], [1, 1], [5, 2], [15, 3]],
  },
  place: {
    name: "Rally point",
    text: "On the rally point your fighters meet. Here you can command your armies.",
    max: 1, start: 1, type: "RALLY_POINT",
    req: {},
    wood: [10, 1.26], stone: [40, 1.275], iron: [30, 1.26],
    pop: [0, 1.17], time: 10860,
    tiers: [[0, 1], [1, 1]],
  },
  statue: {
    name: "Statue",
    text: "At the statue the villagers render homage to your paladin. If your paladin dies you can appoint one of your fighters to become the new paladin.",
    max: 1, start: 0, type: "STATUE",
    req: {},
    wood: [220, 1.26], stone: [220, 1.275], iron: [220, 1.26],
    pop: [10, 1.17], time: 1500,
    tiers: [[0, 1], [1, 1]],
  },
  market: {
    name: "Market",
    text: "On the market you can trade with other players.",
    max: 25, start: 0, type: "MARKET",
    req: { main: 3, storage: 2 },
    wood: [100, 1.26], stone: [100, 1.275], iron: [100, 1.26],
    pop: [20, 1.17], time: 2700,
    tiers: [[0, 1], [1, 1], [5, 2], [20, 3]],
  },
  wood: {
    name: "Timber camp",
    text: "Outside of your village in the dark forests your lumberjacks cut massive trees to produce wood in the timber camp, which is needed for buildings and weapons. The higher its level the more wood is produced.",
    max: 30, start: 5, type: "TIMBER_CAMP",
    req: {},
    wood: [50, 1.25], stone: [60, 1.275], iron: [40, 1.245],
    pop: [5, 1.155], time: 900,
    tiers: [[0, 1], [1, 1], [10, 2], [20, 3]],
  },
  stone: {
    name: "Clay pit",
    text: "In the clay pit your workers extract clay, which is important for new buildings. The higher its level the more clay is produced.",
    max: 30, start: 5, type: "CLAY_PIT",
    req: {},
    wood: [65, 1.27], stone: [50, 1.265], iron: [40, 1.24],
    pop: [10, 1.14], time: 900,
    tiers: [[0, 1], [1, 1], [10, 2], [20, 3]],
  },
  iron: {
    name: "Iron mine",
    text: "In the iron mine your workers dig the war-crucial iron. The higher its level the more iron is produced.",
    max: 30, start: 5, type: "IRON_MINE",
    req: {},
    wood: [75, 1.252], stone: [65, 1.275], iron: [70, 1.24],
    pop: [10, 1.17], time: 1080,
    tiers: [[0, 1], [1, 1], [10, 2], [20, 3]],
  },
  farm: {
    name: "Farm",
    text: "The farm supplies your workers and troops with food. Without extending your farm your village cannot grow. The higher its level the more villagers can be supplied.",
    max: 30, start: 5, type: "FARM",
    req: {},
    wood: [45, 1.3], stone: [40, 1.32], iron: [30, 1.29],
    pop: [0, 1], time: 1200,
    tiers: [[1, 1], [10, 2], [20, 3]],
  },
  storage: {
    name: "Warehouse",
    text: "The warehouse stores your resources. The higher its level the more resources can be stored.",
    max: 30, start: 5, type: "WAREHOUSE",
    req: {},
    wood: [60, 1.265], stone: [50, 1.27], iron: [40, 1.245],
    pop: [0, 1.15], time: 1020,
    tiers: [[1, 1], [10, 2], [20, 3]],
  },
  hide: {
    name: "Hiding place",
    text: "Resources in your hiding place cannot be plundered by your enemies. The bigger it is, the more it can hold. There is no place to hide your troops though. Even your enemies' scouts cannot spot the resources hidden in your hiding place.",
    max: 10, start: 1, type: "HIDING_PLACE",
    req: {},
    wood: [50, 1.25], stone: [60, 1.25], iron: [50, 1.25],
    pop: [2, 1.17], time: 1800,
    tiers: [[0, 1], [1, 1]],
  },
  wall: {
    name: "Wall",
    text: "The wall defends your village against your enemies' troops. The higher its level the better the basic defense of your village. It also increases the defensive strength the troops stationed in the village.",
    max: 20, start: 0, type: "WALL",
    req: { barracks: 1 },
    wood: [50, 1.26], stone: [100, 1.275], iron: [20, 1.26],
    pop: [5, 1.17], time: 3600,
    tiers: [[0, 1], [1, 1], [5, 2], [15, 3]],
  },
};

/* Order in which the original lists buildings (church/watchtower are disabled on this world). */
export const ORDER = ["main", "barracks", "stable", "garage", "snob", "smith", "place", "statue", "market", "wood", "stone", "iron", "farm", "storage", "hide", "wall"];

export const ID_BY_TYPE = Object.fromEntries(ORDER.filter((id) => B[id].type).map((id) => [B[id].type, id]));

/* Screens that the original renders as the "page couldn't be found" box. */
export const MISSING_CLASS = { farm: "Farm", market: "Market", church: "Church", watchtower: "Watchtower" };

/* Accepts a backend type ("HEADQUARTERS") or an original screen id ("main"). */
export function resolveId(t) {
  if (!t) return null;
  if (ID_BY_TYPE[t]) return ID_BY_TYPE[t];
  const s = String(t).toLowerCase();
  if (B[s] || MISSING_CLASS[s] || s === "watchtower" || s === "church") return s;
  return null;
}

/* App routing key for a building. */
export function screenKey(id, level) {
  if (MISSING_CLASS[id]) return id === "farm" ? "building:FARM" : id;
  if (id === "place") return "place";
  // the original's screen=barracks|stable|garage is the recruitment screen once the building exists
  if ((id === "barracks" || id === "stable" || id === "garage") && level > 0) return id;
  return "building:" + (B[id].type ?? id);
}

export function levelOfId(village, id) {
  const b = B[id];
  if (!b) return 0;
  if (!b.type) return b.start;
  return (village.buildings ?? []).find((x) => x.type === b.type)?.level ?? 0;
}

export function queuedOfId(village, id) {
  const t = B[id]?.type;
  return t ? (village.buildQueue ?? []).filter((q) => q.type === t).length : 0;
}

export function tierFor(id, level) {
  let tier = 1;
  for (const [min, t] of B[id].tiers) if (level >= min) tier = t;
  return tier;
}

export const midImg = (id, level, grey) => `/graphic/buildings/mid/${grey ? "grey/" : ""}${id}${tierFor(id, level)}.png`;
export const bigImg = (id, level) => `/graphic/big_buildings/${id}${tierFor(id, level)}.png`;

const cost = ([base, factor], level) => Math.round(base * Math.pow(factor, level - 1));

/* Resources / population / seconds to build `target`. */
export function costAt(id, target, hqLevel) {
  const b = B[id];
  const pop = target <= 1 ? cost(b.pop, 1) : cost(b.pop, target) - cost(b.pop, target - 1);
  const raw = b.time * Math.pow(1.2, target - 1);
  return {
    wood: cost(b.wood, target),
    stone: cost(b.stone, target),
    iron: cost(b.iron, target),
    pop,
    seconds: Math.max(5, Math.round(raw / Math.pow(1.05, hqLevel))),
  };
}

/* What the next construction of `id` looks like (level counts orders already queued). */
export function buildInfo(village, id) {
  const b = B[id];
  const level = levelOfId(village, id);
  const queued = queuedOfId(village, id);
  const lvl = level + queued;
  const dto = b.type ? (village.buildings ?? []).find((x) => x.type === b.type) : null;
  const maxed = dto ? dto.maxedOrQueued : lvl >= b.max;
  const hq = levelOfId(village, "main");
  const c = costAt(id, lvl + 1, hq);
  if (dto && !maxed) {
    c.wood = dto.nextWood;
    c.stone = dto.nextClay;
    c.iron = dto.nextIron;
    c.seconds = dto.nextSeconds;
    if (dto.nextPop != null) c.pop = dto.nextPop;
  }
  return { level, queued, lvl, maxed, ...c };
}

export function reqsMet(village, id) {
  return Object.entries(B[id].req).every(([r, n]) => levelOfId(village, r) >= n);
}

/* Resources as they are right now (backend snapshot + production since it arrived). */
export function useLiveRes(village) {
  const now = useNow();
  const ref = useRef({ v: null, t: 0 });
  if (ref.current.v !== village) ref.current = { v: village, t: Date.now() };
  const hours = Math.max(0, now - ref.current.t) / 3600000;
  const cap = village.warehouseCapacity;
  const grow = (amount, rate) => (amount >= cap ? amount : Math.min(cap, amount + rate * hours));
  return {
    now,
    wood: grow(village.wood, village.woodPerHour),
    stone: grow(village.clay, village.clayPerHour),
    iron: grow(village.iron, village.ironPerHour),
    rate: { wood: village.woodPerHour, stone: village.clayPerHour, iron: village.ironPerHour },
  };
}

/* Port of Format.date() from the original game.js. */
export function formatWhen(ms) {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, "0");
  const time = `${p(d.getHours())}:${p(d.getMinutes())}`;
  const today = new Date();
  const tomorrow = new Date(today);
  tomorrow.setDate(today.getDate() + 1);
  if (today.getDate() === d.getDate()) return `today at ${time}`;
  if (tomorrow.getDate() === d.getDate()) return `tomorrow at ${time}`;
  return `on ${p(d.getDate())}.${p(d.getMonth() + 1)}. at ${time}`;
}

/* Port of BuildingMain.updateBuildableState(): { ok: true } or { ok: false, timer?, text }. */
export function buildState(village, id, info, live) {
  const b = B[id];
  // Buildings the backend does not model look buildable; clicking answers with the original's server error.
  if (!b.type) return { ok: true };
  if (info.pop > village.populationCapacity - village.populationUsed) return { ok: false, text: "Farm too small" };
  const enough = live.wood >= info.wood && live.stone >= info.stone && live.iron >= info.iron;
  if (enough) return { ok: true };
  const cap = village.warehouseCapacity;
  if (info.wood > cap || info.stone > cap || info.iron > cap) return { ok: false, text: "Warehouse too small" };
  const secs = Math.max(
    (info.wood - live.wood) / (live.rate.wood / 3600),
    (info.stone - live.stone) / (live.rate.stone / 3600),
    (info.iron - live.iron) / (live.rate.iron / 3600),
  );
  if (secs <= 120) return { ok: false, text: "Resources available ", timer: Math.max(0, Math.round(secs)) };
  return { ok: false, text: `Resources available ${formatWhen(live.now + secs * 1000)}` };
}

/* ---- Small ports of UI.SuccessMessage / UI.ErrorMessage / UI.ConfirmationBox (game.js) ---- */

export function showMessage(message, cls) {
  document.querySelectorAll(".autoHideBox").forEach((el) => el.remove());
  const box = document.createElement("div");
  box.className = cls ? "autoHideBox " + cls : "autoHideBox";
  box.innerHTML = "<p></p>";
  box.firstChild.textContent = message;
  box.addEventListener("click", () => box.remove());
  document.body.appendChild(box);
  setTimeout(() => {
    box.style.transition = "opacity 0.6s";
    box.style.opacity = "0";
    setTimeout(() => box.remove(), 650);
  }, 2000);
}

export function confirmBox(message, onConfirm) {
  document.getElementById("fader")?.remove();
  const fader = document.createElement("div");
  fader.id = "fader";
  fader.style.zIndex = "14999";
  fader.innerHTML =
    "<div class='confirmation-box' id='confirmation-box' role='dialog' aria-labelledby='confirmation-msg'><div>" +
    "<p id='confirmation-msg' class='confirmation-msg'></p><div class='confirmation-buttons'></div></div></div>";
  fader.querySelector("#confirmation-msg").textContent = message;
  const buttons = fader.querySelector(".confirmation-buttons");
  const close = () => {
    fader.remove();
    document.removeEventListener("keydown", onKey);
  };
  const add = (text, cls, cb) => {
    const btn = document.createElement("button");
    btn.className = "btn btn-default " + cls;
    btn.textContent = text;
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      close();
      cb();
    });
    buttons.appendChild(btn);
    return btn;
  };
  const onKey = (e) => e.key === "Escape" && close();
  add("Confirm", "evt-confirm-btn btn-confirm-yes", onConfirm);
  add("Cancellation", "evt-cancel-btn btn-confirm-no", () => {});
  document.addEventListener("keydown", onKey);
  document.body.appendChild(fader);
  buttons.firstChild.focus();
}

/* Wall stats (world basicDefense 20; +3.7% troop strength per level, classic TW formula). */
export function wallStats(level) {
  return { basic: 20 * level, bonus: Math.round((Math.pow(1.037, level) - 1) * 100) };
}

/* Hiding place capacity at a level (world config: 150 * 1.3335^(level-1)). */
export const hideCapacity = (level) => Math.round(150 * Math.pow(1.333500530983, level - 1));

/* Resource pit production per hour at a level (same curve as the backend BuildingType). */
export const pitProduction = (level) => (level <= 0 ? 5 : Math.round(30 * Math.pow(1.1631180425543, level - 1)));

/* Warehouse capacity at a level (backend curve). */
export const warehouseCapacityAt = (level) => Math.round(1000 * Math.pow(1.2294934136946, Math.max(level, 1) - 1));

/* ------------------------------------------------------------------------------------------
   Smithy / statue / academy data (config/worlds/world/units.json + world.json, en.json).
   ------------------------------------------------------------------------------------------ */

/* Player-facing name of a backend building type (HIDING_PLACE -> "Hiding place"). */
export const buildingTypeName = (type) => Object.values(B).find((b) => b.type === type)?.name ?? type;

/* Backend enum name of a smithy technology id (village.research[].type). */
export const techType = (id) => (id === "spy" ? "SCOUT" : id.toUpperCase());

/* The village's research entry of a technology id, or undefined for spear/sword (always known). */
export const researchOf = (village, id) => (village.research ?? []).find((r) => r.type === techType(id));

/* Order of the smithy's technology table (`$units`): units whose units.json `research` is not false.
   min/max = research.min / research.max, cost/time = research["1"], req = units.json `requirements`
   (spy/light/marcher/heavy/ram/catapult additionally need their recruit building, which the config
   lists in `requirements` too). */
export const SMITH_TECH = [
  { id: "spear", min: 1, max: 1, req: {}, time: 3960 },
  { id: "sword", min: 1, max: 1, req: { smith: 1 }, time: 5940 },
  { id: "axe", min: 0, max: 1, req: { smith: 2 }, time: 6930, wood: 700, stone: 840, iron: 820 },
  { id: "archer", min: 0, max: 1, req: { smith: 5, barracks: 5 }, time: 7950, wood: 640, stone: 560, iron: 740 },
  { id: "spy", min: 0, max: 1, req: { stable: 1 }, time: 3960, wood: 560, stone: 480, iron: 480 },
  { id: "light", min: 0, max: 1, req: { stable: 3 }, time: 8910, wood: 2200, stone: 2400, iron: 2000 },
  { id: "marcher", min: 0, max: 1, req: { stable: 5 }, time: 9900, wood: 3000, stone: 2400, iron: 2000 },
  { id: "heavy", min: 0, max: 1, req: { stable: 10, smith: 15 }, time: 9900, wood: 3000, stone: 2400, iron: 2000 },
  { id: "ram", min: 0, max: 1, req: { garage: 1 }, time: 7910, wood: 1200, stone: 1600, iron: 800 },
  { id: "catapult", min: 0, max: 1, req: { garage: 2, smith: 12 }, time: 9900, wood: 1600, stone: 2000, iron: 1200 },
];

/* Research duration in seconds at a smithy level (the smithy shortens it like the headquarters shortens construction,
   world buildMainFactor 1.05); the backend sends the exact figure (village.research[].seconds, world speed included). */
export const researchSeconds = (tech, smithLevel) => Math.max(1, Math.round(tech.time / Math.pow(1.05, smithLevel)));

/* What the smithy row of a unit looks like: { level, cross, grey, error?, costs? }.
   Older backends without research (village.research missing) count every unit as researched. */
export function techState(village, tech) {
  const reqMet = Object.entries(tech.req).every(([r, n]) => levelOfId(village, r) >= n);
  const entry = researchOf(village, tech.id);
  const queued = (village.researchQueue ?? []).some((q) => q.type === techType(tech.id));
  const researched = tech.min >= 1 || (village.research ? !!entry?.researched : reqMet);
  const level = researched || queued ? tech.max : tech.min;
  const state = { level, cross: !reqMet, grey: reqMet && level === 0 };
  if (level >= tech.max) state.error = "Technology fully researched";
  else if (!reqMet) state.error = "Building requirements unmet";
  else state.costs = { wood: entry?.wood ?? tech.wood, stone: entry?.clay ?? tech.stone, iron: entry?.iron ?? tech.iron };
  return state;
}

/* ---- Statue (world knightActive / knightItems "simple") ---- */
export const KNIGHT = { name: "Paladin", wood: 20, stone: 20, iron: 20, pop: 10, time: 21600 };

/* ---- Academy (world noblemanSystem "coins") ---- */
/* Cost of one gold coin (world noblemanRes) and of the nobleman unit (units.json). */
export const COIN_COST = { wood: 28000, stone: 30000, iron: 25000 };
export const SNOB_UNIT = { population: 100, wood: 40000, stone: 50000, iron: 50000, time: 18000 };
/* Recruit time of a unit at its building level: units.json time * 0.48 / 1.06^level (seen in the original's unit popup data). */
export const nobleRecruitSeconds = (time, level) => Math.round((time * 0.48) / Math.pow(1.06, level));

/* Reads the sub-mode of a building screen from the location hash ("snob:coin" or "building:ACADEMY:coin"). */
export function modeFromHash(id) {
  try {
    const p = decodeURIComponent(window.location.hash.slice(1)).split(":");
    if (p[0] === id) return p[1];
    if (p[0] === "building") return p[2];
  } catch {
    /* ignore */
  }
  return undefined;
}
