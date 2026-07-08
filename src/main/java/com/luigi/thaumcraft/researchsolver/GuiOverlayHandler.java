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
 * Hooks into the GuiResearchTable lifecycle (and its custom replacement elan.tweaks.common.gui.ComposableContainerGui)
 * to inject Solve / View Solution buttons and handle their click actions — entirely client-side.
 */
@SideOnly(Side.CLIENT)
public class GuiOverlayHandler {

    private static final Logger LOG = LogManager.getLogger("ThaumcraftResearchSolver");

    public static final int BTN_SOLVE       = 200;
    public static final int BTN_VIEW_TOGGLE = 201;

    public static boolean viewSolutionActive = false;
    public static ResearchSolver.SolveResult lastSolveResult = null;

    // -------------------------------------------------------------------------
    // Cached reflection fields for vanilla GUI elements
    // -------------------------------------------------------------------------

    static Field f_tileEntity  = null;
    static Field f_player      = null;
    static Field f_buttonList  = null;
    static Field f_guiLeft     = null;
    static Field f_guiTop      = null;

    private static Field findField(Class<?> clazz, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // Try next
            }
        }
        throw new NoSuchFieldException("None of the fields " + java.util.Arrays.toString(names) + " found in " + clazz.getName());
    }

    private static void initReflection() {
        if (f_buttonList != null) {
            return; // already initialised
        }
        LOG.info("[ResearchSolver] Initializing reflection...");
        try {
            // Try vanilla buttonList (MCP) and field_146292_n (SRG)
            f_buttonList = findField(GuiScreen.class, "buttonList", "field_146292_n");
            LOG.info("[ResearchSolver] Found buttonList field.");

            // Try vanilla guiLeft (MCP) and field_147003_i (SRG)
            f_guiLeft = findField(GuiContainer.class, "guiLeft", "field_147003_i");
            LOG.info("[ResearchSolver] Found guiLeft field.");

            // Try vanilla guiTop (MCP) and field_147009_r (SRG)
            f_guiTop = findField(GuiContainer.class, "guiTop", "field_147009_r");
            LOG.info("[ResearchSolver] Found guiTop field.");

            // Try to find vanilla GuiResearchTable-specific fields if available
            try {
                f_tileEntity = GuiResearchTable.class.getDeclaredField("tileEntity");
                f_tileEntity.setAccessible(true);
                LOG.info("[ResearchSolver] Found tileEntity field (GuiResearchTable).");
            } catch (Exception ex) {
                LOG.warn("[ResearchSolver] Failed to find vanilla tileEntity field; will rely on Slot search.");
            }

            try {
                f_player = GuiResearchTable.class.getDeclaredField("player");
                f_player.setAccessible(true);
                LOG.info("[ResearchSolver] Found player field (GuiResearchTable).");
            } catch (Exception ex) {
                LOG.warn("[ResearchSolver] Failed to find vanilla player field; will rely on client player.");
            }

        } catch (Exception e) {
            LOG.error("[ResearchSolver] CRITICAL: Reflection initialization failed!", e);
        }
    }

    // -------------------------------------------------------------------------
    // Universal accessors (safe for both vanilla and custom ComposableContainerGui)
    // -------------------------------------------------------------------------

    static boolean isResearchGui(GuiScreen gui) {
        if (gui == null) return false;
        if (gui instanceof GuiResearchTable) return true;
        return gui.getClass().getName().equals("elan.tweaks.common.gui.ComposableContainerGui");
    }

    static TileResearchTable getTileEntity(GuiScreen gui) {
        if (gui instanceof GuiContainer) {
            GuiContainer containerGui = (GuiContainer) gui;
            if (containerGui.inventorySlots != null && containerGui.inventorySlots.inventorySlots != null) {
                for (Object slotObj : containerGui.inventorySlots.inventorySlots) {
                    if (slotObj instanceof net.minecraft.inventory.Slot) {
                        net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) slotObj;
                        if (slot.inventory instanceof TileResearchTable) {
                            return (TileResearchTable) slot.inventory;
                        }
                    }
                }
            }
        }
        if (gui instanceof GuiResearchTable) {
            try {
                if (f_tileEntity != null) {
                    return (TileResearchTable) f_tileEntity.get(gui);
                }
            } catch (Exception e) {
                LOG.error("[ResearchSolver] Failed to get tileEntity via reflection fallback", e);
            }
        }
        return null;
    }

    static ResearchNoteData getNote(GuiScreen gui) {
        if (gui instanceof GuiResearchTable) {
            return ((GuiResearchTable) gui).note;
        }
        TileResearchTable tile = getTileEntity(gui);
        if (tile != null) {
            net.minecraft.item.ItemStack notesStack = tile.getStackInSlot(1);
            if (notesStack != null) {
                return thaumcraft.common.lib.research.ResearchManager.getData(notesStack);
            }
        }
        return null;
    }

    private static EntityPlayer getPlayer(GuiScreen gui) {
        if (gui instanceof GuiResearchTable && f_player != null) {
            try {
                return (EntityPlayer) f_player.get(gui);
            } catch (Exception e) {
                // fall through
            }
        }
        return Minecraft.getMinecraft().thePlayer;
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtonList(GuiScreen gui) {
        try {
            if (f_buttonList == null) {
                return null;
            }
            return (List<GuiButton>) f_buttonList.get(gui);
        } catch (Exception e) {
            LOG.error("[ResearchSolver] Failed to get buttonList via reflection!", e);
            return null;
        }
    }

    private static int getGuiLeft(GuiScreen gui) {
        try {
            if (f_guiLeft == null) {
                return 0;
            }
            return f_guiLeft.getInt(gui);
        } catch (Exception e) {
            LOG.error("[ResearchSolver] Failed to get guiLeft via reflection!", e);
            return 0;
        }
    }

    private static int getGuiTop(GuiScreen gui) {
        try {
            if (f_guiTop == null) {
                return 0;
            }
            return f_guiTop.getInt(gui);
        } catch (Exception e) {
            LOG.error("[ResearchSolver] Failed to get guiTop via reflection!", e);
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Button injection
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        String guiClassName = (event.gui != null) ? event.gui.getClass().getName() : "null";
        LOG.info("[ResearchSolver] InitGuiEvent.Post event fired. Gui: " + guiClassName);

        if (!isResearchGui(event.gui)) {
            return;
        }
        LOG.info("[ResearchSolver] Detected Research Table GUI (" + guiClassName + "). Injecting buttons.");
        initReflection();

        viewSolutionActive = false;
        lastSolveResult    = null;

        int guiLeft = getGuiLeft(event.gui);
        int guiTop  = getGuiTop(event.gui);
        LOG.info("[ResearchSolver] Computed offsets: guiLeft=" + guiLeft + ", guiTop=" + guiTop);

        boolean isTweaks = event.gui.getClass().getName().equals("elan.tweaks.common.gui.ComposableContainerGui");
        int solveX = isTweaks ? Config.tweakedSolveX : Config.vanillaSolveX;
        int solveY = isTweaks ? Config.tweakedSolveY : Config.vanillaSolveY;
        int viewX = isTweaks ? Config.tweakedViewX : Config.vanillaViewX;
        int viewY = isTweaks ? Config.tweakedViewY : Config.vanillaViewY;

        List<GuiButton> buttons = getButtonList(event.gui);
        if (buttons == null) {
            LOG.error("[ResearchSolver] Cannot inject buttons: buttonList is null!");
            return;
        }
        
        LOG.info("[ResearchSolver] Injecting Solve button at: " + (guiLeft + solveX) + ", " + (guiTop + solveY));
        buttons.add(new GuiButton(BTN_SOLVE,
            guiLeft + solveX, guiTop + solveY,
            76, 16,
            "Solve"));
            
        LOG.info("[ResearchSolver] Injecting View Solution button at: " + (guiLeft + viewX) + ", " + (guiTop + viewY));
        buttons.add(new GuiButton(BTN_VIEW_TOGGLE,
            guiLeft + viewX, guiTop + viewY,
            76, 16,
            "View Solution"));

        LOG.info("[ResearchSolver] Successfully injected buttons. Total button count: " + buttons.size());
        updateButtonStates(event.gui);
    }

    // -------------------------------------------------------------------------
    // Button click handling
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!isResearchGui(event.gui)) {
            return;
        }
        LOG.info("[ResearchSolver] ActionPerformedEvent.Pre event fired. Button ID: " + event.button.id);

        switch (event.button.id) {
            case BTN_SOLVE:
                handleSolve(event.gui);
                break;
            case BTN_VIEW_TOGGLE:
                handleViewToggle(event.gui);
                break;
            default:
                break;
        }

        updateButtonStates(event.gui);
    }

    // -------------------------------------------------------------------------
    // Solving logic
    // -------------------------------------------------------------------------

    private void handleSolve(GuiScreen gui) {
        ResearchSolver.SolveResult result = computeSolution(gui);
        if (result == null) {
            LOG.warn("[ResearchSolver] Solver returned null (no solution found or no notes present).");
            return;
        }
        lastSolveResult = result;

        EntityPlayer player    = getPlayer(gui);
        TileResearchTable tile = getTileEntity(gui);
        if (player == null || tile == null) {
            LOG.error("[ResearchSolver] Cannot solve: player or tile entity is null.");
            return;
        }
        int tx = tile.xCoord, ty = tile.yCoord, tz = tile.zCoord;

        LOG.info("[ResearchSolver] Dispatching " + result.combinations.size() + " combinations and " + result.placements.size() + " placements.");

        for (ResearchSolver.Combination combo : result.combinations) {
            PacketHandler.INSTANCE.sendToServer(
                new PacketAspectCombinationToServer(
                    player, tx, ty, tz,
                    combo.first, combo.second,
                    false, false, false
                )
            );
        }

        ResearchNoteData note = getNote(gui);
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
        LOG.info("[ResearchSolver] Packets sent successfully.");
    }

    private void handleViewToggle(GuiScreen gui) {
        viewSolutionActive = !viewSolutionActive;
        LOG.info("[ResearchSolver] View Solution toggled to: " + viewSolutionActive);
        if (viewSolutionActive) {
            lastSolveResult = computeSolution(gui);
        }
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

    private ResearchSolver.SolveResult computeSolution(GuiScreen gui) {
        ResearchNoteData note = getNote(gui);
        if (note == null) {
            LOG.info("[ResearchSolver] No active research note found in table slot.");
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
        boolean hasNote = getNote(gui) != null;
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
