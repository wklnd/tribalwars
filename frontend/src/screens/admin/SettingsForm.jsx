import { Card, Chip, Switch } from "./kit.jsx";

const defaultOf = (s) => s.defaultValue ?? s.default;

// values are kept as strings (the backend reports "effective values ... as strings")
export function initialValues(catalog, world) {
  const out = {};
  for (const s of catalog?.settings ?? []) {
    const v = world?.settings?.[s.key] ?? defaultOf(s);
    out[s.key] = v == null ? "" : String(v);
  }
  return out;
}

function Control({ s, value, onChange, id }) {
  if (s.type === "boolean") {
    return <Switch id={id} checked={String(value) === "true"} onChange={(on) => onChange(on ? "true" : "false")} label={String(value) === "true" ? "On" : "Off"} />;
  }
  if (s.type === "select") {
    return (
      <select id={id} className="inp" value={value} onChange={(e) => onChange(e.target.value)}>
        {(s.options ?? []).map((o) => (
          <option key={String(o)} value={String(o)}>
            {String(o)}
          </option>
        ))}
      </select>
    );
  }
  return <input id={id} className="inp" type="text" inputMode={s.type === "number" ? "decimal" : undefined} value={value} onChange={(e) => onChange(e.target.value)} />;
}

// one card per settings group, one labelled row per setting
export function SettingsGroups({ catalog, values, onChange }) {
  const groups = [];
  for (const s of catalog?.settings ?? []) {
    let g = groups.find((x) => x.name === s.group);
    if (!g) groups.push((g = { name: s.group, items: [] }));
    g.items.push(s);
  }
  return groups.map((g) => (
    <Card key={g.name} title={g.name} className="settings" flush>
      {g.items.map((s) => {
        const id = "setting_" + s.key;
        const range = s.type === "number" && (s.min != null || s.max != null) ? `${s.min ?? ""} to ${s.max ?? ""}` : "";
        return (
          <div className="setrow" key={s.key}>
            <div className="setrow-l">
              <label htmlFor={id}>
                {s.label}
                {s.implemented === false ? <Chip tone="muted" title="Stored, but the game does not use it yet">not active yet</Chip> : null}
              </label>
              {s.help ? <p>{s.help}</p> : null}
            </div>
            <div className="setrow-c">
              <Control s={s} id={id} value={values[s.key] ?? ""} onChange={(v) => onChange(s.key, v)} />
              {range ? <span className="hint">{range}</span> : null}
            </div>
          </div>
        );
      })}
    </Card>
  ));
}
