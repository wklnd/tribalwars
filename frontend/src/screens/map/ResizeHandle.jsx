import { useEffect, useLayoutEffect, useRef, useState } from "react";

/* The jQuery UI "se" resize grip of #map / #minimap (FreeMap.createResizer): dragging it resizes the
   box in whole grid steps between min and max (all in px). */
export function ResizeHandle({ getSize, grid, min, max, onBegin, onResize, onEnd }) {
  const props = useRef({ getSize, grid, min, max, onBegin, onResize, onEnd });
  useLayoutEffect(() => {
    props.current = { getSize, grid, min, max, onBegin, onResize, onEnd };
  });

  const [h] = useState(() => {
    let drag = null;

    function move(e) {
      if (!drag) return;
      const { grid, min, max, onResize } = props.current;
      const w = drag.start[0] + Math.round((e.clientX - drag.mouse[0]) / grid[0]) * grid[0];
      const h = drag.start[1] + Math.round((e.clientY - drag.mouse[1]) / grid[1]) * grid[1];
      onResize([Math.min(max[0], Math.max(min[0], w)), Math.min(max[1], Math.max(min[1], h))]);
    }
    function stop() {
      window.removeEventListener("mousemove", move, true);
      window.removeEventListener("mouseup", up, true);
    }
    function up() {
      stop();
      if (!drag) return;
      drag = null;
      props.current.onEnd?.();
    }
    function down(e) {
      if (e.button !== 0) return;
      e.preventDefault();
      e.stopPropagation();
      drag = { start: [...props.current.getSize()], mouse: [e.clientX, e.clientY] };
      props.current.onBegin?.();
      window.addEventListener("mousemove", move, true);
      window.addEventListener("mouseup", up, true);
    }
    return { down, stop };
  });

  useEffect(() => h.stop, [h]);

  return (
    <div
      className="ui-resizable-handle ui-resizable-se ui-icon ui-icon-gripsmall-diagonal-se"
      style={{ zIndex: 13 }}
      onMouseDown={h.down}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
      }}
    />
  );
}
