<div align="center">

# OPAC - Essentials

The next generation of OPAC - Better Commands.

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62b47a?logo=minecraft)](https://www.minecraft.net/)
[![NeoForge 21.1.216+](https://img.shields.io/badge/NeoForge-21.1.216%2B-f16436)](https://neoforged.net/)
[![GitHub Repository](https://img.shields.io/badge/GitHub-Repository-181717?logo=github)](https://github.com/Brassworks-smp/OPAC-Essentials)
[![Join Discord](https://img.shields.io/badge/Discord-Join%20Server-5865F2?logo=discord&logoColor=white)](https://discord.com/invite/nDhkgzAPR2)

</div>

**OPAC - Essentials** is an addon for [Open Parties and Claims](https://github.com/thexaero/open-parties-and-claims), developed for the [Brassworks SMP](https://brassworks.opnsoc.org/). It adds configurable short commands, party chat, fine-grained claim permissions and a Xaero's World Map Claim Search integration.

**The UI on version 2.2 or higher requires KotlinForForge**

## Legacy Releases

Every release before 2.0 belongs to the legacy generation of the mod. Those releases use the old **OPAC - Better Commands** name and only provide fixed command features. They do not include configurable command names or the current permission system.

## Features

* Configurable names for every command added by OPAC - Essentials
* Short aliases for commonly used OPAC commands
* Argument-free `/claim` and `/unclaim` commands
* Party chat with direct messages, toggle and status commands
* Claim permission interface through `/claims permissions`
* Claim owner search directly in Xaero's World Map

![ui preview](https://cdn.modrinth.com/data/cached_images/07f4e4e81725d49e62d36932ec57c7db6098c064.png)

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
| Throwable items and projectiles | Throw, use and projectile impact |

Rules can apply to all players or to one or more selected players. To manage them, stand inside a claim or subclaim you own and run:

```text
/claims permissions
```

The interface supports search, registry suggestions, multiple actions and players, per-target player assignments and bulk access changes. Edits remain staged until **Save & Done** is pressed. Server administrators with permission level 2 can also manage the claim or subclaim in which they are standing.

Permissions can also be listed, added and removed through the command tree. Older grouped permission data is migrated automatically when loaded.

## Party Chat

Using the default command names:

```text
/pchat <message>
/pchat toggle
/pchat status
```

Party messages use OPAC's native handling and formatting. Errors returned by OPAC are passed through to the player.

## Xaero's World Map Claim Search

When Xaero's World Map is installed, a search button is added to the map interface. Enter a player's name to center the map on their first claim cluster. Previous and next controls cycle through disconnected clusters, while a high-visibility outline marks the current selection.

Claim snapshots and cluster calculations are processed incrementally and asynchronously to reduce client-side lag with large claim counts.

## Requirements and Compatibility

* Minecraft 1.21.1
* NeoForge 21.1.216 or newer within the 21.1 release line
* Open Parties and Claims 0.25.8 or newer
* Xaero's World Map for the optional claim search interface
* If on Client, **KotlinForForge** is required

OPAC - Essentials contains a compatibility layer for both **OPAC API v1 and API v2**. This keeps older supported OPAC releases using API v1 compatible while allowing newer API v2 releases to work without a separate build.

Both the client and server need the mod when using the permission UI and its network features. The client also needs KotlinForForge. The Xaero claim search itself is a client-side World Map integration.

## Planned Features

The following features are planned and may change during development:

* Complete overhaul of the OPAC claim and party interfaces
* A unified UI for claims, subclaims, parties and permissions
* More permission targets and actions
* Party-, rank- and group-based permission rules
* Permission import, export and advanced migration tools
* More admin commands to manage Claim/Party related stuff

Roadmap entries are goals, not guarantees. Their scope and release order may change based on testing, OPAC API changes and community feedback.

## License

Licensed under the [Apache License 2.0](https://github.com/Brassworks-smp/OPAC-Essentials/blob/main/LICENSE). You may use, modify and redistribute this software under the terms of that license.

## Credits

* Development and design by **DerErneuerer**
* Created for the [Brassworks SMP](https://brassworks.opnsoc.org/)
* Built as an addon for [Open Parties and Claims](https://github.com/thexaero/open-parties-and-claims)

## Links

* [Project repository](https://github.com/Brassworks-smp/OPAC-Essentials)
* [Changelog](https://github.com/Brassworks-smp/OPAC-Essentials/blob/main/CHANGELOG.md)
* [Brassworks SMP website](https://brassworks.opnsoc.org/)
* [Open Parties and Claims repository](https://github.com/thexaero/open-parties-and-claims)

<div><img src="https://cdn.modrinth.com/data/cached_images/c6255d91356f6087b95d4973969100dab69defa7_0.webp" alt="Divider"></div>

<div align="center">

[![View Source Code](https://img.shields.io/badge/View-Source%20Code-181717?logo=github)](https://github.com/Brassworks-smp/OPAC-Essentials)
[![Join Discord](https://img.shields.io/badge/Join-Discord-5865F2?logo=discord&logoColor=white)](https://brassworks.opnsoc.org/discord)

</div>
