// Shared editors of the village creator and the village editor: building level grid, troop counts, resources.
import { Icon, Stepper, cx } from "./kit.jsx";
import {
  BUILDING_BY_TYPE,
  BUILDING_GROUPS,
  RESOURCES,
  UNITS,
  buildingImg,
  buildingName,
  describeMissing,
  maxLevel,
  missingForBuilding,
  missingForUnit,
} from "./game.js";

export function BuildingGrid({ catalog, levels, onChange }) {
  return (
    <div className="bgroups">
      {BUILDING_GROUPS.map((g) => (
        <div key={g.name}>
          <h4 className="grp">{g.name}</h4>
          <div className="bgrid">
            {g.types.map((type) => {
              const level = levels[type] ?? 0;
              const max = maxLevel(catalog, type);
              const missing = missingForBuilding(catalog, levels, type);
              return (
                <div key={type} className={cx("bt", level === 0 && "dim", missing.length && "warn")}>
                  <div className="bt-top">
                    <span className="bt-img">
                      <img src={buildingImg(type, level)} alt="" width="35" height="24" />
                    </span>
                    <div className="bt-name">
                      <strong>{BUILDING_BY_TYPE[type]?.name ?? type}</strong>
                      <span>max {max}</span>
                    </div>
                  </div>
                  <Stepper value={level} min={0} max={max} onChange={(n) => onChange(type, n)} ariaLabel={buildingName(type) + " level"} />
                  {missing.length ? (
                    <div className="bt-warn" title={"Missing: " + describeMissing(missing)}>
                      <Icon name="alert" size={13} />
                      <span>
                        Needs {missing.map((m, i) => (
                          <span key={m.type}>{i ? ", " : ""}{buildingName(m.type)} <b>{m.need}</b></span>
                        ))}
                      </span>
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

export function TroopList({ levels, units, onChange }) {
  return (
    <div className="troops">
      {UNITS.map((u) => {
        const count = units[u.type] ?? 0;
        const missing = missingForUnit(levels, u.type);
        const locked = missing.length > 0 && count === 0;
        return (
          <div key={u.type} className={cx("tr", locked && "locked", missing.length && count > 0 && "warn")}>
            <img src={u.img} alt="" width="40" height="40" />
            <div className="tr-name">
              <strong>{u.name}</strong>
              {missing.length ? (
                <span className="tr-need">
                  <Icon name={locked ? "lock" : "alert"} size={12} /> {locked ? "Locked: needs " : "Needs "}
                  {describeMissing(missing)}
                </span>
              ) : (
                <span className="tr-ok">Ready to train</span>
              )}
            </div>
            <Stepper value={count} min={0} max={1000000} step={10} disabled={locked} onChange={(n) => onChange(u.type, n)} ariaLabel={u.name + " count"} />
          </div>
        );
      })}
    </div>
  );
}

export function ResourceFields({ res, onChange, placeholder = "" }) {
  return (
    <div className="resfields">
      {RESOURCES.map((r) => (
        <label key={r.key} className="resfield">
          <img src={r.img} alt="" width="18" height="16" />
          <span>{r.label}</span>
          <input
            className="inp"
            inputMode="numeric"
            value={res[r.key]}
            placeholder={placeholder}
            onChange={(e) => onChange(r.key, e.target.value.replace(/[^\d]/g, ""))}
          />
        </label>
      ))}
    </div>
  );
}
