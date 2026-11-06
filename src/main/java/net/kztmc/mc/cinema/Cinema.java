package net.kztmc.mc.cinema;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.CinemaHud;
import net.kztmc.mc.cinema.ui.CinemaScreen;
import net.kztmc.mc.cinema.ui.panel.world.WorldSettings;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cinema implements ClientModInitializer {
    public static final String MOD_ID = "cinema";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ClientLevel level;

    @Override
    public void onInitializeClient() {
        //npc
        NpcManager.init();
        //command
        CinemaCommands.register();
        //world
        WorldSettings.load();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(WorldSettings::applyOnJoin));
        //hud
        ClientTickEvents.END_CLIENT_TICK.register(CinemaScreen::tickOpen);
        CinemaHud.init();
        //item
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
                .modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(CinemaWand.create()));
        CinemaWand.init();
        //log
        LOGGER.info("Cinema initialized");
    }
}
