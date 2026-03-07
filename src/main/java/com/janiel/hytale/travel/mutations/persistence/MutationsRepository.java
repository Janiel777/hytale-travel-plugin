package com.janiel.hytale.travel.mutations.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.universe.Universe;
import com.janiel.hytale.travel.mutations.MutationsProgression;
import com.janiel.hytale.travel.mutations.weapon.WeaponType;

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


    // -----------------------------------------
    // V2 schema (list of mutations)
    // -----------------------------------------
    private static final int SCHEMA_VERSION_V2 = 2;

    private static final String ROOT_SCHEMA_VERSION = "schemaVersion";
    private static final String ROOT_MUTATIONS = "mutations";

    private static final String MUT_ID = "id";
    private static final String MUT_COUNTER_ID = "counterId";
    private static final String MUT_COUNTER = "counter";
    private static final String MUT_LEVEL = "level";
    private static final String MUT_NOTE = "note";

    // Mutation IDs (stable keys)
    private static final String ID_MINING = "mining";
    private static final String ID_STAMINA = "stamina";

    private static final String ID_WEAPON_SWORD = "weapon_sword";
    private static final String ID_WEAPON_AXE = "weapon_axe";
    private static final String ID_WEAPON_MACE = "weapon_mace";
    private static final String ID_WEAPON_SPEAR = "weapon_spear";
    private static final String ID_WEAPON_DAGGER = "weapon_dagger";
    private static final String ID_WEAPON_BOW = "weapon_bow";
    private static final String ID_WEAPON_CROSSBOW = "weapon_crossbow";
    private static final String ID_WEAPON_GUN = "weapon_gun";
    private static final String ID_WEAPON_MAGIC = "weapon_magic";
    private static final String ID_WEAPON_THROWABLE = "weapon_throwable";

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
                    before.getStaminaDelayLevel(),
                    before.getSwordKills(),
                    before.getAxeKills(),
                    before.getMaceKills(),
                    before.getSpearKills(),
                    before.getDaggerKills(),
                    before.getBowKills(),
                    before.getCrossbowKills(),
                    before.getGunKills(),
                    before.getMagicKills(),
                    before.getThrowableKills(),
                    before.getSwordLevel(),
                    before.getAxeLevel(),
                    before.getMaceLevel(),
                    before.getSpearLevel(),
                    before.getDaggerLevel(),
                    before.getBowLevel(),
                    before.getCrossbowLevel(),
                    before.getGunLevel(),
                    before.getMagicLevel(),
                    before.getThrowableLevel()
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
                    nextDelayLevel,
                    before.getSwordKills(),
                    before.getAxeKills(),
                    before.getMaceKills(),
                    before.getSpearKills(),
                    before.getDaggerKills(),
                    before.getBowKills(),
                    before.getCrossbowKills(),
                    before.getGunKills(),
                    before.getMagicKills(),
                    before.getThrowableKills(),
                    before.getSwordLevel(),
                    before.getAxeLevel(),
                    before.getMaceLevel(),
                    before.getSpearLevel(),
                    before.getDaggerLevel(),
                    before.getBowLevel(),
                    before.getCrossbowLevel(),
                    before.getGunLevel(),
                    before.getMagicLevel(),
                    before.getThrowableLevel()
            );

            saveState(playerUuid, after);
            MutationsCache.put(playerUuid, after);

            return after;
        }
    }

    public static MutationsState incrementWeaponKillAndGetState(UUID playerUuid, WeaponType weaponType) {
        if (weaponType == null || weaponType == WeaponType.UNKNOWN) {
            return getOrLoadState(playerUuid);
        }

        synchronized (LOCK) {
            MutationsState before = MutationsCache.getOrLoad(playerUuid);

            int swordKills = before.getSwordKills();
            int axeKills = before.getAxeKills();
            int maceKills = before.getMaceKills();
            int spearKills = before.getSpearKills();
            int daggerKills = before.getDaggerKills();
            int bowKills = before.getBowKills();
            int crossbowKills = before.getCrossbowKills();
            int gunKills = before.getGunKills();
            int magicKills = before.getMagicKills();
            int throwableKills = before.getThrowableKills();

            int swordLevel = before.getSwordLevel();
            int axeLevel = before.getAxeLevel();
            int maceLevel = before.getMaceLevel();
            int spearLevel = before.getSpearLevel();
            int daggerLevel = before.getDaggerLevel();
            int bowLevel = before.getBowLevel();
            int crossbowLevel = before.getCrossbowLevel();
            int gunLevel = before.getGunLevel();
            int magicLevel = before.getMagicLevel();
            int throwableLevel = before.getThrowableLevel();

            switch (weaponType) {
                case SWORD:
                    swordKills++;
                    swordLevel = MutationsProgression.computeSwordWeaponLevel(swordKills);
                    break;
                case AXE:
                    axeKills++;
                    axeLevel = MutationsProgression.computeAxeWeaponLevel(axeKills);
                    break;
                case MACE:
                    maceKills++;
                    maceLevel = MutationsProgression.computeMaceWeaponLevel(maceKills);
                    break;
                case SPEAR:
                    spearKills++;
                    spearLevel = MutationsProgression.computeSpearWeaponLevel(spearKills);
                    break;
                case DAGGER:
                    daggerKills++;
                    daggerLevel = MutationsProgression.computeDaggerWeaponLevel(daggerKills);
                    break;
                case BOW:
                    bowKills++;
                    bowLevel = MutationsProgression.computeBowWeaponLevel(bowKills);
                    break;
                case CROSSBOW:
                    crossbowKills++;
                    crossbowLevel = MutationsProgression.computeCrossbowWeaponLevel(crossbowKills);
                    break;
                case GUN:
                    gunKills++;
                    gunLevel = MutationsProgression.computeGunWeaponLevel(gunKills);
                    break;
                case MAGIC:
                    magicKills++;
                    magicLevel = MutationsProgression.computeMagicWeaponLevel(magicKills);
                    break;
                case THROWABLE:
                    throwableKills++;
                    throwableLevel = MutationsProgression.computeThrowableWeaponLevel(throwableKills);
                    break;
                default:
                    break;
            }

            MutationsState after = new MutationsState(
                    before.getBlocksBroken(),
                    before.getMiningLevel(),
                    before.getStaminaDepletions(),
                    before.getStaminaDelayLevel(),
                    swordKills,
                    axeKills,
                    maceKills,
                    spearKills,
                    daggerKills,
                    bowKills,
                    crossbowKills,
                    gunKills,
                    magicKills,
                    throwableKills,
                    swordLevel,
                    axeLevel,
                    maceLevel,
                    spearLevel,
                    daggerLevel,
                    bowLevel,
                    crossbowLevel,
                    gunLevel,
                    magicLevel,
                    throwableLevel
            );

            saveState(playerUuid, after);
            MutationsCache.put(playerUuid, after);

            return after;
        }
    }

    static MutationsState loadState(UUID playerUuid) {
        synchronized (LOCK) {
            JsonObject root = loadOrCreate(playerUuid);

            // Only support V2 format for now.
            if (!isV2(root)) {
                MutationsState freshState = new MutationsState(
                        0, 0,
                        0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                );

                JsonObject fresh = buildV2FromState(freshState);
                save(playerUuid, fresh);
                root = fresh;
            }

            return loadStateV2(playerUuid, root);
        }
    }

    // -----------------------------
    // V2 Load/Save
    // -----------------------------

    private static boolean isV2(JsonObject root) {
        if (root == null) {
            return false;
        }
        if (!root.has(ROOT_SCHEMA_VERSION)) {
            return false;
        }
        try {
            return root.get(ROOT_SCHEMA_VERSION).getAsInt() == SCHEMA_VERSION_V2;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static MutationsState loadStateV2(UUID playerUuid, JsonObject root) {
        boolean changed = false;

        JsonArray muts = null;
        if (root.has(ROOT_MUTATIONS) && root.get(ROOT_MUTATIONS).isJsonArray()) {
            muts = root.getAsJsonArray(ROOT_MUTATIONS);
        }
        if (muts == null) {
            muts = new JsonArray();
            root.add(ROOT_MUTATIONS, muts);
            changed = true;
        }

        // Ensure all mutation entries exist (and read values)
        int blocksBroken = ensureV2Mutation(muts, ID_MINING, "blocksBroken", 0,
                "Mine blocks to increase mining mastery.", playerUuid);
        int staminaDepletions = ensureV2Mutation(muts, ID_STAMINA, "staminaDepletions", 0,
                "Fully deplete stamina to improve recovery.", playerUuid);

        int swordKills = ensureV2Mutation(muts, ID_WEAPON_SWORD, "swordKills", 0,
                "Defeat enemies with swords to gain mastery.", playerUuid);
        int axeKills = ensureV2Mutation(muts, ID_WEAPON_AXE, "axeKills", 0,
                "Defeat enemies with axes to gain mastery.", playerUuid);
        int maceKills = ensureV2Mutation(muts, ID_WEAPON_MACE, "maceKills", 0,
                "Defeat enemies with maces to gain mastery.", playerUuid);
        int spearKills = ensureV2Mutation(muts, ID_WEAPON_SPEAR, "spearKills", 0,
                "Defeat enemies with spears to gain mastery.", playerUuid);
        int daggerKills = ensureV2Mutation(muts, ID_WEAPON_DAGGER, "daggerKills", 0,
                "Defeat enemies with daggers to gain mastery.", playerUuid);
        int bowKills = ensureV2Mutation(muts, ID_WEAPON_BOW, "bowKills", 0,
                "Defeat enemies with bows to gain mastery.", playerUuid);
        int crossbowKills = ensureV2Mutation(muts, ID_WEAPON_CROSSBOW, "crossbowKills", 0,
                "Defeat enemies with crossbows to gain mastery.", playerUuid);
        int gunKills = ensureV2Mutation(muts, ID_WEAPON_GUN, "gunKills", 0,
                "Defeat enemies with guns to gain mastery.", playerUuid);
        int magicKills = ensureV2Mutation(muts, ID_WEAPON_MAGIC, "magicKills", 0,
                "Defeat enemies with magic weapons to gain mastery.", playerUuid);
        int throwableKills = ensureV2Mutation(muts, ID_WEAPON_THROWABLE, "throwableKills", 0,
                "Defeat enemies with throwables to gain mastery.", playerUuid);

        // Compute levels from counters (source of truth)
        int computedMiningLevel = MutationsProgression.computeMiningLevel(blocksBroken);
        int computedStaminaDelayLevel = MutationsProgression.computeStaminaDelayLevel(staminaDepletions);

        int computedSwordLevel = MutationsProgression.computeSwordWeaponLevel(swordKills);
        int computedAxeLevel = MutationsProgression.computeAxeWeaponLevel(axeKills);
        int computedMaceLevel = MutationsProgression.computeMaceWeaponLevel(maceKills);
        int computedSpearLevel = MutationsProgression.computeSpearWeaponLevel(spearKills);
        int computedDaggerLevel = MutationsProgression.computeDaggerWeaponLevel(daggerKills);
        int computedBowLevel = MutationsProgression.computeBowWeaponLevel(bowKills);
        int computedCrossbowLevel = MutationsProgression.computeCrossbowWeaponLevel(crossbowKills);
        int computedGunLevel = MutationsProgression.computeGunWeaponLevel(gunKills);
        int computedMagicLevel = MutationsProgression.computeMagicWeaponLevel(magicKills);
        int computedThrowableLevel = MutationsProgression.computeThrowableWeaponLevel(throwableKills);

        // Ensure levels are stored and consistent
        changed |= ensureV2Level(muts, ID_MINING, computedMiningLevel);
        changed |= ensureV2Level(muts, ID_STAMINA, computedStaminaDelayLevel);

        changed |= ensureV2Level(muts, ID_WEAPON_SWORD, computedSwordLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_AXE, computedAxeLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_MACE, computedMaceLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_SPEAR, computedSpearLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_DAGGER, computedDaggerLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_BOW, computedBowLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_CROSSBOW, computedCrossbowLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_GUN, computedGunLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_MAGIC, computedMagicLevel);
        changed |= ensureV2Level(muts, ID_WEAPON_THROWABLE, computedThrowableLevel);

        if (changed) {
            save(playerUuid, root);
        }

        return new MutationsState(
                blocksBroken,
                computedMiningLevel,
                staminaDepletions,
                computedStaminaDelayLevel,
                swordKills,
                axeKills,
                maceKills,
                spearKills,
                daggerKills,
                bowKills,
                crossbowKills,
                gunKills,
                magicKills,
                throwableKills,
                computedSwordLevel,
                computedAxeLevel,
                computedMaceLevel,
                computedSpearLevel,
                computedDaggerLevel,
                computedBowLevel,
                computedCrossbowLevel,
                computedGunLevel,
                computedMagicLevel,
                computedThrowableLevel
        );
    }

    public static void overwriteFromRawJson(UUID playerUuid, String rawJson) {
        if (playerUuid == null) return;

        synchronized (LOCK) {
            ensureDir();

            String safe = (rawJson == null || rawJson.isBlank()) ? "{}" : rawJson;

            Path file = getPlayerFile(playerUuid);
            try {
                Files.createDirectories(file.getParent());

                // Backup if exists
                if (Files.exists(file)) {
                    String ts = String.valueOf(System.currentTimeMillis());
                    Path backup = file.resolveSibling(file.getFileName().toString() + ".bak_" + ts);
                    Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
                }

                Files.writeString(file, safe, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                // Best-effort: keep existing file if write fails
            }
        }
    }

    public static String readRawJson(UUID playerUuid) throws IOException {
        if (playerUuid == null) {
            return "{}";
        }

        synchronized (LOCK) {
            ensureDir();
            Path file = getPlayerFile(playerUuid);
            if (!Files.exists(file)) {
                return "{}";
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        }
    }

    private static int ensureV2Mutation(JsonArray muts, String id, String counterId, int fallback, String note, UUID playerUuid) {
        JsonObject obj = findMutationObj(muts, id);
        if (obj == null) {
            obj = new JsonObject();
            obj.addProperty(MUT_ID, id);
            obj.addProperty(MUT_COUNTER_ID, counterId);
            obj.addProperty(MUT_COUNTER, fallback);
            obj.addProperty(MUT_LEVEL, 0);
            obj.addProperty(MUT_NOTE, note);
            muts.add(obj);
            return fallback;
        }

        // Ensure fields exist
        boolean changed = false;

        if (!obj.has(MUT_COUNTER_ID)) {
            obj.addProperty(MUT_COUNTER_ID, counterId);
            changed = true;
        }
        if (!obj.has(MUT_NOTE)) {
            obj.addProperty(MUT_NOTE, note);
            changed = true;
        }
        if (!obj.has(MUT_COUNTER)) {
            obj.addProperty(MUT_COUNTER, fallback);
            changed = true;
        }

        int value = readInt(obj, MUT_COUNTER, fallback);

        if (changed) {
            // Don't save here; caller decides
        }

        return value;
    }

    private static boolean ensureV2Level(JsonArray muts, String id, int computedLevel) {
        JsonObject obj = findMutationObj(muts, id);
        if (obj == null) {
            return false;
        }

        int stored = readInt(obj, MUT_LEVEL, computedLevel);
        if (stored != computedLevel || !obj.has(MUT_LEVEL)) {
            obj.addProperty(MUT_LEVEL, computedLevel);
            return true;
        }
        return false;
    }

    private static JsonObject findMutationObj(JsonArray muts, String id) {
        for (int i = 0; i < muts.size(); i++) {
            JsonElement e = muts.get(i);
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (!o.has(MUT_ID)) continue;
            try {
                if (id.equals(o.get(MUT_ID).getAsString())) {
                    return o;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static JsonObject buildV2FromState(MutationsState state) {
        JsonObject root = new JsonObject();
        root.addProperty(ROOT_SCHEMA_VERSION, SCHEMA_VERSION_V2);

        JsonArray muts = new JsonArray();

        muts.add(v2Entry(ID_MINING, "blocksBroken", state.getBlocksBroken(), state.getMiningLevel(),
                "Mine blocks to increase mining mastery."));
        muts.add(v2Entry(ID_STAMINA, "staminaDepletions", state.getStaminaDepletions(), state.getStaminaDelayLevel(),
                "Fully deplete stamina to improve recovery."));

        muts.add(v2Entry(ID_WEAPON_SWORD, "swordKills", state.getSwordKills(), state.getSwordLevel(),
                "Defeat enemies with swords to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_AXE, "axeKills", state.getAxeKills(), state.getAxeLevel(),
                "Defeat enemies with axes to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_MACE, "maceKills", state.getMaceKills(), state.getMaceLevel(),
                "Defeat enemies with maces to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_SPEAR, "spearKills", state.getSpearKills(), state.getSpearLevel(),
                "Defeat enemies with spears to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_DAGGER, "daggerKills", state.getDaggerKills(), state.getDaggerLevel(),
                "Defeat enemies with daggers to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_BOW, "bowKills", state.getBowKills(), state.getBowLevel(),
                "Defeat enemies with bows to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_CROSSBOW, "crossbowKills", state.getCrossbowKills(), state.getCrossbowLevel(),
                "Defeat enemies with crossbows to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_GUN, "gunKills", state.getGunKills(), state.getGunLevel(),
                "Defeat enemies with guns to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_MAGIC, "magicKills", state.getMagicKills(), state.getMagicLevel(),
                "Defeat enemies with magic weapons to gain mastery."));
        muts.add(v2Entry(ID_WEAPON_THROWABLE, "throwableKills", state.getThrowableKills(), state.getThrowableLevel(),
                "Defeat enemies with throwables to gain mastery."));

        root.add(ROOT_MUTATIONS, muts);
        return root;
    }

    private static JsonObject v2Entry(String id, String counterId, int counter, int level, String note) {
        JsonObject obj = new JsonObject();
        obj.addProperty(MUT_ID, id);
        obj.addProperty(MUT_COUNTER_ID, counterId);
        obj.addProperty(MUT_COUNTER, counter);
        obj.addProperty(MUT_LEVEL, level);
        obj.addProperty(MUT_NOTE, note);
        return obj;
    }



    private static int readInt(JsonObject obj, String field, int fallback) {
        if (obj != null && obj.has(field) && obj.get(field).isJsonPrimitive()) {
            try {
                return obj.get(field).getAsInt();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static void saveState(UUID playerUuid, MutationsState state) {
        JsonObject root = buildV2FromState(state);
        save(playerUuid, root);
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
            JsonObject fresh = buildV2FromState(new MutationsState(
                    0, 0,
                    0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            ));
            save(playerUuid, fresh);
            return fresh;
        }

        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject parsed = GSON.fromJson(text, JsonObject.class);
            if (parsed == null) {
                JsonObject fresh = buildV2FromState(new MutationsState(
                        0, 0,
                        0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0
                ));
                save(playerUuid, fresh);
                return fresh;
            }
            return parsed;
        } catch (Exception e) {
            JsonObject fresh = buildV2FromState(new MutationsState(
                    0, 0,
                    0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            ));
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