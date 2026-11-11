package com.starkinhon.battlegroundsmod.power.powers.luffy;

import java.util.Map;
import java.util.WeakHashMap;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Charge;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// Luffy (Garoto Borracha, Stark 2026-11-11, the approved page forja/luffy-gears): holding a key climbs the Gears and
// letting go fires that key's move in the Gear he reached. 0.25 s = G2, 0.5 s = G3, 0.9 s = G4 (the page's TH). The body
// shows the Gear at once (LuffyFx), so whoever watches can read it. Holding stops at G4: the G5 comes only from the
// Tambores da Libertacao (the ult bar, filled fighting): B with it full turns him into the total Gear 5 for 1 minute,
// every key in its G5 version at once, no holding. When it ends he is starving and spent: no stamina, slow, weak and
// without Gears for a while (as in the anime).
public final class Luffy {
    public static final String ID = "rubber_boy";
    public static final String G5 = "luffy_g5", FOME = "luffy_fome", TINY = "luffy_tiny", TIRED = "luffy_tired", FUSEN = "luffy_fusen";
    // The page's TH in ticks: toque, G2, G3, G4, G5.
    static final int[] TH = {0, 5, 10, 18, 28};
    public static final int G5_TICKS = 1200, FOME_TICKS = 400;
    // What each Gear costs (percent of the stamina) when the hold reaches it.
    private static final double[] COST = {0, 5, 8, 14, 0};
    // A Gear he let go of stays on a moment: the next key starts there (combos without holding again).
    private static final int KEEP_TICKS = 80;
    // Held more than this past G4, it goes off by itself.
    private static final int MAX_HOLD = 60;

    private record Keep(int gear, long until) {
    }

    private static final Map<LivingEntity, Keep> KEEP = new WeakHashMap<>();
    // Who is holding a key right now (one charge at a time).
    private static final Map<LivingEntity, Integer> HOLDING = new WeakHashMap<>();

    private Luffy() {
    }

    public static boolean is(Entity entity) {
        return entity instanceof LivingEntity living && living.hasData(ModAttachments.POWER) && ID.equals(living.getData(ModAttachments.POWER).power);
    }

    public static boolean g5(LivingEntity caster) {
        return SubCommon.hasState(caster, G5);
    }

    // V is each Gear's own big move, not the bar's: it never waits for the Tambores.
    public static boolean freeUlt(LivingEntity caster) {
        return is(caster);
    }

    static int gearAt(int ticks) {
        int g = 0;
        for (int i = 1; i < TH.length; i++) {
            if (ticks >= TH[i]) {
                g = i;
            }
        }
        return g;
    }

    // ---------- holding a key ----------

    static boolean press(LivingEntity caster, ServerLevel level, int key) {
        if (HOLDING.containsKey(caster)) {
            return false;
        }
        if (g5(caster)) {
            // Gear 5: every key is already its G5 version.
            return LuffyMoves.fire(caster, level, key, 4);
        }
        boolean starving = SubCommon.hasState(caster, FOME);
        int top = starving ? 0 : 3;
        Keep keep = KEEP.get(caster);
        int startGear = keep != null && keep.until() > level.getGameTime() ? Math.min(top, keep.gear()) : 0;
        int start = TH[startGear];
        // Bots pick how long they hold (a Gear that fits the fight, not always the top).
        int botHold = TH[Math.min(top, SubCommon.RANDOM.nextInt(4))] + 1;
        SubCommon.Guard guard = SubCommon.guard(caster);
        HOLDING.put(caster, key);
        PowerData data = caster.getData(ModAttachments.POWER);
        int stunAt = data.interrupts;
        SubNet.send(level, SubNet.LUFFY_CHARGE, caster.getId(), caster.position(), caster.getLookAngle(), key, startGear, 0);
        SubCommon.pose(caster, "luffy.carga" + (startGear + 1));
        int[] shown = {startGear};
        Tasks.add(age -> {
            if (guard.broken() || data.interrupts != stunAt || Stun.is(caster)) {
                HOLDING.remove(caster);
                SubNet.send(level, SubNet.LUFFY_CHARGE, caster.getId(), caster.position(), Vec3.ZERO, key, shown[0], 2);
                return true;
            }
            int t = start + age;
            int gear = Math.min(top, gearAt(t));
            if (gear > shown[0]) {
                shown[0] = gear;
                if (Stamina.drains(caster)) {
                    Stamina.drain(caster, COST[gear]);
                }
                SubNet.send(level, SubNet.LUFFY_CHARGE, caster.getId(), caster.position(), caster.getLookAngle(), key, gear, 1);
                SubCommon.pose(caster, "luffy.carga" + (gear + 1));
                gearSound(level, caster, gear);
            }
            Charge.report(caster, Math.min(1, t / (double) TH[Math.max(1, top)]));
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false));
            boolean held = caster instanceof Mob ? age < botHold : data.abilityHeld;
            boolean exhausted = Stamina.exhausted(caster);
            if (!held || exhausted || t >= TH[top] + MAX_HOLD) {
                HOLDING.remove(caster);
                SubNet.send(level, SubNet.LUFFY_CHARGE, caster.getId(), caster.position(), Vec3.ZERO, key, gear, 2);
                if (gear >= 1 && gear != 2) {
                    KEEP.put(caster, new Keep(gear, level.getGameTime() + KEEP_TICKS));
                } else {
                    KEEP.remove(caster);
                }
                LuffyMoves.fire(caster, level, key, gear);
                return true;
            }
            return false;
        });
        return true;
    }

    private static void gearSound(ServerLevel level, LivingEntity caster, int gear) {
        Vec3 p = caster.position();
        switch (gear) {
            case 1 -> SubCommon.sound(level, p, SoundEvents.FIRE_EXTINGUISH, 0.9f, 1.5f);
            case 2 -> SubCommon.sound(level, p, SoundEvents.HORSE_BREATHE, 1.2f, 0.6f);
            case 3 -> SubCommon.sound(level, p, SoundEvents.SLIME_JUMP, 1.2f, 0.5f);
            default -> {
            }
        }
    }

    // ---------- B: Gear 5 (the Tambores da Libertacao) ----------

    static boolean gear5(LivingEntity caster, ServerLevel level) {
        if (g5(caster)) {
            return false;
        }
        PowerData data = caster.getData(ModAttachments.POWER);
        boolean free = caster instanceof ServerPlayer p && (p.isCreative() || data.noCooldown);
        if (caster instanceof ServerPlayer && !free && data.ult < PowerData.MAX_ULT) {
            SubCommon.warn(caster, "luffy.tambores", (int) (data.ult / PowerData.MAX_ULT * 100));
            return false;
        }
        if (SubCommon.hasState(caster, FOME)) {
            SubCommon.warn(caster, "luffy.fome");
            return false;
        }
        if (!free) {
            data.ult = 0;
        }
        SubCommon.setState(caster, G5, G5_TICKS);
        SubCommon.clearState(caster, TINY);
        SubCommon.clearState(caster, TIRED);
        KEEP.remove(caster);
        for (int i = 0; i < 4; i++) {
            data.cooldowns[i] = 0;
        }
        SubCommon.pose(caster, "luffy.carga5");
        SubNet.send(level, SubNet.LUFFY_G5, caster.getId(), caster.position(), Vec3.ZERO, 1, G5_TICKS, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 2f, 0.6f);
        Tasks.later(8, () -> SubCommon.sound(level, caster.position(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 2f, 0.6f));
        Tasks.later(20, () -> SubCommon.sound(level, caster.position(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 2f, 0.6f));
        Tasks.later(28, () -> SubCommon.sound(level, caster.position(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 2f, 0.6f));
        caster.heal(caster.getMaxHealth() * 0.25f);
        Stamina.refill(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        long until = level.getGameTime() + G5_TICKS;
        Tasks.add(age -> {
            if (guard.broken()) {
                SubCommon.clearState(caster, G5);
                SubNet.send(level, SubNet.LUFFY_G5, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
                return true;
            }
            // the drums keep beating
            if (age % 40 == 0) {
                SubCommon.sound(level, caster.position(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.2f, 0.6f);
                Tasks.later(8, () -> SubCommon.sound(level, caster.position(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.2f, 0.6f));
            }
            if (level.getGameTime() < until) {
                return false;
            }
            starve(caster, level);
            return true;
        });
        if (caster instanceof ServerPlayer player) {
            PowerLogic.sync(player);
        }
        return true;
    }

    // The G5 is over: starving, no stamina, slow and weak, no Gears until it passes.
    private static void starve(LivingEntity caster, ServerLevel level) {
        SubCommon.clearState(caster, G5);
        SubCommon.setState(caster, FOME, FOME_TICKS);
        SubNet.send(level, SubNet.LUFFY_G5, caster.getId(), caster.position(), Vec3.ZERO, 2, FOME_TICKS, 0);
        Stamina.exhaust(caster);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FOME_TICKS, 2, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FOME_TICKS, 1, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.HUNGER, FOME_TICKS, 2, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FOME_TICKS, 1, false, true));
        if (caster instanceof ServerPlayer player) {
            player.getFoodData().setFoodLevel(Math.min(player.getFoodData().getFoodLevel(), 4));
            player.getFoodData().setSaturation(0);
        }
        SubCommon.warn(caster, "luffy.fome_on");
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_BURP, 1f, 0.5f);
    }

    // ---------- after a move: G3 shrinks him, G4 tires him ----------

    static void after(LivingEntity caster, ServerLevel level, int gear) {
        if (g5(caster)) {
            return;
        }
        if (gear == 2) {
            SubCommon.setState(caster, TINY, 44);
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 44, 2, false, false));
            caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 44, 1, false, false));
            SubNet.send(level, SubNet.LUFFY_AFTER, caster.getId(), caster.position(), Vec3.ZERO, 1, 44, 0);
        } else if (gear == 3) {
            SubCommon.setState(caster, TIRED, 40);
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
            SubNet.send(level, SubNet.LUFFY_AFTER, caster.getId(), caster.position(), Vec3.ZERO, 2, 40, 0);
        }
    }
}
