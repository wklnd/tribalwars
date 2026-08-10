/* templates ally/invite.php (menu name "Recruitment"): pending invitations and the invite form. */
import { useState } from "react";
import { api } from "../../api";
import { PlayerLink, eventDate } from "./common";

export function Invite({ state, run, go }) {
  const [name, setName] = useState("");
  return (
    <div id="ally_content">
      <table width="100%">
        <tbody>
          <tr>
            <td width="45%" valign="top">
              <table width="400" className="vis">
                <tbody>
                  <tr>
                    <th colSpan="3">Invitations</th>
                  </tr>
                  {state.invitations.map((i) => {
                    const [day, time] = eventDate(i.at);
                    return (
                      <tr key={i.id}>
                        <td><PlayerLink name={i.player} go={go} /></td>
                        <td>{day} {time}</td>
                        <td>
                          <a className="btn" href="#" onClick={(e) => { e.preventDefault(); run(() => api.tribeWithdraw(i.playerId)); }}>Withdraw</a>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>{" "}
              <br />
              <form method="post" onSubmit={(e) => { e.preventDefault(); run(() => api.tribeInvite(name)).then((ok) => ok && setName("")); }}>
                <table width="400" className="vis">
                  <tbody>
                    <tr>
                      <th colSpan="3">Invite</th>
                    </tr>
                    <tr>
                      <td>Name:</td>
                      <td><input type="text" value={name} name="name" className="input-text" onChange={(e) => setName(e.target.value)} /></td>
                      <td><input className="btn" type="submit" value="OK" /></td>
                    </tr>
                  </tbody>
                </table>
              </form>
            </td>
            <td width="55%" valign="top" />
          </tr>
        </tbody>
      </table>
    </div>
  );
}
