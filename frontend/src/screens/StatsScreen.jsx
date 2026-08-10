// screen=info_player&mode=stats_own (and s_<name> for another player) — graphs of a player's points, rank, villages
// and defeated opponents over time, drawn as plain SVG. The server stores a record whenever one of them changes, so
// the lines are step lines that run on to "now".
import { useEffect, useState } from "react";
import { api } from "../api";

const RANGES = [
  ["hour", "Last hour"],
  ["day", "Last 24 hours"],
  ["week", "Last 7 days"],
  ["all", "Whole world"],
];
const fmt = (n) => Number(n).toLocaleString("de-DE");

function timeLabel(ms, span) {
  const d = new Date(ms);
  if (span <= 10 * 60e3) return d.toLocaleTimeString("en-GB");
  if (span <= 36 * 3600e3) return d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  return d.toLocaleDateString("en-GB", { day: "numeric", month: "short" });
}

/** A step line chart. `invert` draws small numbers at the top (ranks: 1 is best). */
function LineChart({ points, from, to, invert = false, color = "#7d510f", width = 400, height = 170 }) {
  const padL = 46, padR = 10, padT = 10, padB = 24;
  const w = width - padL - padR;
  const h = height - padT - padB;
  const values = points.map((p) => p.v);
  let lo = Math.min(...values);
  let hi = Math.max(...values);
  if (lo === hi) { lo = Math.max(0, lo - 1); hi = hi + 1; }
  const x = (t) => padL + ((t - from) / Math.max(1, to - from)) * w;
  const y = (v) => padT + (invert ? (v - lo) / (hi - lo) : 1 - (v - lo) / (hi - lo)) * h;

  // step line: hold each value until the next record, and on to `to`
  let d = "";
  points.forEach((p, i) => {
    const px = x(Math.max(from, Math.min(to, p.t)));
    d += (i === 0 ? "M" : "L") + px + "," + (i === 0 ? y(p.v) : y(points[i - 1].v)) + " L" + px + "," + y(p.v) + " ";
  });
  d += "L" + x(to) + "," + y(points[points.length - 1].v);

  const ticks = [0, 0.5, 1].map((f) => lo + (hi - lo) * f);
  const span = to - from;
  return (
    <svg width={width} height={height} style={{ background: "#fbf3dc", border: "1px solid #c1a264", display: "block" }}>
      {ticks.map((v, i) => (
        <g key={i}>
          <line x1={padL} x2={width - padR} y1={y(v)} y2={y(v)} stroke="#e0cd9a" strokeWidth="1" />
          <text x={padL - 5} y={y(v) + 4} textAnchor="end" fontSize="10" fill="#603000">{fmt(Math.round(v))}</text>
        </g>
      ))}
      {[0, 0.5, 1].map((f, i) => (
        <text key={i} x={padL + w * f} y={height - 8} textAnchor={f === 0 ? "start" : f === 1 ? "end" : "middle"} fontSize="10" fill="#603000">
          {timeLabel(from + span * f, span)}
        </text>
      ))}
      <path d={d} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" />
      <circle cx={x(to)} cy={y(points[points.length - 1].v)} r="3" fill={color} />
    </svg>
  );
}

function Delta({ n, invert }) {
  if (!n) return <span>±0</span>;
  const good = invert ? n < 0 : n > 0;
  return <span style={{ color: good ? "#0a7a0a" : "#b00" }}>{n > 0 ? "+" : "−"}{fmt(Math.abs(n))}</span>;
}

export function StatsScreen({ name, go }) {
  const [range, setRange] = useState("all");
  const [stats, setStats] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let alive = true;
    const load = () =>
      api.playerStats(name ?? "me", range).then((s) => alive && (setStats(s), setError(null))).catch((e) => alive && setError(e.message));
    load();
    const id = setInterval(load, 15000);
    return () => { alive = false; clearInterval(id); };
  }, [name, range]);

  if (error) {
    return (
      <>
        <h2>Statistics</h2>
        <div className="error_box">{error}</div>
      </>
    );
  }
  if (!stats) return <p>Loading…</p>;

  const s = stats;
  const from = range === "all" ? s.series[0].t : s.from;
  const series = (pick) => s.series.map((p) => ({ t: p.t, v: pick(p) }));
  const charts = [
    ["Points", (p) => p.points, false, "#7d510f", "points"],
    ["Rank", (p) => p.rank, true, "#2f5f9f", "rank"],
    ["Villages", (p) => p.villages, false, "#2f7f2f", "villages"],
    ["Opponents defeated", (p) => p.kills, false, "#a02020", "kills"],
  ];

  return (
    <>
      <h2>Statistics of {s.name}</h2>
      <p>
        {RANGES.map(([r, label], i) => (
          <span key={r}>
            {i > 0 && " | "}
            {r === range ? <b>{label}</b> : <a href="#" onClick={(e) => { e.preventDefault(); setRange(r); }}>{label}</a>}
          </span>
        ))}
      </p>
      <table className="vis" width="100%" style={{ marginBottom: 10 }}>
        <tbody>
          <tr>
            <th />
            <th>Now</th>
            <th>Change ({RANGES.find(([r]) => r === range)[1].toLowerCase()})</th>
          </tr>
          {charts.map(([label, pick, invert, , key]) => (
            <tr key={key}>
              <td>{label}</td>
              <td>{fmt(pick(s.current))}</td>
              <td><Delta n={pick(s.change)} invert={invert} /></td>
            </tr>
          ))}
        </tbody>
      </table>
      <table width="100%">
        <tbody>
          {[0, 2].map((row) => (
            <tr key={row}>
              {charts.slice(row, row + 2).map(([label, pick, invert, color, key]) => (
                <td key={key} valign="top" style={{ paddingBottom: 12 }}>
                  <table className="vis" width="100%">
                    <tbody>
                      <tr><th>{label}</th></tr>
                      <tr>
                        <td style={{ padding: 4 }}>
                          <LineChart points={series(pick)} from={from} to={s.now} invert={invert} color={color} />
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <a href="#" onClick={(e) => go(e, name ? "info_player:p_" + name : "info_player")}>» Back to the profile</a>
      </p>
    </>
  );
}
