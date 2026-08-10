import { useState } from "react";
import { fmtDuration } from "../../lib/data";
import { KNIGHT, showMessage } from "../../lib/buildings";
import { rawStyle, useNowTick } from "../../lib/military";
import { useUnitPopup } from "../military/UnitPopup";
import { TrainQueue, queueEvents } from "../TrainScreen";

/* game/statue/index.php ($mode "index"): the recruit queue, the recruit form while the player has no paladin, else the
   paladin's status + rename form. "inventory": the item map with no items found and 0% progress (no item system). */
export function StatueBody({ village, mode, onTrain, onCancelTrain, onRenamePaladin }) {
  const popup = useUnitPopup();
  const now = useNowTick();
  const [name, setName] = useState(null);
  const paladinName = village.paladinName ?? "Paladin";
  const speed = village.worldSpeed || 1;

  const train = (e) => {
    e.preventDefault();
    if (village.wood < KNIGHT.wood || village.clay < KNIGHT.stone || village.iron < KNIGHT.iron) {
      showMessage("Not enough resources available", "error");
    } else if (village.populationCapacity - village.populationUsed < KNIGHT.pop) {
      showMessage("Farm too small", "error");
    } else {
      onTrain?.("PALADIN", 1);
    }
    return false;
  };

  const rename = (e) => {
    e.preventDefault();
    const n = String(name ?? paladinName).trim();
    if (n.length < 3 || n.length > 50) showMessage("Name must be between 3 and 50 characters", "error");
    else onRenamePaladin?.(n);
  };

  const status = {
    HOME: `${paladinName} is stationed in ${village.paladinVillageName}.`,
    AWAY: `${paladinName} is away from ${village.paladinVillageName}.`,
    TRAINING: null,
  }[village.paladinState];
  const events = queueEvents(village, now).filter((e) => e.unit === "knight");

  if (mode === "inventory") {
    return (
      <>
        <div ref={rawStyle("width:840px;float:left;")}>
          <div ref={rawStyle("float:right;width:210px;padding-right:5px;")}>
            <p>Items are effective only for units joined by a Paladin equipped with that particular item.</p>
          </div>
          <div ref={rawStyle("float:left;position:relative;z-index:9996;width:605px;padding-left:2px;")}>
            <div ref={rawStyle("width:600px;height:430px;padding:0;margin-right:10px;z-index:9997")}>
              <img src="/graphic/map/empty.png" alt="" title="" className="inv_empty" useMap="#inv" />
              <map id="inv" name="inv"></map>
              <img src="/graphic/inventory/inventory.jpg" alt="" title="" />
            </div>
          </div>
        </div>
        <br style={{ clear: "both" }} />
        <table className="vis" ref={rawStyle("width: 605px; padding:0;margin:0;")}>
          <tbody>
            <tr>
              <th colSpan="3">Progress until finding the next item:</th>
            </tr>
            <tr>
              <td>
                <div className="progress-bar">
                  <span className="label">0%</span>
                  <div style={{ width: "0%" }}></div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        {popup.node}
      </>
    );
  }

  return (
    <>
      {events.length > 0 && <TrainQueue events={events} bid="statue" now={now} onCancel={onCancelTrain} />}
      <input type="hidden" id="knight_0" value="1" readOnly />
      {!village.paladinState ? (
        <form action={`game.php?village=${village.id}&screen=statue&action=train&`} method="post" onSubmit={train}>
          <table className="vis">
            <tbody>
              <tr>
                <th width="150">Unit</th>
                <th colSpan="4" width="120">
                  Requirements
                </th>
                <th width="130">Time (hh:mm:ss)</th>
                <th>Here/total</th>
                <th>Recruit</th>
              </tr>
              <tr>
                <td>
                  <a href="#" onClick={(e) => popup.open(e, "knight")}>
                    <img src="/graphic/unit/unit_knight.png" alt="" />
                    {paladinName}
                  </a>
                </td>
                {[
                  ["wood", "wood", "Wood", KNIGHT.wood],
                  ["stone", "stone", "Clay", KNIGHT.stone],
                  ["iron", "iron", "Iron", KNIGHT.iron],
                  ["face", "population", "Population", KNIGHT.pop],
                ].map(([img, key, title, value]) => (
                  <td key={key}>
                    <img src={`/graphic/${img}.png`} title={title} alt="" />
                    {value}
                  </td>
                ))}
                <td>{fmtDuration((KNIGHT.time / speed) * 1000)}</td>
                <td>0/0</td>
                <td>
                  <a href="#" onClick={train}>
                    Train a Paladin
                  </a>
                </td>
              </tr>
            </tbody>
          </table>
        </form>
      ) : (
        <>
          <h3 style={{ marginTop: 4 }}>{paladinName}</h3>
          {status && (
            <>
              <table className="vis">
                <tbody>
                  <tr>
                    <th>{status}</th>
                  </tr>
                </tbody>
              </table>
              <br />
            </>
          )}
          <form action="#" method="post" onSubmit={rename}>
            <table className="vis">
              <tbody>
                <tr>
                  <td>
                    Name: <input type="text" name="knights_name" value={name ?? paladinName} onChange={(e) => setName(e.target.value)} />{" "}
                    <input type="submit" value="rename" />
                  </td>
                </tr>
              </tbody>
            </table>
          </form>
        </>
      )}
      {popup.node}
    </>
  );
}
