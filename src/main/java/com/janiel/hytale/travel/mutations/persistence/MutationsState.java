package com.janiel.hytale.travel.mutations.persistence;

public final class MutationsState {

    private final int blocksBroken;
    private final int miningLevel;

    private final int staminaDepletions;
    private final int staminaDelayLevel;

    public MutationsState(int blocksBroken, int miningLevel, int staminaDepletions, int staminaDelayLevel) {
        this.blocksBroken = blocksBroken;
        this.miningLevel = miningLevel;
        this.staminaDepletions = staminaDepletions;
        this.staminaDelayLevel = staminaDelayLevel;
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

    public MutationsState withBlocksBroken(int newBlocksBroken) {
        return new MutationsState(newBlocksBroken, this.miningLevel, this.staminaDepletions, this.staminaDelayLevel);
    }

    public MutationsState withMiningLevel(int newMiningLevel) {
        return new MutationsState(this.blocksBroken, newMiningLevel, this.staminaDepletions, this.staminaDelayLevel);
    }

    public MutationsState withStaminaDepletions(int newStaminaDepletions) {
        return new MutationsState(this.blocksBroken, this.miningLevel, newStaminaDepletions, this.staminaDelayLevel);
    }

    public MutationsState withStaminaDelayLevel(int newStaminaDelayLevel) {
        return new MutationsState(this.blocksBroken, this.miningLevel, this.staminaDepletions, newStaminaDelayLevel);
    }
}