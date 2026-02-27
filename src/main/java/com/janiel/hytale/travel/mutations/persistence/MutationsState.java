package com.janiel.hytale.travel.mutations.persistence;

public final class MutationsState {

    private final int blocksBroken;
    private final int miningLevel;

    public MutationsState(int blocksBroken, int miningLevel) {
        this.blocksBroken = blocksBroken;
        this.miningLevel = miningLevel;
    }

    public int getBlocksBroken() {
        return blocksBroken;
    }

    public int getMiningLevel() {
        return miningLevel;
    }

    public MutationsState withBlocksBroken(int newBlocksBroken) {
        return new MutationsState(newBlocksBroken, this.miningLevel);
    }

    public MutationsState withMiningLevel(int newMiningLevel) {
        return new MutationsState(this.blocksBroken, newMiningLevel);
    }
}