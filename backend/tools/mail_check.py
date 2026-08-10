#!/usr/bin/env python3
"""End-to-end check of the in-game mail API (direct mail, replies, blocking, folders, address book, circular mail).
Run against a SCRATCH backend on a fresh DB only (it registers alice/bobby/carol and founds a tribe):
  java -jar backend/build/libs/backend-0.0.1-SNAPSHOT.jar --server.port=8081 --spring.datasource.url=jdbc:h2:file:/tmp/twlan_mail/db
  python3 backend/tools/mail_check.py            # optional: TWLAN_BACKEND=http://localhost:8081
Not re-runnable on the same DB (the tribe and the mails from the first run are still there)."""
import json, os, sys, urllib.request
B = os.environ.get("TWLAN_BACKEND", "http://localhost:8081") + "/api"
fails = []
def call(tok, method, path, body=None, world=1, expect=200):
    req = urllib.request.Request(B + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if tok: req.add_header("Authorization", "Bearer " + tok)
    req.add_header("X-World-Id", str(world))
    try:
        r = urllib.request.urlopen(req); code = r.status; data = json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        code = e.code; data = json.loads(e.read() or b"null")
    return code, data
def check(name, cond, extra=""):
    print(("ok   " if cond else "FAIL ") + name + ("" if cond else "  " + str(extra)))
    if not cond: fails.append(name)
def user(n):
    c, d = call(None, "POST", "/auth/register", {"username": n, "password": "pw123456"})
    if c != 200: c, d = call(None, "POST", "/auth/login", {"username": n, "password": "pw123456"})
    tok = d["token"]
    call(tok, "POST", "/worlds/1/join")
    return tok
A, Bo, C = user("alice"), user("bobby"), user("carol")
# --- direct mail
c, d = call(A, "POST", "/mail", {"to": ["Bobby"], "subject": "Hello", "body": "Hi [b]Bob[/b]"})
check("send", c == 200 and "id" in d, (c, d)); tid = d.get("id")
check("bob unread 1", call(Bo, "GET", "/mail/unread")[1] == {"unread": 1})
check("alice unread 0 (own message)", call(A, "GET", "/mail/unread")[1] == {"unread": 0})
c, v = call(Bo, "GET", "/village"); check("village dto newMails", v.get("newMails") == 1, v.get("newMails"))
c, inbox = call(Bo, "GET", "/mail")
check("inbox row unread", len(inbox["threads"]) == 1 and inbox["threads"][0]["unread"] and inbox["threads"][0]["author"] == "alice", inbox)
c, t = call(Bo, "GET", "/mail/%d" % tid)
check("view", c == 200 and len(t["messages"]) == 1 and t["participants"] == ["alice", "bobby"], t)
check("bob unread 0 after view", call(Bo, "GET", "/mail/unread")[1] == {"unread": 0})
c, d = call(Bo, "POST", "/mail/%d/reply" % tid, {"body": "Yo"}); check("reply", c == 200 and d["id"] == tid, d)
check("alice unread 1", call(A, "GET", "/mail/unread")[1] == {"unread": 1})
c, t = call(A, "GET", "/mail/%d" % tid); check("2 messages", len(t["messages"]) == 2 and t["messages"][1]["own"] is False and t["messages"][0]["own"], t)
# --- validation
check("unknown recipient", call(A, "POST", "/mail", {"to": ["nobody"], "subject": "x", "body": "y"})[0] == 400)
check("self", call(A, "POST", "/mail", {"to": ["alice"], "subject": "x", "body": "y"})[0] == 400)
check("no subject", call(A, "POST", "/mail", {"to": ["bobby"], "subject": " ", "body": "y"})[0] == 400)
check("no body", call(A, "POST", "/mail", {"to": ["bobby"], "subject": "x", "body": ""})[0] == 400)
check("long body", call(A, "POST", "/mail", {"to": ["bobby"], "subject": "x", "body": "y" * 9000})[0] == 400)
check("stranger cannot read", call(C, "GET", "/mail/%d" % tid)[0] == 400)
check("stranger cannot reply", call(C, "POST", "/mail/%d/reply" % tid, {"body": "x"})[0] == 400)
check("no token", call(None, "GET", "/mail")[0] == 401)
# --- block
c, d = call(Bo, "POST", "/mail/blocked", {"name": "alice"}); check("block", c == 200 and len(d) == 1, d)
c, d = call(A, "POST", "/mail", {"to": ["bobby"], "subject": "x", "body": "y"}); check("blocked send refused", c == 400 and "accept" in d["error"], d)
c, d = call(A, "POST", "/mail/%d/reply" % tid, {"body": "x"}); check("blocked reply refused", c == 400, d)
check("block twice", call(Bo, "POST", "/mail/blocked", {"name": "alice"})[0] == 400)
bl = call(Bo, "GET", "/mail/blocked")[1]
check("unblock", call(Bo, "DELETE", "/mail/blocked/%d" % bl[0]["id"])[1] == [])
check("send after unblock", call(A, "POST", "/mail", {"to": ["bobby"], "subject": "Second", "body": "y"})[0] == 200)
# --- folders
c, inbox = call(Bo, "POST", "/mail/folders", {"name": "Keep"}); fid = inbox["folders"][1]["id"]
check("folder created", inbox["folders"][1]["name"] == "Keep", inbox)
check("dup folder", call(Bo, "POST", "/mail/folders", {"name": "keep"})[0] == 400)
check("move", call(Bo, "POST", "/mail/move", {"ids": [tid], "folderId": fid})[0] == 200)
c, inbox = call(Bo, "GET", "/mail?folder=%d" % fid); check("folder listing", [r["id"] for r in inbox["threads"]] == [tid], inbox)
c, inbox = call(Bo, "GET", "/mail"); check("inbox no longer has it", tid not in [r["id"] for r in inbox["threads"]] and len(inbox["threads"]) == 1, inbox)
check("move to foreign folder", call(A, "POST", "/mail/move", {"ids": [tid], "folderId": fid})[0] == 400)
check("rename", call(Bo, "PUT", "/mail/folders/%d" % fid, {"name": "Kept"})[1]["folders"][1]["name"] == "Kept")
c, inbox = call(Bo, "DELETE", "/mail/folders/%d" % fid); check("folder deleted, thread back in inbox", len(inbox["folders"]) == 1 and len(inbox["threads"]) == 2, inbox)
# --- read/unread, delete
call(Bo, "POST", "/mail/read?read=false", {"ids": [tid]}); check("mark unread", call(Bo, "GET", "/mail/unread")[1]["unread"] >= 1)
call(Bo, "POST", "/mail/read?read=true", {"ids": [tid]})
c, inbox = call(Bo, "POST", "/mail/delete", {"ids": [tid]}); check("delete", tid not in [r["id"] for r in inbox["threads"]], inbox)
check("alice still sees it", tid in [r["id"] for r in call(A, "GET", "/mail")[1]["threads"]])
call(A, "POST", "/mail/%d/reply" % tid, {"body": "are you there"})
check("reply revives", tid in [r["id"] for r in call(Bo, "GET", "/mail")[1]["threads"]])
call(Bo, "POST", "/mail/delete", {"ids": [tid]}); call(A, "POST", "/mail/delete", {"ids": [tid]})
check("both deleted: gone", call(A, "GET", "/mail/%d" % tid)[0] == 400)
# --- address book
c, d = call(A, "POST", "/mail/contacts", {"name": "carol"}); check("address add", c == 200 and d[0]["name"] == "carol", d)
check("address dup", call(A, "POST", "/mail/contacts", {"name": "carol"})[0] == 400)
check("address remove", call(A, "DELETE", "/mail/contacts/%d" % d[0]["id"])[1] == [])
# --- circular mail
check("no tribe circular page", call(A, "GET", "/mail/circular")[1]["tribe"] is None)
check("no tribe circular send", call(A, "POST", "/mail/circular", {"subject": "x", "body": "y"})[0] == 400)
c, d = call(A, "POST", "/tribe", {"name": "Mailers", "tag": "ML"}); check("found tribe", c == 200, d)
call(A, "POST", "/tribe/invite", {"name": "bobby"}); call(A, "POST", "/tribe/invite", {"name": "carol"})
for tok in (Bo, C):
    st = call(tok, "GET", "/tribe")[1]
    inv = st.get("myInvitations") or []
    if inv: call(tok, "POST", "/tribe/invitations/%d/accept" % inv[0]["id"])
cp = call(A, "GET", "/mail/circular")[1]; check("circular page allowed", cp["allowed"] and cp["members"] == 3, cp)
check("member may not send", call(Bo, "POST", "/mail/circular", {"subject": "x", "body": "y"})[0] == 400)
c, cp = call(A, "POST", "/mail/circular", {"subject": "Meeting", "body": "tonight"}); check("circular sent", c == 200 and cp["sent"][0]["recipients"] == 2, cp)
mid = cp["sent"][0]["id"]
c, t = call(Bo, "GET", "/mail/%d" % mid); check("bob reads circular", c == 200 and t["mass"] and t["participants"] == ["alice"], t)
c, d = call(Bo, "POST", "/mail/%d/reply" % mid, {"body": "ok"}); check("answer goes to a new private thread", c == 200 and d["id"] != mid, d)
c, t = call(A, "GET", "/mail/%d" % d["id"]); check("alice got the answer", t["subject"] == "Re: Meeting" and t["participants"] == ["alice", "bobby"], t)
print("\nFAILED: %s" % fails if fails else "\nall passed")
sys.exit(1 if fails else 0)
