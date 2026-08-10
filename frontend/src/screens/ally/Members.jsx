/* templates ally/members.php: the member table with the privilege columns, "Edit Rights", kick / rights actions
   (the radio + "Choose Action" select) and the status legend. Rights of a single player: Rights.jsx. */
import { useMemo, useState } from "react";
import { api } from "../../api";
import { PlayerLink, RIGHT_ROLES, thousands } from "./common";

const HEADERS = [
  ["name", "name", 280],
  ["rank", "rank", 40],
  ["points", "Points", 80],
  ["globalRank", "Global Ranking", 60],
  ["villages", "Villages", 40],
];
const ICONS = [
  ["found", "Founder"],
  ["lead", "Leader"],
  ["invite", "Invite"],
  ["diplomacy", "Cancel"],
  ["mass_mail", "Mass Mail"],
  ["forum_mod", "Moderator of internal forum"],
  ["internal_forum", "Internal Forum"],
  ["trusted_member", "Trusted Member"],
];

const rankCell = (n) => <>{n}</>;

export function Members({ state, run, go, onRights, setError }) {
  const { members, me } = state;
  const [order, setOrder] = useState({ key: "rank", dir: 1 });
  const [editing, setEditing] = useState(false);
  const [edits, setEdits] = useState({}); // memberId -> Set(roles), while "Edit Rights" is open
  const [selected, setSelected] = useState(null);
  const [action, setAction] = useState("");
  const canManage = me.permissions.includes("lead");

  const sorted = useMemo(() => {
    const val = (m) => {
      if (order.key === "name") return m.name.toLowerCase();
      if (ICONS.some(([k]) => k === order.key)) return m.roles.includes(order.key) ? 0 : 1;
      return m[order.key];
    };
    return [...members].sort((a, b) => {
      const x = val(a), y = val(b);
      return (x < y ? -1 : x > y ? 1 : 0) * order.dir;
    });
  }, [members, order]);

  const sortBy = (e, key) => {
    e.preventDefault();
    setOrder((o) => ({ key, dir: o.key === key ? -o.dir : 1 }));
  };

  const startEdit = (e) => {
    e.preventDefault();
    setEdits(Object.fromEntries(members.map((m) => [m.id, new Set(m.roles)])));
    setEditing(true);
  };

  // the original's set_found_right / set_lead_right: duke implies baron implies every other privilege
  const effective = (set) => {
    const out = new Set(set);
    if (out.has("found")) out.add("lead");
    if (out.has("lead") || out.has("found")) RIGHT_ROLES.forEach((r) => out.add(r));
    return out;
  };
  const toggle = (id, role) => {
    setEdits((prev) => {
      const set = new Set(prev[id]);
      if (set.has(role)) set.delete(role);
      else set.add(role);
      if (role === "found" && !set.has("found")) set.delete("lead");
      return { ...prev, [id]: set };
    });
  };

  const save = async (e) => {
    e.preventDefault();
    setError(null);
    for (const m of members) {
      const wanted = effective(edits[m.id] ?? new Set(m.roles));
      const wantedKeys = [...wanted].sort().join();
      if (wantedKeys === [...m.roles].sort().join()) continue;
      const ok = await run(() => api.tribeRights(m.id, [...edits[m.id]], m.title, m.titleOutside));
      if (!ok) break;
    }
    setEditing(false);
  };

  const submitAction = (e) => {
    e.preventDefault();
    if (selected == null || !action) return;
    if (action === "rights") return onRights(selected);
    if (action === "kick") {
      const m = members.find((x) => x.id === selected);
      if (window.confirm(`Dismiss ${m?.name}?`)) run(() => api.tribeKick(selected));
    }
  };

  const shown = () => (editing ? { display: "none" } : undefined);
  const hideToggle = editing ? { display: "inline" } : undefined;

  return (
    <>
      <form id="form_rights" method="post" onSubmit={editing ? save : submitAction}>
        <table className="vis">
          <tbody>
            <tr>
              {HEADERS.map(([key, label, width]) => (
                <th key={key} width={width} className="nowrap">
                  <a href="#" onClick={(e) => sortBy(e, key)}>{label}</a>
                </th>
              ))}
              {ICONS.map(([key, title]) => (
                <th key={key}>
                  <a href="#" className="nowrap" onClick={(e) => sortBy(e, key)}>
                    <span title={title} className={"icon ally " + key} />
                  </a>
                </th>
              ))}
            </tr>
            {sorted.map((m, idx) => {
              const set = editing ? effective(edits[m.id] ?? new Set(m.roles)) : new Set(m.roles);
              const founderNow = editing ? (edits[m.id] ?? new Set(m.roles)).has("found") : m.founder;
              const leaderNow = editing ? set.has("lead") : m.leader;
              const cell = (role, disabled) => {
                const on = role === "found" ? founderNow : set.has(role);
                return (
                  <td className="lit-item" key={role}>
                    <input type="checkbox" className="hide_toggle" style={hideToggle} checked={on} disabled={disabled}
                      onChange={() => toggle(m.id, role)} id={`player_id[${m.id}][${role}]`} name={`player_id[${m.id}][${role}]`} />
                    <div className="show_toggle" style={editing ? { display: "none" } : undefined}>
                      <img alt="Yes" src={`graphic/dots/${on ? "green" : "grey"}.png`} />
                    </div>
                  </td>
                );
              };
              return (
                <tr key={m.id} className={"row_a " + (idx % 2 === 0 ? "selected " : "")}>
                  <td className="lit-item">
                    <input type="hidden" value={m.id} name={`player_id[${m.id}][id]`} />{" "}
                    <input type="radio" className="show_toggle" style={shown()} value={m.id} name="player" checked={selected === m.id} onChange={() => setSelected(m.id)} />{" "}
                    <img className="" alt="" title="" src="graphic/stat/green.png" /> <PlayerLink name={m.name} go={go} />
                    {m.title && <> ({m.title})</>}
                  </td>
                  <td className="lit-item">{rankCell(m.rank)}</td>
                  <td className="lit-item">{thousands(m.points)}</td>
                  <td className="lit-item">{rankCell(m.globalRank)}</td>
                  <td className="lit-item">{m.villages}</td>
                  {cell("found", false)}
                  {cell("lead", founderNow)}
                  {RIGHT_ROLES.map((r) => cell(r, leaderNow || founderNow))}
                </tr>
              );
            })}
            <tr>
              {canManage && (
                <>
                  <td className="no_bg">
                    {!editing ? (
                      <div className="show_toggle">
                        <select name="ally_action" value={action} onChange={(e) => setAction(e.target.value)}>
                          <option value="">Choose Action...</option>
                          <option value="rights">Rights and Titles</option>
                          <option value="kick">Kick</option>
                        </select>{" "}
                        <input className="btn" type="submit" value="OK" />
                      </div>
                    ) : (
                      <>
                        <input type="submit" className="hide_toggle btn" style={hideToggle} value="Save rights" />{" "}
                        <a className="hide_toggle btn" style={hideToggle} href="#" onClick={(e) => { e.preventDefault(); setEditing(false); }}>Cancellation</a>
                      </>
                    )}
                  </td>
                  <td className="no_bg align_right" colSpan="11">
                    {!editing && <a className="show_toggle btn" href="#" onClick={startEdit}>» Edit Rights</a>}
                  </td>
                </>
              )}
            </tr>
          </tbody>
        </table>
      </form>
      <br />
      <table className="vis">
        <tbody>
          <tr><th>Status</th></tr>
          <tr><td><img alt="" src="graphic/stat/green.png" />Active</td></tr>
          <tr><td><img alt="" src="graphic/stat/birthday.png" />Birthday</td></tr>
          <tr><td><img alt="" src="graphic/stat/banned.png" />Banned</td></tr>
        </tbody>
      </table>
    </>
  );
}
