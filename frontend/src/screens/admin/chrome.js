// Page-level swap for the admin panel: while AdminPage is mounted the game stylesheets are switched off and
// /admin-ui.css (everything is scoped under the .adm root) is loaded; everything is undone by the returned function.
// `onReady` fires once the stylesheet is in place, so the panel is never shown unstyled.

export const ADMIN_TITLE = "Admin - tribalwars nilz clone";
const CLASHING = ["/game.css", "/overview.css", "/map.css", "/village_target.css"];

export function enterAdminPage(onReady) {
  const doc = document;
  const undo = [];
  const run = () => { for (let i = undo.length - 1; i >= 0; i--) undo[i](); };

  // take the game stylesheets out of the document (a `disabled` flag alone is not reliable) and put them back on exit
  for (const l of Array.from(doc.querySelectorAll('link[rel="stylesheet"]'))) {
    if (!CLASHING.includes((l.getAttribute("href") || "").split("?")[0])) continue;
    const parent = l.parentNode;
    const next = l.nextSibling;
    l.remove();
    undo.push(() => { if (parent && !l.isConnected) parent.insertBefore(l, next && next.parentNode === parent ? next : null); });
  }

  let link = doc.querySelector('link[data-adm="1"]');
  if (link) {
    onReady?.();
  } else {
    link = doc.createElement("link");
    link.rel = "stylesheet";
    link.href = "/admin-ui.css";
    link.dataset.adm = "1";
    link.onload = () => onReady?.();
    link.onerror = () => onReady?.();
    doc.head.appendChild(link);
  }
  undo.push(() => link.remove());

  if (!doc.querySelector('meta[name="viewport"]')) {
    const m = doc.createElement("meta");
    m.name = "viewport";
    m.content = "width=device-width, initial-scale=1";
    doc.head.appendChild(m);
    undo.push(() => m.remove());
  }

  const oldTitle = doc.title;
  doc.title = ADMIN_TITLE;
  undo.push(() => { doc.title = oldTitle; });

  // the panel is a fixed full-viewport layer; keep the (unstyled) page behind it from scrolling
  const oldOverflow = doc.body.style.overflow;
  doc.body.style.overflow = "hidden";
  undo.push(() => { doc.body.style.overflow = oldOverflow; });
  return run;
}
