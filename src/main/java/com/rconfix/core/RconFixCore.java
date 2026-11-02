package com.rconfix.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Loads the RCON Mixins. The RCON classes are vanilla dedicated-server classes, so they need early Mixins, and they
 * only exist on a dedicated server.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("RconFixCore")
public class RconFixCore implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.rconfix.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        if (!FMLLaunchHandler.side()
            .isServer()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.asList("MixinRConThreadClient", "MixinRConThreadMain");
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
