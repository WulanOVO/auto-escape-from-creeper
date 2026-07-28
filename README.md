# Auto Escape From Creeper (AEFC)

> [!WARNING]
> **This mod is still in the early stages of development.** The escape algorithm may not perform as expected under demanding conditions, such as indoors or on complex terrain, and could lead to serious, unforeseen consequences. Please consider carefully whether you need this mod. _(Placing a few extra torches to prevent mob spawning is always preferable to any emergency escape measures)_

A client-only mod that automatically reacts when a Creeper attempts to sneak up on you, helping you escape easily.

When you have your inventory open, are viewing a chest, or a Creeper creeps behind you and ignites without you noticing, the mod instantly triggers and takes temporary control of your player. Using intelligent algorithms, it quickly moves you away from the Creeper to protect you and your builds.

## Features
- **Smart Triggering**: Only activates during vulnerable moments such as when you are interacting with GUIs or when a Creeper sneaks behind you. It does not interfere with regular combat.
- **Automatic Pathfinding**: Intelligent algorithms calculate escape routes and automatically avoid obstacles.
- **Fallback Mechanic**: Two independently configurable shield options — raise shield immediately when trapped with no escape path, or raise shield when escape is predicted to fail due to time constraints.
- **Block Protection**: When a Creeper ignites near your precious blocks (chests, barrels, etc.), the mod can automatically place water at the Creeper's feet before escaping — preventing block destruction even if escape fails.
- **Graphical Configuration**: Supports Mod Menu + Cloth Config API for adjusting all settings in-game.

## Multiplayer Information
This is a client-only mod. It can theoretically be used on any server. **Before using it, confirm that the server permits this mod** to avoid unfair gameplay advantages or bans from server anti-cheat systems.

## Compatibility Requirements
- Fabric Loader >= 0.19.0
- Fabric API
- Cloth Config API
- Java 25+

## License
MIT License
