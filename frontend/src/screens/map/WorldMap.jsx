import { Fragment, useMemo, useRef, useState } from "react";
import { WORLDMAP_SIZE, drawWorldMap, loadSetting, saveSetting } from "../../lib/map";

const FILTERS = [
  ["barbarian", "worldmap_barbarian_toggle", "Barbarians"],
  ["ally", "worldmap_ally_toggle", "Your tribe"],
  ["partner", "worldmap_partner_toggle", "Allies"],
  ["nap", "worldmap_nap_toggle", "Non-Aggression-Pact (NAP)"],
  ["enemy", "worldmap_enemy_toggle", "Enemies"],
];

/* #worldmap: the draggable overview popup (Worldmap.* in js/map.js). The picture is redrawn on a canvas
   instead of being fetched from page.php?page=worldmap_image; clicking it centres the map there. */
export function WorldMap({ open, infos, onClose, onPick }) {
  const [flags, setFlags] = useState({ barbarian: true, ally: true, partner: true, nap: true, enemy: true });
  const [place, setPlace] = useState(() => loadSetting("worldmap_pos", null));
  const rootRef = useRef(null);
  const drag = useRef(null);

  const own = useMemo(() => infos.filter((v) => v.owner !== 0), [infos]);
  const mine = useMemo(() => infos.filter((v) => v.owner === 1), [infos]);
  const inTribe = useMemo(() => infos.filter((v) => v.relation === "ally"), [infos]);
  const barbarians = infos.length - own.length;
  const image = useMemo(
    () => (open ? drawWorldMap(infos, { barbarian: flags.barbarian, own: true, ally: flags.ally, partner: flags.partner, nap: flags.nap, enemy: flags.enemy }) : null),
    [open, infos, flags]
  );
  // the original prints PHP round($x, 2): 58.9, 0.01, 100 (no trailing zeros)
  const pct = (n) => (infos.length ? Math.round((n / infos.length) * 10000) / 100 : 0);

  const onMouseDown = (e) => {
    // jQuery UI draggable: everything but form controls (and the close link) starts a drag
    if (e.button !== 0 || e.target.closest("input, a, label, select, textarea")) return;
    const el = rootRef.current;
    const r = el.getBoundingClientRect();
    const op = el.offsetParent;
    const or = op && op !== document.body && op !== document.documentElement ? op.getBoundingClientRect() : { left: -window.scrollX, top: -window.scrollY };
    drag.current = { mx: e.clientX, my: e.clientY, left: r.left - or.left, top: r.top - or.top };
    e.preventDefault();
    let last = null;
    const move = (ev) => {
      const d = drag.current;
      if (!d) return;
      last = { left: Math.max(0, d.left + ev.clientX - d.mx), top: Math.max(60, d.top + ev.clientY - d.my) };
      setPlace(last);
    };
    const up = () => {
      window.removeEventListener("mousemove", move, true);
      window.removeEventListener("mouseup", up, true);
      drag.current = null;
      if (last) saveSetting("worldmap_pos", last);
    };
    window.addEventListener("mousemove", move, true);
    window.addEventListener("mouseup", up, true);
  };

  const onImageClick = (e) => {
    e.preventDefault();
    const r = e.currentTarget.getBoundingClientRect();
    const bias = 500 - WORLDMAP_SIZE / 2;
    onPick(Math.floor(e.clientX - r.left + bias), Math.floor(e.clientY - r.top + bias));
  };

  return (
    <div
      ref={rootRef}
      id="worldmap"
      className="popup_style ui-draggable"
      style={open ? { display: "block", ...(place ?? {}) } : undefined}
      onMouseDown={onMouseDown}
    >
      <form method="post" action="" name="worldmap" onSubmit={(e) => e.preventDefault()}>
        <div id="worldmap_header">
          <div className="close popup_menu">
            <a
              href="#"
              onClick={(e) => {
                e.preventDefault();
                onClose();
              }}
            >
              close
            </a>
          </div>
          <fieldset id="worldmap_settings">
            {FILTERS.map(([key, name, label]) => (
              <Fragment key={key}>
                <input
                  type="checkbox"
                  checked={flags[key]}
                  onChange={(e) => setFlags({ ...flags, [key]: e.target.checked })}
                  id={name}
                  name={name}
                />
                <label htmlFor={name}>{label}</label>
              </Fragment>
            ))}
          </fieldset>
          <input type="hidden" value="300" name="min_x" />
          <input type="hidden" value="300" name="min_y" />
        </div>
        <img style={{ display: "none" }} alt="Loading..." id="worldmap-throbber" src="graphic/throbber.gif" />
        <div
          id="worldmap_body"
          style={image ? { width: WORLDMAP_SIZE, height: WORLDMAP_SIZE, backgroundImage: `url(${image})` } : undefined}
        >
          <div id="worldmap_image">
            <input
              type="image"
              src="graphic/transparent.png"
              style={image ? { width: WORLDMAP_SIZE, height: WORLDMAP_SIZE } : undefined}
              onClick={onImageClick}
            />
          </div>
        </div>
        <div id="worldmap_footer">
          <table style={{ textAlign: "left", display: "inline" }}>
            <tbody>
              <tr>
                <th>Villages</th>
                <th>Barbarians</th>
                <th>%</th>
                <th>Your tribe</th>
                <th>%</th>
                <th>Your own</th>
                <th>%</th>
              </tr>
              <tr>
                <td>{infos.length}</td>
                <td>{barbarians}</td>
                <td>{pct(barbarians)}</td>
                <td>{inTribe.length}</td>
                <td>{pct(inTribe.length)}</td>
                <td>{mine.length}</td>
                <td>{pct(mine.length)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </form>
    </div>
  );
}
