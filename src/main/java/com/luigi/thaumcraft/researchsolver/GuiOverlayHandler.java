package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.client.gui.GuiResearchTable;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketAspectCombinationToServer;
import thaumcraft.common.lib.network.playerdata.PacketAspectPlaceToServer;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.tiles.TileResearchTable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Hooks into the GuiResearchTable lifecycle to inject Solve / View Solution buttons
 * and handle their click actions — entirely client-side.
 *
 * Button IDs are chosen far above Thaumcraft's own range to avoid collisions:
 *   200 = Solve
 *   201 = View Solution toggle
 *
 * Field access strategy:
 *   - GuiResearchTable.tileEntity  : private    → accessed via cached reflection
 *   - GuiResearchTable.player      : package-private → accessed via cached reflection
 *   - GuiScreen.buttonList         : protected  → accessed via cached reflection
 *   - GuiContainer.guiLeft/guiTop  : (srg names field_147003_i / field_147009_r) → reflection
 *
 * The AT in accesstransformer.cfg correctly declares these as public, but the
 * GTNH convention build only applies ATs to the deobfuscated Minecraft jar — not to
 * third-party mod jars like Thaumcraft. All four reflect-accessed fields are looked up
 * once per class-load and cached as static Field objects, so there is no hot-path cost.
 */
@SideOnly(Side.CLIENT)
public class GuiOverlayHandler {

    private static final Logger LOG = LogManager.getLogger("ThaumcraftResearchSolver");

    public static final int BTN_SOLVE       = 200;
    public static final int BTN_VIEW_TOGGLE = 201;

    /**
     * Whether the "View Solution" overlay is currently active.
     * Read by GuiRenderHandler on every DrawScreen event.
     */
    public static boolean viewSolutionActive = false;

    /**
     * The last computed solve result, cached so the render handler can read it without
     * re-running the solver every frame.
     */
    public static ResearchSolver.SolveResult lastSolveResult = null;

    // -------------------------------------------------------------------------
    // Cached reflection fields — initialised lazily on first GUI open
    // -------------------------------------------------------------------------

    static Field f_tileEntity  = null;
    static Field f_player      = null;
    static Field f_buttonList  = null;
    static Field f_guiLeft     = null;
    static Field f_guiTop      = null;

    private static void initReflection() {
        if (f_tileEntity != null) {
            return; // already initialised
        }
        try {
            f_tileEntity = GuiResearchTable.class.getDeclaredField("tileEntity");
            f_tileEntity.setAccessible(true);

            f_player = GuiResearchTable.class.getDeclaredField("player");
            f_player.setAccessible(true);

            // GuiScreen.buttonList is protected; use the srg name to be safe under obfuscation.
            // In the dev environment (MCP mappings) it is "buttonList".
            f_buttonList = GuiScreen.class.getDeclaredField("buttonList");
            f_buttonList.setAccessible(true);

            // GuiContainer guiLeft / guiTop: srg names field_147003_i / field_147009_r
            f_guiLeft = GuiContainer.class.getDeclaredField("field_147003_i");
            f_guiLeft.setAccessible(true);

            f_guiTop = GuiContainer.class.getDeclaredField("field_147009_r");
            f_guiTop.setAccessible(true);

        } catch (NoSuchFieldException e) {
            LOG.error("[ResearchSolver] Reflection init failed — field not found: {}", e.getMessage());
            LOG.error("[ResearchSolver] This usually means the Thaumcraft or Forge version changed.");
        }
    }

    // Null-safe accessors

    private static TileResearchTable getTileEntity(GuiResearchTable gui) {
        try { return (TileResearchTable) f_tileEntity.get(gui); }
        catch (Exception e) { return null; }
    }

    private static EntityPlayer getPlayer(GuiResearchTable gui) {
        try { return (EntityPlayer) f_player.get(gui); }
        catch (Exception e) { return Minecraft.getMinecraft().thePlayer; }
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtonList(GuiScreen gui) {
        try { return (List<GuiButton>) f_buttonList.get(gui); }
        catch (Exception e) { return null; }
    }

    private static int getGuiLeft(GuiScreen gui) {
        try { return f_guiLeft != null ? f_guiLeft.getInt(gui) : 0; }
        catch (Exception e) { return 0; }
    }

    private static int getGuiTop(GuiScreen gui) {
        try { return f_guiTop != null ? f_guiTop.getInt(gui) : 0; }
        catch (Exception e) { return 0; }
    }

    // -------------------------------------------------------------------------
    // Button injection
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiResearchTable)) {
            return;
        }
        initReflection();

        // Reset overlay state whenever a new Research Table GUI opens.
        viewSolutionActive = false;
        lastSolveResult    = null;

        int guiLeft = getGuiLeft(event.gui);
        int guiTop  = getGuiTop(event.gui);

        // Buttons are placed in the top-right corner of the research parchment panel.
        // The parchment is 176 wide; we place 76-wide buttons just inside the right edge.
        List<GuiButton> buttons = getButtonList(event.gui);
        if (buttons == null) {
            return;
        }
        buttons.add(new GuiButton(BTN_SOLVE,
            guiLeft + 168, guiTop + 4,
            76, 16,
            "Solve"));
        buttons.add(new GuiButton(BTN_VIEW_TOGGLE,
            guiLeft + 168, guiTop + 22,
            76, 16,
            "View Solution"));

        updateButtonStates(event.gui);
    }

    // -------------------------------------------------------------------------
    // Button click handling
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.gui instanceof GuiResearchTable)) {
            return;
        }
        GuiResearchTable gui = (GuiResearchTable) event.gui;

        switch (event.button.id) {
            case BTN_SOLVE:
                handleSolve(gui);
                break;
            case BTN_VIEW_TOGGLE:
                handleViewToggle(gui);
                break;
            default:
                break;
        }

        updateButtonStates(gui);
    }

    // -------------------------------------------------------------------------
    // Logic
    // -------------------------------------------------------------------------

    private void handleSolve(GuiResearchTable gui) {
        ResearchSolver.SolveResult result = computeSolution(gui);
        if (result == null) {
            return;
        }
        lastSolveResult = result;

        EntityPlayer player    = getPlayer(gui);
        TileResearchTable tile = getTileEntity(gui);
        if (player == null || tile == null) {
            LOG.error("[ResearchSolver] Cannot solve: player or tile entity is null (reflection failed?)");
            return;
        }
        int tx = tile.xCoord, ty = tile.yCoord, tz = tile.zCoord;

        // Send combination packets first — the server must know the player performed the
        // combination before the resulting aspect can be placed.
        for (ResearchSolver.Combination combo : result.combinations) {
            PacketHandler.INSTANCE.sendToServer(
                new PacketAspectCombinationToServer(
                    player, tx, ty, tz,
                    combo.first, combo.second,
                    false, false, false
                )
            );
        }

        // Send a placement packet for every hex the solver filled in.
        ResearchNoteData note = gui.note;
        if (note == null || note.hexes == null) {
            return;
        }
        for (Map.Entry<String, Aspect> entry : result.placements.entrySet()) {
            thaumcraft.common.lib.utils.HexUtils.Hex hex = note.hexes.get(entry.getKey());
            if (hex == null) {
                continue;
            }
            PacketHandler.INSTANCE.sendToServer(
                new PacketAspectPlaceToServer(
                    player,
                    (byte) hex.q, (byte) hex.r,
                    tx, ty, tz,
                    entry.getValue()
                )
            );
        }
    }

    private void handleViewToggle(GuiResearchTable gui) {
        viewSolutionActive = !viewSolutionActive;
        if (viewSolutionActive) {
            lastSolveResult = computeSolution(gui);
        }
        // Update the toggle button label.
        List<GuiButton> buttons = getButtonList(gui);
        if (buttons == null) {
            return;
        }
        for (GuiButton btn : buttons) {
            if (btn.id == BTN_VIEW_TOGGLE) {
                btn.displayString = viewSolutionActive ? "Hide Solution" : "View Solution";
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResearchSolver.SolveResult computeSolution(GuiResearchTable gui) {
        ResearchNoteData note = gui.note;
        if (note == null) {
            return null;
        }

        EntityPlayer player = getPlayer(gui);
        if (player == null) {
            return null;
        }

        AspectList playerPool = Thaumcraft.proxy.getPlayerKnowledge()
            .getAspectsDiscovered(player.getCommandSenderName());
        if (playerPool == null) {
            playerPool = new AspectList();
        }

        TileResearchTable tile = getTileEntity(gui);
        AspectList bonusPool = (tile != null && tile.bonusAspects != null)
            ? tile.bonusAspects
            : new AspectList();

        return ResearchSolver.solve(note, playerPool, bonusPool);
    }

    private void updateButtonStates(GuiScreen gui) {
        boolean hasNote = false;
        if (gui instanceof GuiResearchTable) {
            hasNote = ((GuiResearchTable) gui).note != null;
        }
        List<GuiButton> buttons = getButtonList(gui);
        if (buttons == null) {
            return;
        }
        for (GuiButton btn : buttons) {
            if (btn.id == BTN_SOLVE || btn.id == BTN_VIEW_TOGGLE) {
                btn.enabled = hasNote;
            }
        }
    }
}
