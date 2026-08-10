import { useEffect, useState, useCallback, useRef } from "react";
import { api, getStoredWorld, setWorldId, getToken, setToken, getVillageId, setVillageId } from "./api";
import { fmtClock, fmtDate, villagePoints, setPlayerName, PLAYER_NAME } from "./lib/data";
import { lsGet, lsSet } from "./lib/overview";
import { useNow, MissingScreen, CLASS_NAMES, PreContentContext } from "./lib/ui";
import { TopMenu } from "./chrome/TopMenu";
import { HeaderInfo } from "./chrome/HeaderInfo";
import { syncFarmConfig } from "./lib/farm";
import { emitLive, openLive, runCoalesced, setConnected } from "./lib/live";
import { useGroups } from "./lib/groups";
import { MarketScreen } from "./screens/MarketScreen";
import { InfoCommandScreen, InfoVillageScreen } from "./screens/InfoScreens";
import { WorldSwitch } from "./chrome/WorldSwitch";
import { StartPage } from "./screens/WorldSelect";
import { AdminPage } from "./screens/admin/AdminPage";
import { OverviewScreen } from "./screens/OverviewScreen";
import { BuildingScreen } from "./screens/BuildingScreens";
import { TrainScreen, TrainBuildingScreen, TRAIN_BUILDING_IDS } from "./screens/TrainScreen";
import { PlaceScreen } from "./screens/PlaceScreen";
import { MapScreen } from "./screens/MapScreen";
import { ReportsScreen } from "./screens/ReportsScreen";
import { RankingScreen, rankPlayers } from "./screens/RankingScreen";
import { OverviewVillagesScreen } from "./screens/OverviewVillagesScreen";
import { AllyScreen } from "./screens/AllyScreen";
import { InfoAllyScreen } from "./screens/ally/Profile";
import { ProfileScreen } from "./screens/ProfileScreen";
import { StatsScreen } from "./screens/StatsScreen";
import { SettingsScreen } from "./screens/SettingsScreen";
import { MailScreen } from "./screens/MailScreen";
import { AchievementsScreen } from "./screens/achievements/AchievementsScreen";
import { AchievementToasts } from "./screens/achievements/AchievementToasts";

/* Markup across src/ deliberately mirrors the original game's rendered HTML so
   the original public/game.css + public/overview.css apply unchanged. */

// screen keys of the original game that render a building page
const REAL_BUILDING_SCREENS = new Set([
  "main", "wood", "stone", "iron", "storage", "hide", "wall", "statue", "snob", "stable", "garage", "smith", "barracks",
]);

export default function App() {
  const [village, setVillage] = useState(null);
  const [villages, setVillages] = useState([]);
  const [reports, setReports] = useState([]);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [info, setInfo] = useState(null);
  const [pre, setPre] = useState(null);
  const infoTimer = useRef(null);
  const [fetchedAt, setFetchedAt] = useState(() => Date.now());
  const [genTime, setGenTime] = useState(1);
  const [account, setAccount] = useState(null);
  const [authChecked, setAuthChecked] = useState(() => !getToken());
  const [worlds, setWorlds] = useState(null);
  const [worldId, setWorld] = useState(() => getStoredWorld());
  const [worldOpen, setWorldOpen] = useState(false);
  const seenKey = "twlan_seen_report:" + worldId;
  const [seenReport, setSeenReport] = useState(() => lsGet(seenKey, null));
  const [screen, setScreen] = useState(() => decodeURIComponent(window.location.hash.slice(1)) || "overview");
  const now = useNow();

  // Mobile layer (Android app pilot, not the desktop browser experience): the game's markup/CSS is deliberately
  // untouched 1:1 fidelity, and #main_layout's content never reflows (fixed "maincell" width:850 + 25px borders
  // each side = 900px). CSS `zoom` (unlike `transform: scale`) shrinks it without leaving blank space below, and
  // it's a no-op at normal desktop widths (>=900px), so the browser experience is unaffected.
  // #main_layout only exists once account/worldId/village have resolved (StartPage renders first with no
  // #main_layout at all), so the effect must re-run when those settle, not just once at mount - otherwise a
  // fresh load can miss it entirely (window never actually resizes after that, so it'd never get a second try).
  useEffect(() => {
    const NATURAL_WIDTH = 900;
    // Below this, the original's 9pt base font becomes hard to read; let the rest scroll horizontally
    // instead (html/body have no overflow-x:hidden) rather than shrinking further.
    const MIN_ZOOM = 0.6;
    const applyFitZoom = () => {
      const el = document.getElementById("main_layout");
      if (el) el.style.zoom = Math.min(1, Math.max(MIN_ZOOM, window.innerWidth / NATURAL_WIDTH));
    };
    applyFitZoom();
    window.addEventListener("resize", applyFitZoom);
    return () => window.removeEventListener("resize", applyFitZoom);
  }, [account, worldId, village]);

  // is the stored login token still valid?
  useEffect(() => {
    if (!getToken()) return;
    api
      .me()
      .then(setAccount)
      .catch(() => setToken(null))
      .finally(() => setAuthChecked(true));
  }, []);

  // the list of worlds (normal + speed worlds; `joined` once logged in); forget a stored world you have not joined
  useEffect(() => {
    if (!authChecked) return;
    api
      .getWorlds()
      .then((list) => {
        setWorlds(list);
        setWorld((cur) => {
          if (cur != null && account && !list.some((w) => w.id === cur && w.joined)) {
            setWorldId(null);
            return null;
          }
          return cur;
        });
      })
      .catch((e) => setError(e.message));
  }, [authChecked, account]);

  const chooseWorld = (w) => {
    setWorldId(w?.id ?? null);
    setWorld(w?.id ?? null);
    setWorldOpen(false);
    setVillage(null);
    setVillages([]);
    setReports([]);
    setError(null);
    setSeenReport(w ? lsGet("twlan_seen_report:" + w.id, null) : null);
    window.history.replaceState(null, "", "#overview");
    setScreen("overview");
  };

  const afterAuth = (login, remember) => {
    setToken(login.token, remember);
    setError(null);
    setAccount({ id: login.id, username: login.username, admin: !!login.admin });
  };
  const onLogin = async (username, password, remember = true) => afterAuth(await api.login(username, password), remember);
  const onRegister = async (username, password) => afterAuth(await api.register(username, password), true);
  const onLogout = async () => {
    await api.logout();
    setToken(null);
    setAccount(null);
    chooseWorld(null);
  };
  const onJoin = async (w) => {
    await api.joinWorld(w.id);
    chooseWorld(w);
  };

  const villageGroups = useGroups(worldId, !!account && village != null);

  // What the server last sent for the lists: an identical answer keeps the old array, so the map and the lists do not redo their work.
  const sent = useRef({ villages: null, reports: null });
  const [live, setLive] = useState(false); // the push stream is connected (then polling only backs it up)

  const failed = useCallback((e) => {
    if (e.status === 401) {
      setToken(null);
      setAccount(null);
    } else if (e.status === 409) {
      setWorldId(null);
      setWorld(null);
      setVillage(null);
    } else {
      setError(e.message);
    }
  }, []);

  // the three things the game keeps fresh; each can be refetched on its own when the server says only that one changed
  const refreshVillage = useCallback(() => runCoalesced("village", async () => {
    if (worldId == null) return;
    try {
      const t0 = performance.now();
      const v = await api.getVillage();
      // the backend falls back to the first village when the remembered one is not (or no longer) the player's own
      if (v.id !== getVillageId()) setVillageId(v.id);
      setVillage(v);
      setFetchedAt(Date.now());
      setGenTime(Math.max(0.1, Math.round((performance.now() - t0) * 10) / 10));
    } catch (e) {
      failed(e);
    }
  }), [worldId, failed]);
  const refreshMap = useCallback(() => runCoalesced("map", async () => {
    if (worldId == null) return;
    try {
      const vs = await api.getVillages();
      const json = JSON.stringify(vs);
      if (json !== sent.current.villages) {
        sent.current.villages = json;
        setVillages(vs);
      }
    } catch (e) {
      failed(e);
    }
  }), [worldId, failed]);
  const refreshReports = useCallback(() => runCoalesced("reports", async () => {
    if (worldId == null) return;
    try {
      const rs = await api.getReports();
      const json = JSON.stringify(rs);
      if (json !== sent.current.reports) {
        sent.current.reports = json;
        setReports(rs);
      }
    } catch (e) {
      failed(e);
    }
  }), [worldId, failed]);
  const refresh = useCallback(() => Promise.all([refreshVillage(), refreshMap(), refreshReports()]), [refreshVillage, refreshMap, refreshReports]);

  // the push stream: small "something changed" events, each refetching only what it names
  useEffect(() => {
    if (!account || worldId == null) return undefined;
    const close = openLive((ev) => {
      if (ev.event === "hello") refresh();
      else if (ev.event === "village") refreshVillage();
      else if (ev.event === "reports") refreshReports();
      else if (ev.event === "map") refreshMap();
      else if (ev.event === "tribe" || ev.event === "mail") refreshVillage(); // (the menu's new-forum-post / new-mail markers)
      emitLive(ev.event);
    }, (up) => {
      setLive(up);
      setConnected(up);
    });
    return () => {
      close();
      setLive(false);
      setConnected(false);
    };
  }, [account, worldId, refresh, refreshVillage, refreshReports, refreshMap]);

  // the farm assistant's templates/filters follow the player between browsers
  useEffect(() => {
    if (account && worldId != null) syncFarmConfig(worldId);
  }, [account, worldId]);

  // polling: every 2.5 s on its own; while the stream is up only a slow safety net for anything an event missed
  useEffect(() => {
    sent.current = { villages: null, reports: null };
    refresh();
  }, [refresh, worldId]);
  useEffect(() => {
    const id = setInterval(refresh, live ? 30000 : 2500);
    return () => clearInterval(id);
  }, [refresh, live]);

  // like the original, a finished building / recruit / research / arrival shows up the moment its timer hits zero:
  // refresh right after the next deadline instead of waiting for the poll (retry shortly while the server is
  // still applying it; deadlines that are more than 10 s old are left to the poll)
  useEffect(() => {
    if (!village) return;
    const now = Date.now();
    const times = [
      ...(village.buildQueue ?? []), ...(village.trainQueue ?? []), ...(village.researchQueue ?? []),
      ...(village.outgoingMovements ?? []).map((m) => ({ completesAt: m.arrivesAt })),
      ...(village.incomingMovements ?? []).map((m) => ({ completesAt: m.arrivesAt })),
    ].map((q) => (q.completesAt ? new Date(q.completesAt).getTime() : NaN)).filter((t) => t > now - 10000);
    if (times.length === 0) return;
    const delay = Math.max(400, Math.min(...times) - now + 300);
    const id = setTimeout(refreshVillage, Math.min(delay, 2500));
    return () => clearTimeout(id);
  }, [village, refreshVillage]);

  const wrap = (fn) => async (...args) => {
    setBusy(true);
    setError(null);
    try {
      await fn(...args);
      await refresh();
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    } finally {
      setBusy(false);
    }
  };

  // the arrows next to the village name (and the a/d hotkeys): previous / next of the player's own villages
  const switchVillage = wrap(async (dir) => {
    const mine = villageGroups.inGroup(village?.myVillages ?? []);
    if (mine.length < 2) return;
    const at = Math.max(0, mine.findIndex((v) => v.id === village.id));
    const next = mine[(at + dir + mine.length) % mine.length];
    showInfo("Switching village...");
    setVillageId(next.id);
  });
  const selectVillage = (id) => {
    setVillageId(id);
    refresh();
  };

  const handleBuild = wrap((type) => api.build(type));
  const handleCancelBuild = wrap(async (q) => {
    await api.cancelBuild(q.id);
    showInfo("Construction order cancelled.");
  });
  const handleFinishBuild = wrap(async (q) => {
    await api.finishBuild(q.id);
    showInfo("Construction completed.");
  });
  const handleTrain = wrap((type, count) => api.train(type, count));
  const handleCancelTrain = wrap(async (e) => {
    await api.cancelTrain(e.id);
    showInfo("Recruitment order cancelled.");
  });
  const handleRenamePaladin = wrap((name) => api.renamePaladin(name));
  const handleResearch = wrap(async (type) => {
    await api.research(type);
    showInfo("Research started.");
  });
  const handleCancelResearch = wrap(async (q) => {
    await api.cancelResearch(q.id);
    showInfo("Research cancelled.");
  });
  const handleMintCoin = wrap(async (count = 1, villageId = null) => {
    await api.mintCoin(count, villageId);
    showInfo(count === 1 ? "Gold coin minted." : `${count} gold coins minted.`);
  });
  const handleRename = wrap((name) => api.renameVillage(name));
  const handleAttack = wrap((targetId, units) => api.attack(targetId, units));
  const handleSupport = wrap((targetId, units) => api.support(targetId, units));
  const handleWithdraw = wrap(async (armyId, units) => {
    await api.withdrawSupport(armyId, units);
    showInfo("The troops are on their way home.");
  });
  const handleSendBack = wrap(async (armyId) => {
    await api.sendBackSupport(armyId);
    showInfo("The troops are on their way home.");
  });

  // the original counts *new* reports in the menu badge: everything up to the newest report
  // that was on screen the last time the reports were opened counts as read
  const newestReport = reports.reduce((m, r) => Math.max(m, r.id ?? 0), 0);
  useEffect(() => {
    if (!village) return;
    if (seenReport == null || screen.split(":")[0] === "report") {
      if (seenReport !== newestReport) {
        setSeenReport(newestReport);
        lsSet(seenKey, newestReport);
      }
    }
  }, [village, screen, seenReport, newestReport, seenKey]);

  useEffect(() => {
    if (village) document.title = `${village.name} (${village.x}|${village.y}) - tribalwars - nilz clone - ${village.worldName ?? "Welt 1"}`;
  }, [village]);

  // UI.InfoMessage / UI.ErrorMessage of the original: a box that hides itself after ~2s
  const showInfo = useCallback((message, kind) => {
    clearTimeout(infoTimer.current);
    setInfo({ message, kind });
    infoTimer.current = setTimeout(() => setInfo(null), 2000);
  }, []);

  const go = (e, target) => {
    if (e) e.preventDefault();
    window.history.replaceState(null, "", "#" + encodeURIComponent(target).replace(/%3A/g, ":"));
    setScreen(target);
  };

  // HotKeys.js: "v" opens the village overview, "m" the map (ignored while typing)
  useEffect(() => {
    const onKey = (e) => {
      if (e.ctrlKey || e.altKey || e.metaKey || e.shiftKey) return;
      const tag = e.target?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || e.target?.isContentEditable) return;
      if (e.key === "a" || e.key === "d") {
        switchVillage(e.key === "a" ? -1 : 1);
      } else if (e.key === "v") {
        showInfo("Opening Village Overview...");
        go(null, "overview");
      } else if (e.key === "m") {
        showInfo("Opening map...");
        go(null, "map");
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  });

  if (!authChecked) return <div style={{ padding: 20 }}>{error ? `Error: ${error}` : "Loading…"}</div>;

  // the admin panel is its own full page (hash "#admin..."), only for admin accounts
  if (account?.admin && screen.split(":")[0].split("/")[0] === "admin") {
    return <AdminPage account={account} onExit={() => { window.history.replaceState(null, "", "#overview"); setScreen("overview"); }} />;
  }

  // not logged in / no world chosen yet: the original's start site (register, login, list of worlds)
  if (!account || worldId == null) {
    return (
      <StartPage
        account={account}
        worlds={worlds ?? []}
        error={error}
        onLogin={onLogin}
        onRegister={onRegister}
        onLogout={onLogout}
        onPlay={chooseWorld}
        onJoin={onJoin}
        onAdmin={() => go(null, "admin")}
      />
    );
  }

  if (!village) {
    return <div style={{ padding: 20 }}>{error ? `Error: ${error}` : "Loading…"}</div>;
  }

  if (village.playerName) setPlayerName(village.playerName);

  const [screenName, mode] = screen.split(":");
  let content;
  if (screenName === "map") content = <MapScreen village={village} villages={villages} go={go} focusAt={mode} onAttack={handleAttack} />;
  else if (screenName === "report") content = <ReportsScreen reports={reports} mode={mode ?? "all"} go={go} />;
  else if (screenName === "overview_villages")
    content = <OverviewVillagesScreen village={village} mode={mode ?? "combined"} go={go} onRename={handleRename} onSelectVillage={selectVillage} groups={villageGroups} />;
  else if (screenName === "ranking")
    content = <RankingScreen village={village} villages={villages} mode={mode ?? "player"} go={go} />;
  else if (screenName === "info_player" && mode === "awards") content = <AchievementsScreen />;
  else if (screenName === "info_player" && (mode === "stats_own" || mode?.startsWith("s_")))
    content = <StatsScreen name={mode.startsWith("s_") ? mode.slice(2) : undefined} go={go} />;
  else if (screenName === "info_player" && (!mode || mode.startsWith("p_")))
    content = <ProfileScreen name={mode ? mode.slice(2) : undefined} go={go} />;
  else if (screenName === "mail") content = <MailScreen mode={mode} go={go} />;
  else if (screenName === "info_player" && mode === "block") content = <SettingsScreen mode="block" go={go} />;
  else if (screenName === "settings") content = <SettingsScreen mode={mode} go={go} />;
  else if (screenName === "ally") content = <AllyScreen village={village} mode={mode} go={go} />;
  else if (screenName === "info_village" && mode) content = <InfoVillageScreen id={mode} village={village} villages={villages} go={go} />;
  else if (screenName === "info_command" && mode) content = <InfoCommandScreen id={mode} village={village} villages={villages} go={go} />;
  else if (screenName === "info_ally") content = <InfoAllyScreen id={mode} go={go} />;
  else if (screenName === "train") content = <TrainScreen village={village} onTrain={handleTrain} onCancelTrain={handleCancelTrain} busy={busy} go={go} />;
  else if (screenName === "market") content = <MarketScreen village={village} villages={villages} go={go} />;
  else if (screenName === "place")
    content = <PlaceScreen village={village} villages={villages} reports={reports} onAttack={handleAttack} onSupport={handleSupport} onWithdraw={handleWithdraw} onSendBack={handleSendBack} busy={busy} go={go} />;
  else if (screenName === "building")
    content = <BuildingScreen village={village} type={mode} onBuild={handleBuild} onCancelBuild={handleCancelBuild} onFinishBuild={handleFinishBuild} onRename={handleRename} onTrain={handleTrain} onCancelTrain={handleCancelTrain} onRenamePaladin={handleRenamePaladin} onMintCoin={handleMintCoin} onResearch={handleResearch} onCancelResearch={handleCancelResearch} busy={busy} go={go} />;
  else if (TRAIN_BUILDING_IDS.includes(screenName))
    content = <TrainBuildingScreen village={village} id={screenName} onTrain={handleTrain} onCancelTrain={handleCancelTrain} busy={busy} go={go} />;
  else if (REAL_BUILDING_SCREENS.has(screenName))
    content = <BuildingScreen village={village} type={screenName} onBuild={handleBuild} onCancelBuild={handleCancelBuild} onFinishBuild={handleFinishBuild} onRename={handleRename} onTrain={handleTrain} onCancelTrain={handleCancelTrain} onRenamePaladin={handleRenamePaladin} onMintCoin={handleMintCoin} onResearch={handleResearch} onCancelResearch={handleCancelResearch} busy={busy} go={go} />;
  else if (screenName === "overview")
    content = (
      <OverviewScreen
        village={village}
        villages={villages}
        go={go}
        onBuild={handleBuild}
        busy={busy}
        fetchedAt={fetchedAt}
        onCancelBuild={handleCancelBuild} onFinishBuild={handleFinishBuild} onRename={handleRename} onTrain={handleTrain} onCancelTrain={handleCancelTrain} onRenamePaladin={handleRenamePaladin} onMintCoin={handleMintCoin} onResearch={handleResearch} onCancelResearch={handleCancelResearch}
      />
    );
  else content = <MissingScreen name={CLASS_NAMES[screenName] ?? screenName} />;

  // the original answers screen=groups (and a missing screen) with an empty body: no chrome at all
  if (screenName === "groups") return null;

  const nowDate = new Date(now);
  const standing = rankPlayers(village, villages).find((p) => p.name === PLAYER_NAME) ?? { rank: 1, points: villagePoints(village) };
  const unreadReports = seenReport == null ? 0 : reports.filter((r) => (r.id ?? 0) > seenReport).length;

  return (
    <>
      <div className="top_bar">
        <div className="bg_left" />
        <div className="bg_right" />
      </div>
      <div className="top_shadow" />
      <div className="top_background" />
      <table id="main_layout" cellSpacing="0">
        <tbody>
          <tr style={{ height: 48 }}>
            <td className="topbar left" />
            <td className="topbar center">
              <TopMenu go={go} unreadReports={unreadReports} newForumPost={village?.newForumPost ?? false} newMails={village?.newMails ?? 0} points={standing.points} rank={standing.rank} />
            </td>
            <td className="topbar right" />
          </tr>
          <tr className="shadedBG">
            <td className="bg_left" id="SkyScraperAdCellLeft">
              <div className="bg_left" />
            </td>
            <td className="maincell" style={{ width: 850 }}>
              <br className="newStyleOnly" />
              <hr className="oldStyleOnly" />
              <HeaderInfo village={village} go={go} now={now} fetchedAt={fetchedAt} onSwitchVillage={switchVillage} onSelectVillage={selectVillage} groups={villageGroups} />
              <table align="center" id="contentContainer" width="100%">
                <tbody>
                  <tr>
                    <td>
                      <table className="content-border" width="100%" cellSpacing="0">
                        <tbody>
                          <tr>
                            <td id="inner-border">
                              <table className="main" align="left">
                                <tbody>
                                  <tr>
                                    <td id="content_value">
                                      {error && (
                                        <div className="error_box tw-error" onClick={() => setError(null)}>
                                          {error}
                                        </div>
                                      )}
                                      {pre && <div className="error_box"> {pre} </div>}
                                      <div id="content_point" />
                                      <PreContentContext.Provider value={setPre}>{content}</PreContentContext.Provider>
                                    </td>
                                  </tr>
                                </tbody>
                              </table>
                            </td>
                          </tr>
                        </tbody>
                      </table>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p className="server_info">
                {`generated in ${genTime} ms `}
                <b>|</b>
                {" Server time: "}
                <span id="serverTime">{fmtClock(nowDate)}</span>
                {" - "}
                <span id="serverDate">{fmtDate(nowDate)}</span>
              </p>
              <p />
            </td>
            <td className="bg_right" id="SkyScraperAdCell">
              <div className="bg_right" />
            </td>
          </tr>
          <tr>
            <td className="bg_leftborder" />
            <td />
            <td className="bg_rightborder" />
          </tr>
          <tr className="newStyleOnly">
            <td className="bg_bottomleft">&nbsp;</td>
            <td className="bg_bottomcenter">&nbsp;</td>
            <td className="bg_bottomright">&nbsp;</td>
          </tr>
        </tbody>
      </table>
      <AchievementToasts enabled go={go} />
      <div id="footer">
        <div id="footer_logo" />
        <div id="linkContainer">
          <div id="footer_left">
            <a href="#" onClick={(e) => e.preventDefault()}>tribalwars - nilz clone</a>
            {"\u00a0-\u00a0"}
            <a href="#" onClick={(e) => go(e, "settings:ticket")}>Support</a>
            {"\u00a0-\u00a0"}
            <a href="#" onClick={(e) => go(e, "buddies")}>Friends</a>
            {account?.admin && (
              <>
                {"\u00a0-\u00a0"}
                <a href="#" onClick={(e) => go(e, "admin")}>Admin</a>
              </>
            )}
            {"\u00a0-\u00a0"}
            <a
              href="#"
              className="evt-world-selection-toggle"
              onClick={(e) => {
                e.preventDefault();
                setWorldOpen((o) => !o);
              }}
            >
              Worlds
            </a>
            {"\u00a0-\u00a0"}
            <a
              href="#"
              onClick={(e) => {
                e.preventDefault();
                onLogout();
              }}
            >
              Log out
            </a>
          </div>
        </div>
      </div>
      <WorldSwitch
        open={worldOpen}
        worlds={worlds ?? []}
        currentId={worldId}
        onClose={() => setWorldOpen(false)}
        onSelect={chooseWorld}
      />
      {info && (
        <div className={"autoHideBox" + (info.kind ? " " + info.kind : "")} onClick={() => setInfo(null)}>
          <p>{info.message}</p>
        </div>
      )}
    </>
  );
}
