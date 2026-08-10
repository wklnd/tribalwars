import { memo, useRef } from "react";
import {
  HAS_CANVAS,
  MINI,
  MINI_SECTOR,
  TILE,
  drawTopoSector,
  minimapBound,
  minimapOffset,
  topoFallbackSrc,
} from "../../lib/map";
import { ResizeHandle } from "./ResizeHandle";
import { useMover } from "./useMover";

const SECTOR_PX = MINI_SECTOR * MINI;

/* One 250x250 topo image of the minimap (the original loads page.php?page=topo_image for it). */
const MiniSector = memo(function MiniSector({ sx, sy, vmap, vsig, playerId, villageId }) {
  const x = sx * MINI_SECTOR;
  const y = sy * MINI_SECTOR;
  const src = (HAS_CANVAS && drawTopoSector(x, y, vmap, vsig)) || topoFallbackSrc(playerId, villageId, x, y);
  return (
    <div style={{ width: SECTOR_PX, height: SECTOR_PX, position: "absolute", left: sx * SECTOR_PX, top: sy * SECTOR_PX }}>
      <img style={{ position: "absolute", zIndex: 1, left: 0, top: 0 }} src={src} />
    </div>
  );
});

/** #minimap: a FreeMap of 5px tiles that always mirrors the main map's position. */
export function MiniMap({ mapPos, sizePx, miniPx, vmap, vsig, playerId, villageId, applyMapPos, focus, onResize, onResizeEnd }) {
  const { offset, mapTiles } = minimapOffset(miniPx, sizePx);
  const px = (mapPos[0] / TILE[0] - offset[0]) * MINI;
  const py = (mapPos[1] / TILE[1] - offset[1]) * MINI;
  const rootRef = useRef(null);

  const mover = useMover({
    speed: 1,
    getPos: () => [px, py],
    setPos: (x, y) => applyMapPos([(x / MINI + offset[0]) * TILE[0], (y / MINI + offset[1]) * TILE[1]]),
    sizePx: miniPx,
    scale: [MINI, MINI],
    bound: minimapBound(miniPx, sizePx),
  });

  const sectors = [];
  const s0 = [Math.floor(px / SECTOR_PX), Math.floor(py / SECTOR_PX)];
  const s1 = [Math.floor((px + miniPx[0]) / SECTOR_PX), Math.floor((py + miniPx[1]) / SECTOR_PX)];
  for (let sx = s0[0]; sx <= s1[0]; sx++) {
    for (let sy = s0[1]; sy <= s1[1]; sy++) {
      if (sx < 0 || sy < 0 || sx * MINI_SECTOR > 999 || sy * MINI_SECTOR > 999) continue;
      sectors.push(<MiniSector key={`${sx}_${sy}`} sx={sx} sy={sy} vmap={vmap} vsig={vsig} playerId={playerId} villageId={villageId} />);
    }
  }

  const onClick = (e) => {
    e.preventDefault();
    if (mover.isDirty()) return;
    const r = rootRef.current.getBoundingClientRect();
    focus(Math.floor((e.clientX - r.left + px) / MINI), Math.floor((e.clientY - r.top + py) / MINI));
  };

  return (
    <div
      ref={rootRef}
      id="minimap"
      className="ui-resizable"
      style={{ overflow: "hidden", position: "relative", padding: 0, width: miniPx[0], height: miniPx[1] }}
      onClick={onClick}
    >
      <div
        id="minimap_viewport"
        style={{
          border: "1px solid white",
          position: "absolute",
          zIndex: 10,
          width: mapTiles[0] * MINI,
          height: mapTiles[1] * MINI,
          left: offset[0] * MINI,
          top: offset[1] * MINI,
        }}
      />
      <div id="minimap_container" style={{ position: "absolute", left: -px, top: -py, zIndex: 1, overflow: "visible" }}>
        {sectors}
      </div>
      <div
        id="minimap_mover"
        style={{
          position: "absolute",
          left: 0,
          top: 0,
          width: "100%",
          height: "100%",
          zIndex: 12,
          backgroundImage: "url('graphic/map/empty.png')",
          cursor: "move",
          MozUserSelect: "none",
        }}
        onMouseDown={mover.onMouseDown}
      />
      <ResizeHandle
        getSize={() => miniPx}
        grid={[5 * MINI, 5 * MINI]}
        min={[20 * MINI, 20 * MINI]}
        max={[120 * MINI, 120 * MINI]}
        onResize={onResize}
        onEnd={onResizeEnd}
      />
    </div>
  );
}
