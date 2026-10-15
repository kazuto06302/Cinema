package net.kztmc.mc.cinema;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.CinemaScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cinema implements ClientModInitializer {
    public static final String MOD_ID = "cinema";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        NpcManager.init();
        CinemaCommands.register();
        ClientTickEvents.END_CLIENT_TICK.register(CinemaScreen::tickOpen);
        LOGGER.info("Cinema initialized");
    }
}
