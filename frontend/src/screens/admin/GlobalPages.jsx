import { useState } from "react";
import { api } from "../../api";
import { Btn, Card, Chip, ConfirmDialog, Empty, ErrorNote, Field, Loading, NavLink, Notice, PageHead, useToast } from "./kit.jsx";
import { fmtBytes, fmtDate, fmtInt, fmtUptime, plural, toNum, useLoad } from "./util.jsx";
import { SettingsGroups, initialValues } from "./SettingsForm.jsx";

const A = api.admin;

function Stat({ label, value, sub, tone }) {
  return (
    <div className={"stat" + (tone ? " " + tone : "")}>
      <span className="stat-l">{label}</span>
      <strong>{value}</strong>
      {sub ? <span className="stat-s">{sub}</span> : null}
    </div>
  );
}

function KV({ rows }) {
  return (
    <dl className="kv">
      {rows.map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{v}</dd>
        </div>
      ))}
    </dl>
  );
}

// ---- Dashboard ------------------------------------------------------------------------------------------------
export function Dashboard({ worlds, go }) {
  const { data, error, reload } = useLoad(() => A.dashboard(), []);
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const backup = async () => {
    setBusy(true);
    try {
      const b = await A.backup();
      toast(`Backup created: ${b.name} (${fmtBytes(b.sizeBytes)})`);
      reload();
    } catch (err) {
      toast(err.message, "bad");
    } finally {
      setBusy(false);
    }
  };

  if (error) return <ErrorNote>{error}</ErrorNote>;
  if (!data) return <Loading />;
  const v = data.villages ?? {};
  const totalVillages = (v.player ?? 0) + (v.npc ?? 0) + (v.barbarian ?? 0);
  const share = (n) => (totalVillages ? (n / totalVillages) * 100 : 0);
  const backups = [...(data.backups ?? [])].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));

  return (
    <>
      <PageHead
        title="Dashboard"
        sub="Live overview of the running server."
        actions={<Btn icon="refresh" onClick={reload}>Refresh</Btn>}
      />
      <div className="stats">
        <Stat label="Accounts" value={fmtInt(data.accounts)} sub={`${fmtInt(data.npcs)} of them NPC`} />
        <Stat label="Worlds" value={fmtInt(data.worlds)} sub={`${fmtInt(totalVillages)} villages in total`} />
        <Stat label="Constructions" value={fmtInt(data.queuedBuilds)} sub="queued right now" tone={data.queuedBuilds ? "accent" : ""} />
        <Stat label="Recruitments" value={fmtInt(data.queuedTrainings)} sub="queued right now" tone={data.queuedTrainings ? "accent" : ""} />
        <Stat label="Troop movements" value={fmtInt(data.movements)} sub="on their way" tone={data.movements ? "accent" : ""} />
      </div>

      <div className="cols2">
        <Card title="Villages" sub={`${fmtInt(totalVillages)} across all worlds`}>
          <div className="bar" role="img" aria-label="Village ownership">
            <span className="seg-player" style={{ width: share(v.player ?? 0) + "%" }} />
            <span className="seg-npc" style={{ width: share(v.npc ?? 0) + "%" }} />
            <span className="seg-barb" style={{ width: share(v.barbarian ?? 0) + "%" }} />
          </div>
          <ul className="legend">
            <li><i className="seg-player" /> Player villages <strong>{fmtInt(v.player)}</strong></li>
            <li><i className="seg-npc" /> NPC villages <strong>{fmtInt(v.npc)}</strong></li>
            <li><i className="seg-barb" /> Barbarian villages <strong>{fmtInt(v.barbarian)}</strong></li>
          </ul>
        </Card>
        <Card title="Server">
          <KV rows={[["Uptime", fmtUptime(data.uptimeSeconds)], ["Java", data.javaVersion ?? "-"], ["Database size", fmtBytes(data.dbSizeBytes)]]} />
        </Card>
      </div>

      <Card title="Worlds" flush actions={<Btn size="sm" icon="plus" onClick={() => go("create_world")}>New world</Btn>}>
        <table className="tbl">
          <thead>
            <tr>
              <th>World</th>
              <th className="num">Speed</th>
              <th className="num">Players</th>
              <th className="num">Villages</th>
              <th className="num">Barbarian</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {worlds.map((w) => (
              <tr key={w.id}>
                <td><NavLink go={go} to={"w/" + w.id} className="strong-link">{w.name}</NavLink></td>
                <td className="num">x{Number(w.speed)}</td>
                <td className="num">{fmtInt(w.players)}{w.npcs ? <span className="dim"> +{w.npcs} NPC</span> : null}</td>
                <td className="num">{fmtInt(w.villages)}</td>
                <td className="num">{fmtInt(w.barbarians)}</td>
                <td className="acts">
                  <NavLink go={go} to={`w/${w.id}/villages/create`} className="btn sm">Create villages</NavLink>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card
        title="Backups"
        sub="Database snapshots stored on the server (backend/backups)."
        flush
        actions={<Btn variant="primary" size="sm" icon="db" busy={busy} onClick={backup}>Create backup</Btn>}
      >
        {backups.length ? (
          <table className="tbl">
            <thead>
              <tr>
                <th>File</th>
                <th className="num">Size</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {backups.map((b) => (
                <tr key={b.name}>
                  <td className="mono">{b.name}</td>
                  <td className="num">{fmtBytes(b.sizeBytes)}</td>
                  <td>{fmtDate(b.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <Empty title="No backups yet">Create one before you try something risky.</Empty>
        )}
      </Card>
    </>
  );
}

// ---- Accounts -------------------------------------------------------------------------------------------------
export function Accounts({ account }) {
  const { data, error, reload } = useLoad(() => A.accounts(), []);
  const toast = useToast();
  const [dlg, setDlg] = useState(null); // { kind: "password" | "delete", acc }
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [q, setQ] = useState("");

  const run = async (fn, okText) => {
    setBusy(true);
    try {
      await fn();
      toast(okText);
      setDlg(null);
      setPassword("");
      reload();
    } catch (err) {
      toast(err.message, "bad");
    } finally {
      setBusy(false);
    }
  };

  if (error) return <ErrorNote>{error}</ErrorNote>;
  if (!data) return <Loading />;
  const f = q.trim().toLowerCase();
  const rows = f ? data.filter((a) => a.username.toLowerCase().includes(f)) : data;

  return (
    <>
      <PageHead title="Accounts" sub={`${plural(data.length, "account")} on this server, including NPC players.`} />
      <Card
        flush
        title="All accounts"
        actions={<input className="inp search" placeholder="Search accounts" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search accounts" />}
      >
        <table className="tbl">
          <thead>
            <tr>
              <th className="num">ID</th>
              <th>Account</th>
              <th>Worlds</th>
              <th>Created</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((a) => (
              <tr key={a.id}>
                <td className="num dim">{a.id}</td>
                <td>
                  <strong>{a.username}</strong>
                  {account && account.id === a.id ? <Chip tone="info">you</Chip> : null}
                  {a.admin ? <Chip tone="admin">admin</Chip> : null}
                  {a.npc ? <Chip tone="npc">NPC</Chip> : null}
                </td>
                <td>
                  {(a.worlds ?? []).length ? (
                    <div className="tags">
                      {a.worlds.map((w) => (
                        <span className="tag" key={w.worldId}>{w.worldName} <b>{fmtInt(w.villages)}</b></span>
                      ))}
                    </div>
                  ) : (
                    <span className="dim">-</span>
                  )}
                </td>
                <td className="dim">{fmtDate(a.createdAt)}</td>
                <td className="acts">
                  {a.npc ? null : (
                    <Btn size="sm" icon="shield" onClick={() => run(() => A.setAdmin(a.id, !a.admin), a.admin ? `${a.username} is no longer an admin.` : `${a.username} is now an admin.`)}>
                      {a.admin ? "Remove admin" : "Make admin"}
                    </Btn>
                  )}
                  {a.npc ? null : (
                    <Btn size="sm" icon="key" onClick={() => { setPassword(""); setDlg({ kind: "password", acc: a }); }}>Password</Btn>
                  )}
                  <Btn size="sm" variant="danger-ghost" icon="trash" onClick={() => setDlg({ kind: "delete", acc: a })} aria-label={"Delete " + a.username} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!rows.length ? <Empty title="No accounts match" /> : null}
      </Card>

      {dlg?.kind === "delete" ? (
        <ConfirmDialog
          title={`Delete account "${dlg.acc.username}"?`}
          danger
          confirmLabel="Delete account"
          busy={busy}
          onCancel={() => setDlg(null)}
          onConfirm={() => run(() => A.deleteAccount(dlg.acc.id), `Account ${dlg.acc.username} deleted.`)}
        >
          <p>All villages of this account in every world will be removed. This cannot be undone.</p>
        </ConfirmDialog>
      ) : null}
      {dlg?.kind === "password" ? (
        <ConfirmDialog
          title={`Reset password of "${dlg.acc.username}"`}
          confirmLabel="Set password"
          busy={busy}
          disabled={!password}
          onCancel={() => setDlg(null)}
          onConfirm={() => run(() => A.setPassword(dlg.acc.id, password), `Password of ${dlg.acc.username} changed.`)}
        >
          <Field label="New password" htmlFor="new_password">
            <input id="new_password" className="inp" type="text" autoFocus value={password} onChange={(e) => setPassword(e.target.value)} onKeyDown={(e) => e.key === "Enter" && password && run(() => A.setPassword(dlg.acc.id, password), `Password of ${dlg.acc.username} changed.`)} />
          </Field>
        </ConfirmDialog>
      ) : null}
    </>
  );
}

// ---- Create world ---------------------------------------------------------------------------------------------
export function CreateWorld({ catalog, reloadWorlds, go }) {
  const toast = useToast();
  const [name, setName] = useState("");
  const [speed, setSpeed] = useState("1");
  const [values, setValues] = useState(() => initialValues(catalog));
  const [error, setError] = useState(null);
  const [created, setCreated] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setCreated(null);
    const sp = toNum(speed);
    if (!name.trim()) return setError("Please give the world a name.");
    if (sp === undefined || Number.isNaN(sp) || sp <= 0) return setError("Speed must be a positive number.");
    setBusy(true);
    try {
      const w = await A.createWorld({ name: name.trim(), speed: sp, settings: values });
      setCreated(w);
      toast(`World "${w.name}" created.`);
      setName("");
      reloadWorlds();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="narrow">
      <PageHead title="New world" sub="Configure a fresh world. Every setting can be changed later." />
      {created ? (
        <Notice tone="ok" action={<NavLink go={go} to={"w/" + created.id} className="btn sm">Open {created.name}</NavLink>}>
          The world <strong>{created.name}</strong> has been created.
        </Notice>
      ) : null}
      {error ? <ErrorNote>{error}</ErrorNote> : null}
      <Card title="Basics">
        <div className="fgrid">
          <Field label="World name" htmlFor="world_name_new">
            <input id="world_name_new" className="inp" type="text" autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Welt 6" />
          </Field>
          <Field label="Game speed" htmlFor="world_speed_new" hint="1 = normal, 100 = a hundred times faster.">
            <input id="world_speed_new" className="inp" type="text" inputMode="decimal" value={speed} onChange={(e) => setSpeed(e.target.value)} />
          </Field>
        </div>
      </Card>
      <SettingsGroups catalog={catalog} values={values} onChange={(k, v) => setValues((o) => ({ ...o, [k]: v }))} />
      <div className="formbar">
        <Btn type="submit" variant="primary" busy={busy} icon="plus">Create world</Btn>
      </div>
    </form>
  );
}
