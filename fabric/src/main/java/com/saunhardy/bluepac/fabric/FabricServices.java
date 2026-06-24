package com.saunhardy.bluepac.fabric;

import com.saunhardy.bluepac.platform.Services;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class FabricServices implements Services {
    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
