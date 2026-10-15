package net.kztmc.mc.cinema;

import net.fabricmc.api.ClientModInitializer;
import net.kztmc.mc.cinema.npc.NpcManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cinema implements ClientModInitializer {
    public static final String MOD_ID = "cinema";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        NpcManager.init();
        CinemaCommands.register();
        LOGGER.info("Cinema initialized");
    }
}
