# Architecture and guarantees

`CraftLocatorMod` owns the services for one mod instance and connects verified
NeoForge command, tick, startup, datapack-sync and shutdown events. Standard game
registries supply argument completion and validation; console-only planning and
searches are rejected by Minecraft's player-source validation.

- `Commands.kt` validates IDs, quantities, radii, feature flags and server policy.
- `search/Search.kt` stores immutable target/candidate data and a resumable job.
  Regions are sorted by the squared distance lower bound to their 16-cube AABB.
  Matches use block centres or item entity positions and deterministic ties.
- `search/SearchManager.kt` maintains at most one job per player. A new request
  cancels the old job. A round-robin deque shares work units and a soft time
  deadline across all active players. Palette checks, metadata, entity steps and
  inventory slots consume work units as well as block checks. Atomic platform
  calls cannot be preempted; 2 ms is a soft deadline, not a hard real-time promise.
- `Recipes.kt` indexes the server's current RecipeManager. Supported shaped and
  shapeless outputs come from their verified `assemble(CraftingInput.EMPTY)`
  implementations. Other recipes are identified through their public displays;
  they are reported as unsupported instead of executing proprietary processing.
- `core/Planning.kt` is independent of Minecraft. It uses one stock ledger for
  all branches and distinguishes physical allocations, virtual intermediate
  batches and surplus. Scarce exact choices are allocated before broad tags.
  Candidates are ranked by immediate inventory deficit, then recipe ID. It is
  a bounded greedy strategy: 32 recipes/item, 64 alternative expansion items,
  depth 12, 256 expanded nodes, 50,000 lookup evaluations and a 4,096-key local
  lookup cache. It rejects reversible compression cycles before expanding them;
  stocked intermediate ingredients can still break such cycles. Limits fail
  explicitly or leave a material unresolved, never invent an obtainable output.
- `Sources.kt` loads `data/<namespace>/craftlocator/sources/*.json`, including
  datapack overrides, at startup and successful datapack sync. Malformed rules
  are logged individually. The schema validates IDs, types, block lists and
  bounded text. More than 4,096 resources are reported and truncated in sorted
  resource-ID order. Potential harvesting is never reported as a physical item.
- `Feedback.kt` uses translatable components with bundled English fallback.
  Installing the mod on a client enables Spanish translations when selected.
- `GameTests.kt` is gated behind an explicit JVM property. Its test-only item,
  functions and conditional recipe are absent from ordinary gameplay registries.

## World safety and privacy

The production scanner uses only `ServerChunkCache.getChunkNow`. Its actual
26.3 source/bytecode was inspected: it checks the main thread, cache and visible
chunk holders, returning null when no full chunk is already available. It does
not request a future or add a ticket. No production world mutation exists.

The job rechecks the chunk for every work unit, respects the sphere, dimension,
build height and world border, and skips newly unloaded sections. Section palette
pruning is optional; correctness does not depend on it. A section is distance
pruned only when every target already has a strictly closer candidate. This
preserves ties and never accepts the first ore merely because it was encountered
first. An all-air palette is not assumed irrelevant when searching for air.

Dropped items are collected per section into at most 4,097 references; over
4,096 causes an explicit failed job. References become immutable observations
before the next tick. An explicitly authorized container is read one slot per
work unit, with at most 4,096 slots. Capability availability alone is never
permission. Both halves of a double chest must be explicitly authorized and
already loaded before a combined capability is queried. Direct ingredient
searches retain actual ingredient/component tests.

Permissions are rechecked before coordinates are emitted. Disconnect, death,
dimension change, permission revocation, timeout, cancellation, global datapack
sync and server stop release active jobs. No completed-job history, ore database
or private-inventory cache is retained. Recipe indexes are replaced on reload
and cleared on shutdown; per-plan caches are discarded with that plan.

The world can change during an incremental scan. Results are nearest observations
in the domain examined, not an atomic snapshot of a moving world. Item positions
and blocks can change afterward. Unloaded areas are always explicitly excluded.
