package com.luigi.thaumcraft.researchsolver;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.network.PacketHandler;
import thaumcraft.common.lib.network.playerdata.PacketAspectCombinationToServer;
import thaumcraft.common.lib.network.playerdata.PacketAspectPlaceToServer;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.lib.utils.HexUtils;
import thaumcraft.common.tiles.TileResearchTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Applies one complete solution conservatively, acknowledging every mutation. */
final class SolveExecutionController {

    enum Phase {
        IDLE("Ready"), COMBINING("Combining"), PLACING("Placing"), VERIFYING("Verifying"),
        COMPLETE("Complete"), FAILED("Failed"), CANCELLED("Cancelled");

        final String label;
        Phase(String label) { this.label = label; }
    }

    private static final int ACK_TIMEOUT_TICKS = 100;

    private Phase phase = Phase.IDLE;
    private ResearchSolver.SolveResult plan;
    private List<Map.Entry<String, Aspect>> placements;
    private int combinationIndex;
    private int placementIndex;
    private int waitTicks;
    private boolean awaitingAck;
    private int observedResultAmount;
    private String noteKey;
    private String message = "Ready";

    boolean start(GuiScreen gui, ResearchSolver.SolveResult result) {
        if (isBusy()) return false;
        EntityPlayer player = GuiOverlayHandler.getPlayer(gui);
        TileResearchTable tile = GuiOverlayHandler.getTileEntity(gui);
        ResearchNoteData note = GuiOverlayHandler.getNote(gui);
        if (player == null || tile == null || note == null || result == null || !result.complete) {
            fail(player, "No complete solution is available.");
            return false;
        }
        if (!ResourceAwareResearchSolver.validateComplete(note, result.placements)) {
            fail(player, "The planned layout failed local validation; nothing was spent.");
            return false;
        }
        int availableInk = tableInk(tile);
        if (gui instanceof GuiContainer && Config.autoRefillScribingTools) {
            availableInk += ScribeToolHelper.getInventoryInk(((GuiContainer) gui).inventorySlots);
        }
        if (availableInk < result.inkRequired) {
            fail(player, "Need " + result.inkRequired + " ink, but only " + availableInk + " is accessible.");
            return false;
        }

        plan = result;
        placements = new ArrayList<Map.Entry<String, Aspect>>(result.placements.entrySet());
        combinationIndex = 0;
        placementIndex = 0;
        waitTicks = 0;
        awaitingAck = false;
        noteKey = note.key;
        phase = result.combinations.isEmpty() ? Phase.PLACING : Phase.COMBINING;
        message = phase.label;
        return true;
    }

    void tick(GuiScreen gui) {
        if (!isBusy()) return;
        EntityPlayer player = GuiOverlayHandler.getPlayer(gui);
        TileResearchTable tile = GuiOverlayHandler.getTileEntity(gui);
        ResearchNoteData note = GuiOverlayHandler.getNote(gui);
        if (player == null || tile == null || note == null || !same(noteKey, note.key)) {
            cancel(player, "Research note or table changed; transaction stopped.");
            return;
        }
        if (phase == Phase.COMBINING) tickCombination(player, tile);
        else if (phase == Phase.PLACING) tickPlacement(gui, player, tile, note);
        else if (phase == Phase.VERIFYING) tickVerification(player, note);
    }

    private void tickCombination(EntityPlayer player, TileResearchTable tile) {
        if (combinationIndex >= plan.combinations.size()) {
            awaitingAck = false;
            phase = Phase.PLACING;
            message = "Placing 0/" + placements.size();
            return;
        }
        ResearchSolver.Combination combination = plan.combinations.get(combinationIndex);
        AspectList pool = playerPool(player);
        int now = pool.getAmount(combination.result);
        if (awaitingAck) {
            if (now > observedResultAmount) {
                combinationIndex++;
                awaitingAck = false;
                waitTicks = 0;
                message = "Combining " + combinationIndex + "/" + plan.combinations.size();
            } else if (++waitTicks > ACK_TIMEOUT_TICKS) {
                fail(player, "Aspect combination was not acknowledged by the server.");
            }
            return;
        }
        observedResultAmount = now;
        PacketHandler.INSTANCE.sendToServer(new PacketAspectCombinationToServer(
            player, tile.xCoord, tile.yCoord, tile.zCoord,
            combination.first, combination.second,
            combination.firstFromBonus, combination.secondFromBonus, false));
        awaitingAck = true;
        waitTicks = 0;
        message = "Combining " + (combinationIndex + 1) + "/" + plan.combinations.size();
    }

    private void tickPlacement(
        GuiScreen gui, EntityPlayer player, TileResearchTable tile, ResearchNoteData note) {
        if (placementIndex >= placements.size()) {
            awaitingAck = false;
            phase = Phase.VERIFYING;
            waitTicks = 0;
            message = "Verifying completion";
            return;
        }
        Map.Entry<String, Aspect> placement = placements.get(placementIndex);
        ResearchManager.HexEntry live = note.hexEntries.get(placement.getKey());
        if (awaitingAck) {
            if (live != null && live.type == 2 && live.aspect == placement.getValue()) {
                placementIndex++;
                awaitingAck = false;
                waitTicks = 0;
                message = "Placing " + placementIndex + "/" + placements.size();
            } else if (++waitTicks > ACK_TIMEOUT_TICKS) {
                fail(player, "Aspect placement was not acknowledged by the server.");
            }
            return;
        }
        if (tableInk(tile) <= 0 && !swapNextTool(gui, player)) {
            fail(player, "The reserved scribing-tool ink became unavailable.");
            return;
        }
        HexUtils.Hex hex = note.hexes.get(placement.getKey());
        if (hex == null) {
            fail(player, "The research layout changed before placement.");
            return;
        }
        PacketHandler.INSTANCE.sendToServer(new PacketAspectPlaceToServer(
            player, (byte) hex.q, (byte) hex.r, tile.xCoord, tile.yCoord, tile.zCoord,
            placement.getValue()));
        awaitingAck = true;
        waitTicks = 0;
        message = "Placing " + (placementIndex + 1) + "/" + placements.size();
    }

    private void tickVerification(EntityPlayer player, ResearchNoteData note) {
        if (note.complete || ResourceAwareResearchSolver.validateComplete(note, java.util.Collections.<String, Aspect>emptyMap())) {
            phase = Phase.COMPLETE;
            message = "Complete - " + plan.inkRequired + " ink used";
            return;
        }
        if (++waitTicks > ACK_TIMEOUT_TICKS) {
            fail(player, "Placements arrived, but the note did not become complete.");
        }
    }

    private boolean swapNextTool(GuiScreen gui, EntityPlayer player) {
        if (!Config.autoRefillScribingTools || !(gui instanceof GuiContainer)) return false;
        Container container = ((GuiContainer) gui).inventorySlots;
        ScribeToolHelper.ScribeToolCandidate candidate = ScribeToolHelper.getNextScribeTool(container);
        return candidate != null && ScribeToolHelper.performSwap(container, player, candidate.containerSlotIndex);
    }

    void cancel(EntityPlayer player, String reason) {
        if (!isBusy()) return;
        phase = Phase.CANCELLED;
        message = reason;
        if (player != null) player.addChatMessage(new ChatComponentText("[ResearchSolver] " + reason));
    }

    private void fail(EntityPlayer player, String reason) {
        phase = Phase.FAILED;
        message = reason;
        if (player != null) player.addChatMessage(new ChatComponentText("[ResearchSolver] " + reason));
    }

    boolean isBusy() {
        return phase == Phase.COMBINING || phase == Phase.PLACING || phase == Phase.VERIFYING;
    }

    boolean wasSuccessful() { return phase == Phase.COMPLETE; }
    boolean isFinished() { return phase == Phase.COMPLETE || phase == Phase.FAILED || phase == Phase.CANCELLED; }
    Phase getPhase() { return phase; }
    String getMessage() { return message; }
    int getProgressCurrent() { return phase == Phase.COMBINING ? combinationIndex : placementIndex; }
    int getProgressTotal() { return phase == Phase.COMBINING ? plan.combinations.size() : placements == null ? 0 : placements.size(); }

    void resetFinished() {
        if (isFinished()) {
            phase = Phase.IDLE;
            message = "Ready";
        }
    }

    private static int tableInk(TileResearchTable tile) {
        ItemStack stack = tile == null ? null : tile.getStackInSlot(0);
        return ScribeToolHelper.isScribeTool(stack) ? ScribeToolHelper.getRemainingUses(stack) : 0;
    }

    private static AspectList playerPool(EntityPlayer player) {
        AspectList result = Thaumcraft.proxy.getPlayerKnowledge().getAspectsDiscovered(player.getCommandSenderName());
        return result == null ? new AspectList() : result;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
