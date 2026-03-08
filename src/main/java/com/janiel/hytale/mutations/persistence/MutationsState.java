package com.janiel.hytale.mutations.persistence;

public final class MutationsState {

    private final int blocksBroken;
    private final int miningLevel;

    private final int staminaDepletions;
    private final int staminaDelayLevel;

    // Weapon mutations (grouped)
    private final int swordKills;
    private final int axeKills;
    private final int maceKills;
    private final int spearKills;
    private final int daggerKills;
    private final int bowKills;
    private final int crossbowKills;
    private final int gunKills;
    private final int magicKills;
    private final int throwableKills;

    // Weapon progression levels (derived from kills, but persisted for compatibility / UI)
    private final int swordLevel;
    private final int axeLevel;
    private final int maceLevel;
    private final int spearLevel;
    private final int daggerLevel;
    private final int bowLevel;
    private final int crossbowLevel;
    private final int gunLevel;
    private final int magicLevel;
    private final int throwableLevel;

    public MutationsState(
            int blocksBroken,
            int miningLevel,
            int staminaDepletions,
            int staminaDelayLevel,
            int swordKills,
            int axeKills,
            int maceKills,
            int spearKills,
            int daggerKills,
            int bowKills,
            int crossbowKills,
            int gunKills,
            int magicKills,
            int throwableKills,
            int swordLevel,
            int axeLevel,
            int maceLevel,
            int spearLevel,
            int daggerLevel,
            int bowLevel,
            int crossbowLevel,
            int gunLevel,
            int magicLevel,
            int throwableLevel
    ) {
        this.blocksBroken = blocksBroken;
        this.miningLevel = miningLevel;
        this.staminaDepletions = staminaDepletions;
        this.staminaDelayLevel = staminaDelayLevel;

        this.swordKills = swordKills;
        this.axeKills = axeKills;
        this.maceKills = maceKills;
        this.spearKills = spearKills;
        this.daggerKills = daggerKills;
        this.bowKills = bowKills;
        this.crossbowKills = crossbowKills;
        this.gunKills = gunKills;
        this.magicKills = magicKills;
        this.throwableKills = throwableKills;

        this.swordLevel = swordLevel;
        this.axeLevel = axeLevel;
        this.maceLevel = maceLevel;
        this.spearLevel = spearLevel;
        this.daggerLevel = daggerLevel;
        this.bowLevel = bowLevel;
        this.crossbowLevel = crossbowLevel;
        this.gunLevel = gunLevel;
        this.magicLevel = magicLevel;
        this.throwableLevel = throwableLevel;
    }

    public int getBlocksBroken() {
        return blocksBroken;
    }

    public int getMiningLevel() {
        return miningLevel;
    }

    public int getStaminaDepletions() {
        return staminaDepletions;
    }

    public int getStaminaDelayLevel() {
        return staminaDelayLevel;
    }

    public int getSwordKills() {
        return swordKills;
    }

    public int getAxeKills() {
        return axeKills;
    }

    public int getMaceKills() {
        return maceKills;
    }

    public int getSpearKills() {
        return spearKills;
    }

    public int getDaggerKills() {
        return daggerKills;
    }

    public int getBowKills() {
        return bowKills;
    }

    public int getCrossbowKills() {
        return crossbowKills;
    }

    public int getGunKills() {
        return gunKills;
    }

    public int getMagicKills() {
        return magicKills;
    }

    public int getThrowableKills() {
        return throwableKills;
    }

    public int getSwordLevel() {
        return swordLevel;
    }

    public int getAxeLevel() {
        return axeLevel;
    }

    public int getMaceLevel() {
        return maceLevel;
    }

    public int getSpearLevel() {
        return spearLevel;
    }

    public int getDaggerLevel() {
        return daggerLevel;
    }

    public int getBowLevel() {
        return bowLevel;
    }

    public int getCrossbowLevel() {
        return crossbowLevel;
    }

    public int getGunLevel() {
        return gunLevel;
    }

    public int getMagicLevel() {
        return magicLevel;
    }

    public int getThrowableLevel() {
        return throwableLevel;
    }
}