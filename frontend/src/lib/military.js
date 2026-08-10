// Shared data + helpers for the military screens (train / place / report).
// Unit numbers below are the ones the ORIGINAL game embeds in its rendered pages
// (UnitPopup.unit_data), so the unit popup shows exactly what the original shows.
import { useEffect, useState } from "react";
import { levelOf } from "./data";
import { recruitFactor } from "./bonus";

const UNIT_DATA = [
 {
  "name": "Spear fighter",
  "desc": "The spear fighter is the most basic unit. It is good in defending against cavalry and to start plundering other villages.",
  "wood": 50,
  "stone": 30,
  "iron": 10,
  "pop": 1,
  "speed": 0.00092592592592593,
  "attack": 10,
  "defense": 15,
  "defense_cavalry": 45,
  "defense_archer": 20,
  "carry": 25,
  "type": "infantry",
  "prod_building": "barracks",
  "build_time": 0.136,
  "id": "spear"
 },
 {
  "name": "Swordsman",
  "desc": "The swordsman is a relatively slow unit effective as defense especially against infantry.",
  "wood": 30,
  "stone": 30,
  "iron": 70,
  "pop": 1,
  "speed": 0.00075757575757576,
  "attack": 25,
  "defense": 50,
  "defense_cavalry": 15,
  "defense_archer": 40,
  "carry": 15,
  "type": "infantry",
  "prod_building": "barracks",
  "build_time": 0.2,
  "id": "sword"
 },
 {
  "name": "Axeman",
  "desc": "The axeman is a strong offensive unit. They easily forget to protect themselves though.",
  "wood": 60,
  "stone": 30,
  "iron": 40,
  "pop": 1,
  "speed": 0.00092592592592593,
  "attack": 40,
  "defense": 10,
  "defense_cavalry": 5,
  "defense_archer": 10,
  "carry": 10,
  "type": "infantry",
  "prod_building": "barracks",
  "build_time": 0.176,
  "id": "axe"
 },
 {
  "name": "Archer",
  "desc": "The archer is a very effective defensive unit. Their arrows destroy even the hardest armor.",
  "wood": 100,
  "stone": 30,
  "iron": 60,
  "pop": 1,
  "speed": 0.00092592592592593,
  "attack": 15,
  "defense": 50,
  "defense_cavalry": 40,
  "defense_archer": 5,
  "carry": 10,
  "type": "archer",
  "prod_building": "barracks",
  "build_time": 0.24,
  "id": "archer"
 },
 {
  "name": "Scout",
  "desc": "The scout creeps into your enemies' villages to get valuable information.",
  "wood": 50,
  "stone": 50,
  "iron": 20,
  "pop": 2,
  "speed": 0.0018518518518519,
  "attack": 0,
  "defense": 2,
  "defense_cavalry": 1,
  "defense_archer": 2,
  "carry": 0,
  "type": "other",
  "prod_building": "stable",
  "build_time": 0.12,
  "id": "spy"
 },
 {
  "name": "Light cavalry",
  "desc": "The light cavalry is a good offensive unit. One of its advantages is its speed.",
  "wood": 125,
  "stone": 100,
  "iron": 250,
  "pop": 4,
  "speed": 0.0016666666666667,
  "attack": 130,
  "defense": 30,
  "defense_cavalry": 40,
  "defense_archer": 30,
  "carry": 80,
  "type": "cavalry",
  "prod_building": "stable",
  "build_time": 0.24,
  "id": "light"
 },
 {
  "name": "Mounted archer",
  "desc": "The mounted archer is especially useful to disable your enemies archers on the Walls.",
  "wood": 250,
  "stone": 100,
  "iron": 150,
  "pop": 5,
  "speed": 0.0016666666666667,
  "attack": 120,
  "defense": 40,
  "defense_cavalry": 30,
  "defense_archer": 50,
  "carry": 50,
  "type": "archer",
  "prod_building": "stable",
  "build_time": 0.36,
  "id": "marcher"
 },
 {
  "name": "Heavy cavalry",
  "desc": "The heavy cavalry are your elite troops. The noble knights have hardened weapons and strong armor.",
  "wood": 200,
  "stone": 150,
  "iron": 600,
  "pop": 6,
  "speed": 0.0015151515151515,
  "attack": 150,
  "defense": 200,
  "defense_cavalry": 80,
  "defense_archer": 180,
  "carry": 50,
  "type": "cavalry",
  "prod_building": "stable",
  "build_time": 0.48,
  "id": "heavy"
 },
 {
  "name": "Ram",
  "desc": "Rams support your troops in your attacks as it damages your enemies' Wall.",
  "wood": 300,
  "stone": 200,
  "iron": 200,
  "pop": 5,
  "speed": 0.00055555555555556,
  "attack": 2,
  "defense": 20,
  "defense_cavalry": 50,
  "defense_archer": 20,
  "carry": 0,
  "type": "infantry",
  "prod_building": "garage",
  "build_time": 0.64,
  "id": "ram"
 },
 {
  "name": "Catapult",
  "desc": "Catapults are especially good in destroying your enemies' buildings.",
  "wood": 320,
  "stone": 400,
  "iron": 100,
  "pop": 8,
  "speed": 0.00055555555555556,
  "attack": 100,
  "defense": 100,
  "defense_cavalry": 50,
  "defense_archer": 100,
  "carry": 0,
  "type": "infantry",
  "prod_building": "garage",
  "build_time": 0.96,
  "id": "catapult"
 },
 {
  "name": "Paladin",
  "desc": "The noble paladin protects you and your allies' villages from enemy attacks. Each player may only have one paladin.",
  "wood": 20,
  "stone": 20,
  "iron": 20,
  "pop": 10,
  "speed": 0.0016666666666667,
  "attack": 150,
  "defense": 250,
  "defense_cavalry": 400,
  "defense_archer": 150,
  "carry": 100,
  "type": "infantry",
  "prod_building": "statue",
  "build_time": 2.88,
  "id": "knight"
 },
 {
  "name": "Nobleman",
  "desc": "A nobleman will reduce the loyalty of your enemies' villages. If its loyalty falls under 0 you conquer that village. The costs for each nobleman increase with every conquered village and every nobleman that is available or in production.",
  "wood": 40000,
  "stone": 50000,
  "iron": 50000,
  "pop": 100,
  "speed": 0.00047619047619048,
  "attack": 30,
  "defense": 100,
  "defense_cavalry": 50,
  "defense_archer": 100,
  "carry": 0,
  "type": "infantry",
  "prod_building": "snob",
  "build_time": 2.4,
  "id": "snob"
 }
];

const PLURAL = { spear: "Spear fighters" };
export const UNIT_LIST = UNIT_DATA.map((u) => ({ ...u, plural: PLURAL[u.id] ?? u.name }));
export const UNIT_BY_ID = Object.fromEntries(UNIT_LIST.map((u) => [u.id, u]));

// backend enum name <-> original unit id
export const TYPE_TO_ID = { SPEAR: "spear", SWORD: "sword", AXE: "axe", ARCHER: "archer", SCOUT: "spy", PALADIN: "knight", SNOB: "snob", LIGHT: "light", MARCHER: "marcher", HEAVY: "heavy", RAM: "ram", CATAPULT: "catapult" };
export const ID_TO_TYPE = Object.fromEntries(Object.entries(TYPE_TO_ID).map(([k, v]) => [v, k]));
// seconds per unit at barracks level 0 (backend UnitType.buildTimeSeconds)
export const BUILD_SECONDS = { spear: 1020, sword: 1500, axe: 1320, archer: 1800, spy: 900, knight: 21600, snob: 18000, light: 1800, marcher: 2700, heavy: 3600, ram: 4800, catapult: 7200 };

// columns of the rally point command form (original: $unitsColumns)
export const PLACE_COLUMNS = [
  ["spear", "sword", "axe", "archer"],
  ["spy", "light", "marcher", "heavy"],
  ["ram", "catapult"],
  ["knight", "snob"],
];

// Requirements the original lists for units it cannot recruit (rendered text of screen=train)
export const UNMET_REQS = {
  spear: [["barracks", "Barracks", 1, "barracks1.png"]],
  sword: [["smith", "Smithy", 1, "smith1.png"]],
  axe: [["smith", "Smithy", 2, "smith1.png"]],
  archer: [["smith", "Smithy", 5, "smith2.png"], ["barracks", "Barracks", 5, "barracks2.png"]],
  spy: [["stable", "Stable", 1, "stable1.png"]],
  light: [["stable", "Stable", 3, "stable1.png"]],
  marcher: [["stable", "Stable", 5, "stable2.png"]],
  heavy: [["stable", "Stable", 10, "stable3.png"], ["smith", "Smithy", 15, "smith3.png"]],
  ram: [["garage", "Workshop", 1, "garage1.png"]],
  catapult: [["garage", "Workshop", 2, "garage1.png"], ["smith", "Smithy", 12, "smith2.png"]],
};

export function unitsAtHome(village) {
  const out = {};
  for (const u of UNIT_LIST) out[u.id] = 0;
  for (const [type, count] of Object.entries(village.units ?? {})) {
    const id = TYPE_TO_ID[type];
    if (id) out[id] = count;
  }
  return out;
}

// units currently away (outgoing attacks + returning troops)
export function unitsAway(village) {
  const out = {};
  for (const u of UNIT_LIST) out[u.id] = 0;
  for (const m of [...(village.outgoingMovements ?? []), ...(village.incomingMovements ?? [])]) {
    if (m.type === "SUPPORT_IN" || m.type === "INCOMING_ATTACK") continue; // on their way to this village, from another one of the player's
    for (const [type, count] of Object.entries(m.units ?? {})) {
      const id = TYPE_TO_ID[type];
      if (id) out[id] += count;
    }
  }
  return out;
}

// seconds a march takes from `from` to `to` (backend: MovementService.travelSeconds): minutes per field = 1 / (speed * 60),
// the slowest unit sets the pace, the world speed divides it
export function travelSeconds(from, to, unitIds, worldSpeed = 1) {
  const dist = Math.hypot(from.x - to.x, from.y - to.y);
  const slowest = Math.max(...unitIds.map((id) => Math.round(1 / (UNIT_BY_ID[id].speed * 60))));
  return Math.max(1, Math.round((dist * slowest * 60) / (worldSpeed || 1)));
}

// icon + text of one of the player's own commands (backend movement types ATTACKING / SUPPORTING / RETURNING / SUPPORT_IN)
export function commandInfo(m, nameOf) {
  const name = nameOf(m.otherVillageName);
  if (m.type === "SUPPORTING") return { icon: "support", label: `Support for ${name}` };
  if (m.type === "SUPPORT_IN") return { icon: "support", label: `Support from ${name}` };
  if (m.type === "INCOMING_ATTACK") return { icon: "attack", label: `Attack from ${name}` };
  if (m.type === "RETURNING") return { icon: "return", label: `Return of ${name}` };
  return { icon: "attack", label: `Attack on ${name}` };
}

export const continentOf = (v) => "K" + Math.floor(v.y / 100) + Math.floor(v.x / 100);
export const villageDisplayName = (v) => `${v.name} (${v.x}|${v.y}) ${continentOf(v)}`;

const pad2 = (n) => String(n).padStart(2, "0");
// Format.date(): "today at 12:34:56" / "tomorrow at ..." / "on 20.09. at ..."
export function fmtOnTime(target, showSeconds = true) {
  const d = target instanceof Date ? target : new Date(target);
  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(now.getDate() + 1);
  const time = `${pad2(d.getHours())}:${pad2(d.getMinutes())}${showSeconds ? ":" + pad2(d.getSeconds()) : ""}`;
  if (d.toDateString() === now.toDateString()) return `today at ${time}`;
  if (d.toDateString() === tomorrow.toDateString()) return `tomorrow at ${time}`;
  return `on ${pad2(d.getDate())}.${pad2(d.getMonth() + 1)}. at ${time}`;
}

// Format.buildTime(ms): "h:mm:ss" (days: "d:hh:mm:ss")
export function fmtBuildTime(ms) {
  const days = Math.floor(ms / 86400000);
  const hours = Math.floor(ms / 3600000) % 24;
  const minutes = Math.floor(ms / 60000) % 60;
  const seconds = Math.floor(ms / 1000) % 60;
  return (days > 0 ? days + ":" + pad2(hours) : String(hours)) + ":" + pad2(minutes) + ":" + pad2(seconds);
}

/* ---- location hash helpers: the App keeps its screen key ("train:decommission") in the URL hash ---- */
export function hashParts() {
  if (typeof window === "undefined") return [];
  return decodeURIComponent(window.location.hash.slice(1)).split(":");
}
export function setHashTarget(target) {
  window.history.replaceState(null, "", "#" + encodeURIComponent(target).replace(/%3A/g, ":"));
}
// re-render helper; returns [parts, navigate]
export function useHashNav(screenName, go) {
  const [, force] = useState(0);
  const parts = hashParts();
  const navigate = (e, target) => {
    if (e) e.preventDefault();
    if (go) go(null, target);
    else setHashTarget(target);
    force((n) => n + 1);
  };
  return [parts[0] === screenName ? parts.slice(1) : [], navigate];
}

// cross-screen navigation (App's go() when available, otherwise hash + reload)
export function navigateTo(e, target, go) {
  if (e) e.preventDefault();
  if (go) return go(null, target);
  setHashTarget(target);
  window.location.reload();
}

export function useNowTick(ms = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(id);
  }, [ms]);
  return now;
}

// Assign an inline style verbatim (React's style object would normalise "border:none" / "margin:4px 0 0 10px"
// into a different serialisation than the original's markup carries).
export const rawStyle = (css) => (el) => {
  if (el) el.setAttribute("style", css);
};

/* ---- recruitment (screen=train / screen=barracks|stable|garage) ----
   Per-unit recruit building + unit requirements (config/worlds/world/units.json: recruitBuilding,
   requirements, recruitCosts.time). Requirement keys are original building ids. */
export const UNIT_RECRUIT = {
  spear: { building: "barracks", time: 1020, req: {} },
  sword: { building: "barracks", time: 1500, req: { smith: 1 } },
  axe: { building: "barracks", time: 1320, req: { smith: 2 } },
  archer: { building: "barracks", time: 1800, req: { smith: 5, barracks: 5 } },
  spy: { building: "stable", time: 900, req: { stable: 1 } },
  light: { building: "stable", time: 1800, req: { stable: 3 } },
  marcher: { building: "stable", time: 2700, req: { stable: 5 } },
  heavy: { building: "stable", time: 3600, req: { stable: 10, smith: 15 } },
  ram: { building: "garage", time: 4800, req: { garage: 1 } },
  catapult: { building: "garage", time: 7200, req: { garage: 2, smith: 12 } },
  knight: { building: "statue", time: 21600, req: { statue: 1 } },
  snob: { building: "snob", time: 18000, req: { snob: 1 } },
};
// order the original lists them in (units recruited at the statue / academy are not part of screen=train)
export const RECRUIT_ORDER = ["spear", "sword", "axe", "archer", "spy", "light", "marcher", "heavy", "ram", "catapult"];
export const RECRUIT_BUILDINGS = ["barracks", "stable", "garage"];
export const BUILDING_UNITS = Object.fromEntries(
  RECRUIT_BUILDINGS.map((b) => [b, RECRUIT_ORDER.filter((id) => UNIT_RECRUIT[id].building === b)])
);

// original building id -> backend BuildingType
const REQ_TYPE = { barracks: "BARRACKS", stable: "STABLE", garage: "WORKSHOP", smith: "SMITHY" };
export const REQ_NAME = { barracks: "Barracks", stable: "Stable", garage: "Workshop", smith: "Smithy" };
// building image tiers (buildings.json img.level)
const REQ_TIERS = { barracks: [[5, 2], [20, 3]], stable: [[5, 2], [10, 3]], garage: [[5, 2], [10, 3]], smith: [[5, 2], [15, 3]] };
export const reqImage = (b, level) => `${b}${REQ_TIERS[b].reduce((t, [min, tier]) => (level >= min ? tier : t), 1)}.png`;
export const reqLevel = (village, b) => levelOf(village, REQ_TYPE[b]);

/* Unit.canBeRecruited(): every requirement of units.json is met by the village.
   (The original does not look at the recruit building's own level here - spear has no requirement at all.) */
export const requirementsMet = (village, id) =>
  Object.entries(UNIT_RECRUIT[id].req).every(([b, n]) => reqLevel(village, b) >= n) && isResearched(village, id);

/* Smithy research (village.research from the backend; spear/sword/paladin/nobleman need none, and an old backend
   without the list counts every unit as researched). */
export function isResearched(village, id) {
  const type = ID_TO_TYPE[id];
  const entry = (village.research ?? []).find((r) => r.type === type);
  return entry ? entry.researched : true;
}

/* Seconds per unit. The backend (TrainService) shortens the base time by 2% per level of the unit's own
   recruit building (Barracks / Stable / Workshop) and by the village's bonus, if it has a recruiting one. */
export function recruitSeconds(village, id) {
  const level = reqLevel(village, UNIT_RECRUIT[id].building);
  const bonus = recruitFactor(village.bonus, UNIT_RECRUIT[id].building); // bonus village: faster in its barracks / stable / workshop
  return Math.max(1, Math.round((UNIT_RECRUIT[id].time * Math.max(0.1, 1 - level * 0.02) * bonus) / (village.worldSpeed || 1)));
}
