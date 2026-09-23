import { useRef, useState } from "react";

// <div class="register"> (templates/twlan/controllers/index/container_index.php) with the validation UX of
// StartPage.register (js/start.js): a .validation span per field (validation-none / -true / -false) and an
// .error-message box that is shown only while a single field is invalid (or for the first invalid one).
// The original validates on the server (/page/register/validate); the messages below are the ones it returns
// (config/language/en.json register.*). "user name taken" can only be known by the backend, so it arrives as the
// error of onRegister and is put into the same boxes.
const FIELDS = ["name", "password"];
const NONE = { status: "none", message: "", shown: false };

function validateField(type, value) {
  const v = value || "";
  if (type === "name") {
    if (v.length < 4) return "The name has to contain at least 4 characters!";
    if (v.length > 50) return "The name may not exceed 50 characters!";
  } else {
    if (v.length < 4) return "The password has to contain at least 4 characters!";
    if (v.length > 50) return "The password may not exceed 50 characters!";
  }
  return null;
}

// which field does a backend message belong to (register.* language strings)
function fieldOfMessage(msg) {
  return /password/i.test(msg) && !/user ?name/i.test(msg) ? "password" : "name";
}

export function RegisterBox({ onRegister, onNotice }) {
  const [fields, setFields] = useState({ name: NONE, password: NONE });
  const [busy, setBusy] = useState(false);
  const form = useRef(null);
  const focusValue = useRef({});

  // StartPage.register.setInputFieldValid / setInputFieldInvalid, on a snapshot of the field state
  const markValid = (st, type) => {
    const next = { ...st, [type]: { status: "true", message: st[type].message, shown: false } };
    const firstBad = FIELDS.find((f) => next[f].status === "false");
    if (firstBad) next[firstBad] = { ...next[firstBad], shown: true };
    return next;
  };
  const markInvalid = (st, type, messages, forceShow) => {
    const next = { ...st, [type]: { status: "false", message: messages.join("\n"), shown: st[type].shown } };
    const count = FIELDS.filter((f) => next[f].status === "false").length;
    if (count === 1 || forceShow) next[type] = { ...next[type], shown: true };
    return next;
  };

  // $('.require-validation').change(...) : fires on blur when the value changed
  const onFocus = (type) => (e) => { focusValue.current[type] = e.target.value; };
  const onBlur = (type) => (e) => {
    if (focusValue.current[type] === e.target.value) return;
    focusValue.current[type] = e.target.value;
    const err = validateField(type, e.target.value);
    setFields((st) => (err ? markInvalid(st, type, [err]) : markValid(st, type)));
  };

  const submit = async (e) => {
    if (e) e.preventDefault();
    if (busy) return;
    const f = form.current.elements;
    const values = { name: f.register_username.value, password: f.register_password.value };

    // same checks the server does; when something is wrong show it exactly like the server's `errors` result
    const local = {};
    for (const t of FIELDS) { const err = validateField(t, values[t]); if (err) local[t] = err; }
    if (Object.keys(local).length) {
      setFields((st) => {
        let next = st;
        for (const t of FIELDS) next = local[t] ? markInvalid(next, t, [local[t]]) : markValid(next, t);
        const first = FIELDS.find((t) => local[t]);
        return { ...next, [first]: { ...next[first], shown: true } };
      });
      onNotice(FIELDS.filter((t) => local[t]).map((t) => local[t]).join("\n"));
      return;
    }

    setBusy(true);
    try {
      await onRegister(values.name, values.password);
    } catch (err) {
      const msg = (err && err.message) || String(err);
      const type = fieldOfMessage(msg);
      setFields((st) => markInvalid(markValid(markValid(st, "name"), "password"), type, [msg], true));
      onNotice(msg);
    } finally {
      setBusy(false);
    }
  };

  const spanOf = (t) => "validation validation-" + fields[t].status;
  const lines = (t) =>
    fields[t].message.split("\n").flatMap((m, i) => (i ? [<br key={"b" + i} />, m] : [m]));

  return (
    <div className="register">
      <h2>Create a new account</h2>
      <form action="#" method="post" id="register" ref={form} onSubmit={submit}>
        {/* prevent browsers from autocompleting register form with login data */}
        <input type="text" name="un" className="hidden" style={{ display: "none" }} />
        <input type="password" name="pa" className="hidden" style={{ display: "none" }} />
        <input type="hidden" name="server" value="zz1" readOnly />
        <div className="tw2-field" id="form-element-name">
          <label htmlFor="register_username">User name:</label>
          <div className="field-wrap">
            <input
              type="text"
              id="register_username"
              name="register_username"
              className="require-validation"
              data-type="name"
              defaultValue=""
              tabIndex={3}
              onFocus={onFocus("name")}
              onBlur={onBlur("name")}
            />
          </div>
          <span className={spanOf("name")}></span>
          {fields.name.shown && (
            <div className="tw2-error-message error-username">
              <p className="message">{lines("name")}</p>
            </div>
          )}
        </div>
        <div className="tw2-field" id="form-element-password">
          <label htmlFor="register_password">Password:</label>
          <div className="field-wrap">
            <input
              type="password"
              id="register_password"
              name="register_password"
              className="require-validation"
              data-type="password"
              defaultValue=""
              tabIndex={4}
              onFocus={onFocus("password")}
              onBlur={onBlur("password")}
            />
            <span className="icon-locked" />
          </div>
          <span className={spanOf("password")}></span>
          {fields.password.shown && (
            <div className="tw2-error-message error-password">
              <p className="message">{lines("password")}</p>
            </div>
          )}
        </div>
        <button
          title="Register now!"
          id="register-button"
          type="submit"
          className="tw2-btn-big"
          tabIndex={7}
          disabled={busy}
        >
          <span className="cap left" />
          <span className="mid">{busy ? "Working..." : "Register now!"}</span>
          <span className="cap right" />
        </button>
      </form>
    </div>
  );
}
