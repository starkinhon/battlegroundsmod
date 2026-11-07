package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Urahara (SubAlma of Bleach): traps and combos, the Visualizador's Urahara tab.
// Z Shibari, Benihime: a cut with the Benihime turns into a crimson net that flies, opens, falls on the target and
//   tightens, holding it.
// X Hiasobi Juzutsunagi: with someone caught in his net, fire beads light up on its knots and go off one after another
//   up to a big blast.
// C Chikasumi no Tate: a crimson hexagonal barrier in front of him that stops what comes from the front.
// V Kido Seal (Bakudo #61, Rikujokoro): six rods of light slam into the aimed target and hold it still, even a boss.
public final class Urahara {
    // Who is caught in whose net: target -> the net (its id and when it lets go).
    private record Caught(UUID owner, int netId, long until) {
    }

    private static final Map<LivingEntity, Caught> CAUGHT = new HashMap<>();
    private static final int HOLD_TICKS = 80;

    private Urahara() {
    }

    public static Power create() {
        return new Power("urahara", 0xD0213A, 1.0f, List.of(
                new Ability(140, 0, Urahara::shibari),
                new Ability(160, 0, Urahara::juzutsunagi),
                new Ability(180, 0, Urahara::tate).asCounter(),
                new Ability(100, 0, Urahara::seal)));
    }

    // ---------- Z: Shibari ----------

    private static boolean shibari(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "sub.corte");
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
        SubCommon.sound(level, caster.position(), SoundEvents.EVOKER_CAST_SPELL, 0.8f, 1.6f);
        int id = SubNet.newId();
        Vec3 dir = caster.getLookAngle();
        Vec3[] pos = {caster.getEyePosition().add(dir.scale(1.2))};
        double speed = 1.1;
        double range = 26;
        LivingEntity[] caught = {null};
        int[] caughtAt = {0};
        Tasks.add(age -> {
            if (caught[0] == null) {
                Vec3 next = pos[0].add(dir.scale(speed));
                float open = Math.min(1.6f, 0.4f + age * 0.08f);
                for (LivingEntity t : Combat.alongLine(level, pos[0], next, open + 0.4, caster)) {
                    caught[0] = t;
                    caughtAt[0] = age;
                    break;
                }
                boolean wall = !level.noCollision(new net.minecraft.world.phys.AABB(next, next).inflate(0.2));
                pos[0] = next;
                if (caught[0] == null) {
                    boolean end = wall || age * speed >= range;
                    SubNet.send(level, SubNet.NET, id, pos[0], dir, open, 0, end ? 1 : 0);
                    return end;
                }
                LivingEntity t = caught[0];
                Freeze.hold(t, HOLD_TICKS);
                Stun.apply(t, HOLD_TICKS);
                CAUGHT.put(t, new Caught(caster.getUUID(), id, level.getGameTime() + HOLD_TICKS));
                Combat.hurtMagic(caster, t, 3);
                SubCommon.sound(level, t.position(), SoundEvents.FISHING_BOBBER_THROW, 1.2f, 0.6f);
                SubCommon.sound(level, t.position(), SoundEvents.CHAIN_PLACE, 1.2f, 0.8f);
            }
            LivingEntity t = caught[0];
            Caught c = CAUGHT.get(t);
            boolean gone = !t.isAlive() || c == null || c.netId() != id || level.getGameTime() >= c.until();
            float r = Math.max(0.8f, Math.max(t.getBbWidth(), t.getBbHeight()) * 0.6f) * (1.4f - 0.4f * SubCommon.smooth((age - caughtAt[0]) / 6f));
            SubNet.send(level, SubNet.NET, id, Combat.center(t), dir, r, 1, gone ? 1 : 0);
            if (gone) {
                if (c != null && c.netId() == id) {
                    CAUGHT.remove(t);
                }
                Freeze.free(t);
                return true;
            }
            return false;
        });
        return true;
    }

    // ---------- X: Hiasobi Juzutsunagi ----------

    private static boolean juzutsunagi(LivingEntity caster, ServerLevel level) {
        LivingEntity target = null;
        Caught caught = null;
        for (Map.Entry<LivingEntity, Caught> en : CAUGHT.entrySet()) {
            if (en.getValue().owner().equals(caster.getUUID()) && en.getKey().isAlive() && en.getKey().distanceTo(caster) < 48
                    && level.getGameTime() < en.getValue().until()) {
                target = en.getKey();
                caught = en.getValue();
                break;
            }
        }
        if (target == null) {
            return false;
        }
        LivingEntity t = target;
        int netId = caught.netId();
        // Keep the net on until the big one goes off.
        CAUGHT.put(t, new Caught(caught.owner(), netId, level.getGameTime() + 40));
        Freeze.hold(t, 40);
        SubCommon.pose(caster, "sub.estocada");
        SubCommon.sound(level, caster.position(), SoundEvents.FLINTANDSTEEL_USE, 1f, 1f);
        int beads = 8;
        for (int i = 0; i < beads; i++) {
            int k = i;
            Tasks.later(4 + i * 3, () -> {
                if (!t.isAlive()) {
                    return;
                }
                float ang = k / (float) beads * Mth.TWO_PI;
                float r = Math.max(0.8f, t.getBbWidth() * 0.8f);
                Vec3 at = Combat.center(t).add(Mth.cos(ang) * r, (k % 3 - 1) * t.getBbHeight() * 0.35, Mth.sin(ang) * r);
                SubNet.send(level, SubNet.EXPLOSION, 0, at, Vec3.ZERO, 1.2f, 0, 0);
                SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f + k * 0.05f);
                t.invulnerableTime = 0;
                Combat.hurtMagic(caster, t, 1.8f);
            });
        }
        Tasks.later(4 + beads * 3 + 8, () -> {
            CAUGHT.remove(t);
            Freeze.free(t);
            if (!t.isAlive()) {
                return;
            }
            Vec3 at = Combat.center(t);
            SubNet.send(level, SubNet.EXPLOSION, 0, at, Vec3.ZERO, 3.5f, 0, 0);
            SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.8f);
            t.invulnerableTime = 0;
            Combat.hurtMagic(caster, t, 11);
            for (LivingEntity near : Combat.inRadius(level, at, 3.5, caster)) {
                if (near != t) {
                    Combat.hurtMagic(caster, near, 5);
                }
                Combat.pushFrom(near, at, 1.1, 0.6);
            }
        });
        return true;
    }

    // ---------- C: Chikasumi no Tate ----------

    private static boolean tate(LivingEntity caster, ServerLevel level) {
        SubCommon.pose(caster, "sub.palma");
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 0.9f);
        Vec3 center = caster.position().add(0, caster.getBbHeight() * 0.55, 0).add(SubCommon.flat(caster.getLookAngle()).scale(1.5));
        SubDefense.shield(caster, level, center, SubCommon.flat(caster.getLookAngle()), 2.8, 80, 2, null);
        return true;
    }

    // ---------- V: Kido Seal ----------

    private static boolean seal(LivingEntity caster, ServerLevel level) {
        LivingEntity target = SubCommon.aimed(caster, 26);
        if (target == null) {
            return false;
        }
        int ticks = 100;
        SubCommon.pose(caster, "sub.apontar");
        SubCommon.sound(level, caster.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1f, 1.2f);
        SubNet.send(level, SubNet.SEAL, target.getId(), target.position(), Vec3.ZERO, ticks, 0, 0);
        Tasks.later(4, () -> {
            if (!target.isAlive()) {
                return;
            }
            SubCommon.sound(level, target.position(), SoundEvents.ANVIL_LAND, 1.2f, 1.4f);
            SubCommon.sound(level, target.position(), SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
            Freeze.hold(target, ticks - 4);
            Stun.apply(target, ticks - 4);
            Combat.hurtMagic(caster, target, 6);
        });
        return true;
    }
}
