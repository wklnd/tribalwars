#!/usr/bin/env python3
"""
NPC AI simulation harness. Talks to a SCRATCH backend (never the live save):

    java -jar build/libs/backend-0.0.1-SNAPSHOT.jar --server.port=8084 \
         --spring.datasource.url=jdbc:h2:file:/tmp/twlan_sim/db --game.npc-tick-ms=200 > /tmp/twlan_sim/log.txt 2>&1 &
    python3 tools/npc_sim.py --base http://localhost:8084 --log /tmp/twlan_sim/log.txt --minutes 10

It registers an admin (the first account of a fresh DB is admin) and one human account, opens one world per difficulty
(same layout: N NPCs of random archetypes, some barbarians, the human joined), lets the NPCs play and prints per world:
the activity log counts per kind, how the human fared (villages left, battles on it), the NPCs' average development and
army, NPCs that look stuck, plus the backend log's ERROR/WARN count and its slowest NPC ticks.
Use --protection 2880 to check that beginner protection keeps NPCs off the human.
"""
import argparse, collections, json, re, sys, time, urllib.error, urllib.request

ap = argparse.ArgumentParser()
ap.add_argument("--base", default="http://localhost:8084")
ap.add_argument("--log", default="/tmp/twlan_sim/log.txt")
ap.add_argument("--minutes", type=float, default=10)
ap.add_argument("--speed", type=float, default=100)
ap.add_argument("--npcs", type=int, default=30)
ap.add_argument("--barbarians", type=int, default=10)
ap.add_argument("--difficulties", default="passive,normal,hard,brutal")
ap.add_argument("--conquest", default="auto", help="npcConquest setting of every world (auto/never/not-last/anything)")
ap.add_argument("--protection", default="0", help="beginnerProtection minutes")
ap.add_argument("--min-dev", type=int, default=15)
ap.add_argument("--max-dev", type=int, default=55)
args = ap.parse_args()


def call(method, path, body=None, token=None, world=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if world:
        headers["X-World-Id"] = str(world)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(args.base + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {path} -> {e.code} {e.read()[:200]}")


def login(user, pw):
    try:
        return call("POST", "/api/auth/register", {"username": user, "password": pw})["token"]
    except SystemExit:
        return call("POST", "/api/auth/login", {"username": user, "password": pw})["token"]


admin = login("simadmin", "simpass1234")  # first account of a fresh DB is the admin
human = login("simhuman", "simpass1234")

worlds = {}
for diff in args.difficulties.split(","):
    w = call("POST", "/api/admin/worlds", {"name": "Sim " + diff, "speed": args.speed, "settings": {
        "npcDifficulty": diff, "npcConquest": args.conquest, "beginnerProtection": args.protection,
        "npcRhythm": "false"}}, admin)  # (the sim measures what NPCs do, so they must not sleep)
    wid = w["id"]
    call("POST", f"/api/admin/worlds/{wid}/npcs", {"count": args.npcs, "namePrefix": "N" + diff[:1].upper(),
                                                     "minDevelopment": args.min_dev, "maxDevelopment": args.max_dev}, admin)
    call("POST", f"/api/admin/worlds/{wid}/barbarians/random", {"amount": args.barbarians, "minDevelopment": 5, "maxDevelopment": 30}, admin)
    call("POST", f"/api/worlds/{wid}/join", {}, human, wid)
    worlds[diff] = wid
    print(f"world {wid}: {diff} (x{args.speed:g}), {args.npcs} NPCs, {args.barbarians} barbarians, protection {args.protection}")

t0 = time.time()
end = t0 + args.minutes * 60
while time.time() < end:
    time.sleep(min(30, max(1, end - time.time())))
    print(f"  ... {int(time.time() - t0)} s", flush=True)


def stats(wid):
    log = call("GET", f"/api/admin/worlds/{wid}/npc-log?limit=500", token=admin)
    npcs = call("GET", f"/api/admin/worlds/{wid}/npcs", token=admin)["npcs"]
    villages = call("GET", f"/api/admin/worlds/{wid}/villages", token=admin)
    mine = [v for v in villages if v["ownerName"] == "simhuman"]
    levels, army, stuck = [], [], []
    for v in villages:
        if not v["npc"] or v["ownerType"] == "BARBARIAN":
            continue
        d = call("GET", f"/api/admin/villages/{v['id']}", token=admin)
        levels.append(sum(d["buildings"].values()))
        army.append(sum(n for k, n in d["units"].items() if k != "SCOUT"))
        if not d["buildQueue"] and not d["trainQueue"] and max(d["wood"], d["clay"], d["iron"]) < 200:
            stuck.append(v["ownerName"])
    arche = collections.Counter(n["archetype"] for n in npcs)
    onhuman = [e for e in log["entries"] if "simhuman" in e["message"] and e["kind"] in ("BATTLE", "CONQUEST")]
    return log["counts"], levels, army, stuck, arche, len(mine), onhuman


print()
for diff, wid in worlds.items():
    counts, levels, army, stuck, arche, mine, onhuman = stats(wid)
    print(f"== {diff} (world {wid})")
    print("   log:", dict(counts))
    print(f"   NPC villages: {len(levels)}; avg building levels {sum(levels) / max(1, len(levels)):.0f}, avg army {sum(army) / max(1, len(army)):.0f}; archetypes {dict(arche)}")
    print(f"   human villages left: {mine}; battles/conquests at the human in the last 500 log lines: {len(onhuman)}")
    if stuck:
        print("   NPCs with empty queues and no resources:", stuck)

try:
    text = open(args.log, errors="replace").read()
    errors = len(re.findall(r" ERROR ", text))
    warns = re.findall(r"NPC .* stumbled.*|NPC defence failed.*", text)
    ticks = sorted(int(m) for m in re.findall(r"NPC tick took (\d+) ms", text))
    print(f"\nbackend log: {errors} ERROR lines, {len(warns)} NPC stumbles; slow ticks (>500 ms): {len(ticks)}, worst {ticks[-1] if ticks else 0} ms")
except OSError:
    print("\n(backend log not readable: " + args.log + ")")
