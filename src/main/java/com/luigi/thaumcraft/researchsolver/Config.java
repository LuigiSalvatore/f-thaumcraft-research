package com.luigi.thaumcraft.researchsolver;

import net.minecraftforge.common.config.Configuration;
import java.io.File;

public class Config {

    public static Configuration configuration;

    public static int vanillaSolveX = 168;
    public static int vanillaSolveY = 4;
    public static int vanillaViewX = 168;
    public static int vanillaViewY = 22;

    public static int tweakedSolveX = 250;
    public static int tweakedSolveY = 10;
    public static int tweakedViewX = 250;
    public static int tweakedViewY = 28;

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

            tweakedSolveX = configuration.getInt("tweakedSolveX", "position", 250, -1000, 1000, "X offset for Solve button in Tweaked/Custom GUI");
            tweakedSolveY = configuration.getInt("tweakedSolveY", "position", 10, -1000, 1000, "Y offset for Solve button in Tweaked/Custom GUI");
            tweakedViewX = configuration.getInt("tweakedViewX", "position", 250, -1000, 1000, "X offset for View Solution button in Tweaked/Custom GUI");
            tweakedViewY = configuration.getInt("tweakedViewY", "position", 28, -1000, 1000, "Y offset for View Solution button in Tweaked/Custom GUI");
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
