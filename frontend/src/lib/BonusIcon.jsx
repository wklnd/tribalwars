import { bonusOf } from "./bonus";

/* The little icon next to a village's name (overviews); nothing for a village without a bonus. */
export function BonusIcon({ code }) {
  const b = bonusOf(code);
  return b ? <span className={`bonus_icon bonus_icon_${code}`} title={b.text} /> : null;
}
