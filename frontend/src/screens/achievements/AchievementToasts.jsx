// The "Achievement unlocked!" popup of the original (UI.Notification -> .side-notification, bottom right): polls the
// server for levels earned since the last look and shows each one for a few seconds; a click opens the achievements page.
import { useEffect, useRef, useState } from "react";
import { api } from "../../api";
import { useLiveTopic, usePollMs } from "../../lib/live";
import { AwardImage, iconOf } from "./AchievementsScreen";

const SHOW_MS = 9000;

export function AchievementToasts({ enabled, go }) {
  const [toasts, setToasts] = useState([]);
  const busy = useRef(false);
  const pollNow = useRef(null);
  const pollMs = usePollMs(4000, 30000);
  useLiveTopic("achievements", () => pollNow.current?.());

  useEffect(() => {
    if (!enabled) return undefined;
    const poll = async () => {
      if (busy.current) return;
      busy.current = true;
      try {
        const fresh = await api.unseenAchievements();
        if (fresh.length) {
          setToasts((t) => [...t, ...fresh]);
          fresh.forEach((u) => setTimeout(() => setToasts((t) => t.filter((x) => x.id !== u.id)), SHOW_MS));
        }
      } catch {
        /* not logged in / server restarting: try again next time */
      } finally {
        busy.current = false;
      }
    };
    pollNow.current = poll;
    poll();
    const id = setInterval(poll, pollMs);
    return () => {
      pollNow.current = null;
      clearInterval(id);
    };
  }, [enabled, pollMs]);

  if (!toasts.length) return null;
  return (
    <div id="side-notification-container">
      {toasts.map((t, i) => (
        <div
          key={t.id}
          className="side-notification side-notification-visible"
          style={{ position: "relative", marginTop: i ? 4 : 0, height: 66 }}
          onClick={() => { setToasts((x) => x.filter((y) => y.id !== t.id)); go?.(null, "info_player:awards"); }}
        >
          <div className="img-container">
            <AwardImage icon={iconOf({ key: t.key, icon: t.icon }, t.level)} level={t.level} />
          </div>
          <div className="content">
            <strong>Achievement unlocked!</strong>
            <p>
              {t.name}
              {t.level > 1 || t.level === 1 ? ` (Level ${t.level})` : ""}
              <br />
              {t.description.replace("{n}", Number(t.threshold).toLocaleString("de-DE"))}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
