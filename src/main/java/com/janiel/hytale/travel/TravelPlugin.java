package com.janiel.hytale.travel;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

public class TravelPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TravelPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("HytaleTravel plugin constructed");
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("HytaleTravel setup start");

        TravelConfig cfg = TravelConfig.load();
        LOGGER.atInfo().log("TravelConfig loaded. proxyHost=" + cfg.getProxyHost()
                + " serverIds=" + cfg.getListenerPorts().keySet());

        getCommandRegistry().registerCommand(new TravelCommand(cfg));

        LOGGER.atInfo().log("HytaleTravel setup done");
    }
}
