package com.janiel.hytale.mutations.weapon;

public enum WeaponType {
    SWORD,
    AXE,
    MACE,
    SPEAR,
    DAGGER,
    BOW,
    CROSSBOW,
    GUN,
    MAGIC,
    THROWABLE,
    UNKNOWN;


    public static WeaponType fromItemId(String itemId) {
        if (itemId == null) {
            return UNKNOWN;
        }

        String s = itemId.trim();
        if (s.isEmpty()) {
            return UNKNOWN;
        }

        // Normalize to lowercase so we don't depend on exact casing like "Shortbow" vs "Bow"
        String n = s.toLowerCase();

        // Grouping rules:
        // - SWORD includes longsword
        // - AXE includes battleaxe
        // - MAGIC includes staff/wand/spellbook
        // - THROWABLE includes bomb
        //
        // IMPORTANT: shortbows use "shortbow" (lowercase b), so we must match that too.

        if (containsAnyLower(n, "staff", "wand", "spellbook")) {
            return MAGIC;
        }

        if (containsAnyLower(n, "crossbow")) {
            return CROSSBOW;
        }

        // Bow: includes "bow" and "shortbow"
        // (matching "bow" is enough for "shortbow", but keeping both is explicit)
        if (containsAnyLower(n, "shortbow", "bow")) {
            return BOW;
        }

        if (containsAnyLower(n, "gun", "rifle", "handgun", "blunderbuss")) {
            return GUN;
        }

        if (containsAnyLower(n, "bomb")) {
            return THROWABLE;
        }

        if (containsAnyLower(n, "spear")) {
            return SPEAR;
        }

        if (containsAnyLower(n, "mace", "club")) {
            return MACE;
        }

        if (containsAnyLower(n, "dagger")) {
            return DAGGER;
        }

        // Sword includes longsword
        if (containsAnyLower(n, "longsword", "sword")) {
            return SWORD;
        }

        // Axe includes battleaxe (handle both "battleaxe" and "battleaxe"/"battleaxe" variants)
        if (containsAnyLower(n, "battleaxe", "battleaxe", "axe")) {
            return AXE;
        }

        return UNKNOWN;
    }

    private static boolean containsAnyLower(String lower, String... tokensLower) {
        for (String t : tokensLower) {
            if (t != null && !t.isEmpty() && lower.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String s, String... tokens) {
        for (String t : tokens) {
            if (t != null && !t.isEmpty() && s.contains(t)) {
                return true;
            }
        }
        return false;
    }
}