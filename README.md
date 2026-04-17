# CookieDuel

CookieDuel is a Paper duel plugin built around short-lived duel sessions instead of loose command state.
It supports exactly two modes: `WILD` and `ARENA_INSTANCE`.

## Supported versions

- Paper `1.21 - 1.21.11`
- Folia supported through the scheduler layer used by teleports, GUI actions, and duel lifecycle work
- Java `21`

## Features

- Player-created duel queue entries
- 54-slot queue browser GUI with pagination
- Shared-center Wild spawns with blacklist and terrain checks
- Per-duel arena world cloning and cleanup
- Session-based confirm, provision, teleport, fight, and cleanup flow
- Split config files and one central `lang.yml`

## Modes

### WILD

Wild duels use one configured world from `worlds.yml`. CookieDuel searches around that world's spawn, finds one safe center, and places both players on opposite sides of it with roughly equal terrain conditions.

### ARENA_INSTANCE

Arena duels clone a configured template world after both players accept, teleport both players into the temporary instance, then unload and delete it when the duel ends.

## Commands

- `/cookieduel queue <id> <mode>` - create a queue entry you own
- `/cookieduel queues` - open the queue browser GUI
- `/cookieduel info <id>` - show basic info about an active queue
- `/cookieduel leave`
- `/cookieduel accept`
- `/cookieduel deny`
- `/cookieduel surrender`
- `/cookieduel admin reload`
- `/cookieduel admin forcestop <player>`
- `/cookieduel admin cleanupinstances`
- Alias: `/cd`

Modes for queue creation:

- `WILD`
- `ARENA_INSTANCE`

## Dependencies

No external plugin dependencies required.

## Setup

1. Build the jar.
2. Put `CookieDuel-1.0.jar` in `plugins/`.
3. Make sure the Wild world is already loaded before the plugin starts.
4. Make sure arena template worlds already exist in the server world container.
5. Start the server and review the startup log.

## Config files

- `config.yml` - general duel, lobby, and anti-abuse settings
- `worlds.yml` - Wild world settings and arena templates
- `queues.yml` - reserved for queue-related settings; live queue entries are created in game
- `blacklist.yml` - Wild spawn blacklist rules
- `lang.yml` - plugin prefix, chat messages, GUI text, and titles

## Notes

- Queue entries are created by players in game; they are not a static list from config.
- `/cd queues` opens the live queue browser and supports paging plus manual refresh.
- Wild search uses the configured world's spawn as the search origin.
- The Wild world must already be loaded. CookieDuel does not auto-load it.
- Arena templates must already exist and be valid on disk.
- A duel only enters `FIGHTING` after both teleports succeed.
- Folia support is intentional, but you should still test with your own server stack before production use.

## Build

The project uses Gradle with Java 21.

```bash
gradle build
```

Build artifact:

```text
CookieDuel-1.0.jar
```
