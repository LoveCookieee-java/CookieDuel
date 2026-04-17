# CookieDuel

CookieDuel is a session-based Minecraft duel plugin for modern Paper servers. It keeps duel flow centered on a controlled lifecycle instead of letting commands mutate match state ad hoc.

## Compatibility

- Supported server software: `Paper 1.21 - 1.21.11`
- `plugin.yml` API version: `1.21`
- Java requirement: `Java 21`
- Folia support: practical support is built in through scheduler abstraction, entity scheduling, region-owned WILD spawn validation, and safer teleport orchestration

## Features

- Exactly two duel modes: `WILD` and `ARENA_INSTANCE`
- Session lifecycle with defensive state transitions
- Queueing, confirmation, surrender, admin reload, forcestop, and leftover instance cleanup
- Split admin configs: `config.yml`, `queues.yml`, `worlds.yml`, `messages.yml`, `blacklist.yml`
- WILD paired spawn selection from one shared center
- Template world cloning only after both duelists confirm
- Snapshot/restore handling for duel entry and cleanup
- Safer teleport failure rollback so duels do not start in a half-teleported state

## Duel Modes

### `WILD`

WILD uses one configured target world from `worlds.yml`. CookieDuel searches outward from that world's spawn location, finds one safe center, chooses a random horizontal direction, and places both players symmetrically around the same center.

Important WILD behavior:

- Both duelists spawn in the same general area
- Spawn distance stays configurable through `wild.spawn-distance`
- Fairness checks reject obviously uneven terrain
- `blacklist.yml` contributes floor, body/head, and nearby terrain hazard rules
- The WILD world must already be loaded before CookieDuel enables

The validator is intentionally practical rather than overly complex. It checks for unsafe floor blocks, cramped spawn space, nearby hazards, rough terrain, edge drops, and mismatched local terrain conditions between the two spawn points.

### `ARENA_INSTANCE`

ARENA_INSTANCE clones a configured template world only after both players accept the duel. The plugin then loads a fresh temporary instance world, teleports both duelists into configured spawn points inside that instance, and deletes the instance after cleanup.

Important arena-instance behavior:

- Template world must already exist correctly on disk
- Instance names are unique per session
- Failed provisioning is logged clearly and cleaned up defensively
- Leftover instance worlds can be cleaned on startup and by admin command

## Setup

1. Install Java 21 on the server host.
2. Build the plugin jar.
3. Place `CookieDuel-1.0.jar` in the server `plugins/` folder.
4. Make sure the configured WILD world is already loaded before startup.
5. Make sure arena template world folders already exist in the server world container.
6. Start or restart the server and review the CookieDuel startup summary in console.

## Config Files

- `config.yml`
  Main duel timing, lobby behavior, anti-abuse settings, and mode toggles.
- `queues.yml`
  Queue definitions and which duel mode each queue uses.
- `worlds.yml`
  WILD world settings, paired spawn spacing, terrain validation options, and arena template definitions.
- `messages.yml`
  Player/admin messages and placeholders.
- `blacklist.yml`
  Admin-controlled WILD block blacklists for floor, body/head obstruction, and nearby combat hazards.

## Important Notes

- CookieDuel supports exactly two duel modes: `WILD` and `ARENA_INSTANCE`.
- WILD random center search uses the configured world's spawn as the radius origin.
- CookieDuel does not silently auto-load the WILD world. Invalid WILD world configuration fails clearly.
- Arena template folders must exist on disk and be valid before the plugin enables.
- Teleport flow only advances to `FIGHTING` after both players teleport successfully.
- If one teleport fails, the duel is cancelled safely and rollback/restore logic runs instead of starting the fight.
- Avoid using Paper `/reload` for this plugin. Restart the server when testing world cloning, unloading, or lifecycle-heavy behavior.

## Folia Notes

CookieDuel has been revised for real scheduler awareness instead of just adding a label:

- async file copy/delete work stays off-thread
- entity-owned work uses entity schedulers
- WILD paired spawn validation runs through region-owned scheduling
- duel lifecycle transitions use the scheduler facade instead of hardcoded Bukkit main-thread assumptions
- world load/unload remains treated as explicit global work

This is intended to be practically usable on Folia, but you should still validate your exact server stack, world setup, and companion plugins before production rollout.

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

This repository currently uses Gradle build scripts with Java 21.

If you have Gradle installed:

```bash
gradle build
```

The jar artifact is configured to build as:

```text
CookieDuel-1.0.jar
```

If your environment does not already have Gradle available, import the project into your IDE or generate/commit a Gradle wrapper in your own workflow before building in CI.

## Developer Notes

- Core duel flow is session-based, not command-centric.
- Scheduler concerns are funneled through `SchedulerFacade` and `PaperSchedulerFacade`.
- WILD paired spawn logic is isolated in `WildLocationService` and `WildLocationValidator`.
- Arena instance lifecycle is split across provision, cleanup, template, and world manager classes.
- The current architecture is intentionally kept modular so kits, stats, or ranked features can be added later without rewriting duel lifecycle foundations.
