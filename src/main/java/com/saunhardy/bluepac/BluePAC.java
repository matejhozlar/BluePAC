package com.saunhardy.bluepac;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(BluePAC.MODID)
public class BluePAC {
    public static final String MODID = "bluepac";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BluePAC(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        boolean blueMapLoaded = ModList.get().isLoaded("bluemap");
        boolean opacLoaded = ModList.get().isLoaded("openpartiesandclaims");

        if (blueMapLoaded && opacLoaded) {
            LOGGER.info("BlueMap and Open Parties and Claims detected, initializing BluePAC...");
            BlueMapIntegration.init(event.getServer());
        } else {
            if (!blueMapLoaded) LOGGER.warn("BlueMap not found! BluePAC requires BlueMap.");
            if (!opacLoaded) LOGGER.warn("Open Parties and Claims not found! BluePAC requires OpenPAC.");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BlueMapIntegration.shutdown();
    }
}
