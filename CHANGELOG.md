### Added

* Configurable names for every command provided by the mod through `config/opac_essentials-commands.toml`
* Argument-free `/claim` and `/unclaim` shortcuts that operate on the chunk in which the player is standing
* In-game claim permission management through `/claims permissions`
* Separate permission rules for main claims and subclaims
* Permission targets for blocks, block entities, entities, throwable items and projectiles
* Individual actions for interaction, breaking, placing, attacking, item use and projectile impact
* Permission rules for either all players or a specific player
* Registry-aware search and suggestions, including modded content
* Persistent, server-authoritative permission storage and validation
* Command-based permission management as a fallback when the UI is unavailable
* Compatibility layer for both OPAC API v1 and API v2

### Changed

* Renamed **OPAC - Better Commands** to **OPAC - Essentials**
* Expanded the project from a small collection of fixed command aliases into a configurable OPAC utility and permission addon
* `/claim` and `/unclaim` no longer accept coordinates or chunk arguments; they always use the player's current chunk
* `/pchat <message>` and toggled party chat now use OPAC's native `/opm` handling and standard party message format
* Raised the supported NeoForge baseline to **21.1.216** and above for Minecraft 1.21.1
* The permission UI and network features now require OPAC - Essentials on both the client and server

### Fixed

* Restored OPAC's standard party chat prefix and formatting, including the party name, rank colors and custom party names
* Kept native OPAC party chat logging and admin-mode recipients when messages are sent through `/pchat` or toggle mode

### Compatibility

* Minecraft **1.21.1**
* NeoForge **21.1.216 or newer** within the 21.1 release line
* Open Parties and Claims **0.25.8 or newer**
* OPAC **API v1 and API v2**

### Migration Notes

* Releases before 2.0 use the old **OPAC - Better Commands** name and belong to the legacy generation of the mod
* There is no legacy permission data to migrate because the permission system did not exist before 2.0
* The legacy `/party`, `/claims` and `/pchat` names remain the 2.0 defaults, but server owners can rename them in the new command configuration
* A full server restart is required after changing command names
* Back up the world before updating and test ALPHA builds before deploying them to a production server
