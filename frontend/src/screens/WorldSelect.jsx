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

const stop = (e) => e.preventDefault();

let noticeSeq = 0;

export function StartPage({ account = null, worlds = [], error = null, onLogin, onRegister, onLogout, onPlay, onJoin, onAdmin }) {
  const [menuActive, setMenuActive] = useState(false);
  const [topFaded, setTopFaded] = useState(false);
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

  // StartPage.ui.initTopLink: "#up" fades in once scrolled past 100px
  useEffect(() => {
    const onScroll = () => setTopFaded((window.scrollY || document.documentElement.scrollTop || 0) > 100);
    window.addEventListener("scroll", onScroll);
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  // StartPage.spin: konami code adds .spin to .l-header-content
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

  const toTop = (e) => {
    e.preventDefault();
    window.scrollTo(0, 0);
  };
  const logout = (e) => {
    e.preventDefault();
    if (onLogout) onLogout();
  };
  const home = (e) => {
    e.preventDefault();
    setJoinId(null);
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
      {/* INNOGAMES PORTALBAR */}
      <div id="pbar">
        <div className="pb-outer pb-outer-zz">
          <div className="pb-inner"></div>
        </div>
        <div className="pb-tab"></div>
      </div>
      <div className="l-wrapper">
        {/* HEADER */}
        <header>
          <div id="top"></div>
          <nav className="l-header">
            <div className="l-constrained l-constrained-alt">
              <div className={"menu-mobile" + (menuActive ? " active" : "")} onClick={() => setMenuActive((a) => !a)}>
                {" "}
                <span></span>
                <div className="menu-text">Menu</div>
              </div>
              <ul className={"menu-primary l-clearfix l-inline-list" + (menuActive ? " active" : "")}>
                <li>
                  <a id="headerlink-home" className="is-active " href="/" onClick={home}>
                    tribalwars - nilz clone
                  </a>{" "}
                </li>
                {account?.admin && (
                  <li>
                    <a id="headerlink-admin" className=" " href="/admin" onClick={(e) => { e.preventDefault(); if (onAdmin) onAdmin(); }}>
                      Admin Panel
                    </a>{" "}
                  </li>
                )}
                {account && (
                  <li>
                    <a id="headerlink-logout" className=" " href="/page/logout" onClick={logout}>
                      Logout
                    </a>{" "}
                  </li>
                )}
              </ul>
              {!account && <LoginForm onLogin={onLogin || (async () => {})} onNotice={notify} />}
            </div>
          </nav>
          <div className={"l-header-content" + (spin ? " spin" : "")}>
            <div className="l-constrained l-constrained-reg l-clearfix">
              {/* REGISTER */}
              {account ? (
                <WorldLists account={account} worlds={worlds} onPlay={choose} onChoose={choose} onLogout={onLogout} />
              ) : (
                <RegisterBox onRegister={onRegister || (async () => {})} onNotice={notify} />
              )}
              {!account && (
                <div className="aside">
                  <div className="slider-primary">
                    <div className="container">
                      <div className="container-inner">
                        <div className="inner-content">
                          <ul id="page2" className="rslides">
                            <li>
                              <div
                                style={{
                                  width: "100%",
                                  height: 270,
                                  display: "flex",
                                  alignItems: "center",
                                  justifyContent: "center",
                                  textAlign: "center",
                                  background: "url(/graphic/start/inner-middle.jpg)",
                                  color: "#3b2308",
                                  fontFamily: "CrimsonText, Georgia, serif",
                                  fontSize: 34,
                                  lineHeight: 1.15,
                                  fontWeight: "bold",
                                }}
                              >
                                <span>tribalwars<br />- nilz clone -</span>
                              </div>
                            </li>
                          </ul>
                        </div>
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
                </div>
              )}
            </div>
          </div>
        </header>
        {/* MAIN CONTENT */}
        <section className="l-content">
          <div className="content-drop"></div>
          <div className="ornament ornament-top-right"></div>
          <div className="ornament ornament-top-left"></div>
          <div className="ornament ornament-bottom-left"></div>
          <div className="ornament ornament-bottom-right"></div>
          <div className="l-section-divider"></div>
          <div className="l-constrained">
            <div className="l-clearfix">
              <div id="pagination3" className="pagination l-center l-clearfix"></div>
            </div>
          </div>
        </section>
        {/* FOOTER */}
        <div className="l-footer">
          <footer className="l-constrained">
            <div className="l-clearfix">
              <div className="links-footer l-clearfix l-center-block">
                {" "}
                <a>tribalwars - nilz clone</a>
              </div>
            </div>
          </footer>
          <a id="up" className={"one " + (topFaded ? "fade-in" : "transparent")} href="#top" onClick={toTop}>
            ? Go to the top
          </a>
        </div>
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
