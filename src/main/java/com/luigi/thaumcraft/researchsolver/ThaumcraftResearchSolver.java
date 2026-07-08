package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.Map;

@Mod(
    modid = ThaumcraftResearchSolver.MODID,
    name = ThaumcraftResearchSolver.NAME,
    version = ThaumcraftResearchSolver.VERSION,
    acceptableRemoteVersions = "*"
)
public class ThaumcraftResearchSolver {
    public static final String MODID = "thaumcraftresearchsolver";
    public static final String NAME = "Thaumcraft Research Solver";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MODID)
    public static ThaumcraftResearchSolver instance;

    @NetworkCheckHandler
    public boolean checkModList(Map<String, String> modList, Side side) {
        return true;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
    }
}
