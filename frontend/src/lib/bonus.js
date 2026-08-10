/* Bonus villages. The code is the original's bonus id: game.css draws the small icon with `bonus_icon bonus_icon_<code>`
   (graphic/overview/bonus_icons.png; rendered order: axe, shovel, pickaxe, bread, swords, horseshoe, gears, resource pile, wheel) and the map
   popup shows graphic/bonus/<image>.png. The original computes the texts and effects in
   compiled code, so the texts and numbers here are the classic Tribal Wars ones (the backend's BonusType has the same table). */
export const BONUS = {
  1: { image: "wood", text: "+100% wood production" },
  2: { image: "stone", text: "+100% clay production" },
  3: { image: "iron", text: "+100% iron production" },
  4: { image: "farm", text: "+10% population" },
  5: { image: "barracks", text: "Recruitment in the barracks is 33% faster", recruit: { building: "barracks", factor: 1 / 1.5 } },
  6: { image: "stable", text: "Recruitment in the stable is 33% faster", recruit: { building: "stable", factor: 1 / 1.5 } },
  7: { image: "garage", text: "Recruitment in the workshop is 50% faster", recruit: { building: "garage", factor: 0.5 } },
  8: { image: "all", text: "+10% production of all resources" },
  9: { image: "storage", text: "+50% storage capacity" },
};

export const bonusOf = (code) => (code ? BONUS[code] ?? null : null);

/* The factor a bonus puts on the time to recruit in the given building (1 = none). */
export function recruitFactor(code, building) {
  const r = bonusOf(code)?.recruit;
  return r && r.building === building ? r.factor : 1;
}
