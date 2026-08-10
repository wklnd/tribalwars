/* templates ally/header.php: the tribe's name and the mode menu (only the modes the viewer may use). */
export const ALLY_MODES = [
  ["overview", "Overview", () => true],
  ["profile", "Profile", () => true],
  ["members", "Members", () => true],
  ["contracts", "Diplomacy", (me) => me.permissions.includes("diplomacy")],
  ["invite", "Recruitment", (me) => me.permissions.includes("invite")],
  ["forum", "Tribal forum", () => true],
  ["wars", "Wars", () => true],
  ["properties", "Properties", (me) => me.permissions.includes("found") || me.permissions.includes("diplomacy")],
];

export const allyModes = (me) => ALLY_MODES.filter(([, , allowed]) => allowed(me));

export function AllyHeader({ tribe, me, mode, go }) {
  return (
    <>
      <h2>{tribe.name}</h2>
      <table className="vis modemenu">
        <tbody>
          <tr>
            {allyModes(me).map(([m, label]) => (
              <td key={m} width="100" className={mode === m ? "selected" : undefined}>
                <a href="#" onClick={(e) => go(e, "ally:" + m)}>{label}</a>
              </td>
            ))}
          </tr>
        </tbody>
      </table>
      <br />
    </>
  );
}
