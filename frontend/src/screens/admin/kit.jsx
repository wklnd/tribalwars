// Small UI kit of the admin panel (all styled by /admin-ui.css under the .adm root).
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { clampInt } from "./game.js";

export const cx = (...a) => a.filter(Boolean).join(" ");

// ---- toasts ------------------------------------------------------------------------------------------------
const ToastCtx = createContext(() => {});
export const useToast = () => useContext(ToastCtx);

export function ToastHost({ children }) {
  const [items, setItems] = useState([]);
  const seq = useRef(0);
  const push = useCallback((text, tone = "ok") => {
    const id = ++seq.current;
    setItems((l) => [...l.slice(-3), { id, text, tone }]);
    window.setTimeout(() => setItems((l) => l.filter((t) => t.id !== id)), tone === "bad" ? 7000 : 4200);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts" aria-live="polite">
        {items.map((t) => (
          <div key={t.id} className={cx("toast", t.tone)} role="status">
            <Icon name={t.tone === "bad" ? "alert" : "check"} />
            <span>{t.text}</span>
            <button type="button" className="toast-x" onClick={() => setItems((l) => l.filter((x) => x.id !== t.id))} aria-label="Dismiss">
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

// ---- icons (inline, stroke based) --------------------------------------------------------------------------
const PATHS = {
  dashboard: "M3 13h8V3H3zm10 8h8V11h-8zM3 21h8v-6H3zM13 3v6h8V3z",
  users: "M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0zM4 21a8 8 0 0 1 16 0",
  globe: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM3 12h18M12 3c2.6 2.7 3.9 5.7 3.9 9s-1.3 6.300-3.900 9c-2.600-2.700-3.900-5.700-3.900-9S9.400 5.700 12 3z",
  map: "M9 4 3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14",
  plus: "M12 5v14M5 12h14",
  minus: "M5 12h14",
  check: "M5 12.500l4.500 4.500L19 7.500",
  alert: "M12 8v5M12 16.500v.01M10.300 3.900 2.400 17.500A2 2 0 0 0 4.100 20.500h15.800a2 2 0 0 0 1.700-3L13.700 3.900a2 2 0 0 0-3.400 0z",
  trash: "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3",
  edit: "M4 20h4L19 9l-4-4L4 16zM13.500 6.500l4 4",
  back: "M15 5l-7 7 7 7",
  logout: "M9 4H5v16h4M16 8l4 4-4 4M20 12H9",
  dice: "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM8.500 8.500v.01M15.500 8.500v.01M12 12v.01M8.500 15.500v.01M15.500 15.500v.01",
  wand: "M15 4l5 5L9 20l-5-5zM12 7l5 5",
  reset: "M4 12a8 8 0 1 0 3-6.200M4 4v5h5",
  menu: "M4 6h16M4 12h16M4 18h16",
  lock: "M6 11h12v9H6zM8 11V8a4 4 0 0 1 8 0v3",
  save: "M5 4h11l3 3v13H5zM8 4v5h7V4M8 20v-6h8v6",
  db: "M4 6c0-1.700 3.600-3 8-3s8 1.300 8 3-3.600 3-8 3-8-1.300-8-3zM4 6v6c0 1.700 3.600 3 8 3s8-1.300 8-3V6M4 12v6c0 1.700 3.600 3 8 3s8-1.300 8-3v-6",
  bolt: "M13 3 5 14h6l-1 7 8-11h-6z",
  key: "M14 10a4 4 0 1 0-3.500 4L14 17.500h2V20h2.500v-2.500H20V15l-6.500-6.500",
  shield: "M12 3 4 6v6c0 4.500 3.300 7.800 8 9 4.700-1.200 8-4.500 8-9V6z",
  sword: "M14 4h6v6L10 20l-2-2-2 2-2-2 2-2-2-2zM6 14l4 4",
  refresh: "M20 12a8 8 0 1 1-2.300-5.600M20 4v5h-5",
  arrow: "M5 12h14M13 6l6 6-6 6",
  external: "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6",
};
export function Icon({ name, size = 16 }) {
  return (
    <svg className="ico" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={PATHS[name] ?? ""} />
    </svg>
  );
}

// ---- layout ------------------------------------------------------------------------------------------------
export function PageHead({ title, sub, actions, back }) {
  return (
    <div className="pagehead">
      <div className="ph-text">
        {back ? (
          <a className="ph-back" href={back.href} onClick={back.onClick}>
            <Icon name="back" size={14} /> {back.label}
          </a>
        ) : null}
        <h1>{title}</h1>
        {sub ? <p>{sub}</p> : null}
      </div>
      {actions ? <div className="ph-actions">{actions}</div> : null}
    </div>
  );
}

export function Card({ title, sub, actions, children, flush, className, id }) {
  return (
    <section className={cx("card", flush && "flush", className)} id={id}>
      {title || actions ? (
        <header className="card-h">
          <div>
            {title ? <h3>{title}</h3> : null}
            {sub ? <p>{sub}</p> : null}
          </div>
          {actions ? <div className="card-a">{actions}</div> : null}
        </header>
      ) : null}
      <div className="card-b">{children}</div>
    </section>
  );
}

export function Chip({ tone = "neutral", children, title }) {
  return (
    <span className={cx("chip", tone)} title={title}>
      {children}
    </span>
  );
}

export function Btn({ variant, size, icon, busy, children, className, type = "button", ...rest }) {
  return (
    <button type={type} className={cx("btn", variant, size, className)} disabled={busy || rest.disabled} {...rest}>
      {icon ? <Icon name={icon} size={size === "sm" ? 14 : 16} /> : null}
      {children}
    </button>
  );
}

// an <a> that navigates inside the admin (hash routing is done by AdminPage's `go`)
export function NavLink({ go, to, children, className, ...rest }) {
  return (
    <a
      href={"#admin" + (to ? "/" + to : "")}
      className={className}
      onClick={(e) => {
        if (e.metaKey || e.ctrlKey || e.shiftKey || e.button) return;
        e.preventDefault();
        go(to);
      }}
      {...rest}
    >
      {children}
    </a>
  );
}

// ---- states ------------------------------------------------------------------------------------------------
export function Loading({ label = "Loading" }) {
  return (
    <div className="loading">
      <span className="spin" /> {label}
    </div>
  );
}

export function Notice({ tone = "info", children, action }) {
  return (
    <div className={cx("notice", tone)}>
      <Icon name={tone === "bad" ? "alert" : tone === "ok" ? "check" : tone === "warn" ? "alert" : "bolt"} />
      <div className="notice-t">{children}</div>
      {action}
    </div>
  );
}

export const ErrorNote = ({ children }) => <Notice tone="bad">{children}</Notice>;

export function Empty({ title, children }) {
  return (
    <div className="empty">
      <strong>{title}</strong>
      {children ? <p>{children}</p> : null}
    </div>
  );
}

// ---- form controls -----------------------------------------------------------------------------------------
export function Field({ label, hint, htmlFor, children, className }) {
  return (
    <div className={cx("fld", className)}>
      {label ? (
        <label className="lbl" htmlFor={htmlFor}>
          {label}
        </label>
      ) : null}
      {children}
      {hint ? <div className="hint">{hint}</div> : null}
    </div>
  );
}

// number stepper: [-] 12 [+] ; keeps a text draft while typing, commits clamped integers
export function Stepper({ value, onChange, min = 0, max = 999999, step = 1, id, disabled, width, ariaLabel, big }) {
  const [draft, setDraft] = useState(null);
  const shown = draft ?? String(value ?? 0);
  const commit = (n) => onChange(clampInt(n, min, max));
  return (
    <div className={cx("stp", disabled && "off", big && "big")} style={width ? { width } : undefined}>
      <button type="button" tabIndex={-1} disabled={disabled || value <= min} onClick={() => commit(value - step)} aria-label="Decrease">
        <Icon name="minus" size={14} />
      </button>
      <input
        id={id}
        inputMode="numeric"
        value={shown}
        disabled={disabled}
        aria-label={ariaLabel}
        onFocus={(e) => e.target.select()}
        onChange={(e) => {
          const t = e.target.value.replace(/[^\d]/g, "");
          setDraft(t);
          if (t !== "") commit(Number(t));
        }}
        onBlur={() => setDraft(null)}
        onKeyDown={(e) => {
          if (e.key === "ArrowUp") { e.preventDefault(); commit(value + step); setDraft(null); }
          if (e.key === "ArrowDown") { e.preventDefault(); commit(value - step); setDraft(null); }
        }}
      />
      <button type="button" tabIndex={-1} disabled={disabled || value >= max} onClick={() => commit(value + step)} aria-label="Increase">
        <Icon name="plus" size={14} />
      </button>
    </div>
  );
}

export function Switch({ checked, onChange, id, label }) {
  return (
    <label className="switch" htmlFor={id}>
      <input id={id} type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span className="sw-track" />
      {label ? <span className="sw-label">{label}</span> : null}
    </label>
  );
}

// segmented control
export function Segmented({ value, onChange, options, ariaLabel }) {
  return (
    <div className="seg" role="group" aria-label={ariaLabel}>
      {options.map((o) => (
        <button key={o.value} type="button" className={o.value === value ? "on" : ""} aria-pressed={o.value === value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Tabs({ value, onChange, tabs }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.value} type="button" role="tab" aria-selected={t.value === value} className={t.value === value ? "on" : ""} onClick={() => onChange(t.value)}>
          {t.icon ? <Icon name={t.icon} /> : null}
          {t.label}
          {t.badge ? <span className="tab-badge">{t.badge}</span> : null}
        </button>
      ))}
    </div>
  );
}

// two-thumb range slider (values in [min,max])
export function DualRange({ min = 0, max = 100, lo, hi, onChange, step = 1 }) {
  const pct = (v) => ((v - min) / (max - min)) * 100;
  return (
    <div className="dual">
      <div className="dual-track" />
      <div className="dual-fill" style={{ left: pct(lo) + "%", right: 100 - pct(hi) + "%" }} />
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={lo}
        aria-label="Minimum development"
        style={{ zIndex: lo > max - (max - min) * 0.1 ? 3 : 2 }}
        onChange={(e) => onChange(Math.min(Number(e.target.value), hi), hi)}
      />
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={hi}
        aria-label="Maximum development"
        style={{ zIndex: 2 }}
        onChange={(e) => onChange(lo, Math.max(Number(e.target.value), lo))}
      />
    </div>
  );
}

// ---- modal -------------------------------------------------------------------------------------------------
export function Modal({ title, children, onClose, footer, width }) {
  useEffect(() => {
    const onKey = (e) => e.key === "Escape" && onClose?.();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose?.()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} style={width ? { maxWidth: width } : undefined}>
        <h3>{title}</h3>
        <div className="modal-b">{children}</div>
        {footer ? <div className="modal-f">{footer}</div> : null}
      </div>
    </div>
  );
}

export function ConfirmDialog({ title, children, confirmLabel = "Confirm", danger, busy, onConfirm, onCancel, disabled }) {
  return (
    <Modal
      title={title}
      onClose={onCancel}
      footer={
        <>
          <Btn onClick={onCancel}>Cancel</Btn>
          <Btn variant={danger ? "danger" : "primary"} busy={busy} disabled={disabled} onClick={onConfirm}>
            {confirmLabel}
          </Btn>
        </>
      }
    >
      {children}
    </Modal>
  );
}
