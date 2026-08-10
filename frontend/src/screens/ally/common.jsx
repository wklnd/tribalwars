// Shared bits of the tribe (screen=ally) pages.
import { useState } from "react";

export const thousands = (n) => String(Math.floor(Number(n) || 0)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");

/** Player / tribe links go through the app's router (game.php?screen=info_player&id=… in the original). */
export const PlayerLink = ({ name, go }) => (
  <a href="#" onClick={(e) => go(e, "info_player:p_" + name)}>{name}</a>
);
export const TribeLink = ({ id, tag, go, children }) => (
  <a href="#" onClick={(e) => go(e, "info_ally:" + id)}>{children ?? tag}</a>
);

// event texts of the original (game.ally.events.N); {fromPlayer}/{toPlayer}/{toTribe} become links
const EVENT_TEXT = {
  1: "The tribe was founded by {fromPlayer}.",
  2: "{fromPlayer} has changed the internal announcement.",
  3: "The tribe is now allied with {toTribe}.",
  4: "The tribe now has a NAP with {toTribe}.",
  5: "The tribe is now enemies with {toTribe}.",
  6: "{toPlayer} has been invited by {fromPlayer}.",
  7: "{fromPlayer} changed the tribal attributes.",
  8: "The invitation to {toPlayer} has been withdrawn by {fromPlayer}.",
  9: "{fromPlayer} changed the privileges and title of {toPlayer}.",
  10: "{fromPlayer} left the tribe.",
  11: "{toPlayer} has been dismissed by {fromPlayer}.",
  12: "{fromPlayer} changed the description of the tribe.",
  13: "{fromPlayer} joined the tribe.",
  14: "{fromPlayer} rejected the invitation.",
  666: "The tribe has canceled their relationship with {toTribe}.",
};

export function EventMessage({ event, go }) {
  const parts = (EVENT_TEXT[event.type] ?? "").split(/(\{fromPlayer\}|\{toPlayer\}|\{toTribe\})/);
  return (
    <>
      {parts.map((p, i) => {
        if (p === "{fromPlayer}") return <PlayerLink key={i} name={event.from} go={go} />;
        if (p === "{toPlayer}") return <PlayerLink key={i} name={event.to} go={go} />;
        if (p === "{toTribe}") return <TribeLink key={i} id={event.toTribeId} tag={event.toTribeTag} go={go} />;
        return p;
      })}
    </>
  );
}

/** Two-digit day.month and hour:minute, the way the log shows them. */
export function eventDate(iso) {
  const d = new Date(iso);
  const p = (n) => String(n).padStart(2, "0");
  return [`${p(d.getDate())}.${p(d.getMonth() + 1)}`, `${p(d.getHours())}:${p(d.getMinutes())}`];
}

/** Text input state helper. */
export const useField = (initial) => {
  const [v, setV] = useState(initial);
  return [v, (e) => setV(e.target.value), setV];
};

// privilege columns of the member table, in the original's order
export const RIGHT_ROLES = ["invite", "diplomacy", "mass_mail", "forum_mod", "internal_forum", "trusted_member"];
