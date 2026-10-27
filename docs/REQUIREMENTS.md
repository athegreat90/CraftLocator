
# PROJECT: CRAFTLOCATOR
# Minecraft Java Edition 26.3 — NeoForge
# Production-ready Minecraft resource and crafting locator

You are an expert Minecraft mod developer, Kotlin engineer, Java 25 engineer, NeoForge API specialist, and software architect.

Your task is to design, implement, test, document, and prepare a fully functional Minecraft mod named CraftLocator.

Do not stop after generating a project skeleton.

Do not deliver pseudocode, unfinished implementations, mock functionality, or placeholder classes.

Implement a genuinely working mod and validate it against Minecraft Java 26.3 and NeoForge 26.3.x.

Work autonomously. Make sensible engineering decisions without asking for approval unless absolutely necessary.

==================================================
1. PROJECT IDENTITY
==================================================

Project name: CraftLocator
Mod ID: craftlocator
Base package: io.github.athegreat90.craftlocator
Minecraft version: 26.3
Mod loader: NeoForge 26.3.x
Java toolchain: Java 25
Primary language: Kotlin 2.x
Kotlin language adapter: KotlinLangForge
Build system: Gradle with ModDevGradle
Minimum supported environment: Minecraft 26.3

Target environments:
- Single-player integrated server
- Multiplayer dedicated NeoForge server
- Windows development environment
- Linux dedicated server

The mod must be usable through Minecraft commands.

All authoritative searching and recipe analysis must run on the logical server.

The core functionality must not require a client-side HUD, custom rendering, or another gameplay mod.

Prefer commands and vanilla-compatible chat output over unnecessary GUI or networking complexity.

==================================================
2. PRE-IMPLEMENTATION REQUIREMENTS
==================================================

Before writing code:

1. Inspect the repository structure.
2. Inspect any existing CLAUDE.md or AGENTS.md.
3. Verify the latest compatible NeoForge 26.3.x release.
4. Verify the official Minecraft 26.3 NeoForge MDK.
5. Verify the supported ModDevGradle version.
6. Verify the Kotlin compiler version and KotlinLangForge dependency.
7. Confirm Java 25 compatibility.
8. Inspect the relevant Minecraft 26.3 API signatures.
9. Identify the correct server command registration approach.
10. Identify the current inventory capability API.
11. Identify how to inspect loaded chunks without generating or loading additional chunks.
12. Identify the current RecipeManager API.

Authoritative references:

https://neoforged.net/
https://neoforged.net/changelog/
https://docs.neoforged.net/
https://projects.neoforged.net/
https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle
https://github.com/neoforged/NeoForge
https://modrinth.com/mod/kotlin-lang-forge
https://github.com/Mojang/brigadier

Important:

Minecraft 26.3 has API changes compared with 1.21.x and earlier.

Do not blindly copy old tutorials.

Use the actual Minecraft 26.3 class names, methods, registries, and NeoForge APIs.

In particular, investigate the newer ResourceHandler<ItemResource> inventory capability API rather than assuming the legacy IItemHandler API remains valid.

If an example from an older version no longer compiles, adapt it using the actual current source or development classpath.

Pin compatible dependency versions.

Do not use dynamic dependency versions such as "latest.release", "+" or unspecified snapshot versions.

Keep dependency declarations centralized.

==================================================
3. PRIMARY FUNCTIONALITY
==================================================

The mod has TWO principal responsibilities.

A. RESOURCE LOCATOR

Find the nearest matching resource, block, dropped item, or authorized inventory source within a configurable radius.

Examples:

/craftlocate find block minecraft:diamond_ore
/craftlocate find block minecraft:crafting_table
/craftlocate find item minecraft:diamond
/craftlocate source minecraft:diamond

B. CRAFTING MATERIAL LOCATOR

Determine what the player needs to craft an item and help locate nearby sources of missing ingredients.

Examples:

/craftlocate craft minecraft:diamond_pickaxe
/craftlocate craft minecraft:iron_pickaxe
/craftlocate craft minecraft:chest 4
/craftlocate craft minecraft:crafting_table

The mod must:

1. Resolve the requested item.
2. Identify a valid crafting recipe.
3. Calculate required quantities.
4. Read the player's inventory.
5. Calculate missing quantities.
6. Resolve craftable intermediate ingredients.
7. Identify known obtainable world sources.
8. Search nearby loaded chunks.
9. Return source coordinates when found.
10. Calculate distance to those positions.
11. Explain how to obtain the missing materials.
12. Distinguish actual available items from resources that must first be harvested or processed.

Never invent resource coordinates.

Never report an item as physically present when only a potential source block has been located.

==================================================
4. COMMAND INTERFACE
==================================================

Implement the following commands using Brigadier.

4.1 Find the nearest block

/craftlocate find block <block_id> [radius]

Example:

/craftlocate find block minecraft:diamond_ore 64

Expected output:

[CraftLocator] Found Diamond Ore
Position: X: 125, Y: -52, Z: 341
Dimension: minecraft:overworld
Distance: 37.4 blocks
Search radius: 64 blocks
Source: Existing loaded block

4.2 Find a nearby item

/craftlocate find item <item_id> [radius]

Search:
- Dropped Item entities in loaded areas
- Authorized inventories only when enabled

Example:

/craftlocate find item minecraft:diamond 64

This command must distinguish between a dropped diamond and a diamond stored in a container.

Do not treat diamond ore as an existing diamond item.

4.3 Find a source of an item

/craftlocate source <item_id> [radius]

Example:

/craftlocate source minecraft:diamond

Search for:
- Existing nearby matching dropped items
- Known world blocks that can provide the requested item
- Authorized inventory sources if enabled

For diamonds, known source blocks may include:
- minecraft:diamond_ore
- minecraft:deepslate_diamond_ore

A block source is not proof that the requested item can be harvested with the player's current tools.

Report the required harvesting or processing conditions when known.

4.4 Crafting planner

/craftlocate craft <item_id> [quantity] [radius]

Example:

/craftlocate craft minecraft:diamond_pickaxe 1 64

Expected output format:

[CraftLocator] Crafting: Diamond Pickaxe x1

Required:
- Diamond x3
- Stick x2

Inventory:
- Diamond: 1/3
- Stick: 2/2

Missing:
- Diamond x2

Nearest known sources:
- Diamond Ore
  X: 142, Y: -48, Z: 294
  Distance: 31 blocks

Crafting station:
- Crafting Table required
- Nearest station: X: 120, Y: 65, Z: 300
  OR
- No existing table found; provide its recipe.

Show available information without creating misleading results.

4.5 Search controls

/craftlocate status
/craftlocate cancel
/craftlocate help

Status:
- Active search target
- Progress
- Blocks examined
- Loaded chunks examined
- Elapsed time
- Current result count

Cancel:
- Stop the player's current search safely
- Release query state
- Avoid leaving persistent scheduled work

Help:
- Explain every command
- Show syntax and examples
- Explain loaded-chunk limitations

4.6 Command completion

Use actual item and block registries.

Provide:
- Namespaced ID suggestions
- Vanilla item suggestions
- Modded item suggestions
- Modded block suggestions
- Contextual radius suggestions where practical

Unknown identifiers must produce helpful errors.

Invalid arguments must not crash the server.

==================================================
5. SEARCH ENGINE
==================================================

Implement a dedicated SearchEngine.

The search engine must operate exclusively on loaded world data.

Never:
- Generate new chunks
- Force-load chunks
- Teleport the player
- Modify blocks
- Break resources
- Alter inventories
- Trigger exploration merely to answer a search

5.1 Search boundaries

Default radius: 64 blocks
Maximum configurable radius: 128 blocks
Minimum radius: 1 block

Search must be three-dimensional.

Respect:
- Minimum build height
- Maximum build height
- World borders
- Current dimension
- Loaded chunk boundaries

Do not search other dimensions unless a future explicitly authorized feature adds that behavior.

5.2 Nearest-object correctness

Use squared Euclidean distance when comparing positions.

distanceSquared =
    dx * dx + dy * dy + dz * dz

Only calculate the square root when formatting final output.

The nearest object means the closest matching eligible object within the fully examined search domain.

Do not return the first match encountered unless it is mathematically proven to be the nearest.

Use deterministic tie-breaking.

Prioritize nearby chunk sections.

Maintain the current closest candidate.

Prune sections when their minimum possible distance is greater than the closest known candidate.

Do not prematurely stop scanning when an unchecked region could contain a closer match.

5.3 Performance

Minecraft server responsiveness is mandatory.

Never scan millions of blocks synchronously inside a single command invocation.

Use incremental scanning scheduled across server ticks.

Suggested configurable defaults:

maxBlockChecksPerTick = 2048
maxScanTimeMillisPerTick = 2
maxActiveSearchesPerPlayer = 1
maxConcurrentSearches = 4
maxSearchRadius = 128

Treat the tick budget as a global search-work budget, not a separate unrestricted budget per player.

Distribute search effort fairly among players.

Use an incremental state machine.

Do not implement a blocking while-loop that scans an entire search radius during one tick.

Pause and resume using server tick events or another verified scheduling mechanism.

5.4 Chunk handling

Inspect the actual 26.3 APIs for querying currently loaded chunks.

Avoid any API call that implicitly synchronously loads or generates a chunk.

Do not use a chunk-fetching method unless its non-loading behavior is verified.

Optimize scanning by:
- Processing loaded chunks
- Processing sections
- Skipping irrelevant sections where possible
- Using section palette information where supported
- Avoiding repeated allocation
- Avoiding scanning identical positions twice
- Reusing bounded search state
- Pruning impossible search regions

If a palette optimization is unavailable or unsafe, use a correct bounded fallback.

5.5 Multithreading

Do not access Minecraft world state from arbitrary background threads.

World reads must occur on the appropriate server thread unless an API explicitly guarantees another access method is safe.

Pure calculations may be separated from world access.

Do not make scanning asynchronous simply by placing unsafe world calls inside a coroutine or Java executor.

Correctness and thread safety take priority over artificial parallelism.

==================================================
6. SEARCH JOB MANAGEMENT
==================================================

Create a SearchJob abstraction.

Every job must include:

- Unique internal job ID
- Requesting player UUID
- Dimension
- Requested target
- Starting position
- Requested radius
- Search type
- Progress counters
- Search lifecycle state
- Best candidate
- Number of examined blocks
- Number of skipped unloaded sections
- Creation time
- Cancellation flag

Possible states:

CREATED
QUEUED
RUNNING
COMPLETED
CANCELLED
FAILED

Each player may have only one active search by default.

A new request should cancel or replace the previous job according to a documented policy.

Handle:
- Player disconnect
- Player death
- Dimension change
- Server shutdown
- Datapack reload
- Search timeout
- Command cancellation
- Unloaded chunks

If the player changes dimension, cancel the original search rather than silently continuing in the wrong world.

Release references to players, levels, and completed jobs.

Avoid memory leaks.

==================================================
7. CRAFTING RECIPE ENGINE
==================================================

Create a RecipeResolver service.

Read recipes from the server's active RecipeManager.

Support recipes provided by:
- Vanilla Minecraft
- Datapacks
- NeoForge mods using supported standard recipe types

The initial complete implementation must support:

- Shaped crafting recipes
- Shapeless crafting recipes
- Recipe output quantities
- Ingredient alternatives
- Item registry IDs
- Item tags where supported
- Multiple recipes producing the same output
- Crafting table requirements

The implementation must use the current Minecraft 26.3 recipe representation.

Never assume older RecipeHolder, Ingredient, or RecipeManager signatures without verification.

7.1 Recursive dependency resolution

Example:

Diamond Pickaxe:
- Diamonds
- Sticks

Sticks:
- Planks

Planks:
- Logs

Resolve dependencies recursively with bounded recursion.

Include:
- Maximum recursion depth
- Cycle detection
- Maximum expanded recipe nodes
- Quantity propagation
- Intermediate crafting output counts
- Surplus handling
- Reusable ingredient inventory accounting

Do not count an inventory item as available twice when multiple crafting branches require it.

Example:

If crafting one stick batch produces four sticks and the target needs two, account correctly for the recipe output count.

If two separate ingredients need the same materials, share inventory accounting rather than independently consuming the same materials twice.

7.2 Recipe alternatives

When multiple recipes exist:
- Prefer recipes with greater inventory coverage
- Prefer fewer missing materials
- Prefer fewer unsupported transformations
- Use deterministic tie-breaking
- Respect declared crafting constraints

Do not implement an unbounded exponential search over every recipe combination.

Use bounded evaluation and caching where appropriate.

Document the optimization heuristic.

A heuristic result must not be described as a globally optimal solution unless proven.

7.3 Unsupported recipes

Do not guess unsupported recipes.

If a mod adds a proprietary processing machine or custom recipe type that the resolver cannot interpret, show:

"Recipe exists, but automatic material analysis is unavailable for this recipe type."

Continue supporting other recipes normally.

Do not crash on unusual recipes.

7.4 Smelting and processing

Design the service architecture to accommodate:
- Furnace smelting
- Blasting
- Smoking
- Stonecutting
- Smithing
- Custom mod processing

Implement standard processing recipes only after the shaped and shapeless planner is working and tested.

If implemented, correctly account for processing stations, ingredients, and fuel where applicable.

Do not claim smelted output is directly obtainable from raw ore without processing.

==================================================
8. PLAYER INVENTORY ANALYSIS
==================================================

Read the requesting player's actual inventory on the logical server.

Account for:
- Item type
- Item count
- Inventory availability
- Multiple matching stacks
- Tag-equivalent ingredients
- Relevant data components

Do not remove, consume, or move any items.

This is a read-only planning tool.

The planner should produce:

Required
Available
Missing
Surplus

Example:

Target: Chest x4

Required:
- Wooden Planks x32

Available:
- Oak Planks x20

Missing:
- Compatible Wooden Planks x12

The item/tag matching logic must respect recipe ingredient alternatives.

Do not assume every recipe requires one exact item variant.

==================================================
9. WORLD SOURCE RESOLUTION
==================================================

Create a SourceResolver service.

The service maps an item to possible world sources.

Support:

A. Exact matching dropped items

B. Known vanilla resource blocks

C. Configured resource-to-item mappings

D. Compatible modded blocks when a valid source rule exists

E. Authorized container contents when enabled

Example mappings:

diamond:
- diamond_ore
- deepslate_diamond_ore

coal:
- coal_ore
- deepslate_coal_ore

iron_ingot:
- Potentially iron-containing raw resources
- Requires a valid processing path

wooden_planks:
- Valid wood or stem sources
- Requires crafting

Mappings must not imply that harvesting a block always yields the target item.

Account for known requirements:
- Tool tier
- Silk Touch
- Processing
- Item transformation
- Loot conditions

When harvesting is conditional, label the source as a potential source.

9.1 Data-driven mappings

Store source rules in data files.

Use a schema along these lines:

{
  "target_item": "minecraft:diamond",
  "source_blocks": [
    "minecraft:diamond_ore",
    "minecraft:deepslate_diamond_ore"
  ],
  "source_type": "harvestable_block"
}

This is an illustrative schema, not a requirement to use exactly these fields.

Implement proper schema validation.

Support optional metadata for source conditions and transformations.

Support reloadable datapack resources if compatible with the current API.

Invalid mappings must log a helpful error without crashing the entire mod.

9.2 Mod compatibility

Do not hardcode all supported resources to the minecraft namespace.

Use registries and data-driven mappings.

For unknown modded items:

- Search direct dropped items normally
- Resolve standard crafting recipes normally
- Use declared source rules where available
- Otherwise report that no world-source mapping is known

Never invent a harvesting relationship.

==================================================
10. CONTAINER SEARCHES AND PRIVACY
==================================================

Container searches are optional and DISABLED by default.

Potential container types:
- Chest
- Barrel
- Shulker box
- Other compatible inventory block entities

If enabled, inspect the verified NeoForge 26.3 inventory capabilities.

Use the current ResourceHandler<ItemResource> API where appropriate.

Read inventory contents without modifying anything.

Important multiplayer restrictions:

- Non-operators must not inspect arbitrary private inventories.
- Never assume that a container is publicly accessible merely because it exposes an inventory capability.
- Avoid bypassing claim-protection or inventory-lock mechanisms.
- If authorization cannot be established, omit that container.
- Do not enumerate other players' private inventories.

Allow server operators to configure the feature explicitly.

A server administrator enabling inventory searches must understand the privacy implications.

==================================================
11. CRAFTING STATION LOCATOR
==================================================

Detect the crafting station required by a supported recipe.

For normal 3x3 crafting recipes, search for nearby crafting tables.

Return coordinates and distance if found.

For recipes that do not require a crafting table, avoid incorrectly claiming one is mandatory.

For supported processing recipes, identify the required workstation.

If no station exists nearby:
- Explain the requirement
- Show how to craft the station if supported
- Do not fabricate a station coordinate

==================================================
12. OUTPUT AND USER EXPERIENCE
==================================================

Use Minecraft translatable text components.

Create English translations as the default.

Also provide Spanish translations.

All user-facing messages should be readable and consistent.

Recommended translation files:

assets/craftlocator/lang/en_us.json
assets/craftlocator/lang/es_es.json

12.1 Search result formatting

Example:

[CraftLocator]
Nearest Diamond Ore

Coordinates: 120, -54, 315
Distance: 24.7 blocks
Dimension: Overworld
Source: Loaded world block

12.2 Clickable coordinates

Implement clickable coordinates when supported safely by the current component API.

Preferred behavior:
- Click to copy coordinates
- Hover to show coordinates and dimension

Do not automatically teleport the player.

Do not add commands that bypass the player's permissions.

12.3 Progress messages

For long-running searches:

"Searching loaded chunks..."
"Search 42% complete..."

Avoid sending a chat message every tick.

Use throttled progress output.

12.4 No-results messages

Differentiate:

- No matching block found in the examined loaded area
- No valid recipe
- No known source mapping
- Some chunks were not loaded
- Search cancelled
- Permission denied
- Unsupported recipe type

Do not say an item does not exist anywhere in the world merely because the scan returned no match.

==================================================
13. PERMISSIONS
==================================================

This feature can reveal hidden ores, so multiplayer access must be controlled.

Default policy:

- Operators can use world-resource scanning.
- Ordinary players cannot reveal hidden resources unless the administrator explicitly permits it.
- Crafting recipe analysis may be granted separately.
- Container searches require additional explicit authorization.

Provide configurable permission policy.

Use NeoForge-compatible command permission checks.

Never trust client-side permission checks.

Validate permissions before starting jobs and before revealing results.

If a permission system integration is unavailable, use a documented server-operator policy.

Avoid implementing unsafe workarounds.

==================================================
14. CONFIGURATION
==================================================

Use the NeoForge configuration system.

Prefer server configuration for gameplay-affecting settings.

Suggested options:

search.defaultRadius = 64
search.maximumRadius = 128
search.maxBlockChecksPerTick = 2048
search.maxScanTimeMillisPerTick = 2
search.maxConcurrentSearches = 4
search.searchLoadedChunksOnly = true

features.enableBlockSearch = true
features.enableDroppedItemSearch = true
features.enableSourceSearch = true
features.enableCraftingPlanner = true
features.enableContainerSearch = false
features.enableCraftingStationSearch = true

security.requireOperatorForWorldSearch = true
security.requireOperatorForContainerSearch = true

performance.enableSectionPaletteOptimization = true
performance.enableBoundedCaching = true
performance.enableProgressNotifications = true

Treat "searchLoadedChunksOnly" as a mandatory safety invariant for this version.

It must not be possible to turn off this invariant through configuration.

Validate all configurable values.

Use safe defaults.

==================================================
15. ARCHITECTURE
==================================================

Use clean, modular architecture without unnecessary frameworks.

Suggested package layout:

io.github.athegreat90.craftlocator
|
+-- CraftLocatorMod.kt
|
+-- command/
|   +-- CraftLocatorCommand.kt
|   +-- CommandSuggestions.kt
|   +-- CommandFeedback.kt
|
+-- config/
|   +-- CraftLocatorConfig.kt
|
+-- search/
|   +-- SearchEngine.kt
|   +-- SearchJob.kt
|   +-- SearchJobManager.kt
|   +-- SearchQuery.kt
|   +-- SearchResult.kt
|   +-- SearchScheduler.kt
|   +-- BlockSearchStrategy.kt
|   +-- ItemSearchStrategy.kt
|
+-- recipe/
|   +-- RecipeResolver.kt
|   +-- CraftingPlanner.kt
|   +-- RecipeGraph.kt
|   +-- MaterialRequirement.kt
|
+-- source/
|   +-- SourceResolver.kt
|   +-- SourceRule.kt
|   +-- SourceRuleLoader.kt
|
+-- inventory/
|   +-- PlayerInventoryReader.kt
|   +-- ContainerInventoryReader.kt
|
+-- security/
|   +-- PermissionService.kt
|
+-- util/
|   +-- DistanceUtils.kt
|   +-- RegistryUtils.kt
|   +-- MessageUtils.kt

This is a proposed structure.

Adjust it where doing so demonstrably improves clarity, performance, or compatibility.

Do not create one-line wrapper classes without meaningful responsibility.

Do not invent abstractions only to satisfy the directory structure.

Use:
- Kotlin data classes
- Kotlin sealed interfaces/classes where useful
- Kotlin runCatching for recoverable operations where appropriate
- Immutable value objects
- Explicit lifecycle management
- Dependency injection through constructors where useful

Avoid:
- Global mutable search state without lifecycle management
- Excessive singleton objects
- Circular dependencies
- Unsafe null assertions
- Unnecessary reflection
- Unsafe coroutines
- Broad exception swallowing

==================================================
16. CACHING
==================================================

Implement bounded caching only where it produces measurable benefits.

Candidates:
- Recipe lookup results
- Parsed source mappings
- Registry mappings
- Reusable immutable planning data

Do not build a permanent global cache of every block or ore coordinate.

Cache invalidation is mandatory.

Account for:
- Datapack reloads
- Registry-dependent data
- Server shutdown
- World changes

Temporary search results may be cached within a single search job.

Do not persist confidential container contents.

Avoid uncontrolled memory growth.

==================================================
17. ERROR HANDLING
==================================================

Handle invalid commands, unavailable resources, missing recipes, unexpected recipe formats, cancelled searches, and server shutdown safely.

Expected errors must produce actionable feedback.

Examples:

"Unknown block ID: example:unknown_block"

"No matching block found within the scanned loaded area."

"No crafting recipe is available for minecraft:diamond."

"The requested radius exceeds the server limit."

"Your previous search was cancelled."

Do not dump stack traces into player chat.

Unexpected errors should be logged with context.

Never silently convert a search failure into a successful zero-result response.

==================================================
18. TESTING
==================================================

Write actual automated tests.

Use a compatible current JUnit version, preferably JUnit 6 if it works with the chosen Gradle setup.

Use NeoForge GameTests where they provide meaningful integration coverage.

18.1 Unit tests

Test:
- Exact distance calculations
- Nearest-result selection
- Deterministic tie-breaking
- Search boundary checking
- Search cancellation
- Radius validation
- Recipe quantity calculation
- Recipe alternatives
- Shared ingredient accounting
- Intermediate output quantities
- Recursive recipe expansion
- Cyclic recipe detection
- Unsupported recipes
- Inventory subtraction
- Missing material calculation
- Source mappings
- Unknown item identifiers
- Configuration validation

18.2 Integration tests

Test:
- Command registration
- Correct use of actual registries
- Block searching
- Item entity searching
- Crafting table location
- Recipe lookup
- Dedicated server compatibility
- Search cancellation after player disconnect
- Chunk loading restrictions
- Permission restrictions

18.3 Test scenarios

Scenario A:
Player is close to two diamond ores.

Expected:
Return the mathematically closest ore.

Scenario B:
Target ore exists only in an unloaded chunk.

Expected:
Do not load the chunk.
Do not claim a match.

Scenario C:
Player wants a diamond pickaxe and has one diamond.

Expected:
Correctly determine the remaining materials.

Scenario D:
Player already has all required ingredients.

Expected:
Report that no materials are missing.

Scenario E:
Target is a modded item with a standard crafting recipe.

Expected:
Resolve the recipe using the active RecipeManager.

Scenario F:
Modded item uses unsupported proprietary machine crafting.

Expected:
Return an explicit unsupported-recipe message.

Scenario G:
Two players search simultaneously.

Expected:
Independent searches and bounded shared tick work.

Scenario H:
Player disconnects during a long search.

Expected:
Search cancellation and state cleanup.

Scenario I:
Player has no operator permissions.

Expected:
No unauthorized hidden-resource search.

Scenario J:
Search scans only a subset of the configured area because some chunks are unloaded.

Expected:
Report that the result is bounded by loaded chunk availability.

==================================================
19. PERFORMANCE ACCEPTANCE
==================================================

Optimize for a real modded NeoForge dedicated server.

The server may contain many other mods.

Avoid introducing measurable lag during ordinary use.

Requirements:
- Incremental scanning
- Shared per-tick budget
- Bounded memory
- No unintended chunk generation
- No synchronous full-radius scan
- No unrestricted recursive recipe evaluation
- No arbitrary background-thread world access

Track:
- Blocks checked
- Sections checked
- Chunks skipped
- Duration
- Search completion state

Use debug logging when explicitly enabled.

Do not spam production logs.

==================================================
20. GRADLE AND BUILD
==================================================

Create a reproducible Gradle project using the official NeoForge 26.3 MDK as the foundation.

Include:
- settings.gradle.kts or validated Groovy equivalent
- build.gradle.kts or validated Groovy equivalent
- gradle.properties
- Gradle wrapper
- Required NeoForge metadata
- KotlinLangForge integration
- Correct resources
- Java 25 toolchain
- Test configuration

Prefer Gradle Kotlin DSL if compatible with the verified MDK setup.

For KotlinLangForge, follow its current developer documentation.

Do not invent a KotlinLangForge Maven coordinate or mod-loader declaration.

Verify:
- Mod metadata syntax
- Kotlin compiler compatibility
- NeoForge plugin compatibility
- Language adapter version
- Runtime dependencies
- Dedicated server dependency resolution

Avoid unnecessary external libraries.

==================================================
21. DOCUMENTATION
==================================================

Generate:

README.md
LICENSE
CHANGELOG.md
CLAUDE.md
AGENTS.md

README must include:

1. Mod overview
2. Minecraft and NeoForge requirements
3. KotlinLangForge requirement
4. Installation on dedicated server
5. Installation in single-player
6. Client-side installation expectations
7. Command reference
8. Examples
9. Configuration
10. Permissions
11. Supported recipe types
12. Known limitations
13. Performance behavior
14. Troubleshooting
15. Build instructions

CLAUDE.md and AGENTS.md must be aligned.

They should document:
- Project architecture
- Coding conventions
- Dependency policy
- Commands to compile and test
- Minecraft compatibility requirements
- Thread safety rules
- Search engine invariants
- Expected workflow for future agent changes

Avoid duplicating large sections unnecessarily.

==================================================
22. GITHUB ACTIONS
==================================================

Create a GitHub Actions workflow.

Trigger:
- Push to main
- Pull requests
- Manual workflow dispatch

Tasks:
1. Check out repository.
2. Set up Java 25.
3. Configure Gradle.
4. Validate dependencies.
5. Compile Kotlin sources.
6. Run unit tests.
7. Run appropriate integration tests when supported.
8. Build the NeoForge JAR.
9. Upload the artifact.

Use supported stable versions of GitHub Actions.

Do not introduce automated publishing or credentials unless necessary.

Do not require repository secrets for ordinary CI builds.

Include Gradle caching where appropriate.

==================================================
23. IMPLEMENTATION WORKFLOW
==================================================

Implement in phases.

PHASE 1 — Foundation
- Inspect repository
- Verify dependencies
- Create the NeoForge project
- Configure Kotlin
- Register the mod
- Compile the basic project

PHASE 2 — Command system
- Register commands
- Implement argument validation
- Add registry suggestions
- Implement permissions
- Compile and test

PHASE 3 — Resource search
- Implement bounded search jobs
- Implement incremental server-tick scheduling
- Implement loaded chunk scanning
- Implement nearest block matching
- Implement dropped item matching
- Implement cancellation
- Test nearest-result correctness

PHASE 4 — Crafting planner
- Integrate RecipeManager
- Resolve shaped/shapeless recipes
- Calculate quantities
- Analyze player inventory
- Resolve intermediate ingredients
- Test crafting calculations

PHASE 5 — World source resolution
- Implement data-driven source mappings
- Connect crafting requirements to source searches
- Implement crafting station searches
- Add modded-item fallback handling
- Test results

PHASE 6 — Quality
- Add configuration
- Add translations
- Add documentation
- Add unit tests
- Add integration tests
- Add GitHub Actions

PHASE 7 — Validation
- Run compilation
- Run automated tests
- Build JAR
- Test dedicated server startup if possible
- Review permissions
- Review performance
- Fix discovered issues

Do not continue adding optional functionality while the current phase fails compilation.

Prefer one fully functional feature over five unfinished features.

==================================================
24. NON-GOALS
==================================================

For the first stable release, DO NOT implement:

- Automatic resource mining
- Automatic item crafting
- Automatic item collection
- Automatic movement
- Player teleportation
- World generation
- Unrestricted searches of other dimensions
- Client-only X-ray rendering
- Mandatory GUI screens
- Complex custom networking
- Full support for every proprietary mod machine
- A permanent world-wide ore database
- Bypasses of server permissions

This project is a read-only resource locator and crafting assistant.

==================================================
25. DEFINITION OF DONE
==================================================

The task is complete only when:

1. A valid Minecraft 26.3 NeoForge project exists.
2. Java 25 is configured.
3. Kotlin compiles successfully.
4. The language adapter is configured correctly.
5. All required commands are registered.
6. Block searches return correct nearest positions.
7. Dropped item searches work.
8. Crafting analysis works for supported recipes.
9. Player inventory quantities are handled correctly.
10. Missing ingredients can be mapped to known sources.
11. Loaded chunks are never unintentionally generated.
12. Searches execute incrementally without blocking the server.
13. Permissions are enforced.
14. Cancellation works.
15. Automated tests exist and pass.
16. The project builds successfully.
17. The resulting JAR can be installed.
18. README and agent instructions are complete.
19. GitHub Actions is configured.
20. Known limitations are documented.

If environmental restrictions prevent a test, state exactly which test was not executed and why.

Never claim a successful compilation without running it.

==================================================
26. FINAL DELIVERABLE
==================================================

When finished, provide:

A. Architecture summary

B. Actual versions used:
- Minecraft
- NeoForge
- Kotlin
- KotlinLangForge
- Gradle
- ModDevGradle
- Java

C. Directory structure

D. Implemented command list

E. Tests executed and their results

F. Compilation result

G. Final JAR location

H. Installation instructions

I. Known limitations

J. Suggested next improvements

Do not finish with an implementation plan alone.

Actually modify the repository, compile the project, execute the tests, and report the real outcome.

START NOW.
