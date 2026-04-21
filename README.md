# CookieDuel

CookieDuel is a fast Paper duel plugin built for servers that want clean queue-based matchmaking without bloated setup or confusing command flow.
Players create a duel room with a single command, browse live rooms in a polished GUI, send direct duel requests, and fight in either open-world `WILD` matches or temporary cloned `ARENA` instances.

## Why CookieDuel

- Simple player flow: `/cd queue`, `/cd duel <player>`, `/cd list`, `/cd random`
- Exactly one active public mode at a time: `WILD` or `ARENA`
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

Wild duels use one configured world from `worlds.yml`. CookieDuel searches around that world's spawn, finds a safe center, and places both players on opposite sides with fair spacing. This is the default main mode.

### ARENA

Arena duels clone a configured template world when the fight starts, teleport both players into the temporary arena, and fully clean it up after the duel ends. This mode is optional and should only be enabled when its template is ready.

Only one mode may be enabled in `config.yml`.

- If exactly one mode is enabled, CookieDuel runs normally
- If both modes are enabled, CookieDuel disables itself
- If both modes are disabled, CookieDuel disables itself

## Player commands

- `/cd duel <player>` - send a direct duel request using the active config mode
- `/cd accept` or `/cd accept <player>` - accept a pending direct duel request
- `/cd deny` or `/cd deny <player>` - deny a pending direct duel request
- `/cd queue` - create a queue room using your player name as the room identity and the active config mode
- `/cd list` - open the queue browser GUI
- `/cd random` - join a random valid queue
- `/cd leave` - leave your queue before the duel starts
- `/cd out` - leave your queue, cancel your pending challenge, or forfeit your current duel

## Admin commands

- `/cd admin reload`
- `/cd admin forcestop <player>`
- `/cd admin cleanupinstances`

Alias:

- `/cd`

## Queue browser

`/cd list` opens the live queue GUI.

- Queue entries only show rooms from the active configured mode
- Queue entries show `Player`, `Mode`, and `Money`
- The viewer's own head is displayed in a dedicated slot
- The profile card shows `Player`, `Mode`, `Money`, `Kills`, `Deaths`, and `Points`
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
- `/cd queue` automatically uses the player's own name for queue identity
- `/cd duel <player>`, `/cd queue`, `/cd list`, and `/cd random` all follow the active config mode automatically
- `/cd out` removes your queue immediately and awards the other player the win if you use it during a duel
- Quit, kick, disconnect, and lethal damage also remove stale queue state and award the other player the win in an active duel
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
