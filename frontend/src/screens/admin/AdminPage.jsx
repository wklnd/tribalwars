import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { api } from "../../api";
import { enterAdminPage } from "./chrome.js";
import { Icon, Loading, Notice, ToastHost } from "./kit.jsx";
import { Accounts, CreateWorld, Dashboard } from "./GlobalPages.jsx";
import { CreateNpcs, DeletePlayer, WorldPlayers, WorldSettings, WorldVillages } from "./WorldPages.jsx";
import { WorldTribes } from "./TribePages.jsx";
import { NpcAi } from "./NpcPages.jsx";
import { VillageEdit } from "./VillagePages.jsx";
import { VillageCreate } from "./VillageCreate.jsx";

/* Admin panel of the game (dark sidebar + light content). Sub-pages live in the hash below "#admin":
   #admin  #admin/players  #admin/create_world  #admin/w/<id>  #admin/w/<id>/players
   .../players/delete/<accountId>  .../players/npcs  .../villages  .../villages/create[/custom]
   .../villages/edit/<villageId> */

const SS_KEY = "twlan_admin_path";

function readPath() {
  try {
    const h = decodeURIComponent(window.location.hash.slice(1));
    if (h === "admin" || h.startsWith("admin/")) return h.slice(6);
    return window.sessionStorage.getItem(SS_KEY) || "";
  } catch {
    return "";
  }
}

// path -> { page, worldId, accountId, villageId, tab, nav (path of the highlighted nav entry) }
export function parsePath(path) {
  const p = String(path || "").split("/").filter(Boolean);
  if (!p.length) return { page: "dashboard", nav: "" };
  if (p[0] === "players") return { page: "accounts", nav: "players" };
  if (p[0] === "create_world") return { page: "create_world", nav: "create_world" };
  if (p[0] === "w" && p[1]) {
    const id = p[1], base = "w/" + id;
    if (!p[2]) return { page: "world", worldId: id, nav: base };
    if (p[2] === "players") {
      if (p[3] === "delete" && p[4]) return { page: "delete_player", worldId: id, accountId: p[4], nav: base + "/players" };
      if (p[3] === "npcs") return { page: "npcs", worldId: id, nav: base + "/players/npcs" };
      return { page: "w_players", worldId: id, nav: base + "/players" };
    }
    if (p[2] === "npc-ai") return { page: "npc_ai", worldId: id, nav: base + "/npc-ai" };
    if (p[2] === "tribes") return { page: "w_tribes", worldId: id, nav: base + "/tribes" };
    if (p[2] === "villages") {
      if (p[3] === "create") return { page: "v_create", worldId: id, tab: p[4] === "custom" ? "custom" : "random", nav: base + "/villages/create" };
      if (p[3] === "edit" && p[4]) return { page: "v_edit", worldId: id, villageId: p[4], nav: base + "/villages" };
      return { page: "w_villages", worldId: id, nav: base + "/villages" };
    }
  }
  return { page: "dashboard", nav: "" };
}

const href = (path) => "#admin" + (path ? "/" + path : "");

function NavItem({ to, active, go, icon, children, sub }) {
  return (
    <a href={href(to)} className={"nav-i" + (sub ? " sub" : "") + (active ? " on" : "")} onClick={(e) => { e.preventDefault(); go(to); }}>
      {icon ? <Icon name={icon} /> : null}
      <span>{children}</span>
    </a>
  );
}

function WorldNav({ world, route, go }) {
  const b = "w/" + world.id;
  const open = String(route.worldId) === String(world.id);
  const items = [
    ["Settings", b],
    ["Players", b + "/players"],
    ["NPC players", b + "/players/npcs"],
    ["NPC AI", b + "/npc-ai"],
    ["Tribes", b + "/tribes"],
    ["Villages", b + "/villages"],
    ["Create villages", b + "/villages/create"],
  ];
  return (
    <div className={"nav-w" + (open ? " open" : "")}>
      <a href={href(b)} className="nav-wh" onClick={(e) => { e.preventDefault(); go(b); }}>
        <Icon name="globe" />
        <span>{world.name}</span>
        <em>{"x" + Number(world.speed)}</em>
      </a>
      {open ? (
        <div className="nav-ws">
          {items.map(([label, path]) => (
            <NavItem key={path} to={path} active={route.nav === path} go={go} sub>
              {label}
            </NavItem>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function crumbs(route, world) {
  const home = ["Dashboard"];
  switch (route.page) {
    case "accounts": return ["Accounts"];
    case "create_world": return ["Worlds", "New world"];
    case "dashboard": return home;
    default: {
      const w = world ? world.name : "World";
      const t = {
        world: ["Settings"],
        w_players: ["Players"],
        delete_player: ["Players", "Remove"],
        npcs: ["NPC players"],
        npc_ai: ["NPC AI"],
        w_tribes: ["Tribes"],
        w_villages: ["Villages"],
        v_create: ["Villages", "Create"],
        v_edit: ["Villages", "Edit"],
      }[route.page] ?? [];
      return ["Worlds", w, ...t];
    }
  }
}

export function AdminPage({ account, onExit }) {
  const scrollRef = useRef(null);
  const [ready, setReady] = useState(false);
  const [navOpen, setNavOpen] = useState(false);
  const [path, setPath] = useState(readPath);
  const [worlds, setWorlds] = useState(null);
  const [catalog, setCatalog] = useState(null);
  const [loadError, setLoadError] = useState(null);
  // the App's account object may lack `admin` (e.g. right after login): ask /auth/me in that case
  const [me, setMe] = useState(null);
  useEffect(() => {
    if (account && account.admin === undefined) api.me().then(setMe, () => setMe({ admin: false }));
  }, [account]);
  const adminKnown = !!account && (account.admin !== undefined || me !== null);
  const isAdmin = !!account && !!(account.admin ?? me?.admin);

  useLayoutEffect(() => enterAdminPage(() => setReady(true)), []);

  const go = useCallback((p) => {
    const path2 = p || "";
    setPath(path2);
    setNavOpen(false);
    try {
      window.history.replaceState(null, "", href(path2));
      window.sessionStorage.setItem(SS_KEY, path2);
    } catch {
      /* ignore */
    }
    if (scrollRef.current) scrollRef.current.scrollTop = 0;
  }, []);

  // make the hash reflect a path restored from sessionStorage
  useEffect(() => {
    try {
      const h = decodeURIComponent(window.location.hash.slice(1));
      if (h !== "admin" && !h.startsWith("admin/")) window.history.replaceState(null, "", href(path));
    } catch {
      /* ignore */
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const reloadWorlds = useCallback(
    () =>
      api.admin.worlds().then(
        (list) => setWorlds(list),
        (e) => setLoadError(e.message),
      ),
    [],
  );

  useEffect(() => {
    if (!isAdmin) return;
    reloadWorlds();
    api.admin.catalog().then(setCatalog, (e) => setLoadError(e.message));
  }, [isAdmin, reloadWorlds]);

  const route = parsePath(path);
  const world = route.worldId != null && worlds ? worlds.find((x) => String(x.id) === String(route.worldId)) : null;

  let content = null;
  if (!adminKnown && account) {
    content = <Loading />;
  } else if (!isAdmin) {
    content = <Notice tone="bad">Insufficient permissions. This area is only for administrators.</Notice>;
  } else if (loadError && !worlds) {
    content = <Notice tone="bad">{loadError}</Notice>;
  } else if (!worlds || !catalog) {
    content = <Loading />;
  } else {
    switch (route.page) {
      case "dashboard":
        content = <Dashboard worlds={worlds} go={go} />;
        break;
      case "accounts":
        content = <Accounts account={account} />;
        break;
      case "create_world":
        content = <CreateWorld catalog={catalog} reloadWorlds={reloadWorlds} go={go} />;
        break;
      default: {
        if (!world) {
          content = <Notice tone="warn">This world does not exist (any more).</Notice>;
          break;
        }
        const key = world.id + ":" + path;
        if (route.page === "world") content = <WorldSettings key={world.id} world={world} catalog={catalog} reloadWorlds={reloadWorlds} go={go} />;
        else if (route.page === "w_players") content = <WorldPlayers key={key} world={world} go={go} />;
        else if (route.page === "delete_player") content = <DeletePlayer key={key} world={world} accountId={route.accountId} go={go} />;
        else if (route.page === "npcs") content = <CreateNpcs key={key} world={world} go={go} />;
        else if (route.page === "npc_ai") content = <NpcAi key={key} world={world} go={go} />;
        else if (route.page === "w_tribes") content = <WorldTribes key={key} world={world} go={go} />;
        else if (route.page === "w_villages") content = <WorldVillages key={key} world={world} go={go} />;
        else if (route.page === "v_create") content = <VillageCreate key={world.id} world={world} catalog={catalog} tab={route.tab} go={go} reloadWorlds={reloadWorlds} />;
        else if (route.page === "v_edit") content = <VillageEdit key={key} world={world} villageId={route.villageId} catalog={catalog} go={go} />;
      }
    }
  }

  const trail = crumbs(route, world);
  return (
    <div className={"adm" + (navOpen ? " nav-open" : "")} style={ready ? undefined : { visibility: "hidden" }}>
      <aside className="side">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">N</span>
          <div>
            <strong>nilz clone</strong>
            <small>tribalwars admin</small>
          </div>
        </div>
        <nav className="nav" aria-label="Admin navigation">
          <div className="nav-t">Overview</div>
          <NavItem to="" active={route.nav === ""} go={go} icon="dashboard">Dashboard</NavItem>
          <NavItem to="players" active={route.nav === "players"} go={go} icon="users">Accounts</NavItem>
          <div className="nav-t">Worlds</div>
          {(worlds ?? []).map((w) => (
            <WorldNav key={w.id} world={w} route={route} go={go} />
          ))}
          <NavItem to="create_world" active={route.nav === "create_world"} go={go} icon="plus">New world</NavItem>
        </nav>
        <div className="side-f">
          <div className="me">
            <span className="avatar">{(account?.username ?? "?").slice(0, 1).toUpperCase()}</span>
            <div>
              <strong>{account?.username ?? "-"}</strong>
              <small>Administrator</small>
            </div>
          </div>
          <a href="#overview" className="nav-i exit" onClick={(e) => { e.preventDefault(); onExit(); }}>
            <Icon name="logout" />
            <span>Back to game</span>
          </a>
        </div>
      </aside>
      <div className="scrim" onClick={() => setNavOpen(false)} />
      <div className="main">
        <header className="top">
          <button type="button" className="hamb" onClick={() => setNavOpen((o) => !o)} aria-label="Menu">
            <Icon name="menu" size={20} />
          </button>
          <nav className="crumbs" aria-label="Breadcrumb">
            {trail.map((c, i) => (
              <span key={i} className={i === trail.length - 1 ? "last" : ""}>
                {c}
              </span>
            ))}
          </nav>
        </header>
        <div className="scroll" ref={scrollRef}>
          <ToastHost>
            <main className="page">{content}</main>
          </ToastHost>
        </div>
      </div>
    </div>
  );
}

export default AdminPage;
