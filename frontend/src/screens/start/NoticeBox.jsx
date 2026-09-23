import { useEffect, useState } from "react";

// StartPage.noticeBox.show(message, type): a fixed box (.auto-hide-box .error-box) that stays for 2s, fades out
// slowly (600ms) and disappears; a click removes it at once. `notice` = { key, message, type? } or null.
export function NoticeBox({ notice }) {
  const [gone, setGone] = useState(null); // key of the notice that was dismissed / timed out
  const [fading, setFading] = useState(null);
  const key = notice ? notice.key : null;

  useEffect(() => {
    if (key == null) return undefined;
    const t1 = setTimeout(() => setFading(key), 2000);
    const t2 = setTimeout(() => setGone(key), 2600);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [key]);

  if (!notice || gone === key) return null;
  return (
    <div
      className="tw2-notice"
      style={fading === key ? { opacity: 0, transition: "opacity 0.6s" } : undefined}
      onClick={() => setGone(key)}
    >
      <p style={{ margin: 0 }}>{String(notice.message).split("\n").flatMap((m, i) => (i ? [<br key={i} />, m] : [m]))}</p>
    </div>
  );
}
