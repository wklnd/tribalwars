// BB code, as the original's tribe announcements, descriptions, forum posts and profile texts use it.
// <BB text go /> renders it; <BBToolbar /> is the button row of the original's templates/bbcode.php (same markup and
// sprite offsets) that inserts tags into a textarea. The original's own renderer (Text::formatAll) is compiled, so
// the set of tags follows the toolbar: b i u s color size url img quote spoiler center table player ally coord unit
// building br. Anything else stays literal text.
import { useRef, useState } from "react";

const TOKEN = /\[(\/?)([a-z_*|]+)(?:=([^\]]*))?\]/gi;
const PAIRED = new Set(["b", "i", "u", "s", "color", "size", "url", "img", "quote", "spoiler", "center", "table", "player", "ally", "tribe",
  "coord", "unit", "building", "list", "**", "*", "report_display"]);
const SINGLE = new Set(["br", "|", "||"]);

/** Turns the text into a tree: strings and {tag, arg, children}. Unclosed tags close at the end, stray closers are text. */
export function parseBB(text) {
  const root = { tag: "root", children: [] };
  const stack = [root];
  let last = 0;
  const top = () => stack[stack.length - 1];
  const push = (node) => top().children.push(node);
  TOKEN.lastIndex = 0;
  for (let m; (m = TOKEN.exec(text)); ) {
    const [raw, closing, nameRaw, arg] = m;
    const name = nameRaw.toLowerCase();
    if (m.index > last) push(text.slice(last, m.index));
    last = m.index + raw.length;
    if (SINGLE.has(name) && !closing) {
      push({ tag: name, children: [] });
    } else if (!PAIRED.has(name)) {
      push(raw);
    } else if (!closing) {
      const node = { tag: name, arg, children: [] };
      push(node);
      stack.push(node);
    } else {
      const at = stack.map((n) => n.tag).lastIndexOf(name);
      if (at > 0) stack.length = at;
      else push(raw);
    }
  }
  if (last < text.length) push(text.slice(last));
  return root.children;
}

const plain = (nodes) => nodes.map((n) => (typeof n === "string" ? n : plain(n.children))).join("");
const safeUrl = (u) => (/^(https?:\/\/|\/)/i.test(u) ? u : null);
const SIZE = { 6: "6pt", 7: "7pt", 9: "9pt", 12: "12pt", 20: "20pt" };

function Spoiler({ title, children }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="spoiler">
      <input type="button" value={open ? "Hide" : "Show"} onClick={() => setOpen(!open)} /> <b>{title || "Spoiler"}</b>
      <div style={{ display: open ? "block" : "none" }}>{children}</div>
    </div>
  );
}

// [table][**]head1[||]head2[/**] [*]a[|]b [/table]: rows of cells separated by [|] / [||]
function Table({ nodes, render }) {
  const rows = [];
  for (const n of nodes) {
    if (typeof n === "string" || (n.tag !== "*" && n.tag !== "**")) continue;
    const cells = [[]];
    for (const c of n.children) {
      if (typeof c !== "string" && (c.tag === "|" || c.tag === "||")) cells.push([]);
      else cells[cells.length - 1].push(c);
    }
    rows.push({ head: n.tag === "**", cells });
  }
  return (
    <table className="vis">
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>{r.cells.map((c, j) => (r.head ? <th key={j}>{render(c)}</th> : <td key={j}>{render(c)}</td>))}</tr>
        ))}
      </tbody>
    </table>
  );
}

function renderNodes(nodes, go) {
  const one = (n, key) => {
    if (typeof n === "string") return n;
    const kids = () => renderNodes(n.children, go);
    switch (n.tag) {
      case "b": return <b key={key}>{kids()}</b>;
      case "i": return <i key={key}>{kids()}</i>;
      case "u": return <u key={key}>{kids()}</u>;
      case "s": return <s key={key}>{kids()}</s>;
      case "br": return <br key={key} />;
      case "center": return <div key={key} style={{ textAlign: "center" }}>{kids()}</div>;
      case "color": return <span key={key} style={{ color: /^[#a-z0-9(),. ]{1,30}$/i.test(n.arg ?? "") ? n.arg : undefined }}>{kids()}</span>;
      case "size": return <span key={key} style={{ fontSize: SIZE[n.arg] ?? (Number(n.arg) >= 6 && Number(n.arg) <= 30 ? n.arg + "pt" : undefined) }}>{kids()}</span>;
      case "url": {
        const href = safeUrl(n.arg ?? plain(n.children));
        return href ? <a key={key} href={href} target="_blank" rel="noreferrer">{kids()}</a> : kids();
      }
      case "img": {
        const src = safeUrl(plain(n.children));
        return src ? <img key={key} src={src} alt="" style={{ maxWidth: "100%" }} /> : null;
      }
      case "quote":
        return (
          <div key={key} className="quote">
            {n.arg && <div className="quote_author">{n.arg} wrote:</div>}
            <div className="quote_message">{kids()}</div>
          </div>
        );
      case "spoiler": return <Spoiler key={key} title={n.arg}>{kids()}</Spoiler>;
      case "table": return <Table key={key} nodes={n.children} render={(c) => renderNodes(c, go)} />;
      case "player": {
        const name = n.arg || plain(n.children);
        return <a key={key} href="#" onClick={(e) => go?.(e, "info_player:p_" + name)}>{n.children.length && n.arg ? kids() : name}</a>;
      }
      case "ally":
      case "tribe": {
        const tag = n.arg || plain(n.children);
        return <a key={key} href="#" onClick={(e) => go?.(e, "info_ally:t_" + tag)}>{n.children.length && n.arg ? kids() : tag}</a>;
      }
      case "coord": return <span key={key}>{plain(n.children)}</span>;
      case "unit": return <img key={key} src={`graphic/unit/unit_${plain(n.children).trim()}.png`} alt={plain(n.children)} />;
      case "building": return <img key={key} src={`graphic/buildings/${plain(n.children).trim()}.png`} alt={plain(n.children)} />;
      case "list": return <ul key={key}>{kids()}</ul>;
      default: return kids();
    }
  };
  return nodes.map((n, i) => one(n, i));
}

/** Renders BB code text. {@code go} is the app's navigate function ([player] and [ally] tags link inside the game). */
export function BB({ text, go }) {
  if (!text) return null;
  return <>{renderNodes(parseBB(String(text)), go)}</>;
}

// ---- toolbar (templates/bbcode.php) -----------------------------------------------------------------------------

const BUTTONS = [
  ["bold", "Bold", 0, "[b]", "[/b]"],
  ["italic", "Italic", -20, "[i]", "[/i]"],
  ["underline", "Under scored", -40, "[u]", "[/u]"],
  ["strikethrough", "Crossed out", -60, "[s]", "[/s]"],
  ["quote", "Quote", -140, "[quote=Author]\n", "\n[/quote]"],
  ["spoiler", "Spoiler", -260, "[spoiler=Spoiler]", "[/spoiler]"],
  ["url", "Address", -160, "[url]", "[/url]"],
  ["player", "Player", -80, "[player]", "[/player]"],
  ["tribe", "Tribe", -100, "[ally]", "[/ally]"],
  ["coord", "Coordinate", -120, "[coord]", "[/coord]"],
  ["report_display", "Report", -240, "[report_display]", "[/report_display]"],
  ["size", "Font size", -220],
  ["image", "Picture", -180, "[img]", "[/img]"],
  ["color", "Color", -200],
  ["table", "List", -280, "[table]\n[**]head1", "[||]head2[/**]\n[*]test1[|]test2\n[/table]"],
  ["units", "Unit", -300],
  ["building", "Building", -320],
];
const SIZES = [["Very small", 6], ["Small", 7], ["Normal", 9], ["Large", 12], ["Very large", 20]];
const COLORS = ["#f00", "#ff0", "#0f0", "#0ff", "#00f", "#f0f"];
const UNIT_IDS = ["spear", "sword", "axe", "archer", "spy", "light", "marcher", "heavy", "ram", "catapult", "knight", "snob"];
const BUILDING_IDS = ["main", "barracks", "stable", "garage", "snob", "smith", "place", "statue", "market", "wood", "stone", "iron", "farm", "storage", "hide", "wall"];

/** Inserts text around the selection of the textarea the ref points to (and keeps React state in sync). */
function insertAround(area, open, close, set) {
  if (!area) return;
  const { selectionStart: a, selectionEnd: b, value } = area;
  const next = value.slice(0, a) + open + value.slice(a, b) + close + value.slice(b);
  set?.(next);
  area.value = next;
  area.focus();
  const caret = a + open.length + (b - a);
  area.setSelectionRange(caret, caret);
}

/** The button row above a BB code textarea. {@code area} is a ref to it, {@code onChange} receives the new text. */
export function BBToolbar({ area, onChange }) {
  const [popup, setPopup] = useState(null); // "size" | "color" | "units" | "building"
  const [color, setColor] = useState("#ff0000");
  const box = useRef(null);
  const insert = (open, close) => insertAround(area.current, open, close, onChange);
  const click = (e, [id, , , open, close]) => {
    e.preventDefault();
    if (open !== undefined) insert(open, close);
    else setPopup(popup === id ? null : id);
  };
  return (
    <div ref={box}>
      {BUTTONS.map((btn) => (
        <a key={btn[0]} id={"bb_button_" + (btn[0] === "player" ? "player" : btn[0])} title={btn[1]} href="#" onClick={(e) => click(e, btn)}>
          <span style={{ display: "inline-block", background: `url(graphic//bbcodes/bbcodes.png?1) no-repeat ${btn[2]}px 0px`, paddingLeft: 0, paddingBottom: 0, marginRight: 2, marginBottom: 3, width: 20, height: 20 }}>&nbsp;</span>
        </a>
      ))}
      {popup === "size" && (
        <table id="bb_sizes" style={{ clear: "both", whiteSpace: "nowrap", position: "static" }}>
          <tbody>
            <tr>
              <td>
                {SIZES.map(([label, size]) => (
                  <span key={size}>
                    <a href="#" onClick={(e) => { e.preventDefault(); insert(`[size=${size}]`, "[/size]"); setPopup(null); }}>» {label}</a>
                    <br />
                  </span>
                ))}
              </td>
            </tr>
          </tbody>
        </table>
      )}
      {popup === "color" && (
        <div id="bb_color_picker" className="bb_color_picker" style={{ position: "static", clear: "both", display: "block" }}>
          <div className="popup_menu" style={{ cursor: "default" }}>
            <a href="#" onClick={(e) => { e.preventDefault(); setPopup(null); }}>Close</a>
          </div>
          <div id="bb_color_picker_colors">
            {COLORS.map((c, i) => (
              <div key={c} id={"bb_color_picker_c" + i} style={{ background: c }} onClick={() => setColor(c)} />
            ))}
            <br />
          </div>
          <div id="bb_color_picker_preview" style={{ color }}>Text</div>
          <input type="text" id="bb_color_picker_tx" value={color} onChange={(e) => setColor(e.target.value)} />
          <input type="button" value="OK" id="bb_color_picker_ok" onClick={() => { insert(`[color=${color}]`, "[/color]"); setPopup(null); }} />
        </div>
      )}
      {(popup === "units" || popup === "building") && (
        <div style={{ clear: "both" }}>
          {(popup === "units" ? UNIT_IDS : BUILDING_IDS).map((id) => (
            <a key={id} href="#" onClick={(e) => { e.preventDefault(); insert(`[${popup === "units" ? "unit" : "building"}]${id}[/${popup === "units" ? "unit" : "building"}]`, ""); setPopup(null); }}>
              <img src={popup === "units" ? `graphic/unit/unit_${id}.png` : `graphic/buildings/${id}.png`} alt={id} />
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
