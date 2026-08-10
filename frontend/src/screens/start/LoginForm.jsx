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
      <div className="login">
        <input type="text" placeholder="User name" name="username" tabIndex={1} autoFocus />{" "}
        <input type="password" placeholder="Password" name="password" tabIndex={2} />{" "}
        <button className="btn-login" type="submit">
          {busy ? "Working..." : "Login"}
        </button>{" "}
        <div className="login-meta">
          <div className="remember-me-container">
            {" "}
            <span className="checkbox-label">
              {" "}
              <input type="checkbox" className="checkbox" id="remember-me" name="remember-me" defaultChecked />{" "}
            </span>
            <label htmlFor="remember-me">Stay logged in</label>
          </div>{" "}
          | <a href="/page/recovery" id="change-password" onClick={forgot}>Lost password/Change password</a>{" "}
        </div>
        {/* end .login-meta*/}
      </div>
      {/*end .login*/}
    </form>
  );
}
