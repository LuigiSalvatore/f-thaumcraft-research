package com.luigi.thaumcraft.researchsolver;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import thaumcraft.api.IScribeTools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScribeToolHelper {

    public static class ScribeToolCandidate {
        public final int containerSlotIndex;
        public final ItemStack stack;
        public final int remainingUses;
        public final int maxDamage;

        public ScribeToolCandidate(int containerSlotIndex, ItemStack stack, int remainingUses, int maxDamage) {
            this.containerSlotIndex = containerSlotIndex;
            this.stack = stack;
            this.remainingUses = remainingUses;
            this.maxDamage = maxDamage;
        }
    }

    public static boolean isScribeTool(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IScribeTools;
    }

    public static int getRemainingUses(ItemStack stack) {
        if (stack == null) return 0;
        return stack.getMaxDamage() - stack.getItemDamage();
    }

    public static List<ScribeToolCandidate> findScribeTools(Container container) {
        List<ScribeToolCandidate> candidates = new ArrayList<ScribeToolCandidate>();
        if (container == null || container.inventorySlots == null) {
            return candidates;
        }
        for (Object obj : container.inventorySlots) {
            if (obj instanceof Slot) {
                Slot slot = (Slot) obj;
                if (slot.inventory instanceof InventoryPlayer) {
                    ItemStack stack = slot.getStack();
                    if (stack != null && isScribeTool(stack)) {
                        int remaining = getRemainingUses(stack);
                        if (remaining > 0) {
                            candidates.add(new ScribeToolCandidate(
                                slot.slotNumber,
                                stack,
                                remaining,
                                stack.getMaxDamage()
                            ));
                        }
                    }
                }
            }
        }
        return candidates;
    }

    public static ScribeToolCandidate getBestScribeTool(Container container) {
        List<ScribeToolCandidate> candidates = findScribeTools(container);
        if (candidates.isEmpty()) {
            return null;
        }

        // Sort candidates:
        // 1. Lower capacity first (maxDamage ASC - lower tier first)
        // 2. Partially-used first (remaining uses ASC - within the same tier)
        Collections.sort(candidates, new Comparator<ScribeToolCandidate>() {
            @Override
            public int compare(ScribeToolCandidate a, ScribeToolCandidate b) {
                if (a.maxDamage != b.maxDamage) {
                    return Integer.compare(a.maxDamage, b.maxDamage);
                }
                return Integer.compare(a.remainingUses, b.remainingUses);
            }
        });

        return candidates.get(0);
    }

    /** Total usable ink in player-inventory tools visible through this container. */
    public static int getInventoryInk(Container container) {
        int total = 0;
        for (ScribeToolCandidate candidate : findScribeTools(container)) {
            total += candidate.remainingUses;
        }
        return total;
    }

    /**
     * Prefer finishing the smallest partially-used tool first. This keeps the
     * number of stranded, almost-empty inkwells low without ever starting a
     * transaction that cannot finish.
     */
    public static ScribeToolCandidate getNextScribeTool(Container container) {
        List<ScribeToolCandidate> candidates = findScribeTools(container);
        if (candidates.isEmpty()) return null;
        Collections.sort(candidates, new Comparator<ScribeToolCandidate>() {
            @Override
            public int compare(ScribeToolCandidate a, ScribeToolCandidate b) {
                int result = Integer.compare(a.remainingUses, b.remainingUses);
                if (result != 0) return result;
                return Integer.compare(a.maxDamage, b.maxDamage);
            }
        });
        return candidates.get(0);
    }

    public static boolean swapSlots(Container container, EntityPlayer player, int fromSlotIndex, int toSlotIndex) {
        if (container == null || player == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.playerController == null) {
            return false;
        }
        if (player.inventory.getItemStack() != null) {
            return false;
        }
        int windowId = container.windowId;
        mc.playerController.windowClick(windowId, fromSlotIndex, 0, 0, player);
        if (player.inventory.getItemStack() == null) {
            return false;
        }
        mc.playerController.windowClick(windowId, toSlotIndex, 0, 0, player);
        if (player.inventory.getItemStack() != null) {
            mc.playerController.windowClick(windowId, fromSlotIndex, 0, 0, player);
        }
        return player.inventory.getItemStack() == null;
    }

    public static boolean performSwap(Container container, EntityPlayer player, int fromSlotIndex) {
        return swapSlots(container, player, fromSlotIndex, 0);
    }
}
