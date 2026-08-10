import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { api } from "../api";
import { useLiveTopic, usePollMs } from "../lib/live";
import { PLAYER_NAME, fmtDuration, villagePoints } from "../lib/data";
import { loadSettings as loadFarmSettings, loadTemplates, templateProblem, toBackendUnits } from "../lib/farm";
import { UNIT_BY_ID, travelSeconds, unitsAtHome } from "../lib/military";
import { pushRecent } from "../lib/targets";
import {
  BIAS,
  BOUND,
  CTX_TITLES,
  DEFAULT_MAP_SIZE,
  DEFAULT_MINIMAP_SIZE,
  MINI,
  SUB,
  TILE,
  buildVillageInfos,
  centerPos,
  clampPos,
  contextButtons,
  continentByXY,
  focusPos,
  getCenter,
  homeIndicator,
  inViewport,
  loadSetting,
  saveSetting,
  sizeTiles,
  viewport,
  AXIS_X_H,
} from "../lib/map";
import { MapConfig } from "./map/MapConfig";
import { MapLegend } from "./map/MapLegend";
import { MapPopupContent } from "./map/MapPopup";
import { MapSector } from "./map/MapSector";
import { MiniMap } from "./map/MiniMap";
import { ResizeHandle } from "./map/ResizeHandle";
import { WorldMap } from "./map/WorldMap";
import { useMover } from "./map/useMover";

/* World map (screen=map). Mirrors the original's markup (map_whole / map_wrap / map / minimap / map_config ...)
   and re-implements what js/map.js + js/twmap_drag.js do to it: drag scrolling with the "mover" overlay, tiles
   drawn per 5x5 sector, minimap, coordinate axes, hover popup, click context menu, home pointer, fullscreen,
   resizing and the world map popup.
   The original's own css/map.css is loaded from /map.css (public/) while this screen is used. */

const CTX_IDS = ["mp_res", "mp_att", "mp_lock", "mp_unlock", "mp_fav", "mp_unfav", "mp_msg", "mp_profile", "mp_overview", "mp_recruit", "mp_tab", "mp_info", "mp_farm_a", "mp_farm_b"];
const HIDDEN_ICON = { opacity: 0, display: "none" };
const AXIS_RANGE = 20; // coordinate labels kept around the centre (TWMap.mapHandler.onMove)

const toPx = (tiles) => [tiles[0] * TILE[0], tiles[1] * TILE[1]];
const validSize = (s, fallback) =>
  Array.isArray(s) && s.length === 2 && s.every((n) => Number.isInteger(n) && n >= 4 && n <= 120) ? s : [fallback, fallback];
const hashStr = (s) => {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = (Math.imul(h, 33) ^ s.charCodeAt(i)) >>> 0;
  return h.toString(36);
};

function useMapCss() {
  useEffect(() => {
    if (document.getElementById("twlan-map-css")) return;
    const link = document.createElement("link");
    link.id = "twlan-map-css";
    link.rel = "stylesheet";
    link.href = "/map.css";
    document.head.appendChild(link);
  }, []);
}

/** UI.SuccessMessage: the transient green box (game.css .autoHideBox) shown by the context menu. */
function Notice({ text, error, onDone }) {
  const done = useRef(onDone);
  useLayoutEffect(() => {
    done.current = onDone;
  });
  useEffect(() => {
    const t = setTimeout(() => done.current(), 2000);
    return () => clearTimeout(t);
  }, [text]);
  return createPortal(
    <div className={"autoHideBox " + (error ? "error" : "success")} onClick={onDone}>
      <p>{text}</p>
    </div>,
    document.body
  );
}

export function MapScreen({ village, villages, go, focusAt, onAttack }) {
  useMapCss();
  const nav = useCallback(
    (e, target) => {
      if (go) return go(e, target);
      if (e) e.preventDefault();
      window.location.hash = "#" + encodeURIComponent(target).replace(/%3A/g, ":");
      window.location.reload();
    },
    [go]
  );

  /* ---- the viewer's tribe and its relations (colours), refreshed like the village list ---- */
  const [tribe, setTribe] = useState(null);
  const loadTribe = useRef(null);
  const tribePollMs = usePollMs(15000, 60000);
  useLiveTopic("tribe", () => loadTribe.current?.());
  useEffect(() => {
    let alive = true;
    const load = () => api.tribeMap().then((t) => alive && setTribe(t)).catch(() => {});
    loadTribe.current = load;
    load();
    const id = setInterval(load, tribePollMs);
    return () => { alive = false; loadTribe.current = null; clearInterval(id); };
  }, [tribePollMs]);

  /* ---- world data (TWMap.villages / players) ---- */
  const infoJson = useMemo(() => {
    const list = villages.some((v) => v.id === village.id)
      ? villages
      : [...villages, { id: village.id, name: village.name, x: village.x, y: village.y, ownerType: "PLAYER" }];
    return JSON.stringify(
      buildVillageInfos(list, village, PLAYER_NAME, villagePoints(village), village.outgoingMovements ?? [], village.incomingMovements ?? [], tribe)
    );
  }, [villages, village, tribe]);
  const infos = useMemo(() => JSON.parse(infoJson), [infoJson]);
  const vsig = useMemo(() => hashStr(infoJson), [infoJson]);
  const vmap = useMemo(() => new Map(infos.map((v) => [v.x * 1000 + v.y, v])), [infos]);

  /* ---- map state: viewport position (px of the top-left corner) and box sizes (px) ---- */
  const [sizePx, setSizePx] = useState(() => toPx(validSize(loadSetting("size", null), DEFAULT_MAP_SIZE)));
  const [miniPx, setMiniPx] = useState(() => validSize(loadSetting("minisize", null), DEFAULT_MINIMAP_SIZE).map((n) => n * MINI));
  const [pos, setPos] = useState(() => {
    // "map:500,499" (e.g. "Center on map" of a village page) opens the map on those coordinates
    const m = /^(\d{1,3}),(\d{1,3})$/.exec(focusAt ?? "");
    return m ? focusPos(Number(m[1]), Number(m[2]), sizePx) : focusPos(village.x, village.y, sizePx);
  });
  const posRef = useRef(pos);
  const sizeRef = useRef(sizePx);
  useLayoutEffect(() => {
    posRef.current = pos;
    sizeRef.current = sizePx;
  }, [pos, sizePx]);

  const [ctx, setCtx] = useState(null); // open context menu: { village, x, y }
  const [popup, setPopup] = useState(null); // hover popup: { village, px, py, visible }
  const [overVillage, setOverVillage] = useState(false);
  const [ctxEnabled, setCtxEnabledState] = useState(() => loadSetting("context", true));
  const [popupEnabled, setPopupEnabledState] = useState(() => loadSetting("popup", true));
  const [favorites, setFavorites] = useState(() => new Set(loadSetting("favorites", [])));
  const [worldOpen, setWorldOpen] = useState(false);
  const [fullscreen, setFullscreen] = useState(false);
  const [notice, setNotice] = useState(null);
  const [noticeError, setNoticeError] = useState(false);
  const flash = (text, error = false) => {
    setNoticeError(error);
    setNotice(text);
  };
  const rootRef = useRef(null);
  const wrapRef = useRef(null);
  const popupRef = useRef(null);
  const scrolling = useRef(null);
  const dragging = useRef(false);
  const prevSize = useRef(null);
  const resizeCenter = useRef(null);

  const setCtxEnabled = (v) => {
    setCtxEnabledState(v);
    saveSetting("context", v);
    if (!v) setCtx(null);
  };
  const setPopupEnabled = (v) => {
    setPopupEnabledState(v);
    saveSetting("popup", v);
    if (!v) hidePopup();
  };

  const hidePopup = useCallback(() => setPopup((p) => (p && p.visible ? { ...p, visible: false } : p)), []);

  /** FreeMap.setPosPixel: moving the viewport hides the context menu. */
  const applyPos = useCallback((p) => {
    const np = clampPos(p);
    const cur = posRef.current;
    if (np[0] === cur[0] && np[1] === cur[1]) return;
    posRef.current = np;
    setPos(np);
    setCtx(null);
  }, []);

  /** TWMap.focus */
  const focus = useCallback((x, y) => applyPos(focusPos(x, y, sizeRef.current)), [applyPos]);
  /** TWMap.focusUserSpecified (the original also flashes a beacon, which needs a container this page lacks). */
  const focusUser = useCallback((x, y) => focus(x, y), [focus]);

  /** TWMap.resize: change the box size but keep the current centre. */
  const resizeMap = useCallback((newPx, center) => {
    const c = center ?? getCenter(posRef.current, sizeRef.current);
    sizeRef.current = newPx;
    setSizePx(newPx);
    const np = centerPos(c[0], c[1], newPx, false);
    posRef.current = np;
    setPos(np);
    setCtx(null);
  }, []);
  const saveSizes = useCallback(() => {
    if (document.fullscreenElement) return;
    saveSetting("size", sizeTiles(sizeRef.current));
  }, []);

  /* ---- keep the last-used minimap size, sync with the select ---- */
  const resizeMinimap = (n) => {
    const px = [n * MINI, n * MINI];
    setMiniPx(px);
    saveSetting("minisize", [n, n]);
  };

  /* ---- TWMap.scrollBlock: arrows scroll by one screen ---- */
  const scrollBlock = (x, y) => {
    if (scrolling.current) return;
    const c = getCenter(posRef.current, sizeRef.current);
    const st = sizeTiles(sizeRef.current);
    const dst = [c[0] + st[0] * x, c[1] + st[1] * y];
    const stop = () => {
      clearInterval(scrolling.current);
      scrolling.current = null;
    };
    scrolling.current = setInterval(() => {
      const vp = viewport(posRef.current, sizeRef.current);
      const cur = getCenter(posRef.current, sizeRef.current);
      if ((x === -1 && vp[0] <= BOUND[0]) || (y === -1 && vp[1] <= BOUND[1]) || (x === 1 && vp[2] >= BOUND[2]) || (y === 1 && vp[3] >= BOUND[3])) return stop();
      if ((x > 0 && cur[0] >= dst[0]) || (x < 0 && cur[0] <= dst[0]) || (y > 0 && cur[1] >= dst[1]) || (y < 0 && cur[1] <= dst[1])) return stop();
      const p = [cur[0], cur[1]];
      if (cur[0] !== dst[0]) p[0] += x;
      if (cur[1] !== dst[1]) p[1] += y;
      focus(p[0], p[1]);
    }, 30);
  };
  useEffect(() => () => clearInterval(scrolling.current), []);

  /* ---- arrow keys move the map by 15px (TWMap.onKeyDown) ---- */
  useEffect(() => {
    const keys = new Set();
    const typing = (t) => t && t.closest && t.closest("input, textarea, select, [contenteditable]");
    const down = (e) => {
      if (typing(e.target)) return;
      keys.add(e.keyCode);
      const d = [0, 0];
      if (keys.has(37)) d[0] -= 15;
      if (keys.has(38)) d[1] -= 15;
      if (keys.has(39)) d[0] += 15;
      if (keys.has(40)) d[1] += 15;
      if (!d[0] && !d[1]) return;
      e.preventDefault();
      applyPos([posRef.current[0] + d[0], posRef.current[1] + d[1]]);
    };
    const up = (e) => keys.delete(e.keyCode);
    document.addEventListener("keydown", down);
    document.addEventListener("keyup", up);
    return () => {
      document.removeEventListener("keydown", down);
      document.removeEventListener("keyup", up);
    };
  }, [applyPos]);

  /* ---- fullscreen (TWMap.goFullscreen): map fills the screen, minimap + popup move into #map_wrap ---- */
  const goFullscreen = () => {
    const el = wrapRef.current;
    const req = el && (el.requestFullscreen || el.webkitRequestFullscreen || el.mozRequestFullScreen);
    if (req) req.call(el);
  };
  useEffect(() => {
    const wrap = wrapRef.current;
    const change = () => {
      const on = !!wrap && document.fullscreenElement === wrap;
      if (on) {
        prevSize.current = sizeRef.current;
        setFullscreen(true);
        resizeMap([window.innerWidth, Math.max(window.outerHeight, window.innerHeight)]);
      } else if (prevSize.current) {
        const prev = prevSize.current;
        prevSize.current = null;
        setFullscreen(false);
        resizeMap(toPx(sizeTiles(prev)));
      }
    };
    document.addEventListener("fullscreenchange", change);
    return () => {
      document.removeEventListener("fullscreenchange", change);
      if (document.fullscreenElement === wrap) document.exitFullscreen?.();
    };
  }, [resizeMap]);

  /* ---- mouse: drag, hover popup, click -> context menu ---- */
  const mover = useMover({
    speed: 2,
    getPos: () => posRef.current,
    setPos: (x, y) => applyPos([x, y]),
    sizePx,
    scale: TILE,
    bound: BOUND,
    onBegin: () => {
      dragging.current = true;
      hidePopup();
      setOverVillage(false);
    },
    onEnd: () => {
      dragging.current = false;
    },
  });

  const tileAt = (e) => {
    const r = rootRef.current.getBoundingClientRect();
    return [Math.floor((e.clientX - r.left + pos[0]) / TILE[0]), Math.floor((e.clientY - r.top + pos[1]) / TILE[1])];
  };

  const onMapMouseMove = (e) => {
    if (dragging.current) return;
    const [x, y] = tileAt(e);
    const v = vmap.get(x * 1000 + y);
    const away = !ctx || Math.abs(x - ctx.x) >= 2 || Math.abs(y - ctx.y) >= 2;
    if (v && inViewport(viewport(pos, sizePx), x, y) && away) {
      setCtx(null);
      setOverVillage(true);
      if (popupEnabled) setPopup({ village: v, px: e.pageX, py: e.pageY, visible: true });
    } else {
      setOverVillage(false);
      hidePopup();
    }
  };

  const onMapClick = (e) => {
    e.preventDefault();
    if (mover.isDirty()) return;
    const [x, y] = tileAt(e);
    const v = vmap.get(x * 1000 + y);
    if (!v) return setCtx(null);
    if (!ctxEnabled) return nav(null, `info_village:${v.id}`);
    if (ctx && ctx.x === x && ctx.y === y) return nav(null, `info_village:${v.id}`); // second click: open the village info
    hidePopup();
    setCtx({ village: v, x, y });
  };

  /** TWMap.popup.calcPos: put #map_popup next to the pointer, inside the window. */
  useLayoutEffect(() => {
    const el = popupRef.current;
    if (!el || !popup || !popup.visible) return;
    const w = el.offsetWidth;
    const h = el.offsetHeight;
    const sl = window.scrollX;
    const st = window.scrollY;
    const c = [sl + 3, st + 3 + (document.getElementById("topContainer")?.offsetHeight ?? 0) + 3, sl + window.innerWidth - 3, st + window.innerHeight - 3 - ((document.getElementById("footer")?.offsetHeight ?? 0) - 3)];
    let y;
    if (popup.py + 15 + h < c[3]) y = popup.py + 15;
    else if (popup.py - 15 - c[1] >= h) y = popup.py - h - 15;
    else y = popup.py + 15;
    let x = popup.px + 15;
    x -= Math.max(0, x + w - c[2]);
    x = Math.max(x, sl);
    const op = el.offsetParent;
    const base = op && op !== document.body && op !== document.documentElement ? op.getBoundingClientRect() : { left: -sl, top: -st };
    el.style.left = x - (base.left + sl) + "px";
    el.style.top = y - (base.top + st) + "px";
  }, [popup]);

  /* ---- context menu ---- */
  const toggleFavorite = (v) => {
    const next = new Set(favorites);
    const had = next.has(v.id);
    if (had) next.delete(v.id);
    else next.add(v.id);
    setFavorites(next);
    saveSetting("favorites", [...next]);
    setCtx(null);
    setNotice(had ? "Village removed from favorites." : "Village added to favorites.");
  };
  // the farm templates as saved in the Farm Assistant (read when the menu opens, so edits there apply at once)
  const farm = useMemo(() => {
    if (!ctx) return null;
    const templates = loadTemplates(village.worldId);
    return { templates, a: Object.keys(templates.A).length > 0, b: Object.keys(templates.B).length > 0, npc: loadFarmSettings(village.worldId).npc };
  }, [ctx, village.worldId]);
  const sendFarm = async (target, name) => {
    const template = loadTemplates(village.worldId)[name];
    setCtx(null);
    const problem = templateProblem(template, unitsAtHome(village));
    if (problem) return flash(`Template ${name}: ${problem.toLowerCase()}.`, true);
    if (!onAttack) return;
    if (await onAttack(target.id, toBackendUnits(template))) {
      pushRecent(village.worldId, target.id, true);
      flash(`Troops sent (template ${name}).`);
    }
  };
  // the farm buttons' hover text: what the template is and how long it would march
  const farmTitle = (id) => {
    const name = id === "mp_farm_a" ? "A" : "B";
    const template = farm?.templates[name];
    if (!template || !ctx) return CTX_TITLES[id];
    const units = Object.entries(template).map(([u, n]) => `${n}x ${UNIT_BY_ID[u].name}`).join(", ");
    return `Template ${name}: ${units} - arrives in ${fmtDuration(travelSeconds(village, ctx.village, Object.keys(template), village.worldSpeed) * 1000)}`;
  };
  const ctxClick = (id, v) => (e) => {
    e.preventDefault();
    e.stopPropagation();
    switch (id) {
      case "mp_farm_a":
        return sendFarm(v, "A");
      case "mp_farm_b":
        return sendFarm(v, "B");
      case "mp_fav":
      case "mp_unfav":
        return toggleFavorite(v);
      case "mp_att":
        return nav(e, `place:${v.id}`);
      case "mp_res":
        return nav(e, "market");
      case "mp_info":
        return nav(e, `info_village:${v.id}`);
      case "mp_profile":
        return nav(e, v.ownerName ? "info_player:p_" + v.ownerName : "info_player");
      case "mp_msg":
        return nav(e, v.ownerName ? "mail:new_" + v.ownerName : "mail");
      case "mp_recruit":
        return nav(e, "train");
      case "mp_overview":
        return nav(e, "overview");
      default:
    }
  };
  const ctxButtonStyle = useMemo(() => {
    const out = {};
    if (!ctx) return out;
    const cx = ctx.x * TILE[0] - pos[0] + TILE[0] / 2;
    const cy = ctx.y * TILE[1] - pos[1] + TILE[1] / 2;
    for (const b of contextButtons(ctx.village, village.id, favorites, farm)) {
      out[b.id] = { opacity: 1, display: "block", left: cx + b.dx, top: cy + b.dy };
    }
    return out;
  }, [ctx, pos, favorites, village.id, farm]);

  /* ---- derived render data ---- */
  const center = getCenter(pos, sizePx);
  const vp = viewport(pos, sizePx);
  const home = !inViewport(vp, village.x, village.y) ? homeIndicator(village, center, sizePx) : null;

  const sectors = [];
  const s0 = [Math.floor(pos[0] / TILE[0] / SUB), Math.floor(pos[1] / TILE[1] / SUB)];
  const s1 = [Math.floor((pos[0] + sizePx[0]) / TILE[0] / SUB), Math.floor((pos[1] + sizePx[1]) / TILE[1] / SUB)];
  for (let sx = s0[0]; sx <= s1[0]; sx++) {
    for (let sy = s0[1]; sy <= s1[1]; sy++) sectors.push(<MapSector key={`${sx}_${sy}`} sx={sx} sy={sy} vmap={vmap} />);
  }

  const axisY = [];
  for (let c = Math.max(0, center[1] - AXIS_RANGE); c < Math.min(1000, center[1] + AXIS_RANGE); c++) {
    axisY.push(
      <div key={c} style={{ height: TILE[1], lineHeight: TILE[1] + "px", verticalAlign: "middle", position: "absolute", top: c * TILE[1] - BIAS, left: 0 }}>
        {c}
      </div>
    );
  }
  const axisX = [];
  for (let c = Math.max(0, center[0] - AXIS_RANGE); c < Math.min(1000, center[0] + AXIS_RANGE); c++) {
    axisX.push(
      <div key={c} style={{ width: TILE[0], textAlign: "center", position: "absolute", left: c * TILE[0] - BIAS, top: 0 }}>
        {c}
      </div>
    );
  }

  const miniMap = (
    <MiniMap
      mapPos={pos}
      sizePx={sizePx}
      miniPx={miniPx}
      vmap={vmap}
      vsig={vsig}
      playerId={1}
      villageId={village.id}
      applyMapPos={applyPos}
      focus={focus}
      onResize={setMiniPx}
      onResizeEnd={() => saveSetting("minisize", [Math.floor(miniPx[0] / MINI), Math.floor(miniPx[1] / MINI)])}
    />
  );
  const popupEl = (
    <div
      ref={popupRef}
      id="map_popup"
      className="nowrap"
      style={{ position: "absolute", top: 0, left: 0, minWidth: 150, display: popup && popup.visible ? "block" : "none", zIndex: 19, direction: "ltr" }}
    >
      {popup && <MapPopupContent village={popup.village} />}
    </div>
  );
  const navCell = (dx, dy, img, style) => (
    <td align="center" style={style} className="map_navigation" onClick={() => scrollBlock(dx, dy)}>
      <img style={{ zIndex: 1, position: "relative" }} alt={`map/${img}.png`} src={`graphic/map/${img}.png`} />
    </td>
  );
  const arrow = (dx, dy, img, alt) => (
    <img
      src={`graphic/map/${img}.png`}
      style={{ zIndex: 1, position: "relative" }}
      onClick={() => scrollBlock(dx, dy)}
      className="dir_arrow"
      alt={alt}
    />
  );

  return (
    <>
      <h2>
        Kontinent <span id="continent_id">{continentByXY(center[0], center[1])}</span>
      </h2>
      <table cellSpacing="0" cellPadding="0">
        <tbody>
          <tr>
            <td valign="top" className="map_big visible" id="map_big">
              <WorldMap
                open={worldOpen}
                infos={infos}
                onClose={() => setWorldOpen(false)}
                onPick={(x, y) => {
                  setWorldOpen(false);
                  applyPos(centerPos(x, y, sizeRef.current, false));
                }}
              />
              <div id="map_whole" className="containerBorder narrow">
                <table cellSpacing="0" cellPadding="0" className="map_container">
                  <tbody>
                    <tr>
                      <td></td>
                      {navCell(0, -1, "map_n", { paddingLeft: 26 })}
                      <td></td>
                    </tr>
                    <tr>
                      {navCell(-1, 0, "map_w", { paddingBottom: 22 })}
                      <td style={{ padding: 0 }}>
                        <div style={{ position: "relative" }} id="map_wrap" ref={wrapRef}>
                          <div id="map_coord_y_wrap" style={{ height: sizePx[1] - AXIS_X_H }}>
                            <div id="map_coord_y" style={{ position: "absolute", left: 0, top: -(pos[1] - BIAS), height: 38000, overflow: "visible" }}>
                              {axisY}
                            </div>
                          </div>
                          <div id="map_coord_x_wrap" style={{ width: sizePx[0] }}>
                            <div id="map_coord_x" style={{ position: "absolute", left: -(pos[0] - BIAS), top: 0, width: 53000, overflow: "visible" }}>
                              {axisX}
                            </div>
                          </div>
                          <img alt="" onClick={goFullscreen} id="fullscreen" src="graphic/fullscreen.png" style={{ display: fullscreen ? "none" : "inline" }} />
                          {CTX_IDS.map((id) => (
                            <a
                              key={id}
                              href="#"
                              title={id.startsWith("mp_farm") ? farmTitle(id) : CTX_TITLES[id]}
                              id={id}
                              className="mp"
                              style={ctxButtonStyle[id] ?? HIDDEN_ICON}
                              onClick={ctx && ctxButtonStyle[id] ? ctxClick(id, ctx.village) : (e) => e.preventDefault()}
                            ></a>
                          ))}
                          <a
                            ref={rootRef}
                            style={{
                              width: sizePx[0],
                              height: sizePx[1],
                              overflow: "hidden",
                              position: "relative",
                              backgroundImage: "url('graphic/map/gras1.png')",
                            }}
                            href="#"
                            id="map"
                            className="ui-resizable"
                            onClick={onMapClick}
                            onMouseMove={onMapMouseMove}
                            onMouseLeave={hidePopup}
                          >
                            <div
                              style={{ position: "absolute", top: 0, left: 0, width: "100%", height: "100%", backgroundColor: "black", zIndex: 20, opacity: 0, display: "none" }}
                              id="map_blend"
                            ></div>
                            <div style={{ position: "absolute", left: -(pos[0] - BIAS), top: -(pos[1] - BIAS), zIndex: 1, overflow: "visible" }} id="map_container">
                              {sectors}
                            </div>
                            <div
                              style={{
                                position: "absolute",
                                left: 0,
                                top: 0,
                                width: "100%",
                                height: "100%",
                                zIndex: 12,
                                backgroundImage: "url('graphic/map/empty.png')",
                                cursor: overVillage ? "pointer" : "move",
                                MozUserSelect: "none",
                              }}
                              id="map_mover"
                              onMouseDown={mover.onMouseDown}
                            ></div>
                            <div
                              style={{ height: "100%", width: "100%", zIndex: 10, backgroundImage: "url('graphic/map/empty.png')", position: "absolute", left: 0, top: 0, display: "none" }}
                              id="warplanner_selection"
                            ></div>
                            <ResizeHandle
                              getSize={() => sizeRef.current}
                              grid={TILE}
                              min={[4 * TILE[0], 4 * TILE[1]]}
                              max={[30 * TILE[0], 30 * TILE[1]]}
                              onBegin={() => {
                                dragging.current = true;
                                resizeCenter.current = getCenter(posRef.current, sizeRef.current);
                                hidePopup();
                              }}
                              onResize={(px) => resizeMap(px, resizeCenter.current)}
                              onEnd={() => {
                                dragging.current = false;
                                saveSizes();
                              }}
                            />
                          </a>
                          <div id="map_go_home_boundary">
                            <div id="map_go_home" style={home ? { display: "block", left: home.left, top: home.top } : { display: "none" }}>
                              <div id="map_go_home_pointer" style={home ? { transform: `rotate(${home.pointer}rad)` } : undefined}></div>
                              <div id="map_go_home_circle" onClick={() => focusUser(village.x, village.y)}>
                                <div id="map_go_home_text">{home ? home.distance : "home"}</div>
                              </div>
                            </div>
                          </div>
                          {fullscreen && createPortal(miniMap, wrapRef.current)}
                          {fullscreen && createPortal(popupEl, wrapRef.current)}
                        </div>
                      </td>
                      {navCell(1, 0, "map_e", { paddingBottom: 22 })}
                    </tr>
                    <tr>
                      <td></td>
                      {navCell(0, 1, "map_s", { paddingLeft: 26 })}
                      <td></td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <br />
              <MapLegend />
            </td>
            <td valign="top" className="map_topo" id="map_topo">
              <div id="minimap_whole" className="containerBorder">
                <table cellSpacing="1" cellPadding="0" className="map_container minimap_container">
                  <tbody>
                    <tr>
                      <td align="center">{arrow(-1, -1, "map_nw", "North west")}</td>
                      <td align="center">{arrow(0, -1, "map_n", "North")}</td>
                      <td align="center">{arrow(1, -1, "map_ne", "North east")}</td>
                    </tr>
                    <tr>
                      <td align="center">{arrow(-1, 0, "map_w", "West")}</td>
                      <td id="minimap_cont">{!fullscreen && miniMap}</td>
                      <td align="center">{arrow(1, 0, "map_e", "East")}</td>
                    </tr>
                    <tr>
                      <td align="center">{arrow(-1, 1, "map_sw", "South west")}</td>
                      <td align="center">{arrow(0, 1, "map_s", "South")}</td>
                      <td align="center">{arrow(1, 1, "map_se", "South east")}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <MapConfig
                village={village}
                sizePx={sizePx}
                miniPx={miniPx}
                ctxEnabled={ctxEnabled}
                setCtxEnabled={setCtxEnabled}
                popupEnabled={popupEnabled}
                setPopupEnabled={setPopupEnabled}
                onToggleWorld={() => setWorldOpen(!worldOpen)}
                onFocusXY={focusUser}
                onResizeMap={(n) => {
                  resizeMap(toPx([n, n]));
                  saveSetting("size", [n, n]);
                }}
                onResizeMinimap={resizeMinimap}
              />
            </td>
          </tr>
        </tbody>
      </table>
      {/* Translations (read by the original's js) */}
      <input id="newbieProt" defaultValue="The target is still under beginner protection. You may not attack before %s." type="hidden" />
      <input id="barbarianVillage" defaultValue="Barbarian village" type="hidden" />
      <input id="pointFormat" defaultValue="%s (%s points)" type="hidden" />
      <input id="villageFormat" defaultValue="%name% (%x%|%y%) K%con%" type="hidden" />
      <input id="villageNotes" defaultValue="Notes" type="hidden" />
      <input id="villageFavoriteAdded" defaultValue="Village added to favorites." type="hidden" />
      <input id="villageFavoriteRemoved" defaultValue="Village removed from favorites." type="hidden" />
      <input id="changesSaved" defaultValue="Changes saved." type="hidden" />
      <input id="confirmCenterDelete" defaultValue="Really delete the entry '%name%'?" type="hidden" />
      <input id="troopsSent" defaultValue="Troops have been sent." type="hidden" />
      {!fullscreen && popupEl}
      {notice && <Notice text={notice} error={noticeError} onDone={() => setNotice(null)} />}
    </>
  );
}
