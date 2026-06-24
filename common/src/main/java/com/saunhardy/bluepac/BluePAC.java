package com.saunhardy.bluepac;

import com.mojang.logging.LogUtils;
import com.saunhardy.bluepac.platform.Services;

import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;

public final class BluePAC {
    public static final String MOD_ID = "bluepac";
    public static final Logger LOGGER = LogUtils.getLogger();

    private BluePAC() {}

    /** Called once on mod construction, on both loaders. */
    public static void init() {
        Config.load();
    }

    public static void onServerStarting(MinecraftServer server) {
        boolean blueMapLoaded = Services.INSTANCE.isModLoaded("bluemap");
        boolean opacLoaded = Services.INSTANCE.isModLoaded("openpartiesandclaims");

        if (blueMapLoaded && opacLoaded) {
            LOGGER.info("BlueMap and Open Parties and Claims detected, initializing BluePAC...");
            BlueMapIntegration.init(server);
        } else {
            if (!blueMapLoaded) LOGGER.warn("BlueMap not found! BluePAC requires BlueMap.");
            if (!opacLoaded) LOGGER.warn("Open Parties and Claims not found! BluePAC requires OpenPAC.");
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        BlueMapIntegration.shutdown();
    }
}
