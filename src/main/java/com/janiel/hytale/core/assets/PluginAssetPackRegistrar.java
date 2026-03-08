package com.janiel.hytale.core.assets;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

public final class PluginAssetPackRegistrar {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PluginAssetPackRegistrar() {}

    public static void registerSelfAsAssetPack(JavaPlugin plugin) {
        try {
            // Unique pack name (stable)
            String packName = plugin.getIdentifier().toString() + ":assets";

            AssetModule assets = AssetModule.get();
            assets.registerPack(packName, plugin.getFile(), plugin.getManifest(), true);

            // Important: this triggers the pending asset stores to ingest new assets.
            assets.initPendingStores();

            LOGGER.atInfo().log("Registered plugin asset pack: " + packName + " path=" + plugin.getFile());
        } catch (Throwable t) {
            plugin.getLogger().atWarning().log("Failed to register plugin asset pack: " + t);
        }
    }
}
