<div align="center">
<h1>OPAC - Essentials</h1>
<p>The next generation of OPAC - Better Commands.</p>
</div>
<div align="center"><a href="https://www.minecraft.net/" rel="nofollow"> <img src="https://img.shields.io/badge/Minecraft-1.21.1-62b47a?logo=minecraft" alt="Minecraft 1.21.1"> </a> <a href="https://neoforged.net/" rel="nofollow"> <img src="https://img.shields.io/badge/NeoForge-21.1.216%2B-f16436" alt="NeoForge 21.1.216 or newer"> </a> <a href="#development-status"> <img src="https://img.shields.io/badge/version-2.0%20ALPHA-e67e22" alt="Version 2.0 ALPHA"> </a> <a href="https://github.com/Brassworks-smp/OPAC-Essentials" rel="nofollow"> <img src="https://img.shields.io/badge/GitHub-Repository-181717?logo=github" alt="GitHub Repository"> </a> <a href="https://discord.com/invite/nDhkgzAPR2" rel="nofollow"> <img src="https://img.shields.io/badge/Discord-Join%20Server-5865F2?logo=discord&amp;logoColor=white" alt="Join the Discord server"></a></div>
<div align="center">&nbsp;</div>
<p><strong>OPAC - Essentials</strong> is an addon for <a href="https://github.com/thexaero/open-parties-and-claims" rel="nofollow">Open Parties and Claims</a>, developed for the <a href="https://brassworks.opnsoc.org/" rel="nofollow">Brassworks SMP</a>. It expands the original OPAC - Better Commands project with configurable short commands, party chat and fine-grained claim permissions for Minecraft 1.21.1.</p>
<p>Manage access to individual blocks, block entities, entities and throwable items through an in-game screen or commands while keeping OPAC in control of claims and parties.</p>
<blockquote>
<p><strong>⚠ WARNING</strong></p>
<p>OPAC - Essentials 2.0 is currently in <strong>ALPHA</strong>. Features, commands, configuration values, saved data and user interfaces may change between releases. Back up your world before updating and test new versions before using them on a production server.</p>
</blockquote>
<h2>Version 2.0</h2>
<p>Version 2.0 renames and expands <strong>OPAC - Better Commands</strong> into <strong>OPAC - Essentials</strong> and starts the new, configurable generation of the project.</p>
<p><strong>Every release before 2.0 belongs to the legacy version of the mod.</strong> Those releases use the old <strong>OPAC - Better Commands</strong> name and only provide fixed command features. They do not include configurable command names or the expanded 2.0 permission system and roadmap.</p>
<h2>Features</h2>
<ul>
<li>Configurable names for every command added by OPAC - Essentials</li>
<li>Short aliases for commonly used OPAC commands
<ul>
<li><code>/claims</code> for claim management</li>
<li><code>/party</code> for party management</li>
</ul>
</li>
<li>Argument-free <code>/claim</code> and <code>/unclaim</code> commands
<ul>
<li><code>/claim</code> claims the chunk in which the player is standing</li>
<li><code>/unclaim</code> unclaims the chunk in which the player is standing</li>
<li>Coordinates and additional chunk arguments are intentionally not accepted</li>
</ul>
</li>
<li>Party chat with direct messages, toggle and status commands</li>
<li>In-game claim permission screen through <code>/claims permissions</code></li>
<li>Separate permissions for main claims and subclaims</li>
<li>Permissions for all players or a specific player</li>
<li>Registry-aware search and suggestions for modded content</li>
</ul>
<div align="center"><img src="https://cdn.modrinth.com/data/cached_images/7eac537a5b2e55b630641d2191babbeea4f08eb6_0.webp" alt="Preview of the OPAC Essentials claim permissions interface"></div>
<h2>Command Configuration</h2>
<p>Command names can be changed in:</p>
<pre><code>config/opac_essentials-commands.toml</code></pre>
<p>Default configuration:</p>
<pre><code>[commands]
claims = "claims"
party = "party"
claim = "claim"
unclaim = "unclaim"
party_chat = "pchat"</code></pre>
<p>Command names may contain 1-32 lowercase letters, numbers, underscores or hyphens. Each configured command must use a unique name and must not conflict with a command registered by another mod. A full server restart is required after changing the configuration.</p>
<h2>Claim Permissions</h2>
<p>Claim owners can grant exceptions for individual registry entries instead of opening an entire claim.</p>
<table>
<thead>
<tr>
<th>Target</th>
<th>Available actions</th>
</tr>
</thead>
<tbody>
<tr>
<td>Blocks</td>
<td>Interact, break and place</td>
</tr>
<tr>
<td>Block entities</td>
<td>Interact and break</td>
</tr>
<tr>
<td>Entities</td>
<td>Interact and attack</td>
</tr>
<tr>
<td>Throwable items and projectiles</td>
<td>Throwable</td>
</tr>
</tbody>
</table>
<p>Permissions can apply to either all players or one selected player. To manage them, stand inside a claim or subclaim you own and run the configured claims command followed by <code>permissions</code>:</p>
<pre><code>/claims permissions</code></pre>
<p>The same permissions can also be listed, added and removed through the command tree.</p>
<h2>Party Chat</h2>
<p>Using the default command names:</p>
<pre><code>/pchat &lt;message&gt;
/pchat toggle
/pchat status</code></pre>
<h2>Requirements and Compatibility</h2>
<ul>
<li>Minecraft 1.21.1</li>
<li>NeoForge 21.1.216 or newer within the 21.1 release line</li>
<li>Open Parties and Claims 0.25.8 or newer</li>
</ul>
<p>OPAC - Essentials contains a compatibility layer for both <strong>OPAC API v1 and API v2</strong>. This keeps older supported OPAC releases using API v1 compatible while allowing newer API v2 releases to work without a separate build.</p>
<p>Both the client and server need the mod when using the permission UI and its network features.</p>
<h2>Planned Features</h2>
<p>The following features are planned and may change during ALPHA development:</p>
<ul>
<li>Complete overhaul of the OPAC claim and party interfaces</li>
<li>A unified Create-inspired UI for claims, subclaims, parties and permissions</li>
<li>More permission targets and actions</li>
<li>Permission presets for common automation and multiplayer use cases</li>
<li>Party-, rank- and group-based permission rules</li>
<li>Improved permission search, filtering and bulk editing</li>
<li>Better support for automation, inventories, fluids, vehicles and contraptions</li>
<li>Additional protection controls for explosions, projectiles and teleportation</li>
<li>In-game administration and diagnostics for server owners</li>
<li>Permission import, export and migration tools</li>
<li>More configuration options and localization support</li>
<li>Expanded compatibility with other claim, team and automation mods</li>
</ul>
<p>Roadmap entries are goals, not guarantees. Their scope and release order may change based on testing, OPAC API changes and community feedback.</p>
<h2 id="development-status">Development Status</h2>
<p>OPAC - Essentials 2.0 is under active development and should be treated as experimental software. Please include the Minecraft, NeoForge, OPAC and OPAC - Essentials versions when reporting an issue.</p>
<h2>License</h2>
<p>Licensed under the <a href="https://github.com/Brassworks-smp/OPAC-Essentials/blob/main/LICENSE" rel="nofollow"> Apache License 2.0 </a>. You may use, modify and redistribute this software under the terms of that license.</p>
<h2>Credits</h2>
<ul>
<li>Development and design by <strong>DerErneuerer</strong></li>
<li>Created for the <a href="https://brassworks.opnsoc.org/" rel="nofollow">Brassworks SMP</a></li>
<li>Built as an addon for <a href="https://github.com/thexaero/open-parties-and-claims" rel="nofollow"> Open Parties and Claims </a></li>
</ul>
<h2>Links</h2>
<ul>
<li><a href="https://github.com/Brassworks-smp/OPAC-Essentials" rel="nofollow"> Project repository </a></li>
<li><a href="https://brassworks.opnsoc.org/" rel="nofollow"> Brassworks SMP website </a></li>
<li><a href="https://github.com/thexaero/open-parties-and-claims" rel="nofollow"> Open Parties and Claims repository </a></li>
</ul>
<div><img src="https://cdn.modrinth.com/data/cached_images/c6255d91356f6087b95d4973969100dab69defa7_0.webp" alt="Divider"></div>
<div align="center"><a href="https://github.com/Brassworks-smp/OPAC-Essentials" rel="nofollow"> <img src="https://img.shields.io/badge/View-Source%20Code-181717?logo=github" alt="View source code on GitHub"> </a> <a href="https://brassworks.opnsoc.org/discord" rel="nofollow"> <img src="https://img.shields.io/badge/Join-Discord-5865F2?logo=discord&amp;logoColor=white" alt="Join the Discord server"> </a></div>
