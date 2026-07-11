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
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger("ThaumcraftResearchSolver")
                .error("[ResearchSolver] Failed to sync config!", e);
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }
}
