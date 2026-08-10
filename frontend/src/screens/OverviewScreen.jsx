import { useState } from "react";
import { createPortal } from "react-dom";
import { UNITS, fmtDuration, onTime } from "../lib/data";
import { commandInfo } from "../lib/military";
import { Timer } from "../lib/ui";
import {
  DEFAULT_ORDER, SCENE, WIDGET_TITLES, absInt, bigImageFile, greyInt, isNight, lsGet, lsSet, popCost, prodUnit,
  sceneLevels, visualImages,
} from "../lib/overview";

/* Mirrors templates/twlan/controllers/game/overview/*.php of the original game (rendered markup, ids, classes, texts).
   Behaviour that the original does in VillageOverview.js / upgrade_building.js is reproduced in React state. */

// attribute the original ships verbatim (e.g. "font-size:8px !important;", which React style objects can't express)
const rawStyle = (css) => (el) => {
  if (el) el.setAttribute("style", css);
};

/* ---------- jQuery.tooltip look-alike (UI.ToolTip) ---------- */
// Observed on the real overview: the headquarters shows its animated .gif while the build queue is not empty,
// the resource pits while that resource is below the warehouse capacity; nothing else is animated.
function animatedNow(b, village) {
  if (b.id === "main") return village.buildQueue.length > 0;
  const res = { wood: village.wood, stone: village.clay, iron: village.iron }[b.id];
  return res !== undefined && res < village.warehouseCapacity;
}

function useTip() {
  const [tip, setTip] = useState(null);
  const props = (html) => ({
    onMouseEnter: (e) => setTip({ html, x: e.pageX, y: e.pageY }),
    onMouseMove: (e) => setTip({ html, x: e.pageX, y: e.pageY }),
    onMouseLeave: () => setTip(null),
    onClick: () => setTip(null),
  });
  const node =
    tip && typeof document !== "undefined"
      ? createPortal(
          <div id="tooltip" className="tooltip-style" style={{ display: "block", left: tip.x + 15, top: tip.y + 15 }}>
            <h3 dangerouslySetInnerHTML={{ __html: tip.html }} />
            <div className="body" style={{ display: "none" }} />
            <div className="url" style={{ display: "none" }} />
          </div>,
          document.body,
        )
      : null;
  return [props, node];
}

const resSpan = (type, amount) => `<span class='icon header ${type}'></span> ${amount}`;

/* ---------- widget shell ("index.php") ---------- */
// Public API used by other screens: <Widget id title>children</Widget>
export function Widget({ id, title, children, open: openProp, onToggle, dragProps }) {
  const [openLocal, setOpenLocal] = useState(true);
  const open = openProp ?? openLocal;
  const toggle = onToggle ?? (() => setOpenLocal((o) => !o));
  return (
    <div id={"show_" + id} className="vis moveable widget" {...(dragProps?.wrapper ?? {})}>
      <h4 {...(dragProps?.handle ?? {})}>
        <img
          ref={rawStyle("float: right; cursor: pointer;")}
          onClick={toggle}
          src={open ? "/graphic/minus.png" : "/graphic/plus.png"}
          alt=""
        />
        {title}
      </h4>
      <div style={{ display: open ? "block" : "none" }}>{children}</div>
    </div>
  );
}

function HiddenWidget({ id, dragProps }) {
  return (
    <div
      id={"show_" + id}
      className="vis moveable hidden_widget"
      ref={rawStyle("display:none;")}
      {...(dragProps?.wrapper ?? {})}
    >
      <h4>{WIDGET_TITLES[id]}</h4>
    </div>
  );
}

/* ---------- summary (visual) ---------- */
function extraInfo(b, ctx) {
  const { village, fetchedAt, strong } = ctx;
  if (b.id === "main") {
    const active = village.buildQueue.find((q) => q.completesAt);
    if (!active) return null;
    return new Date(active.completesAt).getTime() <= Date.now() ? <span className="warn">overdue</span> : <Timer target={active.completesAt} />;
  }
  if (b.id === "storage") {
    const cap = village.warehouseCapacity;
    const rows = [
      [village.wood, village.woodPerHour],
      [village.clay, village.clayPerHour],
      [village.iron, village.ironPerHour],
    ];
    if (rows.some(([v]) => v >= cap)) {
      return strong ? <strong className="warn">full</strong> : <span className="warn">full</span>;
    }
    const secs = Math.min(...rows.filter(([, r]) => r > 0).map(([v, r]) => ((cap - v) / r) * 3600));
    if (!Number.isFinite(secs)) return null;
    return <Timer target={new Date(fetchedAt + secs * 1000).toISOString()} />;
  }
  return null;
}

function upgradeLabelHtml(b, info) {
  const next = info.level + 1;
  return (
    resSpan("wood", info.nextWood) + " " + resSpan("stone", info.nextClay) + " " + resSpan("iron", info.nextIron) +
    `<br />Villagers needed: ${popCost(b, next + (info.queuedLevels ?? 0))}` +
    `<br />Building time: ${fmtDuration(info.nextSeconds * 1000)}`
  );
}

function VillageScene({ ctx, levels, showLevels, upgradeMode, canUpgrade, go, onUpgrade, npcs }) {
  const { village } = ctx;
  const [tipProps, tipNode] = useTip();
  const night = village.night ?? isNight();
  // at night the village scene uses the original's graphic/visual_night set (two files only exist by day)
  const dir = night ? "visual_night" : "visual";
  const dayFallback = (e) => {
    if (e.currentTarget.src.includes("/visual_night/")) e.currentTarget.src = e.currentTarget.src.replace("/visual_night/", "/visual/");
  };
  const d = new Date();
  const christmas = d.getMonth() === 11 && d.getDate() >= 23;
  return (
    <div className="widget_content" style={{ display: "block" }}>
      <div id="buildings_visual" ref={rawStyle("position:relative; margin: 5px; width: 600px;")}>
        <img width="600" height="418" src={`/graphic/${dir}/back_none.jpg`} alt="" onError={dayFallback} />
        <img className="p_church" src={`/graphic/${dir}/church_disabled.png`} alt="" onError={dayFallback} />
        {christmas && <img className="christmas_tree" src={`/graphic/${dir}/christmas_tree.png`} alt="" onError={dayFallback} />}
        {SCENE.flatMap((b) =>
          visualImages(b, levels[b.id].level, animatedNow(b, village), levels[b.id].queued).map(([cls, file]) => (
            <img key={b.id + cls} className={cls} src={`/graphic/${dir}/` + file} alt={b.name} onError={dayFallback} />
          )),
        )}
        {npcs.map((n) => (
          <img key={n} className={"npc_" + n} src={`/graphic/${dir}/${n}.gif`} alt="" onError={dayFallback} />
        ))}
        <img className="empty" src="/graphic/map/empty.png" alt="" useMap="#map" style={upgradeMode ? { display: "none" } : undefined} />
        <map name="map" id="map">
          {SCENE.filter((b) => levels[b.id].level > 0).map((b) => (
            <area
              key={b.id}
              id={"map_" + b.id}
              shape="poly"
              coords={b.shape}
              href="#"
              alt={b.name}
              title={b.name}
              onClick={(e) => go(e, b.screen)}
            />
          ))}
        </map>
        {SCENE.map((b) => {
          const { level, queued } = levels[b.id];
          const exists = level > 0;
          const upgradable = upgradeMode && showLevels && canUpgrade[b.id];
          const labelClass = [night ? "label_night" : "label", !exists && queued === 0 ? "label_no_lvl" : "", upgradable ? "can_upgrade" : ""]
            .filter(Boolean)
            .join(" ");
          const tip = upgradable ? tipProps(upgradeLabelHtml(b, { ...levels[b.id].info, level, queuedLevels: queued })) : {};
          return (
            <div
              key={b.id}
              id={"l_" + b.id}
              className={"l_" + b.id}
              style={{ display: "inline" }}
              title={upgradable ? "" : b.name}
            >
              {showLevels && (
                <div
                  className={labelClass}
                  style={upgradable && !exists ? { display: "block" } : undefined}
                  {...tip}
                  onClick={upgradable ? (e) => { e.preventDefault(); onUpgrade(b); } : undefined}
                >
                  {exists && (
                    <a href="#" onClick={(e) => (upgradable ? e.preventDefault() : go(e, b.screen))}>
                      <img src={`/graphic/buildings/${b.id}.png`} className="middle" alt={b.name} />
                      {` ${level} `}
                      <span className="building_order_level">{absInt(queued)}</span>
                    </a>
                  )}
                  <br />
                  <span className="building_extra" ref={rawStyle("font-size:8px !important; font-weight:bold")}>
                    {extraInfo(b, ctx)}
                  </span>
                  {upgradable && (
                    <img
                      src="/graphic/overview/build_big.png"
                      style={{ display: "inline" }}
                      id={"hammer_" + b.id}
                      className="upgrade_hammer"
                      alt=""
                    />
                  )}
                </div>
              )}
            </div>
          );
        })}
        {tipNode}
      </div>
    </div>
  );
}

/* ---------- summary (classical) ---------- */
function ClassicalOverview({ ctx, levels, canUpgrade, go, onUpgrade }) {
  const [tipProps, tipNode] = useTip();
  const built = SCENE.filter((b) => levels[b.id].level > 0);
  const anyBuildable = built.some((b) => canUpgrade[b.id]);
  return (
    <>
      <table className="vis" width="100%">
        <tbody>
          {built.map((b) => {
            const { level, queued, info } = levels[b.id];
            const html = canUpgrade[b.id]
              ? `Wood: ${info.nextWood}, Clay: ${info.nextClay}, Iron: ${info.nextIron}, <br/>Population: ${popCost(b, level + queued + 1)} <br/>Building time: ${info.nextSeconds}`
              : "";
            return (
              <tr key={b.id} id={"l_" + b.id}>
                {anyBuildable && (
                  <td>
                    {canUpgrade[b.id] && (
                      <a
                        className="upgrade_level"
                        id={"upgrade_level_" + b.id}
                        href="#"
                        {...tipProps(html)}
                        onClick={(e) => { e.preventDefault(); onUpgrade(b); }}
                      >
                        <img src="/graphic/overview/build.png" alt="Construct" />
                      </a>
                    )}
                  </td>
                )}
                <td width="240">
                  <a href="#" onClick={(e) => go(e, b.screen)}>
                    <img src={`/graphic/buildings/${b.id}.png`} alt="" />
                    {" " + b.name + " "}
                  </a>
                  {`(Level ${level}`}
                  <small id={"order_level_" + b.id}>{absInt(queued)}</small>
                  {")"}
                </td>
                <td className="building_extra">{extraInfo(b, { ...ctx, strong: true })}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {tipNode}
    </>
  );
}

function Summary({ ctx, levels, go, onBuild, busy, visual, setVisual, showLevels, setShowLevels }) {
  const { village } = ctx;
  const [upgradeMode, setUpgradeMode] = useState(() => lsGet("upgrade_buildings", null) != null);
  // the original's get_possible_building_upgrades
  const canUpgrade = {};
  for (const b of SCENE) {
    const info = levels[b.id].info;
    canUpgrade[b.id] =
      !!info && !info.maxedOrQueued && village.wood >= info.nextWood && village.clay >= info.nextClay && village.iron >= info.nextIron;
  }
  const onUpgrade = (b) => {
    if (!busy && b.type) onBuild(b.type);
  };
  const [npcs] = useState(() => ["juggler", "guard", "conversation"].filter(() => Math.floor(Math.random() * 6) === 0));

  if (!visual) {
    return (
      <>
        <ClassicalOverview ctx={ctx} levels={levels} canUpgrade={canUpgrade} go={go} onUpgrade={onUpgrade} />
        <div className="vis_item">
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              setVisual(true);
            }}
          >
            switch to graphical village overview
          </a>
        </div>
      </>
    );
  }
  return (
    <>
      <table width="100%">
        <tbody>
          <tr>
            <td>
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  setShowLevels(!showLevels);
                }}
              >
                {showLevels ? "Hide upgrade levels" : "Show upgrade levels"}
              </a>
            </td>
            <td style={{ textAlign: "center" }}>
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  if (upgradeMode) lsSet("upgrade_buildings", null);
                  else lsSet("upgrade_buildings", 1);
                  setUpgradeMode(!upgradeMode);
                }}
              >
                Upgrade buildings
              </a>
            </td>
            <td align="right">
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  setVisual(false);
                }}
              >
                to classical village overview
              </a>
            </td>
          </tr>
        </tbody>
      </table>
      <VillageScene
        ctx={ctx}
        levels={levels}
        showLevels={showLevels}
        upgradeMode={upgradeMode}
        canUpgrade={canUpgrade}
        go={go}
        onUpgrade={onUpgrade}
        npcs={npcs}
      />
    </>
  );
}

/* ---------- other widgets ---------- */
function Prod({ village }) {
  return (
    <table width="100%">
      <tbody>
        {[
          ["wood", "Wood", village.woodPerHour],
          ["stone", "Clay", village.clayPerHour],
          ["iron", "Iron", village.ironPerHour],
        ].map(([icon, label, perHour]) => {
          const { unit, value } = prodUnit(perHour, village.prodToSeconds, village.prodToMinutes);
          return (
            <tr key={icon} className="nowrap">
              <td width="70">
                <span className={"icon header " + icon} />
                {" " + label + " "}
              </td>
              <td>
                <strong>
                  {greyInt(value, icon).map((p) =>
                    typeof p === "string" ? p : <span key={p.key} className="grey">.</span>,
                  )}
                </strong>
                {` per ${unit} `}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function Units({ village, go }) {
  return (
    <table className="vis" width="100%">
      <tbody>
        {Object.entries(UNITS)
          .filter(([type]) => (village.units[type] ?? 0) > 0)
          .map(([type, u]) => (
            <tr key={type}>
              <td>
                <img src={`/graphic/unit/unit_${u.id}.png`} alt="" />
                <strong>{village.units[type]}</strong>
                {" " + u.name}
              </td>
            </tr>
          ))}
        <tr>
          <td>
            <a href="#" onClick={(e) => go(e, "train")}>» recruit</a>
          </td>
        </tr>
      </tbody>
    </table>
  );
}

function BuildQueue({ village, onCancelBuild }) {
  return (
    <table width="100%" id="overview_buildqueue" className="vis">
      <tbody>
        {village.buildQueue.map((q, i) => {
          const b = SCENE.find((x) => x.type === q.type);
          const info = village.buildings.find((x) => x.type === q.type);
          const active = !!q.completesAt;
          const remaining = active ? new Date(q.completesAt).getTime() - Date.now() : null;
          // duration of a not yet started order: scale the next-level time by the original's 1.2 time factor
          const waitingSecs = q.durationSeconds ?? (info ? Math.round(info.nextSeconds * 1.2 ** (q.targetLevel - (info.level + village.buildQueue.filter((x) => x.type === q.type).length + 1))) : 0);
          return (
            <tr key={i} className="queueRow" style={{ height: 50 }}>
              <td width="40px" align="center">
                <img width="40" src={"/graphic/big_buildings/" + bigImageFile(b, q.targetLevel)} alt={b.name} />
              </td>
              <td>
                {b.name}
                <br />
                <span className={active ? (remaining <= 0 ? "warn small" : "timer small") : "small"}>
                  {active ? (remaining <= 0 ? "overdue" : fmtDuration(remaining)) : fmtDuration(waitingSecs * 1000)}
                </span>
              </td>
              <td align="center">
                <a
                  className="cancel-icon solo evt-confirm"
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    onCancelBuild?.(q);
                  }}
                />
                {"   "}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function Commands({ rows, go, foreign }) {
  return (
    <table className="vis" style={{ width: "100%" }}>
      <tbody>
        <tr>
          <th width="52%">
            {foreign ? "Enemy commands (" : "Own commands ("}
            <span className="command-list-count">{rows.length}</span>
            {")"}
          </th>
          <th width="33%">Arrival</th>
          <th width="15%">Arrival in</th>
        </tr>
        {rows.map((m) => (
          <tr key={m.type + m.id}>
            <td>
              <img src={`/graphic/command/${m.icon}.png`} alt="" />
              <span className="quickedit-out" data-id={m.id} data-ignore-icons="1">
                <span className="quickedit-content">
                  <a href="#" onClick={(e) => go(e, "info_command:" + m.id)}>
                    <span className="quickedit-label">{" " + m.label + " "}</span>
                  </a>
                  <a className="rename-icon" href="#" title="rename" onClick={(e) => e.preventDefault()} />
                </span>
              </span>
            </td>
            <td>{onTime(m.arrivesAt)}</td>
            <td>
              <Timer target={m.arrivesAt} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Notes() {
  return (
    <table width="100%">
      <tbody>
        <tr ref={rawStyle("display:none")}>
          <td id="village_note" />
        </tr>
        <tr>
          <td>
            <a id="edit_notes_link" href="#" onClick={(e) => e.preventDefault()}>» edit</a>
          </td>
        </tr>
      </tbody>
    </table>
  );
}

const displayName = (v) => `${v.name} (${v.x}|${v.y}) K${Math.floor(v.y / 100)}${Math.floor(v.x / 100)}`;

/* ---------- page ---------- */
export function OverviewScreen({ village, villages = [], go, onBuild, busy, onCancelBuild, fetchedAt }) {
  const [visual, setVisualState] = useState(() => lsGet("twlan_ov_visual", true));
  const [showLevels, setShowLevelsState] = useState(() => lsGet("twlan_ov_labels", true));
  const [order, setOrder] = useState(() => lsGet("twlan_ov_order", DEFAULT_ORDER));
  const [collapsed, setCollapsed] = useState(() => lsGet("twlan_ov_collapsed", {}));
  const [dragId, setDragId] = useState(null);

  const setVisual = (v) => { lsSet("twlan_ov_visual", v); setVisualState(v); };
  const setShowLevels = (v) => { lsSet("twlan_ov_labels", v); setShowLevelsState(v); };
  const saveOrder = (o) => { lsSet("twlan_ov_order", o); setOrder(o); };
  const toggle = (id) => {
    const next = { ...collapsed, [id]: !collapsed[id] };
    lsSet("twlan_ov_collapsed", next);
    setCollapsed(next);
  };

  const levels = sceneLevels(village);
  const ctx = { village, fetchedAt: fetchedAt ?? Date.now() };
  const byName = (name) => villages.find((v) => v.name === name);
  const nameOf = (name) => {
    const v = byName(name);
    return v ? displayName(v) : name;
  };
  const own = [...village.outgoingMovements, ...village.incomingMovements].map((m) => ({ ...m, ...commandInfo(m, nameOf) }));

  // widget id -> { active, body } | null (null = the original's template renders nothing -> widget is skipped)
  const widget = (id) => {
    switch (id) {
      case "summary":
        return {
          active: true,
          body: (
            <Summary
              ctx={ctx}
              levels={levels}
              go={go}
              onBuild={onBuild}
              busy={busy}
              visual={visual}
              setVisual={setVisual}
              showLevels={showLevels}
              setShowLevels={setShowLevels}
            />
          ),
        };
      case "outgoingUnits":
        return own.length > 0 ? { active: true, body: <Commands rows={own} go={go} /> } : null;
      case "incomingUnits":
        return null;
      case "prod":
        return { active: true, body: <Prod village={village} /> };
      case "units":
        return { active: true, body: <Units village={village} go={go} /> };
      case "buildqueue":
        return village.buildQueue.length > 0
          ? { active: true, body: <BuildQueue village={village} onCancelBuild={onCancelBuild} /> }
          : { active: false };
      case "notes":
        return { active: true, body: <Notes /> };
      default:
        return { active: false }; // newbie, loyalty, belief, flags, groups
    }
  };

  const move = (col, beforeId) => {
    if (!dragId || dragId === beforeId) return;
    const next = {};
    for (const [c, ids] of Object.entries(order)) next[c] = ids.filter((x) => x !== dragId);
    const list = next[col];
    const at = beforeId ? list.indexOf(beforeId) : -1;
    if (at >= 0) list.splice(at, 0, dragId);
    else list.push(dragId);
    setDragId(null);
    saveOrder(next);
  };

  const renderWidget = (id) => {
    const w = widget(id);
    if (!w) return null;
    const dragProps = {
      wrapper: {
        onDragOver: (e) => dragId && e.preventDefault(),
        onDrop: (e) => {
          e.preventDefault();
          e.stopPropagation();
          move(Object.keys(order).find((c) => order[c].includes(id)), id);
        },
      },
      handle: { draggable: true, onDragStart: () => setDragId(id), onDragEnd: () => setDragId(null) },
    };
    if (!w.active) return <HiddenWidget key={id} id={id} dragProps={dragProps} />;
    return (
      <Widget key={id} id={id} title={WIDGET_TITLES[id]} open={!collapsed["show_" + id]} onToggle={() => toggle("show_" + id)} dragProps={dragProps}>
        {w.body}
      </Widget>
    );
  };

  return (
    <>
      <table cellSpacing="0" cellPadding="0" id="overviewtable" align="center">
        <tbody>
          <tr>
            {["leftcolumn", "rightcolumn"].map((col) => (
              <td
                key={col}
                valign="top"
                id={col}
                width="620"
                onDragOver={(e) => dragId && e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  move(col, null);
                }}
              >
                {order[col].map(renderWidget)}
              </td>
            ))}
          </tr>
        </tbody>
      </table>
      <table style={{ width: "100%", textAlign: "center" }}>
        <tbody>
          <tr>
            <td style={{ textAlign: "left" }}>
              <a
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  lsSet("twlan_ov_order", null);
                  lsSet("twlan_ov_collapsed", null);
                  setOrder(DEFAULT_ORDER);
                  setCollapsed({});
                }}
              >
                » Reset overview
              </a>
            </td>
          </tr>
        </tbody>
      </table>
    </>
  );
}
