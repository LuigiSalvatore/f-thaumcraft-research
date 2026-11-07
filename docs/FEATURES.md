# Feature status

Implemented: client-side Solve/Preview/Solve All; asynchronous weighted A* returning the first complete plan; exact resource ledgers and synthesis provenance; ink preflight and tool swapping; acknowledged execution; cursor-based note transfers across ticks; draggable palette with vanilla/Research Tweaks profiles; rotating per-solve traces; `/tcrstest` diagnostics.

Verified locally: compilation, reobfuscated assembly, and 10 deterministic solver checks including open-board and abundance-choice regressions. See RELEASE_NOTES.md for release scope and limitations.

Unverified in game: the two reported screenshots, multiplayer transfer recovery, live rendering and controls, and log creation in a real launcher instance. Planned: richer hub-routing heuristics and benchmarks from captured live traces. No global optimality guarantee is claimed.
