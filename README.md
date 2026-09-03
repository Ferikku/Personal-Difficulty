# Personal Difficulty

Server-side Fabric mod for Minecraft Java Edition 26.2. Players can choose a personal difficulty and Keep Inventory setting while the server keeps one real world difficulty.

Vanilla clients can join. The mod only needs to be installed on the Fabric server.

## Verified Target

This project targets Minecraft Java Edition `26.2`.

As of 2026-08-31, Fabric's 26.2 announcement recommends:

- Minecraft `26.2`
- Java `25+`
- Fabric Loader `0.19.3`
- Fabric Loom `1.17.x`
- Gradle `9.5.1`
- Mojang/unobfuscated names, with no Yarn mappings for 26.x

Fabric's 26.2 example project and Fabric Maven currently use Fabric API `0.158.0+26.2`. This project pins that API version and Loom `1.17.20`.

## What It Changes

Personal difficulty mirrors Minecraft's own difficulty rules as closely as possible around each player:

| Player difficulty | Main behavior |
| --- | --- |
| Peaceful | Uses vanilla Peaceful difficulty checks where per-player logic can apply. |
| Easy | Uses vanilla Easy damage scaling, starvation limit, mob aim/difficulty checks, and effect durations. |
| Normal | Uses vanilla Normal behavior. |
| Hard | Uses vanilla Hard behavior. |
| Hardcore | Uses vanilla Hard behavior plus the custom max-heart loss/restoration mechanic. |

Players without a saved personal difficulty follow the server's current difficulty.

## Commands

Admin commands require the configured permission tier, defaulting to gamemaster level:

- `/personaldifficulty`
- `/personaldifficulty get`
- `/personaldifficulty get <player>`
- `/personaldifficulty set peaceful`
- `/personaldifficulty set easy`
- `/personaldifficulty set normal`
- `/personaldifficulty set hard`
- `/personaldifficulty set hardcore`
- `/personaldifficulty setplayer <player> peaceful|easy|normal|hard|hardcore`
- `/personaldifficulty reset`
- `/personaldifficulty reset <player>`
- `/personaldifficulty list`
- `/personaldifficulty reload`

Short aliases:

- `/pdifficulty`
- `/pd`

The startup GUI uses player-accessible helper commands internally so players can submit their first choice and personal Keep Inventory setting:

- `/pdifficulty_apply ...`
- `/pdifficulty_keepInventory ...`

## Config And Persistence

On first launch the mod creates:

- `config/personal-difficulty/config.json`
- `config/personal-difficulty/players.json`

Player choices are saved by UUID and survive server restarts. Config can be changed while the server is running, then reloaded with `/personaldifficulty reload`.

Important config fields:

- `adminPermissionLevel`: `0` all, `1` moderators, `2` gamemasters, `3` admins, `4` owners.

## Limits Compared With True Vanilla Per-Player Difficulty

Minecraft still has one real world difficulty. This mod overrides Minecraft's shared difficulty lookups while player-specific actions are running, but it does not create a second full world simulation.

Known limits:

- World-level systems that are not about one specific player can still use the server's real difficulty.
- Custom mobs that extend Minecraft's normal `Mob` or `Projectile` classes and read `level.getDifficulty()` / `getCurrentDifficultyAt(...)` during AI, attack, player damage, food ticking, or projectile hits should pick up the personal difficulty.
- Custom mobs that cache difficulty at spawn, use a private config system, run attacks outside the normal mob/projectile paths, or apply behavior long after the player-specific action may need a compatibility hook.

## Building

Use Java 25 or newer.

```sh
./gradlew build
```

The server jar will be in `build/libs/`. Install it in the server `mods` folder together with Fabric API for Minecraft 26.2.
