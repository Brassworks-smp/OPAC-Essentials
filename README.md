<div align="center">
  <h1>OPAC - Essentials 2.0</h1>
  <p>The next generation of OPAC - Better Commands.</p>
</div>

 <p>[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62b47a?logo=minecraft)](https://www.minecraft.net/)
[![NeoForge 21.1.216+](https://img.shields.io/badge/NeoForge-21.1.216%2B-f16436)](https://neoforged.net/)
[![Version](https://img.shields.io/badge/version-2.0%20ALPHA-e67e22)](#development-status)
[![OPAC API](https://img.shields.io/badge/OPAC%20API-v1%20%7C%20v2-3498db)](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims)
[![GitHub](https://img.shields.io/badge/GitHub-Repository-181717?logo=github)](https://github.com/DerErneuerer/OPAC-BetterCommands) </p>

[![Discord](https://wsrv.nl/?url=https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg&h=64)](https://discord.com/invite/nDhkgzAPR2)

**OPAC - Essentials** is an addon for [Open Parties and Claims](https://github.com/thexaero/open-parties-and-claims), developed for the [Brassworks SMP](https://brassworks.opnsoc.org/). It expands the original OPAC - Better Commands project with configurable short commands, party chat and fine-grained claim permissions for Minecraft 1.21.1.

Manage access to individual blocks, block entities, entities and throwable items through an in-game screen or commands while keeping OPAC in control of claims and parties.

> [WARNING]
> OPAC - Essentials 2.0 is currently in **ALPHA**. Features, commands, configuration values, saved data and user interfaces may change between releases. Back up your world before updating and test new versions before using them on a production server.

## Version 2.0

Version 2.0 renames and expands **OPAC - Better Commands** into **OPAC - Essentials** and starts the new, configurable generation of the project.

**Every release before 2.0 belongs to the legacy version of the mod.** Those releases use the old **OPAC - Better Commands** name and only provide fixed command features. They do not include configurable command names or the expanded 2.0 permission system and roadmap.

## Features

* Configurable names for every command added by OPAC - Essentials
* Short aliases for commonly used OPAC commands
  * `/claims` for claim management
  * `/party` for party management
* Argument-free `/claim` and `/unclaim` commands
  * `/claim` claims the chunk in which the player is standing
  * `/unclaim` unclaims the chunk in which the player is standing
  * Coordinates and additional chunk arguments are intentionally not accepted
* Party chat with direct messages, toggle and status commands
* In-game claim permission screen through `/claims permissions`
* Separate permissions for main claims and subclaims
* Permissions for all players or a specific player
* Registry-aware search and suggestions for modded content
* Server-authoritative validation and persistent permission storage
* Command fallback for clients that cannot use the permission screen
* Compatibility with OPAC API v1 and API v2

## Command Configuration

Command names can be changed in:

```text
config/opac_essentials-commands.toml
```

Default configuration:

```toml
[commands]
claims = "claims"
party = "party"
claim = "claim"
unclaim = "unclaim"
party_chat = "pchat"
```

Command names may contain 1-32 lowercase letters, numbers, underscores or hyphens. Each configured command must use a unique name and must not conflict with a command registered by another mod. A full server restart is required after changing the configuration.

## Claim Permissions

Claim owners can grant exceptions for individual registry entries instead of opening an entire claim.

| Target | Available actions |
| --- | --- |
| Blocks | Interact, break and place |
| Block entities | Interact and break |
| Entities | Interact and attack |
| Throwable items and projectiles | Throwable | 

Permissions can apply to either all players or one selected player. To manage them, stand inside a claim or subclaim you own and run the configured claims command followed by `permissions`:

```text
/claims permissions
```

The same permissions can also be listed, added and removed through the command tree.

## Party Chat

Using the default command names:

```text
/pchat <message>
/pchat toggle
/pchat status
```

## Requirements and Compatibility

* Minecraft 1.21.1
* NeoForge 21.1.216 or newer within the 21.1 release line
* Open Parties and Claims 0.25.8 or newer

OPAC - Essentials contains a compatibility layer for both **OPAC API v1 and API v2**. This keeps older supported OPAC releases using API v1 compatible while allowing newer API v2 releases to work without a separate build.

Both the client and server need the mod when using the permission UI and its network features.

## Planned Features

The following features are planned and may change during ALPHA development:

* Complete overhaul of the OPAC claim and party interfaces
* A unified Create-inspired UI for claims, subclaims, parties and permissions
* More permission targets and actions
* Permission presets for common automation and multiplayer use cases
* Party-, rank- and group-based permission rules
* Improved permission search, filtering and bulk editing
* Better support for automation, inventories, fluids, vehicles and contraptions
* Additional protection controls for explosions, projectiles and teleportation
* In-game administration and diagnostics for server owners
* Permission import, export and migration tools
* More configuration options and localization support
* Expanded compatibility with other claim, team and automation mods

Roadmap entries are goals, not guarantees. Their scope and release order may change based on testing, OPAC API changes and community feedback.

## Development Status

OPAC - Essentials 2.0 is under active development and should be treated as experimental software. Please include the Minecraft, NeoForge, OPAC and OPAC - Essentials versions when reporting an issue.

## License

Licensed under the [Apache License 2.0](./LICENSE). You may use, modify and redistribute this software under the terms of that license.

## Credits

* Development and design by **DerErneuerer**
* Created for the [Brassworks SMP](https://brassworks.opnsoc.org/)
* Built as an addon for [Open Parties and Claims](https://github.com/thexaero/open-parties-and-claims)

## Links

* [Project repository](https://github.com/DerErneuerer/OPAC-BetterCommands)
* [Brassworks SMP website](https://brassworks.opnsoc.org/)
* [Open Parties and Claims repository](https://github.com/thexaero/open-parties-and-claims)
