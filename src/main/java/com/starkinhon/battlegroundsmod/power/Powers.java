package com.starkinhon.battlegroundsmod.power;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.starkinhon.battlegroundsmod.power.powers.DestructionGod;
import com.starkinhon.battlegroundsmod.power.powers.Manipulator;
import com.starkinhon.battlegroundsmod.power.powers.OnePunch;
import com.starkinhon.battlegroundsmod.power.powers.RubberBoy;
import com.starkinhon.battlegroundsmod.power.powers.Strongest;
import com.starkinhon.battlegroundsmod.power.powers.UchihaGhost;

// Every selectable power, in the order shown in the power book.
public final class Powers {
    private static final Map<String, Power> BY_ID = new LinkedHashMap<>();

    public static final Power UCHIHA_GHOST = register(UchihaGhost.create());
    public static final Power MANIPULATOR = register(Manipulator.create());
    public static final Power RUBBER_BOY = register(RubberBoy.create());
    public static final Power DESTRUCTION_GOD = register(DestructionGod.create());
    public static final Power ONE_PUNCH = register(OnePunch.create());
    public static final Power STRONGEST = register(Strongest.create());

    private Powers() {
    }

    private static Power register(Power power) {
        BY_ID.put(power.id(), power);
        return power;
    }

    public static Power get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<Power> all() {
        return BY_ID.values();
    }
}
