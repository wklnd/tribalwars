/* screen=info_village and screen=info_command. The original TWLan has neither ("The page ... couldn't be found"), so
   there is no markup to copy: both are plain `vis` tables built from the strings and classes the game already uses
   (village anchors like the rally point's target rows, quickedit-style command icons, unit icons).
     info_village:<villageId>   owner, tribe, points, distance and the actions you can take against it
     info_command:<movementId>  origin, target, departure/arrival, the troops and the loot of one running command */
import { useState } from "react";
import { PLAYER_NAME } from "../lib/data";
import { Timer, PreErrorBox } from "../lib/ui";
import { loadSetting, saveSetting } from "../lib/map";
import { bonusOf } from "../lib/bonus";
import { BonusIcon } from "../lib/BonusIcon";
import { TYPE_TO_ID, UNIT_BY_ID, commandInfo, continentOf, fmtOnTime, villageDisplayName } from "../lib/military";

const distance = (a, b) => Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
const fmtDistance = (d) => (Math.round(d * 100) / 100).toString().replace(".", ",") + " fields";

const Missing = ({ text }) => (
  <>
    <PreErrorBox text={text} />
    <h2>Information</h2>
  </>
);

export function InfoVillageScreen({ id, village, villages = [], go }) {
  const [, force] = useState(0);
  const v = villages.find((x) => x.id === Number(id)) ?? (Number(id) === village.id ? village : null);
  if (!v) return <Missing text="This village does not exist." />;

  const mine = (village.myVillages ?? []).some((x) => x.id === v.id) || v.id === village.id;
  const isPlayer = v.ownerType === "PLAYER";
  const owner = isPlayer ? v.ownerName ?? PLAYER_NAME : null;
  const points = v.id === village.id ? village.points ?? v.points : v.points;
  const favorites = loadSetting("favorites", []);
  const isFavorite = favorites.includes(v.id);
  const toggleFavorite = (e) => {
    e.preventDefault();
    saveSetting("favorites", isFavorite ? favorites.filter((x) => x !== v.id) : [...favorites, v.id]);
    force((n) => n + 1);
  };
  const link = (target) => (e) => go(e, target);

  return (
    <>
      <h2>{villageDisplayName(v)}</h2>
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <td width="120">Coordinates:</td>
            <td>{`${v.x}|${v.y}`}</td>
          </tr>
          <tr>
            <td>Continent:</td>
            <td>{continentOf(v)}</td>
          </tr>
          <tr>
            <td>Points:</td>
            <td>{points ?? "-"}</td>
          </tr>
          {bonusOf(v.bonus) && (
            <tr id="info_bonus_row">
              <td>Bonus:</td>
              <td><BonusIcon code={v.bonus} /> <strong>{bonusOf(v.bonus).text}</strong></td>
            </tr>
          )}
          <tr>
            <td>Owner:</td>
            <td>{owner ? <a href="#" onClick={link("info_player:p_" + owner)}>{owner}</a> : <span className="grey">{v.ownerType === "BARBARIAN" ? "Barbarian" : "-"}</span>}</td>
          </tr>
          {v.ownerTribeId != null && (
            <tr>
              <td>Tribe:</td>
              <td><a href="#" onClick={link("info_ally:" + v.ownerTribeId)}>{v.ownerTribeTag}</a></td>
            </tr>
          )}
          {v.id !== village.id && (
            <tr>
              <td>Distance:</td>
              <td>{fmtDistance(distance(village, v))}</td>
            </tr>
          )}
        </tbody>
      </table>
      <br />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>Actions</th>
          </tr>
          {v.id !== village.id && (
            <tr>
              <td><a href="#" onClick={link("place:" + v.id)}>» Send troops</a></td>
            </tr>
          )}
          {v.id !== village.id && (
            <tr>
              <td><a href="#" onClick={link("market:" + v.id)}>» Send resources</a></td>
            </tr>
          )}
          {mine && v.id !== village.id && (
            <tr>
              <td><a href="#" onClick={link("overview_villages:combined")}>» Combined overview</a></td>
            </tr>
          )}
          <tr>
            <td><a href="#" onClick={link(`map:${v.x},${v.y}`)}>» Center on map</a></td>
          </tr>
          {v.id !== village.id && (
            <tr>
              <td><a href="#" onClick={toggleFavorite}>{isFavorite ? "» Remove from favorites" : "» Add to favorites"}</a></td>
            </tr>
          )}
        </tbody>
      </table>
    </>
  );
}

export function InfoCommandScreen({ id, village, villages = [], go }) {
  const all = [...(village.outgoingMovements ?? []), ...(village.incomingMovements ?? [])];
  const m = all.find((x) => String(x.id) === String(id));
  if (!m) return <Missing text="This command no longer exists." />;

  const nameOf = (n) => n;
  const info = commandInfo(m, nameOf);
  const at = (vid, fallbackName) => villages.find((x) => x.id === vid) ?? (vid === village.id ? village : { id: vid, name: fallbackName, x: 0, y: 0 });
  const origin = at(m.originVillageId, m.otherVillageName);
  const target = at(m.targetVillageId, m.otherVillageName);
  const villageCell = (v) => (
    <>
      <a href="#" onClick={(e) => go(e, "info_village:" + v.id)}>{v.x || v.y ? villageDisplayName(v) : v.name}</a>
      {v.ownerType === "PLAYER" && (
        <span> — <a href="#" onClick={(e) => go(e, "info_player:p_" + (v.ownerName ?? PLAYER_NAME))}>{v.ownerName ?? PLAYER_NAME}</a></span>
      )}
    </>
  );
  const units = Object.entries(m.units ?? {}).filter(([, n]) => n > 0);
  const loot = (m.wood ?? 0) + (m.clay ?? 0) + (m.iron ?? 0);

  return (
    <>
      <h2>{info.label}</h2>
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <td width="120">Command:</td>
            <td><img src={`/graphic/command/${info.icon}.png`} alt="" /> {info.label}</td>
          </tr>
          <tr>
            <td>Origin:</td>
            <td>{villageCell(origin)}</td>
          </tr>
          <tr>
            <td>Target:</td>
            <td>{villageCell(target)}</td>
          </tr>
          {m.departedAt && (
            <tr>
              <td>Departure:</td>
              <td>{fmtOnTime(m.departedAt)}</td>
            </tr>
          )}
          <tr>
            <td>Arrival:</td>
            <td>{fmtOnTime(m.arrivesAt)}</td>
          </tr>
          <tr>
            <td>Arrival in:</td>
            <td><Timer target={m.arrivesAt} /></td>
          </tr>
          {loot > 0 && (
            <tr>
              <td>Loot:</td>
              <td>
                <span className="nowrap"><span className="icon header wood" /> {Math.round(m.wood)} </span>
                <span className="nowrap"><span className="icon header stone" /> {Math.round(m.clay)} </span>
                <span className="nowrap"><span className="icon header iron" /> {Math.round(m.iron)}</span>
              </td>
            </tr>
          )}
        </tbody>
      </table>
      {units.length > 0 && (
        <>
          <br />
          <table className="vis">
            <tbody>
              <tr>
                {units.map(([type]) => {
                  const u = UNIT_BY_ID[TYPE_TO_ID[type]];
                  return (
                    <th key={type} style={{ width: 35 }}>
                      <img src={`/graphic/unit/unit_${u.id}.png`} title={u.name} alt={u.name} />
                    </th>
                  );
                })}
              </tr>
              <tr>
                {units.map(([type, n]) => (
                  <td key={type} className="unit-item">{n}</td>
                ))}
              </tr>
            </tbody>
          </table>
        </>
      )}
      {m.type === "INCOMING_ATTACK" && <p className="grey">The size of an incoming attack is not known.</p>}
    </>
  );
}
