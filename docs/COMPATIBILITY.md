# Verified compatibility and validation — 2026-10-26

The cloud now supports compilation, automated tests, JAR packaging and dedicated
server development. The initial repository was empty; the implementation and
supporting files were created locally. Nothing has been pushed or released.

## Actual versions

| Component | Verified version |
| --- | --- |
| Minecraft Java | 26.3 (protocol 777) |
| NeoForge | 26.3.0.99 |
| Kotlin compiler | 2.4.21 |
| KotlinLangForge | 2.16.0-k2.4.21-3.1+neoforge |
| Gradle | 9.2.1 |
| ModDevGradle | 2.0.148 |
| Java | Oracle JDK 25.0.5.0.3, classfile target Java 25 |
| JUnit Jupiter | 6.0.3 |

NeoForge 26.3.0.99 was the latest 26.3 release in the Maven catalog when checked.
Mojang's manifest confirmed Minecraft 26.3. Modrinth and the KotlinLangForge Maven
catalog confirmed the exact adapter artifact and its Minecraft 26.3 support.
The installed KotlinLangForge Maven JAR's SHA-512 matched the primary file hash
published by Modrinth for that version. Gradle's embedded Kotlin 2.2.20 is its DSL
runtime and is not the mod compiler.

Foundation: [official MDK](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/tree/543f871769bba1b5fcd138359e80f74739442167)
at commit `543f871769bba1b5fcd138359e80f74739442167`. Its default NeoForge 26.3.0.98
was first compiled successfully as a reference, then the project was pinned to
verified release 26.3.0.99. Its supported Groovy build configuration was retained
and adapted for Kotlin, tests and centralized version properties.

## APIs verified against the actual 26.3 development classpath

- Commands use `RegisterCommandsEvent`, `IdentifierArgument` and live registries.
  Brigadier string words are unsuitable for namespaced IDs because `:` terminates
  a word; command integration tests exercise full namespaced commands.
- Permission checks use `PermissionSet.hasPermission(COMMANDS_GAMEMASTER)`.
- Server configuration uses `ModConfig.Type.SYNCED`; the legacy `SERVER` enum no
  longer exists in this loader.
- Tick work uses `ServerTickEvent.Post` on the logical server thread.
- Inventory capabilities are `Capabilities.Item.BLOCK` with
  `ResourceHandler<ItemResource>`, `size()`, `getResource()` and
  `getAmountAsLong()`. No extraction/insertion transaction is opened.
- `ServerChunkCache.getChunkNow` was inspected in actual source/bytecode. It only
  checks the cache and existing visible full chunk holders. It does not request
  or generate chunks. The scanner never uses a loading lookup.
- RecipeManager exposes `getRecipes()`; shaped recipes expose optional
  ingredients and dimensions. Shapeless ingredients are read from placement
  info. Both standard implementations produce their static result using
  `assemble(CraftingInput.EMPTY)`, preserving result components/counts.
- Actual ingredient predicates test inventory stack components. World item
  matching for missing ingredients reuses those predicates. Unsupported output
  identification uses the recipe's public display and SlotDisplayContext.
- KotlinLangForge is an FML `LIBRARY` providing the `klf` language loader. Declaring
  an additional required mod dependency on library ID `klf` causes a misleading
  missing-mod startup error. The working metadata follows its documented language
  loader registration; runtime dependency resolution and startup were exercised.

## Tests and commands actually completed

`./gradlew test runGameTestServer build --no-daemon --console=plain` completed
successfully after the final code changes.

- **34 JUnit tests: 34 passed, zero failed, zero skipped.** Coverage includes exact
  distances, conservative section bounds, negative coordinates, radius limits,
  deterministic nearest ties, quantity rounding, shared stock, inventory/tag and
  component matching, alternatives, recursive batches, surplus, stocked
  compression cycles, cyclic alternatives, recursion/node limits and bounded
  feedback for large ingredient tags.
- **18 required GameTests passed:** 17 CraftLocator tests and one vanilla test.
  CraftLocator tests cover nearest ore, unloaded chunk omission without loading,
  dropped items versus potential ore sources, crafting tables, unauthorized and
  authorized containers, both-half authorization for double chests, active
  recipes/player inventory, command registration/full execution/permissions,
  cancellation and disconnect, shared global budget/fairness and permission
  revocation, reloadable source maps, an actual test-only item in a non-vanilla
  namespace with a standard recipe, item components, dimension changes and death.
- Kotlin compilation, metadata expansion, resource packaging, `assemble`, `check`
  and `build` succeeded. English/Spanish key sets match (49 keys each).
- Java and Gradle archives passed their pinned SHA-256 checks; the tool installer
  was repeated successfully. TLS verification was retained. Gradle uses the
  supplied proxy and system CA trust store; supported game JVM properties are
  forwarded separately.
- The dedicated development server reached `Done` with Minecraft 26.3,
  NeoForge 26.3.0.99, CraftLocator 0.1.0 and the Kotlin adapter loaded. A local
  Minecraft status-protocol request returned version 26.3/protocol 777.
  `/craftlocate help` executed from the real server console. The console `stop`
  command shut it down cleanly and saved its world. No graphical preview was used.

The GameTest-only registry item and conditional fixture recipe are disabled in
normal gameplay. The dedicated-server launch verified this normal loading path.
Reports: `build/reports/tests/test/` and `runs/gametest/logs/`; dedicated runtime
logs: `runs/server/logs/` (all ignored generated outputs).

## Artifact

`build/libs/craftlocator-0.1.0.jar` (144,777 bytes at final packaging).
SHA-256: `1488c6b4b0d7aabf210b970a9d5e8d86c60021d79d45d39df4bf72c50080c31c`.
A companion `.jar.sha256` file is present. The JAR contains the compiled mod and
resources, expanded metadata and no shaded Kotlin runtime. Install the separate
matching KotlinLangForge JAR. Archive timestamps/order are explicitly reproducible.

## Limits of the evidence

- Dedicated startup was exercised through the real NeoForge development launcher,
  not a separate production installer installation. Real authenticated player
  login, a graphical single-player/client session and Windows execution were not
  run. Minecraft development mode uses its development authentication policy;
  auth-service discovery also encountered connection refusals through the cloud
  route. This did not prevent server startup, status requests or the tests.
- Processing types and special/custom crafting types are not expanded. Known
  displayed outputs report unsupported types; an opaque proprietary recipe with
  no public output display cannot be attributed to its output item. Potential
  ingot source rules validate an active standard cooking path but do not count
  fuel or perform smelting. Crafting remainders are not simulated.
- Search/time budgets and caches are bounded, but large production modpacks and
  pathological third-party capability providers have not been benchmarked. The
  time deadline is soft because platform calls cannot be preempted.
- No globally optimal crafting plan or atomic world snapshot is claimed.
- The GitHub Actions workflow was created and uses verified stable release tags;
  no hosted workflow run, push, release or environment publication was performed
  by the agent. Snapshot publication remains the user's environment-settings step.

The former HTTP 403 dependency blocker was resolved by the user's environment
configuration change; it is no longer an installation prerequisite.
