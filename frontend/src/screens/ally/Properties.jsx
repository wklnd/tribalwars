/* templates ally/props.php: name/tag/homepage/chat channel, recruitment, description, disband and the coat-of-arms box. */
import { useRef, useState } from "react";
import { api } from "../../api";
import { BB, BBToolbar } from "../../lib/bbcode";

export function Properties({ state, run, go, setInfo }) {
  const { tribe, me } = state;
  const isFounder = me.permissions.includes("found");
  const [name, setName] = useState(tribe.name);
  const [tag, setTag] = useState(tribe.tag);
  const [homepage, setHomepage] = useState(tribe.homepage);
  const [irc, setIrc] = useState(tribe.irc);
  const [apply, setApply] = useState(tribe.allowApply);
  const [template, setTemplate] = useState(tribe.applyTemplate);
  const [desc, setDesc] = useState(tribe.description);
  const [editing, setEditing] = useState(false);
  const [preview, setPreview] = useState(null);
  const area = useRef(null);

  const saveDesc = (e) => {
    e.preventDefault();
    if (e.nativeEvent.submitter?.name === "preview") return setPreview(desc);
    setPreview(null);
    run(() => api.tribeDescription(desc)).then((ok) => ok && setEditing(false));
  };
  const disband = (e) => {
    e.preventDefault();
    if (window.confirm("Do you really want to disband your tribe?")) run(() => api.tribeDisband());
  };

  return (
    <div id="ally_content">
      <table cellSpacing="0">
        <tbody>
          <tr>
            <td valign="top">
              <form method="post" onSubmit={(e) => { e.preventDefault(); run(() => api.tribeProperties({ name, tag, homepage, irc })); }}>
                <table width="100%" className="vis">
                  <tbody>
                    <tr>
                      <th colSpan="2">Properties</th>
                    </tr>
                    <tr>
                      <td>Tribe name</td>
                      <td><input type="text" value={name} name="name" onChange={(e) => setName(e.target.value)} /></td>
                    </tr>
                    <tr>
                      <td width="140">Abbreviation(no more than 6 letters):</td>
                      <td><input type="text" value={tag} maxLength="6" name="tag" onChange={(e) => setTag(e.target.value)} /></td>
                    </tr>
                    <tr>
                      <td width="140">Homepage:</td>
                      <td><input type="text" value={homepage} size="50" maxLength="128" name="homepage" onChange={(e) => setHomepage(e.target.value)} /></td>
                    </tr>
                    <tr>
                      <td width="140">IRC-Channel:</td>
                      <td><input type="text" value={irc} size="50" maxLength="128" name="irc-channel" onChange={(e) => setIrc(e.target.value)} /></td>
                    </tr>
                    <tr>
                      <td colSpan="2"><input className="btn" type="submit" value="Save" /></td>
                    </tr>
                  </tbody>
                </table>
              </form>
              <form method="post" onSubmit={(e) => { e.preventDefault(); run(() => api.tribeRecruitment(apply, template)); }}>
                <table width="100%" className="vis">
                  <tbody>
                    <tr>
                      <th colSpan="2">Recruitment</th>
                    </tr>
                    <tr>
                      <td width="140">Application</td>
                      <td>
                        <input type="checkbox" checked={apply} id="applications_enabled" name="applications_enabled" onChange={(e) => setApply(e.target.checked)} />{" "}
                        <label htmlFor="applications_enabled">Players may apply</label>
                      </td>
                    </tr>
                    <tr>
                      <td width="140" valign="top">Template:</td>
                      <td><textarea name="application_template" cols="40" rows="5" value={template} onChange={(e) => setTemplate(e.target.value)} /></td>
                    </tr>
                    <tr>
                      <td colSpan="2"><input className="btn" type="submit" value="Save" /></td>
                    </tr>
                  </tbody>
                </table>
              </form>
              {isFounder && (
                <table width="100%" className="vis">
                  <tbody>
                    <tr>
                      <th>Disband tribe</th>
                    </tr>
                    <tr>
                      <td>
                        <a className="evt-confirm btn" href="#" onClick={disband}>Disband tribe</a>
                      </td>
                    </tr>
                  </tbody>
                </table>
              )}
            </td>
            <td width="360" valign="top">
              <form name="edit_profile" method="post" onSubmit={saveDesc}>
                <table width="100%" className="vis">
                  <tbody style={{ display: editing ? undefined : "none" }} id="tribe_announcement_edit">
                    <tr>
                      <th width="100%" colSpan="2">Description</th>
                    </tr>
                    <tr align="center" id="bb_row">
                      <td colSpan="2"><BBToolbar area={area} onChange={setDesc} /></td>
                    </tr>
                    <tr id="edit_row">
                      <td colSpan="2">
                        <textarea rows="15" style={{ width: "100%", height: 150 }} name="desc_text" id="desc_text" className="ie8scrollfix"
                          ref={area} value={desc} onChange={(e) => setDesc(e.target.value)} />
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
                      <th width="100%" colSpan="2">Description</th>
                    </tr>
                    <tr align="center" id="show_row">
                      <td><BB text={preview ?? tribe.description} go={go} /></td>
                    </tr>
                  </tbody>
                </table>
              </form>{" "}
              {!editing && (
                <a className="btn" href="#" id="tribe_announcement_edit_link" onClick={(e) => { e.preventDefault(); setDesc(tribe.description); setEditing(true); }}>edit</a>
              )}
              <br />
              <br />
              <form method="post" encType="multipart/form-data" onSubmit={(e) => { e.preventDefault(); setInfo("Coats of arms are not available in this game."); }}>
                <table className="vis">
                  <tbody>
                    <tr>
                      <th>Coat of arms:</th>
                    </tr>
                    <tr>
                      <td>
                        <input name="image" type="file" size="40" accept="image/*" maxLength="1048576" />
                        <br />
                        <span className="small">max. 300x200, max. 256kByte, (jpg, jpeg, png, gif)</span>
                        <br />
                        <input type="submit" className="btn" value="OK" />
                      </td>
                    </tr>
                  </tbody>
                </table>
              </form>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
