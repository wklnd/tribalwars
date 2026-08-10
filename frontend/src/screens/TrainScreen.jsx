// screen=train / screen=barracks|stable|garage — mirrors templates/twlan/controllers/game/train/
// {building,queue,train,train_js}.php (+ the h2 / mode tabs the controller wraps around them, and
// layouts/building.php for the three building screens) and the behaviour of js/TrainManager.js
// (max links, live cost totals, red inputs, affordability hint).
//   <TrainScreen/>                       screen=train (recruitment overview: every unit of every building)
//   <TrainBuildingScreen id="stable"/>   screen=barracks|stable|garage once the building exists
//   <TrainBuildingBody id="stable"/>     the same page without the building header
import { useState } from "react";
import { levelOf } from "../lib/data";
import { B, bigImg, confirmBox, showMessage } from "../lib/buildings";
import {
  BUILDING_UNITS,
  ID_TO_TYPE,
  RECRUIT_ORDER,
  REQ_NAME,
  TYPE_TO_ID,
  UNIT_BY_ID,
  UNIT_RECRUIT,
  fmtBuildTime,
  fmtOnTime,
  navigateTo,
  recruitSeconds,
  reqImage,
  reqLevel,
  requirementsMet,
  unitsAtHome,
  unitsAway,
  useHashNav,
  useNowTick,
} from "../lib/military";
import { useUnitPopup } from "./military/UnitPopup";

const MODES = [
  ["train", "Recruitment"],
  ["decommission", "Decommissioning"],
  ["mass", "Mass Recruitment"],
  ["mass_decommission", "Mass Decommissioning"],
];
const RES_KEYS = ["wood", "stone", "iron", "pop"];
const DRAG_TITLE = "Here you can change the order of your queue by dragging them to the desired location with your mouse.";

// `background: none !important` cannot be expressed in a React style object
function ImportantNoBg({ as: Tag = "th", className, children }) {
  return (
    <Tag className={className} ref={(el) => el && el.style.setProperty("background", "none", "important")}>
      {children}
    </Tag>
  );
}

/* ---- the recruit queues of the village (one per building, like the original): the backend lists them all in one
   array, each entry is tagged with the building the unit is recruited in. ---- */
export function queueEvents(village, now) {
  const events = [];
  // every recruit building runs its own queue: the timeline (`finish`) is kept per building
  const finishOf = {};
  const started = {};
  (village.trainQueue ?? []).forEach((t, i) => {
    const id = TYPE_TO_ID[t.type];
    if (!id) return;
    const building = UNIT_RECRUIT[id].building;
    const remaining = t.totalCount - t.producedCount;
    const per = t.completesAt
      ? (new Date(t.completesAt).getTime() - new Date(t.startedAt).getTime()) / 1000 / t.totalCount
      : recruitSeconds(village, id);
    const active = !started[building] && !!t.completesAt;
    started[building] = true;
    const from = finishOf[building] ?? now;
    const end = active ? new Date(t.completesAt).getTime() : from + remaining * per * 1000;
    events.push({
      id: t.id ?? i,
      unit: id,
      building,
      amount: remaining,
      active,
      duration: Math.max(0, end - (active ? now : from)),
      finish: end,
      nextAt: active ? new Date(t.startedAt).getTime() + (t.producedCount + 1) * per * 1000 : null,
    });
    finishOf[building] = end;
  });
  return events;
}

/* ---- current recruitment queue (queue.php) ---- */
export function TrainQueue({ events, bid, now, onCancel }) {
  if (events.length === 0) return null;
  const next = events.find((e) => e.active);
  const waiting = events.filter((e) => !e.active).length;
  let c = 0;
  return (
    <div className="current_prod_wrapper">
      <div id={`replace_${bid}`}>
        <table className="vis">
          <tbody>
            <tr>
              {next && (
                <>
                  <th width="250">{`Completion of the next unit (${UNIT_BY_ID[next.unit].name}):`}</th>
                  <th>
                    <span className="timer">{fmtBuildTime(Math.max(1000, next.nextAt - now))}</span>
                  </th>
                </>
              )}
            </tr>
          </tbody>
        </table>
        <div className="trainqueue_wrap" id={`trainqueue_wrap_${bid}`}>
          <table className="vis">
            <tbody id={`trainqueue_${bid}`} className="ui-sortable">
              <tr>
                <th width="150">Training</th>
                <th width="120">Duration</th>
                <th width="150">Completion</th>
                <th width="100">Cancel *</th>
                <ImportantNoBg />
              </tr>
              {events.map((e, i) => {
                const cls = e.active ? "lit-item" : "";
                const u = UNIT_BY_ID[e.unit];
                return (
                  <tr key={i} className={e.active ? "lit" : "sortable_row"} id={e.active ? undefined : `trainorder_${c++}`}>
                    <td className={cls}>{`${e.amount} ${e.amount === 1 ? u.name : u.plural}`}</td>
                    <td className={cls}>
                      {e.active ? (
                        <span className="timer">{fmtBuildTime(e.duration)}</span>
                      ) : (
                        fmtBuildTime(e.duration)
                      )}
                    </td>
                    <td className={cls}>{fmtOnTime(e.finish)}</td>
                    <td className={cls}>
                      <a
                        className="btn btn-cancel"
                        href="#"
                        onClick={(ev) => {
                          ev.preventDefault();
                          if (onCancel) confirmBox("Are you sure you want to cancel this recruitment order?", () => onCancel(e));
                        }}
                      >
                        cancel
                      </a>
                    </td>
                    <ImportantNoBg as="td" className={cls}>
                      {!e.active && waiting >= 2 && (
                        <div
                          style={{ width: 11, height: 11, backgroundImage: "url(/graphic/sorthandle.png)", cursor: "pointer" }}
                          className="bqhandle"
                          title={DRAG_TITLE}
                        />
                      )}
                    </ImportantNoBg>
                  </tr>
                );
              })}
              {events.length >= 2 && (
                <tr>
                  <td colSpan="3">&nbsp;</td>
                  <td className="lit-item">
                    <a
                      className="btn btn-cancel evt-confirm"
                      href="#"
                      onClick={(ev) => {
                        ev.preventDefault();
                        if (onCancel) confirmBox("Are you sure you want to cancel all recruitment orders?", async () => { for (const e of events) await onCancel(e); });
                      }}
                    >
                      cancel all
                    </a>
                  </td>
                  <ImportantNoBg />
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div style={{ fontSize: "7pt" }}>* (ninety percent of the used resources are refunded to your storage unit)</div>
        <br />
      </div>
    </div>
  );
}

/* ---- Village.canAfford(): { ok, text } ---- */
function affordability(village, cost, now) {
  const have = { wood: village.wood, stone: village.clay, iron: village.iron };
  if (have.wood >= cost.wood && have.stone >= cost.stone && have.iron >= cost.iron) return { ok: true };
  const storage = village.warehouseCapacity;
  if (cost.wood > storage || cost.stone > storage || cost.iron > storage) return { ok: false, text: "Warehouse too small" };
  const prod = { wood: village.woodPerHour, stone: village.clayPerHour, iron: village.ironPerHour };
  let maxTime = 0;
  for (const k of ["wood", "stone", "iron"]) {
    const missing = cost[k] - have[k];
    if (missing > 0) maxTime = Math.max(maxTime, prod[k] > 0 ? (missing / prod[k]) * 3600 : Infinity);
  }
  if (!isFinite(maxTime)) return { ok: false, text: "Warehouse too small" };
  if (maxTime <= 120)
    return { ok: false, timer: fmtBuildTime(Math.round(maxTime) * 1000), text: "Resources available " };
  return { ok: false, text: `Resources available ${fmtOnTime(now + maxTime * 1000, false)}` };
}

/* ---- train/building.php: queue + recruit form + "not available" table for the units of `buildings` ---- */
function TrainBody({ village, buildings, queueId, isTrain, onTrain, onCancelTrain, busy, go }) {
  const now = useNowTick();
  const popup = useUnitPopup();
  const [counts, setCounts] = useState({});
  const [sending, setSending] = useState(false);

  const home = unitsAtHome(village);
  const away = unitsAway(village);
  const order = RECRUIT_ORDER.filter((id) => buildings.includes(UNIT_RECRUIT[id].building));
  const available = order.filter((id) => requirementsMet(village, id));
  const notAvailable = order.filter((id) => !available.includes(id));
  const events = queueEvents(village, now).filter((e) => buildings.includes(e.building));

  // UnitBuildManager: cur_res = village resources - what is typed in the boxes
  const res = {
    wood: Math.floor(village.wood),
    stone: Math.floor(village.clay),
    iron: Math.floor(village.iron),
    pop: village.populationCapacity - village.populationUsed,
  };
  const typed = (id) => {
    const n = parseInt(counts[id], 10);
    return isNaN(n) || n < 0 ? 0 : n;
  };
  const cur = { ...res };
  for (const id of available) for (const k of RES_KEYS) cur[k] -= typed(id) * UNIT_BY_ID[id][k];
  const isOver = RES_KEYS.some((k) => cur[k] < 0);
  const unitMax = (id) => {
    let amount = 999999;
    for (const k of RES_KEYS) amount = Math.min(amount, Math.floor(cur[k] / UNIT_BY_ID[id][k]));
    return amount < 0 ? 0 : amount;
  };
  const maxLabel = (id) => (isTrain ? unitMax(id) : home[id]);

  const setMax = (e, id) => {
    e.preventDefault();
    if (!isTrain) return setCounts({ ...counts, [id]: String(home[id]) });
    const max = unitMax(id);
    setCounts({ ...counts, [id]: max === 0 ? "" : String(max + typed(id)) });
  };

  const submit = async (e) => {
    e.preventDefault();
    const orders = available.filter((id) => typed(id) > 0).map((id) => [id, typed(id)]);
    if (orders.length === 0) return;
    if (!isTrain) return showMessage("Decommissioning is not supported by the server yet.", "error");
    setSending(true);
    try {
      let ok = true;
      for (const [id, n] of orders) ok = (await onTrain(ID_TO_TYPE[id], n)) === true && ok;
      setCounts({});
      if (ok) showMessage("Recruitment started", "success");
    } finally {
      setTimeout(() => setSending(false), 500);
    }
  };

  return (
    <>
      <table width="100%">
        <tbody>
          <tr>
            <td>
              <TrainQueue events={events} bid={queueId} now={now} onCancel={onCancelTrain} />
              {available.length > 0 && (
                <form id="train_form" method="post" onSubmit={submit}>
                  <table className="vis" style={{ width: "100%" }}>
                    <tbody>
                      <tr>
                        <th style={{ width: "25%" }}>Unit</th>
                        <th style={{ width: "40%" }}>Requirements</th>
                        <th>In the village/total</th>
                        <th style={{ width: 150 }}>Recruit</th>
                      </tr>
                      {available.map((id) => {
                        const u = UNIT_BY_ID[id];
                        const count = (isTrain && typed(id)) || 1;
                        const secs = recruitSeconds(village, id);
                        // TrainManager.setUnitCostDisplay() looks the population span up as "_cost_pop", which the
                        // markup ("_cost_population") never has: the original leaves that cell at the single-unit value
                        const cost = { wood: count * u.wood, stone: count * u.stone, iron: count * u.iron, pop: u.pop };
                        const warn = (k) => k !== "pop" && cost[k] > res[k];
                        const afford = isTrain ? affordability(village, { wood: u.wood, stone: u.stone, iron: u.iron }, now) : { ok: true };
                        const total = home[id] + away[id];
                        return (
                          <tr className="row_a" key={id}>
                            <td className="nowrap">
                              <a href="#" className="unit_link" onClick={(e) => popup.open(e, id)}>
                                <img src={`/graphic/unit/recruit/${id}.png`} style={{ verticalAlign: "middle" }} alt="" />{" "}
                                {u.name}
                              </a>
                            </td>
                            <td>
                              <div className="recruit_req">
                                {[["population", "pop"], ["wood", "wood"], ["stone", "stone"], ["iron", "iron"]].map(([name, k]) => (
                                  <span key={name}>
                                    <span className={`icon header ${name}`}> </span>
                                    <span id={`${id}_0_cost_${name}`} className={warn(k) ? "warn" : undefined}>
                                      {cost[k]}
                                    </span>
                                  </span>
                                ))}
                                <span>
                                  <span className="icon header time"> </span>
                                  <span id={`${id}_0_cost_time`}>{fmtBuildTime(count * secs * 1000)}</span>
                                </span>
                              </div>
                            </td>
                            <td style={{ textAlign: "center" }}>{`${home[id]}/${total}`}</td>
                            <td>
                              <span id={`${id}_0_interaction`} style={afford.ok ? undefined : { display: "none" }}>
                                <input
                                  name={id}
                                  className="recruit_unit"
                                  id={`${id}_0`}
                                  type="text"
                                  style={{ width: 50, color: isTrain && isOver ? "red" : "black" }}
                                  maxLength="5"
                                  tabIndex="1"
                                  value={counts[id] ?? ""}
                                  onChange={(e) => setCounts({ ...counts, [id]: e.target.value.replace(/\D/g, "") })}
                                />{" "}
                                <a id={`${id}_0_a`} href="#" onClick={(e) => setMax(e, id)}>
                                  {`(${maxLabel(id)})`}
                                </a>
                              </span>
                              <span
                                id={`${id}_0_afford_hint`}
                                className="inactive"
                                style={{ textAlign: "center", fontSize: 11 }}
                              >
                                {!afford.ok && afford.text}
                                {!afford.ok && afford.timer && <span className="timer_replace">{afford.timer}</span>}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                      <tr>
                        <td colSpan="3" />
                        <td>
                          <input
                            className="btn btn-recruit"
                            style={{ float: "inherit" }}
                            type="submit"
                            value={isTrain ? "Recruit" : "Decomission"}
                            tabIndex="4"
                            disabled={sending || busy}
                          />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </form>
              )}
              <br />
              {notAvailable.length > 0 && (
                <table className="vis" style={{ width: "100%" }}>
                  <tbody>
                    <tr>
                      <th style={{ width: "25%" }}>recruit</th>
                      <th>Requirements</th>
                    </tr>
                    {notAvailable.map((id) => {
                      const u = UNIT_BY_ID[id];
                      return (
                        <tr style={{ lineHeight: "30px" }} key={id}>
                          <td>
                            <a href="#" className="unit_link" onClick={(e) => popup.open(e, id)}>
                              <img src={`/graphic/unit/recruit/grey/${id}.png`} style={{ opacity: 0.7 }} alt="" /> {u.name}
                            </a>
                          </td>
                          <td>
                            <div className="unmet_req float_left" style={{ width: 390 }}>
                              {Object.entries(UNIT_RECRUIT[id].req).filter(([b, level]) => reqLevel(village, b) < level).map(([b, level]) => (
                                <span key={b}>
                                  <span>
                                    <img src={`/graphic/buildings/mid/${reqImage(b, level)}`} style={{ verticalAlign: "middle" }} alt="" />{" "}
                                    <span>{`${REQ_NAME[b]} (Level ${level})`}</span>
                                  </span>
                                </span>
                              ))}
                            </div>
                            <span className="float_right" style={{ marginRight: 5 }}>
                              <img src="/graphic/overview/research.png?79f5f" style={{ verticalAlign: "middle" }} alt="" />
                              &nbsp;
                              <a href="#" style={{ float: "right" }} onClick={(e) => navigateTo(e, "smith", go)}>
                                Research
                              </a>
                            </span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </td>
          </tr>
        </tbody>
      </table>
      {popup.node}
    </>
  );
}

/* ---- screen=train: recruitment overview (all buildings' units) ---- */
export function TrainScreen({ village, onTrain, onCancelTrain, busy, go }) {
  const [parts, navigate] = useHashNav("train", go);
  const mode = parts[0] === "decommission" ? "decommission" : "train";
  const tab = (m) => (m === mode ? "selected" : undefined);

  return (
    <>
      <h2>Recruitment</h2>
      <p>
        In this overview you can recruit all types of units if you have constructed the required buildings and
        researched the needed technologies.
      </p>

      <table className="vis">
        <tbody>
          <tr>
            {MODES.map(([m, label]) => (
              <td key={m} className={tab(m)}>
                <a href="#" onClick={(e) => navigate(e, m === "decommission" ? "train:decommission" : "train")}>
                  {label}
                </a>
              </td>
            ))}
          </tr>
        </tbody>
      </table>
      <TrainBody
        key={mode}
        village={village}
        buildings={["barracks", "stable", "garage"]}
        queueId="barracks"
        isTrain={mode === "train"}
        onTrain={onTrain}
        onCancelTrain={onCancelTrain}
        busy={busy}
        go={go}
      />
    </>
  );
}

/* ---- screen=barracks|stable|garage, level > 0: layouts/building.php (mode menu) around train/building.php ---- */
export function TrainBuildingBody({ village, id, onTrain, onCancelTrain, busy, go }) {
  const [mode, setMode] = useState("train");
  return (
    <>
      <br />
      <table cellPadding="0" cellSpacing="0" width="100%">
        <tbody>
          <tr>
            <td valign="top">
              <table className="vis modemenu">
                <tbody>
                  <tr>
                    {MODES.slice(0, 2).map(([m, label]) => (
                      <td key={m} className={m === mode ? "selected" : undefined} width="100">
                        <a
                          href="#"
                          onClick={(e) => {
                            e.preventDefault();
                            setMode(m);
                          }}
                        >
                          {label}{" "}
                        </a>
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </td>
          </tr>
        </tbody>
      </table>
      <TrainBody
        key={mode}
        village={village}
        buildings={[id]}
        queueId={id}
        isTrain={mode === "train"}
        onTrain={onTrain}
        onCancelTrain={onCancelTrain}
        busy={busy}
        go={go}
      />
    </>
  );
}

export function TrainBuildingScreen({ village, id, onTrain, onCancelTrain, busy, go }) {
  const b = B[id];
  const level = levelOf(village, b.type);
  return (
    <>
      <table width="100%">
        <tbody>
          <tr>
            <td>
              <img src={bigImg(id, level)} alt={b.name} />
            </td>
            <td width="100%">
              <h2>{`${b.name} (${level > 0 ? `Level ${level}` : "not constructed"})`}</h2>
              {b.text}
            </td>
          </tr>
        </tbody>
      </table>
      {level > 0 && <TrainBuildingBody village={village} id={id} onTrain={onTrain} onCancelTrain={onCancelTrain} busy={busy} go={go} />}
    </>
  );
}

// building ids whose screen is the recruitment page once built
export const TRAIN_BUILDING_IDS = Object.keys(BUILDING_UNITS);
