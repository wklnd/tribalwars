import { useEffect, useState } from "react";
import { api } from "../api";
import { PLAYER_NAME, villagePoints } from "../lib/data";
import { ModeMenu } from "../lib/ui";
import { Wars } from "./ally/Wars";

/* Mirrors templates ranking/index.php + navi.php + player.php + ally.php of the original.
   Real behaviour (checked against the running original):
   - unknown/missing mode -> "player"
   - player: gold/silver/bronze podium (only without a name search, first page), table, "rank:" / "Search:" forms;
     the current player's row is highlighted, "rank=N" highlights rank N, "name=X" (case-insensitive
     exact match) shows only that player and no podium.
   - ally: podium + tribe table (rank, tag, points, members, per player, villages, per village);
     rank / tag / name search like the player ranking (the real engine crashes when there are no tribes at all).
   Beyond the original (which answers "Not implemented yet" for them): con_player / con_ally (points inside one
   continent, picked in a drop-down) and kill_player / kill_ally (opponents defeated). All lists come from
   /api/ranking/players|tribes; lists longer than PAGE rows are paged (a rank search jumps to its page). */

export const RANKING_MODES = [
  ["ally", "Tribes"],
  ["player", "Players"],
  ["con_ally", "Continent Tribes"],
  ["con_player", "Continent Players"],
  ["kill_ally", "Opponents defeated tribal ranking"],
  ["kill_player", "Opponents defeated"],
  ["awards", "Achievements"],
  ["wars", "Wars"],
];

const PLAYER_MODES = ["player", "con_player", "kill_player"];
const ALLY_MODES = ["ally", "con_ally", "kill_ally"];

const thousands = (n) => String(Math.floor(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");

function RankSearchForm({ mode, label, name, inputProps, onSubmit, cellStyle, children }) {
  return (
    <td style={cellStyle}>
      <form action="#" method="get" onSubmit={onSubmit}>
        <input type="hidden" name="screen" value="ranking" readOnly />
        <input type="hidden" name="mode" value={mode} readOnly /> {label}
        {children}
        <input name={name} type="text" defaultValue="" {...inputProps} />
        <input className="btn" type="submit" value="OK" />
      </form>
    </td>
  );
}

function AutocompleteStatus() {
  return <span role="status" aria-live="polite" className="ui-helper-hidden-accessible" />;
}

// every player of the world (from the village list), ranked by total points; the viewer's own villages use the live
// points of the open village so the ranking updates the moment a building finishes
export function rankPlayers(village, villages) {
  const byName = new Map();
  const add = (name, points, tribe) => {
    const p = byName.get(name) ?? { name, tag: "", tribeId: null, points: 0, villages: 0 };
    if (tribe) { p.tag = tribe.tag; p.tribeId = tribe.id; }
    p.points += points;
    p.villages += 1;
    byName.set(name, p);
  };
  for (const v of villages) {
    if (v.ownerType !== "PLAYER") continue;
    add(v.ownerName ?? PLAYER_NAME, v.id === village.id ? villagePoints(village) : v.points ?? 0,
      v.ownerTribeId ? { id: v.ownerTribeId, tag: v.ownerTribeTag } : null);
  }
  if (!byName.has(PLAYER_NAME)) add(PLAYER_NAME, villagePoints(village));
  return [...byName.values()]
    .sort((a, b) => b.points - a.points || a.name.localeCompare(b.name))
    .map((p, i) => ({ ...p, id: i + 1, rank: i + 1 }));
}

const LOAD_FAILED = "The ranking could not be loaded (is the backend up to date?)";
const PAGE = 100; // rows per page; the podium only shows on the first page

// fetches a ranking and refreshes it every 10 s; `load` is called again whenever `key` changes
function useRows(load, key) {
  const [data, setData] = useState(null);
  useEffect(() => {
    let alive = true;
    setData(null);
    const run = () => load().then((r) => alive && setData(r)).catch(() => alive && setData((d) => d ?? { failed: true, continents: [], rows: [] }));
    run();
    const id = setInterval(run, 10000);
    return () => { alive = false; clearInterval(id); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return data;
}

// "rank" / "name" / "tag" search + paging shared by every list: which rows to show and which one to highlight
function useListView(all, matchers, defaultLit) {
  const [query, setQuery] = useState({}); // {rank} | {<field>: text}
  const [page, setPage] = useState(0);
  let rows = all;
  let top = true;
  let lit = defaultLit;
  const field = Object.keys(matchers).find((f) => query[f] !== undefined);
  if (field) {
    top = false;
    rows = all.filter((r) => matchers[field](r).toLowerCase() === query[field].toLowerCase());
    lit = (r) => matchers[field](r) === query[field];
  } else if (query.rank !== undefined) {
    lit = (r) => r.rank === query.rank;
  }
  const pages = Math.max(1, Math.ceil(rows.length / PAGE));
  // a rank search jumps to the page holding that rank
  const shown = Math.min(query.rank !== undefined && !field ? Math.floor((query.rank - 1) / PAGE) : page, pages - 1);
  const submit = (name) => (e) => {
    e.preventDefault();
    const v = e.currentTarget.elements[name].value;
    setPage(0);
    if (name === "rank") {
      const n = parseInt(v, 10);
      setQuery(Number.isNaN(n) ? {} : { rank: n });
    } else setQuery({ [name]: String(v) });
  };
  return {
    rows: rows.slice(shown * PAGE, (shown + 1) * PAGE), all: rows, podium: top && shown === 0, lit, submit,
    pager: pages > 1 && { pages, shown, set: (n) => { setQuery((q) => (q.rank !== undefined ? {} : q)); setPage(n); } },
  };
}

function Pager({ pager, total }) {
  if (!pager) return null;
  return (
    <table className="vis" width="100%">
      <tbody>
        <tr>
          <td align="center">
            {Array.from({ length: pager.pages }, (_, i) => {
              const label = `${i * PAGE + 1}-${Math.min(total, (i + 1) * PAGE)}`;
              return i === pager.shown
                ? <strong key={i}> &gt;{label}&lt; </strong>
                : <a key={i} href="#" onClick={(e) => { e.preventDefault(); pager.set(i); }}> [{label}] </a>;
            })}
          </td>
        </tr>
      </tbody>
    </table>
  );
}

// gold / silver / bronze: the first three entries of the list
function Podium({ rows, label, href }) {
  if (rows.length === 0) return null;
  const places = ["gold", "silver", "bronze"];
  return (
    <div className="ranking-top3">
      {places.slice(0, rows.length).map((cls, i) => (
        <div key={cls} className={cls}>
          <a href="#" onClick={href(rows[i])}>{label(rows[i])}</a>
        </div>
      ))}
    </div>
  );
}

const continentOfVillage = (v) => Math.floor(v.y / 100) * 10 + Math.floor(v.x / 100);
const continentName = (c) => "K" + String(c).padStart(2, "0");

function ContinentForm({ continents, value, onChange }) {
  return (
    <table className="vis" width="100%">
      <tbody>
        <tr>
          <td style={{ paddingRight: 10 }}>
            <form action="#" method="get" onSubmit={(e) => e.preventDefault()}>
              Continent:{" "}
              <select value={value} onChange={(e) => onChange(Number(e.target.value))}>
                {continents.map((c) => <option key={c} value={c}>{continentName(c)}</option>)}
              </select>
            </form>
          </td>
        </tr>
      </tbody>
    </table>
  );
}

// modes: player (points), con_player (points within one continent), kill_player (opponents defeated)
function PlayerRanking({ mode, village, go }) {
  const kills = mode === "kill_player";
  const con = mode === "con_player";
  const [continent, setContinent] = useState(() => continentOfVillage(village));
  const data = useRows(() => api.rankingPlayers(kills ? "kills" : "points", con ? continent : undefined), `${mode}/${continent}`);
  const view = useListView(data?.rows ?? [], { name: (p) => p.name }, (p) => p.name === PLAYER_NAME); // the engine highlights the own player by default
  const link = (p) => (e) => go(e, "info_player:p_" + p.name);

  return (
    <>
      {con && data && <ContinentForm continents={data.continents.includes(continent) ? data.continents : [continent, ...data.continents]} value={continent} onChange={setContinent} />}
      {view.podium && <Podium rows={view.all} label={(p) => p.name} href={link} />}
      <table id="player_ranking_table" className="vis" width="100%">
        <tbody>
          <tr>
            <th width="60">rank</th>
            <th width="180">name</th>
            <th width="100">Tribe</th>
            {kills ? <th>Opponents defeated</th> : (
              <>
                <th width="60">Points</th>
                <th>Villages</th>
                <th>Points per village</th>
              </>
            )}
          </tr>
          {view.rows.map((p) => (
            <tr key={p.id} className={view.lit(p) ? "lit" : undefined}>
              <td className="lit-item">{p.rank}</td>
              <td className="lit-item nowrap"><a className="" href="#" onClick={link(p)}> {p.name} </a></td>
              <td className="lit-item nowrap">
                {p.tribeId && <a href="#" onClick={(e) => go(e, "info_ally:" + p.tribeId)}> {p.tribeTag} </a>}
              </td>
              {kills ? <td className="lit-item">{thousands(p.kills)}</td> : (
                <>
                  <td className="lit-item">{thousands(p.points)}</td>
                  <td className="lit-item">{p.villages}</td>
                  <td className="lit-item">{thousands(p.points / p.villages)}</td>
                </>
              )}
            </tr>
          ))}
          {data && view.all.length === 0 && (
            <tr><td className="lit-item" colSpan={kills ? 4 : 6}>{data.failed ? LOAD_FAILED : "No players found."}</td></tr>
          )}
        </tbody>
      </table>
      <Pager pager={view.pager} total={view.all.length} />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <RankSearchForm mode={mode} label="rank:" name="rank" cellStyle={{ paddingRight: 10 }}
              inputProps={{ size: 6 }} onSubmit={view.submit("rank")} />
            <RankSearchForm mode={mode} label="Search:" name="name" cellStyle={{ paddingRight: 10 }}
              inputProps={{ size: 20, className: "autocomplete ui-autocomplete-input", "data-type": "player", autoComplete: "off" }}
              onSubmit={view.submit("name")}>
              <AutocompleteStatus />
            </RankSearchForm>
          </tr>
        </tbody>
      </table>
    </>
  );
}

// modes: ally (points), con_ally (points within one continent), kill_ally (opponents defeated by the members)
function AllyRanking({ mode, village, go }) {
  const kills = mode === "kill_ally";
  const con = mode === "con_ally";
  const [continent, setContinent] = useState(() => continentOfVillage(village));
  const data = useRows(() => api.rankingTribes(kills ? "kills" : "points", con ? continent : undefined), `${mode}/${continent}`);
  const view = useListView(data?.rows ?? [], { tag: (t) => t.tag, name: (t) => t.name }, () => false);
  const link = (t) => (e) => go(e, "info_ally:" + t.id);
  const acInput = { className: "autocomplete ui-autocomplete-input", "data-type": "ally", autoComplete: "off" };

  return (
    <>
      {con && data && <ContinentForm continents={data.continents.includes(continent) ? data.continents : [continent, ...data.continents]} value={continent} onChange={setContinent} />}
      {view.podium && <Podium rows={view.all} label={(t) => t.tag} href={link} />}
      <table id="ally_ranking_table" className="vis" width="100%">
        <tbody>
          <tr>
            <th width="60">rank</th>
            <th width="60">Tribe name</th>
            {kills ? <th>Opponents defeated</th> : (
              <>
                <th width="60">Total points</th>
                <th width="100">Members</th>
                <th width="100">Points per player</th>
                <th width="60">Villages</th>
                <th width="100">Points per village</th>
              </>
            )}
          </tr>
          {view.rows.map((t) => (
            <tr key={t.id} className={view.lit(t) ? "lit" : undefined}>
              <td className="lit-item">{t.rank}</td>
              <td className="lit-item nowrap"><a href="#" onClick={link(t)}> {t.tag} </a></td>
              {kills ? <td className="lit-item">{thousands(t.kills)}</td> : (
                <>
                  <td className="lit-item">{thousands(t.points)}</td>
                  <td className="lit-item">{t.members}</td>
                  <td className="lit-item">{thousands(t.pointsPerPlayer)}</td>
                  <td className="lit-item">{t.villages}</td>
                  <td className="lit-item">{thousands(t.pointsPerVillage)}</td>
                </>
              )}
            </tr>
          ))}
          {data && view.all.length === 0 && (
            <tr><td className="lit-item" colSpan={kills ? 3 : 7}>{data.failed ? LOAD_FAILED : "No tribes found."}</td></tr>
          )}
        </tbody>
      </table>
      <Pager pager={view.pager} total={view.all.length} />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <RankSearchForm mode={mode} label="rank" name="rank" cellStyle={{ paddingRight: 10 }}
              inputProps={{ size: 6 }} onSubmit={view.submit("rank")} />
            <RankSearchForm mode={mode} label="Tribe tag" name="tag" cellStyle={{ paddingRight: 3 }}
              inputProps={{ style: { width: 40 }, ...acInput }} onSubmit={view.submit("tag")}>
              <AutocompleteStatus />
            </RankSearchForm>
            <RankSearchForm mode={mode} label="Tribe name" name="name" cellStyle={{ paddingRight: 3 }}
              inputProps={{ size: 20 }} onSubmit={view.submit("name")} />
          </tr>
        </tbody>
      </table>
    </>
  );
}

// Ranking -> Achievements: the world's players by achievement levels earned
function AwardsRanking({ go }) {
  const [rows, setRows] = useState(null);
  useEffect(() => {
    api.achievementRanking().then(setRows).catch(() => setRows([]));
  }, []);
  return (
    <>
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th width="60">rank</th>
            <th>name</th>
            <th>Achievements</th>
            <th>Levels</th>
          </tr>
          {(rows ?? []).map((p, i) => (
            <tr key={p.player} className={p.player === PLAYER_NAME ? "lit" : undefined}>
              <td className="lit-item">{i + 1}</td>
              <td className="lit-item nowrap">
                <a href="#" onClick={(e) => go(e, "info_player:p_" + p.player)}> {p.player} </a>
              </td>
              <td className="lit-item">{p.achievements}</td>
              <td className="lit-item">{p.levels}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows && rows.length === 0 && <p>Nobody has earned an achievement yet.</p>}
    </>
  );
}

export function RankingScreen({ village, mode, go }) {
  const current = RANKING_MODES.some(([m]) => m === mode) ? mode : "player";
  return (
    <>
      <h2>Ranking</h2>
      <table width="100%">
        <tbody>
          <tr>
            <td valign="top" width="130px">
              <ModeMenu modes={RANKING_MODES} current={current} go={go} screen="ranking" />
            </td>
            <td valign="top">
              {PLAYER_MODES.includes(current) && <PlayerRanking key={current} mode={current} village={village} go={go} />}
              {ALLY_MODES.includes(current) && <AllyRanking key={current} mode={current} village={village} go={go} />}
              {current === "wars" && <Wars key="wars" go={go} />}
              {current === "awards" && <AwardsRanking key="awards" go={go} />}
            </td>
          </tr>
        </tbody>
      </table>
    </>
  );
}
