import { useState } from "react";

// create_account.php: "Do you want to join <world>?" (templates/twlan/controllers/createaccount/index.php,
// wrapped like the saved real page: table.content-border > td > table#content_value). A game.css page.
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
    <table className="content-border" style={{ margin: "auto", marginTop: 25, borderCollapse: "collapse", width: "80%" }}>
      <tbody>
        <tr>
          <td>
            <table id="content_value" className="inner-border main" cellSpacing="0">
              <tbody>
                <tr>
                  <td>
                    <h1>
                      <img src="/graphic/rabe_38x40.png" alt="" /> Join
                    </h1>
                    {shown ? (
                      <div id="error" className="error" style={{ lineHeight: "20px" }}>
                        {shown}
                      </div>
                    ) : null}
                    <b>Description:</b>
                    <table className="vis" style={{ border: "1px solid #000" }} width="400">
                      <tbody>
                        <tr>
                          <td>
                            <ul style={{ margin: 2 }}>
                              <li>{`Game speed: x${speed}`}</li>
                              {world.description ? <li>{world.description}</li> : null}
                            </ul>
                            <a className="small" href="stat.php?mode=settings" onClick={settings}>
                              {"» World settings of "}
                              <strong>{world.name}</strong>
                            </a>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                    <p>
                      Do you want to join <strong>{world.name}</strong>?
                    </p>
                    <form method="post" action="create_account.php?action=confirm" onSubmit={submit}>
                      <input type="submit" value="Join" disabled={busy} />
                    </form>
                    <p>
                      <a className="small" href="#" onClick={back}>
                        {"\u00ab Back"}
                      </a>
                    </p>
                  </td>
                </tr>
              </tbody>
            </table>
          </td>
        </tr>
      </tbody>
    </table>
  );
}
