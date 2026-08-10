// Village creator: "Randomized" (rule-abiding random layouts, live sample preview) and "Custom" (hand-made layout).
import { useEffect, useRef, useState } from "react";
import { api } from "../../api";
import { BuildingGrid, ResourceFields, TroopList } from "./editors.jsx";
import { Btn, Card, DualRange, ErrorNote, Field, Icon, NavLink, Notice, PageHead, Segmented, Stepper, Tabs, cx } from "./kit.jsx";
import {
  BUILDINGS,
  RESOURCES,
  UNITS,
  buildingImg,
  countProblems,
  emptyUnits,
  fixRequirements,
  stageOf,
  startLayout,
} from "./game.js";
import { fmtInt, plural, toNum } from "./util.jsx";

const A = api.admin;
const STAGE_TICKS = [
  [0, "Freshly founded"],
  [25, "Small"],
  [50, "Established"],
  [75, "Well developed"],
  [100, "Fully grown"],
];
const AMOUNT_PRESETS = [5, 10, 25, 50, 100];

// optional spread radius: "" -> undefined, invalid -> NaN
const parseSpread = (s) => {
  const n = toNum(s);
  return n === undefined ? undefined : n;
};

// ---- live preview -------------------------------------------------------------------------------------------
function usePreview(development, seq) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const tick = useRef(0);
  useEffect(() => {
    const my = ++tick.current;
    setState((s) => ({ ...s, loading: true }));
    const t = window.setTimeout(() => {
      A.randomVillagePreview(development).then(
        (data) => tick.current === my && setState({ data, error: null, loading: false }),
        (e) => tick.current === my && setState((s) => ({ data: s.data, error: e.message, loading: false })),
      );
    }, 320);
    return () => window.clearTimeout(t);
  }, [development, seq]);
  return state;
}

function PreviewCard({ preview, onRoll, at, setAt, lo, hi, dev }) {
  const { data, error, loading } = preview;
  return (
    <Card
      className="preview"
      title="Live preview"
      sub={`One sample village at ${dev}% development (${stageOf(dev).toLowerCase()})`}
      actions={
        <Btn size="sm" icon="dice" onClick={onRoll} busy={loading && !data}>
          Roll another
        </Btn>
      }
    >
      <div className="pv-at">
        <span>Preview at</span>
        <Segmented
          ariaLabel="Preview development"
          value={at}
          onChange={setAt}
          options={[
            { value: "min", label: `Low · ${lo}%` },
            { value: "mid", label: `Middle · ${Math.round((lo + hi) / 2)}%` },
            { value: "max", label: `High · ${hi}%` },
          ]}
        />
      </div>
      {error ? <ErrorNote>{error}</ErrorNote> : null}
      {data ? (
        <div className={cx("pv", loading && "stale")}>
          <div className="pv-sum">
            <div>
              <span>Points</span>
              <strong>{fmtInt(data.points)}</strong>
            </div>
            <div>
              <span>Buildings</span>
              <strong>{fmtInt(Object.values(data.buildings).reduce((a, b) => a + b, 0))}</strong>
              <em>levels in total</em>
            </div>
            <div>
              <span>Troops</span>
              <strong>{fmtInt(Object.values(data.units).reduce((a, b) => a + b, 0))}</strong>
              <em>in the village</em>
            </div>
          </div>

          <h4 className="grp">Buildings</h4>
          <div className="mgrid">
            {BUILDINGS.map((b) => {
              const level = data.buildings[b.type] ?? 0;
              return (
                <div key={b.type} className={cx("mt", level === 0 && "dim")} title={`${b.name}: level ${level}`}>
                  <img src={buildingImg(b.type, level)} alt="" width="35" height="24" />
                  <span className="mt-n">{b.name}</span>
                  <b>{level}</b>
                </div>
              );
            })}
          </div>

          <h4 className="grp">Troops</h4>
          <div className="ugrid">
            {UNITS.map((u) => (
              <div key={u.type} className={cx("ut", !(data.units[u.type] > 0) && "dim")}>
                <img src={u.img} alt="" width="36" height="36" />
                <b>{fmtInt(data.units[u.type] ?? 0)}</b>
                <span>{u.name}</span>
              </div>
            ))}
          </div>

          <h4 className="grp">Resources</h4>
          <div className="rgrid">
            {RESOURCES.map((r) => (
              <div key={r.key} className="rt">
                <img src={r.img} alt="" width="18" height="16" />
                <span>{r.label}</span>
                <b>{fmtInt(data[r.key])}</b>
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="pv-empty">{loading ? <><span className="spin" /> Rolling a sample village</> : "No preview available."}</div>
      )}
    </Card>
  );
}

// ---- Randomized ---------------------------------------------------------------------------------------------
function Randomized({ world, go }) {
  const [amount, setAmount] = useState(10);
  const [lo, setLo] = useState(20);
  const [hi, setHi] = useState(70);
  const [spread, setSpread] = useState("");
  const [at, setAt] = useState("mid");
  const [seq, setSeq] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const dev = at === "min" ? lo : at === "max" ? hi : Math.round((lo + hi) / 2);
  const preview = usePreview(dev, seq);

  const create = async () => {
    setError(null);
    setResult(null);
    const sp = parseSpread(spread);
    if (Number.isNaN(sp) || (sp !== undefined && sp < 0)) return setError("Spread radius must be a positive number (or empty).");
    setBusy(true);
    try {
      const body = { amount, minDevelopment: lo, maxDevelopment: hi };
      if (sp !== undefined) body.spread = sp;
      const r = await A.createRandomBarbarians(world.id, body);
      setResult(r);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="create-grid">
      <div className="col">
        <Card title="Random barbarian villages" sub="Every village gets its own random level of development.">
          <div className="stack">
            <Field label="How many villages?" htmlFor="rnd_amount">
              <div className="amount">
                <Stepper id="rnd_amount" big value={amount} min={1} max={500} onChange={setAmount} />
                <div className="presets">
                  {AMOUNT_PRESETS.map((n) => (
                    <button key={n} type="button" className={cx("preset", n === amount && "on")} onClick={() => setAmount(n)}>
                      {n}
                    </button>
                  ))}
                </div>
              </div>
            </Field>

            <Field label="How developed should they be?" className="devrange">
              <div className="dev-read">
                <span className="dev-pill">
                  <b>{lo}%</b>
                  <em>{stageOf(lo)}</em>
                </span>
                <span className="dev-to">to</span>
                <span className="dev-pill">
                  <b>{hi}%</b>
                  <em>{stageOf(hi)}</em>
                </span>
              </div>
              <DualRange lo={lo} hi={hi} onChange={(a, b) => { setLo(a); setHi(b); }} />
              <div className="dual-ticks" aria-hidden="true">
                {STAGE_TICKS.map(([p, label]) => (
                  <span key={p} style={{ left: p + "%" }} className={p === 0 ? "first" : p === 100 ? "last" : ""}>
                    <i />
                    {label}
                  </span>
                ))}
              </div>
              <div className="hint">0% is a freshly founded village, 100% a fully grown one. Each village picks a random value in your range.</div>
            </Field>

            <Field label="Spread radius" htmlFor="rnd_spread" hint="Optional. How many fields around the centre the villages are scattered over. Empty = automatic.">
              <input id="rnd_spread" className="inp narrow-inp" inputMode="numeric" placeholder="automatic" value={spread} onChange={(e) => setSpread(e.target.value.replace(/[^\d.,]/g, ""))} />
            </Field>

            <div className="rules">
              <Icon name="shield" />
              <p>
                Layouts are random, but they always follow the game&apos;s rules: no Academy without Smithy 20, troops only with the buildings that train them,
                population that fits the farm, and resources that fit the warehouse.
              </p>
            </div>

            {error ? <ErrorNote>{error}</ErrorNote> : null}
            <Btn variant="primary" className="big" icon="wand" busy={busy} onClick={create}>
              Create {plural(amount, "village")}
            </Btn>
          </div>
        </Card>

        {result ? (
          <Card className="result" title={`${plural((result.created ?? []).length, "village")} created`} sub={world.name}>
            <div className="rstats">
              <div><span>Created</span><strong>{fmtInt((result.created ?? []).length)}</strong></div>
              <div><span>Lowest points</span><strong>{fmtInt(result.minPoints)}</strong></div>
              <div><span>Average points</span><strong>{fmtInt(result.avgPoints)}</strong></div>
              <div><span>Highest points</span><strong>{fmtInt(result.maxPoints)}</strong></div>
            </div>
            <div className="formbar inline">
              <NavLink go={go} to={`w/${world.id}/villages`} className="btn primary">
                View in villages list <Icon name="arrow" size={14} />
              </NavLink>
            </div>
          </Card>
        ) : null}
      </div>
      <PreviewCard preview={preview} onRoll={() => setSeq((s) => s + 1)} at={at} setAt={setAt} lo={lo} hi={hi} dev={dev} />
    </div>
  );
}

// ---- Custom -------------------------------------------------------------------------------------------------
function Custom({ world, catalog, go }) {
  const [levels, setLevels] = useState(() => startLayout(catalog));
  const [units, setUnits] = useState(emptyUnits);
  const [res, setRes] = useState({ wood: "", clay: "", iron: "" });
  const [amount, setAmount] = useState(1);
  const [spread, setSpread] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [created, setCreated] = useState(null);

  const problems = countProblems(catalog, levels, units);
  const fix = () => setLevels((l) => fixRequirements(catalog, l, units));
  const reset = () => {
    setLevels(startLayout(catalog));
    setUnits(emptyUnits());
    setRes({ wood: "", clay: "", iron: "" });
    setCreated(null);
    setError(null);
  };

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setCreated(null);
    const sp = parseSpread(spread);
    if (Number.isNaN(sp) || (sp !== undefined && sp < 0)) return setError("Spread radius must be a positive number (or empty).");
    const body = { amount, buildings: levels, units };
    if (Object.values(res).some((v) => v !== "")) {
      body.resources = { wood: Number(res.wood || 0), clay: Number(res.clay || 0), iron: Number(res.iron || 0) };
    }
    if (sp !== undefined) body.spread = sp;
    setBusy(true);
    try {
      const r = await A.createBarbarians(world.id, body);
      setCreated(r.created ?? []);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const totalTroops = Object.values(units).reduce((a, b) => a + b, 0);
  return (
    <form className="create-grid custom" onSubmit={submit}>
      <div className="col">
        <Card
          title="Buildings"
          sub="Set a level for every building. Requirements are checked as you type."
          actions={
            <>
              <Btn size="sm" icon="reset" onClick={reset}>Reset</Btn>
              <Btn size="sm" variant={problems ? "primary" : undefined} icon="wand" onClick={fix} disabled={!problems}>Fix requirements</Btn>
            </>
          }
        >
          {problems ? (
            <Notice tone="warn">
              {plural(problems, "entry", "entries")} with unmet requirements. <em>Fix requirements</em> raises the missing prerequisite levels for you.
            </Notice>
          ) : (
            <Notice tone="ok">All requirements are met.</Notice>
          )}
          <BuildingGrid catalog={catalog} levels={levels} onChange={(type, n) => setLevels((l) => ({ ...l, [type]: n }))} />
        </Card>
        <Card title="Troops" sub="Units standing in every new village. Training buildings must be high enough.">
          <TroopList levels={levels} units={units} onChange={(type, n) => setUnits((u) => ({ ...u, [type]: n }))} />
        </Card>
      </div>
      <div className="col sticky">
        <Card title="Create">
          <div className="stack">
            <Field label="How many villages?" htmlFor="cus_amount">
              <Stepper id="cus_amount" big value={amount} min={1} max={500} onChange={setAmount} />
            </Field>
            <Field label="Spread radius" htmlFor="cus_spread" hint="Optional, in fields. Empty = automatic.">
              <input id="cus_spread" className="inp" inputMode="numeric" placeholder="automatic" value={spread} onChange={(e) => setSpread(e.target.value.replace(/[^\d.,]/g, ""))} />
            </Field>
            <Field label="Resources (optional)" hint="Leave empty to use the default stock.">
              <ResourceFields res={res} onChange={(k, v) => setRes((o) => ({ ...o, [k]: v }))} placeholder="default" />
            </Field>
            <div className="summary">
              <span>{fmtInt(BUILDINGS.reduce((a, b) => a + (levels[b.type] ?? 0), 0))} building levels</span>
              <span>{fmtInt(totalTroops)} troops</span>
            </div>
            {problems ? <Notice tone="warn">Some requirements are unmet. The villages are created as entered.</Notice> : null}
            {error ? <ErrorNote>{error}</ErrorNote> : null}
            <Btn type="submit" variant="primary" className="big" icon="plus" busy={busy}>
              Create {plural(amount, "village")}
            </Btn>
          </div>
        </Card>
        {created ? (
          <Card className="result" title={`${plural(created.length, "village")} created`}>
            <div className="chips-row">
              {created.slice(0, 12).map((id) => (
                <NavLink key={id} go={go} to={`w/${world.id}/villages/edit/${id}`} className="tag link">#{id}</NavLink>
              ))}
              {created.length > 12 ? <span className="dim">+{created.length - 12} more</span> : null}
            </div>
            <div className="formbar inline">
              <NavLink go={go} to={`w/${world.id}/villages`} className="btn primary">
                View in villages list <Icon name="arrow" size={14} />
              </NavLink>
            </div>
          </Card>
        ) : null}
      </div>
    </form>
  );
}

// ---- page ------------------------------------------------------------------------------------------------------
export function VillageCreate({ world, catalog, tab = "random", go }) {
  const base = `w/${world.id}/villages/create`;
  return (
    <>
      <PageHead
        title="Create villages"
        sub={`Populate ${world.name} with barbarian villages.`}
        back={{ href: `#admin/w/${world.id}/villages`, onClick: (e) => { e.preventDefault(); go(`w/${world.id}/villages`); }, label: "Villages" }}
      />
      <Tabs
        value={tab}
        onChange={(t) => go(t === "custom" ? base + "/custom" : base)}
        tabs={[
          { value: "random", label: "Randomized", icon: "dice" },
          { value: "custom", label: "Custom", icon: "edit" },
        ]}
      />
      <div hidden={tab === "custom"}>
        <Randomized world={world} go={go} />
      </div>
      <div hidden={tab !== "custom"}>
        <Custom world={world} catalog={catalog} go={go} />
      </div>
    </>
  );
}
