# CookieDuel

CookieDuel is a session-based duel plugin for Paper with two modes only: `WILD` and `ARENA_INSTANCE`.
It is built around queueing, confirmation, setup, teleport, fight, and cleanup instead of command-by-command duel state.

## Supported versions

- Paper `1.21 - 1.21.11`
- Folia supported through scheduler-aware handling
- Java `21`

## Features

- Two duel modes: `WILD` and `ARENA_INSTANCE`
- Session-based duel flow
- Paired Wild spawn search from one shared center
- Arena template cloning per accepted duel
- Split config files
- Snapshot / restore handling
- Safe teleport and cleanup flow

## Modes

### WILD

Uses one configured world from `worlds.yml`, searches around that world's spawn, finds one safe center, then places both players on opposite sides of it. Spawn checks use terrain rules plus `blacklist.yml`.

### ARENA_INSTANCE

Clones a configured template world after both players accept, loads a temporary duel world, teleports both players in, then unloads and deletes the instance after the duel.

## Commands

- `/cookieduel queue <queueId>`
- `/cookieduel leave`
- `/cookieduel accept`
- `/cookieduel deny`
- `/cookieduel surrender`
- `/cookieduel admin reload`
- `/cookieduel admin forcestop <player>`
- `/cookieduel admin cleanupinstances`
- Alias: `/cd`

`<queueId>` is the queue you want to join.

## Dependencies

No external plugin dependencies required.

## Setup

1. Build the jar.
2. Put `CookieDuel-1.0.jar` in `plugins/`.
3. Make sure the Wild world is already loaded.
4. Make sure any arena template worlds already exist in the server world container.
5. Start the server and check the startup log.

## Notes

- CookieDuel only supports `WILD` and `ARENA_INSTANCE`.
- Wild search uses the configured world's spawn as its search origin.
- The Wild world must already be loaded. CookieDuel will not auto-load it.
- Arena template folders must already exist and be valid.
- A duel only enters `FIGHTING` after both teleports succeed.
- Folia is supported, but you should still test it with your own server stack before production use.

## Build

The project uses Gradle with Java 21.

```bash
gradle build
```

Build artifact:

```text
CookieDuel-1.0.jar
```
