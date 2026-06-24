package com.saunhardy.bluepac.fabric;

import com.saunhardy.bluepac.BluePAC;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class BluePACFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        BluePAC.init();

        ServerLifecycleEvents.SERVER_STARTING.register(BluePAC::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(BluePAC::onServerStopping);
    }
}
