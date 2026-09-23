// The logged-in start page's world dialog: "Welcome, <name>" header + "Current worlds" (worlds where the account
// already has a village) and "Available worlds" (the others). Markup = templates/twlan/controllers/index/
// container_post.php (#world-select.dialog.worlds > .world-select.entered / .world-select.suggested).
const stop = (e) => e.preventDefault();

// Speed worlds carry title="Speed x50" and show a small "x50" hint next to the name.
function speedLabel(w) {
  return w.speed > 1 ? "x" + w.speed : null;
}

function WorldLink({ world, onClick }) {
  const hint = speedLabel(world);
  return (
    <a href="#" title={hint ? "Speed " + hint : undefined} onClick={(e) => { e.preventDefault(); onClick(world); }}>
      {world.name}
      {hint && (
        <>
          {" "}
          <small className="world-speed" style={{ fontSize: "0.75em", opacity: 0.85 }}>{hint}</small>
        </>
      )}
    </a>
  );
}

function Section({ cls, title, items, onClick }) {
  return (
    <div className={"tw2-worldlist " + cls}>
      <h3>{title}</h3>
      <ul>
        {items.map((w) => (
          <li key={w.id}>
            <WorldLink world={w} onClick={onClick} />
          </li>
        ))}
      </ul>
    </div>
  );
}

export function WorldLists({ account, worlds, onPlay, onChoose, onLogout }) {
  const list = worlds || [];
  const current = list.filter((w) => w.joined);
  const available = list.filter((w) => !w.joined);
  return (
    <div id="world-select">
      <div className="tw2-welcome">
        <p>{`Welcome, ${account.username}`}</p>
        <p className="logout-link">
          <a href="#" id="world-select-logout" onClick={(e) => { stop(e); if (onLogout) onLogout(); }}>
            {`Not ${account.username}? Click here to logout.`}
          </a>
        </p>
      </div>
      {current.length > 0 && <Section cls="entered" title="Current worlds" items={current} onClick={onPlay} />}
      {available.length > 0 && <Section cls="suggested" title="Available worlds" items={available} onClick={onChoose} />}
    </div>
  );
}
