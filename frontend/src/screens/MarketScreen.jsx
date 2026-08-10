/* screen=market — merchants, shipments and trade offers. The original TWLan has no market page ("The page ...
   couldn't be found"), so this is built from game.css classes only (the building header like the rally point's,
   `vis` tables, the target field of the rally point, `icon header` resource icons); nothing here is a copy.
     market:send (default)   send resources to a village (own or other players'), with a confirm step
     market:other_offer      "Buy": offers of other villages (NPCs and players) you can accept
     market:own_offer        "Sell": make an offer, cancel your own
     market:traders          "Transports": merchants on the road
     market:<villageId>      the send page with that village as target (info_village -> "Send resources") */
import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { useLiveTopic, usePollMs } from "../lib/live";
import { levelOf } from "../lib/data";
import { PreErrorBox, Timer } from "../lib/ui";
import { fmtOnTime, fmtBuildTime, villageDisplayName, hashParts } from "../lib/military";
import { TargetField } from "./military/PlaceParts";

export const RESOURCES = [
  ["WOOD", "wood", "Wood", "wood"],
  ["CLAY", "stone", "Clay", "clay"],
  ["IRON", "iron", "Iron", "iron"],
];
const RES_BY = Object.fromEntries(RESOURCES.map((r) => [r[0], r]));
const MODES = [
  ["send", "Send resources"],
  ["other_offer", "Buy"],
  ["own_offer", "Sell"],
  ["traders", "Transports"],
];
const fmt = (n) => String(Math.floor(n)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
const ResIcon = ({ r }) => <span className={"icon header " + RES_BY[r][1]} title={RES_BY[r][2]} />;

/** The market state of the current village, refreshed every few seconds; actions answer with the fresh state. */
export function useMarket(villageId) {
  const [state, setState] = useState(null);
  const [error, setError] = useState(null);
  const load = useCallback(() => api.market().then((s) => { setState(s); setError(null); }).catch((e) => setError(e.message)), []);
  const pollMs = usePollMs(3000, 30000);
  useLiveTopic("market", load);
  useEffect(() => {
    load();
    const id = setInterval(load, pollMs);
    return () => clearInterval(id);
  }, [load, villageId, pollMs]);
  const act = async (fn) => {
    try {
      setState(await fn());
      setError(null);
      return true;
    } catch (e) {
      setError(e.message);
      return false;
    }
  };
  return { state, error, setError, act, load };
}

const Cargo = ({ wood, clay, iron }) => (
  <>
    {[["WOOD", wood], ["CLAY", clay], ["IRON", iron]].filter(([, n]) => n > 0).map(([r, n]) => (
      <span key={r} className="nowrap" style={{ marginRight: 6 }}><ResIcon r={r} /> {fmt(n)}</span>
    ))}
  </>
);

/** Merchants on the road (this village's and everybody's that are coming to it). */
export function ShipmentsTable({ shipments, go, title = "Merchants on the way" }) {
  if (!shipments?.length) return null;
  return (
    <table className="vis" width="100%">
      <tbody>
        <tr>
          <th colSpan="5">{title}</th>
        </tr>
        <tr>
          <th>Village</th>
          <th>Cargo</th>
          <th>Merchants</th>
          <th>Arrival</th>
          <th>Arrival in</th>
        </tr>
        {shipments.map((s) => {
          const otherId = s.outgoing ? s.targetVillageId : s.originVillageId;
          const otherName = s.outgoing ? s.targetName : s.originName;
          const x = s.outgoing ? s.targetX : s.originX;
          const y = s.outgoing ? s.targetY : s.originY;
          return (
            <tr key={s.id}>
              <td>
                <a href="#" onClick={(e) => go(e, "info_village:" + otherId)}>{`${s.outgoing ? (s.returning ? "Return from " : "To ") : "From "}${otherName} (${x}|${y})`}</a>
              </td>
              <td><Cargo wood={s.wood} clay={s.clay} iron={s.iron} /></td>
              <td>{s.merchants}</td>
              <td className="nowrap">{fmtOnTime(s.arrivesAt)}</td>
              <td><Timer target={s.arrivesAt} /></td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function SendMode({ village, villages, market, go, presetId }) {
  const { state, act } = market;
  const [amounts, setAmounts] = useState({ WOOD: "", CLAY: "", IRON: "" });
  const [type, setType] = useState("coord");
  const [text, setText] = useState("");
  const [target, setTarget] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [presetDone, setPresetDone] = useState(null);
  if (presetId != null && presetId !== presetDone) {
    const v = villages.find((x) => x.id === presetId);
    if (v) {
      setPresetDone(presetId);
      setTarget(v);
    }
  }
  const have = { WOOD: Math.floor(village.wood), CLAY: Math.floor(village.clay), IRON: Math.floor(village.iron) };
  const merchants = state?.merchants ?? village.merchants ?? { total: 0, available: 0, capacity: 1000 };
  const num = (r) => Math.max(0, parseInt(amounts[r], 10) || 0);
  const sum = num("WOOD") + num("CLAY") + num("IRON");
  const needed = Math.ceil(sum / merchants.capacity);

  const fillMax = (e, r) => {
    e.preventDefault();
    const others = RESOURCES.filter(([k]) => k !== r).reduce((a, [k]) => a + num(k), 0);
    const room = Math.max(0, merchants.available * merchants.capacity - others);
    setAmounts({ ...amounts, [r]: String(Math.min(have[r], room)) });
  };

  const check = (e) => {
    e.preventDefault();
    market.setError(null);
    if (!target) return market.setError("You need to enter the x and y coordinates of the destination");
    if (sum <= 0) return market.setError("You have to enter the resources to send");
    if (RESOURCES.some(([r]) => num(r) > have[r])) return market.setError("Not enough resources");
    if (needed > merchants.available) return market.setError(`Not enough merchants (${needed} needed, ${merchants.available} available)`);
    setConfirm({ target, wood: num("WOOD"), clay: num("CLAY"), iron: num("IRON") });
  };
  const seconds = (t) => {
    const d = Math.sqrt((village.x - t.x) ** 2 + (village.y - t.y) ** 2);
    return Math.max(1, Math.round((d * 6 * 60) / (village.worldSpeed || 1)));
  };
  const send = async () => {
    const c = confirm;
    if (await act(() => api.marketSend(c.target.id, c.wood, c.clay, c.iron))) {
      setConfirm(null);
      setAmounts({ WOOD: "", CLAY: "", IRON: "" });
      setText("");
      setTarget(null);
    } else {
      setConfirm(null);
    }
  };

  if (confirm) {
    const secs = seconds(confirm.target);
    return (
      <form onSubmit={(e) => { e.preventDefault(); send(); }}>
        <h3>Send resources</h3>
        <table className="vis">
          <tbody>
            <tr>
              <td>Target:</td>
              <td><a href="#" onClick={(e) => go(e, "info_village:" + confirm.target.id)}>{villageDisplayName(confirm.target)}</a></td>
            </tr>
            <tr>
              <td>Resources:</td>
              <td><Cargo wood={confirm.wood} clay={confirm.clay} iron={confirm.iron} /></td>
            </tr>
            <tr>
              <td>Merchants:</td>
              <td>{Math.ceil((confirm.wood + confirm.clay + confirm.iron) / merchants.capacity)}</td>
            </tr>
            <tr>
              <td>Duration:</td>
              <td>{fmtBuildTime(secs * 1000)}</td>
            </tr>
            <tr>
              <td>Arrival:</td>
              <td>{fmtOnTime(new Date(Date.now() + secs * 1000))}</td>
            </tr>
          </tbody>
        </table>
        <input type="submit" className="btn" value="OK" />{" "}
        <a href="#" onClick={(e) => { e.preventDefault(); setConfirm(null); }}>Cancellation</a>
      </form>
    );
  }

  return (
    <>
      <form id="market_send" onSubmit={check}>
        <table className="vis" style={{ marginBottom: 8 }}>
          <tbody>
            <tr>
              <th colSpan="3">Send resources</th>
            </tr>
            {RESOURCES.map(([r, , name]) => (
              <tr key={r}>
                <td><ResIcon r={r} /> {name}</td>
                <td>
                  <input type="text" size="8" value={amounts[r]} onChange={(e) => setAmounts({ ...amounts, [r]: e.target.value.replace(/\D/g, "") })} />
                </td>
                <td><a href="#" onClick={(e) => fillMax(e, r)}>({fmt(have[r])})</a></td>
              </tr>
            ))}
            <tr>
              <td colSpan="3">
                Merchants: <b>{merchants.available}/{merchants.total}</b> (each carries {fmt(merchants.capacity)}), needed: {needed}
              </td>
            </tr>
          </tbody>
        </table>
        <div style={{ overflow: "hidden" }}>
          <TargetField village={village} villages={villages.filter((v) => v.ownerType !== "BARBARIAN")} type={type} setType={setType} text={text} setText={setText} confirmed={target} setConfirmed={setTarget} />
        </div>
        <p>
          <input type="submit" className="btn" value="OK" />
        </p>
      </form>
      <ShipmentsTable shipments={state?.shipments} go={go} />
    </>
  );
}

function BuyMode({ market, go }) {
  const { state, act } = market;
  const [times, setTimes] = useState({});
  const offers = state?.offers ?? [];
  const merchants = state?.merchants ?? { available: 0, total: 0 };
  return (
    <>
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th>Village</th>
            <th>Offer</th>
            <th>Request</th>
            <th>Ratio</th>
            <th>Distance</th>
            <th>Merchants</th>
            <th>Available</th>
            <th>Accept</th>
          </tr>
          {offers.map((o) => {
            const n = Math.min(o.remaining, Math.max(1, parseInt(times[o.id], 10) || 1));
            const tooFar = o.maxDistance > 0 && o.distance > o.maxDistance;
            return (
              <tr key={o.id}>
                <td>
                  <a href="#" onClick={(e) => go(e, "info_village:" + o.villageId)}>{`${o.villageName} (${o.x}|${o.y})`}</a>
                  <br />
                  <span className="grey small">{o.owner ?? ""}</span>
                </td>
                <td className="nowrap"><ResIcon r={o.sellResource} /> {fmt(o.sellAmount)}</td>
                <td className="nowrap"><ResIcon r={o.buyResource} /> {fmt(o.buyAmount)}</td>
                <td>{o.ratio.toFixed(2)}</td>
                <td className="nowrap">{o.distance.toFixed(1)}{o.maxDistance > 0 ? ` (max ${o.maxDistance})` : ""}</td>
                <td>{o.merchantsNeeded}</td>
                <td>{o.remaining}×</td>
                <td className="nowrap">
                  {tooFar ? (
                    <span className="grey">too far</span>
                  ) : (
                    <>
                      <input type="text" size="2" value={times[o.id] ?? "1"} onChange={(e) => setTimes({ ...times, [o.id]: e.target.value.replace(/\D/g, "") })} />{" "}
                      <a href="#" onClick={(e) => { e.preventDefault(); act(() => api.marketAccept(o.id, n)); }}>» Accept</a>
                    </>
                  )}
                </td>
              </tr>
            );
          })}
          {offers.length === 0 && (
            <tr>
              <td colSpan="8">There are no offers at the moment.</td>
            </tr>
          )}
        </tbody>
      </table>
      <p>Merchants: <b>{merchants.available}/{merchants.total}</b> — you pay the requested resource and your merchants bring the offered one back.</p>
      <ShipmentsTable shipments={state?.shipments} go={go} />
    </>
  );
}

function SellMode({ market, go }) {
  const { state, act } = market;
  const [form, setForm] = useState({ sell: "WOOD", sellAmount: "", buy: "CLAY", buyAmount: "", times: "1", maxDistance: "0" });
  const own = state?.ownOffers ?? [];
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const digits = (k) => (e) => setForm({ ...form, [k]: e.target.value.replace(/\D/g, "") });
  const submit = async (e) => {
    e.preventDefault();
    if (await act(() => api.marketCreateOffer({
      sellResource: form.sell, sellAmount: parseInt(form.sellAmount, 10) || 0, buyResource: form.buy, buyAmount: parseInt(form.buyAmount, 10) || 0,
      times: parseInt(form.times, 10) || 1, maxDistance: parseInt(form.maxDistance, 10) || 0,
    }))) setForm({ ...form, sellAmount: "", buyAmount: "" });
  };
  const select = (k) => (
    <select value={form[k]} onChange={set(k)}>
      {RESOURCES.map(([r, , name]) => <option key={r} value={r}>{name}</option>)}
    </select>
  );
  return (
    <>
      <form onSubmit={submit}>
        <table className="vis">
          <tbody>
            <tr>
              <th colSpan="2">Make an offer</th>
            </tr>
            <tr>
              <td>I offer:</td>
              <td><input type="text" size="6" value={form.sellAmount} onChange={digits("sellAmount")} /> {select("sell")}</td>
            </tr>
            <tr>
              <td>I want:</td>
              <td><input type="text" size="6" value={form.buyAmount} onChange={digits("buyAmount")} /> {select("buy")}</td>
            </tr>
            <tr>
              <td>Number of offers:</td>
              <td><input type="text" size="3" value={form.times} onChange={digits("times")} /></td>
            </tr>
            <tr>
              <td>Maximum distance:</td>
              <td><input type="text" size="3" value={form.maxDistance} onChange={digits("maxDistance")} /> fields (0 = unlimited)</td>
            </tr>
          </tbody>
        </table>
        <p>The offered goods leave your village when you make the offer; you get them back if you cancel it.</p>
        <input type="submit" className="btn" value="Create" />
      </form>
      <br />
      <table className="vis" width="100%">
        <tbody>
          <tr>
            <th colSpan="6">Your offers</th>
          </tr>
          <tr>
            <th>Village</th>
            <th>Offer</th>
            <th>Request</th>
            <th>Left</th>
            <th>Max distance</th>
            <th />
          </tr>
          {own.map((o) => (
            <tr key={o.id}>
              <td><a href="#" onClick={(e) => go(e, "info_village:" + o.villageId)}>{`${o.villageName} (${o.x}|${o.y})`}</a></td>
              <td className="nowrap"><ResIcon r={o.sellResource} /> {fmt(o.sellAmount)}</td>
              <td className="nowrap"><ResIcon r={o.buyResource} /> {fmt(o.buyAmount)}</td>
              <td>{o.remaining}×</td>
              <td>{o.maxDistance || "-"}</td>
              <td><a href="#" onClick={(e) => { e.preventDefault(); act(() => api.marketCancelOffer(o.id)); }}>» Cancel</a></td>
            </tr>
          ))}
          {own.length === 0 && (
            <tr>
              <td colSpan="6">You have no offers.</td>
            </tr>
          )}
        </tbody>
      </table>
    </>
  );
}

export function MarketScreen({ village, villages = [], go }) {
  const parts = hashParts();
  const raw = parts[0] === "market" ? parts[1] ?? "send" : "send";
  const presetId = /^\d+$/.test(raw) ? Number(raw) : null;
  const mode = MODES.some(([m]) => m === raw) ? raw : "send";
  const market = useMarket(village.id);
  const level = levelOf(village, "MARKET");
  const img = level >= 20 ? 3 : level >= 5 ? 2 : 1;

  return (
    <>
      {market.error && <PreErrorBox text={market.error} />}
      <table width="100%">
        <tbody>
          <tr>
            <td>
              <img src={`/graphic/big_buildings/market${img}.png`} alt="Market" />
            </td>
            <td width="100%">
              <h2>{`Market (Level ${level})`}</h2>
              On the market you can trade with other players.
            </td>
          </tr>
        </tbody>
      </table>
      <br />
      {level === 0 ? (
        <p>You need to build a market first.</p>
      ) : (
        <>
          <table className="vis modemenu" width="100%">
            <tbody>
              <tr>
                {MODES.map(([m, label]) => (
                  <td key={m} className={mode === m ? "selected" : undefined} style={{ minWidth: 80 }}>
                    <a href="#" onClick={(e) => go(e, "market:" + m)}>{label}</a>
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
          <br />
          {mode === "send" && <SendMode key={presetId ?? "none"} village={village} villages={villages} market={market} go={go} presetId={presetId} />}
          {mode === "other_offer" && <BuyMode market={market} go={go} />}
          {mode === "own_offer" && <SellMode market={market} go={go} />}
          {mode === "traders" && <ShipmentsTable shipments={market.state?.shipments} go={go} title="Transports" />}
          {mode === "traders" && market.state && market.state.shipments.length === 0 && <p>No merchants are on the road.</p>}
        </>
      )}
    </>
  );
}
