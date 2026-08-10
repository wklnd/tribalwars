import { Fragment, useEffect, useRef } from "react";
import { fmtDuration, onTime } from "../lib/data";
import { GreyNum, MissingScreen, Timer } from "../lib/ui";
import { SmithBody } from "./buildings/SmithBody";
import { SnobBody } from "./buildings/SnobBody";
import { StatueBody } from "./buildings/StatueBody";
import {
  B,
  MISSING_CLASS,
  ORDER,
  bigImg,
  buildInfo,
  buildState,
  confirmBox,
  costAt,
  hideCapacity,
  levelOfId,
  midImg,
  modeFromHash,
  pitProduction,
  reqsMet,
  resolveId,
  screenKey,
  showMessage,
  useLiveRes,
  wallStats,
  warehouseCapacityAt,
} from "../lib/buildings";

/* Every screen in this file mirrors the original's server-rendered markup
   (templates/twlan/controllers/game/{main,storage,hide,wall,res}/*.php and
   templates/layouts/building.php) so the original game.css styles it. */

const levelText = (level) => (level > 0 ? `Level ${level}` : "not constructed");

/* `style="... !important"` cannot be expressed through React's style prop. */
const important = (prop, value) => (el) => el && el.style.setProperty(prop, value, "important");

/* layouts/building.php: image + name (level) + description. */
function BuildingHeader({ id, level }) {
  const b = B[id];
  return (
    <table width="100%">
      <tbody>
        <tr>
          <td>
            <img src={bigImg(id, level)} alt={b.name} />
          </td>
          <td width="100%">
            <h2>{`${b.name} (${levelText(level)})`}</h2>
            {b.text}
          </td>
        </tr>
      </tbody>
    </table>
  );
}

/* ---------------------------------------------------------------- queue */

// the server lets an order with less than 3 minutes left be completed for free
const FREE_FINISH_MS = 180 * 1000;

function BuildQueue({ village, hq, onCancel, onFinish }) {
  const queue = village.buildQueue;
  if (queue.length === 0) return null;
  const rows = [];
  let cursor = null;
  queue.forEach((q, i) => {
    const id = resolveId(q.type);
    const active = !!q.completesAt && i === 0;
    const seconds = q.durationSeconds ?? costAt(id, q.targetLevel, hq).seconds;
    const finish = active ? new Date(q.completesAt).getTime() : (cursor ?? Date.now()) + seconds * 1000;
    cursor = finish;
    rows.push({ q, id, active, seconds, finish, idx: i });
  });
  const waiting = rows.filter((r) => !r.active).length;
  let waitingIdx = 0;
  return (
    <div id="buildqueue_wrap">
      <table id="build_queue" className="vis">
        <tbody id="buildqueue">
          <tr>
            <th width="250">Construction assignment</th>
            <th width="100">Duration</th>
            <th width="150">Completion</th>
            <th>Cancellation</th>
            <th ref={important("background", "none")} />
          </tr>
          {rows.map((r) => {
            const wi = r.active ? null : waitingIdx++;
            const start = r.active ? new Date(r.q.startedAt ?? Date.now()).getTime() : null;
            return [
              <tr
                key={r.idx}
                className={r.active ? "lit nodrag" : "sortable_row nowrap"}
                id={r.active ? undefined : `buildorder_${wi}`}
              >
                <td width="250" className="nowrap lit-item">
                  <img src={midImg(r.id, r.q.targetLevel)} title="" alt="" className="bmain_list_img" />
                  {B[r.id].name}
                  <br />
                  {`Level ${r.q.targetLevel}`}
                </td>
                <td width="100" className="nowrap lit-item">
                  {r.active ? <Timer target={r.finish} /> : <span>{fmtDuration(r.seconds * 1000)}</span>}
                </td>
                <td className="lit-item" width="170">
                  {onTime(r.finish)}
                </td>
                <td className="lit-item">
                  <a className="btn btn-cancel" href="#" onClick={(e) => onCancel(e, r.q)}>
                    cancel
                  </a>
                  {r.active && r.finish - Date.now() <= FREE_FINISH_MS && (
                    <>
                      {" "}
                      <a className="btn btn-instant-free" href="#" title="Complete this order for free (less than 3 minutes left)" onClick={(e) => { e.preventDefault(); onFinish?.(r.q); }}>
                        Complete
                      </a>
                    </>
                  )}
                </td>
                <td className="lit-item" ref={important("background-color", "transparent")}>
                  {!r.active && waiting >= 2 && (
                    <div
                      style={{ width: 11, height: 11, backgroundImage: "url(/graphic/sorthandle.png)", cursor: "pointer" }}
                      className="bqhandle"
                      title="Here you can change the order of your building queue by dragging them to the desired location with your mouse."
                    />
                  )}
                </td>
              </tr>,
              r.active && (
                <tr key={r.idx + "p"} className="lit">
                  <td colSpan="5" style={{ padding: 0 }}>
                    <OrderProgress start={start} finish={r.finish} />
                  </td>
                </tr>
              ),
            ];
          })}
        </tbody>
      </table>
      <br />
    </div>
  );
}

/* OrderProgress.js: a single slot whose width tracks elapsed/total time. */
function OrderProgress({ start, finish }) {
  const total = Math.max(1, (finish - start) / 1000);
  const elapsed = Math.min(total, Math.max(0.1, (Date.now() - start) / 1000));
  const pct = elapsed / total;
  return (
    <div className="order-progress">
      <div
        style={{ width: pct * 100 + "%", backgroundColor: "#92c200" }}
        title={`${Math.round(pct * 100)}% done after ${fmtDuration(elapsed * 1000)}`}
      />
    </div>
  );
}

/* --------------------------------------------------------- build table */

function BuildRow({ village, id, live, go, onBuild }) {
  const b = B[id];
  const info = buildInfo(village, id);
  const link = (
    <a href={"#" + screenKey(id, info.level)} onClick={(e) => go?.(e, screenKey(id, info.level)) ?? e.preventDefault()}>
      <img src={midImg(id, info.level)} title={b.name} alt="" className="bmain_list_img" />
      {b.name}
    </a>
  );
  const head = (
    <td>
      {link}
      <br />
      <span style={{ fontSize: "0.9em" }}>{levelText(info.level)}</span>
    </td>
  );
  if (info.maxed) {
    return (
      <tr id={`main_buildrow_${id}`} className="completed">
        {head}
        <td colSpan="6" align="center" className="inactive">
          Building fully constructed
        </td>
      </tr>
    );
  }
  const state = buildState(village, id, info, live);
  const target = info.lvl + 1;
  return (
    <tr id={`main_buildrow_${id}`}>
      {head}
      <td className={"cost_wood" + (live.wood < info.wood ? " warn" : "")} data-cost={info.wood}>
        <span className="icon header wood"></span>
        {info.wood}
      </td>
      <td className={"cost_stone" + (live.stone < info.stone ? " warn" : "")} data-cost={info.stone}>
        <span className="icon header stone"></span>
        {info.stone}
      </td>
      <td className={"cost_iron" + (live.iron < info.iron ? " warn" : "")} data-cost={info.iron}>
        <span className="icon header iron"></span>
        {info.iron}
      </td>
      <td>
        <span className="icon header population"></span>
        {info.pop}
      </td>
      <td>
        <span className="icon header time"></span>
        {fmtDuration(info.seconds * 1000)}
      </td>
      <td className="build_options" style={{ whiteSpace: "normal", width: 300 }}>
        <a
          className="btn btn-build"
          style={state.ok ? undefined : { display: "none" }}
          data-building={id}
          id={`main_buildlink_${id}`}
          href="#"
          onClick={(e) => {
            e.preventDefault();
            onBuild(id);
          }}
        >
          {info.lvl === 0 ? "Construct" : `Expansion to level ${target}`}
        </a>
        <span className="inactive" style={state.ok ? { display: "none" } : undefined}>
          {state.ok ? "1" : state.timer != null ? (
            <>
              {state.text}
              <span className="timer_replace">{fmtDuration(state.timer * 1000)}</span>
            </>
          ) : (
            state.text
          )}
        </span>
      </td>
    </tr>
  );
}

function UnmetRow({ village, id, go }) {
  const b = B[id];
  const level = levelOfId(village, id);
  const target = screenKey(id, level);
  const nav = (e) => go?.(e, target) ?? e.preventDefault();
  const reqs = Object.entries(b.req);
  return (
    <tr>
      <td>
        <a href={"#" + target} onClick={nav}>
          <img src={midImg(id, level, true)} title="" className="bmain_list_img" alt="" />
        </a>
        <a href={"#" + target} onClick={nav}>
          {b.name}
        </a>
      </td>
      <td>
        <div className="unmet_req">
          {reqs.map(([rid, n]) => {
            const met = levelOfId(village, rid) >= n;
            return (
              <span key={rid}>
                <span>
                  <img
                    src={midImg(rid, levelOfId(village, rid), !met)}
                    style={{ verticalAlign: "middle" }}
                    alt=""
                  />
                  <span className={met ? undefined : "inactive"}>{`${B[rid].name} (${n})`}</span>
                </span>
              </span>
            );
          })}
          {Array.from({ length: Math.max(0, 3 - reqs.length) }, (_, i) => (
            <span key={"f" + i}></span>
          ))}
        </div>
      </td>
    </tr>
  );
}

/* main/build.php */
function BuildingWrapper({ village, live, go, onBuild }) {
  const active = [];
  const inactive = [];
  for (const id of ORDER) {
    const dto = B[id].type ? village.buildings.find((x) => x.type === B[id].type) : null;
    if ((dto?.requirementsMet ?? reqsMet(village, id)) || levelOfId(village, id) > 0) active.push(id);
    else inactive.push(id);
  }
  return (
    <div id="building_wrapper">
      <table id="buildings" className="vis nowrap" width="100%">
        <tbody>
          <tr>
            <th width="220">Buildings</th>
            <th colSpan="4">Requirements</th>
            <th width="100">
              Building time <img src="/graphic/questionmark.png" className="tooltip" title="hh:mm:ss" width="13" height="13" alt="" />
            </th>
            <th style={{ width: 200 }}>Construct</th>
          </tr>
          {active.map((id) => (
            <BuildRow key={id} village={village} id={id} live={live} go={go} onBuild={onBuild} />
          ))}
        </tbody>
      </table>
      <br />
      {inactive.length > 0 && (
        <>
          <table id="buildings_unmet" className="vis nowrap tall" style={{ width: "100%" }}>
            <tbody>
              <tr>
                <th style={{ width: "23%" }}>Not available</th>
                <th>Requirements</th>
              </tr>
              {inactive.map((id) => (
                <UnmetRow key={id} village={village} id={id} go={go} />
              ))}
            </tbody>
          </table>
          <br />
        </>
      )}
    </div>
  );
}

/* main/index.php */
function MainBody({ village, go, onBuild, onCancelBuild, onFinishBuild, onRename }) {
  const live = useLiveRes(village);
  const pending = useRef(null);
  const qlen = village.buildQueue.length;
  const hq = levelOfId(village, "main");

  // BuildingMain.build(): the original shows a success popup once the order is queued.
  useEffect(() => {
    if (pending.current && qlen > pending.current.n) {
      pending.current = null;
      showMessage("The building order was successfully queued.", "success");
    }
  }, [qlen]);

  const build = (id) => {
    const type = B[id].type;
    if (!type) return showMessage("Can't be built in this village", "error");
    pending.current = { n: qlen };
    Promise.resolve(onBuild(type)).finally(() => setTimeout(() => (pending.current = null), 3000));
  };

  const cancel = (e, q) => {
    e.preventDefault();
    confirmBox("Are you sure you want to cancel this building order?", () => onCancelBuild?.(q));
  };

  const rename = (e) => {
    e.preventDefault();
    const name = new FormData(e.currentTarget).get("name");
    if (String(name).trim().length < 3) showMessage("The village name has to contain at least 3 characters.", "error");
    else onRename?.(String(name).trim());
  };

  return (
    <>
      <table style={{ width: "100%" }}>
        <tbody>
          <tr>
            <td>
              <BuildQueue village={village} hq={hq} onCancel={cancel} onFinish={onFinishBuild} />
              <BuildingWrapper village={village} live={live} go={go} onBuild={build} />
            </td>
          </tr>
        </tbody>
      </table>
      <form method="post" onSubmit={rename}>
        <table className="vis" style={{ marginLeft: 5 }}>
          <tbody>
            <tr>
              <th colSpan="3">Change village name</th>
            </tr>
            <tr>
              <td>
                <input type="text" name="name" defaultValue={village.name} maxLength="32" size="32" />
              </td>
              <td>
                <input type="submit" className="btn" value="Change" />
              </td>
            </tr>
          </tbody>
        </table>
      </form>
    </>
  );
}

/* -------------------------------------------------------- resource pits */

const PIT_RES = { wood: ["wood", "Wood", "woodPerHour"], stone: ["stone", "Clay", "clayPerHour"], iron: ["iron", "Iron", "ironPerHour"] };

/* game/res.php */
function PitBody({ village, id, level }) {
  const [res, title, key] = PIT_RES[id];
  const hasNext = level < B[id].max;
  const speed = village.worldSpeed || 1; // world speed multiplies production
  const local = Math.round(pitProduction(level) * speed);
  const next = Math.round(pitProduction(level + 1) * speed);
  const total = Math.round(village[key]);
  return (
    <table className="vis" cellSpacing="1" cellPadding="3">
      <tbody>
        <tr>
          <th>Production</th>
          <th>Units per hour</th>
          {hasNext && <th>{`Units per hour at level ${level + 1}`}</th>}
        </tr>
        <tr>
          <td>
            <img src={`/graphic/${res}.png`} title={title} alt="" className="" /> Base production
          </td>
          <td>
            <GreyNum n={local} />
          </td>
          {hasNext && (
            <td className="inactive">
              <GreyNum n={next} />
            </td>
          )}
        </tr>
        <tr>
          <td>
            <img src={`/graphic/${res}.png`} title={title} alt="" className="" /> Current production
          </td>
          <td>
            <b>
              <GreyNum n={total} />
            </b>
          </td>
          {hasNext && (
            <td className="inactive">
              <GreyNum n={total - local + next} />
            </td>
          )}
        </tr>
      </tbody>
    </table>
  );
}

/* --------------------------------------------------------------- storage */

/* game/storage/index.php */
function StorageBody({ village, level }) {
  const live = useLiveRes(village);
  const cap = village.warehouseCapacity;
  return (
    <>
      <br />
      <table className="vis " width="600px">
        <tbody>
          <tr>
            <th>Storage capacity</th>
            <th>Units per resource</th>
          </tr>
          <tr>
            <td>Current storage capacity</td>
            <td width="160px">
              <b>
                <GreyNum n={cap} />
              </b>
            </td>
          </tr>
          {level < B.storage.max && (
            <tr>
              <td>{`Storage capacity on level ${level + 1} `}</td>
              <td>
                <b>
                  <GreyNum n={warehouseCapacityAt(level + 1)} />
                </b>
              </td>
            </tr>
          )}
        </tbody>
      </table>
      <br />
      <table className="vis " width="600px">
        <tbody>
          <tr>
            <th width="150" colSpan="2">
              Storage full
            </th>
            <th>Time (hh:mm:ss)</th>
          </tr>
          {Object.values(PIT_RES).map(([res, title]) => {
            const seconds = ((cap - live[res]) * 3600) / live.rate[res];
            const full = seconds <= 0;
            const img = <img src={`/graphic/${res}.png`} title={title} alt="" className="" />;
            return (
              <tr key={res}>
                {full ? (
                  <td colSpan="3" className="error">
                    {img} Warehouse is full. No more resources can be stored!
                  </td>
                ) : (
                  <>
                    <td>{img}</td>
                    <td>
                      <strong>{onTime(live.now + seconds * 1000)}</strong>
                    </td>
                    <td width="160px">
                      <span className="timer">{fmtDuration(seconds * 1000)}</span>
                    </td>
                  </>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </>
  );
}

/* ---------------------------------------------------------- hiding place */

/* game/hide/index.php */
function HideBody({ village, level }) {
  const live = useLiveRes(village);
  const cap = hideCapacity(level);
  return (
    <table className="vis">
      <tbody>
        <tr>
          <th colSpan="2">Current size</th>
        </tr>
        <tr>
          <td width="200">Current capacity</td>
          <td>
            <b>
              <GreyNum n={cap} />
            </b>{" "}
            Units per resource
          </td>
        </tr>
        {level < B.hide.max && (
          <tr>
            <td>{`Size on level ${level + 1}`}</td>
            <td>
              <b>
                <GreyNum n={hideCapacity(level + 1)} />
              </b>{" "}
              Units per resource
            </td>
          </tr>
        )}
        <tr>
          <td>
            <span>Plunderable resources</span>
          </td>
          <td>
            <span>
              {["wood", "stone", "iron"].map((res) => (
                <Fragment key={res}>
                  <span className={`icon header ${res}`}></span>
                  {Math.max(0, Math.round(live[res] - cap))}
                </Fragment>
              ))}
            </span>
          </td>
        </tr>
        <tr>
          <td colSpan="2">Offers on the market can be plundered even if the resources fit into your hiding place.</td>
        </tr>
      </tbody>
    </table>
  );
}

/* ------------------------------------------------------------------ wall */

/* game/wall/index.php */
function WallBody({ level }) {
  const cur = wallStats(level);
  const next = wallStats(level + 1);
  return (
    <table className="vis">
      <tbody>
        <tr>
          <th>Level </th>
          <th>Basic defense</th>
          <th>Defensive bonus</th>
        </tr>
        <tr>
          <td width="160">Current</td>
          <td width="160">
            <strong>{cur.basic}</strong>
          </td>
          <td width="160">
            <strong>{`${cur.bonus}%`}</strong>
          </td>
        </tr>
        {level < B.wall.max && (
          <tr>
            <td>{`At level ${level + 1}`}</td>
            <td>
              <strong>{next.basic}</strong>
            </td>
            <td>
              <strong>{`${next.bonus}%`}</strong>
            </td>
          </tr>
        )}
      </tbody>
    </table>
  );
}

/* ---------------------------------------------------------------- screen */

/* layouts/building.php: mode menu shown above the content when a building has two or more modes. */
const STATUE_MODES = [
  ["index", "Paladin"],
  ["inventory", "Inventory"],
];

function LayoutModes({ id, modes, mode, go }) {
  if (!modes || modes.length < 2) return <table cellPadding="0" cellSpacing="0" width="100%"></table>;
  return (
    <table cellPadding="0" cellSpacing="0" width="100%">
      <tbody>
        <tr>
          <td valign="top">
            <table className="vis modemenu">
              <tbody>
                <tr>
                  {modes.map(([m, label]) => (
                    <td key={m} className={m === mode ? "selected" : undefined} width="100">
                      <a href={`#${id}:${m}`} onClick={(e) => (go ? go(e, `${id}:${m}`) : e.preventDefault())}>{`${label} `}</a>
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </td>
        </tr>
      </tbody>
    </table>
  );
}

/* Buildings whose level>0 page is the recruitment screen (TrainBuildingScreen, routed by App as screen=<id>). */
const TRAIN_BUILDINGS = new Set(["barracks", "stable", "garage"]);

export function BuildingScreen({ village, type, onBuild, onCancelBuild, onFinishBuild, onRename, onTrain, onCancelTrain, onRenamePaladin, onMintCoin, onResearch, onCancelResearch, go }) {
  const id = resolveId(type);
  if (!id || MISSING_CLASS[id]) return <MissingScreen name={MISSING_CLASS[id] ?? String(type)} />;
  return <BuildingPage village={village} id={id} onBuild={onBuild} onCancelBuild={onCancelBuild} onFinishBuild={onFinishBuild} onRename={onRename} onTrain={onTrain} onCancelTrain={onCancelTrain} onRenamePaladin={onRenamePaladin} onMintCoin={onMintCoin} onResearch={onResearch} onCancelResearch={onCancelResearch} go={go} />;
}

function BuildingPage({ village, id, onBuild, onCancelBuild, onFinishBuild, onRename, onTrain, onCancelTrain, onRenamePaladin, onMintCoin, onResearch, onCancelResearch, go }) {
  const level = levelOfId(village, id);
  const toTrain = TRAIN_BUILDINGS.has(id) && level > 0;
  // "statue:inventory" (the App keeps the sub-mode in the location hash)
  const statueMode = id === "statue" && modeFromHash("statue") === "inventory" ? "inventory" : "index";
  // screen=barracks/stable/garage is the recruitment screen once the building exists.
  useEffect(() => {
    if (toTrain) go?.(null, id);
  }, [toTrain, go, id]);

  let body = null;
  if (id === "main") body = <MainBody village={village} go={go} onBuild={onBuild} onCancelBuild={onCancelBuild} onFinishBuild={onFinishBuild} onRename={onRename} />;
  else if (PIT_RES[id]) body = <PitBody village={village} id={id} level={level} />;
  else if (id === "storage") body = <StorageBody village={village} level={level} />;
  else if (id === "hide") body = <HideBody village={village} level={level} />;
  else if (id === "wall") body = <WallBody level={level} />;
  else if (id === "smith") body = <SmithBody village={village} onResearch={onResearch} onCancelResearch={onCancelResearch} />;
  else if (id === "statue") body = <StatueBody village={village} mode={statueMode} onTrain={onTrain} onCancelTrain={onCancelTrain} onRenamePaladin={onRenamePaladin} />;
  else if (id === "snob") body = <SnobBody village={village} mode={modeFromHash(id)} go={go} onTrain={onTrain} onCancelTrain={onCancelTrain} onMintCoin={onMintCoin} />;

  return (
    <>
      <BuildingHeader id={id} level={level} />
      {level > 0 && (
        <>
          <br />
          <LayoutModes id={id} modes={id === "statue" ? STATUE_MODES : null} mode={statueMode} go={go} />
          {body}
        </>
      )}
    </>
  );
}
