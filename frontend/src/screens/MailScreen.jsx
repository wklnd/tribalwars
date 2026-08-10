/* screen=mail — the in-game mail: inbox with folders, conversations, writing (with the address-book popup), circular mail to the
   tribe, the address book, folders and blocked senders.
   The original TWLan has no mail (every mail page answers "couldn't be found"): only its menu entries, its header icon and the
   game.css classes for messages (.post, .igmline, #igm_to) exist, so this is built from those.
   Routes: mail:in, mail:in_<folderId>, mail:view_<threadId>, mail:new, mail:new_<player>, mail:mass_out, mail:address, mail:groups. */
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api";
import { BB, BBToolbar } from "../lib/bbcode";
import { useLiveTopic, usePollMs } from "../lib/live";
import { PlayerLink, eventDate } from "./ally/common";

const MODES = [
  ["in", "Mail"],
  ["new", "Write message"],
  ["mass_out", "Circular mail"],
  ["address", "Address book"],
  ["groups", "Folders"],
  ["block", "Block sender"],
];

const when = (iso) => {
  if (!iso) return "";
  const [d, t] = eventDate(iso);
  return `${d} ${t}`;
};

const Err = ({ error }) => (error ? <div className="error_box">{error}</div> : null);
const Ok = ({ text }) => (text ? <div className="success_box" style={{ padding: 4 }}>{text}</div> : null);

// load a resource, refetch it when the server says mail changed and (slowly) now and then
function useMail(load, deps) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const loadRef = useRef(load);
  loadRef.current = load;
  const reload = useCallback(() => loadRef.current().then((d) => { setData(d); setError(null); }).catch((e) => setError(e.message)), []);
  useEffect(() => {
    setData(null);
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useLiveTopic("mail", reload);
  const every = usePollMs(10000, 60000);
  useEffect(() => {
    const id = setInterval(reload, every);
    return () => clearInterval(id);
  }, [reload, every]);
  return { data, setData, error, setError, reload };
}

// run an action; its error goes to the box of the screen
async function attempt(setError, fn) {
  try {
    setError(null);
    return (await fn()) ?? true;
  } catch (e) {
    setError(e.message);
    return false;
  }
}

/* ---------------- inbox ---------------- */
function Inbox({ folder, go }) {
  const [page, setPage] = useState(0);
  const { data, error, setError, reload } = useMail(() => api.mailInbox(folder, page), [folder, page]);
  const [sel, setSel] = useState({});
  const [target, setTarget] = useState("");
  if (!data) return <Err error={error} />;
  const ids = data.threads.filter((t) => sel[t.id]).map((t) => t.id);
  const allSelected = data.threads.length > 0 && data.threads.every((t) => sel[t.id]);
  const act = async (fn) => {
    if (!ids.length) return;
    if (await attempt(setError, fn)) { setSel({}); reload(); }
  };
  return (
    <>
      <Err error={error} />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            {data.folders.map((f) => (
              <td key={f.id} className={f.id === data.folder ? "selected" : undefined} style={{ textAlign: "center" }}>
                <a href="#" onClick={(e) => go(e, "mail:in" + (f.id ? "_" + f.id : ""))}>{f.name}{f.unread > 0 ? ` (${f.unread})` : ""}</a>
              </td>
            ))}
          </tr>
        </tbody>
      </table>
      <table id="mail_list" className="vis" width="100%">
        <tbody>
          <tr>
            <th width="20" />
            <th>Subject</th>
            <th>Last message from</th>
            <th>Sent</th>
          </tr>
          {data.threads.map((t) => (
            <tr key={t.id}>
              <td><input type="checkbox" checked={!!sel[t.id]} onChange={(e) => setSel({ ...sel, [t.id]: e.target.checked })} /></td>
              <td>
                {t.unread && <span className="icon header new_mail" title="New mail" style={{ display: "inline-block", verticalAlign: "middle" }} />}{" "}
                <a href="#" onClick={(e) => go(e, "mail:view_" + t.id)}>{t.unread ? <b>{t.subject}</b> : t.subject}</a>
                {t.mass && <span className="grey"> (circular mail)</span>}
                {t.messages > 1 && <span className="grey"> ({t.messages})</span>}
              </td>
              <td><PlayerLink name={t.author} go={go} /></td>
              <td className="nowrap">{when(t.at)}</td>
            </tr>
          ))}
          {data.threads.length === 0 && <tr><td colSpan="4">There are no messages in this folder.</td></tr>}
          <tr>
            <th colSpan="4">
              <input type="checkbox" id="select_all" checked={allSelected} onChange={(e) => setSel(Object.fromEntries(data.threads.map((t) => [t.id, e.target.checked])))} />{" "}
              <label htmlFor="select_all">select all</label>
            </th>
          </tr>
        </tbody>
      </table>
      {data.pages > 1 && (
        <p>
          {Array.from({ length: data.pages }, (_, i) => i).map((i) =>
            i === data.page ? <strong key={i}>&gt;{i + 1}&lt; </strong> : <a key={i} href="#" onClick={(e) => { e.preventDefault(); setPage(i); }}>[{i + 1}] </a>,
          )}
        </p>
      )}
      <table className="vis" style={{ float: "left" }}>
        <tbody>
          <tr>
            <td>
              <input type="button" className="btn btn-cancel" value="Delete" onClick={() => act(async () => { await api.mailDelete(ids); })} />{" "}
              <input type="button" className="btn" value="Mark as read" onClick={() => act(() => api.mailRead(ids, true))} />{" "}
              <input type="button" className="btn" value="Mark as unread" onClick={() => act(() => api.mailRead(ids, false))} />
            </td>
            <td>
              <select value={target} onChange={(e) => setTarget(e.target.value)}>
                <option value="">Move to…</option>
                {data.folders.filter((f) => f.id !== data.folder).map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
              </select>{" "}
              <input type="button" className="btn" value="Move" disabled={target === ""} onClick={() => act(() => api.mailMove(ids, Number(target)))} />
            </td>
          </tr>
        </tbody>
      </table>
      <div style={{ clear: "both" }} />
    </>
  );
}

/* ---------------- one conversation ---------------- */
function Conversation({ id, go }) {
  const { data, error, setError, reload } = useMail(() => api.mailView(id), [id]);
  const [reply, setReply] = useState("");
  const [target, setTarget] = useState("");
  const area = useRef(null);
  if (!data) return <Err error={error} />;
  const send = async (e) => {
    e.preventDefault();
    const r = await attempt(setError, () => api.mailReply(id, reply));
    if (!r) return;
    setReply("");
    if (r.id !== id) go(null, "mail:view_" + r.id); // an answer to a circular mail is a new conversation
    else reload();
  };
  const lastOwn = data.messages.length > 0 && data.messages[data.messages.length - 1].own;
  return (
    <>
      <Err error={error} />
      <h3>{data.subject}</h3>
      <p className="small">
        {data.mass ? "Circular mail from: " : "Participants: "}
        {data.participants.map((n, i) => <span key={n + i}>{i > 0 && ", "}<PlayerLink name={n} go={go} /></span>)}
      </p>
      <p>
        {data.previousId != null && <><a href="#" onClick={(e) => go(e, "mail:view_" + data.previousId)}>« newer message</a>{" "}</>}
        <a href="#" onClick={(e) => go(e, "mail:in" + (data.folderId ? "_" + data.folderId : ""))}>» Back to the list</a>
        {data.nextId != null && <>{" "}<a href="#" onClick={(e) => go(e, "mail:view_" + data.nextId)}>older message »</a></>}
      </p>
      <div style={{ overflow: "hidden" }}>
        {data.messages.map((m) => (
          <div key={m.id} className={"post" + (m.own ? " own" : "")}>
            <div className="igmline">
              <span className="postheader_left">{" "}<PlayerLink name={m.author} go={go} /></span>
              <span className="postheader_right">{when(m.at)}</span>
            </div>
            <div style={{ padding: 6 }}><BB text={m.body} go={go} /></div>
          </div>
        ))}
      </div>
      <form onSubmit={send} style={{ clear: "both" }}>
        <table className="vis" width="100%">
          <tbody>
            <tr><th>{data.mass ? "Answer the sender" : "Reply"}</th></tr>
            {data.mass && lastOwn && <tr><td className="grey">This is your own circular mail: its recipients answer you personally.</td></tr>}
            <tr><td><BBToolbar area={area} onChange={setReply} /></td></tr>
            <tr><td><textarea ref={area} rows="8" style={{ width: "100%" }} value={reply} onChange={(e) => setReply(e.target.value)} /></td></tr>
            <tr><td><input type="submit" className="btn answer" value="Send" /></td></tr>
          </tbody>
        </table>
      </form>
      <p>
        <a href="#" onClick={async (e) => { e.preventDefault(); if (await attempt(setError, async () => { await api.mailDelete([id]); })) go(null, "mail:in"); }}>» Delete</a>{" "}
        <a href="#" onClick={async (e) => { e.preventDefault(); if (await attempt(setError, () => api.mailRead([id], false))) go(null, "mail:in"); }}>» Mark as unread</a>{" "}
        <select value={target} onChange={async (e) => {
          const v = e.target.value;
          setTarget(v);
          if (v !== "" && (await attempt(setError, () => api.mailMove([id], Number(v))))) { setTarget(""); reload(); }
        }}>
          <option value="">Move to…</option>
          {data.folders.filter((f) => f.id !== data.folderId).map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
        </select>
      </p>
    </>
  );
}

/* ---------------- write ---------------- */
// the original's igm_to_* helpers: the recipient field holds names separated by ";", the address book fills it
function AddressPopup({ onPick, onClose }) {
  const { data } = useMail(() => api.mailAddresses(), []);
  return (
    <div id="igm_to" style={{ display: "inline" }}>
      <table className="vis" width="100%">
        <tbody>
          <tr><th>Address book <a href="#" style={{ float: "right" }} onClick={(e) => { e.preventDefault(); onClose(); }}>x</a></th></tr>
        </tbody>
      </table>
      <div id="igm_to_content">
        {!data && "Loading…"}
        {data && data.length === 0 && <span className="grey">Your address book is empty.</span>}
        {data && data.map((c) => (
          <div key={c.id}><a href="#" onClick={(e) => { e.preventDefault(); onPick(c.name); }}>{c.name}</a></div>
        ))}
      </div>
    </div>
  );
}

function Compose({ to: initialTo, go }) {
  const [to, setTo] = useState(initialTo ? initialTo + ";" : "");
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [error, setError] = useState(null);
  const [book, setBook] = useState(false);
  const area = useRef(null);
  const submit = async (e) => {
    e.preventDefault();
    const names = to.split(/[;,]/).map((n) => n.trim()).filter(Boolean);
    const r = await attempt(setError, () => api.mailSend(names, subject, body));
    if (r) go(null, "mail:view_" + r.id);
  };
  return (
    <form onSubmit={submit}>
      <Err error={error} />
      <table className="vis" width="100%">
        <tbody>
          <tr><th colSpan="2">Write message</th></tr>
          <tr>
            <td width="80">To:</td>
            <td style={{ position: "relative" }}>
              <input type="text" id="to" name="to" size="50" value={to} onChange={(e) => setTo(e.target.value)} />{" "}
              <a href="#" onClick={(e) => { e.preventDefault(); setBook(!book); }}>» Address book</a>{" "}
              <a href="#" onClick={(e) => { e.preventDefault(); setTo(""); }}>» Clear</a>
              {book && <AddressPopup onPick={(n) => setTo((v) => v + n + ";")} onClose={() => setBook(false)} />}
            </td>
          </tr>
          <tr>
            <td>Subject:</td>
            <td><input type="text" name="subject" size="50" maxLength="120" value={subject} onChange={(e) => setSubject(e.target.value)} /></td>
          </tr>
          <tr><td colSpan="2"><BBToolbar area={area} onChange={setBody} /></td></tr>
          <tr>
            <td colSpan="2">
              <textarea ref={area} name="text" rows="12" style={{ width: "100%" }} value={body} onChange={(e) => setBody(e.target.value)} />
              <div>
                <a href="#" onClick={(e) => { e.preventDefault(); area.current?.setAttribute("rows", String(Number(area.current.getAttribute("rows")) + 3)); }}>» bigger</a>{" "}
                <a href="#" onClick={(e) => { e.preventDefault(); const r = Number(area.current?.getAttribute("rows")); if (r >= 4) area.current.setAttribute("rows", String(r - 3)); }}>» smaller</a>
              </div>
            </td>
          </tr>
          <tr><td colSpan="2"><input type="submit" className="btn" value="Send" /></td></tr>
        </tbody>
      </table>
    </form>
  );
}

/* ---------------- circular mail ---------------- */
function Circular({ go }) {
  const { data, setData, error, setError } = useMail(() => api.mailCircular(), []);
  const [subject, setSubject] = useState("");
  const [body, setBody] = useState("");
  const [done, setDone] = useState(null);
  const area = useRef(null);
  if (!data) return <Err error={error} />;
  if (!data.tribe) return <><Err error={error} /><p className="error_box">You do not belong to a tribe, so there is nobody to write a circular mail to.</p></>;
  const submit = async (e) => {
    e.preventDefault();
    setDone(null);
    const r = await attempt(setError, () => api.mailSendCircular(subject, body));
    if (r && r !== true) { setData(r); setSubject(""); setBody(""); setDone("Your circular mail was sent."); }
  };
  return (
    <>
      <Err error={error} />
      <Ok text={done} />
      {data.allowed ? (
        <form onSubmit={submit}>
          <table className="vis" width="100%">
            <tbody>
              <tr><th colSpan="2">Circular mail to {data.tribe} ({data.members} members)</th></tr>
              <tr>
                <td width="80">Subject:</td>
                <td><input type="text" size="50" maxLength="120" value={subject} onChange={(e) => setSubject(e.target.value)} /></td>
              </tr>
              <tr><td colSpan="2"><BBToolbar area={area} onChange={setBody} /></td></tr>
              <tr><td colSpan="2"><textarea ref={area} rows="12" style={{ width: "100%" }} value={body} onChange={(e) => setBody(e.target.value)} /></td></tr>
              <tr><td colSpan="2"><input type="submit" className="btn" value="Send" /></td></tr>
            </tbody>
          </table>
        </form>
      ) : (
        <p className="error_box">You may not write circular mails. A duke can give you this privilege on the members page of the tribe.</p>
      )}
      <br />
      <table className="vis" width="100%">
        <tbody>
          <tr><th>Sent circular mails</th><th>Recipients</th><th>Sent</th></tr>
          {data.sent.map((c) => (
            <tr key={c.id}>
              <td><a href="#" onClick={(e) => go(e, "mail:view_" + c.id)}>{c.subject}</a></td>
              <td>{c.recipients}</td>
              <td className="nowrap">{when(c.at)}</td>
            </tr>
          ))}
          {data.sent.length === 0 && <tr><td colSpan="3">You have not sent a circular mail yet.</td></tr>}
        </tbody>
      </table>
    </>
  );
}

/* ---------------- address book and blocked senders (same list, two purposes) ---------------- */
function ContactList({ blocked, go }) {
  const { data, setData, error, setError } = useMail(() => (blocked ? api.mailBlocked() : api.mailAddresses()), [blocked]);
  const [name, setName] = useState("");
  if (!data) return <Err error={error} />;
  const swap = async (fn) => {
    const r = await attempt(setError, fn);
    if (r && r !== true) setData(r);
    return r;
  };
  return (
    <>
      <Err error={error} />
      <table className="vis" width="100%">
        <tbody>
          <tr><th>{blocked ? "Blocked senders" : "Address book"}</th><th width="200">Action</th></tr>
          {data.map((c) => (
            <tr key={c.id}>
              <td><PlayerLink name={c.name} go={go} /></td>
              <td>
                {!blocked && <><a href="#" onClick={(e) => go(e, "mail:new_" + c.name)}>write</a>{" "}</>}
                <a href="#" onClick={(e) => { e.preventDefault(); swap(() => (blocked ? api.mailUnblock(c.id) : api.mailRemoveAddress(c.id))); }}>{blocked ? "unblock" : "remove"}</a>
              </td>
            </tr>
          ))}
          {data.length === 0 && <tr><td colSpan="2">{blocked ? "You have not blocked anybody." : "Your address book is empty."}</td></tr>}
        </tbody>
      </table>
      <br />
      <form onSubmit={async (e) => { e.preventDefault(); if (await swap(() => (blocked ? api.mailBlock(name) : api.mailAddAddress(name)))) setName(""); }}>
        <table className="vis">
          <tbody>
            <tr>
              <td>{blocked ? "Block player:" : "Add player:"}</td>
              <td><input type="text" value={name} maxLength="40" onChange={(e) => setName(e.target.value)} /></td>
              <td><input type="submit" className="btn" value={blocked ? "Block" : "Add"} /></td>
            </tr>
          </tbody>
        </table>
      </form>
      {blocked && <p className="grey">Blocked players cannot send you messages or circular mails.</p>}
    </>
  );
}

// the "Block sender" page of the settings / profile menus
export const BlockedSenders = ({ go }) => <ContactList blocked go={go} />;

/* ---------------- folders ---------------- */
function Folders() {
  const { data, setData, error, setError } = useMail(() => api.mailInbox(0, 0), []);
  const [name, setName] = useState("");
  const [edits, setEdits] = useState({});
  if (!data) return <Err error={error} />;
  const swap = async (fn) => {
    const r = await attempt(setError, fn);
    if (r && r !== true) { setData(r); setEdits({}); }
    return r;
  };
  return (
    <>
      <Err error={error} />
      <table className="vis" width="100%">
        <tbody>
          <tr><th>Folder</th><th width="80">Messages</th><th width="140">Action</th></tr>
          <tr><td>Mail</td><td>{data.folders[0]?.threads ?? 0}</td><td className="grey">(cannot be changed)</td></tr>
          {data.folders.filter((f) => f.id !== 0).map((f) => {
            const value = edits[f.id] ?? f.name;
            return (
              <tr key={f.id}>
                <td><input type="text" value={value} maxLength="40" onChange={(e) => setEdits({ ...edits, [f.id]: e.target.value })} /></td>
                <td>{f.threads}</td>
                <td>
                  <a href="#" onClick={(e) => { e.preventDefault(); swap(() => api.mailRenameFolder(f.id, value)); }}>rename</a>{" "}
                  <a href="#" onClick={(e) => { e.preventDefault(); if (window.confirm("Delete this folder? Its messages go back to your inbox.")) swap(() => api.mailDeleteFolder(f.id)); }}>delete</a>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <br />
      <form onSubmit={async (e) => { e.preventDefault(); if (await swap(() => api.mailCreateFolder(name))) setName(""); }}>
        <table className="vis">
          <tbody>
            <tr>
              <td>New folder:</td>
              <td><input type="text" value={name} maxLength="40" onChange={(e) => setName(e.target.value)} /></td>
              <td><input type="submit" className="btn" value="Create" /></td>
            </tr>
          </tbody>
        </table>
      </form>
    </>
  );
}

export function MailScreen({ mode, go }) {
  const m = mode ?? "in";
  const at = m.indexOf("_");
  const base = at < 0 ? m : m.slice(0, at);
  const arg = at < 0 ? "" : m.slice(at + 1);
  let content;
  if (base === "view" && arg) content = <Conversation id={Number(arg)} go={go} />;
  else if (base === "new") content = <Compose key={arg} to={arg} go={go} />;
  else if (base === "mass") content = <Circular go={go} />; // mass_out
  else if (base === "address") content = <ContactList blocked={false} go={go} />;
  else if (base === "groups") content = <Folders />;
  else if (base === "block") content = <ContactList blocked go={go} />;
  else {
    const folder = base === "in" && arg ? Number(arg) : 0;
    content = <Inbox key={folder} folder={folder} go={go} />; // (a new key = a fresh page number and selection)
  }
  const current = base === "view" ? "in" : base === "mass" ? "mass_out" : MODES.some(([k]) => k === base) ? base : "in";
  return (
    <>
      <h2>Mail</h2>
      <table className="no_spacing" width="100%">
        <tbody>
          <tr>
            <td valign="top">
              <table className="vis modemenu" width="100">
                <tbody>
                  {MODES.map(([k, label]) => (
                    <tr key={k}>
                      <td className={k === current ? "selected" : undefined} style={{ minWidth: 80 }}>
                        <a href="#" onClick={(e) => go(e, "mail:" + k)}>{label + " "}</a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </td>
            <td valign="top" width="100%">{content}</td>
          </tr>
        </tbody>
      </table>
    </>
  );
}
