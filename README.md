# TWLAN2

A local, singleplayer reimplementation of Tribal Wars, built from scratch in Java and React.
Not affiliated with or published by InnoGames. The original `TWLan-linux64` game bundle is kept
locally (gitignored, never committed) purely as a reference for how the real game looks and plays —
this repo doesn't reuse its code, just its visual fidelity as a target.

## What this is

A personal game, not a public one. Several worlds run side by side at different speeds, each
populated by AI-controlled NPC villages and tribes that build, recruit, scout, farm, retaliate and
conquer on their own — the world stays alive without a real player online. On top of the classic
building/unit/combat loop (balance numbers matched against the original where possible), it has:

- Accounts and multiple villages per account, conquest via noblemen + loyalty
- Tribes: diplomacy, forum, wars, support between members
- In-game mail, a market, achievements, a paladin, day/night
- Live updates pushed to the browser (server-sent events, not polling)
- An admin panel for managing worlds, NPCs, accounts and barbarian villages

## Stack

- `backend/` — Java 21, Spring Boot, Gradle (wrapper included). Game state lives in a local H2
  file database, no external services required.
- `frontend/` — React + Vite. Loads the original game's own CSS and mirrors its rendered markup,
  so the UI matches the original 1:1 rather than approximating it.

## Running it locally

```bash
# backend (:8080)
cd backend && ./gradlew bootRun

# frontend (:5173)
cd frontend && npm install && npm run dev
```

Then open http://localhost:5173. Game state persists in `backend/data/` between restarts — that
folder holds the actual save, so back it up before touching it rather than deleting it to "reset."

## Self-hosting

`docker-compose.yml` builds both services (Gradle → jar, Vite → static build served by nginx) for
deploying elsewhere, e.g. via Coolify. See `CLAUDE.md` for the constraints around that (still
personal-use only, never openly public).
