// Pure helpers for the world map screen. Everything here is a port of the original
// TWLan js/map.js + js/twmap_drag.js (FreeMap, TWMap.*) maths, minus the DOM work.
import { hashTile } from "./data";

/* ---- geometry (TWMap.tileSize / mapSubSectorSize / FreeMap scales) ---- */
export const TILE = [53, 38]; // main map tile size in px
export const SUB = 5; // main map "sector" = 5x5 tiles (TWMap.mapSubSectorSize)
export const MINI = 5; // minimap px per tile
export const MINI_SECTOR = 50; // minimap sector = 50x50 tiles (250px topo image)
export const BIAS = 26500; // FreeMap bias of the main map (keeps css offsets small)
export const BOUND = [0, 0, 999, 999]; // TWMap.scrollBound
export const CON = { CON_COUNT: 10, SEC_COUNT: 20, SUB_COUNT: 5 };
export const MAP_SIZES = [4, 5, 7, 9, 11, 13, 15, 20, 30]; // #map_chooser_select
export const MINIMAP_SIZES = [20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120]; // #minimap_chooser_select
export const DEFAULT_MAP_SIZE = 9;
export const DEFAULT_MINIMAP_SIZE = 50;
export const AXIS_X_H = 17; // #map_coord_x_wrap height
export const AXIS_Y_W = 24; // #map_coord_y_wrap width

/* ---- TWMap.colors ---- */
export const COLORS = {
  this: [255, 255, 255],
  player: [240, 200, 0],
  friend: [69, 255, 146],
  ally: [0, 0, 244],
  partner: [0, 160, 244],
  nap: [128, 0, 128],
  enemy: [244, 0, 0],
  other: [130, 60, 10],
  sleep: [0, 0, 0],
  grey: [150, 150, 150],
};
export const rgb = (c) => `rgb(${c[0]},${c[1]},${c[2]})`;

export const continentByXY = (x, y) => {
  const cx = Math.floor(x / (CON.SEC_COUNT * CON.SUB_COUNT));
  const cy = Math.floor(y / (CON.SEC_COUNT * CON.SUB_COUNT));
  return cx + cy * CON.CON_COUNT;
};

/* ---- FreeMap maths (positions are the pixel offset of the viewport's top-left corner) ---- */
export const sizeTiles = (sizePx) => [Math.floor(sizePx[0] / TILE[0]), Math.floor(sizePx[1] / TILE[1])];
export const coordByPixel = (px, py, scale = TILE) => [Math.floor(px / scale[0]), Math.floor(py / scale[1])];
export const getCenter = (pos, sizePx, scale = TILE) => coordByPixel(pos[0] + sizePx[0] / 2, pos[1] + sizePx[1] / 2, scale);
export const clampPos = (pos, bound = BOUND, scale = TILE) => [
  Math.max(pos[0], bound[0] * scale[0]),
  Math.max(pos[1], bound[1] * scale[1]),
];
export const viewport = (pos, sizePx, scale = TILE) => {
  const tl = coordByPixel(pos[0], pos[1], scale);
  const br = coordByPixel(pos[0] + sizePx[0], pos[1] + sizePx[1], scale);
  return [tl[0], tl[1], br[0], br[1]];
};
export const inViewport = (vp, x, y) => x >= vp[0] && y >= vp[1] && x <= vp[2] && y <= vp[3];

/** FreeMap.centerPos */
export function centerPos(x, y, sizePx, fix, scale = TILE) {
  let px = x * scale[0] - sizePx[0] / 2 + scale[0] / 2;
  let py = y * scale[1] - sizePx[1] / 2 + scale[1] / 2;
  if (fix) {
    px -= px % scale[0];
    py -= py % scale[1];
  }
  return clampPos([px, py]);
}

/** TWMap.focus: where the viewport goes when the map should be centered on tile (x,y). */
export function focusPos(x, y, sizePx) {
  const sz = sizeTiles(sizePx);
  x = ~~Math.max(x, BOUND[0] + sz[0] / 2);
  y = ~~Math.max(y, BOUND[1] + sz[1] / 2);
  x = Math.min(x, BOUND[2] + 1 - sz[0] / 2);
  y = Math.min(y, BOUND[3] + 1 - sz[1] / 2);
  return centerPos(x, y, sizePx, true);
}

/** TWMap.scaleMinimap: offset (tiles) between the minimap's and the map's top-left corners. */
export const minimapOffset = (miniPx, sizePx) => {
  const min = [~~(miniPx[0] / MINI), ~~(miniPx[1] / MINI)];
  const max = [~~(sizePx[0] / TILE[0]), ~~(sizePx[1] / TILE[1])];
  return { offset: [~~((min[0] - max[0]) / 2), ~~((min[1] - max[1]) / 2)], tiles: min, mapTiles: max };
};

/** TWMap.getMinimapScrollBound */
export const minimapBound = (miniPx, sizePx) => {
  const { offset, tiles } = minimapOffset(miniPx, sizePx);
  const size = sizeTiles(sizePx);
  return [
    BOUND[0] - offset[0],
    BOUND[1] - offset[1],
    BOUND[2] + (tiles[0] - offset[0] - size[0]),
    BOUND[3] + (tiles[1] - offset[1] - size[1]),
  ];
};

/* ---- village data (TWMap.villages) ---- */
/** Village sprite in graphic/map/ by points; "_left" variants are abandoned (barbarian) villages. */
export function villageSprite(points, barbarian) {
  const lvl = points < 300 ? 1 : points < 1000 ? 2 : points < 3000 ? 3 : points < 9000 ? 4 : points < 11000 ? 5 : 6;
  return `v${lvl}${barbarian ? "_left" : ""}.png`;
}

/** Plausible points for villages the backend does not send points for. */
export const barbarianPoints = (x, y) => 26 + (hashTile(x, y) % 175);

/** Static terrain: same coordinates always yield the same tile (grass variants; no lone mountain tiles,
 *  the berg*.png sprites only look right in clusters). */
export function terrainSprite(x, y) {
  const h = hashTile(x, y);
  return h % 100 < 30 ? `gras${((h >>> 9) % 3) + 2}.png` : "gras1.png";
}

/** Rebuild TWMap.villages from the API village list. */
export function buildVillageInfos(villages, home, playerName, homePoints, outgoing = [], incoming = [], tribe = null) {
  // the viewer's tribe and how it sees the others (from /api/tribe/map): own tribe -> "ally" colour, PARTNER/NAP/ENEMY
  const tribeRows = new Map((tribe?.tribes ?? []).map((t) => [t.id, t]));
  const RELATION = { PARTNER: "partner", NAP: "nap", ENEMY: "enemy" };
  const relationOf = (v) => {
    if (!v.ownerTribeId) return null;
    if (tribe?.tribeId && v.ownerTribeId === tribe.tribeId) return "ally";
    return RELATION[tribe?.relations?.[v.ownerTribeId]] ?? null;
  };
  // Keyed by village id, not name: every barbarian village is literally named "Abandoned Camp" (WorldService),
  // so keying by name made one outgoing attack paint the icon onto every barbarian village on the map.
  const icons = new Map(); // village id -> command icon names
  const addIcon = (id, img) => {
    if (id == null) return;
    if (!icons.has(id)) icons.set(id, []);
    if (!icons.get(id).includes(img)) icons.get(id).push(img);
  };
  outgoing.forEach((m) => addIcon(m.targetVillageId, m.type === "SUPPORTING" ? "support" : "attack"));
  incoming.forEach((m) => addIcon(m.originVillageId, m.type === "SUPPORT_IN" ? "support" : m.type === "INCOMING_ATTACK" ? "attack" : "return"));
  const isOwn = (v) => v.id === home.id || (v.ownerType === "PLAYER" && v.ownerName === playerName);
  const pointsOf = (v) => (typeof v.points === "number" ? v.points : v.id === home.id ? homePoints : 100);
  // total points per owner (the popup shows "Owner (N points)")
  const ownerTotals = new Map();
  for (const v of villages) {
    if (v.ownerType === "PLAYER") {
      const key = isOwn(v) ? playerName : v.ownerName;
      ownerTotals.set(key, (ownerTotals.get(key) ?? 0) + pointsOf(v));
    }
  }
  return villages.map((v) => {
    const barbarian = v.ownerType === "BARBARIAN";
    const own = !barbarian && isOwn(v);
    const isHome = v.id === home.id;
    const points = barbarian ? v.points || barbarianPoints(v.x, v.y) : pointsOf(v);
    const ownerName = barbarian ? null : own ? playerName : v.ownerName ?? "?";
    const relation = barbarian ? null : relationOf(v);
    const tribeRow = barbarian || !v.ownerTribeId ? null : tribeRows.get(v.ownerTribeId);
    return {
      id: v.id,
      x: v.x,
      y: v.y,
      name: v.name,
      owner: barbarian ? 0 : own ? 1 : 2, // 0 = barbarian, 1 = the viewing player, 2 = another player
      npc: !barbarian && !!v.ownerNpc, // owned by a computer player
      ownerName,
      ownerPoints: barbarian ? 0 : ownerTotals.get(ownerName) ?? points,
      points,
      sprite: villageSprite(points, barbarian),
      color: barbarian ? null : isHome ? COLORS.this : own ? COLORS.player : relation ? COLORS[relation] : COLORS.other,
      relation,
      tribeName: tribeRow?.name ?? null,
      tribePoints: tribeRow?.points ?? 0,
      bonus: v.bonus ?? null,
      icons: icons.get(v.id) ?? [],
    };
  });
}

/* ---- minimap topo image (page.php?page=topo_image) redrawn on a canvas ---- */
const TOPO = { cell: "rgb(73,103,21)", line: "rgb(67,98,19)", con20: "rgb(48,73,14)", con100: "rgb(0,0,0)" };
export const HAS_CANVAS = typeof navigator === "undefined" || !/jsdom/i.test(navigator.userAgent);
const topoCache = new Map();

export function drawTopoSector(sx, sy, vmap, cacheKey) {
  const key = `${cacheKey}|${sx}|${sy}`;
  if (topoCache.has(key)) return topoCache.get(key);
  const px = MINI_SECTOR * MINI;
  const canvas = document.createElement("canvas");
  canvas.width = px;
  canvas.height = px;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  for (let ty = 0; ty < MINI_SECTOR; ty++) {
    for (let tx = 0; tx < MINI_SECTOR; tx++) {
      const x = sx + tx;
      const y = sy + ty;
      if (x < 0 || y < 0 || x > 999 || y > 999) continue;
      const v = vmap.get(x * 1000 + y);
      const ox = tx * MINI;
      const oy = ty * MINI;
      ctx.fillStyle = TOPO.line;
      ctx.fillRect(ox, oy, MINI, MINI);
      ctx.fillStyle = v ? rgb(v.color ?? COLORS.grey) : TOPO.cell;
      ctx.fillRect(ox + 1, oy + 1, MINI - 1, MINI - 1);
    }
  }
  // sector/continent grid lines (every 20 tiles dark green, every 100 black)
  for (let t = 0; t < MINI_SECTOR; t++) {
    for (const [c, horizontal] of [[sx + t, false], [sy + t, true]]) {
      if (c % 20) continue;
      ctx.fillStyle = c % 100 ? TOPO.con20 : TOPO.con100;
      if (horizontal) ctx.fillRect(0, t * MINI, px, 1);
      else ctx.fillRect(t * MINI, 0, 1, px);
    }
  }
  let url = null;
  try {
    url = canvas.toDataURL("image/png");
  } catch {
    url = null;
  }
  if (topoCache.size > 120) topoCache.delete(topoCache.keys().next().value);
  topoCache.set(key, url);
  return url;
}

export const topoFallbackSrc = (playerId, villageId, x, y) =>
  `page.php?page=topo_image&player_id=${playerId}&village_id=${villageId}&x=${x}&y=${y}&church=0&political=0&war=0`;

/* ---- world map image (page.php?page=worldmap_image) ---- */
export const WORLDMAP_SIZE = 650;
export function drawWorldMap(infos, flags) {
  if (!HAS_CANVAS) return null;
  const canvas = document.createElement("canvas");
  canvas.width = WORLDMAP_SIZE;
  canvas.height = WORLDMAP_SIZE;
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  const bias = 500 - WORLDMAP_SIZE / 2;
  ctx.fillStyle = "rgb(88,118,27)";
  ctx.fillRect(0, 0, WORLDMAP_SIZE, WORLDMAP_SIZE);
  ctx.fillStyle = "#000";
  for (let c = 0; c <= 1000; c += 100) {
    const p = c - bias;
    if (p >= 0 && p < WORLDMAP_SIZE) {
      ctx.fillRect(p, 0, 1, WORLDMAP_SIZE);
      ctx.fillRect(0, p, WORLDMAP_SIZE, 1);
    }
  }
  for (const v of infos) {
    if (v.owner === 0 ? !flags.barbarian : !flags.own) continue;
    // a tribe group switched off in the popup is drawn like any other player's village
    const hidden = v.relation && flags[v.relation] === false;
    ctx.fillStyle = rgb(hidden ? COLORS.other : v.color ?? COLORS.grey);
    ctx.fillRect(v.x - bias - 1, v.y - bias - 1, 3, 3);
  }
  try {
    return canvas.toDataURL("image/png");
  } catch {
    return null;
  }
}

/* ---- TWMap.home: the "home" pointer shown when the own village is outside the viewport ---- */
export function homeIndicator(home, center, sizePx) {
  const dy = home.y - center[1];
  const dx = home.x - center[0];
  const distance = Math.sqrt(dx * dx + dy * dy);
  const pointer = Math.atan2(dy, dx);
  let angle = -Math.atan2(dy, dx);
  if (angle < 0) angle = 2 * Math.PI + angle;
  const corner = Math.atan2(sizePx[1] / 2, sizePx[0] / 2);
  let side = "bottom";
  if (angle < corner || angle >= Math.PI * 2 - corner) side = "right";
  else if (angle <= Math.PI - corner) side = "top";
  else if (angle <= Math.PI + corner) side = "left";
  const bound = { left: AXIS_Y_W, top: 0, right: sizePx[0] - 50, bottom: sizePx[1] - 50 - AXIS_X_H };
  let left = bound.left;
  let top = bound.top;
  if (side === "right") left = bound.right;
  if (side === "bottom") top = bound.bottom;
  if (side === "right" || side === "left") {
    const mod = side === "right" ? 1 : -1;
    top = sizePx[1] / 2 - mod * (Math.tan(angle) * (sizePx[0] / 2));
    if (home.y > center[1]) top -= 50;
    top = mod > 0 ? Math.min(top, bound.right) : Math.max(top, bound.left);
  }
  if (side === "top" || side === "bottom") {
    const mod = side === "top" ? 1 : -1;
    left = sizePx[0] / 2 - mod * (Math.tan(angle + Math.PI / 2) * (sizePx[1] / 2));
    if (home.x < center[0]) {
      left -= 50;
      left = Math.max(left, bound.left);
    } else left = Math.min(left, bound.right);
  }
  return { left, top, pointer, distance: Math.floor(distance) };
}

/* ---- TWMap.context: village context menu ---- */
export const CTX_TITLES = {
  mp_res: "Send resources",
  mp_att: "Send troops",
  mp_lock: "Make a noble claim for the village",
  mp_unlock: "Delete noble claim",
  mp_fav: "Add to favorites",
  mp_unfav: "Delete from favorites",
  mp_msg: "Write message",
  mp_profile: "Show player profile",
  mp_overview: "Village overview",
  mp_recruit: "Mass recruitment",
  mp_tab: "Show in new tab",
  mp_info: "Village information",
  mp_farm_a: "Send farm template A",
  mp_farm_b: "Send farm template B",
};
const CIRCLE_POS = [[-12, -12], [-12, -49], [20, -30], [20, 6], [-11, 25], [-44, 6], [-44, -30], [20, -30], [20, 6]];
const OTHER_ORDER = ["mp_info", "mp_lock", "mp_profile", "mp_msg", "mp_fav", "mp_res", "mp_att", "mp_farm_a", "mp_farm_b"];
const OWN_ORDER = ["mp_info", "mp_recruit", "mp_profile", "mp_overview", "mp_fav", "mp_res", "mp_att", "mp_farm_a", "mp_farm_b"];

/** Buttons (id -> offset from the village centre) TWMap.context.spawn would show for a village.
 *  Page config of the original: premium menu on, no tribe (no claims), igm + send troops enabled; farm buttons only when a template is set. */
export function contextButtons(village, homeId, favorites, farm = null) {
  const order = village.owner === 1 ? OWN_ORDER : OTHER_ORDER; // owner 1 = the (only) player
  // farm = { a, b, npc }: which farm templates are set, and whether computer players' villages are farm targets too.
  // The original shows A/B on abandoned villages only (in the profile/message slots); for a computer player's village
  // those slots are taken, so A goes to the free noble-claim slot and B replaces "write message" (nobody to write to).
  const npcTarget = village.owner === 2 && village.npc && !!farm?.npc;
  const out = [];
  order.forEach((v, k) => {
    if (village.owner === 0 && (v === "mp_profile" || v === "mp_msg")) return;
    if (v === "mp_farm_a" || v === "mp_farm_b") {
      if (!(village.owner === 0 || npcTarget) || !farm?.[v === "mp_farm_a" ? "a" : "b"]) return;
      const slot = village.owner === 0 ? k : v === "mp_farm_a" ? 1 : 3;
      out.push({ id: v, dx: CIRCLE_POS[slot][0], dy: CIRCLE_POS[slot][1] });
      return;
    }
    if (npcTarget && farm.b && v === "mp_msg") return;
    if (v === "mp_lock") return;
    if (homeId === village.id && (v === "mp_res" || v === "mp_att")) return;
    if (!village.points && (v === "mp_fav" || v === "mp_lock")) return;
    if (v === "mp_fav" && favorites.has(village.id)) v = "mp_unfav";
    out.push({ id: v, dx: CIRCLE_POS[k][0], dy: CIRCLE_POS[k][1] });
  });
  return out;
}

/* ---- persisted per-viewer settings (the original saves them through ajax to the account) ---- */
export function loadSetting(key, fallback) {
  try {
    const v = window.localStorage.getItem("twlan.map." + key);
    return v === null ? fallback : JSON.parse(v);
  } catch {
    return fallback;
  }
}
export function saveSetting(key, value) {
  try {
    window.localStorage.setItem("twlan.map." + key, JSON.stringify(value));
  } catch {
    /* storage unavailable */
  }
}

/** game.js xProcess(): typing "500|499" or "500499" into the x field jumps to y. */
export function xProcess(xv, yv) {
  if (xv.indexOf("|") !== -1) {
    const part = xv.split("|");
    const x = parseInt(part[0], 10);
    const y = part[1].length !== 0 ? parseInt(part[1], 10) : undefined;
    return { x: Number.isNaN(x) ? "" : String(x), y: y === undefined || Number.isNaN(y) ? yv : String(y), focusY: true };
  }
  if (xv.length === 3 && yv.length === 0) return { x: xv, y: yv, focusY: true };
  if (xv.length > 3) return { x: xv.substr(0, 3), y: xv.substring(3), focusY: true };
  return { x: xv, y: yv, focusY: false };
}
