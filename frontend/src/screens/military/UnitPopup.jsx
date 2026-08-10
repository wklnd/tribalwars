// The original's unit popup (unit_popup.js + templates/unit_popup.php) as a React hook.
//   const popup = useUnitPopup();  ->  popup.open(event, "spear")  /  {popup.node}
// `node` renders the exact markup the original appends after the page content:
//   <div class="popup_helper"><div id="inline_popup">…</div></div><div id="unit_popup_template">…</div>
import { useEffect, useRef, useState } from "react";
import { UNIT_BY_ID, rawStyle } from "../../lib/military";

const HIDDEN = { display: "none" };

/* The popup template. With `data` it is the filled copy that UnitPopup.fetchContent() injects
   (requirement / tech blocks hidden because this world defines none); without it, the pristine
   hidden template that the original keeps in the page. */
export function UnitTemplate({ data, unitId }) {
  const d = data;
  const speed = d ? Math.round(1 / (d.speed * 60)) : null;
  const speedText = d ? (speed === 1 ? "1 minute per field" : `${speed} minutes per field`) : null;
  return (
    <div className="inner-border main content-border" ref={rawStyle("border: none; font-weight: normal")}>
      <table style={{ float: "left", width: 380 }}>
        <tbody>
          <tr>
            <td>
              <h2 className="unit_name">{d?.name}</h2>
              <p className="unit_desc">{d?.desc}</p>
            </td>
          </tr>
          <tr>
            <td>
              <table ref={rawStyle("border: 1px solid #DED3B9;")} className="vis" width="100%">
                <tbody>
                  <tr>
                    <th width="180">Costs</th>
                    <th>Population</th>
                    <th>Speed</th>
                    <th>Loot</th>
                  </tr>
                  <tr className="center">
                    <td>
                      <nobr>
                        <span className="icon header wood"> </span>
                        <span className="unit_wood">{d?.wood}</span>
                      </nobr>{" "}
                      <nobr>
                        <span className="icon header stone"> </span>
                        <span className="unit_stone">{d?.stone}</span>
                      </nobr>{" "}
                      <nobr>
                        <span className="icon header iron"> </span>
                        <span className="unit_iron">{d?.iron}</span>
                      </nobr>{" "}
                    </td>
                    <td>
                      <span className="icon header population"> </span>
                      <span className="unit_pop">{d?.pop}</span>
                    </td>
                    <td id="unit_speed">{speedText}</td>
                    <td className="unit_carry">{d?.carry}</td>
                  </tr>
                </tbody>
              </table>
              <br />

              <table
                className="vis has_levels_only"
                ref={rawStyle("border: 1px solid #DED3B9;text-align:center")}
                width="100%"
              >
                <tbody>
                  <tr>
                    <th colSpan="3">Battle statistics</th>
                  </tr>
                  <tr>
                    <td align="left">Attack Power</td>
                    <td width="20px">
                      <img src="/graphic/unit/att.png?1" alt="Attack strength" />
                    </td>
                    <td>
                      <span className="unit_attack">{d?.attack}</span>
                    </td>
                  </tr>
                  <tr>
                    <td align="left">General Defense</td>
                    <td>
                      <img src="/graphic/unit/def.png?1" alt="General Defense" />
                    </td>
                    <td>
                      <span className="unit_defense">{d?.defense}</span>
                    </td>
                  </tr>
                  <tr>
                    <td align="left">Cavalry Defense</td>
                    <td>
                      <img src="/graphic/unit/def_cav.png?1" alt="Cavalry Defense" />
                    </td>
                    <td>
                      <span className="unit_defense_cavalry">{d?.defense_cavalry}</span>
                    </td>
                  </tr>
                  <tr>
                    <td align="left">Archer Defense</td>
                    <td>
                      <img src="/graphic/unit/def_archer.png?1" alt="Archer Defense" />
                    </td>
                    <td>
                      <span className="unit_defense_archer">{d?.defense_archer}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
              <br />

              <div className="show_if_has_reqs" style={d ? HIDDEN : undefined}>
                <table className="vis" width="100%">
                  <tbody>
                    <tr>
                      <th id="reqs_count" colSpan="1">Requirements</th>
                    </tr>
                    <tr id="reqs" />
                  </tbody>
                </table>
                <br />
              </div>

              <table className="unit_tech vis unit_tech_levels" width="100%" style={d ? HIDDEN : undefined}>
                <tbody>
                  <tr style={{ textAlign: "center" }}>
                    <th>Tech-level</th>
                    <th width="350">Research costs</th>
                    <th width="30" style={{ textAlign: "center" }}>
                      <img src="/graphic/unit/att.png?1" alt="Attack Power" />
                    </th>
                    <th width="30" style={{ textAlign: "center" }}>
                      <img src="/graphic/unit/def.png?1" alt="General Defense" />
                    </th>
                    <th width="30" style={{ textAlign: "center" }}>
                      <img src="/graphic/unit/def_cav.png?1" alt="STRING game.unit.defense_sav NOT FOUND" />
                    </th>
                    <th width="30" style={{ textAlign: "center" }}>
                      <img src="/graphic/unit/def_archer.png?1" alt="Archer Defense" />
                    </th>
                  </tr>
                  <tr id="unit_tech_prototype" style={{ display: "none", textAlign: "center" }}>
                    <td className="tech_level" />
                    <td>
                      <span className="grey tech_researched" style={d ? HIDDEN : undefined}>already researched</span>
                      <span className="tech_res_list" style={d ? HIDDEN : undefined}>
                        <span className="icon header wood" />
                        <span className="tech_wood" />
                        <span className="icon header stone" />
                        <span className="tech_stone" />
                        <span className="icon header iron" />
                        <span className="tech_iron" />
                      </span>
                    </td>
                    <td className="tech_att" />
                    <td className="tech_def" />
                    <td className="tech_def_cav" />
                    <td className="tech_def_archer" />
                  </tr>
                </tbody>
              </table>
              <table className="vis unit_tech unit_tech_cost" width="100%" style={d ? HIDDEN : undefined}>
                <tbody>
                  <tr>
                    <th>Research costs</th>
                  </tr>
                  <tr>
                    <td>
                      <span className="icon header wood" />
                      <span className="tech_cost_wood" />
                      <span className="icon header stone" />
                      <span className="tech_cost_stone" />
                      <span className="icon header iron" />
                      <span className="tech_cost_iron" />
                    </td>
                  </tr>
                </tbody>
              </table>
            </td>
          </tr>
        </tbody>
      </table>
      <img
        style={{ marginTop: 60 }}
        id="unit_image"
        src={unitId ? `/graphic/unit_popup/${unitId}.png` : undefined}
        alt=""
      />
    </div>
  );
}

/* inlinePopup(): position = click point + offset(-100,-100), clamped like game.js does. */
function popupPosition(e) {
  const off = -100;
  const doc = document.documentElement;
  const scrollX = window.pageXOffset || 0;
  const scrollY = window.pageYOffset || 0;
  let x = (e?.clientX ?? 0) + scrollX + off;
  let y = (e?.clientY ?? 0) + scrollY + off;
  x = Math.min(Math.max(x, 0), (doc.scrollWidth || 1000) - off);
  y = Math.min(Math.max(y, 60), (doc.scrollHeight || 1000) - off);
  return { x, y };
}

export function useUnitPopup() {
  // phase: "idle" (initial markup) | "show" | "hidden"
  const [st, setSt] = useState({ phase: "idle", unit: null, x: 0, y: 0 });
  const timer = useRef(null);
  const drag = useRef(null);

  useEffect(() => () => clearTimeout(timer.current), []);

  const open = (e, unitId) => {
    if (e && e.preventDefault) e.preventDefault();
    if (!UNIT_BY_ID[unitId]) return false;
    clearTimeout(timer.current);
    const pos = popupPosition(e);
    setSt({ phase: "show", unit: unitId, x: pos.x, y: pos.y });
    return false;
  };

  const close = (e) => {
    if (e && e.preventDefault) e.preventDefault();
    setSt((s) => ({ ...s, phase: "closing" }));
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setSt((s) => ({ ...s, phase: "hidden" })), 300);
  };

  const onMenuDown = (e) => {
    if (e.target.tagName === "A") return;
    drag.current = { dx: e.clientX - st.x, dy: e.clientY - st.y };
    const move = (ev) => {
      if (!drag.current) return;
      setSt((s) => ({ ...s, x: ev.clientX - drag.current.dx, y: ev.clientY - drag.current.dy }));
    };
    const up = () => {
      drag.current = null;
      window.removeEventListener("mousemove", move);
      window.removeEventListener("mouseup", up);
    };
    window.addEventListener("mousemove", move);
    window.addEventListener("mouseup", up);
  };

  const active = st.phase === "show" || st.phase === "closing" || st.phase === "hidden";
  const cls = st.phase === "show" ? "show" : st.phase === "hidden" ? "hidden" : undefined;
  const style = active
    ? { width: 700, left: st.x + "px", top: st.y + "px" }
    : { width: 400 };

  const node = (
    <>
      <div className="popup_helper">
        <div id="inline_popup" className={cls} style={style}>
          <div id="inline_popup_menu" onMouseDown={onMenuDown}>
            <a href="#" onClick={close}>close</a>
          </div>
          <div id="inline_popup_main" style={{ height: "auto", ...(active ? { maxHeight: 950 } : null) }}>
            <div id="inline_popup_content">
              {active && st.unit && <UnitTemplate data={UNIT_BY_ID[st.unit]} unitId={st.unit} />}
            </div>
          </div>
        </div>
      </div>
      <div id="unit_popup_template" style={{ display: "none" }}>
        <UnitTemplate />
      </div>
    </>
  );
  return { open, close, node };
}
