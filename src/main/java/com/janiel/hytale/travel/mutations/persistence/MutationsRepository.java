package com.janiel.hytale.travel.mutations.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.universe.Universe;

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

    private MutationsRepository() {
    }

    public static int getBlocksBroken(UUID playerUuid) {
        synchronized (LOCK) {
            JsonObject obj = loadOrCreate(playerUuid);
            if (obj.has(FIELD_BLOCKS_BROKEN) && obj.get(FIELD_BLOCKS_BROKEN).isJsonPrimitive()) {
                try {
                    return obj.get(FIELD_BLOCKS_BROKEN).getAsInt();
                } catch (Exception ignored) {
                    // Fallthrough to 0 below.
                }
            }
            return 0;
        }
    }

    public static int incrementBlocksBroken(UUID playerUuid) {
        synchronized (LOCK) {
            JsonObject obj = loadOrCreate(playerUuid);

            int current = 0;
            if (obj.has(FIELD_BLOCKS_BROKEN) && obj.get(FIELD_BLOCKS_BROKEN).isJsonPrimitive()) {
                try {
                    current = obj.get(FIELD_BLOCKS_BROKEN).getAsInt();
                } catch (Exception ignored) {
                    current = 0;
                }
            }

            int next = current + 1;
            obj.addProperty(FIELD_BLOCKS_BROKEN, next);

            save(playerUuid, obj);
            return next;
        }
    }

    private static Path getMutationsDir() {
        // Universe.get().getPath() -> carpeta raíz del universe
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
            save(playerUuid, fresh);
            return fresh;
        }

        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject parsed = GSON.fromJson(text, JsonObject.class);
            if (parsed == null) {
                JsonObject fresh = new JsonObject();
                fresh.addProperty(FIELD_BLOCKS_BROKEN, 0);
                save(playerUuid, fresh);
                return fresh;
            }
            if (!parsed.has(FIELD_BLOCKS_BROKEN)) {
                parsed.addProperty(FIELD_BLOCKS_BROKEN, 0);
                save(playerUuid, parsed);
            }
            return parsed;
        } catch (Exception e) {
            // Si el JSON está corrupto o ilegible, lo reseteamos de forma segura
            JsonObject fresh = new JsonObject();
            fresh.addProperty(FIELD_BLOCKS_BROKEN, 0);
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
                // Fallback sin ATOMIC_MOVE (por si el FS no lo soporta)
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