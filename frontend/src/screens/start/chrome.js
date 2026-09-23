// Page-level chrome swaps for the start experience. The React app normally shows game screens styled by
// /game.css (+ overview.css ...), body#ds_body. The start pages (including the join page - it's part of the
// same account flow, so it keeps the same skin rather than switching to a game.css page) are separate
// documents styled by start2.css, so while any of them are mounted we swap stylesheets / body id+class /
// <html> attributes / <title>, and return an exact undo function.

export const START_TITLE = "tribalwars - nilz clone";
export const JOIN_TITLE = "tribalwars - nilz clone - Join";
// Stylesheets of the game shell.
const CLASHING_START = ["/game.css", "/overview.css", "/map.css", "/village_target.css"];

function disableSheets(doc, hrefs, undo) {
  const links = Array.from(doc.querySelectorAll('link[rel="stylesheet"]')).filter((l) =>
    hrefs.includes((l.getAttribute("href") || "").split("?")[0]),
  );
  // `link.disabled = true` should suffice, but the associated CSSStyleSheet's own `disabled` flag can end up
  // out of sync with it (observed live: link.disabled reads true while its sheet.disabled reads false, and
  // its rules keep applying) - detach the node instead, which unambiguously drops it from the cascade.
  for (const l of links) {
    const parent = l.parentNode;
    const next = l.nextSibling;
    l.remove();
    undo.push(() => parent.insertBefore(l, next));
  }
}

function setAttrs(el, attrs, undo) {
  for (const [name, value] of Object.entries(attrs)) {
    const old = el.getAttribute(name);
    if (value === null) el.removeAttribute(name);
    else el.setAttribute(name, value);
    undo.push(() => (old === null ? el.removeAttribute(name) : el.setAttribute(name, old)));
  }
}

function setTitle(doc, title, undo) {
  const old = doc.title;
  doc.title = title;
  undo.push(() => { doc.title = old; });
}

function enterStartSkin(doc, title, undo) {
  disableSheets(doc, CLASHING_START, undo);
  for (const href of ["/start2.css"]) {
    const l = doc.createElement("link");
    l.rel = "stylesheet";
    l.type = "text/css";
    l.href = href;
    doc.head.appendChild(l);
    undo.push(() => l.remove());
  }
  // MedievalSharp: the banner title's display font. Loaded only while the start skin is mounted.
  const fontLink = doc.createElement("link");
  fontLink.rel = "stylesheet";
  fontLink.href = "https://fonts.googleapis.com/css2?family=MedievalSharp&display=swap";
  doc.head.appendChild(fontLink);
  undo.push(() => fontLink.remove());
  // <meta name="viewport"> (the start pages are responsive)
  if (!doc.querySelector('meta[name="viewport"]')) {
    const m = doc.createElement("meta");
    m.name = "viewport";
    m.content = "width=device-width, initial-scale=1";
    doc.head.appendChild(m);
    undo.push(() => m.remove());
  }
  setTitle(doc, title, undo);
  setAttrs(doc.body, { id: "home", class: "tw2", dir: "ltr" }, undo);
  setAttrs(doc.documentElement, { class: "no-js", dir: "ltr" }, undo);
}

export function enterStartPage() {
  const doc = document;
  const undo = [];
  enterStartSkin(doc, START_TITLE, undo);
  return () => { for (let i = undo.length - 1; i >= 0; i--) undo[i](); };
}

// create_account.php ("Join <world>?"): same start2.css skin as the rest of the account flow.
export function enterJoinPage() {
  const doc = document;
  const undo = [];
  enterStartSkin(doc, JOIN_TITLE, undo);
  return () => { for (let i = undo.length - 1; i >= 0; i--) undo[i](); };
}
