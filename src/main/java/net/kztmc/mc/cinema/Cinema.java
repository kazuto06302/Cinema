package net.kztmc.mc.cinema;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kztmc.mc.cinema.npc.NpcManager;
import net.kztmc.mc.cinema.ui.CinemaHud;
import net.kztmc.mc.cinema.ui.CinemaScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cinema implements ClientModInitializer {
    public static final String MOD_ID = "cinema";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ClientLevel level;

    @Override
    public void onInitializeClient() {

        NpcManager.init();
        CinemaCommands.register();
        ClientTickEvents.END_CLIENT_TICK.register(CinemaScreen::tickOpen);
        CinemaHud.init();

        LOGGER.info("Cinema initialized");
    }
}
