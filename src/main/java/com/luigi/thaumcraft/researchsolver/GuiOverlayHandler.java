package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.client.gui.GuiResearchTable;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketAspectCombinationToServer;
import thaumcraft.common.lib.network.playerdata.PacketAspectPlaceToServer;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.tiles.TileResearchTable;

import java.util.List;
import java.util.Map;

/**
 * Hooks into the GuiResearchTable lifecycle to inject Solve / View Solution buttons
 * and handle their click actions — entirely client-side.
 *
 * Button IDs are chosen far above Thaumcraft's own range to avoid collisions:
 *   200 = Solve
 *   201 = View Solution toggle
 */
@SideOnly(Side.CLIENT)
public class GuiOverlayHandler {

    // IDs far above TC's own button range to avoid collisions.
    public static final int BTN_SOLVE       = 200;
    public static final int BTN_VIEW_TOGGLE = 201;

    /**
     * Shared mutable state: whether the "View Solution" overlay is currently active.
     * Read by GuiRenderHandler on every DrawScreen event.
     */
    public static boolean viewSolutionActive = false;

    /**
     * The last computed solve result, cached so the render handler can use it without
     * re-running the solver every frame.
     */
    public static ResearchSolver.SolveResult lastSolveResult = null;

    // -------------------------------------------------------------------------
    // Button injection
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiResearchTable)) {
            return;
        }
        // Reset overlay state whenever a new Research Table GUI opens.
        viewSolutionActive = false;
        lastSolveResult = null;

        // Inject buttons at the bottom of the research grid panel.
        // The vanilla GUI is 256x236 and centred; buttons go below the hex grid.
        // We use small (60-wide) buttons stacked in the top-right of the parchment.
        int guiLeft = getGuiLeft(event.gui);
        int guiTop  = getGuiTop(event.gui);

        //noinspection unchecked
        List<GuiButton> buttons = event.buttonList;
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

        EntityPlayer player    = gui.player;
        TileResearchTable tile = gui.tileEntity;
        int tx = tile.xCoord, ty = tile.yCoord, tz = tile.zCoord;

        // Send combination packets first — the server must know the player performed the
        // combination before the resulting aspect can be placed.
        for (ResearchSolver.Combination combo : result.combinations) {
            PacketHandler.INSTANCE.sendToServer(
                new PacketAspectCombinationToServer(
                    player, tx, ty, tz,
                    combo.first, combo.second,
                    false, false, // neither comes from the table bonus pool
                    false
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
            // Recompute (or reuse) the solution when toggling ON.
            lastSolveResult = computeSolution(gui);
        }
        // Update the toggle button label to reflect the new state.
        for (Object o : ((GuiScreen) gui).buttonList) {
            if (o instanceof GuiButton) {
                GuiButton btn = (GuiButton) o;
                if (btn.id == BTN_VIEW_TOGGLE) {
                    btn.displayString = viewSolutionActive ? "Hide Solution" : "View Solution";
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Runs the solver with the player's current knowledge and the table's bonus aspects.
     * Returns null if there is no note in the table or no moves can be made.
     */
    private ResearchSolver.SolveResult computeSolution(GuiResearchTable gui) {
        ResearchNoteData note = gui.note;
        if (note == null) {
            return null;
        }

        EntityPlayer player = gui.player;
        AspectList playerPool = Thaumcraft.proxy.getPlayerKnowledge()
            .getAspectsDiscovered(player.getCommandSenderName());
        if (playerPool == null) {
            playerPool = new AspectList();
        }

        TileResearchTable tile = gui.tileEntity;
        AspectList bonusPool = (tile != null && tile.bonusAspects != null)
            ? tile.bonusAspects
            : new AspectList();

        return ResearchSolver.solve(note, playerPool, bonusPool);
    }

    /**
     * Enables/disables the Solve and View Solution buttons based on whether there is
     * an active note and the solver produced any placements.
     */
    private void updateButtonStates(GuiScreen gui) {
        boolean hasNote = false;
        if (gui instanceof GuiResearchTable) {
            hasNote = ((GuiResearchTable) gui).note != null;
        }
        for (Object o : gui.buttonList) {
            if (o instanceof GuiButton) {
                GuiButton btn = (GuiButton) o;
                if (btn.id == BTN_SOLVE || btn.id == BTN_VIEW_TOGGLE) {
                    btn.enabled = hasNote;
                }
            }
        }
    }

    private static int getGuiLeft(GuiScreen gui) {
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.inventory.GuiContainer.class
                .getDeclaredField("field_147003_i");
            f.setAccessible(true);
            return f.getInt(gui);
        } catch (Exception e) {
            return (Minecraft.getMinecraft().displayWidth / 2) - 128;
        }
    }

    private static int getGuiTop(GuiScreen gui) {
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.inventory.GuiContainer.class
                .getDeclaredField("field_147009_r");
            f.setAccessible(true);
            return f.getInt(gui);
        } catch (Exception e) {
            return (Minecraft.getMinecraft().displayHeight / 2) - 118;
        }
    }
}
