import { useRef, useState } from "react";

// <form id="login"> in the header nav (templates/twlan/controllers/index/header.php).
// StartPage.auth.submitLogin: button shows "Working..." while busy; a failure shows the notice box.
export function LoginForm({ onLogin, onNotice }) {
  const [busy, setBusy] = useState(false);
  const form = useRef(null);
  const forgot = (e) => e.preventDefault(); // /page/recovery does not exist here

  const submit = async (e) => {
    e.preventDefault();
    if (busy) return;
    const f = form.current.elements;
    setBusy(true);
    try {
      await onLogin(f.username.value, f.password.value, f["remember-me"].checked);
    } catch (err) {
      onNotice((err && err.message) || String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form action="#" method="post" id="login" ref={form} onSubmit={submit}>
      <h2>Login</h2>
      <div className="tw2-field">
        <label htmlFor="login_username">User name:</label>
        <div className="field-wrap">
          <input type="text" id="login_username" placeholder="User name" name="username" tabIndex={1} autoFocus />
        </div>
      </div>
      <div className="tw2-field">
        <label htmlFor="login_password">Password:</label>
        <div className="field-wrap">
          <input type="password" id="login_password" placeholder="Password" name="password" tabIndex={2} />
          <span className="icon-locked" />
        </div>
      </div>
      <div className="tw2-meta">
        <span className="checkbox-label">
          {" "}
          <input type="checkbox" className="checkbox" id="remember-me" name="remember-me" defaultChecked />{" "}
        </span>
        <label htmlFor="remember-me">Stay logged in</label>
        {" · "}
        <a href="/page/recovery" id="change-password" onClick={forgot}>Lost password/Change password</a>
      </div>
      <button className="tw2-btn-big" type="submit" disabled={busy}>
        <span className="cap left" />
        <span className="mid">{busy ? "Working..." : "Login"}</span>
        <span className="cap right" />
      </button>
    </form>
  );
}
