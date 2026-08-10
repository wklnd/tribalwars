import { useState } from "react";
import { api } from "../../api";
import { Btn, Card, Chip, ConfirmDialog, Empty, ErrorNote, Icon, Loading, PageHead, useToast } from "./kit.jsx";
import { fmtInt, plural, useLoad } from "./util.jsx";

const A = api.admin;
const KIND = { PARTNER: "ally", NAP: "NAP", ENEMY: "enemy" };

/* A world's tribes: members, relations, disband, and moving players in and out (NPC tribes form on their own). */
export function WorldTribes({ world }) {
  const toast = useToast();
  const { data, error, reload } = useLoad(() => A.worldTribes(world.id), [world.id]);
  const players = useLoad(() => A.worldPlayers(world.id), [world.id]);
  const [disband, setDisband] = useState(null);
  const [busy, setBusy] = useState(false);
  const [adding, setAdding] = useState({}); // tribeId -> accountId picked

  const act = async (fn, message) => {
    setBusy(true);
    try {
      await fn();
      toast?.(message);
      await reload();
      await players.reload();
    } catch (e) {
      toast?.(e.message || String(e), "bad");
    } finally {
      setBusy(false);
      setDisband(null);
    }
  };

  if (error) return <ErrorNote>{error}</ErrorNote>;
  if (!data) return <Loading />;
  const inTribe = new Set(data.flatMap((t) => t.members.map((m) => m.id)));
  const free = (players.data ?? []).filter((p) => !inTribe.has(p.accountId));
  return (
    <>
      <PageHead title="Tribes" sub={`${plural(data.length, "tribe")} in ${world.name}. NPC players found and grow tribes on their own (setting "NPC tribes").`} />
      {data.length ? (
        data.map((t) => (
          <Card key={t.tribe.id} title={`[${t.tribe.tag}] ${t.tribe.name}`}
            sub={`${fmtInt(t.tribe.points)} points · rank ${t.tribe.rank} · ${plural(t.tribe.members, "member")} · ${plural(t.tribe.villages, "village")}`}
            actions={<Btn size="sm" variant="danger-ghost" onClick={() => setDisband(t)}><Icon name="trash" size={14} /> Disband</Btn>}>
            <table className="tbl">
              <tbody>
                {t.members.map((m) => (
                  <tr key={m.id}>
                    <td>
                      <strong>{m.name}</strong>
                      {m.npc ? <Chip tone="npc">NPC</Chip> : null}
                      {m.founder ? <Chip tone="admin">duke</Chip> : null}
                    </td>
                    <td className="acts">
                      <Btn size="sm" variant="danger-ghost" busy={busy} onClick={() => act(() => A.removeTribeMember(world.id, m.id), `${m.name} left the tribe.`)}>Remove</Btn>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {t.relations.length ? (
              <p className="dim">Relations: {t.relations.map((r) => `${r.tag} (${KIND[r.kind] ?? r.kind})`).join(", ")}</p>
            ) : null}
            {free.length ? (
              <p>
                <select value={adding[t.tribe.id] ?? ""} onChange={(e) => setAdding({ ...adding, [t.tribe.id]: e.target.value })}>
                  <option value="">Add a player…</option>
                  {free.map((p) => <option key={p.accountId} value={p.accountId}>{p.name}{p.npc ? " (NPC)" : ""}</option>)}
                </select>{" "}
                <Btn size="sm" busy={busy} disabled={!adding[t.tribe.id]}
                  onClick={() => act(() => A.addTribeMember(world.id, t.tribe.id, Number(adding[t.tribe.id])), "Player added.")}>Add</Btn>
              </p>
            ) : null}
          </Card>
        ))
      ) : (
        <Card flush><Empty title="No tribes yet">Players can found tribes in the game; NPC players do it on their own.</Empty></Card>
      )}
      {disband ? (
        <ConfirmDialog title="Disband tribe" danger confirmLabel="Disband" busy={busy} onCancel={() => setDisband(null)}
          onConfirm={() => act(() => A.disbandTribe(world.id, disband.tribe.id), "Tribe disbanded.")}>
          Disband <strong>{disband.tribe.name}</strong>? Its {plural(disband.members.length, "member")} lose their tribe, relations and log are deleted, stationed troops go home.
        </ConfirmDialog>
      ) : null}
    </>
  );
}
