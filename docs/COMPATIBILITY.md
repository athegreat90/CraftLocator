# CraftLocator 0.2.0 — verified compatibility (2026-10-27)

CraftLocator supports every currently released Minecraft version from 26.1.2
through 26.3: **26.1.2, 26.2 and 26.3**. Each target has its own compiled JAR,
exact Minecraft dependency metadata and tested NeoForge pin. Version 0.2.0 is a
minor feature release for the expanded support; 0.1.0 supported only 26.3.

## Actual toolchain and targets

| Minecraft | NeoForge | FML | Gameplay config | Local checks |
| --- | --- | --- | --- | --- |
| 26.1.2 | 26.1.2.124 | 11.0.15 | SERVER | 34 JUnit + 18 GameTests passed |
| 26.2 | 26.2.0.93 | 11.0.16 | SERVER | 34 JUnit + 18 GameTests passed |
| 26.3 | 26.3.0.99 | 12.0.12 | SYNCED | 34 JUnit + 18 GameTests passed |

All builds use Java 25 (cloud Oracle JDK 25.0.5.0.3), Kotlin 2.4.21,
KotlinLangForge 2.16.0-k2.4.21-3.1+neoforge, ModDevGradle 2.0.148 and JUnit 6.0.3.
Minecraft's official version manifests confirm Java 25 for all three targets.
The NeoForge Maven catalog was checked for the latest release of each target.
KotlinLangForge's exact artifact declares all three game versions; its installed
Maven JAR matched the official Modrinth primary file's SHA-512.

**Gradle 9.8.2** was the latest stable release from
`https://services.gradle.org/versions/current` when checked. The official wrapper
was regenerated under 9.8.2 and verified:

- Distribution SHA-256:
  `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`.
- Wrapper JAR SHA-256:
  `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`.

The cloud tool installer was updated and rerun successfully. TLS and checksum
verification remain enabled. The Kotlin version reported by Gradle's own
`--version` output is its embedded DSL runtime, not the selected mod compiler.

## API compatibility verification

Official Minecraft client classpaths were downloaded and checked against their
manifest hashes, then relevant API signatures were inspected with javap. Actual
NeoForge sources/classpaths and loader enums were also inspected. Compile/runtime
checks use the patched NeoForge development artifacts, not only the client JARs.

The shared APIs used by CraftLocator are available across the requested range:
Identifier/IdentifierArgument, PermissionSet, ResourceHandler<ItemResource>,
loaded-only ServerChunkCache.getChunkNow, shaped/shapeless recipe assembly,
ingredient predicates and current RecipeManager lookups. Classes outside this
used API subset have changed between releases; each JAR is compiled against its
own game/loader, not declared universally compatible by a widened range.

FML 11 exposes SERVER and FML 12 exposes SYNCED. Minimal compile-time adapters
under `src/compat/{legacy,modern}` select the verified enum; shared mod logic
remains in main. No reflective enum/field fallback is used. A fresh configuration
of each target selects only the matching adapter. The GameTest fixture's structure
DataVersion was set to the oldest supported game's official value (4790), and
all three games successfully loaded it through their data-fixing pipeline.

## Checks actually completed

For **each** supported target:

```bash
./gradlew -Pminecraft_version=<target> test runGameTestServer build --no-daemon --console=plain
```

- Compilation, metadata expansion, resources, assemble, check and build passed.
- **34 JUnit tests passed, zero failed/errors/skipped**, per game target.
- **18 required GameTests passed**, per target: 17 CraftLocator tests and one
  vanilla test. These exercise actual registries, command execution, recipes,
  inventories/components, nearby sources/stations, loaded-chunk restrictions,
  double-chest authorization, permission revocation, cancellation, death,
  dimension changes and shared-budget fairness.
- All three JARs passed ZIP integrity checks and metadata inspection: exact
  Minecraft version, matching minimum NeoForge version and mod version 0.2.0.
- Config adapters and resources are present in the intended artifacts; no shaded
  Kotlin runtime is required because KotlinLangForge supplies it externally.

This is **102 JUnit executions and 54 GameTest executions** across the local
matrix. Reports are isolated in `build/mc<target>/reports/tests/test/` and
`runs/<target>/gametest/logs/`.

The **17 Python automation tests** passed. They cover central target/pin
validation, missing/duplicate/wrongly classified JAR rejection, all three assets
and checksums, exact commit targeting, release notes, retrying drafts, preserving
published releases, and refusing publication after failed uploads/API failures.
Workflow syntax passed actionlint 1.7.12; shell scripts passed Bash syntax checks.

The dedicated Minecraft **26.1.2** development server reached `Done`, returned
26.1.2 from a real local Minecraft status-protocol request, and executed console
help. The 26.3 dedicated startup/status/help had already been exercised in 0.1.0;
its 0.2.0 configuration adapter and server functionality passed the new GameTests.
GUI clients, Windows execution and real authenticated player login were not run.
Development authentication policy and cloud auth-service access are unchanged.

## Local artifacts

| JAR | Bytes | SHA-256 |
| --- | --- | --- |
| `craftlocator-0.2.0-mc26.1.2.jar` | 145832 | `cb3d0944f7854bc0db74ecf7d43509683f72faa846265040bf7a4523fd2e89a4` |
| `craftlocator-0.2.0-mc26.2.jar` | 145832 | `61e5fcb66d06397badcc7300fac4b531555a5453cb5f44ada8169d399f743444` |
| `craftlocator-0.2.0-mc26.3.jar` | 145850 | `3b7fc6630a434eb84f0001bda6d10949ccbc897e621f761083b8a688716ca76a` |

Each is under `build/mc<target>/libs/` with a companion `.sha256` file. Hosted
release checksums are generated from the exact CI artifacts. Install only the
JAR for your instance's game version plus the external KotlinLangForge JAR.

## CI and publication

The supported list and loader pins in gradle.properties drive the GitHub matrix;
there is no second hardcoded version list in the workflow. Release publication
requires every matrix build to succeed and exactly one classified JAR per target.
All three JARs and all three checksum files are uploaded before publishing the
draft. Pull requests/manual runs do not publish. The existing main-commit tag
policy is retained; release titles and asset names carry version 0.2.0.

The previous hosted 0.1.0 build and release workflow passed, including
[the initial CI run](https://github.com/athegreat90/CraftLocator/actions/runs/40563913455).
The 0.2.0 automation commit exercises the new hosted matrix and release job.
Environment snapshots are separate from GitHub pushes and Releases.

## Continuing limitations

Special/custom processing recipes and crafting-remainder simulation remain
unsupported by the planner. Public output displays identify unsupported recipe
types where possible. Potential ingot source rules validate an active standard
cooking recipe but do not count fuel or claim an existing ingot. The planner is
a bounded heuristic and world scans are incremental observations, not atomic
snapshots. Large production modpacks and arbitrary third-party capability
providers have not been benchmarked. These limits are unchanged from 0.1.0.
