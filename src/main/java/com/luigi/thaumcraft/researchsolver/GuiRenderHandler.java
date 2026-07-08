package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.gui.GuiResearchTable;
import thaumcraft.common.lib.research.ResearchManager;
import thaumcraft.common.lib.research.ResearchNoteData;
import thaumcraft.common.lib.utils.HexUtils;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Renders ghost aspect icons (50% alpha) over vacant hexes when the
 * "View Solution" overlay is toggled on.
 */
@SideOnly(Side.CLIENT)
public class GuiRenderHandler {

    private static final Logger LOG = LogManager.getLogger("ThaumcraftResearchSolver");
    private static final int HEX_SIZE = 9;

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!GuiOverlayHandler.isResearchGui(event.gui)) {
            return;
        }
        if (!GuiOverlayHandler.viewSolutionActive) {
            return;
        }
        
        ResearchSolver.SolveResult result = GuiOverlayHandler.lastSolveResult;
        if (result == null || result.placements.isEmpty()) {
            return;
        }

        net.minecraft.client.gui.GuiScreen gui = event.gui;
        ResearchNoteData note = GuiOverlayHandler.getNote(gui);
        if (note == null || note.hexes == null || note.hexEntries == null) {
            return;
        }

        int guiLeft = getGuiLeft(gui);
        int guiTop  = getGuiTop(gui);

        boolean isTweaks = gui.getClass().getName().equals("elan.tweaks.common.gui.ComposableContainerGui");
        // Center offsets of the research area on the parchment relative to guiLeft/guiTop:
        // Vanilla: center at (169, 83)
        // Tweaked: center at (171, 110)
        int centerX = isTweaks ? 171 : 169;
        int centerY = isTweaks ? 110 : 83;

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569f);

        for (Map.Entry<String, Aspect> entry : result.placements.entrySet()) {
            String hexKey = entry.getKey();
            Aspect aspect = entry.getValue();

            // Only render on hexes that are still vacant in the live note data.
            ResearchManager.HexEntry hexEntry = note.hexEntries.get(hexKey);
            if (hexEntry == null || hexEntry.type != 0) {
                continue; // not vacant any more
            }

            HexUtils.Hex hex = note.hexes.get(hexKey);
            if (hex == null) {
                continue;
            }

            HexUtils.Pixel pixel = hex.toPixel(HEX_SIZE);
            int screenX = guiLeft + centerX + (int) pixel.x;
            int screenY = guiTop  + centerY + (int) pixel.y;

            drawGhostAspect(aspect, screenX, screenY);
        }

        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private void drawGhostAspect(Aspect aspect, int cx, int cy) {
        if (aspect == null || aspect.getImage() == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(aspect.getImage());

        GL11.glColor4f(1f, 1f, 1f, 0.5f); // 50% ghost alpha

        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(cx - 8, cy + 8, 0, 0, 1);
        tess.addVertexWithUV(cx + 8, cy + 8, 0, 1, 1);
        tess.addVertexWithUV(cx + 8, cy - 8, 0, 1, 0);
        tess.addVertexWithUV(cx - 8, cy - 8, 0, 0, 0);
        tess.draw();
    }

    private static int getGuiLeft(net.minecraft.client.gui.GuiScreen gui) {
        try {
            Field f = GuiOverlayHandler.f_guiLeft;
            return f != null ? f.getInt(gui) : fallbackGuiLeft();
        } catch (Exception e) {
            return fallbackGuiLeft();
        }
    }

    private static int getGuiTop(net.minecraft.client.gui.GuiScreen gui) {
        try {
            Field f = GuiOverlayHandler.f_guiTop;
            return f != null ? f.getInt(gui) : fallbackGuiTop();
        } catch (Exception e) {
            return fallbackGuiTop();
        }
    }

    private static int fallbackGuiLeft() {
        return (Minecraft.getMinecraft().displayWidth / 2) - 128;
    }

    private static int fallbackGuiTop() {
        return (Minecraft.getMinecraft().displayHeight / 2) - 118;
    }
}
