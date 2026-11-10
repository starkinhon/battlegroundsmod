package com.starkinhon.soulsjjk.power;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.starkinhon.soulsjjk.power.powers.Strongest;
import com.starkinhon.soulsjjk.power.powers.Sukuna;

// Every selectable power, in the order shown in the power book. The Divine Souls (the bosses' powers) are on the book's
// first tab, the SubAlmas (Low Souls: characters from each boss's anime) on the second.
public final class Powers {
    private static final Map<String, Power> BY_ID = new LinkedHashMap<>();
    private static final List<Power> DIVINE = new ArrayList<>();
    private static final List<Power> SUB = new ArrayList<>();
    // Powers only mobs use (the Fighters' base combat): found by get, but not selectable and without a soul crystal.
    private static final Map<String, Power> HIDDEN = new LinkedHashMap<>();

    public static final Power STRONGEST = register(Strongest.create());
    public static final Power SUKUNA = register(Sukuna.create());

    private Powers() {
    }

    private static Power register(Power power) {
        BY_ID.put(power.id(), power);
        DIVINE.add(power);
        return power;
    }

    private static Power registerSub(Power power) {
        BY_ID.put(power.id(), power);
        SUB.add(power);
        return power;
    }

    private static Power registerHidden(Power power) {
        HIDDEN.put(power.id(), power);
        return power;
    }

    public static Power get(String id) {
        Power power = BY_ID.get(id);
        return power != null || id == null ? power : HIDDEN.get(id);
    }

    public static Collection<Power> all() {
        return BY_ID.values();
    }

    public static List<Power> divine() {
        return DIVINE;
    }

    public static List<Power> sub() {
        return SUB;
    }

    public static boolean isSub(String id) {
        Power power = BY_ID.get(id);
        return power != null && SUB.contains(power);
    }
}
