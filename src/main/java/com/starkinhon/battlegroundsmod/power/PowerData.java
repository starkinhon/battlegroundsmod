package com.starkinhon.battlegroundsmod.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// Per-player power state. Stored as a data attachment on the player and mirrored to the client for the HUD.
public class PowerData {
    public static final float MAX_ENERGY = 100f;
    public static final float MAX_ULT = 100f;

    // Slots 0-3 are the power's abilities (Z, X, C, V), 4-5 are the shared melee moves (strong attack, barrage).
    public static final int SLOT_COUNT = 6;
    public static final int SLOT_ULT = 3;
    public static final int SLOT_STRONG = 4;
    public static final int SLOT_BARRAGE = 5;

    public static final Codec<PowerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("power", "").forGetter(d -> d.power),
            Codec.FLOAT.optionalFieldOf("energy", MAX_ENERGY).forGetter(d -> d.energy),
            Codec.FLOAT.optionalFieldOf("ult", 0f).forGetter(d -> d.ult),
            Codec.BOOL.optionalFieldOf("received_book", false).forGetter(d -> d.receivedBook)
    ).apply(instance, PowerData::new));

    public String power = "";
    public float energy = MAX_ENERGY;
    public float ult = 0f;
    public boolean receivedBook = false;

    // Not saved: these only matter while the player is online.
    public int[] cooldowns = new int[SLOT_COUNT];
    public int noFallTicks = 0;
    public int comboStep = 0;
    public int comboResetTicks = 0;
    public int punchDelay = 0;
    public int busyTicks = 0;
    // Set by /poder semcooldown: no cooldowns and no energy or ult cost, for testing and recording.
    public boolean noCooldown = false;

    public PowerData() {
    }

    private PowerData(String power, float energy, float ult, boolean receivedBook) {
        this.power = power;
        this.energy = energy;
        this.ult = ult;
        this.receivedBook = receivedBook;
    }

    public boolean hasPower() {
        return Powers.get(power) != null;
    }

    public void resetCombat() {
        cooldowns = new int[SLOT_COUNT];
        energy = MAX_ENERGY;
        comboStep = 0;
        comboResetTicks = 0;
        punchDelay = 0;
        busyTicks = 0;
    }
}
