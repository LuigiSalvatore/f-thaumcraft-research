package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.items.ItemResearchNotes;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.tiles.TileResearchTable;

import java.util.ArrayList;
import java.util.List;

/** Floating, draggable frontend for asynchronous solving and acknowledged execution. */
@SideOnly(Side.CLIENT)
public final class ModernGuiOverlayHandler {
    public static final int SOLVE = 300;
    public static final int PREVIEW = 301;
    public static final int SOLVE_ALL = 302;
    public static final int LAYOUT = 303;
    public static final int HANDLE = 304;
    public static final int STATUS = 305;

    private enum Request { NONE, APPLY, PREVIEW, ALL }

    private final AsyncSolveService solver = new AsyncSolveService();
    private final SolveExecutionController execution = new SolveExecutionController();
    private final CursorSlotSwapController noteSwap = new CursorSlotSwapController();
    private Request request = Request.NONE;
    private int paletteX;
    private int paletteY;
    private boolean dragging;
    private int dragX;
    private int dragY;
    private boolean previousLeftDown;
    private boolean previousRightDown;

    private List<Integer> allSlots;
    private int allIndex;
    private int allInventorySlot = -1;
    private int allDelay;
    private boolean allExecuting;
    private boolean allWaitingSwapIn;
    private boolean allWaitingSwapOut;
    private boolean allPendingSuccess;
    private boolean allNoteInTable;
    private int allSolved;
    private int allSkipped;
    private boolean liveTestArmed;
    private boolean liveTestApply;

    @SubscribeEvent
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!GuiOverlayHandler.isResearchGui(event.gui)) return;
        GuiOverlayHandler.initReflection();
        solver.cancel();
        request = Request.NONE;
        dragging = false;
        GuiOverlayHandler.viewSolutionActive = false;
        GuiOverlayHandler.lastSolveResult = null;
        boolean tweaks = isTweaks(event.gui);
        paletteX = GuiOverlayHandler.getGuiLeft(event.gui) + (tweaks ? Config.tweakedPaletteX : Config.vanillaPaletteX);
        paletteY = GuiOverlayHandler.getGuiTop(event.gui) + (tweaks ? Config.tweakedPaletteY : Config.vanillaPaletteY);
        clamp(event.gui);
        inject(event.gui);
    }

    private void inject(GuiScreen gui) {
        List<GuiButton> buttons = GuiOverlayHandler.getButtonList(gui);
        if (buttons == null) return;
        for (int index = buttons.size() - 1; index >= 0; index--) {
            int id = buttons.get(index).id;
            if (id >= SOLVE && id <= STATUS) buttons.remove(index);
        }
        int width = actionWidth();
        buttons.add(new PaletteButton(HANDLE, 0, 0, 14, 16, Config.paletteLocked ? "#" : "+"));
        buttons.add(new PaletteButton(SOLVE, 0, 0, width, 16, Config.paletteCompact ? "S" : "Solve"));
        buttons.add(new PaletteButton(PREVIEW, 0, 0, width, 16, Config.paletteCompact ? "P" : "Preview"));
        buttons.add(new PaletteButton(SOLVE_ALL, 0, 0, width, 16, Config.paletteCompact ? "All" : "Solve All"));
        buttons.add(new PaletteButton(LAYOUT, 0, 0, width, 16, Config.paletteLocked ? "Layout" : "Lock Layout"));
        buttons.add(new PaletteButton(STATUS, 0, 0, 100, 15, "Ready"));
        layout(gui);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!GuiOverlayHandler.isResearchGui(event.gui)) {
            if (execution.isBusy()) execution.cancel(Minecraft.getMinecraft().thePlayer, "Research GUI closed.");
            solver.cancel();
            allSlots = null;
            return;
        }
        solver.poll();
        if (liveTestArmed && GuiOverlayHandler.getNote(event.gui) != null && !solver.isRunning() && !execution.isBusy()) {
            boolean apply = liveTestApply;
            liveTestArmed = false;
            GuiOverlayHandler.viewSolutionActive = !apply;
            begin(event.gui, apply ? Request.APPLY : Request.PREVIEW);
            chat(event.gui, apply ? "Armed live apply test started." : "Armed live dry-run started.");
        }
        handleMouse(event.gui, event.mouseX, event.mouseY);
        acceptResult(event.gui);
        execution.tick(event.gui);
        tickAll(event.gui);
        clamp(event.gui);
        layout(event.gui);
        updateButtons(event.gui);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END &&
            GuiOverlayHandler.isResearchGui(Minecraft.getMinecraft().currentScreen)) {
            noteSwap.tick();
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!GuiOverlayHandler.isResearchGui(event.gui)) return;
        if (event.button.id == SOLVE) begin(event.gui, Request.APPLY);
        else if (event.button.id == PREVIEW) {
            GuiOverlayHandler.viewSolutionActive = !GuiOverlayHandler.viewSolutionActive;
            if (GuiOverlayHandler.viewSolutionActive) begin(event.gui, Request.PREVIEW);
            else GuiOverlayHandler.lastSolveResult = null;
        } else if (event.button.id == SOLVE_ALL) startAll(event.gui);
        else if (event.button.id == LAYOUT) {
            Config.setPaletteLocked(!Config.paletteLocked);
            inject(event.gui);
        }
    }

    private void handleMouse(GuiScreen gui, int mouseX, int mouseY) {
        boolean leftDown = Mouse.isButtonDown(0);
        boolean rightDown = Mouse.isButtonDown(1);
        if (leftDown && !previousLeftDown && !Config.paletteLocked && inside(mouseX, mouseY, paletteX, paletteY, 14, 16)) {
            dragging = true;
            dragX = mouseX - paletteX;
            dragY = mouseY - paletteY;
        } else if (!leftDown && previousLeftDown && dragging) {
            dragging = false;
            save(gui);
        } else if (dragging && leftDown) {
            paletteX = mouseX - dragX;
            paletteY = mouseY - dragY;
            clamp(gui);
        }
        if (rightDown && !previousRightDown && overLayout(mouseX, mouseY)) {
            boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            if (shift) {
                Config.paletteCompact = !Config.paletteCompact;
                Config.configuration.get("ui", "paletteCompact", Config.paletteCompact).set(Config.paletteCompact);
            } else {
                Config.paletteVertical = !Config.paletteVertical;
                Config.configuration.get("ui", "paletteVertical", Config.paletteVertical).set(Config.paletteVertical);
            }
            Config.configuration.save();
            inject(gui);
        }
        previousLeftDown = leftDown;
        previousRightDown = rightDown;
    }

    private void begin(GuiScreen gui, Request nextRequest) {
        if (solver.isRunning() || execution.isBusy()) return;
        ResearchNoteData note = GuiOverlayHandler.getNote(gui);
        EntityPlayer player = GuiOverlayHandler.getPlayer(gui);
        TileResearchTable tile = GuiOverlayHandler.getTileEntity(gui);
        if (note == null || player == null) return;
        AspectList pool = Thaumcraft.proxy.getPlayerKnowledge().getAspectsDiscovered(player.getCommandSenderName());
        AspectList bonus = tile == null || tile.bonusAspects == null ? new AspectList() : tile.bonusAspects;
        if (solver.start(note, pool, bonus)) request = nextRequest;
    }

    boolean startLiveTest(GuiScreen gui, boolean apply) {
        if (!GuiOverlayHandler.isResearchGui(gui) || solver.isRunning() || execution.isBusy()) return false;
        GuiOverlayHandler.viewSolutionActive = !apply;
        begin(gui, apply ? Request.APPLY : Request.PREVIEW);
        return solver.isRunning();
    }

    void armLiveTest(boolean apply) {
        liveTestArmed = true;
        liveTestApply = apply;
    }

    private void acceptResult(GuiScreen gui) {
        String error = solver.takeError();
        if (error != null) {
            chat(gui, "Solver error: " + error);
            if (request == Request.ALL) finishAll(gui, false);
            request = Request.NONE;
            return;
        }
        if (!solver.hasResult()) return;
        ResearchSolver.SolveResult result = solver.takeResult();
        GuiOverlayHandler.lastSolveResult = result;
        Request completed = request;
        request = Request.NONE;
        if (completed == Request.PREVIEW) {
            if (!result.complete) chat(gui, noSolutionText(result));
            return;
        }
        if (!result.complete) {
            chat(gui, noSolutionText(result));
            if (completed == Request.ALL) finishAll(gui, false);
            return;
        }
        if (execution.start(gui, result)) {
            if (completed == Request.ALL) allExecuting = true;
        } else if (completed == Request.ALL) finishAll(gui, false);
    }

    private static String noSolutionText(ResearchSolver.SolveResult result) {
        return result.timedOut ? "No complete solution before timeout; nothing was spent." : "No complete solution; nothing was spent.";
    }

    private void startAll(GuiScreen gui) {
        if (!(gui instanceof GuiContainer) || solver.isRunning() || execution.isBusy()) return;
        EntityPlayer player = GuiOverlayHandler.getPlayer(gui);
        if (player == null || player.inventory.getItemStack() != null) {
            chat(gui, "Place the item currently held by the mouse before using Solve All.");
            return;
        }
        allSlots = new ArrayList<Integer>();
        if (GuiOverlayHandler.getNote(gui) != null) allSlots.add(-1);
        allSlots.addAll(inventoryNotes(gui));
        allIndex = 0;
        allDelay = 0;
        allSolved = 0;
        allSkipped = 0;
        allExecuting = false;
        allWaitingSwapIn = false;
        allWaitingSwapOut = false;
        allNoteInTable = false;
        chat(gui, "Solve All queued " + allSlots.size() + " note(s).");
    }

    private void tickAll(GuiScreen gui) {
        if (allSlots == null || !(gui instanceof GuiContainer)) return;
        if (allWaitingSwapIn) {
            if (noteSwap.isBusy()) return;
            allWaitingSwapIn = false;
            if (!noteSwap.takeSuccess()) {
                abortAll(gui, noteSwap.takeError());
                return;
            }
            allNoteInTable = true;
            allDelay = 4;
            return;
        }
        if (allWaitingSwapOut) {
            if (noteSwap.isBusy()) return;
            allWaitingSwapOut = false;
            if (!noteSwap.takeSuccess()) {
                abortAll(gui, noteSwap.takeError());
                return;
            }
            completeAllItem(allPendingSuccess);
            return;
        }
        if (allExecuting) {
            if (!execution.isFinished()) return;
            boolean success = execution.wasSuccessful();
            execution.resetFinished();
            allExecuting = false;
            finishAll(gui, success);
            return;
        }
        if (solver.isRunning() || execution.isBusy()) return;
        if (allDelay > 0) { allDelay--; return; }
        if (allIndex >= allSlots.size()) {
            chat(gui, "Solve All complete: " + allSolved + " solved, " + allSkipped + " skipped.");
            allSlots = null;
            return;
        }
        int slot = allSlots.get(allIndex);
        if (slot >= 0 && !allNoteInTable) {
            allInventorySlot = slot;
            if (!noteSwap.begin(((GuiContainer) gui).inventorySlots,
                GuiOverlayHandler.getPlayer(gui), allInventorySlot, 1)) {
                abortAll(gui, noteSwap.takeError());
                return;
            }
            allWaitingSwapIn = true;
            return;
        }
        allInventorySlot = -1;
        begin(gui, Request.ALL);
    }

    private void finishAll(GuiScreen gui, boolean success) {
        if (allSlots == null) return;
        if (allInventorySlot >= 0 && gui instanceof GuiContainer) {
            allPendingSuccess = success;
            if (!noteSwap.begin(((GuiContainer) gui).inventorySlots,
                GuiOverlayHandler.getPlayer(gui), allInventorySlot, 1)) {
                abortAll(gui, noteSwap.takeError());
                return;
            }
            allWaitingSwapOut = true;
            return;
        }
        completeAllItem(success);
    }

    private void completeAllItem(boolean success) {
        if (success) allSolved++; else allSkipped++;
        allIndex++;
        allInventorySlot = -1;
        allNoteInTable = false;
        allDelay = 8;
    }

    private void abortAll(GuiScreen gui, String reason) {
        chat(gui, "Solve All stopped: " + (reason == null ? "note transfer failed." : reason));
        allSlots = null;
        allInventorySlot = -1;
        allWaitingSwapIn = false;
        allWaitingSwapOut = false;
        allExecuting = false;
        allNoteInTable = false;
    }

    private static List<Integer> inventoryNotes(GuiScreen gui) {
        List<Integer> result = new ArrayList<Integer>();
        if (!(gui instanceof GuiContainer)) return result;
        for (Object object : ((GuiContainer) gui).inventorySlots.inventorySlots) {
            if (!(object instanceof Slot)) continue;
            Slot slot = (Slot) object;
            ItemStack stack = slot.getStack();
            if (slot.inventory instanceof InventoryPlayer && stack != null && stack.getItem() instanceof ItemResearchNotes) {
                result.add(slot.slotNumber);
            }
        }
        return result;
    }

    private void layout(GuiScreen gui) {
        List<GuiButton> buttons = GuiOverlayHandler.getButtonList(gui);
        if (buttons == null) return;
        int width = actionWidth();
        int action = 0;
        for (GuiButton item : buttons) {
            if (item.id == HANDLE) {
                item.xPosition = paletteX; item.yPosition = paletteY;
                item.displayString = Config.paletteLocked ? "#" : "+";
            } else if (item.id >= SOLVE && item.id <= LAYOUT) {
                item.width = width;
                item.xPosition = Config.paletteVertical ? paletteX + 16 : paletteX + 16 + action * (width + 2);
                item.yPosition = Config.paletteVertical ? paletteY + action * 18 : paletteY;
                action++;
            } else if (item.id == STATUS) {
                item.xPosition = paletteX;
                item.yPosition = Config.paletteVertical ? paletteY + 74 : paletteY + 18;
                item.width = Config.paletteVertical ? width + 16 : width * 4 + 22;
                item.displayString = status();
            }
        }
    }

    private void updateButtons(GuiScreen gui) {
        List<GuiButton> buttons = GuiOverlayHandler.getButtonList(gui);
        if (buttons == null) return;
        boolean busy = solver.isRunning() || execution.isBusy() || allSlots != null;
        boolean note = GuiOverlayHandler.getNote(gui) != null;
        for (GuiButton item : buttons) {
            if (item.id == SOLVE || item.id == PREVIEW) item.enabled = note && !busy;
            else if (item.id == SOLVE_ALL) item.enabled = !busy && (note || !inventoryNotes(gui).isEmpty());
            else if (item.id == HANDLE) item.enabled = !Config.paletteLocked;
            else if (item.id == STATUS) item.enabled = false;
            if (item.id == PREVIEW) item.displayString = Config.paletteCompact
                ? (GuiOverlayHandler.viewSolutionActive ? "H" : "P")
                : (GuiOverlayHandler.viewSolutionActive ? "Hide" : "Preview");
            if (item.id == LAYOUT) item.displayString = Config.paletteCompact ? "L" :
                (Config.paletteLocked ? "Layout" : "Lock Layout");
        }
    }

    private String status() {
        if (solver.isRunning()) return "Solving...";
        if (execution.isBusy() || execution.isFinished()) return execution.getMessage();
        ResearchSolver.SolveResult result = GuiOverlayHandler.lastSolveResult;
        if (result != null && result.complete) return result.inkRequired + " ink | " + result.combinations.size() + " combines";
        return Config.paletteLocked ? "Ready" : "Drag + | right-click Layout";
    }

    private int actionWidth() { return Config.paletteCompact ? 32 : 88; }

    private void clamp(GuiScreen gui) {
        int width = Config.paletteVertical ? actionWidth() + 16 : actionWidth() * 4 + 22;
        int height = Config.paletteVertical ? 89 : 33;
        paletteX = Math.max(2, Math.min(paletteX, gui.width - width - 2));
        paletteY = Math.max(2, Math.min(paletteY, gui.height - height - 2));
    }

    private void save(GuiScreen gui) {
        Config.savePalettePosition(isTweaks(gui), paletteX - GuiOverlayHandler.getGuiLeft(gui),
            paletteY - GuiOverlayHandler.getGuiTop(gui));
    }

    private boolean overLayout(int x, int y) {
        int width = actionWidth();
        int left = Config.paletteVertical ? paletteX + 16 : paletteX + 16 + 3 * (width + 2);
        int top = Config.paletteVertical ? paletteY + 54 : paletteY;
        return inside(x, y, left, top, width, 16);
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static boolean isTweaks(GuiScreen gui) {
        return gui != null && gui.getClass().getName().equals("elan.tweaks.common.gui.ComposableContainerGui");
    }

    private static void chat(GuiScreen gui, String message) {
        EntityPlayer player = GuiOverlayHandler.getPlayer(gui);
        if (player != null) player.addChatMessage(new ChatComponentText("[ResearchSolver] " + message));
    }
}
