package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;

/**
 * Main mod entry point for Thaumcraft Research Solver.
 *
 * Key client-only guarantees:
 *   - acceptableRemoteVersions = "*" : FML will not require the server to have this mod.
 *   - @NetworkCheckHandler returning true : FML's own version-handshake check is bypassed
 *     unconditionally, so connecting to a vanilla/GTNH server that has never heard of this
 *     mod will not cause a disconnect.
 *   - All event handlers are registered on the Forge bus from the client-side init only;
 *     no server-side code paths exist in this mod.
 */
@Mod(
    modid   = ThaumcraftResearchSolver.MODID,
    name    = ThaumcraftResearchSolver.NAME,
    version = ThaumcraftResearchSolver.VERSION,
    acceptableRemoteVersions = "*",
    guiFactory = "com.luigi.thaumcraft.researchsolver.ModGuiFactory"
)
public class ThaumcraftResearchSolver {

    public static final String MODID   = "thaumcraftresearchsolver";
    public static final String NAME    = "Thaumcraft Research Solver";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MODID)
    public static ThaumcraftResearchSolver instance;

    // -------------------------------------------------------------------------
    // FML lifecycle
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Load configurations
        Config.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Register both client-side event handlers on the Forge event bus.
        // These are annotated @SideOnly(CLIENT) so they are never loaded on a
        // dedicated server, but as a belt-and-suspenders check: FML will not
        // call this class at all on the logical server because acceptableRemoteVersions="*"
        // and no server-side code is referenced anywhere in this mod.
        MinecraftForge.EVENT_BUS.register(new GuiOverlayHandler());
        MinecraftForge.EVENT_BUS.register(new GuiRenderHandler());

        // Register configuration changed event handler on FML bus
        FMLCommonHandler.instance().bus().register(this);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Nothing needed at post-init.
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.modID.equals(MODID)) {
            Config.syncConfig();
        }
    }

    // -------------------------------------------------------------------------
    // Network version handshake bypass
    // -------------------------------------------------------------------------

    /**
     * Returns true for all incoming mod lists from servers and clients alike.
     * This prevents FML from kicking the player when connecting to a server
     * that does not have this mod installed.
     */
    @NetworkCheckHandler
    public boolean checkModList(Map<String, String> modList, Side side) {
        return true;
    }
}
