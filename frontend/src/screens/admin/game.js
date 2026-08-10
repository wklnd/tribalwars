// Game metadata for the admin panel: buildings / units (backend types <-> icons), and the requirement rules.
import { B, ID_BY_TYPE, ORDER, midImg } from "../../lib/buildings";
import { TYPE_TO_ID, UNIT_BY_ID } from "../../lib/military";

const ECONOMY = ["HEADQUARTERS", "TIMBER_CAMP", "CLAY_PIT", "IRON_MINE", "FARM", "WAREHOUSE", "MARKET", "HIDING_PLACE"];
const MILITARY = ["BARRACKS", "STABLE", "WORKSHOP", "SMITHY", "ACADEMY", "WALL", "RALLY_POINT", "STATUE"];

const NAMES = { HEADQUARTERS: "Headquarters", WORKSHOP: "Workshop", ACADEMY: "Academy", SMITHY: "Smithy", HIDING_PLACE: "Hiding place" };

// { type, id (original screen id, used for the artwork), name }
export const BUILDINGS = ORDER.filter((id) => B[id].type).map((id) => ({
  type: B[id].type,
  id,
  name: NAMES[B[id].type] ?? B[id].name,
  start: B[id].start,
  max: B[id].max,
}));
export const BUILDING_BY_TYPE = Object.fromEntries(BUILDINGS.map((b) => [b.type, b]));
export const BUILDING_GROUPS = [
  { name: "Economy", types: ECONOMY },
  { name: "Military and defence", types: MILITARY },
];

export const buildingName = (type) => BUILDING_BY_TYPE[type]?.name ?? type;
// building artwork (level-dependent, greyed out while the building is not built)
export const buildingImg = (type, level) => midImg(ID_BY_TYPE[type], level || 0, !level);

// { type, id, name, img }
export const UNITS = ["SPEAR", "SWORD", "AXE", "ARCHER", "SCOUT", "LIGHT", "MARCHER", "HEAVY", "RAM", "CATAPULT", "SNOB"].map((type) => {
  const id = TYPE_TO_ID[type];
  return { type, id, name: UNIT_BY_ID[id]?.name ?? type, img: `/graphic/unit/unit_${id}_60.png`, icon: `/graphic/unit/unit_${id}.png` };
});
export const UNIT_BY_TYPE = Object.fromEntries(UNITS.map((u) => [u.type, u]));
export const unitName = (type) => UNIT_BY_TYPE[type]?.name ?? type;

// buildings a unit can only be trained with (units.json requirements + the recruit building itself)
export const UNIT_REQUIREMENTS = {
  SPEAR: { BARRACKS: 1 },
  SWORD: { BARRACKS: 1, SMITHY: 1 },
  AXE: { BARRACKS: 1, SMITHY: 2 },
  ARCHER: { BARRACKS: 5, SMITHY: 5 },
  SCOUT: { STABLE: 1 },
  LIGHT: { STABLE: 3 },
  MARCHER: { STABLE: 5 },
  HEAVY: { STABLE: 10, SMITHY: 15 },
  RAM: { WORKSHOP: 1 },
  CATAPULT: { WORKSHOP: 2, SMITHY: 12 },
  SNOB: { ACADEMY: 1 },
};

export const RESOURCES = [
  { key: "wood", label: "Wood", img: "/graphic/holz.png" },
  { key: "clay", label: "Clay", img: "/graphic/lehm.png" },
  { key: "iron", label: "Iron", img: "/graphic/eisen.png" },
];

export function catalogBuilding(catalog, type) {
  return (catalog?.buildings ?? []).find((b) => b.type === type);
}
export const maxLevel = (catalog, type) => catalogBuilding(catalog, type)?.maxLevel ?? BUILDING_BY_TYPE[type]?.max ?? 30;
export const startLevel = (catalog, type) => catalogBuilding(catalog, type)?.startLevel ?? BUILDING_BY_TYPE[type]?.start ?? 0;
export const requirementsOf = (catalog, type) => catalogBuilding(catalog, type)?.requirements ?? {};

// unmet requirements of a building at the given levels: [{ type, need, have }]. A building at level 0 has none.
export function missingForBuilding(catalog, levels, type) {
  if (!(levels[type] > 0)) return [];
  return Object.entries(requirementsOf(catalog, type))
    .filter(([t, need]) => (levels[t] ?? 0) < need)
    .map(([t, need]) => ({ type: t, need, have: levels[t] ?? 0 }));
}

// unmet training requirements of a unit
export function missingForUnit(levels, type) {
  return Object.entries(UNIT_REQUIREMENTS[type] ?? {})
    .filter(([t, need]) => (levels[t] ?? 0) < need)
    .map(([t, need]) => ({ type: t, need, have: levels[t] ?? 0 }));
}

export const describeMissing = (missing) => missing.map((m) => `${buildingName(m.type)} ${m.need}`).join(", ");

// number of problems (buildings + troops) in a layout
export function countProblems(catalog, levels, units) {
  let n = 0;
  for (const b of BUILDINGS) if (missingForBuilding(catalog, levels, b.type).length) n++;
  for (const u of UNITS) if ((units[u.type] ?? 0) > 0 && missingForUnit(levels, u.type).length) n++;
  return n;
}

// raise prerequisite levels (recursively) until every built building and every present unit is valid
export function fixRequirements(catalog, levels, units) {
  const out = { ...levels };
  const raise = (type, need) => {
    out[type] = Math.max(out[type] ?? 0, Math.min(need, maxLevel(catalog, type)));
  };
  for (let guard = 0; guard < 40; guard++) {
    let changed = false;
    for (const b of BUILDINGS) {
      if (!(out[b.type] > 0)) continue;
      for (const [t, need] of Object.entries(requirementsOf(catalog, b.type))) {
        if ((out[t] ?? 0) < need) {
          raise(t, need);
          changed = true;
        }
      }
    }
    for (const u of UNITS) {
      if (!((units?.[u.type] ?? 0) > 0)) continue;
      for (const [t, need] of Object.entries(UNIT_REQUIREMENTS[u.type])) {
        if ((out[t] ?? 0) < need) {
          raise(t, need);
          changed = true;
        }
      }
    }
    if (!changed) break;
  }
  return out;
}

export const startLayout = (catalog) => Object.fromEntries(BUILDINGS.map((b) => [b.type, startLevel(catalog, b.type)]));
export const emptyUnits = () => Object.fromEntries(UNITS.map((u) => [u.type, 0]));

// -- formatting --------------------------------------------------------------------------------------------
export const clampInt = (v, lo, hi) => {
  const n = Math.floor(Number(v));
  if (!Number.isFinite(n)) return lo;
  return Math.min(hi, Math.max(lo, n));
};

// development percentage -> plain-language stage (the five ticks of the slider)
export const STAGES = [
  [0, "Freshly founded"],
  [25, "Small"],
  [50, "Established"],
  [75, "Well developed"],
  [100, "Fully grown"],
];
export const stageOf = (pct) => STAGES[Math.max(0, Math.min(4, Math.round(pct / 25)))][1];
