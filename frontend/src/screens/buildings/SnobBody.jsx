import { Fragment, useState } from "react";
import { fmtDuration } from "../../lib/data";
import { COIN_COST, SNOB_UNIT, formatWhen, levelOfId, nobleRecruitSeconds, showMessage, useLiveRes } from "../../lib/buildings";
import { UNIT_BY_ID, useNowTick } from "../../lib/military";
import { TrainQueue, queueEvents } from "../TrainScreen";
import { useUnitPopup } from "../military/UnitPopup";

/* game/snob/coin_{index,navi,train,coin,queue}.php (world noblemanSystem "coins").
   The counters (coins, limit, existing/educating noblemen, conquered villages) come from the backend (village.nobles). */

const MODES = [
  ["train", "Create"],
  ["coin", "Mint gold coins"],
];

/* The template drops an <input type="hidden"> straight into the <tr>; the HTML parser keeps it there. */
const hiddenInRow = (id) => (tr) => {
  if (tr && !tr.querySelector("input#" + id)) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.id = id;
    input.value = "1";
    tr.appendChild(input);
  }
};
const snobHidden = hiddenInRow("snob_0");

/* Text::formatInt(n, '<span class="grey">.</span>'): the engine wraps the delimiter in yet another grey span. */
function NestedGrey({ n }) {
  const str = String(Math.floor(n));
  if (str.length <= 3) return <>{str}</>;
  const parts = [];
  for (let i = str.length; i > 0; i -= 3) parts.unshift(str.slice(Math.max(0, i - 3), i));
  return (
    <>
      {parts.map((p, i) => (
        <Fragment key={i}>
          {i > 0 && (
            <span className="grey">
              <span className="grey">.</span>
            </span>
          )}
          {p}
        </Fragment>
      ))}
    </>
  );
}

/* coin_train.php counters */
const NO_NOBLES = { coins: 0, limit: 0, existing: 0, inProduction: 0, conquered: 0, possible: 0, coinsMissing: 1, coinsAlready: 0 };

function TrainMode({ village, level, popup, onTrain, onCancelTrain, onMintCoin }) {
  const NOBLE = { ...NO_NOBLES, ...(village.nobles ?? {}) };
  const now = useNowTick();
  const possible = Math.max(0, NOBLE.possible);
  const errorAg = possible <= 0 ? "No more noblemen can be produced" : null;
  const coinsMissing = NOBLE.coinsMissing;
  const coinsAlready = NOBLE.coinsAlready;
  const events = queueEvents(village, now).filter((e) => e.unit === "snob");
  const live = useLiveRes(village);
  const cap = village.warehouseCapacity;
  const enough = Object.entries(COIN_COST).every(([r, v]) => live[r] >= v);
  let coinError = null;
  if (Math.max(...Object.values(COIN_COST)) > cap) coinError = "The warehouse is too small";
  else if (!enough) {
    const secs = Math.max(...Object.entries(COIN_COST).map(([r, v]) => ((v - live[r]) * 3600) / live.rate[r]));
    coinError = `Resources available ${formatWhen(live.now + secs * 1000)}`;
  }
  const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
  const unitTime = Math.max(1, Math.round(nobleRecruitSeconds(SNOB_UNIT.time, level) / (village.worldSpeed || 1)));
  const mint = (e) => {
    e.preventDefault();
    onMintCoin?.();
  };
  const produce = (e) => {
    e.preventDefault();
    if (village.wood < SNOB_UNIT.wood || village.clay < SNOB_UNIT.stone || village.iron < SNOB_UNIT.iron) {
      showMessage("Not enough resources available", "error");
    } else if (village.populationCapacity - village.populationUsed < SNOB_UNIT.population) {
      showMessage("Farm too small", "error");
    } else {
      onTrain?.("SNOB", 1);
    }
  };

  return (
    <>
      {events.length > 0 && <TrainQueue events={events} bid="snob" now={now} onCancel={onCancelTrain} />}
      <table className="vis">
        <tbody>
          <tr>
            <th>Unit</th>
            <th>Requirements</th>
            <th>In the village/total</th>
            <th>Produce</th>
          </tr>
          <tr ref={snobHidden}>
            <td className="nowrap">
              <a href="#" className="unit_link" onClick={(e) => popup.open(e, "snob")}>
                <img src="/graphic/unit/unit_snob.png" title="Nobleman" alt="Nobleman" />
                {" " + UNIT_BY_ID.snob.name}
              </a>
            </td>
            <td className="nowrap">
              {Object.entries(SNOB_UNIT).map(([name, value]) => (
                <Fragment key={name}>
                  <span className={`icon header ${name}`}> </span>
                  {name === "time" ? fmtDuration(unitTime * 1000) : value}{" "}
                </Fragment>
              ))}
            </td>
            <td>{`${village.units?.SNOB ?? 0}/${NOBLE.existing}`}</td>
            {errorAg == null ? (
              <td>
                <a href="#" className="btn btn-recruit" onClick={produce}>
                  Produce Unit
                </a>
              </td>
            ) : (
              <td className="inactive">{errorAg}</td>
            )}
          </tr>
        </tbody>
      </table>
      <br />
      <h4>Number of nobleman that can be educated</h4>
      <table className="vis">
        <tbody>
          <tr>
            <td>Noblemen limit:</td>
            <td>{NOBLE.limit}</td>
          </tr>
          <tr>
            <td>- Existing noblemen:</td>
            <td>{NOBLE.existing}</td>
          </tr>
          <tr>
            <td>- Noblemen in education:</td>
            <td>{NOBLE.inProduction}</td>
          </tr>
          <tr>
            <td>- Number of conquered villages:</td>
            <td>{NOBLE.conquered}</td>
          </tr>
          <tr>
            <th>You can still educate:</th>
            <th>{possible}</th>
          </tr>
        </tbody>
      </table>
      <br />
      <table>
        <tbody>
          <tr>
            <td>
              <img src="/graphic/gold_big.png" alt="Coins" />
            </td>
            <td>
              <h4>Coins</h4>
              <p>
                To educate more noblemen you have to mint gold coins. The more gold coins you have, the more villages you can have.
              </p>
            </td>
          </tr>
        </tbody>
      </table>
      <br />
      <table className="vis">
        <tbody>
          <tr>
            <th colSpan="2" width="350">
              Coins
            </th>
          </tr>
          <tr>
            <td>Overall:</td>
            <td>{NOBLE.coins}</td>
          </tr>
          <tr>
            <td style={{ background: "none" }}></td>
          </tr>
          <tr>
            <th colSpan="2">{UNIT_BY_ID.snob.name}</th>
          </tr>
          <tr>
            <td>Current limit of noblemen:</td>
            <td>{NOBLE.limit}</td>
          </tr>
          <tr>
            <td>{`Still missing for the limit of noblemen ${NOBLE.limit + 1}:`}</td>
            <td className="nowrap">{plural(coinsMissing, "Coin", "Coins")}</td>
          </tr>
          <tr>
            <td>{`Already saved for the limit of noblemen ${NOBLE.limit + 1}:`}</td>
            <td className="nowrap">{plural(coinsAlready, "Coin", "Coins")}</td>
          </tr>
        </tbody>
      </table>
      <br />
      <table className="vis">
        <tbody>
          <tr>
            <th>Requirements</th>
            <th>Mint</th>
          </tr>
          <tr>
            <td>
              {Object.entries(COIN_COST).map(([name, value]) => (
                <Fragment key={name}>
                  <span className={`icon header ${name}`}></span> <NestedGrey n={value} />{" "}
                </Fragment>
              ))}
            </td>
            <td>
              {coinError == null ? (
                <a href="#" className="btn" onClick={mint}>
                  Mint gold coins
                </a>
              ) : (
                <span className="inactive">{coinError}</span>
              )}
            </td>
          </tr>
        </tbody>
      </table>
    </>
  );
}

function CoinMode({ village, onMintCoin }) {
  const live = useLiveRes(village);
  const [pageSize, setPageSize] = useState(5);
  const [pageSizeInput, setPageSizeInput] = useState("5");
  const [amountSel, setAmount] = useState(null);
  const [picked, setPicked] = useState({});
  const vid = village.id;

  // every village of the player: the current one ticks live, the others show what the server last computed
  const gmOf = (r, cap) => (Math.max(...Object.values(COIN_COST)) > cap ? 0 : Math.floor(Math.min(...Object.entries(COIN_COST).map(([k, v]) => r[k] / v))));
  const maxGm = gmOf(live, village.warehouseCapacity);
  const villages = (village.myVillages?.length ? village.myVillages : [{ id: vid, name: village.name }]).map((v) =>
    v.id === vid
      ? { id: vid, name: village.name, res: { wood: live.wood, stone: live.stone, iron: live.iron }, storage: village.warehouseCapacity, maxGm }
      : {
          id: v.id,
          name: v.name,
          res: { wood: v.wood, stone: v.clay, iron: v.iron },
          storage: v.warehouseCapacity,
          maxGm: gmOf({ wood: v.wood, stone: v.clay, iron: v.iron }, v.warehouseCapacity),
        }
  );
  const gmSingleMax = Math.max(0, ...villages.map((v) => v.maxGm));
  const amount = amountSel ?? String(gmSingleMax); // the first <option> is selected by default
  const pages = Math.max(1, Math.ceil(villages.length / pageSize));
  const [from, setFrom] = useState(0);

  const amountOptions = [];
  for (let c = gmSingleMax; c >= gmSingleMax * -1 + (gmSingleMax === 0 ? 0 : 1); --c) amountOptions.push(c);
  const sum = villages.reduce((s, v) => s + (picked[v.id] ?? 0), 0);

  const choose = () => {
    const a = parseInt(amount, 10);
    const next = {};
    for (const v of villages) {
      if (v.maxGm <= 0) continue;
      // Snob.Coin.setCoinAmount(): select option index `a` (negative: counted from the end)
      let at = a < 0 ? v.maxGm + a : a > v.maxGm ? v.maxGm : a;
      if (at < 0) at = 0;
      next[v.id] = at;
    }
    setPicked(next);
  };

  const mint = async () => {
    // the original's "Mint gold coins" submits every chosen village; one request per village here
    const chosen = villages.filter((v) => (picked[v.id] ?? 0) > 0);
    if (chosen.length === 0) return showMessage("No villages selected", "error");
    for (const v of chosen) if ((await onMintCoin?.(picked[v.id], v.id)) !== true) break;
    setPicked({});
  };
  const nav = (e, f) => {
    e.preventDefault();
    setFrom(f);
  };

  return (
    <>
      <div className="vis_item" style={{ textAlign: "center" }}>
        <strong className="group_tooltip">&gt;all&lt;</strong>
      </div>

      <table className="vis" width="100%">
        <tbody>
          <tr>
            <td align="center" colSpan="2">
              {Array.from({ length: pages }, (_, i) => {
                const value = i * pageSize;
                return value === from ? (
                  <strong key={i}>{`>${i + 1}<`}</strong>
                ) : (
                  <a key={i} className="paged-nav-item" href="#" onClick={(e) => nav(e, value)}>
                    {`[${i + 1}]`}
                  </a>
                );
              })}
              {from >= 0 ? (
                <a className="paged-nav-item" href="#" onClick={(e) => nav(e, -1)}>
                  [all]
                </a>
              ) : (
                <strong>&gt;all&lt;</strong>
              )}
            </td>
          </tr>
        </tbody>
      </table>

      <table className="vis overview_table" id="coin_overview_table" width="100%">
        <tbody>
          <tr>
            <td colSpan="3">
              <input className="mint_multi_button btn" type="button" value="Mint gold coins" onClick={mint} />
              (<span id="selectedBunches_top">{sum}</span> x <img alt="" className="" src="/graphic/gold.png" title="" />)
            </td>
            <td>
              <select className="coin_amount" name="coin_amount" value={amount} onChange={(e) => setAmount(e.target.value)}>
                {amountOptions.map((c) => (
                  <option key={c} value={String(c)}>
                    {c >= 0 ? `${c}x` : `Max ${c}x`}
                  </option>
                ))}
              </select>
              <input className="btn" id="select_anchor_top" type="button" value="Chose" onClick={choose} />
            </td>
          </tr>

          <tr>
            <th>Village</th>
            <th>Resources</th>
            <th>Warehouse</th>
            <th>Choose Amount</th>
          </tr>

          {villages.map((v) => (
            <tr key={v.id} className={vid === v.id ? "selected" : undefined} id={`village_${v.id}`}>
              <td>
                <a href="#" onClick={(e) => e.preventDefault()}>
                  {v.name}
                </a>
              </td>

              <td className="nowrap resources">
                {Object.entries(v.res).map(([n, r]) => (
                  <span key={n} className={`res ${n}`}>
                    <NestedGrey n={r} />
                  </span>
                ))}
              </td>

              <td>{v.storage}</td>

              <td>
                {v.maxGm > 0 && (
                  <select
                    className="select_coins"
                    id={`id_${v.id}`}
                    name={`id_${v.id}`}
                    value={String(picked[v.id] ?? 0)}
                    onChange={(e) => setPicked({ ...picked, [v.id]: parseInt(e.target.value, 10) })}
                  >
                    <option value="0">- None -</option>
                    {Array.from({ length: v.maxGm }, (_, i) => i + 1).map((c) => (
                      <option key={c} value={String(c)}>
                        {`${c}x (${Object.values(COIN_COST).map((p) => p * c).join(", ")})`}
                      </option>
                    ))}
                  </select>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <form
        action={`game.php?village=${vid}&action=change_page_size&screen=snob`}
        method="post"
        onSubmit={(e) => {
          e.preventDefault();
          const n = parseInt(pageSizeInput, 10);
          if (n > 0) {
            setPageSize(n);
            setFrom(0);
          }
        }}
      >
        <table className="vis">
          <tbody>
            <tr>
              <th colSpan="2">Villages per page:</th>
              <td>
                <input
                  name="page_size"
                  style={{ width: 50 }}
                  type="text"
                  value={pageSizeInput}
                  onChange={(e) => setPageSizeInput(e.target.value)}
                />
              </td>
              <td>
                <input className="btn" type="submit" value="OK" />
              </td>
            </tr>
          </tbody>
        </table>
      </form>
    </>
  );
}

/* coin_index.php: mode menu on the left, the selected mode on the right. */
export function SnobBody({ village, mode, go, onTrain, onCancelTrain, onMintCoin }) {
  const popup = useUnitPopup();
  const level = levelOfId(village, "snob");
  const current = MODES.some(([m]) => m === mode) ? mode : "train";
  return (
    <>
      <table>
        <tbody>
          <tr>
            <td valign="top">
              <table className="vis modemenu">
                <tbody>
                  {MODES.map(([m, label]) => (
                    <tr key={m}>
                      <td className={m === current ? "selected" : undefined} style={{ minWidth: 80 }}>
                        <a
                          href={`#snob:${m}`}
                          onClick={(e) => (go ? go(e, `snob:${m}`) : e.preventDefault())}
                        >{`${label} `}</a>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </td>
            <td valign="top">
              {current === "train" ? (
                <TrainMode village={village} level={level} popup={popup} onTrain={onTrain} onCancelTrain={onCancelTrain} onMintCoin={onMintCoin} />
              ) : (
                <CoinMode village={village} onMintCoin={onMintCoin} />
              )}
            </td>
          </tr>
        </tbody>
      </table>
      {popup.node}
    </>
  );
}
