import { useEffect, useState } from "react";
import { api } from "../../api";
import { Btn, Card, Chip, Empty, ErrorNote, Loading, NavLink, PageHead, useToast } from "./kit.jsx";
import { fmtDate, fmtInt, plural, useLoad } from "./util.jsx";

const A = api.admin;
const DIFFICULTY_TEXT = {
  off: "NPCs build but never attack.",
  passive: "A lively backdrop: raids on barbarians, rarely you.",
  normal: "Scouts before attacking, notices and dodges attacks, conquers barbarian villages.",
  hard: "Farms barbarians, retaliates, asks tribe-mates for help, conquers NPC and player villages (not your last).",
  brutal: "Fast and precise, coordinated waves, can take your last village.",
};
const KIND_TONE = { CONQUEST: "bad", BATTLE: "neutral", RAID: "npc", SCOUT: "neutral", NOBLE: "admin", DODGE: "neutral", SUPPORT: "neutral", TRADE: "neutral", MARKET: "neutral", ALARM: "bad" };

/* The NPC AI of a world: difficulty, who plays which archetype (editable) and what they have been doing. */
export function NpcAi({ world, go }) {
  const toast = useToast();
  const npcs = useLoad(() => A.worldNpcs(world.id), [world.id]);
  const [kind, setKind] = useState("");
  const [who, setWho] = useState("");
  const log = useLoad(() => A.npcLog(world.id, { kind, npc: who, limit: 100 }), [world.id, kind, who]);
  const [edits, setEdits] = useState({}); // accountId -> { archetype, skill }
  const [busy, setBusy] = useState(null);

  // the log and the list follow the game while the page is open
  const reloadLog = log.reload, reloadNpcs = npcs.reload;
  useEffect(() => {
    const id = setInterval(() => { reloadLog?.(); reloadNpcs?.(); }, 5000);
    return () => clearInterval(id);
  }, [reloadLog, reloadNpcs]);

  const save = async (row) => {
    const e = edits[row.accountId];
    if (!e) return;
    setBusy(row.accountId);
    try {
      await A.updateNpcProfile(row.accountId, { archetype: e.archetype ?? row.archetype, skill: e.skill ?? row.skill });
      toast?.(`${row.name} updated.`);
      setEdits((x) => { const n = { ...x }; delete n[row.accountId]; return n; });
      await npcs.reload();
    } catch (err) {
      toast?.(err.message || String(err), "bad");
    } finally {
      setBusy(null);
    }
  };

  if (npcs.error) return <ErrorNote>{npcs.error}</ErrorNote>;
  if (!npcs.data) return <Loading />;
  const { difficulty, archetypes, npcs: rows } = npcs.data;
  const kinds = Object.keys(log.data?.counts ?? {});
  return (
    <>
      <PageHead title="NPC AI" sub={`How the NPC players of ${world.name} play, and what they have been doing.`} />
      <Card title={`Difficulty: ${difficulty}`} sub={DIFFICULTY_TEXT[difficulty]}
        actions={<NavLink go={go} to={"w/" + world.id}>Change in world settings</NavLink>}>
        <p className="dim">Difficulty and conquest rules are world settings (group NPC). Each NPC has an archetype (its role) and a skill that scales the difficulty for that NPC.</p>
      </Card>
      <Card title={plural(rows.length, "NPC player")} flush>
        {rows.length ? (
          <table className="tbl">
            <thead>
              <tr><th>Name</th><th>Archetype</th><th>Skill</th><th>Villages</th><th>Rhythm</th><th>Last action</th><th /></tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const e = edits[r.accountId] ?? {};
                const dirty = e.archetype != null || e.skill != null;
                return (
                  <tr key={r.accountId}>
                    <td><strong>{r.name}</strong><Chip tone="npc">NPC</Chip></td>
                    <td>
                      <select className="inp" value={e.archetype ?? r.archetype} onChange={(ev) => setEdits({ ...edits, [r.accountId]: { ...e, archetype: ev.target.value } })}>
                        {archetypes.map((a) => <option key={a.name} value={a.name}>{a.label}</option>)}
                      </select>
                    </td>
                    <td>
                      <input className="inp" type="number" min="0" max="100" style={{ width: 72 }} value={e.skill ?? r.skill}
                        onChange={(ev) => setEdits({ ...edits, [r.accountId]: { ...e, skill: Math.max(0, Math.min(100, Number(ev.target.value) || 0)) } })} />
                    </td>
                    <td>{fmtInt(r.villages)}</td>
                    <td className="dim">{r.rhythm ?? "-"}</td>
                    <td className="dim">{r.lastMessage ? `${r.lastKind}: ${r.lastMessage}` : "-"}</td>
                    <td className="acts">
                      <Btn size="sm" busy={busy === r.accountId} disabled={!dirty} onClick={() => save(r)}>Save</Btn>{" "}
                      <Btn size="sm" onClick={() => setWho(String(who) === String(r.accountId) ? "" : String(r.accountId))}>{String(who) === String(r.accountId) ? "All" : "Log"}</Btn>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        ) : (
          <Empty title="No NPC players">Create some on the NPC players page.</Empty>
        )}
      </Card>
      <Card title="Activity" sub="The latest 100 actions (the log keeps the newest 1500 per world)"
        actions={
          <select className="inp" value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="">All kinds</option>
            {kinds.map((k) => <option key={k} value={k}>{k} ({fmtInt(log.data.counts[k])})</option>)}
          </select>
        } flush>
        {log.data?.entries?.length ? (
          <table className="tbl">
            <thead>
              <tr><th>Time</th><th>NPC</th><th>Kind</th><th>What</th></tr>
            </thead>
            <tbody>
              {log.data.entries.map((e) => (
                <tr key={e.id}>
                  <td className="dim nowrap">{fmtDate(e.at)}</td>
                  <td>{e.npc}</td>
                  <td><Chip tone={KIND_TONE[e.kind] ?? "neutral"}>{e.kind}</Chip></td>
                  <td>{e.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <Empty title="Nothing yet">NPC actions show up here as they happen.</Empty>
        )}
      </Card>
    </>
  );
}
