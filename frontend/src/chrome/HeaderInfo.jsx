import { useState } from "react";
import { createPortal } from "react-dom";
import { resClass } from "../lib/data";
import { ShadowRow } from "../lib/ui";

// The original ships some inline styles as "margin:0;padding:0;"; keep the attribute verbatim.
const rawStyle = (css) => (el) => {
  if (el) el.setAttribute("style", css);
};

/* Mirrors the `#header_info` block of the original layouts/game.php.
   `now`/`fetchedAt` drive the client-side resource ticking the original does in game.js
   (Timing.tickHandlers.resources). */
/* UI.AjaxPopup(... 'group_popup' ...) of the original: the village list of the group dropdown (arrow next to the
   village name). The player has only the built-in group "all". */
function GroupPopup({ village, rect, onSelect, onClose, groups }) {
  const all = village.myVillages?.length ? village.myVillages : [{ id: village.id, name: village.name, x: village.x, y: village.y }];
  const mine = groups ? groups.inGroup(all) : all;
  return createPortal(
    <div className="popup_helper">
      <div
        id="group_popup"
        className="popup_style"
        style={{ display: "block", width: 320, position: "fixed", top: (rect?.bottom ?? 60) + 1, left: Math.min(rect?.left ?? 0, window.innerWidth - 330) }}
      >
        <div id="group_popup_menu" className="popup_menu">
          Village groups
          <a id="closelink_group_popup" href="#" onClick={(e) => { e.preventDefault(); onClose(); }}>X</a>
        </div>
        <div id="group_popup_content" className="popup_content" style={{ height: 380, overflowY: "auto" }}>
          <form id="select_group_box" onSubmit={(e) => e.preventDefault()}>
            <p style={{ margin: "0 0 10px 0", fontWeight: "bold" }}>
              Group:
              <select
                id="group_id"
                name="group_id"
                style={{ marginLeft: 3 }}
                value={groups?.groupId ?? 0}
                onChange={(e) => groups?.selectGroup(Number(e.target.value))}
              >
                <option value="0">all</option>
                {(groups?.groups ?? []).map((g) => (
                  <option key={g.id} value={g.id}>{g.name}</option>
                ))}
              </select>
            </p>
          </form>
          <div id="group_list_content" style={{ overflow: "auto", height: 340 }}>
            <table className="vis" width="100%" cellPadding="5" cellSpacing="0">
              <tbody>
                <tr>
                  <th className="group_label" colSpan="2">Village</th>
                </tr>
              </tbody>
            </table>
            <div id="group_popup_content_container">
              <table id="group_table" className="vis" width="100%" cellPadding="5" cellSpacing="0">
                <tbody>
                  {mine.map((v) => {
                    const current = v.id === village.id;
                    return (
                      <tr key={v.id}>
                        <td id={current ? "selected_popup_village" : undefined} className={current ? "selected" : undefined}>
                          <a href="#" onClick={(e) => { e.preventDefault(); onSelect(v.id); }}>{v.name}</a>
                        </td>
                        <td className={current ? "selected" : undefined} style={{ fontWeight: "bold", width: 100, textAlign: "right" }}>
                          {v.x}|{v.y}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}

export function HeaderInfo({ village, go, now, fetchedAt, onSwitchVillage, onSelectVillage, groups }) {
  const [groupsOpen, setGroupsOpen] = useState(false);
  const [groupsRect, setGroupsRect] = useState(null);
  const cap = village.warehouseCapacity;
  const continent = "K" + Math.floor(village.y / 100) + Math.floor(village.x / 100);
  const dt = Math.max(0, ((now ?? Date.now()) - (fetchedAt ?? Date.now())) / 1000);
  const tick = (value, perHour) => Math.min(cap, value + ((perHour ?? 0) / 3600) * dt);
  const link = (target) => (e) => go(e, target);

  const resCell = (id, icon, title, value, perHour, screen, first) => {
    const cur = tick(value, perHour);
    return (
      <>
        <td className={"box-item" + (first ? " icon-box firstcell" : "")}>
          <a href="#" title={title} onClick={link(screen)}>
            <span className={"icon header " + icon} />
          </a>
        </td>
        <td className="box-item">
          <span id={id} title={perHour} className={resClass(cur, cap)}>
            {Math.floor(cur)}
          </span>
        </td>
      </>
    );
  };

  const incomingAttacks = village.incomingMovements.filter((m) => m.type === "INCOMING_ATTACK" || m.type === "ATTACKING").length;

  return (
    <table id="header_info" align="center" width="100%" cellSpacing="0">
      <colgroup>
        <col width="1%" />
        <col width="96%" />
        <col width="1%" />
        <col width="1%" />
        <col width="1%" />
      </colgroup>
      <tbody>
        <tr>
          <td className="topAlign">
            <table className="header-border">
              <tbody>
                <tr>
                  <td>
                    <table className="box menu nowrap">
                      <tbody>
                        <tr id="menu_row2">
                          {(village.myVillages?.length ?? 0) > 1 && (
                            <>
                              <td className="box-item icon-box arrowCell">
                                <a id="village_switch_left" className="village_switch_link" href="#" accessKey="a" onClick={(e) => { e.preventDefault(); onSwitchVillage?.(-1); }}>
                                  <span className="arrowLeft"></span>
                                </a>
                              </td>
                              <td className="box-item icon-box arrowCell">
                                <a id="village_switch_right" className="village_switch_link" href="#" accessKey="d" onClick={(e) => { e.preventDefault(); onSwitchVillage?.(1); }}>
                                  <span className="arrowRight"></span>
                                </a>
                              </td>
                            </>
                          )}
                          <td ref={rawStyle("white-space:nowrap;")} id="menu_row2_village" className="box-item icon-box nowrap">
                            <a className="nowrap" href="#" onClick={link("overview")}>
                              <span className="icon header village" />
                              {village.name}
                            </a>
                          </td>
                          <td className="box-item">
                            <b className="nowrap">{`(${village.x}|${village.y}) ${continent}`}</b>
                          </td>
                          <td className="box-item">
                            <a href="#" id="open_groups" onClick={(e) => { e.preventDefault(); setGroupsRect(e.currentTarget.getBoundingClientRect()); setGroupsOpen(true); }} style={groupsOpen ? { display: "none" } : undefined}>
                              <span className="icon header arr_down" />
                            </a>
                            <a href="#" id="close_groups" onClick={(e) => { e.preventDefault(); setGroupsOpen(false); }} style={groupsOpen ? undefined : { display: "none" }}>
                              <span className="icon header arr_up" />
                            </a>
                            {groupsOpen && (
                              <GroupPopup
                                village={village}
                                groups={groups}
                                rect={groupsRect}
                                onClose={() => setGroupsOpen(false)}
                                onSelect={(id) => { setGroupsOpen(false); if (id !== village.id) onSelectVillage?.(id); }}
                              />
                            )}
                            <input type="hidden" id="popup_close" value="close" />
                            <input type="hidden" value="#" id="show_groups_villages_link" />
                            <input type="hidden" value="#" id="village_link" />
                            <input type="hidden" value="overview" id="group_popup_mode" />
                            <input type="hidden" value="Group:" id="group_popup_select_title" />
                            <input type="hidden" value="Village" id="group_popup_villages_select" />
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
                <ShadowRow />
              </tbody>
            </table>
          </td>
          <td align="right" className="topAlign" />
          <td align="right" className="topAlign">
            <table align="right" className="header-border menu_block_right">
              <tbody>
                <tr>
                  <td>
                    <table className="box smallPadding" cellSpacing="0" ref={rawStyle("empty-cells:show;")}>
                      <tbody>
                        <tr ref={rawStyle("height:20px;")}>
                          {resCell("wood", "wood", "Wood", village.wood, village.woodPerHour, "building:TIMBER_CAMP", true)}
                          {resCell("stone", "stone", "Clay", village.clay, village.clayPerHour, "building:CLAY_PIT")}
                          {resCell("iron", "iron", "Iron", village.iron, village.ironPerHour, "building:IRON_MINE")}
                          <td className="box-item icon-box">
                            <a href="#" title="Storage capacity" onClick={link("building:WAREHOUSE")}>
                              <span className="icon header ressources" />
                            </a>
                          </td>
                          <td className="box-item">
                            <span id="storage">{cap}</span>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
                <ShadowRow />
              </tbody>
            </table>
          </td>
          <td align="right" className="topAlign">
            <table className="header-border menu_block_right">
              <tbody>
                <tr>
                  <td>
                    <table className="box smallPadding" cellSpacing="0">
                      <tbody>
                        <tr>
                          <td className="box-item icon-box firstcell">
                            <a href="#" title="Farm" onClick={link("building:FARM")}>
                              <span className="icon header population" />
                            </a>
                          </td>
                          <td className="box-item" align="center" ref={rawStyle("margin:0;padding:0;")}>
                            <span id="pop_current_label">{village.populationUsed}</span>/
                            <span id="pop_max_label">{village.populationCapacity}</span>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
                <ShadowRow />
              </tbody>
            </table>
          </td>
          <td align="right" className="topAlign">
            <table className="header-border menu_block_right" ref={rawStyle("border-collapse:collapse;")}>
              <tbody>
                <tr>
                  <td>
                    <table className="box" cellSpacing="0">
                      <tbody>
                        <tr>
                          <td className="box-item firstcell">
                            <a title="Paladin" href="#" onClick={link("statue")}>
                              <span className="icon header knight" />
                            </a>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
                <ShadowRow />
              </tbody>
            </table>
          </td>
          {incomingAttacks > 0 && (
            <td className="topAlign">
              <table className="header-border menu_block_right">
                <tbody>
                  <tr>
                    <td>
                      <table className="box smallPadding no-gap" cellSpacing="0">
                        <tbody>
                          <tr>
                            <td height="20" align="center" className="box-item firstcell">
                              <a href="#" onClick={link("overview_villages:incomings")}>
                                <img src="/graphic/unit/att.png" title="Incoming attacks" alt="" />
                              </a>
                            </td>
                            <td className="box-item">
                              <a href="#" onClick={link("overview_villages:incomings")}>({incomingAttacks})</a>
                            </td>
                          </tr>
                        </tbody>
                      </table>
                    </td>
                  </tr>
                  <ShadowRow />
                </tbody>
              </table>
            </td>
          )}
        </tr>
      </tbody>
    </table>
  );
}
