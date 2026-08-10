import { useState } from "react";
import { api } from "../../api";
import { BuildingGrid, ResourceFields, TroopList } from "./editors.jsx";
import { Btn, Card, Chip, ErrorNote, Field, Loading, NavLink, Notice, PageHead, useToast } from "./kit.jsx";
import { BUILDINGS, UNITS, buildingImg, buildingName, countProblems, fixRequirements, unitName, UNIT_BY_TYPE } from "./game.js";
import { fmtInt, useLoad } from "./util.jsx";
import { BONUS } from "../../lib/bonus";

const A = api.admin;

const fromVillage = (v) => ({
  name: v.name ?? "",
  res: { wood: String(Math.floor(v.wood ?? 0)), clay: String(Math.floor(v.clay ?? 0)), iron: String(Math.floor(v.iron ?? 0)) },
  levels: Object.fromEntries(BUILDINGS.map((b) => [b.type, v.buildings?.[b.type] ?? 0])),
  units: Object.fromEntries(UNITS.map((u) => [u.type, v.units?.[u.type] ?? 0])),
  bonus: String(v.bonus ?? 0),
});

// ---- village editor -----------------------------------------------------------------------------------------------
export function VillageEdit({ world, villageId, catalog, go }) {
  const { data, error, reload } = useLoad(() => A.village(villageId), [villageId]);
  if (error) return <ErrorNote>{error}</ErrorNote>;
  if (!data) return <Loading />;
  return <VillageEditForm key={data.id} v={data} world={world} catalog={catalog} go={go} reload={reload} />;
}

function VillageEditForm({ v, world, catalog, go }) {
  const toast = useToast();
  const init = fromVillage(v);
  const [name, setName] = useState(init.name);
  const [res, setRes] = useState(init.res);
  const [levels, setLevels] = useState(init.levels);
  const [units, setUnits] = useState(init.units);
  const [bonus, setBonus] = useState(init.bonus);
  const [q, setQ] = useState(v);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [finishing, setFinishing] = useState(false);
  const listPath = `w/${world.id}/villages`;

  const apply = (nv) => {
    const f = fromVillage(nv);
    setQ(nv);
    setName(f.name);
    setRes(f.res);
    setLevels(f.levels);
    setUnits(f.units);
    setBonus(f.bonus);
  };

  const save = async (e) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return setError("Please give the village a name.");
    setSaving(true);
    try {
      const nv = await A.updateVillage(v.id, {
        name: name.trim(),
        wood: Number(res.wood || 0),
        clay: Number(res.clay || 0),
        iron: Number(res.iron || 0),
        buildings: levels,
        units,
        bonus: Number(bonus),
      });
      apply(nv);
      toast("Village saved.");
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const finish = async () => {
    setError(null);
    setFinishing(true);
    try {
      const r = await A.finishQueues(v.id);
      apply(await A.village(v.id));
      toast(`Finished ${r?.finishedBuilds ?? 0} construction(s) and ${r?.finishedTrainings ?? 0} recruitment(s).`);
    } catch (err) {
      setError(err.message);
    } finally {
      setFinishing(false);
    }
  };

  const buildQueue = q.buildQueue ?? [];
  const trainQueue = q.trainQueue ?? [];
  const problems = countProblems(catalog, levels, units);
  const barb = q.ownerType === "BARBARIAN" || !q.ownerName;

  return (
    <form onSubmit={save} className="edit">
      <PageHead
        title={q.name}
        sub={
          <span className="subline">
            <span className="mono">{q.x}|{q.y}</span>
            <span>{q.worldName ?? world.name}</span>
            {barb ? <Chip tone="barb">barbarian</Chip> : <strong>{q.ownerName}</strong>}
            <span className="dim">village #{q.id}</span>
          </span>
        }
        back={{ href: "#admin/" + listPath, onClick: (e) => { e.preventDefault(); go(listPath); }, label: "Villages" }}
      />
      {error ? <ErrorNote>{error}</ErrorNote> : null}
      <div className="create-grid custom">
        <div className="col">
          <Card
            title="Buildings"
            actions={<Btn size="sm" icon="wand" disabled={!problems} onClick={() => setLevels((l) => fixRequirements(catalog, l, units))}>Fix requirements</Btn>}
          >
            {problems ? <Notice tone="warn">{problems} {problems === 1 ? "entry has" : "entries have"} unmet requirements.</Notice> : null}
            <BuildingGrid catalog={catalog} levels={levels} onChange={(type, n) => setLevels((l) => ({ ...l, [type]: n }))} />
          </Card>
          <Card title="Troops">
            <TroopList levels={levels} units={units} onChange={(type, n) => setUnits((u) => ({ ...u, [type]: n }))} />
          </Card>
        </div>
        <div className="col sticky">
          <Card title="Details">
            <div className="stack">
              <Field label="Village name" htmlFor="village_name">
                <input id="village_name" className="inp" value={name} onChange={(e) => setName(e.target.value)} />
              </Field>
              <Field label="Bonus" hint="Bonus villages keep their bonus when conquered. It has no effect in worlds whose Bonus villages setting is off.">
                <select id="village_bonus" className="inp" value={bonus} onChange={(e) => setBonus(e.target.value)}>
                  <option value="0">none</option>
                  {Object.entries(BONUS).map(([code, b]) => <option key={code} value={code}>{b.text}</option>)}
                </select>
              </Field>
              <Field label="Resources">
                <ResourceFields res={res} onChange={(k, val) => setRes((o) => ({ ...o, [k]: val }))} />
              </Field>
            </div>
          </Card>
          <Card
            title="Queues"
            actions={
              <Btn size="sm" icon="bolt" busy={finishing} disabled={!buildQueue.length && !trainQueue.length} onClick={finish}>
                Finish all now
              </Btn>
            }
          >
            <h4 className="grp">Constructions</h4>
            {buildQueue.length ? (
              <ul className="queue">
                {buildQueue.map((x, i) => (
                  <li key={i}>
                    <img src={buildingImg(x.type, x.targetLevel)} alt="" width="35" height="24" />
                    <span>{buildingName(x.type)}</span>
                    <b>to level {x.targetLevel}</b>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="dim">Nothing under construction.</p>
            )}
            <h4 className="grp">Recruitments</h4>
            {trainQueue.length ? (
              <ul className="queue">
                {trainQueue.map((x, i) => (
                  <li key={i}>
                    <img src={UNIT_BY_TYPE[x.type]?.icon} alt="" width="18" height="18" />
                    <span>{unitName(x.type)}</span>
                    <b>{fmtInt(x.count)} units</b>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="dim">Nothing in training.</p>
            )}
          </Card>
        </div>
      </div>
      <div className="formbar sticky-bar">
        <NavLink go={go} to={listPath} className="btn">Cancel</NavLink>
        <Btn type="submit" variant="primary" icon="save" busy={saving}>Save village</Btn>
      </div>
    </form>
  );
}
