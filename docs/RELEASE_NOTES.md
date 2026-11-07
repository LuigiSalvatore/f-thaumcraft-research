# v1.3.1-beta.1

Changes since the published v1.3.0 release:

- Replace uniform-cost resource-state expansion with weighted A* (`g + 2h`). A board-derived remaining-placement estimate directs the search toward the target. Return the first complete plan rather than spending the remaining budget comparing every root order. This favors faster answers over global minimum ink; equal-length choices favor abundant aspects.
- Reuse the solve's sorted aspect registry when fingerprinting resource ledgers, avoiding a full registry sort for every visited state. Keep separate player, bonus, and crafted pools and complete-only application.
- Add diagnostic traces under the instance's `logs/thaumcraftresearchsolver/` directory, enabled by default. Each trace records puzzle coordinates and roots, aspect recipes and counts, abundant compatible bridge pairs, attempted root orders, path timings, queue sizes, generated/visited/duplicate/resource-rejected states, timeout stage, combinations, and final placements. Retain the newest 25 traces; disable with `solverDiagnosticLogging=false` in the behavior config. Traces do not record player names, server addresses, or chat.
- Spread Solve All cursor transfers across ticks and check the expected slots and empty cursor before reporting transfer success. Stop and attempt recovery after a failed exchange.
- Add a 61-cell, five-root regression and an abundance-choice regression, bringing deterministic solver diagnostics to 10 checks.
- Retain the inventory return slot throughout Solve All so solved notes can be swapped back. Pin the Gradle project name so builds work regardless of checkout directory name.

The draggable palette, asynchronous solving, synthesis provenance, ink preflight, and acknowledged execution already shipped in v1.3.0; they are retained here.

Build verification uses Java 25, the pinned Gradle wrapper, `check assemble`, and the deterministic diagnostics. The reobfuscated artifact targets Java 8 bytecode. Standard Gradle `test` has no test sources; the solver diagnostics run separately. Formatting and Checkstyle are disabled by the existing project configuration.

Release checks on 2026-11-07: a fresh checkout completed `check assemble solverDiagnostics` with all preparation tasks enabled and all 10 diagnostics passing. The existing access-transformer warnings about Thaumcraft GUI fields during the Minecraft source transformation phase remain visible; compilation and reobfuscation succeeded. These are local checks, not CI or in-game results.

Minecraft/GTNH runtime acceptance, both photographed issue notes, cursor-transfer behavior under multiplayer latency, and end-to-end trace creation in a running game remain unverified. This is a beta prerelease. Related aspect pairs such as Ordo/Instrumentum can be repeated by the search and are ranked in diagnostics; no separate hardcoded hub-routing algorithm is included.
