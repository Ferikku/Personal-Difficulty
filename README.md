# Personal Difficulty

Server-side Fabric Mod for 26.3. Players can choose a Personal Difficulty and Keep Inventory Setting while the server keeps one real world difficulty.


Vanilla clients can join. The mod only needs to be installed on the Fabric server.


## What It Changes


Personal difficulty mirrors Minecraft's own difficulty rules as closely as possible around each player:


| Player difficulty | Main behavior |
| --- | --- |
| Easy | Uses vanilla Easy damage scaling, starvation limit, mob aim/difficulty checks, and effect durations. |
| Normal | Uses vanilla Normal behavior. |
| Hard | Uses vanilla Hard behavior. |
| Hardcore | Uses vanilla Hard behavior plus the custom max-heart loss/restoration mechanic. |

In **Hardcore mode**, players start with **10 hearts**, lose **1 heart** per death down to a minimum of **3**, and can restore 1 heart by eating an **Enchanted Golden Apple**.

Players without a saved Settings follow the server's current Settings.


## Commands


- `/pdifficulty get <player>`
- `/pdifficulty set <player> <easy|normal|hard|hardcore> <true|false>`
- `/pdifficulty reset <player>`
- `/pdifficulty list`
- `/pdifficulty reload`


## Limits Compared With True Vanilla Per-Player Difficulty


Minecraft still has one real world difficulty. This mod overrides Minecraft's shared difficulty lookups while player-specific actions are running, but it does not create a second full world simulation.


Known limits:


- World-level systems that are not about one specific player can still use the server's real difficulty.
- Custom mobs that extend Minecraft's normal `Mob` or `Projectile` classes and read `level.getDifficulty()` / `getCurrentDifficultyAt(...)` during AI, attack, player damage, food ticking, or projectile hits should pick up the personal difficulty.
- Custom mobs that cache difficulty at spawn, use a private config system, run attacks outside the normal mob/projectile paths, or apply behavior long after the player-specific action may need a compatibility hook.
