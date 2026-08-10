/* templates ally/members_rights.php: privileges and title of one member (reached with "Rights and Titles"). */
import { useState } from "react";
import { api } from "../../api";
import { RIGHT_ROLES } from "./common";

const LABEL = { found: "Founder", lead: "Leader", invite: "Invite", diplomacy: "Cancel", mass_mail: "Mass Mail",
  forum_mod: "Moderator of internal forum", internal_forum: "Internal Forum", trusted_member: "Trusted Member" };
// the original left these three descriptions as untranslated "Lang todos" placeholders; short real texts here
const DESC = {
  invite: "May invite players into the tribe and withdraw invitations.",
  diplomacy: "May set the relations to other tribes and change the tribe description.",
  mass_mail: "May write circular mails to the tribe (Mail > Circular mail).",
  forum_mod: "May moderate the internal forum: edit and delete posts, close and move threads.",
  internal_forum: "May read and write in the internal forum.",
  trusted_member: "Sees the hidden forums of the tribe.",
};

export function Rights({ state, memberId, run, back }) {
  const member = state.members.find((m) => m.id === memberId);
  const [roles, setRoles] = useState(() => new Set(member?.roles ?? []));
  const [title, setTitle] = useState(member?.title ?? "");
  const [outside, setOutside] = useState(!!member?.titleOutside);
  if (!member) return null;

  const founder = roles.has("found");
  const leader = roles.has("lead") || founder;
  const toggle = (role) => setRoles((prev) => {
    const set = new Set(prev);
    if (set.has(role)) set.delete(role);
    else set.add(role);
    if (role === "found" && !set.has("found")) set.delete("lead");
    return set;
  });
  const submit = async (e) => {
    e.preventDefault();
    if (await run(() => api.tribeRights(member.id, [...roles], title, outside))) back();
  };
  const box = (role, disabled) => (
    <label>
      <h5>
        <input type="checkbox" checked={disabled || roles.has(role)} disabled={disabled} id={`player[${role}]`} name={`player[${role}]`}
          onChange={() => toggle(role)} />{" "}
        <span title={LABEL[role]} className={"icon ally " + (role === "found" ? "founder" : role)} /> {LABEL[role]}
      </h5>
    </label>
  );
  return (
    <div id="ally_content">
      <h3>Change privileges on Player {member.name}</h3>
      <p>Here you can set the privileges for the players in your tribe. The duke privileges should only be given to players you know VERY well and that you can fully trust.</p>
      <form method="post" onSubmit={submit}>
        {box("found", false)}
        <p>Sets status to duke. A duke possesses all tribal privileges, can disband or rename the tribe, set the homepage and chat-channel, administer the tribal forum and name other members dukes.</p>
        {box("lead", founder)}
        <p>Barons can set privileges and titles of other members and disband members. They can receive other privileges as well that can be given by the dukes.</p>
        {RIGHT_ROLES.map((role) => (
          <div key={role}>
            {box(role, leader)}
            <p>{DESC[role]}</p>
          </div>
        ))}
        <h3>title</h3>
        <p>
          Tribal status: <input type="text" value={title} maxLength="24" name="player[title]" onChange={(e) => setTitle(e.target.value)} />
        </p>
        <label>
          <h5>
            <input type="checkbox" checked={outside} id="player[external_title]" name="player[external_title]" onChange={(e) => setOutside(e.target.checked)} />
            Tribal status visible to outsiders
          </h5>
        </label>
        <p>
          <input className="btn" type="submit" value="OK" />
        </p>
      </form>
    </div>
  );
}
