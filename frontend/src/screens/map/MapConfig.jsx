import { useState } from "react";
import { MAP_SIZES, MINIMAP_SIZES, MINI, TILE, xProcess } from "../../lib/map";

/* Size <select>: the hidden first option carries the current "WxH" so non-square sizes still show. */
function sizeSelectValue(w, h, list) {
  return w === h && list.includes(w) ? String(w) : `${w}x${h}`;
}

/* Right-hand column below the minimap: display options, "Center map" and "Change map size". */
export function MapConfig({
  village,
  sizePx,
  miniPx,
  ctxEnabled,
  setCtxEnabled,
  popupEnabled,
  setPopupEnabled,
  onToggleWorld,
  onFocusXY,
  onResizeMap,
  onResizeMinimap,
}) {
  const [cx, setCx] = useState(String(village.x));
  const [cy, setCy] = useState(String(village.y));
  const [unitsOn, setUnitsOn] = useState(false);
  const mapW = Math.floor(sizePx[0] / TILE[0]);
  const mapH = Math.floor(sizePx[1] / TILE[1]);
  const miniW = Math.floor(miniPx[0] / MINI);
  const miniH = Math.floor(miniPx[1] / MINI);
  const sizeLink = "game.php?village=" + village.id + "&screen=settings&ajaxaction=set_map_size";

  const typedX = (e) => {
    const r = xProcess(e.target.value, cy);
    setCx(r.x);
    setCy(r.y);
    if (r.focusY) document.getElementById("mapy")?.focus();
  };
  const cbRow = (id, name) => (
    <tr>
      <td>
        <input type="checkbox" name={name} id={id} defaultChecked={false} />
      </td>
      <td>
        <label htmlFor={id}>{{
          map_popup_attack: "Show last attack",
          map_popup_moral: "Show morale",
          map_popup_res: "Show resources",
          map_popup_pop: "Show population",
          map_popup_trader: "Show merchants",
          map_popup_reservation: "Show reservations",
          map_popup_units_times: "Show walking duration",
          map_popup_notes: "Show Village Notebook",
        }[id]}</label>
      </td>
    </tr>
  );

  return (
    <>
      <div id="map_config">
        <div style={{ marginTop: 10, marginBottom: 10 }}>
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              onToggleWorld();
            }}
          >
            » Show World Map
          </a>
          <br />
        </div>
        <table width="100%" style={{ borderSpacing: 0, borderCollapse: "collapse" }} className="vis">
          <tbody>
            <tr>
              <th colSpan={3}>Display options</th>
            </tr>
            <tr id="pmap_options" style={{ display: "none" }}>
              <td style={{ paddingLeft: 8 }} colSpan={3}>
                <label>
                  <input type="radio" defaultChecked id="pmap_filter1" value="1" name="pmap_filter" /> Show all{" "}
                </label>
                <br />
                <label>
                  <input type="radio" id="pmap_filter2" value="2" name="pmap_filter" /> Show all tribes{" "}
                </label>
                <br />
                <label>
                  <input type="radio" id="pmap_filter3" value="3" name="pmap_filter" /> Only show own tribe{" "}
                </label>
                <br />
                <label>
                  <input type="radio" id="pmap_filter4" value="4" name="pmap_filter" /> Show owned villages{" "}
                </label>
                <br /> <br />
                <label>
                  <input type="checkbox" defaultChecked id="pmap_show_topo" /> Display on minimap{" "}
                </label>
                <br />
                <label>
                  <input type="checkbox" defaultChecked id="pmap_show_map" /> Display on map{" "}
                </label>
              </td>
            </tr>
            <tr>
              <td>
                <input
                  type="checkbox"
                  checked={ctxEnabled}
                  onChange={(e) => setCtxEnabled(e.target.checked)}
                  id="classiclink"
                  name="usecontext"
                />
              </td>
              <td>
                <label htmlFor="classiclink"> Activate context menu </label>
              </td>
              <td></td>
            </tr>
            <tr>
              <td>
                <input type="checkbox" id="show_popup" checked={popupEnabled} onChange={(e) => setPopupEnabled(e.target.checked)} />
              </td>
              <td>
                <label htmlFor="show_popup">Show popup</label>
              </td>
              <td width="18" style={{ display: "none" }}>
                <img src="graphic/icons/slide_down.png" className="popup_options_toggler" />
              </td>
            </tr>
            <tr id="popup_options" style={{ display: "none" }}>
              <td style={{ paddingLeft: 8 }} colSpan={3}>
                <form id="form_map_popup" onSubmit={(e) => e.preventDefault()}>
                  <table>
                    <tbody>
                      {cbRow("map_popup_attack", "map_popup_attack")}
                      {cbRow("map_popup_moral", "map_popup_moral")}
                      {cbRow("map_popup_res", "map_popup_res")}
                      {cbRow("map_popup_pop", "map_popup_pop")}
                      {cbRow("map_popup_trader", "map_popup_trader")}
                      {cbRow("map_popup_reservation", "map_popup_reservation")}
                      <tr>
                        <td>
                          <input
                            type="checkbox"
                            checked={unitsOn}
                            onChange={(e) => setUnitsOn(e.target.checked)}
                            name="map_popup_units"
                            id="map_popup_units"
                          />
                        </td>
                        <td>
                          <label htmlFor="map_popup_units">Show troops</label>
                        </td>
                      </tr>
                      <tr>
                        <td>
                          <input
                            type="checkbox"
                            key={String(unitsOn)}
                            disabled={!unitsOn}
                            defaultChecked={false}
                            name="map_popup_units_home"
                            id="map_popup_units_home"
                          />
                        </td>
                        <td>
                          <label htmlFor="map_popup_units_home">Show local troops</label>
                        </td>
                      </tr>
                      {cbRow("map_popup_units_times", "map_popup_units_times")}
                      {cbRow("map_popup_notes", "map_popup_notes")}
                    </tbody>
                  </table>
                </form>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <br />
      <form
        method="post"
        action=""
        onSubmit={(e) => {
          e.preventDefault();
          onFocusXY(~~cx, ~~cy);
        }}
      >
        <table width="100%" style={{ borderSpacing: 0, borderCollapse: "collapse" }} className="vis">
          <tbody>
            <tr>
              <th colSpan={3}>Center map</th>
            </tr>
            <tr>
              <td className="nowrap">
                x:&nbsp;
                <input type="text" onChange={typedX} size={5} value={cx} className="centercoord" id="mapx" name="x" /> y:&nbsp;
                <input type="text" size={5} value={cy} onChange={(e) => setCy(e.target.value)} className="centercoord" id="mapy" name="y" />
              </td>
              <td>
                <input type="submit" value="OK" />
              </td>
              <td width="18" style={{ display: "none" }}>
                <img className="map-slider centercoords_toggler" src="graphic/icons/slide_down.png" />
              </td>
            </tr>
            <tr id="centercoords" style={{ display: "none" }}></tr>
          </tbody>
        </table>
      </form>
      <br />
      <table width="100%" className="vis">
        <tbody>
          <tr>
            <th colSpan={2}>Change map size</th>
          </tr>
          <tr>
            <td>
              <table cellSpacing="0">
                <tbody>
                  <tr>
                    <td width="80">Map:</td>
                    <td>
                      <select
                        value={sizeSelectValue(mapW, mapH, MAP_SIZES)}
                        onChange={(e) => onResizeMap(parseInt(e.target.value, 10))}
                        id="map_chooser_select"
                      >
                        <option style={{ display: "none" }} value={`${mapW}x${mapH}`} id="current-map-size">
                          {`${mapW}x${mapH}`}
                        </option>
                        {MAP_SIZES.map((n) => (
                          <option key={n} value={n}>
                            {`${n}x${n}`}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td valign="middle">
                      <img width="13" height="13" src="graphic/questionmark.png" className="tooltip" alt="" />
                    </td>
                  </tr>
                </tbody>
              </table>
              <input type="hidden" id="change_map_size_link" value={sizeLink} />
            </td>
          </tr>
          <tr>
            <td>
              <table cellSpacing="0">
                <tbody>
                  <tr>
                    <td width="80">Minimap:</td>
                    <td colSpan={2}>
                      <select
                        value={sizeSelectValue(miniW, miniH, MINIMAP_SIZES)}
                        onChange={(e) => onResizeMinimap(parseInt(e.target.value, 10))}
                        id="minimap_chooser_select"
                      >
                        <option style={{ display: "none" }} value={`${miniW}x${miniH}`} id="current-minimap-size">
                          {`${miniW}x${miniH}`}
                        </option>
                        {MINIMAP_SIZES.map((n) => (
                          <option key={n} value={n}>
                            {`${n}x${n}`}
                          </option>
                        ))}
                      </select>
                    </td>
                  </tr>
                </tbody>
              </table>
              <input type="hidden" id="change_map_size_link" value={sizeLink} />
            </td>
          </tr>
        </tbody>
      </table>
    </>
  );
}
