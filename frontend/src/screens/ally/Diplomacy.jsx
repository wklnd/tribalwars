/* templates ally/diplomaty.php ("Diplomacy"): the tribe's relations to other tribes (allies / NAP / enemies).
   Non-binding, like the original says: they only colour the map for the tribe's members. */
import { useState } from "react";
import { api } from "../../api";
import { TribeLink } from "./common";

const TYPES = [
  ["PARTNER", "Allies"],
  ["NAP", "Non-Aggression-Pact (NAP)"],
  ["ENEMY", "Enemies"],
];

export function Diplomacy({ state, run, go }) {
  const [tag, setTag] = useState("");
  const [kind, setKind] = useState("PARTNER");
  return (
    <div id="ally_content">
      <p>
        On this page your relations with other tribes are administered. The settings are <strong>non-binding within the game</strong>, but
        villages will be coloured accordingly on the map. The status is visible only to tribe members and may be changed by tribal diplomats only.
      </p>
      <table width="100%" className="vis" id="partners">
        <tbody>
          {TYPES.map(([type, label]) => (
            <FragmentRows key={type} label={label}>
              {state.relations.filter((r) => r.kind === type).map((r) => (
                <tr key={r.tribeId}>
                  <td><TribeLink id={r.tribeId} tag={r.tag} go={go} /></td>
                  <td>
                    <a className="btn" href="#" onClick={(e) => { e.preventDefault(); run(() => api.tribeEndRelation(r.tribeId)); }}>terminate</a>
                  </td>
                </tr>
              ))}
            </FragmentRows>
          ))}
        </tbody>
      </table>
      <br style={{ clear: "both" }} />
      <h3>Add relationship</h3>
      <form method="post" onSubmit={(e) => { e.preventDefault(); run(() => api.tribeAddRelation(tag, kind)).then((ok) => ok && setTag("")); }}>
        <label htmlFor="tag">Tribe tag:</label>{" "}
        <input type="text" maxLength="30" style={{ width: 60 }} name="tag" value={tag} onChange={(e) => setTag(e.target.value)} />{" "}
        <select name="type" value={kind} onChange={(e) => setKind(e.target.value)}>
          {TYPES.map(([type, label]) => <option key={type} value={type}>{label}</option>)}
        </select>{" "}
        <button className="btn">OK</button>
      </form>
    </div>
  );
}

// one heading row, the tribes of that kind, and a spacer row
function FragmentRows({ label, children }) {
  return (
    <>
      <tr>
        <th colSpan="2">{label}</th>
      </tr>
      {children}
      <tr>
        <td style={{ height: 12, background: "none" }} colSpan="2" />
      </tr>
    </>
  );
}
