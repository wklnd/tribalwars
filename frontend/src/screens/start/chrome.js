// Page-level chrome swaps for the start experience. The React app normally shows game screens styled by
// /game.css (+ overview.css ...), body#ds_body. The original's start pages are separate documents, so while they
// are mounted we swap stylesheets / body id+class / <html> attributes / <title>, and return an exact undo function.

export const START_TITLE = "tribalwars - nilz clone";
export const JOIN_TITLE = "tribalwars - nilz clone - Join";
// Stylesheets of the game shell (game.css is the join page's own stylesheet, so it is handled separately).
const CLASHING_START = ["/game.css", "/overview.css", "/map.css", "/village_target.css"];
const CLASHING_JOIN = ["/overview.css", "/map.css", "/village_target.css"];

function disableSheets(doc, hrefs, undo) {
  const links = Array.from(doc.querySelectorAll('link[rel="stylesheet"]')).filter((l) =>
    hrefs.includes((l.getAttribute("href") || "").split("?")[0]),
  );
  for (const l of links) {
    const was = l.disabled;
    l.disabled = true;
    undo.push(() => { l.disabled = was; });
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

// start.css pages: <html dir="ltr" class="no-js">, <body dir="ltr" id="home" class=" "> (index/layouts/index.php)
export function enterStartPage() {
  const doc = document;
  const undo = [];
  disableSheets(doc, CLASHING_START, undo);
  // start.css + the inline <style> of the original's portal bar (kept as a file next to it)
  for (const href of ["/start.css", "/worldselect-portalbar.css"]) {
    const l = doc.createElement("link");
    l.rel = "stylesheet";
    l.type = "text/css";
    l.href = href;
    doc.head.appendChild(l);
    undo.push(() => l.remove());
  }
  // <meta name="viewport"> (the original start page is responsive)
  if (!doc.querySelector('meta[name="viewport"]')) {
    const m = doc.createElement("meta");
    m.name = "viewport";
    m.content = "width=device-width, initial-scale=1";
    doc.head.appendChild(m);
    undo.push(() => m.remove());
  }
  setTitle(doc, START_TITLE, undo);
  setAttrs(doc.body, { id: "home", class: " ", dir: "ltr" }, undo);
  setAttrs(doc.documentElement, { class: "no-js", dir: "ltr" }, undo);
  return () => { for (let i = undo.length - 1; i >= 0; i--) undo[i](); };
}

// create_account.php ("Join <world>?"): a game.css page, <body id="ds_body" class="header">
export function enterJoinPage() {
  const doc = document;
  const undo = [];
  disableSheets(doc, CLASHING_JOIN, undo);
  setTitle(doc, JOIN_TITLE, undo);
  setAttrs(doc.body, { id: "ds_body", class: "header" }, undo);
  return () => { for (let i = undo.length - 1; i >= 0; i--) undo[i](); };
}
