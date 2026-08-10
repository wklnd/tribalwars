import { useEffect, useLayoutEffect, useRef, useState } from "react";

/* Port of FreeMapMover: dragging the #map_mover / #minimap_mover overlay pans the map.
   `api` is read on every event, so callers may pass fresh closures each render:
     getPos()            -> [x, y] current viewport position in the map's own pixels
     setPos(x, y)        -> apply a new position
     sizePx, scale       -> viewport size and px-per-tile of this map
     bound               -> scroll bound in tiles (optional)
     speed               -> multiplier (the main map moves 2x the mouse distance)
     onBegin/onEnd       -> drag started / finished
   Returns { onMouseDown, isDirty() } - isDirty() is true while/just after a drag so the click that
   ends a drag can be ignored. */
export function useMover(api) {
  const apiRef = useRef(api);
  useLayoutEffect(() => {
    apiRef.current = api;
  });

  const [h] = useState(() => {
    const s = { dirty: false, container: [0, 0], mouse: [0, 0], active: false };

    function onMove(e) {
      const a = apiRef.current;
      const diff = [e.clientX - s.mouse[0], e.clientY - s.mouse[1]];
      s.mouse = [e.clientX, e.clientY];
      const pos = [s.container[0] - diff[0] * a.speed, s.container[1] - diff[1] * a.speed];
      if (a.bound) {
        const lim = a.bound;
        if (pos[0] / a.scale[0] < lim[0] && diff[0] > 0) diff[0] = 0;
        if (pos[1] / a.scale[1] < lim[1] && diff[1] > 0) diff[1] = 0;
        if (Math.floor((pos[0] + a.sizePx[0]) / a.scale[0]) > lim[2] && diff[0] < 0) diff[0] = 0;
        if (Math.floor((pos[1] + a.sizePx[1]) / a.scale[1]) > lim[3] && diff[1] < 0) diff[1] = 0;
      }
      if ((diff[0] !== 0 || diff[1] !== 0) && !s.dirty) {
        a.onBegin?.();
        s.dirty = true;
      }
      s.container[0] -= diff[0] * a.speed;
      s.container[1] -= diff[1] * a.speed;
      a.setPos(s.container[0], s.container[1]);
    }

    function onUp(e) {
      stop();
      if (s.dirty) apiRef.current.onEnd?.();
      // the click that follows mouseup must still see the drag as "dirty"
      setTimeout(() => {
        s.dirty = false;
      }, 50);
      e.preventDefault();
    }

    function stop() {
      window.removeEventListener("mousemove", onMove, true);
      window.removeEventListener("mouseup", onUp, true);
      s.active = false;
    }

    function onMouseDown(e) {
      if (e.button !== 0 || s.active) return;
      s.active = true;
      s.container = [...apiRef.current.getPos()];
      s.mouse = [e.clientX, e.clientY];
      s.dirty = false;
      window.addEventListener("mousemove", onMove, true);
      window.addEventListener("mouseup", onUp, true);
      e.preventDefault();
    }

    return { onMouseDown, stop, isDirty: () => s.dirty };
  });

  useEffect(() => h.stop, [h]);
  return h;
}
