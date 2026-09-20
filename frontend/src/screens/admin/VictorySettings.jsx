// Victory condition config for a world: none, or one of Domination / War / Rune villages, each with its
// own fully configurable parameters. Used by both "Create world" and a world's Settings page - the caller
// holds the { type, params } value and passes it through unchanged into the world create/update body as
// victoryType / victoryParams (see VictoryService.configure on the backend for the exact keys it parses).
import { Card, Field, Chip } from "./kit.jsx";

const TYPES = [
  ["NONE", "None", "The world runs forever, like today."],
  ["DOMINATION", "Domination", "One tribe must hold most of the player villages for a while."],
  ["WAR", "War", "The strongest tribes are locked into a fight to the last one standing."],
  ["RUNE", "Rune villages", "Tribes race to hold a set of rare, high-level barbarian villages."],
];

const DEFAULTS = {
  NONE: {},
  DOMINATION: { thresholdPercent: "65", afterDays: "180", holdDays: "5" },
  WAR: { rosterSize: "10", selectAfterDays: "90", prepDays: "14", bonusResource: "IRON", bonusAmount: "5000" },
  RUNE: { totalTarget: "23", requireEveryPopulatedContinent: "true", holdDays: "7", spawnShare: "0.01" },
};

export function defaultVictoryValue(world) {
  const type = world?.victoryType && DEFAULTS[world.victoryType] ? world.victoryType : "NONE";
  return { type, params: { ...DEFAULTS[type], ...(world?.victoryParams ?? {}) } };
}

export function VictorySettings({ value, onChange }) {
  const { type, params } = value;
  const setType = (t) => onChange({ type: t, params: { ...DEFAULTS[t] } });
  const set = (k) => (e) => onChange({ type, params: { ...params, [k]: e.target.value } });
  const bool = (k) => (e) => onChange({ type, params: { ...params, [k]: String(e.target.checked) } });

  return (
    <Card title="Victory condition" sub="Real calendar days, never sped up or slowed down by the world's game speed.">
      <div className="choices">
        {TYPES.map(([v, title, text]) => (
          <label key={v} className={"choice" + (type === v ? " on" : "")}>
            <input type="radio" name="victory_type" value={v} checked={type === v} onChange={() => setType(v)} />
            <span>
              <strong>{title}</strong>
              <em>{text}</em>
            </span>
          </label>
        ))}
      </div>

      {type === "DOMINATION" ? (
        <div className="fgrid mt">
          <Field label="Threshold (%)" htmlFor="vc_threshold" hint="Share of all player villages one tribe must hold.">
            <input id="vc_threshold" className="inp" type="number" min="1" max="100" step="0.1" value={params.thresholdPercent} onChange={set("thresholdPercent")} />
          </Field>
          <Field label="After (days)" htmlFor="vc_after_days" hint="World age before this condition can trigger at all.">
            <input id="vc_after_days" className="inp" type="number" min="0" value={params.afterDays} onChange={set("afterDays")} />
          </Field>
          <Field label="Hold (days)" htmlFor="vc_hold_days" hint="Consecutive days the threshold must be held.">
            <input id="vc_hold_days" className="inp" type="number" min="1" value={params.holdDays} onChange={set("holdDays")} />
          </Field>
        </div>
      ) : null}

      {type === "WAR" ? (
        <div className="fgrid mt">
          <Field label="Roster size" htmlFor="vc_roster" hint="How many of the largest tribes (by total points) are locked in.">
            <input id="vc_roster" className="inp" type="number" min="2" value={params.rosterSize} onChange={set("rosterSize")} />
          </Field>
          <Field label="Select after (days)" htmlFor="vc_select_days" hint="World age before the roster is locked in.">
            <input id="vc_select_days" className="inp" type="number" min="0" value={params.selectAfterDays} onChange={set("selectAfterDays")} />
          </Field>
          <Field label="Prep (days)" htmlFor="vc_prep_days" hint="Time between the roster locking in and the war going live.">
            <input id="vc_prep_days" className="inp" type="number" min="0" value={params.prepDays} onChange={set("prepDays")} />
          </Field>
          <Field label="Bonus resource" htmlFor="vc_bonus_res" hint="Paid to every surviving winning member's home village.">
            <select id="vc_bonus_res" className="inp" value={params.bonusResource} onChange={set("bonusResource")}>
              {["WOOD", "CLAY", "IRON"].map((r) => <option key={r} value={r}>{r.charAt(0) + r.slice(1).toLowerCase()}</option>)}
            </select>
          </Field>
          <Field label="Bonus amount" htmlFor="vc_bonus_amt">
            <input id="vc_bonus_amt" className="inp" type="number" min="0" value={params.bonusAmount} onChange={set("bonusAmount")} />
          </Field>
        </div>
      ) : null}

      {type === "RUNE" ? (
        <div className="fgrid mt">
          <Field label="Rune villages needed" htmlFor="vc_total" hint="Total a tribe must hold at once.">
            <input id="vc_total" className="inp" type="number" min="1" value={params.totalTarget} onChange={set("totalTarget")} />
          </Field>
          <Field label="Hold (days)" htmlFor="vc_rune_hold" hint="Consecutive days the requirement must be held.">
            <input id="vc_rune_hold" className="inp" type="number" min="1" value={params.holdDays} onChange={set("holdDays")} />
          </Field>
          <Field label="Spawn share" htmlFor="vc_spawn" hint="Chance a new barbarian village rolls as a rune village (0 to 1).">
            <input id="vc_spawn" className="inp" type="number" min="0" max="1" step="0.001" value={params.spawnShare} onChange={set("spawnShare")} />
          </Field>
          <Field label="Every populated continent" htmlFor="vc_continents" hint="Also require at least one held in every continent that has a player village.">
            <label className="choice inline">
              <input id="vc_continents" type="checkbox" checked={params.requireEveryPopulatedContinent === "true" || params.requireEveryPopulatedContinent === true} onChange={bool("requireEveryPopulatedContinent")} />
              <span>Required</span>
            </label>
          </Field>
        </div>
      ) : null}
    </Card>
  );
}

export function VictoryBadge({ world }) {
  if (!world?.victoryWonTribeName) return null;
  return <Chip tone="ok">Won by {world.victoryWonTribeName}</Chip>;
}
