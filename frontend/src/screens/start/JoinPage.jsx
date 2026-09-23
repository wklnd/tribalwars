import { useState } from "react";

// create_account.php: "Do you want to join <world>?" - part of the same account flow as login/register/world
// select, so it shares the start2.css skin (see screens/start/chrome.js: enterJoinPage).
// The original's description is a list of lines from the world config; ours is derived from the world's speed.
export function JoinPage({ world, onJoin, onBack, error }) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const shown = err || error;
  const speed = world.speed > 0 ? world.speed : 1;

  const submit = async (e) => {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setErr(null);
    try {
      await onJoin(world);
    } catch (ex) {
      setErr((ex && ex.message) || String(ex));
    } finally {
      setBusy(false);
    }
  };
  const settings = (e) => e.preventDefault();
  const back = (e) => {
    e.preventDefault();
    if (!busy) onBack();
  };

  return (
    <div className="tw2-page">
      <div className="tw2-banner">
        <h1>
          tribalwars
          <span className="sub">- nilz clone -</span>
        </h1>
      </div>
      <div className="tw2-content">
        <div className="box-border red">
          <div className="inner tw2-join">
            <h2>Join</h2>
            {shown ? (
              <div id="error" className="tw2-error-message">
                {shown}
              </div>
            ) : null}
            <p>
              <b>Description:</b>
            </p>
            <div className="world-desc">
              <ul>
                <li>{`Game speed: x${speed}`}</li>
                {world.description ? <li>{world.description}</li> : null}
              </ul>
              <a className="small" href="stat.php?mode=settings" onClick={settings}>
                {"» World settings of "}
                <strong>{world.name}</strong>
              </a>
            </div>
            <p>
              Do you want to join <strong>{world.name}</strong>?
            </p>
            <form method="post" action="create_account.php?action=confirm" onSubmit={submit}>
              <button type="submit" className="tw2-btn-big" disabled={busy}>
                <span className="cap left" />
                <span className="mid">{busy ? "Working..." : "Join"}</span>
                <span className="cap right" />
              </button>
            </form>
            <p className="back-link">
              <a href="#" onClick={back}>
                {"« Back"}
              </a>
            </p>
          </div>
          <div className="top-left"></div>
          <div className="top-right"></div>
          <div className="middle-top"></div>
          <div className="middle-bottom"></div>
          <div className="middle-left"></div>
          <div className="middle-right"></div>
          <div className="bottom-left"></div>
          <div className="bottom-right"></div>
        </div>
      </div>
    </div>
  );
}
