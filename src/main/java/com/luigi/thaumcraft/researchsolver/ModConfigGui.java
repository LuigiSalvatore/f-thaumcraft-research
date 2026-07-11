package com.luigi.thaumcraft.researchsolver;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import java.util.ArrayList;
import java.util.List;

public class ModConfigGui extends GuiConfig {

    @SuppressWarnings("rawtypes")
    private static List getConfigElements() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        elements.addAll(new ConfigElement(Config.configuration.getCategory("position")).getChildElements());
        elements.addAll(new ConfigElement(Config.configuration.getCategory("behavior")).getChildElements());
        return elements;
    }

    @SuppressWarnings("unchecked")
    public ModConfigGui(GuiScreen parent) {
        super(
            parent,
            getConfigElements(),
            ThaumcraftResearchSolver.MODID,
            false,
            false,
            GuiConfig.getAbridgedConfigPath(Config.configuration.toString())
        );
    }
}
