/* The tribe forum (ally:forum, ally:forum_b<boardId>, ally:forum_t<threadId>, ally:forum_admin).
   The original TWLan has no forum (its "Tribal forum" tab is an iframe onto a route that does not exist), so this is built from
   its language strings and the game.css classes for forums/posts (.forum, .post, .igmline, .postheader_*, .vis). */
import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../../api";
import { BB, BBToolbar } from "../../lib/bbcode";
import { PlayerLink, eventDate } from "./common";

const KIND_LABEL = { PUBLIC: "public", PRIVATE: "private", HIDDEN: "hidden forum" };
const when = (iso) => {
  if (!iso) return "";
  const [d, t] = eventDate(iso);
  return `${d} ${t}`;
};

// load + poll a forum resource; `run` performs an action and swaps in its answer
function useForum(load, deps) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const loadRef = useRef(load);
  loadRef.current = load;
  const reload = useCallback(() => loadRef.current().then((d) => { setData(d); setError(null); }).catch((e) => setError(e.message)), []);
  useEffect(() => {
    setData(null);
    reload();
    const id = setInterval(reload, 15000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  const run = async (fn) => {
    try {
      const r = await fn();
      setError(null);
      return r ?? true;
    } catch (e) {
      setError(e.message);
      return false;
    }
  };
  return { data, setData, error, setError, reload, run };
}

const Err = ({ error }) => (error ? <div className="error_box">{error}</div> : null);

function BoardTabs({ boards, current, go, canAdmin }) {
  return (
    <div>
      {boards.map((b) => (
        <div key={b.id} className={"forum" + (b.id === current ? " selected" : "")} style={{ marginRight: 4 }}>
          {" "}
          <a href="#" onClick={(e) => go(e, "ally:forum_b" + b.id)}>{b.name}</a>
          {b.unread > 0 && <b> ({b.unread})</b>}{" "}
        </div>
      ))}{" "}
      {canAdmin && (
        <div className="forum" style={{ marginRight: 4 }}>
          {" "}<a href="#" onClick={(e) => go(e, "ally:forum_admin")}>» Administer forums</a>{" "}
        </div>
      )}
    </div>
  );
}

export function ForumIndex({ go }) {
  const { data, error, run, setData } = useForum(() => api.forum(), []);
  if (!data) return <Err error={error} />;
  return (
    <div id="ally_content">
      <Err error={error} />
      <BoardTabs boards={data.boards} go={go} canAdmin={data.canAdmin} />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>Forum</th>
            <th>Topics</th>
            <th>Last post</th>
          </tr>
          {data.boards.map((b) => (
            <tr key={b.id}>
              <td>
                <a href="#" onClick={(e) => go(e, "ally:forum_b" + b.id)}>{b.name}</a>
                {b.unread > 0 && <b> ({b.unread})</b>} <span className="grey">{b.kind !== "PUBLIC" ? "(" + KIND_LABEL[b.kind] + ")" : ""}</span>
              </td>
              <td>{b.threads}</td>
              <td>{b.lastAt ? `${b.lastAuthor ?? ""} ${when(b.lastAt)}` : "-"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <a href="#" onClick={async (e) => { e.preventDefault(); const r = await run(() => api.forumMarkRead()); if (r && r !== true) setData(r); }}>
          » Mark all forums as read
        </a>
      </p>
    </div>
  );
}

function NewThread({ boardId, onDone }) {
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [poll, setPoll] = useState(false);
  const [question, setQuestion] = useState("");
  const [opts, setOpts] = useState(["", ""]);
  const [days, setDays] = useState(7);
  const [show, setShow] = useState(false);
  const [error, setError] = useState(null);
  const area = useRef(null);
  const submit = async (e) => {
    e.preventDefault();
    try {
      const r = await api.forumNewThread(boardId, {
        title, body, poll: poll ? { question, options: opts, days: Number(days), showResults: show } : null,
      });
      onDone(r.id);
    } catch (err) {
      setError(err.message);
    }
  };
  return (
    <form onSubmit={submit}>
      <Err error={error} />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th colSpan="2">{poll ? "Create New Poll" : "Create new Thread"}</th>
          </tr>
          <tr>
            <td width="80">title:</td>
            <td><input type="text" value={title} maxLength="120" size="60" onChange={(e) => setTitle(e.target.value)} /></td>
          </tr>
          <tr>
            <td colSpan="2"><BBToolbar area={area} onChange={setBody} /></td>
          </tr>
          <tr>
            <td colSpan="2">
              <textarea ref={area} rows="10" style={{ width: "100%" }} value={body} onChange={(e) => setBody(e.target.value)} />
            </td>
          </tr>
          <tr>
            <td colSpan="2">
              <label><input type="checkbox" checked={poll} onChange={(e) => setPoll(e.target.checked)} /> Poll</label>
            </td>
          </tr>
          {poll && (
            <>
              <tr>
                <td>question:</td>
                <td><input type="text" value={question} maxLength="200" size="60" onChange={(e) => setQuestion(e.target.value)} /></td>
              </tr>
              {opts.map((o, i) => (
                <tr key={i}>
                  <td>option {i + 1}:</td>
                  <td><input type="text" value={o} maxLength="120" size="40" onChange={(e) => setOpts(opts.map((x, j) => (j === i ? e.target.value : x)))} /></td>
                </tr>
              ))}
              <tr>
                <td />
                <td>
                  {opts.length < 10 && <a href="#" onClick={(e) => { e.preventDefault(); setOpts([...opts, ""]); }}>» add</a>}
                </td>
              </tr>
              <tr>
                <td>End the poll after</td>
                <td>
                  <input type="number" min="0" max="365" style={{ width: 50 }} value={days} onChange={(e) => setDays(e.target.value)} /> days (0 = never){" "}
                  <label><input type="checkbox" checked={show} onChange={(e) => setShow(e.target.checked)} /> Show results before voting</label>
                </td>
              </tr>
            </>
          )}
          <tr>
            <td colSpan="2"><input type="submit" className="btn" value="Create" /></td>
          </tr>
        </tbody>
      </table>
    </form>
  );
}

export function BoardView({ id, go }) {
  const [page, setPage] = useState(0);
  const forum = useForum(() => api.forum(), []);
  const { data, error, reload } = useForum(() => api.forumBoard(id, page), [id, page]);
  const [creating, setCreating] = useState(false);
  if (!data) return <Err error={error} />;
  return (
    <div id="ally_content">
      <Err error={error} />
      {forum.data && <BoardTabs boards={forum.data.boards} current={Number(id)} go={go} canAdmin={forum.data.canAdmin} />}
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>Topics</th>
            <th>Replies</th>
            <th>Author</th>
            <th>Last post</th>
          </tr>
          {data.threads.map((t) => (
            <tr key={t.id}>
              <td>
                {t.unread && <b>* </b>}
                {t.pinned && <span title="pinned">[pinned] </span>}
                <a href="#" onClick={(e) => go(e, "ally:forum_t" + t.id)}>{t.title}</a>
                {t.poll && <span> (Poll)</span>}
                {t.closed && <span className="grey"> (closed)</span>}
              </td>
              <td>{t.replies}</td>
              <td>{t.author}</td>
              <td>{t.lastAuthor} {when(t.lastAt)}</td>
            </tr>
          ))}
          {data.threads.length === 0 && (
            <tr><td colSpan="4">There are no topics in this forum yet.</td></tr>
          )}
        </tbody>
      </table>
      {data.pages > 1 && (
        <p>
          {Array.from({ length: data.pages }, (_, i) => i).map((i) =>
            i === data.page ? <strong key={i}>&gt;{i + 1}&lt; </strong> : <a key={i} href="#" onClick={(e) => { e.preventDefault(); setPage(i); }}>[{i + 1}] </a>,
          )}
        </p>
      )}
      <p>
        <a href="#" onClick={(e) => { e.preventDefault(); setCreating(!creating); }}>» Create new Thread</a>{" "}
        <a href="#" onClick={async (e) => { e.preventDefault(); await api.forumMarkRead(id); reload(); }}>» Mark forum as read</a>
      </p>
      {creating && <NewThread boardId={id} onDone={(tid) => go(null, "ally:forum_t" + tid)} />}
    </div>
  );
}

function PollBox({ poll, onVote }) {
  const total = poll.total;
  return (
    <table className="vis" width="60%">
      <tbody>
        <tr>
          <th colSpan="3">{poll.question}</th>
        </tr>
        {poll.options.map((o) => (
          <tr key={o.id}>
            <td>
              {poll.canVote ? <a href="#" onClick={(e) => { e.preventDefault(); onVote(o.id); }}>{o.text}</a> : o.text}
              {poll.myVote === o.id && <b> ✓</b>}
            </td>
            <td width="80">{o.votes >= 0 ? o.votes : "?"}</td>
            <td width="200">
              {o.votes >= 0 && total > 0 && (
                <div style={{ background: "#804000", height: 8, width: Math.round((o.votes / total) * 100) + "%" }} />
              )}
            </td>
          </tr>
        ))}
        <tr>
          <td colSpan="3" className="small grey">
            {poll.ended ? "The poll has ended." : poll.endsAt ? "Ends " + when(poll.endsAt) : "No end date."}
          </td>
        </tr>
      </tbody>
    </table>
  );
}

export function ThreadView({ id, go }) {
  const [page, setPage] = useState(-1);
  const { data, setData, error, run, reload } = useForum(() => api.forumThread(id, page), [id, page]);
  const [reply, setReply] = useState("");
  const [editing, setEditing] = useState(null); // { id, body }
  const area = useRef(null);
  const editArea = useRef(null);
  if (!data) return <Err error={error} />;
  const apply = async (fn) => {
    const r = await run(fn);
    if (r && r !== true && r.id) setData(r);
    return r;
  };
  return (
    <div id="ally_content">
      <Err error={error} />
      <h3>{data.title}{data.closed ? " (closed)" : ""}</h3>
      <p>
        <a href="#" onClick={(e) => go(e, "ally:forum_b" + data.boardId)}>» {data.boardName}</a>
      </p>
      {data.poll && <PollBox poll={data.poll} onVote={(oid) => apply(() => api.forumVote(id, oid))} />}
      <div style={{ overflow: "hidden" }}>
        {data.posts.map((p) => (
          <div key={p.id} className={"post" + (p.own ? " own" : "")}>
            <div className="igmline">
              <span className="postheader_left">
                {" "}<PlayerLink name={p.author} go={go} />
              </span>
              <span className="postheader_right">
                {when(p.at)}
                {p.editedBy && <span className="grey"> (edited by {p.editedBy})</span>}
                {p.canEdit && (
                  <>
                    {" "}
                    <a href="#" onClick={(e) => { e.preventDefault(); setEditing({ id: p.id, body: p.body }); }}>edit</a>{" "}
                    <a href="#" onClick={async (e) => {
                      e.preventDefault();
                      if (!window.confirm("Are you sure, you want to delete this thread?".replace("thread", "post"))) return;
                      const r = await run(() => api.forumDeletePost(p.id));
                      if (r && r.threadDeleted) go(null, "ally:forum_b" + data.boardId);
                      else reload();
                    }}>delete</a>
                  </>
                )}
              </span>
            </div>
            <div style={{ padding: 6 }}>
              {editing?.id === p.id ? (
                <form onSubmit={async (e) => { e.preventDefault(); if (await run(() => api.forumEditPost(p.id, editing.body))) { setEditing(null); reload(); } }}>
                  <BBToolbar area={editArea} onChange={(v) => setEditing({ ...editing, body: v })} />
                  <textarea ref={editArea} rows="8" style={{ width: "100%" }} value={editing.body} onChange={(e) => setEditing({ ...editing, body: e.target.value })} />
                  <input type="submit" className="btn" value="Save" /> <a href="#" onClick={(e) => { e.preventDefault(); setEditing(null); }}>Cancellation</a>
                </form>
              ) : (
                <BB text={p.body} go={go} />
              )}
            </div>
          </div>
        ))}
      </div>
      {data.pages > 1 && (
        <p>
          {Array.from({ length: data.pages }, (_, i) => i).map((i) =>
            i === data.page ? <strong key={i}>&gt;{i + 1}&lt; </strong> : <a key={i} href="#" onClick={(e) => { e.preventDefault(); setPage(i); }}>[{i + 1}] </a>,
          )}
        </p>
      )}
      {(!data.closed || data.canModerate) && (
        <form onSubmit={async (e) => { e.preventDefault(); if (await apply(() => api.forumReply(id, reply))) setReply(""); }} style={{ clear: "both" }}>
          <table className="vis" width="100%">
            <tbody>
              <tr><th>Reply</th></tr>
              <tr><td><BBToolbar area={area} onChange={setReply} /></td></tr>
              <tr><td><textarea ref={area} rows="6" style={{ width: "100%" }} value={reply} onChange={(e) => setReply(e.target.value)} /></td></tr>
              <tr><td><input type="submit" className="btn answer" value="Reply" /></td></tr>
            </tbody>
          </table>
        </form>
      )}
      {data.canModerate && (
        <p style={{ clear: "both" }}>
          <a href="#" onClick={(e) => { e.preventDefault(); apply(() => api.forumModerate(id, data.closed ? "open" : "close")); }}>» {data.closed ? "open" : "close"}</a>{" "}
          <a href="#" onClick={(e) => { e.preventDefault(); apply(() => api.forumModerate(id, data.pinned ? "unpin" : "pin")); }}>» {data.pinned ? "unpin" : "pin"}</a>{" "}
          <select value="" onChange={(e) => e.target.value && apply(() => api.forumMove(id, Number(e.target.value)))}>
            <option value="">move to…</option>
            {data.boards.filter((b) => b.id !== data.boardId).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
          </select>{" "}
          <a href="#" onClick={async (e) => {
            e.preventDefault();
            if (window.confirm("Are you sure, you want to delete this thread?") && (await run(() => api.forumDeleteThread(id)))) go(null, "ally:forum_b" + data.boardId);
          }}>» delete</a>
        </p>
      )}
    </div>
  );
}

export function ForumAdmin({ go }) {
  const { data, setData, error, run } = useForum(() => api.forum(), []);
  const [name, setName] = useState("");
  const [kind, setKind] = useState("PUBLIC");
  const [edits, setEdits] = useState({});
  if (!data) return <Err error={error} />;
  const swap = async (fn) => {
    const r = await run(fn);
    if (r && r !== true) { setData(r); setEdits({}); }
  };
  return (
    <div id="ally_content">
      <Err error={error} />
      <BoardTabs boards={data.boards} go={go} canAdmin />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>Forumname</th>
            <th>access</th>
            <th>action</th>
          </tr>
          {data.boards.map((b) => {
            const e = edits[b.id] ?? { name: b.name, kind: b.kind };
            return (
              <tr key={b.id}>
                <td><input type="text" value={e.name} maxLength="60" onChange={(ev) => setEdits({ ...edits, [b.id]: { ...e, name: ev.target.value } })} /></td>
                <td>
                  <select value={e.kind} onChange={(ev) => setEdits({ ...edits, [b.id]: { ...e, kind: ev.target.value } })}>
                    {Object.entries(KIND_LABEL).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
                  </select>
                </td>
                <td>
                  <a href="#" onClick={(ev) => { ev.preventDefault(); swap(() => api.forumEditBoard(b.id, e.name, e.kind)); }}>modify</a>{" "}
                  <a href="#" onClick={(ev) => { ev.preventDefault(); if (window.confirm("Delete this forum with all its topics?")) swap(() => api.forumDeleteBoard(b.id)); }}>delete</a>
                </td>
              </tr>
            );
          })}
          <tr>
            <td><input type="text" value={name} maxLength="60" placeholder="Create new forum" onChange={(ev) => setName(ev.target.value)} /></td>
            <td>
              <select value={kind} onChange={(ev) => setKind(ev.target.value)}>
                {Object.entries(KIND_LABEL).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
              </select>
            </td>
            <td><a href="#" onClick={(ev) => { ev.preventDefault(); swap(() => api.forumCreateBoard(name, kind)).then(() => setName("")); }}>add</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}
