/* screen=ally for a player WITHOUT a tribe (templates ally/index.php): the invitations table and the "Establish tribe" form. */
import { useState } from "react";
import { api } from "../../api";
import { TribeLink } from "./common";

export function NoTribe({ state, run, go, error, village }) {
  const [name, setName] = useState("");
  const [tag, setTag] = useState("");
  const submit = (e) => {
    e.preventDefault();
    run(() => api.foundTribe(name, tag));
  };
  return (
    <>
      <h2>Tribe</h2>
      <p>To join a tribe, you must have an invitation from that tribe.</p>
      <table width="100%">
        <tbody>
          <tr>
            <td width="45%" valign="top">
              <table width="100%" className="vis">
                <tbody>
                  <tr>
                    <th colSpan="3">Invitations</th>
                  </tr>
                  {state.myInvitations.map((i) => (
                    <tr key={i.id}>
                      <td><TribeLink id={i.tribeId} tag={i.tribeName} go={go} /></td>
                      <td align="center"><a href="#" onClick={(e) => { e.preventDefault(); run(() => api.tribeAccept(i.id)); }}>Accept</a></td>
                      <td align="center"><a href="#" onClick={(e) => { e.preventDefault(); run(() => api.tribeReject(i.id)); }}>Decline</a></td>
                    </tr>
                  ))}
                </tbody>
              </table>{" "}
              <br />
              <form method="post" action={`game.php?village=${village.id}&screen=ally&action=create`} onSubmit={submit}>
                <table width="100%" className="vis">
                  <tbody>
                    <tr>
                      {error && <font color="red">{error}</font>}
                      <th colSpan="2">Establish tribe</th>
                    </tr>
                    <tr>
                      <td>Tribe name:</td>
                      <td><input type="text" name="name" value={name} onChange={(e) => setName(e.target.value)} /></td>
                    </tr>
                    <tr>
                      <td>
                        Abbreviation:
                        <br />
                        (no more than 6 letters)
                      </td>
                      <td><input type="text" maxLength="6" name="tag" value={tag} onChange={(e) => setTag(e.target.value)} /></td>
                    </tr>
                    <tr>
                      <td colSpan="2">
                        <input type="submit" style={{ fontSize: "10pt" }} value="Establish tribe" />
                      </td>
                    </tr>
                  </tbody>
                </table>
              </form>
            </td>
            <td />
          </tr>
        </tbody>
      </table>
    </>
  );
}
