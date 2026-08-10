/* In-game world switcher, styled with the original game.css world-selection classes
   (#world_selection_popup / #servers-list-block / .world_button_*). */
export function WorldSwitch({ open, worlds, currentId, onClose, onSelect }) {
  return (
    <>
      <div id="world_selection_clicktrap" style={{ display: open ? "block" : "none" }} onClick={onClose} />
      <div id="world_selection_popup" style={{ display: open ? "block" : "none" }}>
        <div className="servers-list-top" />
        <div id="servers-list-block">
          <div id="active_server">
            <span className="pseudo-heading">Active worlds</span>
          </div>
          {worlds.map((w) => (
            <a
              key={w.id}
              href="#"
              className={w.id === currentId ? "world_button_inactive" : "world_button_active"}
              title={w.speed > 1 ? `Speed x${w.speed}` : undefined}
              onClick={(e) => {
                e.preventDefault();
                if (w.id !== currentId) onSelect(w);
                else onClose();
              }}
            >
              {w.name}
            </a>
          ))}
          <div style={{ clear: "both" }} />
        </div>
        <div className="servers-list-bottom" />
      </div>
    </>
  );
}
