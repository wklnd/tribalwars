// screen=settings — only "Profile" exists (the text, location and birthday shown on your profile page);
// "Block player" is the list of blocked mail senders; the other settings pages of the original need features this game does not have (quick bar, premium, ...).
import { useEffect, useState } from "react";
import { api } from "../api";
import { MissingScreen, ModeMenu } from "../lib/ui";
import { BlockedSenders } from "./MailScreen";

const MODES = [
  ["profile", "Profile"],
  ["settings", "Settings"],
  ["change_passwd", "Change password"],
  ["delete", "Delete account"],
  ["block", "Block player"],
];

function ProfileForm({ go }) {
  const [form, setForm] = useState(null);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.player("me").then((p) => setForm({ description: p.description, location: p.location, birthday: p.birthday })).catch((e) => setError(e.message));
  }, []);

  if (!form) return error ? <div className="error_box">{error}</div> : <p>Loading…</p>;
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const save = async (e) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    try {
      await api.editProfile(form);
      setMessage("Your profile was saved.");
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <form onSubmit={save}>
      {error && <div className="error_box">{error}</div>}
      {message && <div className="success_box" style={{ padding: 4 }}>{message}</div>}
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th colSpan="2">Profile</th>
          </tr>
          <tr>
            <td width="130">Birthday:</td>
            <td><input type="date" value={form.birthday} onChange={set("birthday")} /></td>
          </tr>
          <tr>
            <td>Location:</td>
            <td><input type="text" maxLength="60" size="30" value={form.location} onChange={set("location")} /></td>
          </tr>
          <tr>
            <td valign="top">Profile text:</td>
            <td><textarea rows="12" cols="60" maxLength="4000" value={form.description} onChange={set("description")} /></td>
          </tr>
          <tr>
            <td />
            <td>
              <input className="btn" type="submit" value="Save" />{" "}
              <a href="#" onClick={(e) => go(e, "info_player")}>» View profile</a>
            </td>
          </tr>
        </tbody>
      </table>
    </form>
  );
}

export function SettingsScreen({ mode, go }) {
  const current = MODES.some(([m]) => m === mode) ? mode : "profile";
  return (
    <>
      <h2>Settings</h2>
      <table width="100%">
        <tbody>
          <tr>
            <td valign="top" width="130px">
              <ModeMenu modes={MODES} current={current} go={go} screen="settings" />
            </td>
            <td valign="top">
              {current === "profile" ? <ProfileForm go={go} /> : current === "block" ? <BlockedSenders go={go} /> : <MissingScreen name={"settings"} />}
            </td>
          </tr>
        </tbody>
      </table>
    </>
  );
}
