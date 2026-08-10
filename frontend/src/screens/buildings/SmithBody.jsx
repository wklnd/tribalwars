import { fmtDuration } from "../../lib/data";
import { SMITH_TECH, levelOfId, researchOf, researchSeconds, showMessage, techState, techType, useLiveRes } from "../../lib/buildings";
import { TYPE_TO_ID, UNIT_BY_ID, fmtBuildTime, fmtOnTime, useNowTick } from "../../lib/military";
import { useUnitPopup } from "../military/UnitPopup";

/* game/smith/{index,queue,main}.php: the research queue (queue.php) above the technology table (main.php). */
export function SmithBody({ village, onResearch, onCancelResearch }) {
  const live = useLiveRes(village);
  const popup = useUnitPopup();
  const now = useNowTick();
  const smith = levelOfId(village, "smith");
  const speed = village.worldSpeed || 1;
  const queue = village.researchQueue ?? [];

  const research = (e, tech) => {
    e.preventDefault();
    const st = techState(village, tech);
    if (!st.costs) return showMessage(st.error, "error");
    if (live.wood < st.costs.wood || live.clay < st.costs.stone || live.iron < st.costs.iron) {
      return showMessage("Not enough resources available", "error");
    }
    onResearch?.(techType(tech.id));
    return false;
  };

  // when each order finishes: the running one at completesAt, the waiting ones one after another
  let finish = now;
  const events = queue.map((q, i) => {
    const active = i === 0 && !!q.completesAt;
    const end = active ? new Date(q.completesAt).getTime() : finish + q.durationSeconds * 1000;
    finish = end;
    return { ...q, active, duration: Math.max(0, end - (active ? now : end - q.durationSeconds * 1000)), finish: end };
  });

  return (
    <>
      {events.length > 0 && (
        <div id="current_research">
          <table id="build_queue" className="vis">
            <thead>
              <tr>
                <th width="250">Research assignment</th>
                <th width="100">Duration</th>
                <th width="150">Completion</th>
                <th>Cancellation</th>
                <th style={{ background: "none" }}></th>
              </tr>
            </thead>
            <tbody>
              {events.map((e) => (
                <tr key={e.id} className={e.active ? "lit nodrag" : "sortable_row nowrap"}>
                  <td width="250" className="nowrap lit-item">
                    {`${UNIT_BY_ID[TYPE_TO_ID[e.type]]?.name ?? e.type} (Level 1)`}
                  </td>
                  <td width="100" className="nowrap lit-item">
                    {e.active ? <span className="timer">{fmtBuildTime(Math.max(1000, e.duration))}</span> : fmtBuildTime(e.duration)}
                  </td>
                  <td className="lit-item" width="170">{fmtOnTime(e.finish)}</td>
                  <td className="lit-item">
                    <a className="btn btn-cancel" href="#" onClick={(ev) => { ev.preventDefault(); onCancelResearch?.(e); }}>
                      cancel
                    </a>
                  </td>
                  <td className="lit-item" style={{ backgroundColor: "transparent" }}></td>
                </tr>
              ))}
            </tbody>
          </table>
          <br />
        </div>
      )}
      <table style={{ width: "100%" }}>
        <tbody>
          <tr>
            <td></td>
          </tr>
        </tbody>
      </table>
      <table className="vis tall" id="tech_list" style={{ width: "100%" }}>
        <tbody>
          <tr>
            <th>technology</th>
            <th colSpan="3">Requirements</th>
            <th>Research time</th>
            <th>Research</th>
          </tr>
          {SMITH_TECH.map((tech) => {
            const st = techState(village, tech);
            const suffix = st.cross ? "_cross" : st.grey ? "_grey" : "";
            return (
              <tr key={tech.id}>
                <td>
                  <div className={`float_left unit_sprite unit_sprite_smaller ${tech.id}${suffix}`}></div>
                  &nbsp;
                  <a href="#" className="unit_link" onClick={(e) => popup.open(e, tech.id)}>
                    {UNIT_BY_ID[tech.id].name + (st.level ? ` (${st.level})` : "")}
                  </a>
                </td>
                {st.error ? (
                  <td colSpan="5" className="inactive">
                    {st.error}
                  </td>
                ) : (
                  <>
                    {["wood", "stone", "iron"].map((res) => (
                      <td key={res}>
                        <span
                          className={"nowrap" + (live[res] < st.costs[res] ? " warn" : "")}
                          id={`${tech.id}_cost_${res}`}
                        >
                          <span className={`icon header ${res}`}></span>
                          {st.costs[res]}
                        </span>
                      </td>
                    ))}
                    <td>
                      <span className="icon header time"></span>
                      {fmtDuration((researchOf(village, tech.id)?.seconds ?? Math.max(1, Math.round(researchSeconds(tech, smith) / speed))) * 1000)}
                    </td>
                    <td>
                      <a className="btn btn-research" href="#" onClick={(e) => research(e, tech)}>
                        {`Level ${st.level + 1}`}
                      </a>
                    </td>
                  </>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
      <br />
      {popup.node}
    </>
  );
}
