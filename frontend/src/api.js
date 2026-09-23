const BASE = "/api";
const WORLD_KEY = "twlan_world";

// which world this browser plays in (sent with every request as X-World-Id)
export function getStoredWorld() {
  try {
    const v = window.localStorage.getItem(WORLD_KEY);
    return v ? Number(v) : null;
  } catch {
    return null;
  }
}

// login token: kept in localStorage ("Stay logged in") or sessionStorage (this tab only)
const TOKEN_KEY = "twlan_token";

export function getToken() {
  try {
    return window.localStorage.getItem(TOKEN_KEY) || window.sessionStorage.getItem(TOKEN_KEY) || null;
  } catch {
    return null;
  }
}

export function setToken(token, remember = true) {
  try {
    window.localStorage.removeItem(TOKEN_KEY);
    window.sessionStorage.removeItem(TOKEN_KEY);
    if (token) (remember ? window.localStorage : window.sessionStorage).setItem(TOKEN_KEY, token);
  } catch {
    /* storage unavailable */
  }
}

let worldId = getStoredWorld();

export function setWorldId(id) {
  worldId = id ?? null;
  loadVillageId();
  try {
    if (id == null) window.localStorage.removeItem(WORLD_KEY);
    else window.localStorage.setItem(WORLD_KEY, String(id));
  } catch {
    /* storage unavailable: the choice just isn't remembered */
  }
}

// which of the account's villages is played (sent as X-Village-Id; per world, forgotten when it is not one's own any more)
const villageKey = () => "twlan_village_" + (worldId ?? "x");
let villageId = null;
function loadVillageId() {
  try {
    const v = window.localStorage.getItem(villageKey());
    villageId = v ? Number(v) : null;
  } catch {
    villageId = null;
  }
}
loadVillageId();

export function getVillageId() {
  return villageId;
}

export function setVillageId(id) {
  villageId = id ?? null;
  try {
    if (id == null) window.localStorage.removeItem(villageKey());
    else window.localStorage.setItem(villageKey(), String(id));
  } catch {
    /* storage unavailable: the choice just isn't remembered */
  }
}

const requestHeaders = () => ({
  "Content-Type": "application/json",
  ...(worldId != null ? { "X-World-Id": String(worldId) } : {}),
  ...(villageId != null ? { "X-Village-Id": String(villageId) } : {}),
  ...(getToken() ? { Authorization: "Bearer " + getToken() } : {}),
});

// a long-lived response the caller reads chunk by chunk (the live-update stream): same headers as every request
export function openStream(path, signal) {
  return fetch(BASE + path, { headers: requestHeaders(), signal });
}

async function request(path, options) {
  const res = await fetch(BASE + path, {
    headers: requestHeaders(),
    ...options,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const err = new Error(body.error || `Request failed: ${res.status}`);
    err.status = res.status;
    throw err;
  }
  return res.json();
}

export const api = {
  register: (username, password) => request("/auth/register", { method: "POST", body: JSON.stringify({ username, password }) }),
  login: (username, password) => request("/auth/login", { method: "POST", body: JSON.stringify({ username, password }) }),
  logout: () => request("/auth/logout", { method: "POST" }).catch(() => {}),
  me: () => request("/auth/me"),
  joinWorld: (id) => request("/worlds/" + id + "/join", { method: "POST" }),
  getWorlds: () => request("/worlds"),
  createWorld: (name, speed) => request("/worlds", { method: "POST", body: JSON.stringify({ name, speed }) }),
  getVillage: () => request("/village"),
  getVillages: () => request("/villages"),
  getReports: () => request("/reports"),
  renameVillage: (name) => request("/village/name", { method: "PUT", body: JSON.stringify({ name }) }),
  build: (type) => request("/village/build", { method: "POST", body: JSON.stringify({ type }) }),
  finishBuild: (id) => request("/village/build/" + id + "/finish", { method: "POST" }),
  research: (type) => request("/village/research", { method: "POST", body: JSON.stringify({ type }) }),
  cancelResearch: (id) => request("/village/research/" + id, { method: "DELETE" }),
  mintCoin: (count = 1, villageId = null) => request("/village/coin", { method: "POST", body: JSON.stringify({ count, villageId }) }),
  cancelBuild: (id) => request("/village/build/" + id, { method: "DELETE" }),
  train: (type, count) => request("/village/train", { method: "POST", body: JSON.stringify({ type, count }) }),
  decommission: (type, count) => request("/village/decommission", { method: "POST", body: JSON.stringify({ type, count }) }),
  player: (name) => request("/players/" + encodeURIComponent(name)),
  playerStats: (name, range) => request("/players/" + encodeURIComponent(name) + "/stats?range=" + range),
  editProfile: (body) => request("/profile", { method: "PUT", body: JSON.stringify(body) }),
  achievements: () => request("/achievements"),
  unseenAchievements: () => request("/achievements/unseen", { method: "POST" }),
  achievementRanking: () => request("/achievements/ranking"),
  cancelTrain: (id) => request("/village/train/" + id, { method: "DELETE" }),
  renamePaladin: (name) => request("/village/paladin-name", { method: "PUT", body: JSON.stringify({ name }) }),
  support: (targetVillageId, units) =>
    request("/village/support", { method: "POST", body: JSON.stringify({ targetVillageId, units }) }),
  withdrawSupport: (armyId, units) =>
    request("/village/support/withdraw", { method: "POST", body: JSON.stringify({ armyId, units }) }),
  sendBackSupport: (armyId) => request("/village/support/sendback", { method: "POST", body: JSON.stringify({ armyId }) }),
  attack: (targetVillageId, units) =>
    request("/village/attack", { method: "POST", body: JSON.stringify({ targetVillageId, units }) }),

  // ---- tribes (every action answers with the viewer's fresh tribe state)
  farm: () => request("/farm"),
  saveFarm: (config) => request("/farm", { method: "PUT", body: JSON.stringify({ config }) }),
  tribes: () => request("/tribes"),
  rankingPlayers: (sort = "points", continent) =>
    request("/ranking/players?sort=" + sort + (continent != null ? "&continent=" + continent : "")),
  rankingTribes: (sort = "points", continent) =>
    request("/ranking/tribes?sort=" + sort + (continent != null ? "&continent=" + continent : "")),
  tribeProfile: (id) => request("/tribes/" + id),
  tribeMap: () => request("/tribe/map"),
  tribe: (start = 0) => request("/tribe?start=" + start),
  foundTribe: (name, tag) => request("/tribe", { method: "POST", body: JSON.stringify({ name, tag }) }),
  tribeInvite: (name) => request("/tribe/invite", { method: "POST", body: JSON.stringify({ name }) }),
  tribeWithdraw: (playerId) => request("/tribe/invite/withdraw", { method: "POST", body: JSON.stringify({ playerId }) }),
  tribeAccept: (id) => request("/tribe/invitations/" + id + "/accept", { method: "POST" }),
  tribeReject: (id) => request("/tribe/invitations/" + id + "/reject", { method: "POST" }),
  tribeLeave: () => request("/tribe/leave", { method: "POST" }),
  tribeKick: (playerId) => request("/tribe/kick", { method: "POST", body: JSON.stringify({ playerId }) }),
  tribeRights: (playerId, roles, title, titleOutside) =>
    request("/tribe/members/" + playerId + "/rights", { method: "PUT", body: JSON.stringify({ roles, title, titleOutside }) }),
  tribeProperties: (body) => request("/tribe/properties", { method: "PUT", body: JSON.stringify(body) }),
  tribeRecruitment: (allowApply, template) => request("/tribe/recruitment", { method: "PUT", body: JSON.stringify({ allowApply, template }) }),
  tribeDescription: (text) => request("/tribe/description", { method: "PUT", body: JSON.stringify({ text }) }),
  tribeAnnouncement: (text) => request("/tribe/announcement", { method: "PUT", body: JSON.stringify({ text }) }),
  tribeAddRelation: (tag, kind) => request("/tribe/relations", { method: "POST", body: JSON.stringify({ tag, kind }) }),
  tribeEndRelation: (tribeId) => request("/tribe/relations/" + tribeId, { method: "DELETE" }),
  tribeDisband: () => request("/tribe/disband", { method: "POST" }),

  market: () => request("/market"),
  marketSend: (targetVillageId, wood, clay, iron) => request("/market/send", { method: "POST", body: JSON.stringify({ targetVillageId, wood, clay, iron }) }),
  marketCreateOffer: (offer) => request("/market/offers", { method: "POST", body: JSON.stringify(offer) }),
  marketCancelOffer: (id) => request("/market/offers/" + id, { method: "DELETE" }),
  marketAccept: (id, times) => request("/market/offers/" + id + "/accept", { method: "POST", body: JSON.stringify({ times }) }),

  groups: () => request("/groups"),
  createGroup: (name) => request("/groups", { method: "POST", body: JSON.stringify({ name }) }),
  renameGroup: (id, name) => request("/groups/" + id, { method: "PUT", body: JSON.stringify({ name }) }),
  deleteGroup: (id) => request("/groups/" + id, { method: "DELETE" }),
  assignGroups: (villageId, groupIds) => request("/groups/village/" + villageId, { method: "PUT", body: JSON.stringify({ groupIds }) }),

  tribeWars: (own = false) => request("/tribes/wars" + (own ? "?own=true" : "")),

  // ---- tribe forum
  forum: () => request("/tribe/forum"),
  forumUnread: () => request("/tribe/forum/unread"),
  forumMarkRead: (boardId) => request("/tribe/forum/read" + (boardId ? "?boardId=" + boardId : ""), { method: "POST" }),
  forumBoard: (id, page = 0) => request("/tribe/forum/boards/" + id + "?page=" + page),
  forumCreateBoard: (name, kind) => request("/tribe/forum/boards", { method: "POST", body: JSON.stringify({ name, kind }) }),
  forumEditBoard: (id, name, kind) => request("/tribe/forum/boards/" + id, { method: "PUT", body: JSON.stringify({ name, kind }) }),
  forumDeleteBoard: (id) => request("/tribe/forum/boards/" + id, { method: "DELETE" }),
  forumNewThread: (boardId, body) => request("/tribe/forum/boards/" + boardId + "/threads", { method: "POST", body: JSON.stringify(body) }),
  forumThread: (id, page = -1) => request("/tribe/forum/threads/" + id + "?page=" + page),
  forumReply: (id, body) => request("/tribe/forum/threads/" + id + "/posts", { method: "POST", body: JSON.stringify({ body }) }),
  forumModerate: (id, what) => request("/tribe/forum/threads/" + id + "/" + what, { method: "POST" }),
  forumMove: (id, boardId) => request("/tribe/forum/threads/" + id + "/move", { method: "POST", body: JSON.stringify({ boardId }) }),
  forumVote: (id, optionId) => request("/tribe/forum/threads/" + id + "/vote", { method: "POST", body: JSON.stringify({ optionId }) }),
  forumDeleteThread: (id) => request("/tribe/forum/threads/" + id, { method: "DELETE" }),
  forumEditPost: (id, body) => request("/tribe/forum/posts/" + id, { method: "PUT", body: JSON.stringify({ body }) }),
  forumDeletePost: (id) => request("/tribe/forum/posts/" + id, { method: "DELETE" }),

  // ---- mail
  mailInbox: (folder = 0, page = 0) => request("/mail?folder=" + folder + "&page=" + page),
  mailUnread: () => request("/mail/unread"),
  mailView: (id) => request("/mail/" + id),
  mailSend: (to, subject, body) => request("/mail", { method: "POST", body: JSON.stringify({ to, subject, body }) }),
  mailReply: (id, body) => request("/mail/" + id + "/reply", { method: "POST", body: JSON.stringify({ body }) }),
  mailDelete: (ids) => request("/mail/delete", { method: "POST", body: JSON.stringify({ ids }) }),
  mailRead: (ids, read = true) => request("/mail/read?read=" + read, { method: "POST", body: JSON.stringify({ ids }) }),
  mailMove: (ids, folderId) => request("/mail/move", { method: "POST", body: JSON.stringify({ ids, folderId }) }),
  mailCreateFolder: (name) => request("/mail/folders", { method: "POST", body: JSON.stringify({ name }) }),
  mailRenameFolder: (id, name) => request("/mail/folders/" + id, { method: "PUT", body: JSON.stringify({ name }) }),
  mailDeleteFolder: (id) => request("/mail/folders/" + id, { method: "DELETE" }),
  mailAddresses: () => request("/mail/contacts"),
  mailAddAddress: (name) => request("/mail/contacts", { method: "POST", body: JSON.stringify({ name }) }),
  mailRemoveAddress: (id) => request("/mail/contacts/" + id, { method: "DELETE" }),
  mailBlocked: () => request("/mail/blocked"),
  mailBlock: (name) => request("/mail/blocked", { method: "POST", body: JSON.stringify({ name }) }),
  mailUnblock: (id) => request("/mail/blocked/" + id, { method: "DELETE" }),
  mailCircular: () => request("/mail/circular"),
  mailSendCircular: (subject, body) => request("/mail/circular", { method: "POST", body: JSON.stringify({ subject, body }) }),
};

// ---- admin panel (/api/admin/*; needs an ADMIN account). Some endpoints answer 204 No Content, hence the own helper.
async function adminRequest(path, method = "GET", body) {
  const res = await fetch(BASE + "/admin" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(getToken() ? { Authorization: "Bearer " + getToken() } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  const text = await res.text().catch(() => "");
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    const err = new Error((data && data.error) || `Request failed: ${res.status}`);
    err.status = res.status;
    throw err;
  }
  return data;
}

api.admin = {
  dashboard: () => adminRequest("/dashboard"),
  backup: () => adminRequest("/backup", "POST"),
  catalog: () => adminRequest("/catalog"),
  worlds: () => adminRequest("/worlds"),
  createWorld: (body) => adminRequest("/worlds", "POST", body),
  updateWorld: (id, body) => adminRequest("/worlds/" + id, "PUT", body),
  deleteWorld: (id) => adminRequest("/worlds/" + id, "DELETE"),
  compactWorld: (id) => adminRequest("/worlds/" + id + "/compact", "POST"),
  worldPlayers: (id) => adminRequest("/worlds/" + id + "/players"),
  removePlayer: (worldId, accountId, body) => adminRequest("/worlds/" + worldId + "/players/" + accountId + "/remove", "POST", body),
  worldNpcs: (worldId) => adminRequest("/worlds/" + worldId + "/npcs"),
  updateNpcProfile: (accountId, body) => adminRequest("/npcs/" + accountId + "/profile", "PUT", body),
  npcLog: (worldId, { kind, npc, limit } = {}) => adminRequest("/worlds/" + worldId + "/npc-log?limit=" + (limit ?? 100) + (kind ? "&kind=" + kind : "") + (npc ? "&npc=" + npc : "")),
  createNpcs: (worldId, body) => adminRequest("/worlds/" + worldId + "/npcs", "POST", body),
  worldVillages: (id) => adminRequest("/worlds/" + id + "/villages"),
  worldTribes: (id) => adminRequest("/worlds/" + id + "/tribes"),
  disbandTribe: (worldId, tribeId) => adminRequest("/worlds/" + worldId + "/tribes/" + tribeId + "/disband", "POST"),
  addTribeMember: (worldId, tribeId, accountId) => adminRequest("/worlds/" + worldId + "/tribes/" + tribeId + "/members", "POST", { accountId }),
  removeTribeMember: (worldId, accountId) => adminRequest("/worlds/" + worldId + "/tribes/members/" + accountId, "DELETE"),
  createBarbarians: (worldId, body) => adminRequest("/worlds/" + worldId + "/barbarians", "POST", body),
  village: (id) => adminRequest("/villages/" + id),
  updateVillage: (id, body) => adminRequest("/villages/" + id, "PUT", body),
  finishQueues: (id) => adminRequest("/villages/" + id + "/finish-queues", "POST"),
  deleteVillage: (id) => adminRequest("/villages/" + id, "DELETE"),
  accounts: () => adminRequest("/accounts"),
  setAdmin: (id, admin) => adminRequest("/accounts/" + id + "/admin", "POST", { admin }),
  setPassword: (id, password) => adminRequest("/accounts/" + id + "/password", "POST", { password }),
  deleteAccount: (id) => adminRequest("/accounts/" + id, "DELETE"),
  createRandomBarbarians: (worldId, body) => adminRequest("/worlds/" + worldId + "/barbarians/random", "POST", body),
  randomVillagePreview: (development) => adminRequest("/random-village-preview", "POST", { development }),
};
