// Client-side battle simulator for the rally point "Simulator" tab (classic Tribal Wars maths,
// using the unit numbers the original ships). Returns {attackingArmy:{before,after}, defendingArmy:{before,after}, wall?}.
import { UNIT_LIST } from "../../lib/military";

const num = (v) => {
  const n = parseInt(v, 10);
  return isNaN(n) || n < 0 ? 0 : n;
};

const CATEGORY = { infantry: "infantry", cavalry: "cavalry", archer: "archer", other: "infantry" };
const kindOf = (u) => (u.id === "spy" ? "infantry" : CATEGORY[u.type] ?? "infantry");

export function simulate({ att, def, wall = 0, moral = 100, luck = 0, night = false, beliefAtt = false, beliefDef = false }) {
  const attBefore = {};
  const defBefore = {};
  for (const u of UNIT_LIST) {
    attBefore[u.id] = num(att[u.id]);
    defBefore[u.id] = num(def[u.id]);
  }
  const morale = Math.min(100, Math.max(0, Number(moral) || 100)) / 100;
  const luckF = 1 + (Math.min(25, Math.max(-25, Number(luck) || 0))) / 100;
  // the world has no church (world.church = false), so belief never weakens either side; the two checkboxes of the
  // original's form are kept but have no effect. Everything else is the maths of the backend's BattleCalculator.
  void beliefAtt;
  void beliefDef;
  const faithAtt = 1;
  const faithDef = 1;

  // attack power split by category
  const off = { infantry: 0, cavalry: 0, archer: 0 };
  let offTotal = 0;
  for (const u of UNIT_LIST) {
    const p = attBefore[u.id] * u.attack;
    off[kindOf(u)] += p;
    offTotal += p;
  }
  offTotal *= morale * luckF * faithAtt;

  // defence: weighted by what is attacking
  const wallLevel = num(wall);
  const wallBonus = Math.pow(1.037, wallLevel) * (night ? 2 : 1);
  let defTotal = 0;
  const shares =
    offTotal > 0
      ? { infantry: off.infantry / (off.infantry + off.cavalry + off.archer), cavalry: off.cavalry / (off.infantry + off.cavalry + off.archer), archer: off.archer / (off.infantry + off.cavalry + off.archer) }
      : { infantry: 1, cavalry: 0, archer: 0 };
  for (const u of UNIT_LIST) {
    const per = u.defense * shares.infantry + u.defense_cavalry * shares.cavalry + u.defense_archer * shares.archer;
    defTotal += defBefore[u.id] * per;
  }
  defTotal = (defTotal * wallBonus + 20) * faithDef;

  const attAfter = { ...attBefore };
  const defAfter = { ...defBefore };
  if (offTotal > defTotal) {
    const lossRatio = offTotal === 0 ? 0 : Math.pow(defTotal / offTotal, 1.5);
    for (const u of UNIT_LIST) {
      attAfter[u.id] = Math.round(attBefore[u.id] * (1 - lossRatio));
      defAfter[u.id] = 0;
    }
  } else {
    const lossRatio = defTotal === 0 ? 0 : Math.pow(offTotal / defTotal, 1.5);
    for (const u of UNIT_LIST) {
      defAfter[u.id] = Math.round(defBefore[u.id] * (1 - lossRatio));
      attAfter[u.id] = 0;
    }
  }
  return {
    attackingArmy: { before: attBefore, after: attAfter },
    defendingArmy: { before: defBefore, after: defAfter },
  };
}
