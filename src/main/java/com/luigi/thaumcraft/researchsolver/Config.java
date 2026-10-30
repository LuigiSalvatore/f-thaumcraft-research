package com.luigi.thaumcraft.researchsolver;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class Config {

    public static Configuration configuration;

    public static int vanillaSolveX = 168;
    public static int vanillaSolveY = 4;
    public static int vanillaViewX = 168;
    public static int vanillaViewY = 22;
    public static int vanillaSolveAllX = 168;
    public static int vanillaSolveAllY = 40;

    public static int tweakedSolveX = 250;
    public static int tweakedSolveY = 10;
    public static int tweakedViewX = 250;
    public static int tweakedViewY = 28;
    public static int tweakedSolveAllX = 250;
    public static int tweakedSolveAllY = 46;

    public static boolean autoRefillScribingTools = true;
    public static int maxSolveMs = 1500;
    public static int vanillaPaletteX = 168;
    public static int vanillaPaletteY = 4;
    public static int tweakedPaletteX = 250;
    public static int tweakedPaletteY = 10;
    public static boolean paletteVertical = true;
    public static boolean paletteCompact = false;
    public static boolean paletteLocked = true;
    public static boolean plainButtonMode = false;

    public static void load(File file) {
        configuration = new Configuration(file);
        syncConfig();
    }

    public static void syncConfig() {
        try {
            vanillaSolveX = configuration.getInt("vanillaSolveX", "position", 168, -1000, 1000, "X offset for Solve button in vanilla GUI");
            vanillaSolveY = configuration.getInt("vanillaSolveY", "position", 4, -1000, 1000, "Y offset for Solve button in vanilla GUI");
            vanillaViewX = configuration.getInt("vanillaViewX", "position", 168, -1000, 1000, "X offset for View Solution button in vanilla GUI");
            vanillaViewY = configuration.getInt("vanillaViewY", "position", 22, -1000, 1000, "Y offset for View Solution button in vanilla GUI");
            vanillaSolveAllX = configuration.getInt("vanillaSolveAllX", "position", 168, -1000, 1000, "X offset for Solve All button in vanilla GUI");
            vanillaSolveAllY = configuration.getInt("vanillaSolveAllY", "position", 40, -1000, 1000, "Y offset for Solve All button in vanilla GUI");

            tweakedSolveX = configuration.getInt("tweakedSolveX", "position", 250, -1000, 1000, "X offset for Solve button in Tweaked/Custom GUI");
            tweakedSolveY = configuration.getInt("tweakedSolveY", "position", 10, -1000, 1000, "Y offset for Solve button in Tweaked/Custom GUI");
            tweakedViewX = configuration.getInt("tweakedViewX", "position", 250, -1000, 1000, "X offset for View Solution button in Tweaked/Custom GUI");
            tweakedViewY = configuration.getInt("tweakedViewY", "position", 28, -1000, 1000, "Y offset for View Solution button in Tweaked/Custom GUI");
            tweakedSolveAllX = configuration.getInt("tweakedSolveAllX", "position", 250, -1000, 1000, "X offset for Solve All button in Tweaked/Custom GUI");
            tweakedSolveAllY = configuration.getInt("tweakedSolveAllY", "position", 46, -1000, 1000, "Y offset for Solve All button in Tweaked/Custom GUI");

            autoRefillScribingTools = configuration.getBoolean("autoRefillScribingTools", "behavior", true, "Auto-refill scribing tools from player inventory when the table's ink runs dry");
            maxSolveMs = configuration.getInt("maxSolveMs", "behavior", 1500, 100, 30000,
                "Maximum solver time in milliseconds. Only complete solutions are ever applied.");
            vanillaPaletteX = configuration.getInt("vanillaPaletteX", "ui", vanillaSolveX, -2000, 2000,
                "Research Solver palette X offset from the research GUI's top-left anchor");
            vanillaPaletteY = configuration.getInt("vanillaPaletteY", "ui", vanillaSolveY, -2000, 2000,
                "Research Solver palette Y offset from the research GUI's top-left anchor");
            tweakedPaletteX = configuration.getInt("tweakedPaletteX", "ui", tweakedSolveX, -2000, 2000,
                "Research Tweaks palette X offset from its GUI top-left anchor");
            tweakedPaletteY = configuration.getInt("tweakedPaletteY", "ui", tweakedSolveY, -2000, 2000,
                "Research Tweaks palette Y offset from its GUI top-left anchor");
            paletteVertical = configuration.getBoolean("paletteVertical", "ui", true,
                "Lay out palette actions vertically instead of horizontally");
            paletteCompact = configuration.getBoolean("paletteCompact", "ui", false,
                "Use compact action labels");
            paletteLocked = configuration.getBoolean("paletteLocked", "ui", true,
                "Lock the palette position. Use Layout to unlock and drag it.");
            plainButtonMode = configuration.getBoolean("plainButtonMode", "ui", false,
                "Use vanilla buttons for compatibility with GUI-overhaul mods");
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger("ThaumcraftResearchSolver")
                .error("[ResearchSolver] Failed to sync config!", e);
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }

    public static void savePalettePosition(boolean tweaks, int x, int y) {
        if (tweaks) {
            tweakedPaletteX = x;
            tweakedPaletteY = y;
            configuration.get("ui", "tweakedPaletteX", x).set(x);
            configuration.get("ui", "tweakedPaletteY", y).set(y);
        } else {
            vanillaPaletteX = x;
            vanillaPaletteY = y;
            configuration.get("ui", "vanillaPaletteX", x).set(x);
            configuration.get("ui", "vanillaPaletteY", y).set(y);
        }
        configuration.save();
    }

    public static void setPaletteLocked(boolean locked) {
        paletteLocked = locked;
        configuration.get("ui", "paletteLocked", locked).set(locked);
        configuration.save();
    }
}
