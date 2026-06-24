package com.saunhardy.bluepac.forge;

import com.saunhardy.bluepac.BluePAC;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(BluePAC.MOD_ID)
public final class BluePACForge {

    public BluePACForge() {
        BluePAC.init();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        BluePAC.onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        BluePAC.onServerStopping(event.getServer());
    }
}
