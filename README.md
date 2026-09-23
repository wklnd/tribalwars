# TWLAN - Tribal Wars Lan / Private Server

A modern, local, clone of **Tribal Wars**, built from scratch with Java and React.

> Not affiliated with or published by InnoGames.

## The World

TWLAN2 recreates the classic Tribal Wars experience as a persistent, singleplayer world.

Multiple worlds can run side by side, each with its own speed and population. AI-controlled villages and tribes develop, recruit armies, scout, farm, retaliate and conquer on their own. Wars can start, villages can change hands, and the world continues to evolve even when no human player is online.

The game follows the original Tribal Wars systems and mechanics as closely as practical / possible, while the implementation itself is built entirely from scratch.

## Features

* Multiple worlds running independently at different speeds
* Accounts with multiple villages
* Village development and resource management
* Building and technology progression
* Recruitment, scouting and combat
* Noblemen and conquest through loyalty
* AI-controlled NPC villages and tribes
* NPCs that build, recruit, scout, farm, retaliate and conquer
* Tribes with diplomacy, forums, wars and member support
* In-game mail and messaging
* Player-to-player market
* Achievements
* Paladin
* Day and night cycle
* Live browser updates using Server-Sent Events
* Administration panel for managing worlds, NPCs, accounts and barbarian villages

Game mechanics, values and systems are matched to the original Tribal Wars wherever possible.

## Technology

### Backend

* Java 21
* Spring Boot
* Gradle
* H2

Game state is stored in a local H2 file database. No external services or database servers are required.

### Frontend

* React
* Vite

The frontend uses the original game's CSS and mirrors its rendered markup to reproduce the classic Tribal Wars interface as closely as possible.

## Project Structure

```text
TWLAN2/
├── backend/       # Java / Spring Boot game server
├── frontend/      # React / Vite client
└── docker-compose.yml
```

## Running Locally

Start the backend:

```bash
cd backend
./gradlew bootRun
```

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Then open:

**http://localhost:5173**

Game state is persisted in:

```text
backend/data/
```

This directory contains the actual world save. Back it up before modifying or removing it.

## Self-Hosting

TWLAN2 can be deployed using Docker Compose:

```bash
docker compose up --build
```

The Compose setup builds the Java backend and React frontend, with the frontend served through nginx.

The project is intended for personal and private use.


**The world does not wait.**
