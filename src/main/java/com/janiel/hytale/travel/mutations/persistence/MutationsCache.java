package com.janiel.hytale.travel.mutations.persistence;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MutationsCache {

    private static final ConcurrentHashMap<UUID, MutationsState> CACHE = new ConcurrentHashMap<>();

    private MutationsCache() {
    }

    public static MutationsState getOrLoad(UUID playerUuid) {
        MutationsState cached = CACHE.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        MutationsState loaded = MutationsRepository.loadState(playerUuid);
        MutationsState existing = CACHE.putIfAbsent(playerUuid, loaded);
        return existing != null ? existing : loaded;
    }

    public static void put(UUID playerUuid, MutationsState state) {
        CACHE.put(playerUuid, state);
    }

    public static void invalidate(UUID playerUuid) {
        CACHE.remove(playerUuid);
    }
}