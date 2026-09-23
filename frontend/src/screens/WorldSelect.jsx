// The account/start experience (login, register, world list, join), in React:
//   logged out : the home page (templates/layouts/index.php + controllers/index): header login form, the
//                "Register now!" box with the original's inline validation UX (js/start.js StartPage.register)
//   logged in  : #world-select dialog with "Current worlds" / "Available worlds"
//   join       : create_account.php, "Do you want to join <World>?" + Join button
// The home views are full pages with their own stylesheet (/start.css) and body id/class, so while they are mounted
// the app's page-level chrome (game.css / overview.css, body id/class, <title>) is swapped for the start page's
// and restored exactly on unmount; the join view is a game.css page of its own (see start/chrome.js).
//
//   <StartPage account={null | {id, username}} worlds={[{id, name, speed, joined}]} error={string?}
//              onLogin(username, password, remember)  async, throws Error(message)
//              onRegister(username, password)         async, throws Error(message)
//              onLogout()                             sync
//              onPlay(world)                          a Current world was chosen
//              onJoin(world)                          async, throws Error(message); App then plays the world />
// Behaviour taken from js/start.js (StartPage.ui / auth / register / spin): mobile menu toggle, "go to the top"
// link fade-in, konami "spin", "Working..." buttons, notice box.
import { useCallback, useEffect, useLayoutEffect, useState } from "react";
import { enterJoinPage, enterStartPage } from "./start/chrome";
import { LoginForm } from "./start/LoginForm";
import { RegisterBox } from "./start/RegisterBox";
import { WorldLists } from "./start/WorldLists";
import { JoinPage } from "./start/JoinPage";
import { NoticeBox } from "./start/NoticeBox";

const KONAMI = "38384040373937396665";

let noticeSeq = 0;

export function StartPage({ account = null, worlds = [], error = null, onLogin, onRegister, onLogout, onPlay, onJoin, onAdmin }) {
  const [spin, setSpin] = useState(false);
  const [joinId, setJoinId] = useState(null);
  const [notice, setNotice] = useState(null);

  // a half-finished join belongs to the account that started it (logout / another login forgets it)
  const joinWorld =
    account && joinId && joinId.acc === account.id
      ? (worlds || []).find((w) => w.id === joinId.id && !w.joined)
      : null;
  const mode = joinWorld ? "join" : "start";

  useLayoutEffect(() => (mode === "join" ? enterJoinPage() : enterStartPage()), [mode]);

  const notify = useCallback((message) => setNotice({ key: ++noticeSeq, message }), []);
  // errors handed in by the app (e.g. the worlds could not be loaded) go into the same notice box
  useEffect(() => { if (error) notify(error); }, [error, notify]);

  // StartPage.spin: konami code adds .spin to the page
  useEffect(() => {
    let input = "";
    const onKey = (e) => {
      input += e.keyCode;
      if (input.length > KONAMI.length) input = input.substring(input.length - KONAMI.length);
      if (input === KONAMI) setSpin(true);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  const logout = (e) => {
    e.preventDefault();
    if (onLogout) onLogout();
  };
  const choose = (w) => {
    if (w.joined) {
      if (onPlay) onPlay(w);
    } else {
      setJoinId({ acc: account.id, id: w.id });
    }
  };

  if (joinWorld) {
    return (
      <>
        <JoinPage
          key={joinWorld.id}
          world={joinWorld}
          onJoin={onJoin || (async () => {})}
          onBack={() => setJoinId(null)}
        />
        <NoticeBox notice={notice} />
      </>
    );
  }

  return (
    <>
      <noscript>
        <p>Please enable JavaScript before using this website</p>
      </noscript>
      <div className={"tw2-page" + (spin ? " spin" : "")}>
        <div className="tw2-topbar">
          <span className="tw2-brand">tribalwars - nilz clone</span>
          <div className="tw2-topbar-links">
            {account?.admin && (
              <a id="headerlink-admin" href="/admin" onClick={(e) => { e.preventDefault(); if (onAdmin) onAdmin(); }}>
                Admin Panel
              </a>
            )}
            {account && (
              <a id="headerlink-logout" href="/page/logout" onClick={logout}>
                Logout
              </a>
            )}
          </div>
        </div>

        <div className="tw2-banner">
          <h1>
            tribalwars
            <span className="sub">- nilz clone -</span>
          </h1>
        </div>

        <div className="tw2-content">
          <div className="paladin" />
          <div className="box-border red">
            <div className="inner">
              {account ? (
                <WorldLists account={account} worlds={worlds} onPlay={choose} onChoose={choose} onLogout={onLogout} />
              ) : (
                <div className="tw2-columns">
                  <div className="col">
                    <LoginForm onLogin={onLogin || (async () => {})} onNotice={notify} />
                  </div>
                  <div className="divider">
                    <span className="cap top" />
                    <span className="bar" />
                    <span className="cap bottom" />
                  </div>
                  <div className="col">
                    <RegisterBox onRegister={onRegister || (async () => {})} onNotice={notify} />
                  </div>
                </div>
              )}
            </div>
            <div className="top-left"></div>
            <div className="top-right"></div>
            <div className="middle-top"></div>
            <div className="middle-bottom"></div>
            <div className="middle-left"></div>
            <div className="middle-right"></div>
            <div className="bottom-left"></div>
            <div className="bottom-right"></div>
          </div>
        </div>

        <div className="tw2-footer">tribalwars - nilz clone</div>
      </div>
      <NoticeBox notice={notice} />
    </>
  );
}

// Old entry point (logged-in world list only): kept as a thin alias.
export function WorldSelect({ playerName = "Player", worlds = [], onSelect, onLogout }) {
  return (
    <StartPage
      account={{ id: 0, username: playerName }}
      worlds={worlds.map((w) => ({ ...w, joined: true }))}
      onPlay={onSelect}
      onLogout={onLogout}
    />
  );
}

export default StartPage;
