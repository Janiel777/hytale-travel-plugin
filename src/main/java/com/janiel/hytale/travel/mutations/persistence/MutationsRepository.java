package com.janiel.hytale.travel.mutations.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    private static final String FIELD_BLOCKS_BROKEN = "blocksBroken";
    private static final String FIELD_MINING_LEVEL = "miningLevel";

    private static final String FIELD_STAMINA_DEPLETIONS = "staminaDepletions";
    private static final String FIELD_STAMINA_DELAY_LEVEL = "staminaDelayLevel";

    // Weapon mutations (kills)
    private static final String FIELD_SWORD_KILLS = "swordKills";
    private static final String FIELD_AXE_KILLS = "axeKills";
    private static final String FIELD_MACE_KILLS = "maceKills";
    private static final String FIELD_SPEAR_KILLS = "spearKills";
    private static final String FIELD_DAGGER_KILLS = "daggerKills";
    private static final String FIELD_BOW_KILLS = "bowKills";
    private static final String FIELD_CROSSBOW_KILLS = "crossbowKills";
    private static final String FIELD_GUN_KILLS = "gunKills";
    private static final String FIELD_MAGIC_KILLS = "magicKills";
    private static final String FIELD_THROWABLE_KILLS = "throwableKills";

    // Weapon mutations (levels)
    private static final String FIELD_SWORD_LEVEL = "swordLevel";
    private static final String FIELD_AXE_LEVEL = "axeLevel";
    private static final String FIELD_MACE_LEVEL = "maceLevel";
    private static final String FIELD_SPEAR_LEVEL = "spearLevel";
    private static final String FIELD_DAGGER_LEVEL = "daggerLevel";
    private static final String FIELD_BOW_LEVEL = "bowLevel";
    private static final String FIELD_CROSSBOW_LEVEL = "crossbowLevel";
    private static final String FIELD_GUN_LEVEL = "gunLevel";
    private static final String FIELD_MAGIC_LEVEL = "magicLevel";
    private static final String FIELD_THROWABLE_LEVEL = "throwableLevel";

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

    // Package-private: usado por MutationsCache para cargar 1 vez desde disco
    static MutationsState loadState(UUID playerUuid) {
        synchronized (LOCK) {
            JsonObject obj = loadOrCreate(playerUuid);

            int blocksBroken = readInt(obj, FIELD_BLOCKS_BROKEN, 0);
            int computedMiningLevel = MutationsProgression.computeMiningLevel(blocksBroken);

            int miningLevel = readInt(obj, FIELD_MINING_LEVEL, computedMiningLevel);
            if (miningLevel != computedMiningLevel) {
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

            // Kills (ensure defaults)
            int swordKills = ensureInt(obj, playerUuid, FIELD_SWORD_KILLS, 0);
            int axeKills = ensureInt(obj, playerUuid, FIELD_AXE_KILLS, 0);
            int maceKills = ensureInt(obj, playerUuid, FIELD_MACE_KILLS, 0);
            int spearKills = ensureInt(obj, playerUuid, FIELD_SPEAR_KILLS, 0);
            int daggerKills = ensureInt(obj, playerUuid, FIELD_DAGGER_KILLS, 0);
            int bowKills = ensureInt(obj, playerUuid, FIELD_BOW_KILLS, 0);
            int crossbowKills = ensureInt(obj, playerUuid, FIELD_CROSSBOW_KILLS, 0);
            int gunKills = ensureInt(obj, playerUuid, FIELD_GUN_KILLS, 0);
            int magicKills = ensureInt(obj, playerUuid, FIELD_MAGIC_KILLS, 0);
            int throwableKills = ensureInt(obj, playerUuid, FIELD_THROWABLE_KILLS, 0);

            // Levels (computed from kills; persisted for backward compatibility + UI)
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

            int swordLevel = readInt(obj, FIELD_SWORD_LEVEL, computedSwordLevel);
            int axeLevel = readInt(obj, FIELD_AXE_LEVEL, computedAxeLevel);
            int maceLevel = readInt(obj, FIELD_MACE_LEVEL, computedMaceLevel);
            int spearLevel = readInt(obj, FIELD_SPEAR_LEVEL, computedSpearLevel);
            int daggerLevel = readInt(obj, FIELD_DAGGER_LEVEL, computedDaggerLevel);
            int bowLevel = readInt(obj, FIELD_BOW_LEVEL, computedBowLevel);
            int crossbowLevel = readInt(obj, FIELD_CROSSBOW_LEVEL, computedCrossbowLevel);
            int gunLevel = readInt(obj, FIELD_GUN_LEVEL, computedGunLevel);
            int magicLevel = readInt(obj, FIELD_MAGIC_LEVEL, computedMagicLevel);
            int throwableLevel = readInt(obj, FIELD_THROWABLE_LEVEL, computedThrowableLevel);

            boolean levelMismatch = false;
            levelMismatch |= (swordLevel != computedSwordLevel);
            levelMismatch |= (axeLevel != computedAxeLevel);
            levelMismatch |= (maceLevel != computedMaceLevel);
            levelMismatch |= (spearLevel != computedSpearLevel);
            levelMismatch |= (daggerLevel != computedDaggerLevel);
            levelMismatch |= (bowLevel != computedBowLevel);
            levelMismatch |= (crossbowLevel != computedCrossbowLevel);
            levelMismatch |= (gunLevel != computedGunLevel);
            levelMismatch |= (magicLevel != computedMagicLevel);
            levelMismatch |= (throwableLevel != computedThrowableLevel);

            if (levelMismatch) {
                swordLevel = computedSwordLevel;
                axeLevel = computedAxeLevel;
                maceLevel = computedMaceLevel;
                spearLevel = computedSpearLevel;
                daggerLevel = computedDaggerLevel;
                bowLevel = computedBowLevel;
                crossbowLevel = computedCrossbowLevel;
                gunLevel = computedGunLevel;
                magicLevel = computedMagicLevel;
                throwableLevel = computedThrowableLevel;

                obj.addProperty(FIELD_SWORD_LEVEL, swordLevel);
                obj.addProperty(FIELD_AXE_LEVEL, axeLevel);
                obj.addProperty(FIELD_MACE_LEVEL, maceLevel);
                obj.addProperty(FIELD_SPEAR_LEVEL, spearLevel);
                obj.addProperty(FIELD_DAGGER_LEVEL, daggerLevel);
                obj.addProperty(FIELD_BOW_LEVEL, bowLevel);
                obj.addProperty(FIELD_CROSSBOW_LEVEL, crossbowLevel);
                obj.addProperty(FIELD_GUN_LEVEL, gunLevel);
                obj.addProperty(FIELD_MAGIC_LEVEL, magicLevel);
                obj.addProperty(FIELD_THROWABLE_LEVEL, throwableLevel);
                save(playerUuid, obj);
            } else {
                // Ensure the fields exist even if computed matches
                if (!obj.has(FIELD_SWORD_LEVEL)) {
                    obj.addProperty(FIELD_SWORD_LEVEL, swordLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_AXE_LEVEL)) {
                    obj.addProperty(FIELD_AXE_LEVEL, axeLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_MACE_LEVEL)) {
                    obj.addProperty(FIELD_MACE_LEVEL, maceLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_SPEAR_LEVEL)) {
                    obj.addProperty(FIELD_SPEAR_LEVEL, spearLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_DAGGER_LEVEL)) {
                    obj.addProperty(FIELD_DAGGER_LEVEL, daggerLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_BOW_LEVEL)) {
                    obj.addProperty(FIELD_BOW_LEVEL, bowLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_CROSSBOW_LEVEL)) {
                    obj.addProperty(FIELD_CROSSBOW_LEVEL, crossbowLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_GUN_LEVEL)) {
                    obj.addProperty(FIELD_GUN_LEVEL, gunLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_MAGIC_LEVEL)) {
                    obj.addProperty(FIELD_MAGIC_LEVEL, magicLevel);
                    save(playerUuid, obj);
                }
                if (!obj.has(FIELD_THROWABLE_LEVEL)) {
                    obj.addProperty(FIELD_THROWABLE_LEVEL, throwableLevel);
                    save(playerUuid, obj);
                }
            }

            return new MutationsState(
                    blocksBroken,
                    miningLevel,
                    staminaDepletions,
                    staminaDelayLevel,
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
        }
    }

    private static int ensureInt(JsonObject obj, UUID playerUuid, String field, int fallback) {
        int v = readInt(obj, field, fallback);
        if (!obj.has(field)) {
            obj.addProperty(field, v);
            save(playerUuid, obj);
        }
        return v;
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

        obj.addProperty(FIELD_SWORD_KILLS, state.getSwordKills());
        obj.addProperty(FIELD_AXE_KILLS, state.getAxeKills());
        obj.addProperty(FIELD_MACE_KILLS, state.getMaceKills());
        obj.addProperty(FIELD_SPEAR_KILLS, state.getSpearKills());
        obj.addProperty(FIELD_DAGGER_KILLS, state.getDaggerKills());
        obj.addProperty(FIELD_BOW_KILLS, state.getBowKills());
        obj.addProperty(FIELD_CROSSBOW_KILLS, state.getCrossbowKills());
        obj.addProperty(FIELD_GUN_KILLS, state.getGunKills());
        obj.addProperty(FIELD_MAGIC_KILLS, state.getMagicKills());
        obj.addProperty(FIELD_THROWABLE_KILLS, state.getThrowableKills());

        obj.addProperty(FIELD_SWORD_LEVEL, state.getSwordLevel());
        obj.addProperty(FIELD_AXE_LEVEL, state.getAxeLevel());
        obj.addProperty(FIELD_MACE_LEVEL, state.getMaceLevel());
        obj.addProperty(FIELD_SPEAR_LEVEL, state.getSpearLevel());
        obj.addProperty(FIELD_DAGGER_LEVEL, state.getDaggerLevel());
        obj.addProperty(FIELD_BOW_LEVEL, state.getBowLevel());
        obj.addProperty(FIELD_CROSSBOW_LEVEL, state.getCrossbowLevel());
        obj.addProperty(FIELD_GUN_LEVEL, state.getGunLevel());
        obj.addProperty(FIELD_MAGIC_LEVEL, state.getMagicLevel());
        obj.addProperty(FIELD_THROWABLE_LEVEL, state.getThrowableLevel());

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

            // Weapon defaults (kills)
            fresh.addProperty(FIELD_SWORD_KILLS, 0);
            fresh.addProperty(FIELD_AXE_KILLS, 0);
            fresh.addProperty(FIELD_MACE_KILLS, 0);
            fresh.addProperty(FIELD_SPEAR_KILLS, 0);
            fresh.addProperty(FIELD_DAGGER_KILLS, 0);
            fresh.addProperty(FIELD_BOW_KILLS, 0);
            fresh.addProperty(FIELD_CROSSBOW_KILLS, 0);
            fresh.addProperty(FIELD_GUN_KILLS, 0);
            fresh.addProperty(FIELD_MAGIC_KILLS, 0);
            fresh.addProperty(FIELD_THROWABLE_KILLS, 0);

            // Weapon defaults (levels)
            fresh.addProperty(FIELD_SWORD_LEVEL, 0);
            fresh.addProperty(FIELD_AXE_LEVEL, 0);
            fresh.addProperty(FIELD_MACE_LEVEL, 0);
            fresh.addProperty(FIELD_SPEAR_LEVEL, 0);
            fresh.addProperty(FIELD_DAGGER_LEVEL, 0);
            fresh.addProperty(FIELD_BOW_LEVEL, 0);
            fresh.addProperty(FIELD_CROSSBOW_LEVEL, 0);
            fresh.addProperty(FIELD_GUN_LEVEL, 0);
            fresh.addProperty(FIELD_MAGIC_LEVEL, 0);
            fresh.addProperty(FIELD_THROWABLE_LEVEL, 0);

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

                // Weapon defaults (kills)
                fresh.addProperty(FIELD_SWORD_KILLS, 0);
                fresh.addProperty(FIELD_AXE_KILLS, 0);
                fresh.addProperty(FIELD_MACE_KILLS, 0);
                fresh.addProperty(FIELD_SPEAR_KILLS, 0);
                fresh.addProperty(FIELD_DAGGER_KILLS, 0);
                fresh.addProperty(FIELD_BOW_KILLS, 0);
                fresh.addProperty(FIELD_CROSSBOW_KILLS, 0);
                fresh.addProperty(FIELD_GUN_KILLS, 0);
                fresh.addProperty(FIELD_MAGIC_KILLS, 0);
                fresh.addProperty(FIELD_THROWABLE_KILLS, 0);

                // Weapon defaults (levels)
                fresh.addProperty(FIELD_SWORD_LEVEL, 0);
                fresh.addProperty(FIELD_AXE_LEVEL, 0);
                fresh.addProperty(FIELD_MACE_LEVEL, 0);
                fresh.addProperty(FIELD_SPEAR_LEVEL, 0);
                fresh.addProperty(FIELD_DAGGER_LEVEL, 0);
                fresh.addProperty(FIELD_BOW_LEVEL, 0);
                fresh.addProperty(FIELD_CROSSBOW_LEVEL, 0);
                fresh.addProperty(FIELD_GUN_LEVEL, 0);
                fresh.addProperty(FIELD_MAGIC_LEVEL, 0);
                fresh.addProperty(FIELD_THROWABLE_LEVEL, 0);

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

            // Weapon defaults (kills)
            fresh.addProperty(FIELD_SWORD_KILLS, 0);
            fresh.addProperty(FIELD_AXE_KILLS, 0);
            fresh.addProperty(FIELD_MACE_KILLS, 0);
            fresh.addProperty(FIELD_SPEAR_KILLS, 0);
            fresh.addProperty(FIELD_DAGGER_KILLS, 0);
            fresh.addProperty(FIELD_BOW_KILLS, 0);
            fresh.addProperty(FIELD_CROSSBOW_KILLS, 0);
            fresh.addProperty(FIELD_GUN_KILLS, 0);
            fresh.addProperty(FIELD_MAGIC_KILLS, 0);
            fresh.addProperty(FIELD_THROWABLE_KILLS, 0);

            // Weapon defaults (levels)
            fresh.addProperty(FIELD_SWORD_LEVEL, 0);
            fresh.addProperty(FIELD_AXE_LEVEL, 0);
            fresh.addProperty(FIELD_MACE_LEVEL, 0);
            fresh.addProperty(FIELD_SPEAR_LEVEL, 0);
            fresh.addProperty(FIELD_DAGGER_LEVEL, 0);
            fresh.addProperty(FIELD_BOW_LEVEL, 0);
            fresh.addProperty(FIELD_CROSSBOW_LEVEL, 0);
            fresh.addProperty(FIELD_GUN_LEVEL, 0);
            fresh.addProperty(FIELD_MAGIC_LEVEL, 0);
            fresh.addProperty(FIELD_THROWABLE_LEVEL, 0);

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