package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.client.config.GuiConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

public class ModConfigGui extends GuiConfig {

    @SuppressWarnings("unchecked")
    public ModConfigGui(GuiScreen parent) {
        super(
            parent,
            new ConfigElement(Config.configuration.getCategory("position")).getChildElements(),
            ThaumcraftResearchSolver.MODID,
            false,
            false,
            GuiConfig.getAbridgedConfigPath(Config.configuration.toString())
        );
    }
}
