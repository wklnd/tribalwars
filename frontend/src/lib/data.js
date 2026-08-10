export const BUILDINGS = {
  HEADQUARTERS: {
    id: "main",
    name: "Village Headquarters",
    ext: "png",
    tiers: [[15, "3"], [5, "2"], [1, "1"]],
    shape: "373,187,417,129,407,72,329,65,306,99,311,150",
    screen: "building:HEADQUARTERS",
  },
  TIMBER_CAMP: {
    id: "wood",
    name: "Timber camp",
    ext: "gif",
    tiers: [[20, "3"], [10, "2"], [1, "1"]],
    shape: "472,379,523,417,583,373,528,330",
    screen: "building:TIMBER_CAMP",
  },
  CLAY_PIT: {
    id: "stone",
    name: "Clay pit",
    ext: "gif",
    tiers: [[20, "3"], [10, "2"], [1, "1"]],
    shape: "34,300,0,349,15,399,67,417,91,402,92,341",
    screen: "building:CLAY_PIT",
  },
  IRON_MINE: {
    id: "iron",
    name: "Iron mine",
    ext: "gif",
    tiers: [[20, "3"], [10, "2"], [1, "1"]],
    shape: "0,55,45,90,93,58,89,6,39,9",
    screen: "building:IRON_MINE",
  },
  FARM: {
    id: "farm",
    name: "Farm",
    ext: "png",
    tiers: [[20, "3"], [10, "2"]],
    shape: "456,0,477,41,526,75,583,88,597,18,597,0",
    screen: "building:FARM",
  },
  WAREHOUSE: {
    id: "storage",
    name: "Warehouse",
    ext: "png",
    tiers: [[20, "3"], [10, "2"], [1, "1"]],
    shape: "96,192,153,218,195,215,193,148,133,121",
    screen: "building:WAREHOUSE",
  },
  BARRACKS: {
    id: "barracks",
    name: "Barracks",
    ext: "png",
    tiers: [[20, "3"], [5, "2"], [1, "1"]],
    shape: "392,289,444,313,506,283,481,235,442,216,392,252",
    screen: "train",
  },
  WALL: {
    id: "wall",
    name: "Wall",
    ext: "png",
    tiers: [[15, "3"], [5, "2"], [1, "1"]],
    shape: "464,12,444,70,534,112,566,49",
    screen: "building:WALL",
  },
};

export const BUILDING_ORDER = Object.keys(BUILDINGS);

export const PLACE = {
  id: "place",
  name: "Rally point",
  shape: "315,271,379,275,401,229,375,206,343,207",
  screen: "place",
};

export const MAIN_FLAG_TIERS = [[15, "mainflag3.gif"], [5, "mainflag2.gif"], [1, "mainflag1.gif"]];

export const UNITS = {
  SPEAR: { id: "spear", name: "Spear fighter" },
  SWORD: { id: "sword", name: "Swordsman" },
  AXE: { id: "axe", name: "Axeman" },
  ARCHER: { id: "archer", name: "Archer" },
  SCOUT: { id: "spy", name: "Scout" },
  LIGHT: { id: "light", name: "Light cavalry" },
  MARCHER: { id: "marcher", name: "Mounted archer" },
  HEAVY: { id: "heavy", name: "Heavy cavalry" },
  RAM: { id: "ram", name: "Ram" },
  CATAPULT: { id: "catapult", name: "Catapult" },
  PALADIN: { id: "knight", name: "Paladin" },
  SNOB: { id: "snob", name: "Nobleman" },
};

export function tierFor(tiers, level) {
  const t = tiers.find(([min]) => level >= min);
  return t ? t[1] : null;
}

export function sceneImage(type, level) {
  const b = BUILDINGS[type];
  const tier = tierFor(b.tiers, level);
  return tier ? `/graphic/visual/${b.id}${tier}.${b.ext}` : null;
}

export function bigImage(type, level) {
  const b = BUILDINGS[type];
  const tier = tierFor(b.tiers, level) ?? "1";
  return `/graphic/big_buildings/${b.id}${tier}.png`;
}

export function fmtDuration(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

export function fmtClock(date) {
  const p = (n) => String(n).padStart(2, "0");
  return `${p(date.getHours())}:${p(date.getMinutes())}:${p(date.getSeconds())}`;
}

export function fmtDate(date) {
  const p = (n) => String(n).padStart(2, "0");
  return `${p(date.getDate())}.${p(date.getMonth() + 1)}.${date.getFullYear()}`;
}

export function onTime(target) {
  const d = new Date(target);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return `today at ${fmtClock(d)}`;
  return `on ${fmtDate(d)} at ${fmtClock(d)}`;
}

export function resClass(amount, capacity) {
  if (amount >= capacity) return "warn";
  if (amount >= capacity * 0.9) return "warn_90";
  return "res";
}

export function levelOf(village, type) {
  return village.buildings.find((b) => b.type === type)?.level ?? 0;
}

export const OV = (mode) => "overview_villages:" + mode;

// the logged-in account's name (ES live binding: App sets it once the village has loaded)
export let PLAYER_NAME = "Player";
export function setPlayerName(name) {
  PLAYER_NAME = name || "Player";
}

// [base, factor] of the original world config's building "points" curve (buildings.json); mirrors
// BuildingType.points() in the backend. A building at level L is worth round(base * factor^(L-1)) points.
export const BUILDING_POINTS = {
  HEADQUARTERS: [10, 1.1999971560929],
  TIMBER_CAMP: [6, 1.2000041287667],
  CLAY_PIT: [6, 1.2000041287667],
  IRON_MINE: [6, 1.2000041287667],
  FARM: [5, 1.1999971560929],
  WAREHOUSE: [6, 1.2000041287667],
  BARRACKS: [16, 1.2000019829319],
  WALL: [8, 1.2001027195781],
  STABLE: [20, 1.2000039538005],
  WORKSHOP: [24, 1.1999609284227],
  ACADEMY: [512, 1.1997721137783],
  SMITHY: [19, 1.1999987515464],
  STATUE: [24, 1.25],
  MARKET: [10, 1.1999971560929],
  HIDING_PLACE: [5, 1.201035728641],
};

export function villagePoints(village) {
  let total = 0;
  for (const [type, [base, factor]] of Object.entries(BUILDING_POINTS)) {
    const level = levelOf(village, type);
    if (level > 0) total += Math.round(base * factor ** (level - 1));
  }
  return total;
}

export function hashTile(x, y) {
  const h = Math.imul(x, 374761393) + Math.imul(y, 668265263);
  return Math.abs(h ^ (h >>> 13));
}
