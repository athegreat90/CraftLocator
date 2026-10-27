# CraftLocator agent instructions

Read README.md and docs/ARCHITECTURE.md before changing behavior. The original
request is preserved in docs/REQUIREMENTS.md; validation evidence is recorded in
docs/COMPATIBILITY.md. CLAUDE.md points here so there is one instruction source.

Use the existing checkout. Cloud tasks are already isolated; do not create a
Git worktree unless the user explicitly requests one. Preserve user changes.

## Toolchain and checks

- Target Minecraft 26.3, NeoForge 26.3.0.99, Java 25 and Kotlin 2.4.21.
- Pinned dependency/plugin versions live in gradle.properties. Gradle 9.2.1 is
  pinned, with SHA-256 verification, in gradle/wrapper/gradle-wrapper.properties.
- Use the official ModDevGradle configuration and KotlinLangForge's `klf`
  language adapter. It is an FML library; do not invent a required mod dependency
  on its library ID. Do not shade Kotlin into the mod.
- On this cloud machine: `source /workspace/.craftlocator-tools/activate.sh`.
- Compile/test: `./gradlew compileKotlin test`; full verification:
  `./gradlew test runGameTestServer build`. Windows: `gradlew.bat` with Java 25.
- The GameTest run sets `craftlocator.tests=true`. Test registries and fixtures
  must remain disabled during normal gameplay. Never accept a zero-test run as
  integration validation; inspect the completed test counts and process status.
- Preserve TLS, signatures and checksums. Never update a checksum merely to make
  a failed download pass. Never add credentials to source, instructions or logs.

## Implementation invariants

Use immutable data for planning and search candidates. World access belongs on
Minecraft's server thread. Never use coroutines or executor threads for world
reads. Verify actual APIs against the development sources when updating versions;
26.3 uses Identifier, PermissionSet, ResourceHandler<ItemResource>, and a new
recipe representation. Do not transplant older tutorial signatures.

Searches must never create chunk tickets, load/generate chunks, move players,
mutate inventory or modify blocks. The production scanner's sole chunk lookup
is `ServerChunkCache.getChunkNow`; recheck it after tick boundaries. Maintain
3D sphere/build-height/border checks, deterministic distance ties, conservative
section lower bounds and palette pruning. A section can only be pruned when
no target in it can improve. The work/time budget is global and round-robin.

Validate world permission at command submission and before disclosing results.
Keep container searching disabled by default. Enabling it never authorizes a
container: operators also need its exact position in the administrator's
allowlist. Do not add claim/lock bypasses or player-inventory enumeration.

Keep recursive planning bounded, share one inventory ledger across branches,
check actual ingredient predicates and components, preserve output batch sizes,
and separate planned surplus from existing items. Processing and custom recipe
outputs must not be guessed. Never describe the heuristic as globally optimal.

Clear jobs/indexes on reload and shutdown. Jobs must not retain levels or players.
Do not persist ore coordinates or container contents. Keep unexpected failures
explicit; they must not become successful searches with zero results.

Add English and Spanish translations with identical key sets and an English
fallback for clients without CraftLocator. Add focused unit/GameTests for
behavior changes, run affected checks, then update the relevant documentation.
