// screen=report — mirrors templates/twlan/controllers/game/report/{index,overview,view_attack}.php.
// The original ignores `mode` (every tab renders the "all" list with the first tab highlighted),
// so we do the same. Single reports: hash "report:all:<id>"  (original: mode=all&view=<id>).
import { useEffect, useMemo, useState } from "react";
import { api } from "../api";
import { PLAYER_NAME } from "../lib/data";
import { buildingTypeName } from "../lib/buildings";
import {
  TYPE_TO_ID,
  UNIT_LIST,
  fmtOnTime,
  hashParts,
  navigateTo,
  setHashTarget,
  villageDisplayName,
} from "../lib/military";
import { useUnitPopup } from "./military/UnitPopup";

export const REPORT_MODES = [
  ["all", "all"],
  ["attack", "attacks"],
  ["defense", "Defenses"],
  ["support", "support"],
  ["trade", "trade"],
  ["other", "other"],
  ["forwarded", "forwarded"],
  ["public", "public"],
  ["filter", "Filter"],
  ["groups", "Ordner"],
];

/* Reports can't be deleted server-side (no backend endpoint); the delete/read state is kept per browser. */
const LS_DELETED = "twlan2.reports.deleted";
const LS_READ = "twlan2.reports.read";
const loadSet = (key) => {
  try {
    return new Set(JSON.parse(window.localStorage.getItem(key) || "[]"));
  } catch {
    return new Set();
  }
};
const saveSet = (key, set) => {
  try {
    window.localStorage.setItem(key, JSON.stringify([...set]));
  } catch {
    /* storage unavailable */
  }
};

// the player who attacked (reports written before players were named are the viewer's own attacks)
const attackerName = (r) => r.attackerPlayer ?? PLAYER_NAME;

// green: nothing lost, yellow: won with losses, red: lost - from the viewer's side (a defender report is the mirror image)
function dotColor(r) {
  const viewerWon = r.defenderView ? r.outcome !== "ATTACKER_WIN" : r.outcome === "ATTACKER_WIN";
  if (!viewerWon) return "red";
  const lost = Object.values((r.defenderView ? r.defenderLosses : r.attackerLosses) ?? {}).reduce((a, b) => a + b, 0);
  return lost > 0 ? "yellow" : "green";
}

function useVillages() {
  const [villages, setVillages] = useState([]);
  useEffect(() => {
    let alive = true;
    api.getVillages().then((v) => alive && setVillages(v)).catch(() => {});
    return () => {
      alive = false;
    };
  }, []);
  return villages;
}

const toIdMap = (byType) => {
  const out = {};
  for (const u of UNIT_LIST) out[u.id] = 0;
  for (const [t, n] of Object.entries(byType ?? {})) if (TYPE_TO_ID[t]) out[TYPE_TO_ID[t]] = n;
  return out;
};

/* ---------------- list (overview.php) ---------------- */
function ReportList({ reports, villages, go, readIds, onDelete }) {
  const [sel, setSel] = useState({});
  const [pageSize, setPageSize] = useState("12");
  const byName = (n) => villages.find((v) => v.name === n);
  const allSelected = reports.length > 0 && reports.every((r) => sel[r.id]);
  return (
    <>
      <table className="vis" width="100%" style={{ display: "none" }}>
        {/* TODO Implementation */}
        <tbody>
          <tr>
            <td style={{ width: 40, textAlign: "center" }}>
              <a href="#" onClick={(e) => e.preventDefault()}>[Alle]</a>{" "}
            </td>
            <td align="center" colSpan="2">
              <strong>&gt;New reports&lt; </strong>{" "}
              <a href="#" onClick={(e) => e.preventDefault()}>[Archiv]</a>{" "}
            </td>
            <td width="140">
              <a href="#" onClick={(e) => e.preventDefault()}>» Ordner erstelle</a>
            </td>
          </tr>
        </tbody>
      </table>

      <form
        method="post"
        onSubmit={(e) => {
          e.preventDefault();
          const ids = reports.filter((r) => sel[r.id]).map((r) => r.id);
          if (ids.length) onDelete(ids);
          setSel({});
        }}
      >
        <table id="report_list" className="vis" width="100%">
          <tbody>
            <tr>
              <th colSpan="2">Subject</th>
              <th>Received</th>
            </tr>
            {reports.map((r) => {
              const to = byName(r.defenderVillageName);
              const toName = to ? villageDisplayName(to) : r.defenderVillageName;
              return (
                <tr key={r.id}>
                  <td>
                    <input
                      name={`id_${r.id}`}
                      type="checkbox"
                      checked={!!sel[r.id]}
                      onChange={(e) => setSel({ ...sel, [r.id]: e.target.checked })}
                    />
                  </td>
                  <td style={{ overflow: "hidden" }}>
                    <div className="nowrap float_right" style={{ marginTop: 2 }}>
                      <img src="/graphic/command/attack.png" className="" />
                    </div>
                    <img src={`/graphic/dots/${dotColor(r)}.png`} className="" />{" "}
                    <span className="quickedit" data-id={r.id}>
                      <span className="quickedit-content">
                        <a href="#" onClick={(e) => go(e, `report:all:${r.id}`)}>
                          <span className="quickedit-label">
                            {`${attackerName(r)} (${r.attackerVillageName}) attacks ${toName}`}
                          </span>
                        </a>
                        {!readIds.has(r.id) && "(new)"}{" "}
                        <a className="rename-icon" href="#" title="rename" onClick={(e) => e.preventDefault()} />
                      </span>
                    </span>
                  </td>
                  <td className="nowrap">{fmtOnTime(r.occurredAt)}</td>
                </tr>
              );
            })}
            <tr>
              <th colSpan="2">
                <input
                  name="all"
                  type="checkbox"
                  className="selectAll"
                  id="select_all"
                  checked={allSelected}
                  onChange={(e) => setSel(Object.fromEntries(reports.map((r) => [r.id, e.target.checked])))}
                />{" "}
                <label htmlFor="select_all">select all</label>
              </th>
              <th />
            </tr>
          </tbody>
        </table>

        <table className="vis" align="left" style={{ float: "left" }}>
          <tbody>
            <tr>
              <td>
                <input type="hidden" value="0" name="from" readOnly />
                <input type="hidden" value={String(reports.length)} name="num_reports" readOnly />
                <input type="hidden" value="0" name="current_group_id" readOnly />
                <input className="btn btn-cancel" type="submit" value="Delete" name="del" />
                <input style={{ display: "none" }} className="btn" type="submit" value="Publish" name="forward" />
                <input style={{ display: "none" }} className="btn" type="submit" value="Weiterleiten" name="real_forward" />
              </td>
              <td style={{ display: "none" }}>
                <select name="group_id">
                  <option value="127">Archiv</option>
                </select>
                <input className="btn" type="submit" value="Verschieben" name="arch" />
              </td>
            </tr>
          </tbody>
        </table>
      </form>

      <form method="post" onSubmit={(e) => e.preventDefault()}>
        <table className="vis nowrap" align="left" style={{ float: "left" }}>
          <tbody>
            <tr>
              <th colSpan="2">Reports per page:</th>
              <td>
                <input name="page_size" type="text" style={{ width: 50 }} value={pageSize} onChange={(e) => setPageSize(e.target.value)} />
              </td>
              <td>
                <input className="btn" type="submit" value="Ok" />
              </td>
            </tr>
          </tbody>
        </table>
      </form>

      <div style={{ clear: "both" }}> </div>
    </>
  );
}

/* ---------------- single report (view_attack.php) ---------------- */
function UnitRows({ id, before, after, popup }) {
  return (
    <table id={id} className="vis" style={{ borderCollapse: "collapse" }}>
      <tbody>
        <tr className="center">
          <td />
          {UNIT_LIST.map((u) => (
            <td width="35" key={u.id}>
              <a className="unit_link" href="#" onClick={(e) => popup.open(e, u.id)}>
                <img src={`/graphic/unit/unit_${u.id}.png`} title={u.name} alt="" className={!before[u.id] ? "faded" : undefined} />
              </a>
            </td>
          ))}
        </tr>
        <tr>
          <td width="20%">Amount:</td>
          {UNIT_LIST.map((u) => (
            <td style={{ textAlign: "center" }} className={"unit-item" + (before[u.id] === 0 ? " hidden" : "")} key={u.id}>
              {before[u.id]}
            </td>
          ))}
        </tr>
        <tr>
          <td align="left" width="20%">losses:</td>
          {UNIT_LIST.map((u) => {
            const lost = before[u.id] - after[u.id];
            return (
              <td style={{ textAlign: "center" }} className={"unit-item" + (lost === 0 ? " hidden" : "")} key={u.id}>
                {lost}
              </td>
            );
          })}
        </tr>
      </tbody>
    </table>
  );
}

/* What the surviving scouts saw: resources, buildings, troops at home and troops away (depending on how many made it). */
function SpyInfo({ spy, popup }) {
  const units = (byType) => toIdMap(byType);
  const troopRow = (title, id, byType) => {
    const counts = units(byType);
    return (
      <tr>
        <th colSpan="2">{title}</th>
        <td colSpan="1" style={{ padding: 0 }}>
          <table id={id} className="vis" style={{ borderCollapse: "collapse" }}>
            <tbody>
              <tr className="center">
                {UNIT_LIST.map((u) => (
                  <td width="35" key={u.id}>
                    <a className="unit_link" href="#" onClick={(e) => popup.open(e, u.id)}>
                      <img src={`/graphic/unit/unit_${u.id}.png`} title={u.name} alt="" className={!counts[u.id] ? "faded" : undefined} />
                    </a>
                  </td>
                ))}
              </tr>
              <tr>
                {UNIT_LIST.map((u) => (
                  <td style={{ textAlign: "center" }} className={"unit-item" + (counts[u.id] ? "" : " hidden")} key={u.id}>
                    {counts[u.id]}
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
        </td>
      </tr>
    );
  };
  return (
    <>
      <br />
      <table id="attack_spy" width="100%" style={{ border: "1px solid #DED3B9" }}>
        <tbody>
          <tr>
            <th colSpan="3">Espionage</th>
          </tr>
          <tr>
            <th>Resources:</th>
            <td colSpan="2">
              {[["wood", spy.wood], ["stone", spy.clay], ["iron", spy.iron]].map(([k, n]) => (
                <span className="nowrap" key={k}>
                  <span className={`icon header ${k}`}> </span>
                  {n}{" "}
                </span>
              ))}
            </td>
          </tr>
          {spy.level >= 2 && (
            <tr>
              <th>Buildings:</th>
              <td colSpan="2">
                {Object.entries(spy.buildings ?? {}).map(([type, level]) => (
                  <div key={type}>{`${buildingTypeName(type)} (Level ${level})`}</div>
                ))}
              </td>
            </tr>
          )}
          {spy.level >= 3 && troopRow("Troops in the village:", "spy_units_home", spy.unitsHome ?? {})}
          {spy.level >= 4 && troopRow("Troops outside:", "spy_units_away", spy.unitsAway ?? {})}
        </tbody>
      </table>
    </>
  );
}

function ReportView({ report: r, reports, villages, go, popup, onDelete }) {
  const from = villages.find((v) => v.name === r.attackerVillageName);
  const to = villages.find((v) => v.name === r.defenderVillageName);
  const fromName = from ? villageDisplayName(from) : r.attackerVillageName;
  const toName = to ? villageDisplayName(to) : r.defenderVillageName;
  const won = r.outcome === "ATTACKER_WIN";
  const lossesA = toIdMap(r.attackerLosses);
  const lossesD = toIdMap(r.defenderLosses);
  // the backend report only stores losses; use the sent/present troops when it provides them
  const beforeA = r.attackerUnits ? toIdMap(r.attackerUnits) : lossesA;
  const beforeD = r.defenderUnits ? toIdMap(r.defenderUnits) : lossesD;
  const afterA = Object.fromEntries(UNIT_LIST.map((u) => [u.id, Math.max(0, beforeA[u.id] - lossesA[u.id])]));
  const afterD = Object.fromEntries(UNIT_LIST.map((u) => [u.id, Math.max(0, beforeD[u.id] - lossesD[u.id])]));
  const showDefenderTroops = r.defenderView || won || Object.values(afterA).some((n) => n > 0);
  const luck = Number(r.luck ?? 0);
  const p = luck < 0 ? -2 * luck : 0;
  const g = luck > 0 ? 2 * luck : 0;
  const loot = { wood: Math.floor(r.lootWood ?? 0), stone: Math.floor(r.lootClay ?? 0), iron: Math.floor(r.lootIron ?? 0) };
  const used = loot.wood + loot.stone + loot.iron;
  const max = r.lootCapacity ?? used;
  const idx = reports.findIndex((x) => x.id === r.id);
  const next = reports[idx + 1] ?? r;
  const subject = `${attackerName(r)} (${r.attackerVillageName}) attacks ${toName}`;
  const exportCode = useMemo(() => {
    try {
      return btoa(JSON.stringify(r));
    } catch {
      return "";
    }
  }, [r]);

  return (
    <>
      <table className="vis" width="470">
        <tbody>
          <tr>
            <td className="nopad">
              <table align="center" className="vis" width="100%" style={{ marginTop: -2 }}>
                <tbody>
                  <tr>
                    <td align="center" width="20%" style={{ display: "none" }}>
                      {/* TODO */}
                      <a href="#" onClick={(e) => e.preventDefault()}>Weiterleiten</a>
                    </td>
                    <td align="center" width="20%" style={{ display: "none" }}>
                      {/* TODO */}
                      <a href="#" onClick={(e) => e.preventDefault()}>Verschieben</a>
                    </td>
                    <td align="center" width="20%">
                      <a
                        href="#"
                        onClick={(e) => {
                          onDelete([r.id]);
                          go(e, "report:all");
                        }}
                      >
                        Delete
                      </a>
                    </td>
                    <td align="center" width="20%" style={{ display: "none" }}>
                      {/* TODO */}
                      <a href="#" onClick={(e) => e.preventDefault()}>
                        <span className="">Exportieren</span>
                      </a>
                    </td>
                    <td align="center" width="20%">
                      <a href="#" id="report-next" className="" onClick={(e) => go(e, `report:all:${next.id}`)}>
                        <img src="/graphic/arrow_up.png" style={{ verticalAlign: -2 }} alt="" className="" />
                      </a>
                    </td>
                    <td align="center" width="20%" />
                  </tr>
                  <tr className="move_list" style={{ display: "none" }}>
                    {/* TODO */}
                    <td align="center" colSpan="4">
                      <form method="POST" onSubmit={(e) => e.preventDefault()}>
                        <select name="group_id">
                          <option value="127">Archiv</option>
                        </select>
                        <input className="btn" type="submit" value="Verschieben" />
                      </form>
                    </td>
                  </tr>
                </tbody>
              </table>

              <table className="vis">
                <tbody>
                  <tr>
                    <th width="140">Subject</th>
                    <th width="400">
                      <img src={`/graphic/dots/${dotColor(r)}.png`} className="" />{" "}
                      <span className="quickedit" data-id={r.id}>
                        <span className="quickedit-content">
                          <span className="quickedit-label">{subject}</span>
                          <a className="rename-icon" href="#" title="rename" onClick={(e) => e.preventDefault()} />
                        </span>
                      </span>
                    </th>
                  </tr>
                  <tr>
                    <td>Time</td>
                    <td>{fmtOnTime(r.occurredAt)}</td>
                  </tr>
                  <tr>
                    <td colSpan="2" valign="top" height="160" style={{ border: "solid 1px black", padding: 4 }}>
                      <h3>{`${won ? attackerName(r) : r.defenderPlayer ?? to?.ownerName ?? "Defender"} has won`}</h3>
                      <div className="report_image image_attack_won">
                        <div className="report_transparent_overlay">
                          <h4>Attackerluck</h4>
                          <table id="attack_luck">
                            <tbody>
                              <tr>
                                <td className="nobg">
                                  <img src={`/graphic/rabe${luck < 0 ? "" : "_grau"}.png`} alt="bad luck" className="" />
                                </td>
                                <td className="nobg">
                                  <table className="luck" cellSpacing="0" cellPadding="0">
                                    <tbody>
                                      <tr>
                                        <td className="luck-item nobg" width="0" height="12" />
                                        <td className="luck-item nobg" width={50 - p} />
                                        {p !== 0 && (
                                          <td className="luck-item nobg" style={{ backgroundImage: "url(/graphic/balken_pech.png)" }} width={p} />
                                        )}
                                        <td className="luck-item nobg" width="1" style={{ backgroundColor: "black" }} />
                                        {g !== 0 && (
                                          <td className="luck-item nobg" style={{ backgroundImage: "url(/graphic/balken_glueck.png)" }} width={g} height="12" />
                                        )}
                                        <td className="luck-item nobg" width={50 - g} />
                                      </tr>
                                    </tbody>
                                  </table>
                                </td>
                                <td className="nobg">
                                  <img src={`/graphic/klee${luck > 0 ? "" : "_grau"}.png`} alt="luck" className="" />
                                </td>
                                <td className="nobg">
                                  <b>{`${luck}%`}</b>
                                </td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </div>

                      <table id="attack_info_att" width="100%" style={{ border: "1px solid #DED3B9" }}>
                        <tbody>
                          <tr>
                            <th style={{ width: "20%" }}>Attacker:</th>
                            <th>
                              <a href="#" onClick={(e) => navigateTo(e, r.attackerPlayer ? "info_player:p_" + r.attackerPlayer : "info_player", go)}>{attackerName(r)}</a>
                            </th>
                          </tr>
                          <tr>
                            <td>origin:</td>
                            <td>
                              <span className="village_anchor contexted" data-player="2" data-id={from?.id ?? ""}>
                                <a href="#" onClick={(e) => navigateTo(e, from?.id ? "info_village:" + from.id : "info_village", go)}>{fromName}</a>{" "}
                                <a className="ctx" href="#" onClick={(e) => e.preventDefault()} />
                              </span>
                            </td>
                          </tr>
                          <tr>
                            <td colSpan="2" style={{ padding: 0 }}>
                              <UnitRows id="attack_info_att_units" before={beforeA} after={afterA} popup={popup} />
                            </td>
                          </tr>
                        </tbody>
                      </table>
                      <br />

                      <table id="attack_info_def" width="100%" style={{ border: "1px solid #DED3B9" }}>
                        <tbody>
                          <tr>
                            <th style={{ width: "20%" }}>Defender:</th>
                            <th>{r.defenderPlayer ?? (to?.ownerType === "PLAYER" ? to.ownerName : null) ? <a href="#" onClick={(e) => navigateTo(e, "info_player:p_" + (r.defenderPlayer ?? to.ownerName), go)}>{r.defenderPlayer ?? to.ownerName}</a> : "---"}</th>
                          </tr>
                          <tr>
                            <td>target:</td>
                            <td>
                              <span className="village_anchor contexted" data-player="0" data-id={to?.id ?? ""}>
                                <a href="#" onClick={(e) => navigateTo(e, to?.id ? "info_village:" + to.id : "info_village", go)}>{toName}</a>{" "}
                                <a className="ctx" href="#" onClick={(e) => e.preventDefault()} />
                              </span>
                            </td>
                          </tr>
                          {!showDefenderTroops ? (
                            <tr>
                              <td colSpan="2">
                                None of your troops have returned.
                                <br /> No information about the strength of your enemy's army could be collected
                              </td>
                            </tr>
                          ) : (
                            <tr>
                              <td colSpan="2" style={{ padding: 0 }}>
                                <UnitRows id="attack_info_def_units" before={beforeD} after={afterD} popup={popup} />
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                      <br />
                      <table id="attack_results" width="100%" style={{ border: "1px solid #DED3B9" }}>
                        <tbody>
                          {won && !r.spy?.level && (
                            <tr>
                              <th>Loot:</th>
                              <td width="250">
                                {["wood", "stone", "iron"].map((k) => (
                                  <span className="nowrap" key={k}>
                                    <span className={`icon header ${k}`}> </span>
                                    {loot[k]}
                                  </span>
                                ))}
                              </td>
                              <td>{`${used}/${max}`}</td>
                            </tr>
                          )}
                          {r.morale != null && r.morale < 100 && (
                            <tr>
                              <th>Morale:</th>
                              <td colSpan="2">{`${r.morale}%`}</td>
                            </tr>
                          )}
                          {r.wallBefore != null && r.wallAfter != null && r.wallAfter < r.wallBefore && (
                            <tr>
                              <th>Wall:</th>
                              <td colSpan="2">
                                Wall damaged from level <b>{r.wallBefore}</b> to <b>{r.wallAfter}</b>
                              </td>
                            </tr>
                          )}
                          {Object.entries(r.buildingDamage ?? {})
                            .filter(([type]) => type !== "WALL")
                            .map(([type, levels]) => (
                              <tr key={type}>
                                <th>Damage:</th>
                                <td colSpan="2">
                                  <b>{buildingTypeName(type)}</b> was damaged by <b>{levels}</b> {levels === 1 ? "level" : "levels"}
                                </td>
                              </tr>
                            ))}
                          {r.loyaltyFrom != null && r.loyaltyTo != null && (
                            <tr>
                              <th>Loyalty:</th>
                              <td colSpan="2">
                                Loyalty loss from <b>{Math.ceil(r.loyaltyFrom)}</b> to <b>{Math.ceil(r.loyaltyTo)}</b>
                                {r.conquered && (
                                  <>
                                    <br />
                                    <b>The village has been conquered.</b>
                                  </>
                                )}
                              </td>
                            </tr>
                          )}
                        </tbody>
                      </table>
                      {r.spy && <SpyInfo spy={r.spy} popup={popup} />}
                      <br />
                      {/* TODO below */}
                      <a style={{ display: "none" }} href="#" onClick={(e) => e.preventDefault()}>
                        » Insert troops into the simulator
                      </a>
                      <br />
                      <a style={{ display: "none" }} href="#" onClick={(e) => e.preventDefault()}>
                        » Insert surviving troops into the simulator
                      </a>
                      <hr />
                      <a style={{ display: "none" }} href="#" onClick={(e) => e.preventDefault()}>
                        » Attack the village
                      </a>
                      <br />
                      <a style={{ display: "none" }} href="#" onClick={(e) => e.preventDefault()}>
                        » Mit gleichen Truppe noch einmal angreifen
                      </a>
                      <br />
                      <a style={{ display: "none" }} href="#" onClick={(e) => e.preventDefault()}>
                        » Mit allen Truppe noch einmal angreifen
                      </a>
                      <hr />
                      <a style={{ display: "none" }} href="#" onClick={(e) => e.preventDefault()}>
                        » Publish the report
                      </a>
                    </td>
                  </tr>
                </tbody>
              </table>
            </td>
          </tr>
        </tbody>
      </table>
      {/* TODO Export Report + Quickedit below */}
      <textarea
        cols="55"
        rows="7"
        onClick={(e) => {
          e.target.focus();
          e.target.select();
        }}
        readOnly
        id="report_export_code"
        style={{ display: "none" }}
        value={`[spoiler][report_export]${exportCode}[/report_export][/spoiler]`}
      />
    </>
  );
}

export function ReportsScreen({ reports = [], mode = "all", go }) {
  const villages = useVillages();
  const popup = useUnitPopup();
  const [deleted, setDeleted] = useState(() => loadSet(LS_DELETED));
  const [readIds, setReadIds] = useState(() => loadSet(LS_READ));
  const [, force] = useState(0);

  // "report:<mode>:<id>" -> single report view (original: &mode=all&view=<id>)
  const parts = hashParts();
  const viewId = parts[0] === "report" && parts[2] ? Number(parts[2]) : null;
  // the "attacks" / "Defenses" tabs split the reports by the side the viewer was on
  const visible = reports.filter((r) => !deleted.has(r.id) && (mode === "attack" ? !r.defenderView : mode === "defense" ? !!r.defenderView : true));
  const viewed = viewId != null ? visible.find((r) => r.id === viewId) : null;

  useEffect(() => {
    if (viewed && !readIds.has(viewed.id)) {
      const next = new Set(readIds).add(viewed.id);
      setReadIds(next);
      saveSet(LS_READ, next);
    }
  }, [viewed, readIds]);

  const onDelete = (ids) => {
    const next = new Set(deleted);
    ids.forEach((i) => next.add(i));
    setDeleted(next);
    saveSet(LS_DELETED, next);
  };

  // tabs of the original all lead to the same "all" list
  const nav = (e, target) => {
    if (go) return go(e, target);
    if (e) e.preventDefault();
    setHashTarget(target);
    force((n) => n + 1);
  };

  return (
    <>
      <h2>Reports</h2>

      <table className="no_spacing" width="100%">
        <tbody>
          <tr>
            <td valign="top">
              <table className="vis modemenu" width="100">
                <tbody>
                  {REPORT_MODES.map(([m, label]) => (
                    <tr key={m}>
                      <td className={m === (["attack", "defense"].includes(mode) ? mode : "all") ? "selected" : undefined} style={{ minWidth: 80 }}>
                        <a href="#" onClick={(e) => nav(e, "report:" + m)}>
                          {label + " "}
                        </a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </td>
            <td valign="top" width="100%">
              {viewed ? (
                <ReportView report={viewed} reports={visible} villages={villages} go={nav} popup={popup} onDelete={onDelete} />
              ) : (
                <ReportList reports={visible} villages={villages} go={nav} readIds={readIds} onDelete={onDelete} />
              )}
            </td>
          </tr>
        </tbody>
      </table>
      {viewed && popup.node}
    </>
  );
}
