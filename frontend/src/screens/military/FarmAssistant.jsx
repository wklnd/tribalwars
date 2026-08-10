// Rally point -> Farm Assistant (place:farm). Premade armies ("templates" A, B, C) that go to a village with one click.
// The original TWLan has no such page, so it is built from game.css classes (vis tables, btn, dots and unit icons) and
// sends through the same attack command as the rally point. Templates, filters and hidden targets are kept per world
// in the browser (lib/farm.js).
import { useEffect, useMemo, useState } from "react";
import { UNIT_LIST, travelSeconds, useNowTick } from "../../lib/military";
import { fmtDuration } from "../../lib/data";
import { Timer } from "../../lib/ui";
import { pushRecent } from "../../lib/targets";
import {
  TEMPLATE_KEYS, ago, cleanUnits, farmTargets, lastReports, loadHidden, loadSettings, loadTemplates,
  onFarmSynced, reportDot, saveHidden, saveSettings, saveTemplates, templateProblem, toBackendUnits,
} from "../../lib/farm";

const MAX_ROWS = 100;
const inputsOf = (templates) =>
  Object.fromEntries(TEMPLATE_KEYS.map((n) => [n, Object.fromEntries(Object.entries(templates[n]).map(([id, c]) => [id, String(c)]))]));
const num = (v) => Math.max(0, parseInt(v, 10) || 0);

function TemplateEditor({ templates, home, onSave }) {
  const [draft, setDraft] = useState(() => inputsOf(templates));
  const [saved, setSaved] = useState(false);
  const set = (name, id, value) => {
    setSaved(false);
    setDraft((d) => ({ ...d, [name]: { ...d[name], [id]: value } }));
  };
  const save = (e) => {
    e.preventDefault();
    const next = Object.fromEntries(TEMPLATE_KEYS.map((n) => [n, cleanUnits(draft[n])]));
    onSave(next);
    setDraft(inputsOf(next));
    setSaved(true);
  };
  return (
    <form onSubmit={save}>
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>Template</th>
            {UNIT_LIST.map((u) => (
              <th key={u.id} style={{ textAlign: "center" }}>
                <img src={`/graphic/unit/unit_${u.id}.png`} title={u.name} alt={u.name} />
              </th>
            ))}
          </tr>
          {TEMPLATE_KEYS.map((name) => (
            <tr key={name}>
              <td><b>{name}</b></td>
              {UNIT_LIST.map((u) => (
                <td key={u.id} style={{ textAlign: "center" }}>
                  <input type="text" style={{ width: 38 }} value={draft[name]?.[u.id] ?? ""}
                    onChange={(e) => set(name, u.id, e.target.value)} />
                </td>
              ))}
            </tr>
          ))}
          <tr>
            <td className="grey">At home</td>
            {UNIT_LIST.map((u) => (
              <td key={u.id} className="grey" style={{ textAlign: "center" }}>{home[u.id] ?? 0}</td>
            ))}
          </tr>
        </tbody>
      </table>
      <p>
        <input type="submit" className="btn" value="Save templates" />{" "}
        {saved && <span className="grey">Saved.</span>}
      </p>
    </form>
  );
}

export function FarmAssistant({ village, villages, reports = [], home, onAttack, busy, go }) {
  const worldId = village.worldId;
  const now = useNowTick();
  const [templates, setTemplates] = useState(() => loadTemplates(worldId));
  const [settings, setSettings] = useState(() => loadSettings(worldId));
  const [hidden, setHidden] = useState(() => loadHidden(worldId));
  const [sending, setSending] = useState(null); // "<villageId><template>" while the command is on its way
  const [sent, setSent] = useState({}); // villageId -> template it was last sent with (this visit)
  const [revision, setRevision] = useState(0); // bumped when the server's copy replaced ours, to rebuild the editor
  useEffect(
    () =>
      onFarmSynced(() => {
        setTemplates(loadTemplates(worldId));
        setSettings(loadSettings(worldId));
        setHidden(loadHidden(worldId));
        setRevision((r) => r + 1);
      }),
    [worldId]
  );

  const targets = useMemo(() => farmTargets(village, villages, settings, hidden), [village, villages, settings, hidden]);
  const history = useMemo(() => lastReports(reports), [reports]);
  // attacks of this village that are still on their way, by target: the earliest arrival
  const enRoute = useMemo(() => {
    const out = new Map();
    for (const m of village.outgoingMovements ?? []) {
      if (m.type !== "ATTACKING" || m.targetVillageId == null) continue;
      const cur = out.get(m.targetVillageId);
      if (!cur || new Date(m.arrivesAt) < new Date(cur)) out.set(m.targetVillageId, m.arrivesAt);
    }
    return out;
  }, [village]);

  const changeSettings = (patch) => {
    const next = { ...settings, ...patch };
    setSettings(next);
    saveSettings(worldId, next);
  };
  const setHiddenIds = (ids) => {
    setHidden(ids);
    saveHidden(worldId, ids);
  };
  const saveAll = (next) => {
    setTemplates(next);
    saveTemplates(worldId, next);
  };

  const farm = async (target, name) => {
    setSending(target.id + name);
    const ok = await onAttack(target.id, toBackendUnits(templates[name]));
    setSending(null);
    if (ok) {
      pushRecent(worldId, target.id, true);
      setSent((s) => ({ ...s, [target.id]: name }));
    }
  };

  const shown = targets.slice(0, MAX_ROWS);
  return (
    <>
      <h3>Farm Assistant</h3>
      <p>
        Set up to three premade armies and send one to a village with a single click. Barbarian villages are listed
        nearest first, with what your last attack there found. Put nobles in a template to conquer with it.
      </p>
      <TemplateEditor key={revision} templates={templates} home={home} onSave={saveAll} />

      <table className="vis" width="100%">
        <tbody>
          <tr>
            <td>
              Distance up to{" "}
              <input type="text" style={{ width: 36 }} value={settings.maxDistance || ""}
                onChange={(e) => changeSettings({ maxDistance: num(e.target.value) })} /> fields
            </td>
            <td>
              Points{" "}
              <input type="text" style={{ width: 46 }} value={settings.minPoints || ""} placeholder="min"
                onChange={(e) => changeSettings({ minPoints: num(e.target.value) })} />
              {" - "}
              <input type="text" style={{ width: 46 }} value={settings.maxPoints || ""} placeholder="max"
                onChange={(e) => changeSettings({ maxPoints: num(e.target.value) })} />
            </td>
            <td>
              <label>
                <input type="checkbox" checked={settings.npc} onChange={(e) => changeSettings({ npc: e.target.checked })} />
                {" "}Include villages of computer players
              </label>
            </td>
            <td>
              {hidden.length > 0 && (
                <a href="#" onClick={(e) => { e.preventDefault(); setHiddenIds([]); }}>
                  Show {hidden.length} hidden village{hidden.length === 1 ? "" : "s"}
                </a>
              )}
            </td>
          </tr>
        </tbody>
      </table>

      <table id="plunder_list" className="vis" width="100%">
        <tbody>
          <tr>
            <th />
            <th>Last attack</th>
            <th>Haul</th>
            <th>Wall</th>
            <th>Village</th>
            <th>Points</th>
            <th>Distance</th>
            <th>Send</th>
            <th />
          </tr>
          {shown.map((t) => {
            const h = history.get(t.id);
            const battle = h?.battle;
            const arrives = enRoute.get(t.id);
            return (
              <tr key={t.id}>
                <td>
                  {battle ? (
                    <a href="#" onClick={(e) => go(e, "report:all:" + battle.report.id)}>
                      <img src={`/graphic/dots/${reportDot(battle.report)}.png`} alt="" />
                    </a>
                  ) : (
                    <img src="/graphic/dots/grey.png" alt="" title="No report yet" />
                  )}
                </td>
                <td className="nowrap">{battle ? ago(battle.report.occurredAt, now) : h?.spy ? "scouted" : "-"}</td>
                <td className="nowrap">
                  {battle && (
                    <>
                      <img src={`/graphic/max_loot/${battle.full ? 1 : 0}.png`} alt=""
                        title={battle.full ? "Full haul" : "Partial haul"} /> {battle.loot}
                    </>
                  )}
                </td>
                <td>{h?.wall ?? "?"}</td>
                <td className="nowrap">
                  <a href="#" onClick={(e) => go(e, "info_village:" + t.id)}>{t.name} ({t.x}|{t.y})</a>
                  {t.ownerNpc && <span className="grey"> {t.ownerName}</span>}
                </td>
                <td>{t.points}</td>
                <td>{t.distance.toFixed(1)}</td>
                <td className="nowrap">
                  {TEMPLATE_KEYS.map((name) => {
                    const problem = templateProblem(templates[name], home);
                    const secs = problem ? null : travelSeconds(village, t, Object.keys(templates[name]), village.worldSpeed);
                    return (
                      <input key={name} type="button" className="btn" value={name}
                        style={{ marginRight: 3, opacity: problem ? 0.4 : 1 }}
                        disabled={!!problem || busy || sending != null}
                        title={problem ?? `Send template ${name}: arrives in ${fmtDuration(secs * 1000)}`}
                        onClick={() => farm(t, name)} />
                    );
                  })}
                  {sent[t.id] && !arrives && <span className="grey"> sent ({sent[t.id]})</span>}
                  {arrives && (
                    <span title="An attack is on its way">
                      {" "}<img src="/graphic/command/attack.png" alt="" /> <Timer target={arrives} />
                    </span>
                  )}
                </td>
                <td className="nowrap">
                  <a href="#" title="Give commands at the rally point" onClick={(e) => go(e, "place:" + t.id)}>Rally point</a>
                  {" "}
                  <a href="#" title="Hide this village" onClick={(e) => { e.preventDefault(); setHiddenIds([...hidden, t.id]); }}>
                    <img src="/graphic/delete_small.png" alt="Hide" />
                  </a>
                </td>
              </tr>
            );
          })}
          {shown.length === 0 && (
            <tr><td colSpan={9}>No villages match the filters.</td></tr>
          )}
        </tbody>
      </table>
      {targets.length > MAX_ROWS && (
        <p className="grey">Showing the nearest {MAX_ROWS} of {targets.length} villages; narrow the distance to see the rest.</p>
      )}
    </>
  );
}
