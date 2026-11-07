# Thaumcraft Research Solver

A client-side-only Minecraft Forge mod that adds a draggable action palette to the **Thaumcraft Research Table** GUI:

- **Solve** — automatically fills in the hex research puzzle with valid aspects drawn from your knowledge pool, sending the same clicks the game would have you make manually.
- **View Solution** — shows a semi-transparent ghost preview of what each empty hex should be filled with, without actually placing anything.
- **Solve All** — automatically iterates through all unsolved research notes in your hotbar and inventory, placing each in the table in turn, solving it, and returning it.

**100% client-side.** You can install this mod on your own game client and play on a GTNH server that has never heard of it — no kick, no errors, no server installation required.

---

## Compatibility & Configurability

### Supporting Custom GUI Mods (e.g. Thaumcraft Research Tweaks)
The mod is designed to work with both the standard vanilla **Thaumcraft Research Table** and the rewritten custom GUI from the **Thaumcraft Research Tweaks** mod (`elan.tweaks.common.gui.ComposableContainerGui`). It automatically detects which GUI is open and adjusts layout offsets accordingly so that buttons and ghost previews are placed correctly.

### Draggable palette and behavior
Click **Layout** to unlock the palette, then drag its `+` handle. Its position is clamped to the screen and saved independently for vanilla Thaumcraft and Research Tweaks. Right-click **Layout** to switch vertical/horizontal orientation; Shift-right-click toggles compact mode. A plain-button compatibility mode is available in config.

You can also configure palette offsets and behavior:
1. Go to the main menu -> **Mods**.
2. Select **Thaumcraft Research Solver** from the list and click **Config**.
3. Under **ui**, configure layout, compact mode, locking, and compatibility rendering.
4. Under **behavior**, toggle **Auto-swap Scribing Tools** and set the solver time budget.
   Diagnostic tracing is enabled by default and can be disabled there if needed.
5. Saving your changes will update settings immediately in-game (no restart required!).

Alternatively, you can manually edit the config file created at `config/thaumcraftresearchsolver.cfg`.

---

## What the mod does (plain language)

Thaumcraft's research minigame shows you a hex grid of aspect slots. Some hexes are pre-filled (the roots — shown with a coloured background). You must fill in the blank hexes so that every filled neighbour pair shares a relationship: either one aspect is a direct component of the other, or they share a component. The puzzle is complete when all roots are connected through a chain of related aspects.

**Solve** finds such a chain for you and places the aspects automatically, spending from your discovered knowledge pool (just like dragging aspects manually).

**View Solution** shows you the same answer as a translucent overlay so you can see what goes where without committing.

**Solve All** automates the entire process for multiple papers. It scans your inventory for research notes, slots them in one-by-one, solves them, and places them back.

### Auto-swap Scribing Tools

Solving puzzles consumes ink from the scribing tools (quill or inkwell) located in slot 0 of the table. When the tool in the table runs dry:
- If **Auto-refill Scribing Tools** is enabled, the mod scans your inventory for any valid scribing tool (`IScribeTools` implementation).
- If multiple candidates are found, it sorts them by a smart priority system:
  1. **Lower-capacity tools first.** Always prefer the lowest-capacity tool type (lower tier) first, full stop — this means every normal Thaumcraft inkwell (maxDamage 100) gets selected and fully drained before a ThaumicTinkerer infused inkwell (maxDamage 800) or other higher-tier tools are touched, regardless of how depleted the higher-tier tool already is.
  2. **Partially-used tools first** (within the same tier) — so among multiple normal quills, the most-used one gets finished off before starting a fresh one.
- It swaps the tool in using client-side window-click packets authoritative to the server, and continues solving.
- If no scribing tools remain in your inventory, it stops cleanly with a chat message informing you how many notes were solved.

### How aspect priority works

When more than one aspect would legally fit a hex, the mod picks by this order:

1. **Deplete your most abundant aspects first.** If you have 200 Aer and 3 Ignis, it tries to spend Aer before Ignis.
2. **Prefer primal aspects (Aer, Aqua, Ignis, Terra, Ordo, Perditio)** when amounts are tied.
3. **Prefer compound aspects requiring fewer combination steps** when both of the above tie.

### What it can and cannot do

The server genuinely does not store the "correct answer" for each blank hex — it only records what the player has placed and checks connectivity rules on completion. This means:

- ✅ The solver finds **a valid solution** (there are usually many).
- ❌ It cannot magically reveal a single predetermined answer — there isn't one.
- ❌ It cannot fill hexes for which you lack the required aspects (it will skip those and fill what it can).

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.7.10 |
| Minecraft Forge | 10.13.4.1614 |
| Thaumcraft | 4.2.3.5 (GTNH build) |

---

## Installation (for players)

You only need to install this on **your own game client**. The server does not need it.

1. Make sure Forge 10.13.4.1614 is installed for Minecraft 1.7.10 (the GTNH pack already includes this).
2. Download [v1.3.1-beta.1](https://github.com/LuigiSalvatore/f-thaumcraft-research/releases/tag/v1.3.1-beta.1) and drop `thaumcraftresearchsolver-v1.3.1-beta.1.jar` into your `mods` folder, replacing the old solver JAR.
   - On Windows: `%AppData%\.minecraft\mods\` (or your GTNH instance's `mods\` folder if using a launcher).
   - On macOS: `~/Library/Application Support/minecraft/mods/`
   - On Linux: `~/.minecraft/mods/`
3. Launch the game. The mod will appear in the **Mods** list in the main menu.

---

## Building from source

### Prerequisites

- **Java 25** (the pinned daemon toolchain; output targets Java 8)
- **Git**
- Network access to GTNH Maven

### Step 1 — Get the Thaumcraft jar

This mod compiles against the GTNH build of Thaumcraft. The jar is **not included** in this repository (it is copyrighted mod content). You need to copy it from your GTNH modpack's `mods/` folder:

```
Thaumcraft-1.7.10-4.2.3.5.jar
```

The current build resolves the deobfuscated development dependency from GTNH Maven through `dependencies.gradle`. No manual JAR copy is needed. Never commit copyrighted mod JARs.

### Step 2 — Build

```
./gradlew -I scripts/verify.gradle check assemble solverDiagnostics --no-configuration-cache
```

On Windows use `gradlew.bat`:

```
gradlew.bat -I scripts/verify.gradle check assemble solverDiagnostics --no-configuration-cache
```

The built jar will appear under `build/libs/`.

### Troubleshooting the build

- **Dependency resolution errors**: check GTNH Maven access and the pinned dependency in `dependencies.gradle`.
- **Java version errors**: run `java -version` and use Java 25 with this wrapper.

---

## How it works (plain-language)

The mod hooks into Forge's `GuiScreenEvent` — a standard client-side event the game fires whenever a GUI screen is opened, drawn, or interacts with a button. When the Research Table screen opens, the mod injects three extra buttons into the button list.

It subscribes to `GuiScreenEvent.DrawScreenEvent.Pre` to continuously update the buttons' enabled/disabled states based on whether notes are present in the table or in the player's inventory.

When you click **Solve**, the mod:
1. Reads `ResearchNoteData` from the notes item currently in the table.
2. Runs a resource-aware weighted A* search over root connection orders. It tries the nearest-root order first and returns the first complete local-best plan. A board-derived estimate guides each search toward its target while exact player, bonus, and crafted-aspect ledgers prevent infeasible plans. Equal-length choices favor the aspects you hold most of, which naturally supports repeatable bridge pairs such as Ordo/Instrumentum when those are abundant.
3. Sends `PacketAspectCombinationToServer` for any aspects it needs to combine, then `PacketAspectPlaceToServer` for each hex placement.

When you click **Solve All**, the mod iterates through your inventory notes, sends window click packets to swap the note into slot 1, solves it, and swaps it back.

---

## Technical notes (for future maintainers / agents)

- **Strategy used:** Strategy A (extend vanilla GUI via `GuiScreenEvent`). Strategy B (full GUI replacement) was not needed because Thaumcraft's `GuiResearchTable` exposes enough fields at package-private or public visibility.
- **Root ordering, resource state, and timeout:** Root order matters when aspects are scarce, so the solver tries the distance-sorted order first and only evaluates alternatives if that order fails. Weighted A* deliberately favors progress toward the target and returns the first complete plan instead of spending the remaining budget proving global optimality. Remaining player, bonus, and crafted aspects stay in the visited-state identity. Only complete solutions are returned or applied; a timeout without a complete result spends nothing.
- **Solver diagnostic traces:** Each Solve/Preview/Solve All calculation writes a standalone trace under `.minecraft/logs/thaumcraftresearchsolver/`. It records the full note geometry, roots and existing nodes, aspect recipes and pools, abundant valid bridge pairs, root orders, per-path search counters, timeouts, combinations, and final placements. No player name, server address, or chat is recorded. The newest 25 traces are retained. Set `solverDiagnosticLogging=false` under `behavior` to disable them.
- **Scribing tools charge model:** Durability is measured via `stack.getMaxDamage() - stack.getItemDamage()`. Standard vanilla scribe tools have 100 uses, infused inkwells have 800. Any valid `IScribeTools` implementation is supported.
- **Window click packets:** Inventory swaps (for Solve All notes and Auto-refill scribe tools) are sent via `PlayerControllerMP.windowClick` (which sends `C0EPacketClickWindow` to the server). The swap runs locally first, which makes it synchronous and instantaneous on the client.
- **Access Transformers:** `src/main/resources/META-INF/accesstransformer.cfg` widens `GuiResearchTable.tileEntity` (private → public) and `GuiResearchTable.player` (package-private → public). `guiLeft`/`guiTop` on the vanilla `GuiContainer` superclass are obfuscated vanilla fields — they cannot be AT-widened in a mod-only AT — so those are read once per GUI open via reflection.
- **Known limitations:**
  - View Solution overlay does not update in real-time as you place aspects. Toggle it off and on to refresh after manual placements.
  - The mod does not handle the table's bonus aspects (from linked pedestals) being updated mid-puzzle — it reads them once when Solve/View Solution is clicked.

---

## Known limitations

- No per-hex tooltip explaining why a hex was left empty.
- View Solution overlay does not auto-refresh as you place aspects manually.

---

## Releasing

Follow [docs/RELEASING.md](docs/RELEASING.md) for the current prerelease workflow. [Release notes](docs/RELEASE_NOTES.md) describe changes since v1.3.0 and remaining runtime checks. The helper below is historical and does not implement all current release checks.

Releases are published with the [`scripts/release.ps1`](scripts/release.ps1) PowerShell script. It
builds the jar, creates a git tag, and publishes a GitHub release with the reobfuscated jar attached
— all from the command line.

### Prerequisites

1. Install the [GitHub CLI](https://cli.github.com):
   ```powershell
   winget install GitHub.cli
   ```
2. Authenticate:
   ```powershell
   gh auth login
   ```

### Usage

```powershell
# Publish a release immediately
.\scripts\release.ps1 -Version "v1.2.0"

# With custom release notes
.\scripts\release.ps1 -Version "v1.2.0" -Notes "Bugfixes and performance improvements"

# Create as a draft (review on GitHub before publishing)
.\scripts\release.ps1 -Version "v1.2.0" -Draft
```

The script will:
1. Fail loudly if the build fails — no tag or push happens.
2. Warn if there are uncommitted changes, and ask for confirmation.
3. Auto-generate release notes from `git log` since the last tag if `-Notes` is not given.
4. Attach the reobfuscated jar (newest non-`-dev`/`-sources` jar in `build/libs/`).
