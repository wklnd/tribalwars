import { useState } from "react";
import { api } from "../../api";
import { Btn, Card, Chip, ConfirmDialog, Empty, ErrorNote, Field, Icon, Loading, NavLink, Notice, PageHead, Stepper, DualRange, useToast } from "./kit.jsx";
import { fmtDate, fmtInt, plural, toNum, useLoad } from "./util.jsx";
import { SettingsGroups, initialValues } from "./SettingsForm.jsx";
import { VictorySettings, VictoryBadge, defaultVictoryValue } from "./VictorySettings.jsx";

const A = api.admin;

function Stat({ label, value }) {
  return (
    <div className="stat">
      <span className="stat-l">{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

const OwnerChips = ({ v }) =>
  v.ownerType === "BARBARIAN" || !v.ownerName ? (
    <Chip tone="barb">barbarian</Chip>
  ) : (
    <>
      <strong>{v.ownerName}</strong>
      {v.npc ? <Chip tone="npc">NPC</Chip> : null}
    </>
  );

// ---- World overview + settings --------------------------------------------------------------------------------
export function WorldSettings({ world, catalog, reloadWorlds, go }) {
  const toast = useToast();
  const [name, setName] = useState(world.name);
  const [speed, setSpeed] = useState(String(world.speed));
  const [values, setValues] = useState(() => initialValues(catalog, world));
  const [victory, setVictory] = useState(() => defaultVictoryValue(world));
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [typed, setTyped] = useState("");
  const [deleting, setDeleting] = useState(false);
  const [confirmingCompact, setConfirmingCompact] = useState(false);
  const [compacting, setCompacting] = useState(false);

  const compact = async () => {
    setCompacting(true);
    try {
      const r = await A.compactWorld(world.id);
      toast(`${plural(r.villages, "village")} moved into a disc of radius ${r.radius}.`);
    } catch (err) {
      toast(err.message, "bad");
    } finally {
      setCompacting(false);
      setConfirmingCompact(false);
    }
  };

  const save = async (e) => {
    e.preventDefault();
    setError(null);
    const sp = toNum(speed);
    if (!name.trim()) return setError("Please give the world a name.");
    if (sp === undefined || Number.isNaN(sp) || sp <= 0) return setError("Speed must be a positive number.");
    setSaving(true);
    try {
      await A.updateWorld(world.id, { name: name.trim(), speed: sp, settings: values, victoryType: victory.type, victoryParams: victory.params });
      toast("Settings saved.");
      reloadWorlds();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const del = async () => {
    setDeleting(true);
    try {
      await A.deleteWorld(world.id);
      toast(`World "${world.name}" deleted.`);
      await reloadWorlds();
      go("");
    } catch (err) {
      toast(err.message, "bad");
      setConfirming(false);
    } finally {
      setDeleting(false);
    }
  };

  const b = "w/" + world.id;
  return (
    <form onSubmit={save} className="narrow">
      <PageHead
        title={<>{world.name} {world.victoryWonAt ? <Chip tone="muted">closed</Chip> : null} <VictoryBadge world={world} /></>}
        sub={`World #${world.id}, created ${fmtDate(world.createdAt)}`}
        actions={
          <>
            <NavLink go={go} to={b + "/players"} className="btn">Players</NavLink>
            <NavLink go={go} to={b + "/villages/create"} className="btn primary">Create villages</NavLink>
          </>
        }
      />
      <div className="stats">
        <Stat label="Players" value={fmtInt(world.players)} />
        <Stat label="NPC players" value={fmtInt(world.npcs)} />
        <Stat label="Villages" value={fmtInt(world.villages)} />
        <Stat label="Barbarian villages" value={fmtInt(world.barbarians)} />
      </div>
      {error ? <ErrorNote>{error}</ErrorNote> : null}
      <Card title="World" sub="Name and game speed">
        <div className="fgrid">
          <Field label="Name" htmlFor="world_name">
            <input id="world_name" className="inp" type="text" value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Game speed" htmlFor="world_speed" hint="1 = normal, 100 = a hundred times faster.">
            <input id="world_speed" className="inp" type="text" inputMode="decimal" value={speed} onChange={(e) => setSpeed(e.target.value)} />
          </Field>
        </div>
      </Card>
      <SettingsGroups catalog={catalog} values={values} onChange={(k, v) => setValues((o) => ({ ...o, [k]: v }))} />
      <VictorySettings value={victory} onChange={setVictory} />
      <div className="formbar">
        <Btn type="submit" variant="primary" icon="save" busy={saving}>Save settings</Btn>
      </div>
      <Card title="Layout" sub="Where the villages of this world stand">
        <div className="dz">
          <div>
            <strong>Compact world</strong>
            <p>Moves every village (players, NPCs and barbarians) to a new random spot in one dense disc around the centre, like the original's world map. Only the coordinates change; nothing else is touched.</p>
          </div>
          <Btn icon="map" onClick={() => setConfirmingCompact(true)}>Compact world</Btn>
        </div>
      </Card>
      <Card title="Danger zone" className="danger-zone">
        <div className="dz">
          <div>
            <strong>Delete this world</strong>
            <p>Removes the world with every village and every player village in it. This cannot be undone.</p>
          </div>
          <Btn variant="danger" icon="trash" onClick={() => { setTyped(""); setConfirming(true); }}>Delete world</Btn>
        </div>
      </Card>
      {confirmingCompact ? (
        <ConfirmDialog
          title={`Compact "${world.name}"?`}
          confirmLabel="Compact world"
          busy={compacting}
          onCancel={() => setConfirmingCompact(false)}
          onConfirm={compact}
        >
          <p>All {plural(world.villages, "village")} get new coordinates, including any player's home village. Marches on their way keep their arrival times, so travel distances may no longer match.</p>
        </ConfirmDialog>
      ) : null}
      {confirming ? (
        <ConfirmDialog
          title={`Delete "${world.name}"?`}
          danger
          confirmLabel="Delete world"
          busy={deleting}
          disabled={typed.trim() !== world.name}
          onCancel={() => setConfirming(false)}
          onConfirm={del}
        >
          <p>{plural(world.villages, "village")} and {plural(world.players + world.npcs, "player")} will be removed for good.</p>
          <Field label={`Type "${world.name}" to confirm`} htmlFor="del_world_confirm">
            <input id="del_world_confirm" className="inp" autoFocus value={typed} onChange={(e) => setTyped(e.target.value)} />
          </Field>
        </ConfirmDialog>
      ) : null}
    </form>
  );
}

// ---- Players of a world ---------------------------------------------------------------------------------------
export function WorldPlayers({ world, go }) {
  const { data, error } = useLoad(() => A.worldPlayers(world.id), [world.id]);
  if (error) return <ErrorNote>{error}</ErrorNote>;
  if (!data) return <Loading />;
  const sorted = [...data].sort((a, b) => b.points - a.points);
  return (
    <>
      <PageHead
        title="Players"
        sub={`${plural(data.length, "player")} in ${world.name}.`}
        actions={<NavLink go={go} to={`w/${world.id}/players/npcs`} className="btn primary"><Icon name="plus" /> NPC players</NavLink>}
      />
      <Card flush>
        {data.length ? (
          <table className="tbl">
            <thead>
              <tr>
                <th className="num">Rank</th>
                <th>Player</th>
                <th className="num">Points</th>
                <th className="num">Villages</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {sorted.map((p) => (
                <tr key={p.accountId}>
                  <td className="num dim">{1 + data.filter((o) => o.points > p.points).length}</td>
                  <td>
                    <strong>{p.name}</strong>
                    {p.npc ? <Chip tone="npc">NPC</Chip> : null}
                    {p.admin ? <Chip tone="admin">admin</Chip> : null}
                  </td>
                  <td className="num">{fmtInt(p.points)}</td>
                  <td className="num">{fmtInt(p.villages)}</td>
                  <td className="acts">
                    <NavLink go={go} to={`w/${world.id}/players/delete/${p.accountId}`} className="btn sm danger-ghost"><Icon name="trash" size={14} /> Remove</NavLink>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <Empty title="Nobody has joined this world yet">Create NPC players to get some life on the map.</Empty>
        )}
      </Card>
    </>
  );
}

// ---- Remove a player -----------------------------------------------------------------------------------------
const HANDLING = [
  ["delete", "Delete their villages", "The villages disappear from the map."],
  ["move", "Transfer to another player", "Everything stays as it is, under a new owner."],
  ["barbarian", "Make them barbarian villages", "The villages stay on the map without an owner."],
];

export function DeletePlayer({ world, accountId, go }) {
  const { data, error } = useLoad(() => A.worldPlayers(world.id), [world.id]);
  const toast = useToast();
  const [handle, setHandle] = useState("delete");
  const [moveUser, setMoveUser] = useState("");
  const [busy, setBusy] = useState(false);
  const [asking, setAsking] = useState(false);
  const [problem, setProblem] = useState(null);
  const [done, setDone] = useState(null);
  const back = `w/${world.id}/players`;

  if (error) return <ErrorNote>{error}</ErrorNote>;
  if (!data) return <Loading />;
  const player = data.find((p) => String(p.accountId) === String(accountId));

  const ask = (e) => {
    e.preventDefault();
    setProblem(null);
    if (handle === "move" && !moveUser.trim()) return setProblem("Enter the name of the player who receives the villages.");
    setAsking(true);
  };

  const run = async () => {
    setBusy(true);
    try {
      const body = { villageHandling: handle === "move" ? "transfer" : handle };
      if (handle === "move") body.transferTo = moveUser.trim();
      const r = await A.removePlayer(world.id, accountId, body);
      setDone({ text: `${player ? player.name : accountId} was removed from ${world.name}; ${plural(r?.removedVillages ?? 0, "village")} handled.` });
      toast("Player removed.");
    } catch (err) {
      setProblem(err.message);
    } finally {
      setBusy(false);
      setAsking(false);
    }
  };

  const backLink = { href: "#admin/" + back, onClick: (e) => { e.preventDefault(); go(back); }, label: "Players" };
  if (done) {
    return (
      <>
        <PageHead title="Player removed" back={backLink} />
        <Notice tone="ok" action={<NavLink go={go} to={back} className="btn sm">Back to players</NavLink>}>{done.text}</Notice>
      </>
    );
  }
  if (!player) {
    return (
      <>
        <PageHead title="Remove player" back={backLink} />
        <Notice tone="warn">This player is not in {world.name} (any more).</Notice>
      </>
    );
  }
  return (
    <form onSubmit={ask} className="narrow">
      <PageHead title={`Remove ${player.name}`} sub={`Takes ${player.name} out of ${world.name}. The account itself is kept.`} back={backLink} />
      {problem ? <ErrorNote>{problem}</ErrorNote> : null}
      <Card title="What should happen to their villages?" sub={`${plural(player.villages, "village")}, ${fmtInt(player.points)} points`}>
        <div className="choices">
          {HANDLING.map(([value, title, text]) => (
            <label key={value} className={"choice" + (handle === value ? " on" : "")}>
              <input type="radio" name="villagehandle" value={value} checked={handle === value} onChange={() => setHandle(value)} />
              <span>
                <strong>{title}</strong>
                <em>{text}</em>
              </span>
            </label>
          ))}
        </div>
        {handle === "move" ? (
          <Field label="Receiving player" htmlFor="input_move_user" className="mt">
            <input id="input_move_user" className="inp" type="text" value={moveUser} onChange={(e) => setMoveUser(e.target.value)} placeholder="Username" />
          </Field>
        ) : null}
      </Card>
      <div className="formbar">
        <NavLink go={go} to={back} className="btn">Cancel</NavLink>
        <Btn type="submit" variant="danger" icon="trash">Remove player</Btn>
      </div>
      {asking ? (
        <ConfirmDialog title={`Remove ${player.name}?`} danger confirmLabel="Remove player" busy={busy} onCancel={() => setAsking(false)} onConfirm={run}>
          <p>
            {handle === "delete"
              ? `${plural(player.villages, "village")} will be deleted.`
              : handle === "move"
                ? `${plural(player.villages, "village")} will be transferred to ${moveUser.trim()}.`
                : `${plural(player.villages, "village")} will become barbarian villages.`}
          </p>
        </ConfirmDialog>
      ) : null}
    </form>
  );
}

// ---- NPC players -----------------------------------------------------------------------------------------------
export function CreateNpcs({ world, go }) {
  const toast = useToast();
  const [count, setCount] = useState(3);
  const [prefix, setPrefix] = useState("");
  const [lo, setLo] = useState(0);
  const [hi, setHi] = useState(40);
  const [archetype, setArchetype] = useState("random");
  const [skill, setSkill] = useState(null); // null = random per NPC
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [created, setCreated] = useState([]);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setCreated([]);
    setBusy(true);
    try {
      const body = { count, minDevelopment: lo, maxDevelopment: hi };
      if (prefix.trim()) body.namePrefix = prefix.trim();
      if (archetype !== "random") body.archetype = archetype;
      if (skill != null) body.skill = skill;
      const r = await A.createNpcs(world.id, body);
      setCreated(r.created ?? []);
      toast(`${plural((r.created ?? []).length, "NPC player")} created.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="narrow">
      <PageHead title="NPC players" sub={`AI-controlled players build, recruit and attack on their own. Each one gets a new village in ${world.name}.`} />
      {error ? <ErrorNote>{error}</ErrorNote> : null}
      <Card title="Create NPC players">
        <div className="fgrid">
          <Field label="Amount" htmlFor="npc_count">
            <Stepper id="npc_count" value={count} onChange={setCount} min={1} max={200} big />
          </Field>
          <Field label="Name prefix" htmlFor="npc_prefix" hint="Optional. Leave empty for generated names.">
            <input id="npc_prefix" className="inp" type="text" value={prefix} onChange={(e) => setPrefix(e.target.value)} />
          </Field>
        </div>
        <div className="fgrid">
          <Field label="Archetype" htmlFor="npc_arch" hint="The role the NPCs play (what they build, recruit and attack). Random gives every NPC its own.">
            <select id="npc_arch" className="inp" value={archetype} onChange={(e) => setArchetype(e.target.value)}>
              <option value="random">Random</option>
              {["FARMER", "RAIDER", "TURTLE", "CONQUEROR", "TRADER", "BALANCED"].map((a) => <option key={a} value={a}>{a.charAt(0) + a.slice(1).toLowerCase()}</option>)}
            </select>
          </Field>
          <Field label="Skill" htmlFor="npc_skill" hint="0 to 100, scales the world's difficulty for these NPCs. Empty = random per NPC.">
            <input id="npc_skill" className="inp" type="number" min="0" max="100" value={skill ?? ""} placeholder="random"
              onChange={(e) => setSkill(e.target.value === "" ? null : Math.max(0, Math.min(100, Number(e.target.value) || 0)))} />
          </Field>
        </div>
        <Field label={`Starting development · ${lo}% to ${hi}%`} hint="Every NPC gets its own randomized village within this range (0% is a fresh village, 100% is fully grown), placed at a random spot near the centre.">
          <DualRange lo={lo} hi={hi} onChange={(a, b) => { setLo(a); setHi(b); }} />
        </Field>
        <div className="formbar inline">
          <Btn type="submit" variant="primary" icon="plus" busy={busy}>Create {plural(count, "NPC player")}</Btn>
        </div>
      </Card>
      {created.length ? (
        <Card title={`Created ${plural(created.length, "player")}`} flush>
          <table className="tbl">
            <thead>
              <tr><th>Name</th><th>Village</th></tr>
            </thead>
            <tbody>
              {created.map((c) => (
                <tr key={c.accountId}>
                  <td><strong>{c.name}</strong><Chip tone="npc">NPC</Chip></td>
                  <td><NavLink go={go} to={`w/${world.id}/villages/edit/${c.villageId}`}>Village {c.villageId}</NavLink></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      ) : null}
    </form>
  );
}

// ---- Villages list -----------------------------------------------------------------------------------------------
const PAGE = 100;

export function WorldVillages({ world, go }) {
  const { data, error, reload } = useLoad(() => A.worldVillages(world.id), [world.id]);
  const toast = useToast();
  const [filter, setFilter] = useState("");
  const [kind, setKind] = useState("all");
  const [limit, setLimit] = useState(PAGE);
  const [confirm, setConfirm] = useState(null);
  const [busy, setBusy] = useState(false);

  if (error) return <ErrorNote>{error}</ErrorNote>;
  if (!data) return <Loading />;

  const isBarb = (v) => v.ownerType === "BARBARIAN" || !v.ownerName;
  const f = filter.trim().toLowerCase();
  const rows = data.filter((v) => {
    if (kind === "barbarian" && !isBarb(v)) return false;
    if (kind === "player" && (isBarb(v) || v.npc)) return false;
    if (kind === "npc" && !v.npc) return false;
    return !f || `${v.id} ${v.name} ${v.x}|${v.y} ${v.ownerName ?? "barbarian"}`.toLowerCase().includes(f);
  });
  const count = (fn) => data.filter(fn).length;

  const del = async () => {
    setBusy(true);
    try {
      await A.deleteVillage(confirm.id);
      toast(`Village ${confirm.name} (${confirm.x}|${confirm.y}) deleted.`);
      setConfirm(null);
      reload();
    } catch (err) {
      toast(err.message, "bad");
    } finally {
      setBusy(false);
    }
  };

  const filters = [
    ["all", "All", data.length],
    ["player", "Players", count((v) => !isBarb(v) && !v.npc)],
    ["npc", "NPC", count((v) => v.npc)],
    ["barbarian", "Barbarian", count(isBarb)],
  ];

  return (
    <>
      <PageHead
        title="Villages"
        sub={`${plural(data.length, "village")} in ${world.name}.`}
        actions={<NavLink go={go} to={`w/${world.id}/villages/create`} className="btn primary"><Icon name="plus" /> Create villages</NavLink>}
      />
      <Card flush>
        <div className="toolbar">
          <div className="seg" role="group" aria-label="Owner filter">
            {filters.map(([value, label, n]) => (
              <button key={value} type="button" className={kind === value ? "on" : ""} onClick={() => { setKind(value); setLimit(PAGE); }}>
                {label} <span className="seg-n">{fmtInt(n)}</span>
              </button>
            ))}
          </div>
          <input className="inp search" placeholder="Search name, owner or x|y" aria-label="Search villages" value={filter} onChange={(e) => { setFilter(e.target.value); setLimit(PAGE); }} />
        </div>
        <table className="tbl">
          <thead>
            <tr>
              <th className="num">ID</th>
              <th>Village</th>
              <th>Coordinates</th>
              <th>Owner</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.slice(0, limit).map((v) => (
              <tr key={v.id}>
                <td className="num dim">{v.id}</td>
                <td><NavLink go={go} to={`w/${world.id}/villages/edit/${v.id}`} className="strong-link">{v.name}</NavLink></td>
                <td className="mono">{v.x}|{v.y}</td>
                <td><OwnerChips v={v} /></td>
                <td className="acts">
                  <NavLink go={go} to={`w/${world.id}/villages/edit/${v.id}`} className="btn sm"><Icon name="edit" size={14} /> Edit</NavLink>
                  <Btn size="sm" variant="danger-ghost" icon="trash" aria-label={"Delete " + v.name} onClick={() => setConfirm(v)} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length ? <Empty title="No villages found">{data.length ? "Try a different filter." : "Use “Create villages” to populate this world."}</Empty> : null}
        {rows.length > limit ? (
          <div className="more">
            Showing {fmtInt(limit)} of {fmtInt(rows.length)}
            <Btn size="sm" onClick={() => setLimit((l) => l + PAGE * 5)}>Show more</Btn>
          </div>
        ) : null}
      </Card>
      {confirm ? (
        <ConfirmDialog title="Delete this village?" danger confirmLabel="Delete village" busy={busy} onCancel={() => setConfirm(null)} onConfirm={del}>
          <p><strong>{confirm.name}</strong> ({confirm.x}|{confirm.y}) will be removed from the map. This cannot be undone.</p>
        </ConfirmDialog>
      ) : null}
    </>
  );
}
