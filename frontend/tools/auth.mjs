// Dev-tool login for compare.mjs / smoke.mjs now that the backend needs an account.
// Uses an account "devtool" (its own village in world 1 — your real village is never touched).
// Register it once AFTER you have registered your own account in the UI (the first account inherits the
// pre-accounts save!):  TWLAN_REGISTER=1 node tools/smoke.mjs
const BACKEND = process.env.TWLAN_BACKEND || 'http://localhost:8080'
const USER = process.env.TWLAN_USER || 'devtool'
const PASS = process.env.TWLAN_PASS || 'devtool1'

async function post(path, body, token) {
  const res = await fetch(BACKEND + path, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body ? JSON.stringify(body) : undefined })
  return { ok: res.ok, status: res.status, json: await res.json().catch(() => null) }
}

/** -> { token, worldId } or null when the backend has no accounts yet (old backend) / login is not possible. */
export async function devSession(worldId = Number(process.env.TWLAN_WORLD) || 1) {
  try {
    let r = await post('/api/auth/login', { username: USER, password: PASS })
    if (!r.ok && r.status !== 404 && process.env.TWLAN_REGISTER === '1') r = await post('/api/auth/register', { username: USER, password: PASS })
    if (!r.ok) {
      if (r.status === 401 || r.status === 400) console.error(`[tools] no dev login (${r.json?.error ?? r.status}); run once with TWLAN_REGISTER=1 after registering your own account`)
      return null
    }
    await post(`/api/worlds/${worldId}/join`, null, r.json.token)
    return { token: r.json.token, worldId }
  } catch { return null }
}

export function seedStorage(win, session) {
  if (!session) return
  win.localStorage.setItem('twlan_token', session.token)
  win.localStorage.setItem('twlan_world', String(session.worldId))
}
