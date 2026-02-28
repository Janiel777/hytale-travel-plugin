package com.janiel.hytale.travel.mutations.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.universe.Universe;
import com.janiel.hytale.travel.mutations.MutationsProgression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class MutationsRepository {

    private static final Object LOCK = new Object();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static final String FIELD_BLOCKS_BROKEN = "blocksBroken";
    private static final String FIELD_MINING_LEVEL = "miningLevel";

    private static final String FIELD_STAMINA_DEPLETIONS = "staminaDepletions";
    private static final String FIELD_STAMINA_DELAY_LEVEL = "staminaDelayLevel";

    private MutationsRepository() {
    }

    public static MutationsState getOrLoadState(UUID playerUuid) {
        return MutationsCache.getOrLoad(playerUuid);
    }

    public static int getBlocksBroken(UUID playerUuid) {
        return getOrLoadState(playerUuid).getBlocksBroken();
    }

    public static int getMiningLevel(UUID playerUuid) {
        return getOrLoadState(playerUuid).getMiningLevel();
    }

    public static int getStaminaDepletions(UUID playerUuid) {
        return getOrLoadState(playerUuid).getStaminaDepletions();
    }

    public static int getStaminaDelayLevel(UUID playerUuid) {
        return getOrLoadState(playerUuid).getStaminaDelayLevel();
    }

    public static int incrementBlocksBroken(UUID playerUuid) {
        return incrementBlocksBrokenAndGetState(playerUuid).getBlocksBroken();
    }

    public static MutationsState incrementBlocksBrokenAndGetState(UUID playerUuid) {
        synchronized (LOCK) {
            MutationsState before = MutationsCache.getOrLoad(playerUuid);

            int nextBlocksBroken = before.getBlocksBroken() + 1;
            int nextMiningLevel = MutationsProgression.computeMiningLevel(nextBlocksBroken);

            MutationsState after = new MutationsState(
                    nextBlocksBroken,
                    nextMiningLevel,
                    before.getStaminaDepletions(),
                    before.getStaminaDelayLevel()
            );

            saveState(playerUuid, after);
            MutationsCache.put(playerUuid, after);

            return after;
        }
    }

    public static MutationsState incrementStaminaDepletionsAndGetState(UUID playerUuid) {
        synchronized (LOCK) {
            MutationsState before = MutationsCache.getOrLoad(playerUuid);

            int nextDepletions = before.getStaminaDepletions() + 1;
            int nextDelayLevel = MutationsProgression.computeStaminaDelayLevel(nextDepletions);

            MutationsState after = new MutationsState(
                    before.getBlocksBroken(),
                    before.getMiningLevel(),
                    nextDepletions,
                    nextDelayLevel
            );

            saveState(playerUuid, after);
            MutationsCache.put(playerUuid, after);

            return after;
        }
    }

    // Package-private: usado por MutationsCache para cargar 1 vez desde disco
    static MutationsState loadState(UUID playerUuid) {
        synchronized (LOCK) {
            JsonObject obj = loadOrCreate(playerUuid);

            int blocksBroken = readInt(obj, FIELD_BLOCKS_BROKEN, 0);
            int computedMiningLevel = MutationsProgression.computeMiningLevel(blocksBroken);

            int miningLevel = readInt(obj, FIELD_MINING_LEVEL, computedMiningLevel);
            if (miningLevel != computedMiningLevel) {
                // Mantener archivo consistente con la progresión actual
                miningLevel = computedMiningLevel;
                obj.addProperty(FIELD_MINING_LEVEL, miningLevel);
                save(playerUuid, obj);
            } else if (!obj.has(FIELD_MINING_LEVEL)) {
                obj.addProperty(FIELD_MINING_LEVEL, miningLevel);
                save(playerUuid, obj);
            }

            if (!obj.has(FIELD_BLOCKS_BROKEN)) {
                obj.addProperty(FIELD_BLOCKS_BROKEN, blocksBroken);
                save(playerUuid, obj);
            }

            int staminaDepletions = readInt(obj, FIELD_STAMINA_DEPLETIONS, 0);
            int computedStaminaDelayLevel = MutationsProgression.computeStaminaDelayLevel(staminaDepletions);

            int staminaDelayLevel = readInt(obj, FIELD_STAMINA_DELAY_LEVEL, computedStaminaDelayLevel);
            if (staminaDelayLevel != computedStaminaDelayLevel) {
                staminaDelayLevel = computedStaminaDelayLevel;
                obj.addProperty(FIELD_STAMINA_DELAY_LEVEL, staminaDelayLevel);
                save(playerUuid, obj);
            } else if (!obj.has(FIELD_STAMINA_DELAY_LEVEL)) {
                obj.addProperty(FIELD_STAMINA_DELAY_LEVEL, staminaDelayLevel);
                save(playerUuid, obj);
            }

            if (!obj.has(FIELD_STAMINA_DEPLETIONS)) {
                obj.addProperty(FIELD_STAMINA_DEPLETIONS, staminaDepletions);
                save(playerUuid, obj);
            }

            return new MutationsState(blocksBroken, miningLevel, staminaDepletions, staminaDelayLevel);
        }
    }

    private static int readInt(JsonObject obj, String field, int fallback) {
        if (obj.has(field) && obj.get(field).isJsonPrimitive()) {
            try {
                return obj.get(field).getAsInt();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static void saveState(UUID playerUuid, MutationsState state) {
        JsonObject obj = new JsonObject();
        obj.addProperty(FIELD_BLOCKS_BROKEN, state.getBlocksBroken());
        obj.addProperty(FIELD_MINING_LEVEL, state.getMiningLevel());
        obj.addProperty(FIELD_STAMINA_DEPLETIONS, state.getStaminaDepletions());
        obj.addProperty(FIELD_STAMINA_DELAY_LEVEL, state.getStaminaDelayLevel());
        save(playerUuid, obj);
    }

    private static Path getMutationsDir() {
        return Universe.get().getPath().resolve("mutations");
    }

    private static Path getPlayerFile(UUID playerUuid) {
        return getMutationsDir().resolve(playerUuid.toString() + ".json");
    }

    private static JsonObject loadOrCreate(UUID playerUuid) {
        ensureDir();

        Path file = getPlayerFile(playerUuid);
        if (!Files.exists(file)) {
            JsonObject fresh = new JsonObject();
            fresh.addProperty(FIELD_BLOCKS_BROKEN, 0);
            fresh.addProperty(FIELD_MINING_LEVEL, 0);
            fresh.addProperty(FIELD_STAMINA_DEPLETIONS, 0);
            fresh.addProperty(FIELD_STAMINA_DELAY_LEVEL, 0);
            save(playerUuid, fresh);
            return fresh;
        }

        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject parsed = GSON.fromJson(text, JsonObject.class);
            if (parsed == null) {
                JsonObject fresh = new JsonObject();
                fresh.addProperty(FIELD_BLOCKS_BROKEN, 0);
                fresh.addProperty(FIELD_MINING_LEVEL, 0);
                fresh.addProperty(FIELD_STAMINA_DEPLETIONS, 0);
                fresh.addProperty(FIELD_STAMINA_DELAY_LEVEL, 0);
                save(playerUuid, fresh);
                return fresh;
            }
            return parsed;
        } catch (Exception e) {
            JsonObject fresh = new JsonObject();
            fresh.addProperty(FIELD_BLOCKS_BROKEN, 0);
            fresh.addProperty(FIELD_MINING_LEVEL, 0);
            fresh.addProperty(FIELD_STAMINA_DEPLETIONS, 0);
            fresh.addProperty(FIELD_STAMINA_DELAY_LEVEL, 0);
            save(playerUuid, fresh);
            return fresh;
        }
    }

    private static void save(UUID playerUuid, JsonObject obj) {
        ensureDir();

        Path file = getPlayerFile(playerUuid);
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");

        String json = GSON.toJson(obj);

        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveNotSupported) {
            try {
                Files.writeString(file, json, StandardCharsets.UTF_8);
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to save mutations file for player " + playerUuid + ": " + e, e);
            }
        }
    }

    private static void ensureDir() {
        Path dir = getMutationsDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create mutations directory at " + dir + ": " + e, e);
        }
    }
}