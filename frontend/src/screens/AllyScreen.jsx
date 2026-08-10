/* screen=ally — the tribe pages. Without a tribe every mode shows the "invitations + establish tribe" page
   (templates ally/index.php); with one, header.php + the page of the mode:
     overview allyindex.php · profile profile.php · members members.php (+ members_rights.php via "Rights and Titles")
     invite invite.php ("Recruitment") · properties props.php · contracts diplomaty.php ("Diplomacy")
   Actions go to /api/tribe/*; every answer is the viewer's fresh tribe state. */
import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { PreErrorBox } from "../lib/ui";
import { NoTribe } from "./ally/NoTribe";
import { AllyHeader, allyModes } from "./ally/Header";
import { Overview } from "./ally/Overview";
import { TribeProfile } from "./ally/Profile";
import { Members } from "./ally/Members";
import { Rights } from "./ally/Rights";
import { Invite } from "./ally/Invite";
import { Properties } from "./ally/Properties";
import { Diplomacy } from "./ally/Diplomacy";
import { Wars } from "./ally/Wars";
import { BoardView, ForumAdmin, ForumIndex, ThreadView } from "./ally/Forum";

export function AllyScreen({ village, mode, go }) {
  const [state, setState] = useState(null);
  const [error, setError] = useState(null);
  const [info, setInfo] = useState(null);
  const [start, setStart] = useState(0);
  const [rightsFor, setRightsFor] = useState(null);

  const reload = useCallback(() => api.tribe(start).then(setState).catch((e) => setError(e.message)), [start]);
  useEffect(() => {
    reload();
    const id = setInterval(reload, 10000);
    return () => clearInterval(id);
  }, [reload]);

  // run an action: its answer is the new state; a refused rule shows its message
  const run = async (fn) => {
    setError(null);
    setInfo(null);
    try {
      setState(await fn());
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    }
  };

  if (!state) return error ? <div className="error_box">{error}</div> : <p>Loading…</p>;
  if (!state.tribe) return <NoTribe state={state} run={run} go={go} error={error} village={village} />;

  const { tribe, me } = state;
  const allowed = allyModes(me).map(([m]) => m);
  // forum sub-pages: forum, forum_b<board>, forum_t<thread>, forum_admin
  const forumPage = mode?.startsWith("forum") ? mode : null;
  const current = forumPage ? "forum" : allowed.includes(mode) ? mode : "overview";

  let body;
  if (rightsFor != null && current === "members") {
    body = <Rights state={state} memberId={rightsFor} run={run} back={() => setRightsFor(null)} />;
  } else if (forumPage) {
    body = forumPage.startsWith("forum_b") ? <BoardView key={forumPage} id={forumPage.slice(7)} go={go} />
      : forumPage.startsWith("forum_t") ? <ThreadView key={forumPage} id={forumPage.slice(7)} go={go} />
      : forumPage === "forum_admin" ? <ForumAdmin go={go} />
      : <ForumIndex go={go} />;
  } else if (current === "wars") {
    body = <Wars own go={go} />;
  } else if (current === "profile") {
    body = <TribeProfile tribe={tribe} description={tribe.description} members={state.members} own go={go} homepage={tribe.homepage} irc={tribe.irc} />;
  } else if (current === "members") {
    body = <Members state={state} run={run} go={go} onRights={setRightsFor} setError={setError} />;
  } else if (current === "invite") {
    body = <Invite state={state} run={run} go={go} />;
  } else if (current === "contracts") {
    body = <Diplomacy state={state} run={run} go={go} />;
  } else if (current === "properties") {
    body = <Properties state={state} run={run} go={go} setInfo={setInfo} />;
  } else {
    body = <Overview state={state} run={run} go={go} start={start} setStart={setStart} village={village} />;
  }

  return (
    <>
      {error && <PreErrorBox text={error} />}
      {info && <PreErrorBox text={info} />}
      <AllyHeader tribe={tribe} me={me} mode={current} go={(e, target) => { setRightsFor(null); go(e, target); }} />
      {body}
    </>
  );
}
