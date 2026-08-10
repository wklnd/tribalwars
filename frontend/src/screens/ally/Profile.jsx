/* templates ally/profile.php: the tribe's numbers and description. Used for ally:profile (own tribe) and for
   info_ally:<id> / info_ally:t_<tag> — the original has no working info_ally page (its links say "page couldn't be
   found"), so that page is this same profile plus the member list. */
import { useEffect, useState } from "react";
import { api } from "../../api";
import { BB } from "../../lib/bbcode";
import { PlayerLink, thousands } from "./common";

export function TribeProfile({ tribe, description, members, own, go, homepage, irc }) {
  const best40 = (members ?? []).slice().sort((a, b) => b.points - a.points).slice(0, 40).reduce((s, m) => s + m.points, 0);
  return (
    <div id="ally_content">
      <table>
        <tbody>
          <tr>
            <td valign="top">
              <table width="100%" className="vis">
                <tbody>
                  <tr>
                    <th colSpan="2">Properties</th>
                  </tr>
                  <tr>
                    <td width="100">Tribe name</td>
                    <td>{tribe.name}</td>
                  </tr>
                  <tr>
                    <td>Abbreviation</td>
                    <td>{tribe.tag}</td>
                  </tr>
                  <tr>
                    <td>Number of members</td>
                    <td>{tribe.members}</td>
                  </tr>
                  <tr>
                    <td>Points of the best 40 players</td>
                    <td>{members ? thousands(best40) : ""}</td>
                  </tr>
                  <tr>
                    <td>Total points:</td>
                    <td>{thousands(tribe.points)}</td>
                  </tr>
                  <tr>
                    <td>Average points:</td>
                    <td>{thousands(tribe.pointsPerPlayer)}</td>
                  </tr>
                  <tr>
                    <td>rank</td>
                    <td>{tribe.rank}</td>
                  </tr>
                  <tr>
                    <td>Opponents defeated:</td>
                    <td className="tooltip" id="kill_info">{thousands(tribe.kills)}</td>
                  </tr>
                  {homepage && (
                    <tr>
                      <td>Homepage:</td>
                      <td><a href={/^https?:\/\//i.test(homepage) ? homepage : undefined} target="_blank" rel="noreferrer">{homepage}</a></td>
                    </tr>
                  )}
                  {irc && (
                    <tr>
                      <td>IRC-Channel:</td>
                      <td>{irc}</td>
                    </tr>
                  )}
                  <tr>
                    <td align="center" colSpan="2">
                      <a href="#" onClick={(e) => (own ? go(e, "ally:members") : e.preventDefault())}>
                        {" "}
                        Members{" "}
                      </a>
                    </td>
                  </tr>
                  <tr>
                    <td align="center" className="no_bg" colSpan="2">
                      <br />
                      <hr />
                    </td>
                  </tr>
                </tbody>
              </table>
            </td>
            <td valign="top">
              <table width="300" className="vis">
                <tbody>
                  <tr>
                    <th>Description</th>
                  </tr>
                  <tr>
                    <td align="center"><BB text={description} go={go} /></td>
                  </tr>
                </tbody>
              </table>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}

/** screen=info_ally: any tribe of the world by id (`info_ally:5`) or tag (`info_ally:t_WLF`), with its member list. */
export function InfoAllyScreen({ id, go }) {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    setProfile(null);
    setError(null);
    const resolve = id && id.startsWith("t_")
      ? api.tribes().then((rows) => {
          const row = rows.find((r) => r.tag.toLowerCase() === id.slice(2).toLowerCase());
          if (!row) throw new Error("Tribe not found");
          return row.id;
        })
      : Promise.resolve(id);
    resolve.then((tid) => api.tribeProfile(tid)).then((p) => alive && setProfile(p)).catch((e) => alive && setError(e.message));
    return () => { alive = false; };
  }, [id]);

  if (error) return <div className="error_box">{error}</div>;
  if (!profile) return <p>Loading…</p>;
  return (
    <>
      <h2>{profile.tribe.name}</h2>
      <TribeProfile tribe={profile.tribe} description={profile.description} members={profile.members} own={profile.own} go={go}
        homepage={profile.homepage} irc={profile.irc} />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>name</th>
            <th>Points</th>
            <th>Villages</th>
          </tr>
          {profile.members.map((m) => (
            <tr key={m.id}>
              <td><PlayerLink name={m.name} go={go} />{m.title && m.titleOutside ? ` (${m.title})` : ""}</td>
              <td>{thousands(m.points)}</td>
              <td>{m.villages}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
