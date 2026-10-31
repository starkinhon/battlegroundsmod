package com.starkinhon.battlegroundsmod.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// Per-player power state. Stored as a data attachment on the player and mirrored to the client for the HUD.
// There is no energy for now: moves only have cooldowns (an exhaustion system will come later).
// Bosses carry one too, so they use the same moves, cooldowns and busy time.
public class PowerData {
    public static final float MAX_ULT = 100f;

    // Slots 0-3 are the power's abilities (Z, X, C, V), 4-6 are the shared base attacks (strong attack, barrage, dash
    // attack), picked in the base attack selector above the hotbar, and 7 is a fifth ability (B) for powers that have one.
    // 8-11 are the Shift variants of Z, X, C and V (11 is a second ult), for powers that have them, and 12 the Shift
    // variant of the fifth ability.
    public static final int SLOT_COUNT = 13;
    public static final int SLOT_ULT = 3;
    public static final int SLOT_STRONG = 4;
    public static final int SLOT_BARRAGE = 5;
    public static final int SLOT_DASH_ATTACK = 6;
    public static final int SLOT_EXTRA = 7;
    public static final int SLOT_SHIFT = 8;
    public static final int SLOT_SHIFT_ULT = 11;
    public static final int SLOT_SHIFT_EXTRA = 12;
    // A cooldown this long means the move is spent until death or a power swap (the Izanami). The HUD shows it as used.
    public static final int SPENT = 1_000_000_000;

    public static final Codec<PowerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("power", "").forGetter(d -> d.power),
            Codec.FLOAT.optionalFieldOf("ult", 0f).forGetter(d -> d.ult),
            Codec.BOOL.optionalFieldOf("received_book", false).forGetter(d -> d.receivedBook),
            Codec.INT.optionalFieldOf("tier", Tiers.HUMANO).forGetter(d -> d.tier)
    ).apply(instance, PowerData::new));

    public String power = "";
    public float ult = 0f;
    public boolean receivedBook = false;
    // Power tier, 0 (god) to 10 (human): health, strength, armor and speed (see Tiers).
    public int tier = Tiers.HUMANO;

    // Not saved: these only matter while the player is online.
    public int[] cooldowns = new int[SLOT_COUNT];
    public int noFallTicks = 0;
    public int comboStep = 0;
    public int comboResetTicks = 0;
    public int punchDelay = 0;
    // A click that came in just before the next punch was allowed; it is thrown as soon as it can be.
    public boolean punchQueued = false;
    public int busyTicks = 0;
    // Set by /soul semcooldown: no cooldowns and no ult cost, for testing and recording.
    public boolean noCooldown = false;
    // Awakened mode: skills use the "despertado" values of their spec. Toggled by /soul despertar for now.
    public boolean awakened = false;
    // Last move used and when (game time), so bosses can read what their target just did. -1 = nothing yet.
    public int lastCastSlot = -1;
    public long lastCastTime = -1;
    // Combat mode lasts until this game time (see CombatMode). On the client, combat is the synced flag instead.
    public long combatUntil = 0;
    // Who this fighter last hit or was hit by, and when (game time): the teleport goes to them.
    public int foughtId = -1;
    public long foughtAt = -1;
    public boolean combat = false;
    // No skills until this game time (the Mangekyo seals them; see PowerLogic.seal).
    public long sealedUntil = 0;
    // Goes up each time the fighter's moves are cut off (PowerLogic.interrupt): a move running over several ticks
    // keeps the value it started with and stops when it changes.
    public int interrupts = 0;
    // Players: the key that uses the picked base attack is held down (a held Forte charges, a held Barragem lasts longer).
    public boolean attackHeld = false;
    // Players: a skill key is held down (a charged skill like the giant Katon is thrown when it comes up).
    public boolean abilityHeld = false;
    // Players: the aura state the watchers were last told (the power while in combat, "" when off; null = never sent).
    public String auraSent = null;

    public PowerData() {
    }

    private PowerData(String power, float ult, boolean receivedBook, int tier) {
        this.power = power;
        this.ult = ult;
        this.receivedBook = receivedBook;
        this.tier = tier;
    }

    public static boolean isUlt(int slot) {
        return slot == SLOT_ULT || slot == SLOT_SHIFT_ULT;
    }

    // The normal skill a Shift variant belongs to (8 -> 0, 12 -> 7), or the slot itself.
    public static int baseSlot(int slot) {
        return slot == SLOT_SHIFT_EXTRA ? SLOT_EXTRA : slot >= SLOT_SHIFT ? slot - SLOT_SHIFT : slot;
    }

    public boolean hasPower() {
        return Powers.get(power) != null;
    }

    public void resetCombat() {
        cooldowns = new int[SLOT_COUNT];
        comboStep = 0;
        comboResetTicks = 0;
        punchDelay = 0;
        punchQueued = false;
        busyTicks = 0;
    }
}
