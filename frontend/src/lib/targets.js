/* Rally point target lists: "Favorites" (the villages starred on the map, shared with the map's own list) and
   "History" (the last villages you sent troops to, newest first, per world). The original's popups load them from
   screen=targets, a page its server never implemented, so they are kept in the browser like the map's settings. */
import { loadSetting } from "./map";

const KEEP = 15;
const key = (worldId) => "twlan.targets.recent." + (worldId ?? "x");

export const favoriteIds = () => {
  const list = loadSetting("favorites", []);
  return Array.isArray(list) ? list : [];
};

/** [{ id, attack }] newest first */
export function recentTargets(worldId) {
  try {
    const list = JSON.parse(window.localStorage.getItem(key(worldId)) ?? "[]");
    return Array.isArray(list) ? list.filter((e) => e && typeof e.id === "number") : [];
  } catch {
    return [];
  }
}

export function pushRecent(worldId, id, attack) {
  const list = [{ id, attack: !!attack }, ...recentTargets(worldId).filter((e) => e.id !== id)].slice(0, KEEP);
  try {
    window.localStorage.setItem(key(worldId), JSON.stringify(list));
  } catch {
    /* storage unavailable: the history just isn't remembered */
  }
}

/** The village last attacked (not just supported), for the quick button next to the target field. */
export const lastAttacked = (worldId) => recentTargets(worldId).find((e) => e.attack)?.id ?? null;
