import { memo } from "react";
import { BIAS, BOUND, SUB, TILE, rgb, terrainSprite } from "../../lib/map";

/* One 5x5-tile "sector" of #map_container, as TWMap.mapHandler.spawnSector builds it:
   a root div, two 1px border lines (heavier on continent borders) and one absolutely
   positioned <img> per tile (plus command icons for villages with movements). */

const GFX = "graphic/map/";

function cmdIcons(v, left, top) {
  const sizeDiff = (Math.max(2, v.icons.length) - 2) * 2;
  const w = 14 - sizeDiff;
  return v.icons.map((img, i) => (
    <img
      key={`c${left}_${top}_${i}`}
      id={`map_cmdicons_${v.id}_${i}`}
      src={`${GFX}${img}.png`}
      style={{
        position: "absolute",
        right: 0,
        zIndex: 4,
        width: w,
        height: w,
        marginTop: 0,
        marginLeft: 34 + sizeDiff * 2 - i * (w + 5 - sizeDiff),
        left,
        top,
      }}
    />
  ));
}

export const MapSector = memo(function MapSector({ sx, sy, vmap }) {
  const x0 = sx * SUB;
  const y0 = sy * SUB;
  const tiles = [];
  for (let tx = 0; tx < SUB; tx++) {
    const x = x0 + tx;
    if (x < BOUND[0] || x > BOUND[2]) continue;
    for (let ty = 0; ty < SUB; ty++) {
      const y = y0 + ty;
      if (y < BOUND[1] || y > BOUND[3]) continue;
      const left = tx * TILE[0];
      const top = ty * TILE[1];
      const v = vmap.get(x * 1000 + y);
      if (v) {
        tiles.push(...cmdIcons(v, left, top));
        tiles.push(
          <img
            key={`t${tx}_${ty}`}
            id={`map_village_${v.id}`}
            src={GFX + v.sprite}
            style={{
              position: "absolute",
              zIndex: 2,
              ...(v.color ? { backgroundColor: rgb(v.color) } : null),
              left,
              top,
            }}
          />
        );
        if (v.bonus) {
          // a bonus village: its bonus icon (the same sprite as in the overviews) in the tile's bottom-left corner
          tiles.push(
            <span
              key={`b${tx}_${ty}`}
              className={`bonus_icon bonus_icon_${v.bonus}`}
              style={{ position: "absolute", zIndex: 4, marginLeft: 0, left: left + 3, top: top + TILE[1] - 20, pointerEvents: "none", backgroundColor: "rgba(244,228,188,0.85)", borderRadius: 3, boxShadow: "0 0 2px rgba(0,0,0,0.6)" }}
            />
          );
        }
      } else {
        tiles.push(
          <img key={`t${tx}_${ty}`} src={GFX + terrainSprite(x, y)} style={{ position: "absolute", zIndex: 2, left, top }} />
        );
      }
    }
  }
  return (
    <div
      style={{
        width: SUB * TILE[0],
        height: SUB * TILE[1],
        position: "absolute",
        left: x0 * TILE[0] - BIAS,
        top: y0 * TILE[1] - BIAS,
      }}
    >
      <div
        className={x0 % 100 === 0 ? "map_con_border" : "map_border"}
        style={{ zIndex: 3, position: "absolute", width: 1, height: SUB * TILE[1], left: 0, top: 0 }}
      />
      <div
        className={y0 % 100 === 0 ? "map_con_border" : "map_border"}
        style={{ zIndex: 3, position: "absolute", height: 1, width: SUB * TILE[0], left: 0, top: 0 }}
      />
      {tiles}
    </div>
  );
});
