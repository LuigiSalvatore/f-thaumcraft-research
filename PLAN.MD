Implementation status: the core plan is implemented. The resource-aware complete-only solver, aspect synthesis provenance, exact ink preflight/tool swapping, acknowledged transactional execution, asynchronous solving, confirmed Solve All flow, and draggable action palette are now wired into the mod.

The main conclusion: aspect combining is already partially implemented, but the solver’s resource model and search pruning make it unreliable. Ink “refilling” is actually tool swapping, and the current guard can knowingly begin a plan without enough ink to finish it.

## Current implementation

The flow is:

1. The GUI handler reads the research note, discovered aspects, table bonus aspects, and scribing tool.
2. [ResearchSolver.java](/D:/projects/ftr/src/main/java/com/luigi/thaumcraft/researchsolver/ResearchSolver.java:162) merges player and bonus aspects into one pool.
3. A Dijkstra-like search connects roots sequentially.
4. It tries every root ordering, with a nominal one-second timeout.
5. Missing compound aspects are recursively decomposed into their parents by `tryToConsume`.
6. The result contains placement actions and aspect-combination actions.
7. [GuiOverlayHandler.java](/D:/projects/ftr/src/main/java/com/luigi/thaumcraft/researchsolver/GuiOverlayHandler.java:319) sends all combination packets, followed immediately by all placement packets.
8. [ScribeToolHelper.java](/D:/projects/ftr/src/main/java/com/luigi/thaumcraft/researchsolver/ScribeToolHelper.java:41) looks for another charged `IScribeTools` item and swaps it into the table.

### Confirmed Thaumcraft 4.2.3.5 mechanics

From the bundled source:

- Two neighboring aspects are valid only when one is a direct component of the other. Merely sharing a component is not sufficient. The README currently describes this incorrectly.
- `Humanus` is a direct parent of both `Lucrum` and `Instrumentum`, so this chain is valid:

  `Lucrum — Humanus — Instrumentum`

- Each placement consumes exactly one ink durability and one aspect point.
- Combining consumes one point from each parent and creates one point of the compound aspect.
- Combining consumes no ink.
- Normal combination packets are not gated behind Research Expertise/Mastery. Research Tweaks’ batch and automatic UI conveniences are gated, however. Its documented implementation supports drag/right-click combination and batches up to ten aspects. [Thaumcraft Research Tweaks](https://github.com/GTNewHorizons/thaumcraft-research-tweaks#aspect-combination)
- The combination packet has flags indicating whether each input comes from table bonus aspects. The current mod always sends both flags as `false`.

## Important problems found

### 1. The search discards feasible inventory states

The visited key is only:

`hex position + current aspect`

at [ResearchSolver.java](/D:/projects/ftr/src/main/java/com/luigi/thaumcraft/researchsolver/ResearchSolver.java:285).

Two paths can reach the same hex/aspect while retaining completely different aspect inventories. The cheaper path is kept and the other is discarded even when only the discarded inventory can finish the remaining roots.

This is the most likely explanation for the `Lucrum/Humanus/Instrumentum` failure in `issues.txt`. The aspect relationship is recognized; the surrounding search state is being pruned incorrectly.

### 2. The timeout does not cover permutation generation

`getPermutations` constructs all `(roots - 1)!` permutations before the deadline is checked. See [ResearchSolver.java](/D:/projects/ftr/src/main/java/com/luigi/thaumcraft/researchsolver/ResearchSolver.java:198) and [ResearchSolver.java](/D:/projects/ftr/src/main/java/com/luigi/thaumcraft/researchsolver/ResearchSolver.java:235).

Consequently, a large note can freeze or allocate heavily before the one-second timeout becomes effective.

### 3. Timed-out partial solutions are applied

The solver returns and applies partial placements after a timeout. These cannot complete the note but still consume aspects and ink. That directly conflicts with the efficiency goal.

### 4. Player and bonus aspect provenance is lost

Player aspects and bonus aspects are merged into a single `AspectList`. The planner therefore cannot say which source a combination input should use.

For combinations involving bonus aspects, the generated packet will often fail because both bonus flags are currently `false`.

### 5. The cost function does not optimize ink

The current objective favors abundant aspects. Multiple low-cost, abundant placements can beat a shorter path containing one scarcer aspect.

That means the result is not necessarily:

- Minimum ink.
- Minimum number of aspect points.
- Minimum synthesis cost.
- Globally optimal across root orders.

It is only locally cheapest according to the current fractional abundance score.

### 6. “Auto-refill” does not refill tools

It swaps in another already-charged scribing tool. It does not use black dye to refill an existing tool.

More importantly, [GuiOverlayHandler.java](/D:/projects/ftr/src/main/java/com/luigi/thaumcraft/researchsolver/GuiOverlayHandler.java:539) ends with `return currentUses > 0`. Thus a plan requiring ten ink can be approved when the table only has one ink remaining. The remaining packets then fail partway through, wasting resources.

### 7. Execution is optimistic

Combination and placement packets are sent immediately, with no acknowledgement or authoritative re-read between phases. Solve All increments its solved counter and swaps notes back without verifying that the server marked the note complete.

## Recommended implementation plan

### Phase 1 — Correct resource model and regression tests

Create a solver-side `ResourceLedger` containing separate indexed counts for:

- Player aspect points.
- Table bonus aspects.
- Aspects produced during the plan.
- Planned ink usage.
- Planned combination operations.

Build the aspect graph dynamically from `Aspect.aspects` and `getComponents()` when the research GUI opens. This supports GTNH addon aspects and avoids Research Tweaks’ documented late-aspect initialization problem.

Add regression fixtures before replacing the algorithm:

- `Lucrum — Humanus — Instrumentum`.
- Same hex/aspect reached with two different remaining inventories.
- Nested synthesis.
- Mixed player and bonus inputs.
- Insufficient ink by exactly one point.
- Timeout with no resource consumption.

### Phase 2 — Make completion and ink the primary objectives

Use a lexicographic cost vector instead of one `double`:

1. Must connect every root.
2. Minimize placements/ink.
3. Minimize weighted aspect depletion.
4. Minimize total research points consumed through synthesis.
5. Minimize combination operations.

A configurable alternative could prioritize scarce-aspect preservation ahead of ink, but minimum ink is a sensible default because it generally also minimizes placed aspect points.

Use iterative deepening from a geometric lower bound: first search for a solution with the minimum possible number of vacant cells, then increase the placement limit only when necessary.

### Phase 3 — Replace unsafe pruning and factorial enumeration

- Include the resource state in search labels.
- Retain multiple nondominated labels for a hex/aspect: one state may use less ink while another preserves more rare aspects.
- Precompute direct aspect adjacency instead of scanning every registered aspect during every expansion.
- Use indexed integer arrays and parent pointers instead of repeatedly copying `AspectList`, path lists, and combination lists.
- Generate root orderings lazily or incorporate the connected-root mask into the search.
- Never materialize every permutation.
- Move solving off the render thread using an immutable note/inventory snapshot.
- On timeout, return the best complete solution only. Never auto-apply a partial result.

### Phase 4 — Finish aspect combining properly

Represent synthesis as a `CraftPlan` DAG:

- Output aspect.
- Two parents.
- Quantity.
- Source of each parent: player, bonus, or previously crafted.
- Dependency order.

Then:

1. Topologically sort combinations so intermediate aspects are created first.
2. Coalesce repeated recipes.
3. Send normal vanilla combination packets as the compatibility baseline.
4. Set bonus flags from the resource ledger.
5. Wait for the resulting aspect-pool update before sending dependent combinations.
6. Re-read the authoritative pools before placement.
7. Abort cleanly if any expected delta does not arrive.

Research Tweaks batch behavior can be a later optimization. Per-combination vanilla packets are simpler, work without making Research Tweaks a hard dependency, and preserve normal Thaumcraft progression.

### Phase 5 — Replace ink checking with an ink execution plan

Rename the current feature to “Auto-swap scribing tools”; that is what it does.

Before applying:

- Calculate `requiredInk = number of placements`.
- Calculate current-tool ink plus all accessible spare-tool ink.
- Refuse to begin unless the complete plan can finish.
- Create swap boundaries, such as “place 7, swap, place 12”.
- Drain the current partial tool first.
- Then select either the smallest adequate tool or the fullest tool, depending on whether the preference is fewer swaps or finishing partial tools.

True black-dye refilling should be a separate opt-in feature. Because this is client-only and the research-table container has no crafting grid, silently crafting a refill from inventory would not be server-authoritative. I would not include that until there is a legitimate container/recipe interaction available.

### Phase 6 — Transactional application

Replace the immediate packet loops with a tick-based state machine:

`Snapshot → Combine → Confirm pools → Ensure tool → Place chunk → Confirm note → Swap tool/note`

Also:

- Abort when the GUI closes, the note changes, or inventory diverges.
- Validate the final layout locally using Thaumcraft’s exact direct-parent rule before sending anything.
- After the last placement, wait for `note.complete`.
- Only then increment Solve All’s success count and return the note to inventory.

### Phase 7 — Modern draggable UI and layout editor

Replace the three disconnected vanilla buttons with a small floating **Research Solver action palette**. Keep the implementation on the existing Forge `GuiScreenEvent` overlay so the mod remains client-only and does not gain a hard dependency on a particular GTNH UI-library version.

The default palette should contain:

- **Solve** — primary action with a wand/check icon.
- **Preview / Hide** — toggled eye icon instead of changing button width with different labels.
- **Solve All** — stacked-paper icon, visually separated because it is a larger operation.
- **Settings / Layout** — gear icon that opens configuration and layout controls.
- A compact status row showing `Ready`, `Solving`, `Combining`, `Placing`, `Complete`, or a short error state.

Use a restrained Thaumcraft-compatible visual style rather than plain `GuiButton`: a translucent dark panel, parchment/gold accent, consistent 16-pixel icons, clear hover/pressed/disabled states, and short localized tooltips. Text labels should remain available in expanded mode and for accessibility; compact mode can use icons only.

#### Dragging and positioning

- Add a visible drag handle to move the whole palette without accidentally triggering Solve.
- Add an optional **Edit Layout** mode in which individual actions can be dragged to reorder them or change between horizontal and vertical layouts.
- Treat movement beyond a small threshold as a drag and suppress the button click on release.
- Snap to the research GUI edges, nearby buttons, and an optional small grid.
- Clamp the palette to the visible scaled screen so GUI-scale or resolution changes cannot strand it off-screen.
- Show alignment guides and a translucent placement preview while dragging.
- Support `Reset position`, `Reset current GUI profile`, and `Reset all UI settings`.
- Allow keyboard nudging by one pixel, or by a larger step while Shift is held, for precise placement.

Store positions relative to an anchor (`top-left`, `top-right`, `bottom-left`, or `bottom-right`) instead of only absolute X/Y values. Persist separate layout profiles for vanilla `GuiResearchTable` and Research Tweaks' `ComposableContainerGui`, with the existing numeric config fields retained as migration data and a manual fallback.

The stored layout should include:

- Anchor and relative X/Y offset.
- Horizontal or vertical orientation.
- Compact or expanded presentation.
- Button order.
- Palette locked/unlocked state.
- Optional scale and opacity, within safe limits.

#### Better feedback before spending resources

After a solution is calculated, show a compact plan summary beside the palette or in its tooltip:

- Placements and ink required.
- Direct aspect points consumed.
- Compound aspects that must be combined.
- Number of scribing-tool swaps.
- Warnings for insufficient ink, missing aspects, timeout, or a stale note snapshot.

During transactional application, replace repeated chat messages with the status row and a small progress indicator. Important failures should still be sent to chat so they are not lost when the GUI closes. Disable conflicting actions while solving or applying, expose Cancel where cancellation is safe, and only show success after the server confirms completion.

#### Compatibility and implementation boundaries

- Implement the first version as small custom `GuiButton`/renderer/input-handler classes using Minecraft 1.7.10 rendering and Forge GUI events.
- Do not replace the full Thaumcraft or Research Tweaks GUI.
- Keep the palette above the research GUI but out of the hex board, inventory slots, and known Research Tweaks controls.
- Recalculate anchored positions whenever GUI size, GUI scale, or screen resolution changes.
- Make textures and colors resource-pack overrideable and put all visible strings in localization files.
- Preserve a plain-button compatibility mode in case another GUI mod conflicts with custom rendering or mouse capture.
- Borrow the responsive panel, widget-state, and theme concepts documented by [GTNH ModularUI2](https://github.com/GTNewHorizons/ModularUI2), but avoid making it mandatory; projects such as [TaskNH](https://github.com/GTNewHorizons/TaskNH#requirements) document pack-version constraints for newer ModularUI2/GTNHLib combinations.

#### UI acceptance tests

- Drag the palette and individual buttons at every Minecraft GUI scale and several window resolutions.
- Confirm a drag never triggers Solve, Preview, or Solve All.
- Confirm positions persist after closing the table, restarting the client, and switching worlds.
- Confirm vanilla and Research Tweaks retain independent positions.
- Resize the window after placing the palette at every edge and verify it remains visible.
- Test overlapping NEI panels and Research Tweaks controls; the palette must remain usable without covering inventory slots or the aspect grid.
- Test compact/expanded, horizontal/vertical, reordered buttons, reset actions, keyboard nudging, and plain compatibility mode.
- Verify disabled, solving, combining, placing, success, failure, and cancellation states are visually distinct.

## Minecraft test pass

Use `/tcrstest all` in a cheats-enabled creative singleplayer world for the automated solver/resource, ink, UI, and armed table dry-run checks. The command arms its live phase when no table is open; open a Research Table containing a note afterward. `/tcrstest live` arms a safe asynchronous preview, while `/tcrstest live apply` arms the validated transactional solve. The command was deliberately added only after the implementation phases above.

Once implemented, I recommend testing these in both vanilla Research Table GUI and Research Tweaks:

- The exact `Lucrum/Humanus/Instrumentum` note.
- Zero ink, exact ink, one ink short, and total ink spread across several tools.
- Vanilla scribing tools plus an infused/high-capacity inkwell.
- A compound requiring two or more synthesis levels.
- Synthesis using player aspects only.
- Synthesis where a parent exists only in bonus aspects.
- Research Expertise/Mastery locked and unlocked.
- High-latency multiplayer, to expose packet-order assumptions.
- Solve All with successful, completed, impossible, and timed-out notes mixed together.
- Close the GUI during combining and during placement.
- Drag and reposition the action palette before, during, and after previewing a solution.
- Change GUI scale or resize the game window and confirm the saved anchored position remains usable.
- Compare before/after: ink used, player aspect deltas, bonus deltas, combination count, placement count, solve time, and whether the note actually completed.

The highest-value first milestone is: fix resource-state pruning, prohibit partial application, and make the ink preflight exact. Those three changes should address the observed failure and stop most avoidable aspect/ink waste before undertaking the larger optimizer rewrite.
