/* Tribal wars: every pair of tribes that has destroyed units of the other or set the other as enemy.
   Used by Ranking -> Wars (all wars of the world) and by the tribe's own "Wars" mode (own tribe first).
   The original never implemented either page ("Not implemented yet"), so there is no markup to copy: plain vis tables. */
import { useEffect, useState } from "react";
import { api } from "../../api";
import { TribeLink, thousands } from "./common";

const REL = { PARTNER: "Partner", NAP: "Non-aggression pact", ENEMY: "Enemy" };

export function Wars({ own = false, go }) {
  const [wars, setWars] = useState(null);
  useEffect(() => {
    const load = () => api.tribeWars(own).then(setWars).catch(() => setWars([]));
    load();
    const id = setInterval(load, 15000);
    return () => clearInterval(id);
  }, [own]);
  return (
    <>
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>{own ? "Your tribe" : "Tribe"}</th>
            <th>Opponent</th>
            <th>Opponents defeated</th>
            <th>Losses</th>
            <th>Relation</th>
          </tr>
          {(wars ?? []).map((w) => (
            <tr key={w.a.id + ":" + w.b.id}>
              <td><TribeLink id={w.a.id} tag={w.a.tag} go={go} /></td>
              <td><TribeLink id={w.b.id} tag={w.b.tag} go={go} /></td>
              <td>{thousands(w.killsA)}</td>
              <td>{thousands(w.killsB)}</td>
              <td className="nowrap">
                {w.relationAB ? REL[w.relationAB] : "-"}
                {w.relationBA ? <span className="grey"> / {REL[w.relationBA]}</span> : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {wars && wars.length === 0 && <p>There are no wars at the moment.</p>}
    </>
  );
}
