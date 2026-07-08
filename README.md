# Thaumcraft Research Solver

A client-side-only Minecraft Forge mod that adds two buttons to the **Thaumcraft Research Table** GUI:

- **Solve** — automatically fills in the hex research puzzle with valid aspects drawn from your knowledge pool, sending the same clicks the game would have you make manually.
- **View Solution** — shows a semi-transparent ghost preview of what each empty hex should be filled with, without actually placing anything.

**100% client-side.** You can install this mod on your own game client and play on a GTNH server that has never heard of it — no kick, no errors, no server installation required.

---

## Compatibility & Configurability

### Supporting Custom GUI Mods (e.g. Thaumcraft Research Tweaks)
The mod is designed to work with both the standard vanilla **Thaumcraft Research Table** and the rewritten custom GUI from the **Thaumcraft Research Tweaks** mod (`elan.tweaks.common.gui.ComposableContainerGui`). It automatically detects which GUI is open and adjusts layout offsets accordingly so that buttons and ghost previews are placed correctly.

### Configurable Button Positions
You can configure the exact X/Y offsets of both the **Solve** and **View Solution** buttons:
1. Go to the main menu -> **Mods**.
2. Select **Thaumcraft Research Solver** from the list and click **Config**.
3. Under the **position** category, you can change the button offsets for both the vanilla GUI and the tweaked GUI.
4. Saving your changes will update the button positions immediately in-game (no restart required!).

Alternatively, you can manually edit the config file created at `config/thaumcraftresearchsolver.cfg`.

---

## What the mod does (plain language)

Thaumcraft's research minigame shows you a hex grid of aspect slots. Some hexes are pre-filled (the roots — shown with a coloured background). You must fill in the blank hexes so that every filled neighbour pair shares a relationship: either one aspect is a direct component of the other, or they share a component. The puzzle is complete when all roots are connected through a chain of related aspects.

**Solve** finds such a chain for you and places the aspects automatically, spending from your discovered knowledge pool (just like dragging aspects manually).

**View Solution** shows you the same answer as a translucent overlay so you can see what goes where without committing.

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
2. Drop the compiled `thaumcraftresearchsolver-1.0.0.jar` file into your `mods` folder.
   - On Windows: `%AppData%\.minecraft\mods\` (or your GTNH instance's `mods\` folder if using a launcher).
   - On macOS: `~/Library/Application Support/minecraft/mods/`
   - On Linux: `~/.minecraft/mods/`
3. Launch the game. The mod will appear in the **Mods** list in the main menu.

---

## Building from source

### Prerequisites

- **Java 8** (the build toolchain requires JDK 8)
- **Git**
- The Thaumcraft jar from your GTNH install (see below)

### Step 1 — Get the Thaumcraft jar

This mod compiles against the GTNH build of Thaumcraft. The jar is **not included** in this repository (it is copyrighted mod content). You need to copy it from your GTNH modpack's `mods/` folder:

```
Thaumcraft-1.7.10-4.2.3.5.jar
```

Place it at `libs/Thaumcraft-1.7.10-4.2.3.5.jar` inside this project folder (create the `libs/` directory if it doesn't exist). The `.gitignore` intentionally excludes this file — never commit it.

### Step 2 — Build

```
./gradlew build
```

On Windows use `gradlew.bat`:

```
gradlew build
```

The built jar will appear at `build/libs/thaumcraftresearchsolver-1.0.0.jar`.

### Troubleshooting the build

- **"Could not resolve Thaumcraft"** — make sure `libs/Thaumcraft-1.7.10-4.2.3.5.jar` exists.
- **Java version errors** — run `java -version` and make sure it is Java 8. Java 11+ will not work with ForgeGradle for 1.7.10.

---

## How it works (plain-language)

The mod hooks into Forge's `GuiScreenEvent` — a standard client-side event the game fires whenever a GUI screen is opened or drawn. When the Research Table screen opens, the mod injects two extra buttons into the button list Thaumcraft already renders.

When you click **Solve**, the mod:
1. Reads `ResearchNoteData` from the notes item currently in the table (this is standard synced data the server already sends to your client).
2. Runs a path-finding search (similar to Dijkstra's algorithm) that builds a chain of aspects connecting all the ROOT hexes through vacant NODE hexes, choosing aspects by the priority order above.
3. Sends `PacketAspectCombinationToServer` for any aspects it needs to combine, then `PacketAspectPlaceToServer` for each hex placement — these are the exact same network packets the vanilla Research Table GUI sends when you drag an aspect manually.

The server receives the same packets it always would, validates them by its normal rules, and marks the research complete when connectivity is satisfied. The mod never touches server state directly.

---

## Technical notes (for future maintainers / agents)

- **Strategy used:** Strategy A (extend vanilla GUI via `GuiScreenEvent`). Strategy B (full GUI replacement) was not needed because Thaumcraft's `GuiResearchTable` exposes enough fields at package-private or public visibility.
- **Thaumcraft jar:** Compiled and tested against `Thaumcraft-1.7.10-4.2.3.5.jar` (GTNH fork). If the jar has been updated in your modpack, verify the field names `GuiResearchTable.tileEntity`, `GuiResearchTable.player`, `GuiResearchTable.note` and the packet constructor signatures against the new jar's bytecode.
- **Open question resolution (§2):** The server does **not** pre-populate aspect values for blank hexes — `HexEntry.aspect` is null for all type-0 (VACANT) hexes. Confirmed from `ResearchManager.createNote()` bytecode. This means the solver performs genuine path-finding (not a trivial read-and-place) and will find one of many valid solutions.
- **Completion validation:** `ResearchManager.checkResearchCompletion()` only checks hex connectivity (via `checkConnections()`), **not** that placed aspect amounts match `ResearchCategories.getResearch(key).tags`. Adjacency-consistency alone is sufficient for the server to mark the puzzle complete.
- **Access Transformers:** `src/main/resources/META-INF/accesstransformer.cfg` widens `GuiResearchTable.tileEntity` (private → public) and `GuiResearchTable.player` (package-private → public). `guiLeft`/`guiTop` on the vanilla `GuiContainer` superclass are obfuscated vanilla fields — they cannot be AT-widened in a mod-only AT — so those are read once per GUI open via reflection.
- **Known limitations:**
  - If the solver cannot find a path (e.g. you lack the aspects needed to connect two ROOT hexes), it places what it can and silently skips the rest. There is no per-hex failure feedback yet.
  - The View Solution overlay does not update in real-time as you place aspects. Toggle it off and on to refresh after manual placements.
  - The mod does not handle the table's bonus aspects (from linked pedestals) being updated mid-puzzle — it reads them once when Solve/View Solution is clicked.

---

## Known limitations

- Does not support automatically finding and combining deep compound chains (more than 2 steps of synthesis). It will synthesize one level of combination (two source aspects → one compound) but not recurse further.
- No per-hex tooltip explaining why a hex was left empty.
- View Solution overlay does not auto-refresh as you place aspects manually.
