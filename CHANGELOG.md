# Changelog

## 0.2.0 — Unreleased

- Add Minecraft 26.1.2 and 26.2 builds while retaining 26.3; ship exact-target JARs.
- Pin NeoForge 26.1.2.124, 26.2.0.93 and 26.3.0.99 and retain Java 25/Kotlin 2.4.21.
- Upgrade the verified Gradle wrapper and cloud tool installer to stable 9.8.2.
- Adapt authoritative configuration registration to FML 11 SERVER and FML 12 SYNCED.
- Isolate target build caches and development/test worlds.
- Derive the CI matrix from centralized pins and release all three tested JARs
  with individual checksums only after every target passes.

## 0.1.0 — 2026-10-27

- Add server-side `/craftlocate` block, dropped-item, source and crafting commands.
- Add loaded-chunk-only, incremental 3D searches with global round-robin budgets,
  nearest-result selection, status, cancellation and lifecycle cleanup.
- Add shaped/shapeless recipe planning, shared inventory accounting, alternatives,
  component predicates, recursive intermediates and planned surplus.
- Add data-driven source mappings with explicit harvesting/processing conditions.
- Add operator world-search policy and exact allowlists for optional containers
  using NeoForge's ResourceHandler<ItemResource> capability.
- Add server SYNCED configuration, English/Spanish text and copyable coordinates.
- Add JUnit and server GameTests, reproducible Gradle wrapper and GitHub Actions.
- Publish a commit-tagged GitHub Release after each successful main push, with
  changes since the preceding release, the tested JAR and its SHA-256 checksum.
  Retry interrupted drafts safely and preserve already published releases.
