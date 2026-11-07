package com.luigi.thaumcraft.researchsolver;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

/**
 * Spreads a cursor-based slot exchange across ticks and verifies that the
 * cursor is empty before reporting success. Sending all three clicks in one
 * frame can leave a note attached to the mouse when a container update races
 * the final click.
 */
final class CursorSlotSwapController {
    private static final int CLICK_DELAY_TICKS = 2;
    private static final int TIMEOUT_TICKS = 60;

    private Container container;
    private EntityPlayer player;
    private int fromSlot;
    private int toSlot;
    private ItemStack originalFrom;
    private ItemStack originalTo;
    private int phase;
    private int delay;
    private int elapsed;
    private boolean busy;
    private boolean finished;
    private boolean successful;
    private String error;

    boolean begin(Container nextContainer, EntityPlayer nextPlayer, int nextFromSlot, int nextToSlot) {
        if (busy || nextContainer == null || nextPlayer == null || Minecraft.getMinecraft().playerController == null) {
            return false;
        }
        if (nextPlayer.inventory.getItemStack() != null) {
            error = "The mouse cursor is already carrying an item.";
            finished = true;
            successful = false;
            return false;
        }
        ItemStack source = stack(nextContainer, nextFromSlot);
        ItemStack target = stack(nextContainer, nextToSlot);
        if (source == null && target != null) {
            // Exchanges are symmetric. Starting from the occupied side also
            // handles returning a note when the table was initially empty.
            int swap = nextFromSlot;
            nextFromSlot = nextToSlot;
            nextToSlot = swap;
            source = target;
            target = null;
        }
        if (source == null) {
            error = "Both note-transfer slots became empty.";
            finished = true;
            successful = false;
            return false;
        }
        container = nextContainer;
        player = nextPlayer;
        fromSlot = nextFromSlot;
        toSlot = nextToSlot;
        originalFrom = source.copy();
        originalTo = target == null ? null : target.copy();
        phase = 0;
        delay = 0;
        elapsed = 0;
        busy = true;
        finished = false;
        successful = false;
        error = null;
        return true;
    }

    void tick() {
        if (!busy) return;
        if (++elapsed > TIMEOUT_TICKS) {
            failAndRecover("Timed out while moving a research note.");
            return;
        }
        if (delay > 0) {
            delay--;
            return;
        }
        ItemStack cursor = player.inventory.getItemStack();
        if (phase == 0) {
            click(fromSlot);
            phase = 1;
            delay = CLICK_DELAY_TICKS;
        } else if (phase == 1) {
            if (cursor == null || !same(cursor, originalFrom)) {
                failAndRecover("The server did not pick up the expected research note.");
                return;
            }
            click(toSlot);
            phase = 2;
            delay = CLICK_DELAY_TICKS;
        } else if (phase == 2) {
            // An empty target completes in two clicks. An occupied target puts
            // its previous stack on the cursor and needs the final return click.
            if (cursor != null) click(fromSlot);
            phase = 3;
            delay = CLICK_DELAY_TICKS;
        } else {
            if (cursor != null) {
                failAndRecover("The final note-placement click was not accepted.");
                return;
            }
            if (!same(stack(container, toSlot), originalFrom) || !same(stack(container, fromSlot), originalTo)) {
                failAndRecover("The note slots did not match the requested exchange.");
                return;
            }
            finish(true, null);
        }
    }

    private void failAndRecover(String reason) {
        // At every supported failure point the source slot is the safest place
        // to return the carried stack. Never continue Solve All with an item on
        // the cursor.
        if (player != null && player.inventory.getItemStack() != null && container != null) {
            click(fromSlot);
        }
        if (player != null && player.inventory.getItemStack() != null) {
            reason += " An item is still on the cursor; place it manually before continuing.";
        }
        finish(false, reason);
    }

    private void click(int slot) {
        Minecraft.getMinecraft().playerController.windowClick(container.windowId, slot, 0, 0, player);
    }

    private void finish(boolean success, String reason) {
        busy = false;
        finished = true;
        successful = success;
        error = reason;
    }

    boolean isBusy() { return busy; }

    boolean hasFinished() { return finished; }

    boolean takeSuccess() {
        boolean result = finished && successful;
        finished = false;
        successful = false;
        return result;
    }

    String takeError() {
        String result = error;
        error = null;
        return result;
    }

    private static ItemStack stack(Container container, int slot) {
        try {
            return container.getSlot(slot).getStack();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean same(ItemStack first, ItemStack second) {
        return first == null ? second == null : second != null && ItemStack.areItemStacksEqual(first, second);
    }
}
