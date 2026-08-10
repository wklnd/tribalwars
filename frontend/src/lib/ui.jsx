import { createContext, useContext, useEffect, useLayoutEffect, useState } from "react";
import { fmtDuration } from "./data";

export function useNow() {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  return now;
}

export function Timer({ target }) {
  const now = useNow();
  return <span className="timer">{fmtDuration(new Date(target).getTime() - now)}</span>;
}

export function GreyNum({ n }) {
  const s = String(Math.floor(n));
  if (s.length <= 3) return <>{s}</>;
  return (
    <>
      {s.slice(0, -3)}
      <span className="grey">.</span>
      {s.slice(-3)}
    </>
  );
}

export function ShadowRow() {
  return (
    <tr className="newStyleOnly">
      <td className="shadow">
        <div className="leftshadow" />
        <div className="rightshadow" />
      </td>
    </tr>
  );
}

export function ModeMenu({ modes, current, go, screen, style }) {
  return (
    <table className="vis modemenu" style={style}>
      <tbody>
        {modes.map(([mode, label]) => (
          <tr key={mode}>
            <td className={mode === current ? "selected" : undefined} style={{ minWidth: 80 }}>
              <a href="#" onClick={(e) => go(e, screen + ":" + mode)}> {label} </a>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/* The real game prints its page-level error box ("... couldn't be found.", "TBI",
   "Not implemented yet") BEFORE <div id="content_point"></div>. A screen cannot put
   markup before a sibling that App renders, so App may provide PreContentContext
   (a state setter) and render the string as that box; without a provider the box is
   rendered inline (visually identical, only the DOM order differs). */
export const PreContentContext = createContext(null);

export function PreErrorBox({ text }) {
  const setPre = useContext(PreContentContext);
  useLayoutEffect(() => {
    if (!setPre) return undefined;
    setPre(text);
    return () => setPre(null);
  }, [setPre, text]);
  if (setPre) return null;
  return <div className="error_box"> {text} </div>;
}

/* The engine derives the controller class from the screen key: underscores removed, lower-cased, first letter up. */
export function controllerClassName(name) {
  const s = String(name ?? "").replace(/_/g, "").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function MissingScreen({ name }) {
  return <PreErrorBox text={`The page "TWLan\\Controllers\\Game\\${controllerClassName(name)}" couldn't be found.`} />;
}

export const CLASS_NAMES = {
  market: "Market", farm: "Farm", church: "Church", watchtower: "Watchtower",
  info_player: "Infoplayer", info_village: "Infovillage", info_command: "Infocommand", info_ally: "Infoally",
  mail: "Mail", settings: "Settings", inventory: "Inventory", buddies: "Buddies", stat: "Stat",
};
