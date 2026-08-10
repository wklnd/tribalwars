/* templates ally/allyindex.php: the event log (10 per page), leave button and the internal announcement. */
import { useRef, useState } from "react";
import { api } from "../../api";
import { BB, BBToolbar } from "../../lib/bbcode";
import { EventMessage, eventDate } from "./common";

export function Overview({ state, run, go, start, setStart, village }) {
  const { tribe, me, events, eventsTotal } = state;
  const sites = Math.ceil(eventsTotal / 10);
  const site = Math.floor(start / 10) + 1;
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(tribe.announcement);
  const [preview, setPreview] = useState(null);
  const area = useRef(null);
  const canEdit = me.permissions.includes("lead");

  const leave = (e) => {
    e.preventDefault();
    if (window.confirm("Are you sure you want to leave your tribe?")) run(() => api.tribeLeave());
  };
  const save = (e) => {
    e.preventDefault();
    const submitter = e.nativeEvent.submitter?.name;
    if (submitter === "preview") return setPreview(text);
    setPreview(null);
    run(() => api.tribeAnnouncement(text)).then(() => setEditing(false));
  };

  return (
    <div id="ally_content">
      <table width="100%">
        <tbody>
          <tr>
            <td width="*" valign="top">
              {sites > 1 && (
                <table width="100%" className="vis">
                  <tbody>
                    <tr>
                      <td align="center">
                        {Array.from({ length: sites }, (_, i) => i + 1).map((c) =>
                          site === c ? (
                            <strong key={c}>&gt;{c}&lt; </strong>
                          ) : (
                            <span key={c}>
                              <a href="#" onClick={(e) => { e.preventDefault(); setStart(c * 10 - 10); }}>[{c}]</a>{" "}
                            </span>
                          ),
                        )}
                      </td>
                    </tr>
                  </tbody>
                </table>
              )}
              <table width="100%" className="vis">
                <tbody>
                  <tr>
                    <th>Date</th>
                    <th>Event</th>
                  </tr>
                  {events.map((ev) => {
                    const [day, time] = eventDate(ev.at);
                    return (
                      <tr key={ev.id}>
                        <td width="80">{day}<br />{time}</td>
                        <td><EventMessage event={ev} go={go} /></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </td>
            <td width="370" valign="top">
              <table width="100%" className="vis">
                <tbody>
                  <tr>
                    <td>
                      <a className="evt-confirm btn" href={`game.php?village=${village.id}&screen=ally&action=exit&`} onClick={leave}>Leave tribe</a>
                    </td>
                  </tr>
                </tbody>
              </table>
              <form name="edit_profile" method="post" onSubmit={save}>
                <table width="100%" className="vis">
                  <tbody style={{ display: editing ? undefined : "none" }} id="tribe_announcement_edit">
                    <tr>
                      <th width="100%" colSpan="2">Internal Announcements</th>
                    </tr>
                    <tr align="center" id="bb_row">
                      <td colSpan="2"><BBToolbar area={area} onChange={setText} /></td>
                    </tr>
                    <tr id="edit_row">
                      <td colSpan="2">
                        <textarea rows="15" style={{ width: "100%", height: 150 }} name="message" id="message" className="ie8scrollfix"
                          ref={area} value={text} onChange={(e) => setText(e.target.value)} />
                      </td>
                    </tr>
                    <tr id="submit_row">
                      <td>
                        <input type="submit" value="Save" name="edit" /> <input type="submit" value="Preview" name="preview" />
                      </td>
                      <td align="right"><a target="_blank" rel="noreferrer" href="http://help.die-staemme.de/wiki/BB-Codes">BB-Codes</a></td>
                    </tr>
                  </tbody>
                  <tbody id="tribe_announcement_show" style={{ display: editing && preview === null ? "none" : undefined }}>
                    <tr>
                      <th width="100%" colSpan="2">Internal Announcements</th>
                    </tr>
                    <tr align="center" id="show_row">
                      <td colSpan="2"><BB text={preview ?? tribe.announcement} go={go} /></td>
                    </tr>
                  </tbody>
                </table>
              </form>
              {canEdit && !editing && (
                <>
                  <a className="btn" href="#" id="tribe_announcement_edit_link" onClick={(e) => { e.preventDefault(); setText(tribe.announcement); setEditing(true); }}>edit</a>
                  <br />
                </>
              )}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
