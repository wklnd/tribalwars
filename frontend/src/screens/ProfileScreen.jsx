// screen=info_player — a player's profile: standing, villages, kills, achievements and their own text.
//   #info_player          your own profile
//   #info_player:p_<name> another player's profile (linked from the ranking, the map, reports)
// Layout follows Tribal Wars' profile page (name header, key facts table, village list, description); the original TWLan
// bundle has no such page, so the tables use its .vis styling.
import { useEffect, useState } from "react";
import { api } from "../api";
import { continentOf } from "../lib/military";
import { AwardImage } from "./achievements/AchievementsScreen";

const fmt = (n) => Number(n).toLocaleString("de-DE");
const fmtDay = (iso) => (iso ? new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" }) : "");

export const profileTarget = (name) => "info_player:p_" + name;

export function ProfileScreen({ name, go }) {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    setProfile(null);
    setError(null);
    const load = () => api.player(name ?? "me").then((p) => alive && setProfile(p)).catch((e) => alive && setError(e.message));
    load();
    const id = setInterval(load, 10000);
    return () => { alive = false; clearInterval(id); };
  }, [name]);

  if (error) return <div className="error_box">{error}</div>;
  if (!profile) return <p>Loading…</p>;

  const p = profile;
  const paragraphs = (p.description || "").split(/\n/);
  return (
    <>
      <h2>{p.name}</h2>
      <table width="100%">
        <tbody>
          <tr>
            <td valign="top" width="320">
              <table className="vis" width="100%">
                <tbody>
                  <tr>
                    <th colSpan="2">Player</th>
                  </tr>
                  <tr><td width="110">Name:</td><td>{p.name}</td></tr>
                  <tr><td>Rank:</td><td>{p.rank} of {p.players}</td></tr>
                  <tr><td>Points:</td><td>{fmt(p.points)}</td></tr>
                  <tr>
                    <td>Tribe:</td>
                    <td>
                      {p.tribeId ? (
                        <>
                          <a href="#" onClick={(e) => go(e, "info_ally:" + p.tribeId)}>{p.tribeName}</a>
                          {p.tribeTitle ? ` (${p.tribeTitle})` : ""}
                        </>
                      ) : "—"}
                    </td>
                  </tr>
                  <tr><td>Villages:</td><td>{p.villages.length}</td></tr>
                  <tr><td>Opponents defeated:</td><td>{fmt(p.kills)}</td></tr>
                  <tr><td>Player since:</td><td>{fmtDay(p.memberSince)}</td></tr>
                  {p.birthday && <tr><td>Birthday:</td><td>{fmtDay(p.birthday)}</td></tr>}
                  {p.location && <tr><td>Location:</td><td>{p.location}</td></tr>}
                </tbody>
              </table>
              <br />
              <table className="vis" width="100%">
                <tbody>
                  <tr>
                    <th>Villages</th>
                    <th>Coordinates</th>
                    <th>Points</th>
                  </tr>
                  {p.villages.map((v) => (
                    <tr key={v.id}>
                      <td>{v.name}</td>
                      <td>{`${v.x}|${v.y} ${continentOf(v)}`}</td>
                      <td>{fmt(v.points)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p>
                <a href="#" onClick={(e) => go(e, p.own ? "info_player:stats_own" : "info_player:s_" + p.name)}>» Statistics</a>
                {!p.own && (
                  <>
                    <br />
                    <a href="#" onClick={(e) => go(e, "mail:new_" + p.name)}>» Send message</a>
                  </>
                )}
                {p.own && (
                  <>
                    <br />
                    <a href="#" onClick={(e) => go(e, "settings:profile")}>» Edit profile</a>
                  </>
                )}
              </p>
            </td>
            <td valign="top">
              <table className="vis" width="100%">
                <tbody>
                  <tr>
                    <th>Profile text</th>
                  </tr>
                  <tr>
                    <td>
                      {p.description ? paragraphs.map((line, i) => <div key={i}>{line || " "}</div>) : <span className="inactive">{p.own ? "You have not written anything about yourself yet." : "This player has not written anything yet."}</span>}
                    </td>
                  </tr>
                </tbody>
              </table>
              <br />
              <table className="vis" width="100%">
                <tbody>
                  <tr>
                    <th>Achievements ({p.achievements.reduce((n, a) => n + a.level, 0)} levels)</th>
                  </tr>
                  <tr>
                    <td>
                      {p.achievements.length === 0 && <span className="inactive">No achievements earned yet.</span>}
                      {p.achievements.map((a) => (
                        <span key={a.key} title={`${a.name} (level ${a.level}/${a.maxLevel})`} style={{ display: "inline-block", marginRight: 4, cursor: "pointer" }}
                          onClick={(e) => p.own && go(e, "info_player:awards")}>
                          <AwardImage icon={a.icon} level={a.level} />
                        </span>
                      ))}
                      <div style={{ clear: "both" }} />
                    </td>
                  </tr>
                  {p.own && (
                    <tr>
                      <td><a href="#" onClick={(e) => go(e, "info_player:awards")}>» All achievements</a></td>
                    </tr>
                  )}
                </tbody>
              </table>
            </td>
          </tr>
        </tbody>
      </table>
    </>
  );
}
