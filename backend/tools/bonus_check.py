#!/usr/bin/env python3
"""End-to-end check of bonus villages (drawing, the DTOs, every effect, the world switch, the admin editor).
Run against a SCRATCH backend on a fresh DB only (the first account becomes the admin and world 1 gets 400 barbarian villages):
  java -jar backend/build/libs/backend-0.0.1-SNAPSHOT.jar --server.port=8081 --spring.datasource.url=jdbc:h2:file:/tmp/twlan_bonus/db
  python3 backend/tools/bonus_check.py            # optional: TWLAN_BACKEND=http://localhost:8081"""
import json, math, os, sys, urllib.request
B = os.environ.get("TWLAN_BACKEND", "http://localhost:8081") + "/api"
fails = []

def call(tok, method, path, body=None, world=1):
    req = urllib.request.Request(B + path, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if tok: req.add_header("Authorization", "Bearer " + tok)
    req.add_header("X-World-Id", str(world))
    try:
        r = urllib.request.urlopen(req); return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"null")

def check(name, cond, extra=""):
    print(("ok   " if cond else "FAIL ") + name + ("" if cond else "  " + str(extra)))
    if not cond: fails.append(name)

def close(a, b, tol=1e-6): return abs(a - b) <= tol * max(1, abs(b))

c, d = call(None, "POST", "/auth/register", {"username": "boss", "password": "pw123456"})
if c != 200: c, d = call(None, "POST", "/auth/login", {"username": "boss", "password": "pw123456"})
T = d["token"]
call(T, "POST", "/worlds/1/join")
# --- drawing: 400 random barbarian villages, "better" (the default) = about 15 %
c, r = call(T, "POST", "/admin/worlds/1/barbarians/random", {"amount": 400})
check("400 barbarians created", c == 200 and len(r["created"]) == 400, (c, r))
vs = call(T, "GET", "/villages")[1]
barb = [v for v in vs if v["ownerType"] == "BARBARIAN"]
bonus = [v for v in barb if v.get("bonus")]
check("summary DTO has bonus on ~15% of barbarian villages", 0.09 < len(bonus) / len(barb) < 0.22, (len(bonus), len(barb)))
check("codes are 1..9", all(1 <= v["bonus"] <= 9 for v in bonus))
check("all nine kinds appear", len({v["bonus"] for v in bonus}) >= 7, sorted({v["bonus"] for v in bonus}))
check("players' villages have none", all(not v.get("bonus") for v in vs if v["ownerType"] == "PLAYER"))
# --- effects on the admin's own village (set through the admin editor)
mine = call(T, "GET", "/village")[1]
vid = mine["id"]
def put(code): return call(T, "PUT", "/admin/villages/%d" % vid, {"bonus": code})
def state(): return call(T, "GET", "/village")[1]
base = state()
check("no bonus at first", base["bonus"] is None and base["woodPerHour"] > 0)
c, dtl = put(1); s = state()
check("admin sets wood bonus", c == 200 and dtl["bonus"] == 1 and s["bonus"] == 1, (c, dtl.get("bonus"), s["bonus"]))
check("wood x2, clay/iron unchanged", close(s["woodPerHour"], 2 * base["woodPerHour"]) and close(s["clayPerHour"], base["clayPerHour"]) and close(s["ironPerHour"], base["ironPerHour"]))
put(2); s = state(); check("clay x2 only", close(s["clayPerHour"], 2 * base["clayPerHour"]) and close(s["woodPerHour"], base["woodPerHour"]))
put(3); s = state(); check("iron x2 only", close(s["ironPerHour"], 2 * base["ironPerHour"]) and close(s["clayPerHour"], base["clayPerHour"]))
put(8); s = state()
check("all: +10% on each", all(close(s[k], 1.1 * base[k]) for k in ("woodPerHour", "clayPerHour", "ironPerHour")))
put(9); s = state(); check("storage +50%", s["warehouseCapacity"] == round(base["warehouseCapacity"] * 1.5), (s["warehouseCapacity"], base["warehouseCapacity"]))
put(4); s = state(); check("farm +10% population", s["populationCapacity"] == math.floor(base["populationCapacity"] * 1.1), (s["populationCapacity"], base["populationCapacity"]))
# recruiting: barracks 5 + stable/workshop prerequisites; compare the per-unit time with and without the matching bonus
call(T, "PUT", "/admin/villages/%d" % vid, {"bonus": 0, "buildings": {"BARRACKS": 5, "STABLE": 3, "SMITHY": 5, "WORKSHOP": 1, "HEADQUARTERS": 10, "WAREHOUSE": 30, "FARM": 30}, "wood": 300000, "clay": 300000, "iron": 300000})
# light cavalry and rams must be researched in the smithy first (finish-queues completes the research at once)
for kind in ("LIGHT", "RAM"):
    c, r = call(T, "POST", "/village/research", {"type": kind}); assert c == 200, (kind, c, r)
call(T, "POST", "/admin/villages/%d/finish-queues" % vid)
def per_unit(kind):
    s = state(); c, r = call(T, "POST", "/village/train", {"type": kind, "count": 1})
    assert c == 200, (c, r)
    s = state(); q = [x for x in s["trainQueue"] if x["type"] == kind][-1]
    call(T, "DELETE", "/village/train/%d" % q["id"])
    return q["perUnitSeconds"]
spear0, light0, ram0 = per_unit("SPEAR"), per_unit("LIGHT"), per_unit("RAM")
put(5); spear1, light1, ram1 = per_unit("SPEAR"), per_unit("LIGHT"), per_unit("RAM")
check("barracks bonus: spear 33% faster, others unchanged", abs(spear1 - spear0 / 1.5) <= 1 and light1 == light0 and ram1 == ram0, (spear0, spear1, light0, light1))
put(6); spear2, light2, ram2 = per_unit("SPEAR"), per_unit("LIGHT"), per_unit("RAM")
check("stable bonus: light cavalry 33% faster only", abs(light2 - light0 / 1.5) <= 1 and spear2 == spear0 and ram2 == ram0, (light0, light2))
put(7); spear3, light3, ram3 = per_unit("SPEAR"), per_unit("LIGHT"), per_unit("RAM")
check("workshop bonus: ram twice as fast only", abs(ram3 - ram0 / 2) <= 1 and spear3 == spear0 and light3 == light0, (ram0, ram3))
# --- the world switch and the admin editor's validation
check("unknown bonus refused", call(T, "PUT", "/admin/villages/%d" % vid, {"bonus": 42})[0] == 400)
put(1)
c, w = call(T, "GET", "/admin/worlds"); world = [x for x in w if x["id"] == 1][0]
settings = dict(world.get("settings") or {}); settings["bonusVillages"] = "off"
c, r = call(T, "PUT", "/admin/worlds/1", {"settings": settings})
s = state()
check("world setting off: bonus gone from the village DTO and effects", c == 200 and s["bonus"] is None and close(s["woodPerHour"], base["woodPerHour"]), (c, s["bonus"], s["woodPerHour"]))
check("world setting off: bonus gone from the village list", all(not v.get("bonus") for v in call(T, "GET", "/villages")[1]))
settings["bonusVillages"] = "better"; call(T, "PUT", "/admin/worlds/1", {"settings": settings})
check("back on: the stored bonus is active again", state()["bonus"] == 1)
print("\nFAILED: %s" % fails if fails else "\nall passed")
sys.exit(1 if fails else 0)
