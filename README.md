# CookieDuel

CookieDuel is a session-based duel plugin for modern Paper servers. It keeps match flow centered on queueing, confirmation, setup, teleport, fight, and cleanup instead of scattering duel state across commands.

## Compatibility

- Server support: `Paper 1.21 - 1.21.11`
- Folia support: yes, with scheduler-aware handling
- Java: `21`
- `plugin.yml` API version: `1.21`

## What It Does

CookieDuel supports two duel modes and nothing more:

- `WILD`
- `ARENA_INSTANCE`

The current codebase focuses on reliable session flow, fair Wild spawns, and safe arena world cleanup. It is not trying to be a kits, ranked, or stats plugin yet.

## Duel Modes

### `WILD`

Wild mode uses one configured world from `worlds.yml`. The plugin searches outward from that world's spawn, picks one safe center, then places both players on opposite sides of that center at the configured distance.

That means:

- both players start in the same general area
- both sides are generated from one shared center, not two unrelated random teleports
- terrain checks try to keep the fight fair
- `blacklist.yml` is part of spawn validation

Wild validation is intentionally practical. It checks the floor block, headroom, nearby hazards, rough terrain, edge drops, and whether both spawn points feel roughly equivalent.

### `ARENA_INSTANCE`

Arena instance mode clones a template world only after both players accept. A fresh temporary world is loaded for the duel, the players are teleported to the template spawn points, and the instance is unloaded and deleted after cleanup.

This keeps arena matches isolated without leaving a permanent duel world running between fights.

## Features

- Session-based duel lifecycle
- Queue system with accept / deny flow
- Two-mode design: `WILD` and `ARENA_INSTANCE`
- Split config files
- Config-driven Wild safety checks and blacklists
- Inventory and state snapshot / restore
- Safe teleport flow that only starts fights after both teleports succeed
- Arena instance cleanup on duel end and optional leftover cleanup on startup

## Setup

1. Install Java 21.
2. Build the plugin jar.
3. Put `CookieDuel-1.0.jar` in your server `plugins/` folder.
4. Make sure the Wild world is already loaded before the plugin starts.
5. Make sure any arena template worlds already exist in the server world container.
6. Start the server and check the CookieDuel startup log for config validation and mode status.

## Config Files

- `config.yml`
  Main timings, lobby settings, mode toggles, and anti-abuse values.
- `queues.yml`
  Queue definitions and which mode each queue uses.
- `worlds.yml`
  Wild world settings, spawn validation settings, and arena template definitions.
- `messages.yml`
  Player and admin message text.
- `blacklist.yml`
  Extra block blacklists used by Wild spawn validation.

## Important Notes

- CookieDuel only supports `WILD` and `ARENA_INSTANCE`.
- Wild search uses the configured world's spawn as the search origin.
- The Wild world must already be loaded. CookieDuel does not auto-load it.
- Arena template folders must already exist and be valid.
- Teleports must succeed for both players before a duel can move into `FIGHTING`.
- If setup, teleport, or instance cleanup fails, the plugin cancels safely rather than forcing the duel forward.
- Use a proper restart when testing heavy world lifecycle behavior. Avoid Paper `/reload`.

## Folia

CookieDuel is written to be scheduler-aware rather than just marked as Folia compatible.

- entity work is scheduled through entity schedulers
- Wild spawn checks run through region-owned execution
- file copy and delete work stays async
- world load and unload work stays explicit

It should be workable on Folia, but you should still test it with your own server stack and other plugins before relying on it in production.

## Commands

- `/cookieduel queue <queueId>`
- `/cookieduel leave`
- `/cookieduel accept`
- `/cookieduel deny`
- `/cookieduel surrender`
- `/cookieduel admin reload`
- `/cookieduel admin forcestop <player>`
- `/cookieduel admin cleanupinstances`

## Build

The project uses Gradle with Java 21.

```bash
gradle build
```

The configured build artifact name is:

```text
CookieDuel-1.0.jar
```

If you do not keep Gradle installed locally, generate or commit a Gradle wrapper in your own workflow before using CI.

## Developer Notes

- Duel flow is session-based, not command-driven.
- `SchedulerFacade` keeps scheduler usage in one place.
- Wild spawn logic is isolated from the rest of the duel lifecycle.
- Arena instance provisioning and cleanup are separate services.
- The current structure is meant to stay small and maintainable while leaving room for future kit or ranked features.
