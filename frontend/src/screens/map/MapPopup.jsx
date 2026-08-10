import { continentByXY } from "../../lib/map";
import { bonusOf } from "../../lib/bonus";

/* Content of #map_popup: the "tpl_popup" template of the original (no extra info; the tribe row is the template's info_ally_row;
   a bonus village gets the picture cell and the bold text row of the template). */
export function MapPopupContent({ village }) {
  const bonus = bonusOf(village.bonus);
  return (
    <table id="info_content" className="vis" style={{ backgroundColor: "#e5d7b2", width: "auto" }}>
      <tbody>
        {bonus && (
          <tr id="info_bonus_image_row">
            <td id="info_bonus_image" rowSpan={14}>
              <img src={`/graphic/bonus/${bonus.image}.png`} />
            </td>
          </tr>
        )}
        <tr>
          <th colSpan={2}>
            {village.name} ({village.x}|{village.y}) K{continentByXY(village.x, village.y)}
          </th>
        </tr>
        {bonus && (
          <tr id="info_bonus_text_row">
            <td colSpan={2}>
              <strong id="info_bonus_text">{bonus.text}</strong>
            </td>
          </tr>
        )}
        <tr id="info_points_row">
          <td width="100px">Points:</td>
          <td id="info_points">{village.points}</td>
        </tr>
        {village.owner ? (
          <tr id="info_owner_row">
            <td>Owner:</td>
            <td>
              {village.ownerName} ({village.ownerPoints} points)
            </td>
          </tr>
        ) : (
          <tr id="info_left_row">
            <td colSpan={2}>abandoned</td>
          </tr>
        )}
        {village.tribeName && (
          <tr id="info_ally_row">
            <td>Tribe:</td>
            <td>
              {village.tribeName} ({village.tribePoints} points)
            </td>
          </tr>
        )}
      </tbody>
    </table>
  );
}
