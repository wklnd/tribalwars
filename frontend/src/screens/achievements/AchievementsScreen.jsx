// screen=info_player&mode=awards — the player's achievements, grouped like the Tribal Wars knowledge-base page
// (Daily / Combat / Social / Growth / Other). Frames, icons and the .award / .award-group / .progress-bar classes are
// the original's (graphic/awards, game.css); the group boxes are drawn in App.css because the original's ui images
// are not part of the bundle. Achievements the game cannot award (conquering, tribes, premium, events, ...) are greyed.
import { useCallback, useEffect, useState } from "react";
import { api } from "../../api";

export const CATEGORIES = ["Daily", "Combat", "Social", "Growth", "Other"];

const fmt = (n) => Number(n).toLocaleString("de-DE"); // "10.000" like the knowledge-base page

/** The description with the target of the level being worked on. */
export function describe(e, level = e.level) {
  const target = e.thresholds[Math.min(level, e.thresholds.length - 1)];
  return e.description.replace("{n}", fmt(target));
}

export function iconOf(e, level = e.level) {
  return e.key === "years_of_service" ? `years_of_service_${Math.max(1, Math.min(20, level))}` : e.icon;
}

export function AwardImage({ icon, level }) {
  // the explicit size keeps the notification's ".side-notification img { 60px }" from stretching the icon
  return (
    <div className={"award level" + Math.min(4, level)}>
      <img src={`/graphic/awards/${icon}.png`} alt="" style={{ width: 46, height: 46, margin: 7 }} />
    </div>
  );
}

function Progress({ e }) {
  if (!e.available) return <p className="inactive">Not available in this game.</p>;
  const maxed = e.level >= e.maxLevel;
  const next = e.thresholds[Math.min(e.level, e.maxLevel - 1)];
  if (e.lowerIsBetter) {
    // ranks: smaller is better, the bar shows how close the rank is to the next target
    const rank = e.value;
    const pct = maxed ? 100 : rank > 0 ? Math.min(100, (next / rank) * 100) : 0;
    return (
      <div className={"progress-bar" + (maxed ? " progress-bar-alive" : "")}>
        <span className="label">{rank > 0 ? `Rank ${fmt(rank)} / top ${fmt(next)}` : `Not ranked yet / top ${fmt(next)}`}</span>
        <div className={maxed ? "full" : undefined} style={{ width: pct + "%" }} />
      </div>
    );
  }
  const shown = maxed ? next : Math.min(e.value, next);
  return (
    <div className="progress-bar">
      <span className="label">{`${fmt(shown)} / ${fmt(next)}`}</span>
      <div className={maxed ? "full" : undefined} style={{ width: Math.min(100, (shown / next) * 100) + "%" }} />
    </div>
  );
}

function AwardBox({ e }) {
  return (
    <>
      <div className="award-box" style={e.available ? undefined : { opacity: 0.6 }}>
        <AwardImage icon={iconOf(e, Math.max(1, e.level))} level={e.level} />
        <div className="award-desc">
          <p>
            <strong>{e.name}</strong>
            {e.level > 0 && e.maxLevel > 1 ? ` (Level ${e.level}/${e.maxLevel})` : e.level > 0 ? " (earned)" : ""}
          </p>
          <p className={e.available ? undefined : "inactive"}>{describe(e, e.level >= e.maxLevel ? e.maxLevel - 1 : e.level)}</p>
          <Progress e={e} />
        </div>
        <div style={{ clear: "both" }} />
      </div>
      <hr />
    </>
  );
}

function Group({ title, items }) {
  return (
    <div className="award-group">
      <div className="award-group-head">{title}</div>
      <div className="award-group-content">
        {items.map((e) => (
          <AwardBox key={e.key} e={e} />
        ))}
      </div>
      <div className="award-group-foot" />
    </div>
  );
}

export function AchievementsScreen() {
  const [list, setList] = useState(null);
  const [error, setError] = useState(null);
  const [category, setCategory] = useState("Combat");

  const load = useCallback(() => api.achievements().then(setList).catch((e) => setError(e.message)), []);
  useEffect(() => {
    load();
    const id = setInterval(load, 5000);
    return () => clearInterval(id);
  }, [load]);

  if (error) return <div className="error_box">{error}</div>;
  if (!list) return <p>Loading…</p>;

  const earned = list.reduce((n, e) => n + e.level, 0);
  const distinct = list.filter((e) => e.level > 0).length;
  const inCategory = list.filter((e) => e.category === category);
  const count = (c) => list.filter((e) => e.category === c).reduce((n, e) => n + e.level, 0);

  // "Other" is split into the knowledge base's sub-groups (General, event pages, ...)
  const groups = [];
  for (const e of inCategory) {
    const title = e.group ?? category;
    let g = groups.find((x) => x.title === title);
    if (!g) groups.push((g = { title, items: [] }));
    g.items.push(e);
  }

  return (
    <>
      <h2>Achievements</h2>
      <p>
        You have earned <b>{earned}</b> achievement levels in <b>{distinct}</b> achievements. Achievements that need
        things this game does not have (conquering villages, tribes, premium, quests, events, …) are shown greyed out.
      </p>
      <table width="100%">
        <tbody>
          <tr>
            <td valign="top" width="130px">
              <table className="vis modemenu">
                <tbody>
                  {CATEGORIES.map((c) => (
                    <tr key={c}>
                      <td className={c === category ? "selected" : undefined} style={{ minWidth: 80 }}>
                        <a href="#" onClick={(ev) => { ev.preventDefault(); setCategory(c); }}>
                          {c} {count(c) > 0 ? `(${count(c)})` : ""}
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </td>
            <td valign="top">
              {groups.map((g) => (
                <Group key={g.title} title={g.title} items={g.items} />
              ))}
            </td>
          </tr>
        </tbody>
      </table>
    </>
  );
}
