# CookieDuel

CookieDuel is a fast Paper duel plugin built for servers that want clean queue-based matchmaking without bloated setup or confusing command flow.
Players create a duel room with a single command, browse live rooms in a polished GUI, and fight in either open-world `WILD` matches or temporary cloned `ARENA` instances.

## Why CookieDuel

- Simple player flow: `/cd queue <mode>`, `/cd list`, `/cd random`
- Only two clear public modes: `WILD` and `ARENA`
- Queue identity is automatic and tied to the owner's player name
- 54-slot live queue browser with paging, refresh, and viewer profile card
- Safe arena instance provisioning and cleanup
- Centralized messages in `lang.yml`
- Paper 1.21 to 1.21.11 support, with Folia-safe direction in scheduling-sensitive areas

## Supported versions

- Paper and compatible forks such as Purpur, Leaf, and Folia: `1.21 - 1.21.11`
- Java `21`

## Duel modes

### WILD

Wild duels use one configured world from `worlds.yml`. CookieDuel searches around that world's spawn, finds a safe center, and places both players on opposite sides with fair spacing.

### ARENA

Arena duels clone a configured template world when the fight starts, teleport both players into the temporary arena, and fully clean it up after the duel ends.

## Player commands

- `/cd queue <mode>` - create a queue room using your player name as the room identity
- `/cd list` - open the queue browser GUI
- `/cd random` - join a random valid queue
- `/cd leave` - leave your queue before the duel starts
- `/cd surrender` - surrender an active duel

Available queue modes:

- `WILD`
- `ARENA`

## Admin commands

- `/cd admin reload`
- `/cd admin forcestop <player>`
- `/cd admin cleanupinstances`

Alias:

- `/cd`

## Queue browser

`/cd list` opens the live queue GUI.

- Queue entries show `Player`, `Mode`, and `Money`
- The viewer's own head is displayed in a dedicated slot
- The profile card shows `Player`, `Money`, `Kills`, `Deaths`, and `Points`
- Previous page, next page, and refresh controls stay available on the bottom row

## Optional integrations

CookieDuel does not hard-require external plugins.

- `PlaceholderAPI` is an optional soft dependency
- `PlayerPoints` can be displayed through PlaceholderAPI if its placeholders are available

If PlaceholderAPI is missing, CookieDuel still runs normally. GUI values that depend on placeholders fall back safely, and CookieDuel's own placeholders simply do not register.

## Placeholders

When PlaceholderAPI is installed, CookieDuel registers:

- `%CD_WILD%` - total active `WILD` queue rooms
- `%CD_ARENA%` - total active `ARENA` queue rooms

## Configuration files

- `config.yml` - duel flow, timing, and mode settings
- `worlds.yml` - Wild world settings and arena template configuration
- `queues.yml` - queue-related settings
- `blacklist.yml` - Wild spawn blacklist rules
- `lang.yml` - GUI text, messages, labels, and titles

## Setup

1. Build the jar.
2. Put `CookieDuel-1.0.jar` in `plugins/`.
3. Make sure the Wild world is already loaded before the plugin starts.
4. Make sure arena template worlds already exist in the server world container.
5. Start the server and review the startup log.

## Notes

- Queue rooms are created live by players, not pre-defined in config
- A player can only own one active queue at a time
- `/cd queue <mode>` automatically uses the player's own name for queue identity
- `%CD_WILD%` and `%CD_ARENA%` reflect live queue counts, not cached values
- Arena templates must already exist on disk
- Wild spawn search uses the configured world's spawn as its origin

## Build

The project uses Gradle with Java 21.

```bash
gradle build
```

Build artifact:

```text
CookieDuel-1.0.jar
```
