package com.luigi.thaumcraft.researchsolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.tiles.TileResearchTable;

/** Creative singleplayer smoke/regression command, intentionally registered last. */
public final class ResearchSolverTestCommand extends CommandBase {
    private final ModernGuiOverlayHandler overlay;

    ResearchSolverTestCommand(ModernGuiOverlayHandler overlay) {
        this.overlay = overlay;
    }

    @Override
    public String getCommandName() { return "tcrstest"; }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/tcrstest <all|solver|ink|ui|live [apply]>";
    }

    @Override
    public int getRequiredPermissionLevel() { return 0; }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        boolean cheats = player != null && minecraft.getIntegratedServer() != null &&
            minecraft.getIntegratedServer().getConfigurationManager().func_152596_g(player.getGameProfile());
        if (player == null || !minecraft.isSingleplayer() || !player.capabilities.isCreativeMode || !cheats) {
            say(sender, "This test command requires a cheats-enabled creative singleplayer world.");
            return;
        }
        String mode = args.length == 0 ? "all" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (mode.equals("solver") || mode.equals("all")) runSolver(sender);
        if (mode.equals("ink") || mode.equals("all")) runInk(sender, minecraft.currentScreen);
        if (mode.equals("ui") || mode.equals("all")) runUi(sender, minecraft.currentScreen);
        if (mode.equals("live")) {
            boolean apply = args.length > 1 && args[1].equalsIgnoreCase("apply");
            runLive(sender, minecraft.currentScreen, apply);
        } else if (mode.equals("all")) {
            runLive(sender, minecraft.currentScreen, false);
        } else if (!mode.equals("solver") && !mode.equals("ink") && !mode.equals("ui")) {
            say(sender, getCommandUsage(sender));
        }
    }

    private static void runSolver(ICommandSender sender) {
        ResearchSolverDiagnostics.Report report = ResearchSolverDiagnostics.runAll();
        for (String message : report.messages) say(sender, message);
        say(sender, "SOLVER: " + report.passed + " passed, " + report.failed + " failed");
    }

    private static void runInk(ICommandSender sender, GuiScreen gui) {
        if (!GuiOverlayHandler.isResearchGui(gui)) {
            say(sender, "INK: SKIP (open a Research Table)");
            return;
        }
        TileResearchTable tile = GuiOverlayHandler.getTileEntity(gui);
        ItemStack current = tile == null ? null : tile.getStackInSlot(0);
        int tableInk = ScribeToolHelper.isScribeTool(current) ? ScribeToolHelper.getRemainingUses(current) : 0;
        int inventoryInk = gui instanceof GuiContainer
            ? ScribeToolHelper.getInventoryInk(((GuiContainer) gui).inventorySlots) : 0;
        say(sender, "INK: table=" + tableInk + ", spare=" + inventoryInk + ", total=" + (tableInk + inventoryInk));
        say(sender, "INK: " + (Config.autoRefillScribingTools ? "auto-swap enabled" : "auto-swap disabled"));
    }

    private static void runUi(ICommandSender sender, GuiScreen gui) {
        say(sender, "UI: " + (Config.paletteVertical ? "vertical" : "horizontal") + ", " +
            (Config.paletteCompact ? "compact" : "expanded") + ", " +
            (Config.paletteLocked ? "locked" : "unlocked") +
            (Config.plainButtonMode ? ", plain buttons" : ", themed buttons"));
        say(sender, "UI profiles: vanilla=" + Config.vanillaPaletteX + "," + Config.vanillaPaletteY +
            " tweaks=" + Config.tweakedPaletteX + "," + Config.tweakedPaletteY);
        say(sender, GuiOverlayHandler.isResearchGui(gui) ? "UI: PASS (palette active; drag/clamp visually inspectable)" :
            "UI: SKIP live bounds check (open a Research Table)");
    }

    private void runLive(ICommandSender sender, GuiScreen gui, boolean apply) {
        if (!GuiOverlayHandler.isResearchGui(gui)) {
            overlay.armLiveTest(apply);
            say(sender, apply ? "LIVE: armed; open a Research Table with a note to apply the test" :
                "LIVE: armed; open a Research Table with a note to run the dry-run test");
            return;
        }
        ResearchNoteData note = GuiOverlayHandler.getNote(gui);
        if (note == null) {
            say(sender, "LIVE: FAIL (put a research note in the table)");
            return;
        }
        if (overlay.startLiveTest(gui, apply)) {
            say(sender, apply ? "LIVE: asynchronous validated solve started" :
                "LIVE: asynchronous dry-run started; the preview/status will show the result");
        } else {
            say(sender, "LIVE: FAIL (solver/executor is busy)");
        }
    }

    private static void say(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText("[TCRSTest] " + text));
    }
}
