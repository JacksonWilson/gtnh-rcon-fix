package com.rconfix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

@Mod(
    modid = RconFix.MODID,
    name = "RCON Fix",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class RconFix {

    public static final String MODID = "rconfix";
    public static final Logger LOG = LogManager.getLogger("RconFix");
    public static final LoginGuard LOGIN_GUARD = new LoginGuard(System::currentTimeMillis);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RconFixConfig.load(event.getSuggestedConfigurationFile());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ServerThreadQueue.cancelAll();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) ServerThreadQueue.drain();
    }
}
