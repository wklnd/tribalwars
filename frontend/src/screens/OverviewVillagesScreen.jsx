import { useEffect, useRef, useState } from "react";
import { OV, levelOf, villagePoints } from "../lib/data";
import { PreErrorBox } from "../lib/ui";
import { BonusIcon } from "../lib/BonusIcon";
import { ShipmentsTable, useMarket } from "./MarketScreen";

/* Mirrors templates overviewvillages/{header,combined,groups,footer}.php of the original.
   Real behaviour (checked against the running original):
   - modes with a template: combined, groups (trader is ours, see TransportsView). Every other mode in the menu (prod, trader, units, commands,
     incomings, buildings, tech) renders <div class="error_box">TBI</div> + the empty paged view + text " TODO".
   - unknown/missing mode renders like "combined".
   - the row icons/titles are static in the original (its own "TODO: Implement real data here"). */

export const OVERVIEW_MODES = [
  ["combined", "Combined"],
  ["prod", "Production"],
  ["trader", "Transports"],
  ["units", "Troops"],
  ["commands", "Commands"],
  ["incomings", "Incoming"],
  ["buildings", "Buildings"],
  ["tech", "Research"],
  ["groups", "Groups"],
];

/* [unit id, backend unit type or null (we have no such unit), localized name] in the original's order */
export const COMBINED_UNITS = [
  ["spear", "SPEAR", "Spear fighter"],
  ["sword", "SWORD", "Swordsman"],
  ["axe", "AXE", "Axeman"],
  ["archer", "ARCHER", "Archer"],
  ["spy", "SCOUT", "Scout"],
  ["light", "LIGHT", "Light cavalry"],
  ["marcher", "MARCHER", "Mounted archer"],
  ["heavy", "HEAVY", "Heavy cavalry"],
  ["ram", "RAM", "Ram"],
  ["catapult", "CATAPULT", "Catapult"],
  ["knight", "PALADIN", "Paladin"],
  ["snob", "SNOB", "Nobleman"],
];

const noop = (e) => e.preventDefault();

export function StatusIcon({ src, title, screen, go }) {
  return (
    <a href="#" onClick={(e) => go(e, screen)}>
      <img src={`/graphic/overview/${src}.png`} title={title} alt="" className="status-icon" />
    </a>
  );
}

/* label span + hidden edit span, toggled by the rename icon exactly like game.js editToggle()/editSubmitNew() */
function VillageNameCell({ id, label, name, onRename, go, groupsMode }) {
  const [editing, setEditing] = useState(false);
  const inputRef = useRef(null);
  const okRef = useRef(null);
  const toggle = (e) => {
    e.preventDefault();
    setEditing((v) => !v);
  };
  useEffect(() => {
    if (editing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [editing]);
  const submit = () => {
    const v = inputRef.current.value.trim();
    if (v) onRename(v);
    setEditing(false);
  };
  return (
    <>
      <span id={`label_${id}`} style={editing ? { display: "none" } : undefined}>
        <a href="#" onClick={(e) => go(e, "overview")}>
          <span id={`label_text_${id}`}>{groupsMode ? label : <> {label} </>}</span>
        </a>
        <a className="rename-icon" href="#" onClick={toggle}>&nbsp;</a>
      </span>
      <span id={`edit_${id}`} style={{ display: editing ? undefined : "none" }}>
        <input
          id={`edit_input_${id}`}
          key={name}
          ref={inputRef}
          defaultValue={name}
          onKeyDown={(e) => {
            if (e.keyCode === 13) {
              e.preventDefault();
              okRef.current.click();
            }
          }}
        />
        <input ref={okRef} type="button" value="OK" onClick={submit} />
      </span>
    </>
  );
}

export function CombinedTable({ village, go, name, onRename, onSelectVillage, rows: given }) {
  const sort = () => noop;
  // every village of the player (the backend lists them all; the current one first shows live data)
  const rows = given ?? (village.myVillages?.length
    ? village.myVillages
    : [{ id: village.id, name, x: village.x, y: village.y, populationUsed: village.populationUsed, populationCapacity: village.populationCapacity, farmLevel: levelOf(village, "FARM"), units: village.units }]);
  return (
    <table id="combined_table" className="vis overview_table" width="100%" style={{ whiteSpace: "nowrap" }}>
      <tbody>
        <tr>
          <th><span className="note-icon" /></th>
          <th style={{ textAlign: "left" }}>
            <a href="#" onClick={sort("name")} />
            {`(${rows.length})`}
          </th>
          <th><img src="/graphic/overview/main.png" title="" alt="" /></th>
          <th><img src="/graphic/overview/barracks.png" title="" alt="" /></th>
          <th><img src="/graphic/overview/stable.png" title="" alt="" /></th>
          <th><img src="/graphic/overview/garage.png" title="" alt="" /></th>
          <th><img src="/graphic/overview/smith.png" title="" alt="" /></th>
          <th>
            <a href="#" onClick={sort("pop_available")}>
              <img src="/graphic/overview/farm.png" title="" alt="" />
            </a>
          </th>
          {COMBINED_UNITS.map(([id, , uname]) => (
            <th key={id} style={{ textAlign: "center" }}>
              <a href="#" onClick={sort("spear")}>
                <img src={`/graphic/unit/unit_${id}.png`} alt={uname} title={uname} />
              </a>
            </th>
          ))}
          <th>
            <a href="#" onClick={sort("trader_current")}>
              <img src="/graphic/overview/trader.png" title="" alt="" />
            </a>
          </th>
        </tr>
        {rows.map((v, i) => {
          const isCurrent = v.id === village.id;
          const continent = "K" + Math.floor(v.y / 100) + Math.floor(v.x / 100);
          // the other villages are opened first (village switch), then the screen
          const open = (screen) => (e) => {
            e.preventDefault();
            if (!isCurrent) onSelectVillage?.(v.id);
            go(null, screen);
          };
          const units = isCurrent ? village.units ?? {} : v.units ?? {};
          return (
            <tr key={v.id} className={"nowrap " + (i % 2 ? "row_b" : "row_a")}>
              <td />
              <td>
                {isCurrent ? (
                  <VillageNameCell id={v.id} name={name} label={`${name} (${v.x}|${v.y}) ${continent}`} onRename={onRename} go={go} />
                ) : (
                  <span id={`label_${v.id}`}>
                    <a href="#" onClick={open("overview")}>
                      <span id={`label_text_${v.id}`}> {`${v.name} (${v.x}|${v.y}) ${continent}`} </span>
                    </a>
                  </span>
                )}
                <BonusIcon code={v.bonus} />
              </td>
              <td><StatusIcon src="prod_avail" title="No production" screen="building:HEADQUARTERS" go={open("building:HEADQUARTERS")} /></td>
              <td><StatusIcon src="prod_avail" title="No recruitment" screen="train" go={open("train")} /></td>
              <td><StatusIcon src="prod_avail" title="No recruitment" screen="stable" go={open("stable")} /></td>
              <td><StatusIcon src="prod_avail" title="No recruitment" screen="garage" go={open("garage")} /></td>
              <td><StatusIcon src="prod_finish" title="Technologies fully researched" screen="smith" go={open("smith")} /></td>
              <td>
                <a href="#" onClick={open("farm")}>
                  {`${v.populationCapacity - v.populationUsed} (${isCurrent ? levelOf(village, "FARM") : v.farmLevel ?? 0})`}
                </a>
              </td>
              {COMBINED_UNITS.map(([id, type]) => {
                const amount = type ? units[type] ?? 0 : 0;
                return (
                  <td key={id} className={"unit-item" + (amount ? "" : " hidden")}>
                    {id === "snob" ? <a href="#" onClick={open("snob")}>{amount}</a> : amount}
                  </td>
                );
              })}
              <td>
                <a href="#" onClick={open("market")}>{`${v.populationUsed}/${v.populationCapacity}`}</a>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

/* Group management block. The original fills #group_list via VillageGroups.displayGroupInfo() and the "» edit" rows via
   VillageGroups.showGroups() (JS), so the post-JS DOM is rendered here. Its server side (screen=groups) was never
   implemented, the behaviour follows the JS: group "all" (0) always first, at most 5 groups until "Show N more groups",
   rename/delete icons, checkbox form per village. */
function GroupsView({ village, go, name, onRename, groups, villages }) {
  const vid = village.id;
  const base = `game.php?village=${vid}`;
  const [showAll, setShowAll] = useState(false);
  const [renaming, setRenaming] = useState(null); // group id
  const [editing, setEditing] = useState(null); // village id
  const [checked, setChecked] = useState([]);
  const [error, setError] = useState(null);
  const [newName, setNewName] = useState("");

  const list = [{ id: 0, name: "all" }, ...groups.groups];
  const shown = showAll ? list : list.slice(0, 5);
  const attempt = async (fn) => {
    setError(null);
    try {
      await fn();
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    }
  };
  const groupsOf = (id) => groups.groups.filter((g) => g.villageIds.includes(id));
  const toggleEdit = (e, id) => {
    e.preventDefault();
    if (editing === id) return setEditing(null);
    setEditing(id);
    setChecked(groupsOf(id).map((g) => g.id));
  };

  return (
    <>
      <hr size="3" />
      <div id="group_config" className="group_config" style={{ margin: "10px 0" }}>
        <form
          id="add_group_form"
          action={`${base}&ajaxaction=create_group&screen=groups`}
          method="post"
          onSubmit={async (e) => {
            e.preventDefault();
            if (await attempt(() => groups.actions.create(newName))) setNewName("");
          }}
        >
          <input type="hidden" name="mode" value="groups" readOnly />
          Create group:<input name="group_name" id="add_new_group_name" value={newName} onChange={(e) => setNewName(e.target.value)} />{" "}
          <input className="btn" type="submit" value="OK" />
        </form>

        <input type="hidden" value="Groups" id="group_config_headline" readOnly />
        <input type="hidden" value="OK" id="group_submit_text" readOnly />
        <input type="hidden" value="Rename group" id="group_title_rename" readOnly />
        <input type="hidden" value="Delete group" id="group_title_delete" readOnly />
        <input type="hidden" value={'Are you sure you want to delete group "%1"?'} id="group_msg_confirm_delete" readOnly />
        <input type="hidden" value={`${base}&mode=groups&type=static&group=0&partial=1&screen=overview_villages`} id="start_edit_group_link" readOnly />
        <input type="hidden" value={`${base}&ajaxaction=delete_group&h=fa5a&screen=groups`} id="delete_group_link" readOnly />
        <input type="hidden" value={`${base}&ajaxaction=rename_group&h=fa5a&screen=groups`} id="rename_group_link" readOnly />

        <div id="error_div">{error && <div className="error_box">{error}</div>}</div>
        <div id="group_list">
          <table className="vis" id="group_table" style={{ width: "100%" }}>
            <tbody>
              <tr>
                <th style={{ width: "100%" }}>Groups</th>
              </tr>
              {shown.map((g) => (
                <tr key={g.id} id={`tr_group${g.id}`}>
                  <td id={`show_group${g.id}`} className={g.id === groups.groupId ? "selected" : undefined} style={renaming === g.id ? { display: "none" } : undefined}>
                    <a href="#" onClick={(e) => { e.preventDefault(); groups.selectGroup(g.id); }}>{g.name}</a>
                    {g.id !== 0 && (
                      <>
                        <a
                          href="#"
                          className="float_right"
                          onClick={async (e) => {
                            e.preventDefault();
                            if (window.confirm(`Are you sure you want to delete group "${g.name}"?`)) await attempt(() => groups.actions.remove(g.id));
                          }}
                        >
                          <img src="/graphic/delete_14.png" title="Delete group" alt="" />
                        </a>
                        <a href="#" style={{ margin: "0 5px" }} onClick={(e) => { e.preventDefault(); setRenaming(g.id); }}>
                          <img src="/graphic/rename.png" title="Rename group" alt="" />
                        </a>
                      </>
                    )}
                  </td>
                  {g.id !== 0 && renaming === g.id && (
                    <td id={`rename_group${g.id}`}>
                      <form
                        id={`rename_group_form${g.id}`}
                        className="rename_group_form"
                        method="post"
                        onSubmit={async (e) => {
                          e.preventDefault();
                          if (await attempt(() => groups.actions.rename(g.id, new FormData(e.target).get("group_name")))) setRenaming(null);
                        }}
                      >
                        <input type="hidden" name="group_id" value={g.id} readOnly />
                        <input type="text" name="group_name" defaultValue={g.name} />
                        <input type="submit" className="btn-default" value="OK" />
                      </form>
                    </td>
                  )}
                </tr>
              ))}
              {!showAll && list.length > 5 && (
                <tr>
                  <td>
                    <a href="#" onClick={(e) => { e.preventDefault(); setShowAll(true); }}>
                      {list.length - 5 === 1 ? "Show 1 more group" : `Show ${list.length - 5} more groups`}
                    </a>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <input type="hidden" id="group_assign_action" value={`${base}&screen=groups&ajaxaction=village`} readOnly />
      <form action={`${base}&screen=overview_villages&mode=\`\`bt\`a&action=bulk_edit_villages`} method="post" onSubmit={noop}>
        <table className="vis overview_table" width="100%" id="group_assign_table">
          <tbody>
            <tr>
              <th width="280">
                <a href="#" onClick={noop}>Village</a>
                ({villages.length})
              </th>
              <th><a href="#" onClick={noop}>Amount</a></th>
              <th><a href="#" onClick={noop}>Points</a></th>
              <th><a href="#" onClick={noop}>Farm</a></th>
              <th>Groups</th>
              <th width="100">edit</th>
            </tr>
            {villages.map((v, i) => {
              const cid = v.id;
              const isCurrent = cid === vid;
              const mine = groupsOf(cid);
              return [
                <tr key={cid} className={i % 2 ? "row_b" : "row_a"}>
                  <td>
                    {isCurrent ? (
                      <VillageNameCell id={cid} name={name} label={name} onRename={onRename} go={go} groupsMode />
                    ) : (
                      <span id={`label_${cid}`}>
                        <a href="#" onClick={(e) => { e.preventDefault(); go(e, "overview"); }}>
                          <span id={`label_text_${cid}`}>{v.name}</span>
                        </a>
                      </span>
                    )}
                    <BonusIcon code={v.bonus} />
                  </td>
                  <td id={`assigned_groups_${cid}_count`}>{mine.length}</td>
                  <td id={`assigned_groups_${cid}_points`}>{v.points ?? villagePoints(village)}</td>
                  <td id={`assigned_groups_${cid}_pop`}>{`${isCurrent ? village.populationUsed : v.populationUsed}/${isCurrent ? village.populationCapacity : v.populationCapacity}`}</td>
                  <td id={`assigned_groups_${cid}_names`}>
                    {mine.length ? mine.map((g) => g.name).join(", ") : <span className="grey" style={{ fontStyle: "italic" }}>no groups present</span>}
                  </td>
                  <td>
                    <a href="#" onClick={(e) => toggleEdit(e, cid)}>» edit</a>
                  </td>
                </tr>,
                <tr key={"e" + cid} id={`group_edit_tr_${cid}`} className="nohover" style={editing === cid ? undefined : { display: "none" }}>
                  <td>
                    <div className="group_edit" id={`group_edit_div_${cid}`}>
                      {editing === cid && (
                        <form
                          id={`reassign_village_to_groups_form_group_edit_div_${cid}`}
                          method="post"
                          onSubmit={async (e) => {
                            e.preventDefault();
                            if (await attempt(() => groups.actions.assign(cid, checked))) setEditing(null);
                          }}
                        >
                          <table id="group_table" width="100%" className="vis">
                            <tbody>
                              {groups.groups.map((g) => (
                                <tr key={g.id}>
                                  <td>
                                    <input
                                      type="checkbox"
                                      className="check"
                                      id={`checkbox_${g.name}`}
                                      name="groups[]"
                                      value={g.id}
                                      checked={checked.includes(g.id)}
                                      onChange={(e) => setChecked(e.target.checked ? [...checked, g.id] : checked.filter((x) => x !== g.id))}
                                    />
                                    <p className="p_groups"><label htmlFor={`checkbox_${g.name}`}>{g.name}</label></p>
                                  </td>
                                </tr>
                              ))}
                              {groups.groups.length === 0 && (
                                <tr><td><span className="grey" style={{ fontStyle: "italic" }}>no groups present</span></td></tr>
                              )}
                            </tbody>
                          </table>
                          <input type="hidden" name="village_id" value={cid} readOnly />
                          <input type="submit" className="btn-default" value="OK" />
                        </form>
                      )}
                    </div>
                  </td>
                </tr>,
              ];
            })}
          </tbody>
        </table>
      </form>
      <p>Sorting: village ascending</p>
      <p><small>Click on "Village", "Amount" or "Points" to change the sorting.</small></p>
    </>
  );
}

/* Overviews > Transports: the merchants of every village and everything that is on the road. The original renders "TBI"
   for this mode, so there is no template: plain vis tables. */
function TransportsView({ village, go, rows }) {
  const market = useMarket(village.id);
  const state = market.state;
  const byId = Object.fromEntries((state?.villages ?? []).map((v) => [v.villageId, v]));
  return (
    <>
      <table className="vis overview_table" width="100%">
        <tbody>
          <tr>
            <th>Village ({rows.length})</th>
            <th>Merchants</th>
            <th><span className="icon header wood" /></th>
            <th><span className="icon header stone" /></th>
            <th><span className="icon header iron" /></th>
          </tr>
          {rows.map((v, i) => {
            const m = byId[v.id];
            const cur = v.id === village.id;
            return (
              <tr key={v.id} className={i % 2 ? "row_b" : "row_a"}>
                <td>
                  <a href="#" onClick={(e) => go(e, "market")}>{`${v.name} (${v.x}|${v.y})`}</a>
                </td>
                <td>{m ? `${m.available}/${m.total}` : "-"}</td>
                <td>{Math.floor(cur ? village.wood : v.wood ?? 0)}</td>
                <td>{Math.floor(cur ? village.clay : v.clay ?? 0)}</td>
                <td>{Math.floor(cur ? village.iron : v.iron ?? 0)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <br />
      <ShipmentsTable shipments={state?.shipments} go={go} title="Merchants on the way" />
    </>
  );
}

export function OverviewVillagesScreen({ village, mode, go, onRename, onSelectVillage, groups }) {
  const current = OVERVIEW_MODES.some(([m]) => m === mode) ? mode : "combined";
  const name = village.name;
  const setRenamed = (n) => onRename && onRename(n);
  const hasTemplate = current === "combined" || current === "groups" || current === "trader";
  const allVillages = village.myVillages?.length ? village.myVillages : [{ id: village.id, name, x: village.x, y: village.y, populationUsed: village.populationUsed, populationCapacity: village.populationCapacity, farmLevel: levelOf(village, "FARM"), units: village.units }];
  const inGroup = groups ? groups.inGroup(allVillages) : allVillages;
  return (
    <>
      {!hasTemplate && <PreErrorBox text="TBI" />}
      <input type="hidden" id="overview" value={current} readOnly />
      <table className="vis modemenu" width="100%" id="overview_menu">
        <tbody>
          <tr>
            {OVERVIEW_MODES.map(([m, label]) => (
              <td key={m} style={{ textAlign: "center" }} className={m === current ? "selected" : undefined} width="100">
                <a href="#" onClick={(e) => go(e, OV(m))}>{label}</a>
              </td>
            ))}
          </tr>
        </tbody>
      </table>
      <br />
      <div id="paged_view_content">
        <div className="vis_item" align="center">
          <strong title={`Villages: ${inGroup.length}`} className="group_tooltip">
            &gt;{groups?.group?.name ?? "all"}&lt;
          </strong>
        </div>
        <table className="vis" width="100%">
          <tbody>
            <tr>
              <td align="center" />
            </tr>
          </tbody>
        </table>
        {current === "combined" && <CombinedTable village={village} go={go} name={name} onRename={setRenamed} onSelectVillage={onSelectVillage} rows={inGroup} />}
        {current === "groups" && groups && <GroupsView village={village} go={go} name={name} onRename={setRenamed} groups={groups} villages={allVillages} />}
        {current === "trader" && <TransportsView village={village} go={go} rows={inGroup} />}
        {!hasTemplate && " TODO"}
      </div>
      <form action={`game.php?village=${village.id}&screen=overview_villages&mode=combined&action=change_page_size0`} method="post" onSubmit={noop}>
        <table className="vis">
          <tbody>
            <tr>
              <th colSpan="2"> Villages per page </th>
              <td><input name="page_size" type="text" size="5" defaultValue="5" /></td>
              <td><input type="submit" value="OK" /></td>
            </tr>
          </tbody>
        </table>
      </form>
    </>
  );
}
