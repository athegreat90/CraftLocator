# CraftLocator 0.2.0

A read-only Minecraft resource locator and crafting assistant for NeoForge.
Authoritative world searches and recipe analysis run on the logical server.
There is no HUD, automatic mining, crafting, collection, movement or teleportation.

## Requirements

| Component | Pinned version |
| --- | --- |
| Minecraft Java Edition | 26.1.2, 26.2, 26.3 |
| NeoForge | Pinned separately for each game version (below) |
| Java toolchain | 25 (cloud validation: Oracle 25.0.5.0.3) |
| Kotlin | 2.4.21 |
| KotlinLangForge | 2.16.0-k2.4.21-3.1+neoforge |
| Gradle | 9.8.2 |
| ModDevGradle | 2.0.148 |
| JUnit Jupiter | 6.0.3 |

Dependency and plugin versions are centralized in `gradle.properties`; the
Gradle distribution and checksum are pinned in the wrapper configuration. The
foundation is the official NeoForge ModDevGradle MDK. KotlinLangForge is
required at runtime: [official download](https://modrinth.com/mod/kotlin-lang-forge).
Its `klf` language adapter is declared through `modLoader`/`loaderVersion`, not
through an additional required dependency on its FML library ID.

## Compatibility and versioning

CraftLocator **0.2.0** is a minor feature release: it adds Minecraft 26.1.2 and
26.2 support while retaining 26.3. Java 25 and KotlinLangForge are required for
all targets. Gradle **9.8.2** was the latest stable release when verified on
2026-10-27; its distribution and wrapper JAR were checked against official hashes.

| Minecraft | NeoForge build tested | Release JAR |
| --- | --- | --- |
| 26.1.2 | 26.1.2.124 | `craftlocator-0.2.0-mc26.1.2.jar` |
| 26.2 | 26.2.0.93 | `craftlocator-0.2.0-mc26.2.jar` |
| 26.3 | 26.3.0.99 | `craftlocator-0.2.0-mc26.3.jar` |

Each JAR is compiled against its target game's actual APIs and declares that
exact Minecraft version in its metadata. Install the JAR matching your instance;
the three files are alternatives for different game versions. The default build
is 26.3; `-Pminecraft_version=26.1.2` or `26.2` selects another supported target.
All Minecraft/NeoForge pins and the supported list live in `gradle.properties`.

## Installation

For a dedicated Linux or Windows server, install Java 25 and the NeoForge build
for your Minecraft version from the table above using the official installer.
Put that version's CraftLocator JAR and the matching KotlinLangForge NeoForge JAR
in the server's `mods` directory, then start NeoForge
using its generated launch script. Review Minecraft's EULA before running a server.
Use the server console to grant operator permissions where appropriate.

For single-player, install the same NeoForge version, CraftLocator and
KotlinLangForge in that instance's `mods` directory. The integrated server runs
all searches. Cheats/operator permission is required for world scanning by
default; recipe analysis is separate.

No client HUD or custom network payload is required. The server implementation
uses vanilla-compatible chat components and English fallbacks. Installing
CraftLocator plus KotlinLangForge on a matching NeoForge client enables its
Spanish resource translations when Spanish is selected. An actual graphical
client connection and Windows runtime were not exercised in the Linux cloud;
see the validation record for exact checks.

## Commands

Commands require a player source, except `help`.

| Command | Behavior |
| --- | --- |
| `/craftlocate find block <block_id> [radius]` | Nearest existing matching loaded block |
| `/craftlocate find item <item_id> [radius]` | Existing dropped items, plus explicitly authorized containers if enabled |
| `/craftlocate source <item_id> [radius]` | Existing items plus declared potential harvesting/processing sources |
| `/craftlocate craft <item_id> [quantity] [radius]` | Recipe, inventory allocation, recursive missing base materials, surplus and station search |
| `/craftlocate status` | Active target, state, progress, blocks/chunks examined, elapsed time and result count |
| `/craftlocate cancel` | Cancel and release the current search |
| `/craftlocate help` | Syntax, examples and loaded-world limitations |

Examples:

```text
/craftlocate find block minecraft:diamond_ore 64
/craftlocate find item minecraft:diamond
/craftlocate source minecraft:iron_ingot 64
/craftlocate craft minecraft:diamond_pickaxe 1 64
/craftlocate craft minecraft:chest 4
```

Namespaced registry completion includes modded items and blocks. Unqualified
identifiers use `minecraft:`. Quantity is 1–4096; default radius is 64 and the
absolute maximum is 128, subject to the server's configured lower limit.
A new search cancels that player's previous search. The default global capacity
is four active jobs; excess requests receive an explicit error.

Coordinates can be clicked to copy. Distances use block centres or observed item
positions, measured from the player's position at submission. Potential source
blocks are labelled separately from existing items. No result guarantees the
player has the necessary tool, that loot will drop, or that a recipe sequence
can be executed automatically. Missing areas are never silently searched by
loading or generating chunks.

## Configuration and permissions

The configuration is authoritative on the logical server:

- Minecraft **26.1.2/26.2** uses FML 11's `SERVER` type:
  `<world>/serverconfig/craftlocator-server.toml` (templates in `defaultconfigs`).
- Minecraft **26.3** uses FML 12's `SYNCED` type:
  `config/craftlocator-synced.toml`, with an optional override in
  `<world>/syncedconfig/craftlocator-synced.toml`.

A compile-time adapter selects the actual loader API for each target. Client-local
values do not authorize server commands. Stop the server before editing persistent
settings; respect the platform's config reload behavior.

| Setting | Default |
| --- | --- |
| `search.defaultRadius` / `maximumRadius` | 64 / 128 |
| `search.maxBlockChecksPerTick` | 2048 shared work units |
| `search.maxScanTimeMillisPerTick` | 2 ms soft global deadline |
| `search.maxConcurrentSearches` | 4 |
| `search.timeoutSeconds` | 120 |
| `features.enableBlockSearch` / `enableDroppedItemSearch` | true / true |
| `features.enableSourceSearch` / `enableCraftingPlanner` | true / true |
| `features.enableContainerSearch` | false |
| `features.enableCraftingStationSearch` | true |
| `security.requireOperatorForWorldSearch` | true |
| `security.requireOperatorForCrafting` | false |
| `security.authorizedContainers` | empty list |
| `performance.enableSectionPaletteOptimization` | true |
| `performance.enableProgressNotifications` | true |

World scanning requires Minecraft's `COMMANDS_GAMEMASTER` permission (operator
level 2 or higher) by default. An administrator can explicitly allow all players
world scanning or independently restrict recipe analysis. Permissions are checked
again before coordinates are disclosed. Crafting analysis remains available
without world permission but emits no source or station coordinates.

Containers additionally require **all** of: the container feature enabled,
operator permission, and its exact location in the administrator's allowlist.
Example: `minecraft:overworld@120,65,300`. Add only containers whose owners have
explicitly authorized inspection and whose protection rules permit it. No generic
claim/lock integration is assumed; containers outside the allowlist are omitted.
Both halves of a double chest must be explicitly authorized and already loaded.
Other players' inventories are never searched. The container reader uses the
verified `ResourceHandler<ItemResource>` capability without transactions or
mutation. There is no switch to disable loaded-chunk-only safety or to authorize
non-operators to read containers.

## Recipes and source rules

Shaped and shapeless standard crafting recipes from the active RecipeManager are
supported, including modded outputs, tags, ingredient alternatives, component
predicates, output counts and recursive intermediates. Physical inventory is
shared across the entire plan; generated surplus is explicitly virtual. Recipes
fitting 2×2 do not require a table unless an intermediate recipe does.

Selection is a bounded deterministic greedy heuristic, not a global optimum.
It ranks immediate inventory coverage and missing quantities, then recipe ID,
allocating narrow ingredients before broad alternatives. It evaluates at most
32 recipes per item and expands at most 64 alternative items, depth 12 and 256
nodes. Other hard ceilings are documented in [architecture](docs/ARCHITECTURE.md).
A selected raw variant may not be the variant closest in the world.

Automatic smelting, blasting, smoking, smithing, stonecutting, custom machines,
dynamic/special recipes and crafting-remainder simulation are not supported by
the recursive planner. Recipes of unsupported types with exposed output displays
produce an explicit unsupported message. Recipes without an inspectable public
output cannot be attributed to an item. Ore-to-ingot rules only explain a
potential processing path; they do not claim a finished ingot or calculate fuel.

Datapacks can add/override rules at
`data/<namespace>/craftlocator/sources/<name>.json`:

```json
{
  "target_item": "minecraft:diamond",
  "source_blocks": ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"],
  "source_type": "harvestable_block",
  "conditions": "craftlocator.condition.diamond"
}
```

`conditions` is a translation key or a readable fallback string. Optional
`processing_input` and `processing_recipe_type` must be supplied together;
smelting/blasting paths are validated against active cooking recipes before the
rule is accepted. Invalid rules are logged and omitted individually. Source
rules and recipe indexes refresh on successful global datapack synchronization;
active searches are cancelled. Unknown modded items still support direct dropped
item searches and standard recipe analysis, but no harvesting relationship is
invented without a declared rule.

## Performance and limitations

Searches process nearby sections first, use squared Euclidean distance, apply
safe palette/distance pruning, and share one round-robin tick budget. Metadata,
entity processing and inventory slots also consume work units. Minecraft calls
cannot be preempted, so the millisecond deadline is soft. Pathological sections
with more than 4096 item entities or inventories with more than 4096 slots fail
explicitly rather than returning a truncated nearest result. The default timeout
cancels long jobs safely. No permanent ore/container coordinate cache exists.

Results are observations gathered during an incremental scan, not an atomic
snapshot: blocks can change and item entities can move. The nearest result is
relative to the loaded domain actually examined. Performance on large third-party
modpacks and crowded production servers has not been benchmarked.

## Build, tests and development

Use Java 25:

```bash
./gradlew compileKotlin test runGameTestServer build
```

Select a target explicitly, for example:

```bash
./gradlew -Pminecraft_version=26.1.2 compileKotlin test runGameTestServer build
```

Windows: use `gradlew.bat` with the same arguments. The output is
`build/mc<game-version>/libs/craftlocator-0.2.0-mc<game-version>.jar`.
KotlinLangForge remains an external runtime dependency. JUnit reports are in
`build/mc<game-version>/reports/tests/test/`; GameTest logs are in
`runs/<game-version>/gametest/logs/`. These directories isolate incremental
compiler data, dependencies and generated metadata between targets.

Cloud machines also provide a repeatable tool installer:

```bash
python3 scripts/setup-tools.py
source /workspace/.craftlocator-tools/activate.sh
./gradlew test runGameTestServer build
```

The helper requires Linux x86-64 and Python 3.12+. It keeps JDK/Gradle artifacts
outside the checkout, checks fixed SHA-256 digests, and preserves TLS verification
with the platform's supplied proxy and Java trust store. The pinned Oracle
archive is a development tool with its bundled license, not a redistributed mod
dependency. Its `latest` URL has a fixed expected digest: a future replacement
fails closed and requires an independently verified tool update.

`./scripts/run-dev-server.sh [minecraft-version]` starts a dedicated development
server in `runs/<minecraft-version>/server` (26.3 by default)
and supplies loopback-only defaults if no properties file exists.
`./gradlew runServer` runs the same Gradle launch task directly;
`./gradlew -Pminecraft_version=26.1.2 runClient` starts a graphical development
client for that target. Use `python3 scripts/server-status.py 26.1.2` to check an
older dedicated server's version. Each target uses its own test/development worlds;
existing worlds from the old `runs/server` location are left untouched. The cloud has no
GUI/display, so graphical use is optional. Development mode uses Minecraft's development authentication policy; real online
player authentication was not exercised. Review/accept Minecraft's EULA yourself
if required by the server. Test functions and the conditional test-only item
recipe are enabled exclusively in `runGameTestServer`, not normal gameplay.

The test suites include **34 JUnit tests** and **17 CraftLocator GameTests**.
The Minecraft GameTest runner may include its own test as well. Every supported
target runs both suites and builds its own JAR; exact results are recorded in
[the validation evidence](docs/COMPATIBILITY.md).

GitHub Actions compiles, resolves dependencies, runs JUnit and GameTests, builds
the JAR and uploads it with reports. The initial hosted build also
[passed on GitHub](https://github.com/athegreat90/CraftLocator/actions/runs/40563913455).

### Automatic releases from main

Every push to `main` publishes a GitHub Release **after** the build and tests
succeed. Pull requests and manual workflow runs validate the project without
publishing. Independent main pushes are not cancelled when newer code arrives.

Release tags are `main-<full-commit-SHA>` and titles include the mod version and
short commit ID. This gives each tested commit its own immutable release without
automatically changing `mod_version`. Release notes list commit subjects since
the nearest ancestor release tag, with a full comparison link; the first release
lists the available history. Notes are generated from Git history, so meaningful
commit messages become the change descriptions.

Download the JAR and its SHA-256 file directly from
[GitHub Releases](https://github.com/athegreat90/CraftLocator/releases).
CI derives its three-version matrix from `gradle.properties`. A release waits
for **all** target builds and tests to pass and consumes their exact JAR artifacts.
It attaches all three labelled JARs and their individual SHA-256 files. It is
created as a draft, uploaded, then published only after all six assets are available. Retrying resumes an interrupted draft or skips an already
published release, preserving published assets.

The build job has read-only repository access. Only the release job receives
`contents: write`, using GitHub's automatically provided token; no personal token
or additional repository secret is required. Repository/organization policies
must permit that workflow permission. The release helper's offline tests run with:

```bash
python3 -m unittest discover -s scripts/tests -v
```

## Troubleshooting

- Wrong Java/class version: confirm `java -version` is 25 and Gradle uses that JDK.
- Missing Kotlin loader: install the exact NeoForge KotlinLangForge release.
- HTTP 403 downloading dependencies: check the environment's network policy;
  proxy settings in Gradle are separate from shell `HTTPS_PROXY` variables.
- Certificate errors: repair the trusted CA store; never disable TLS checking.
- No resource found: inspect skipped unloaded sections, dimension, radius,
  permissions and mapping availability. The search does not explore other chunks.
- No coordinates after `craft`: recipe-analysis access does not grant world access.
- Busy/timeout/budget errors: reduce the request or adjust the bounded server
  settings. See server logs for unexpected failures; chat does not expose traces.
- Unsupported recipe: use the game's manual recipe/machine workflow; no guessed
  processing plan is returned.

## Layout and maintenance

```text
src/main/kotlin/io/github/athegreat90/craftlocator/
  CraftLocatorMod.kt  Commands.kt  Config.kt  Feedback.kt
  Recipes.kt         Sources.kt   GameTests.kt
  core/Geometry.kt   core/Planning.kt
  search/Search.kt   search/SearchManager.kt
src/compat/{legacy,modern}/kotlin/  # FML SERVER / SYNCED configuration adapters
src/main/resources/{META-INF,assets/craftlocator/lang,data/craftlocator}/
src/test/kotlin/io/github/athegreat90/craftlocator/
scripts/setup-tools.py
.github/workflows/build.yml
```

See [architecture and safety invariants](docs/ARCHITECTURE.md),
[validation evidence](docs/COMPATIBILITY.md), [agent instructions](AGENTS.md),
[original requirements](docs/REQUIREMENTS.md) and [changelog](CHANGELOG.md).
Future improvements: measured modpack performance, authorized protection-system
integrations and explicit processing adapters with fuel/remainder accounting.

License: MIT for CraftLocator. Minecraft, NeoForge, KotlinLangForge and build
tools retain their own licenses.
