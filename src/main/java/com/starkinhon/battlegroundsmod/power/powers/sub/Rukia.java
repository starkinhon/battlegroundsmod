package com.starkinhon.battlegroundsmod.power.powers.sub;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Rukia (SubAlma of Bleach), the 2026-11-11 remake in the Realista+Anime style. The approved page
// (forja/bleach/rukia-remake.html, rukia.js) is the program: the client plays its timelines (client/fx/BleachFx, Rk) and
// this side does the same thing at the same moments (who is hit, frozen, shattered). Ticks below = the page's seconds x 20.
// Z Some no Mai, Tsukishiro: the white circle where she aims; 0.4 s later a column of light and ice rises, all inside
//   turns into an ice statue (stun), the column cracks at 1.85 s and the statues shatter at 2.85 s (second damage).
// X Tsugi no Mai, Hakuren: four stabs into the ground, then a white avalanche 18 blocks long that freezes all it reaches;
//   everything shatters 2.25 s after it left (second damage).
// C San no Mai, Shirafune: the blade turns to crystal and stretches 17 blocks at once. A hit freezes out from the wound;
//   into the ground it leaves an ice stake for 8 s that freezes whoever touches it (a trap).
// B Kido: tap = Bakudo 61 Rikujokoro (six bars of light, 3.5 s held, can't move or use skills); hold = Hado 73 Soren
//   Sokatsui (a 0.75 s chant, then the twin blue fire to the target and its blast).
// V Bankai, Hakka no Togame: she freezes over and turns white; absolute zero spreads 10 blocks round her and whoever it
//   reaches, or whoever touches her while it lasts, becomes an ice flower that shatters. At the end she thaws slowly and
//   is defenseless for a moment.
public final class Rukia {
    // the Bankai: the ice climbs over her for CLIMB ticks, then absolute zero lasts ON ticks
    public static final int CLIMB = 16;
    public static final int ON = 200;
    // the old effects' numbers (client/fx/SubFx, IceShell still read them; nothing sends those effects any more)
    public static final int TSUKI_HOLD = 60;
    public static final int BANKAI_X = 40;
    public static final int BANKAI_FROST = 20;
    public static final int BANKAI_W = 80;
    public static final int BANKAI_M = 190;
    public static final float BANKAI_ARMOR = 0.76f;

    private Rukia() {
    }

    public static Power create() {
        return new Power("rukia", 0xBFE8FF, 1.0f, List.of(
                new Ability(160, 0, Rukia::tsukishiro),
                new Ability(150, 0, Rukia::hakuren),
                new Ability(100, 0, Rukia::shirafune),
                new Ability(420, 0, Rukia::bankai),
                new Ability(140, 0, Rukia::kido)));
    }

    // ---------- ice over a body ----------

    // Frozen from `from` (mode 0: from the feet up), speed = how much of the body per second, h = the page's height for a
    // body 2 blocks tall; held for `ticks`, then it shatters with `shatterDamage` (the second hit).
    static void freeze(LivingEntity caster, ServerLevel level, LivingEntity target, int ticks, Vec3 from, int mode, float h, float speed, int style,
            float shatterDamage) {
        Freeze.hold(target, ticks);
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), 140));
        SubNet.send(level, SubNet.BL_ICE, target.getId(), from, new Vec3(speed, 0, 0), h, mode + 2 * style, 0);
        SubCommon.sound(level, target.position(), SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.8f);
        Tasks.later(ticks, () -> {
            SubNet.send(level, SubNet.BL_ICE, target.getId(), target.position(), Vec3.ZERO, 0, style, 1);
            if (target.isAlive()) {
                SubCommon.sound(level, target.position(), SoundEvents.GLASS_BREAK, 1.2f, 0.9f);
                if (shatterDamage > 0 && caster.isAlive()) {
                    target.invulnerableTime = 0;
                    Combat.hurtMagic(caster, target, shatterDamage);
                }
            }
        });
    }

    // ---------- Z: Tsukishiro ----------

    private static boolean tsukishiro(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 center = SubCommon.ground(level, SubCommon.aimPoint(caster, 24));
        float radius = 3f;
        SubCommon.pose(caster, "sub.apontar_baixo");
        SubCommon.busy(caster, 12);
        SubNet.send(level, SubNet.BL_TSUKI, 0, center, Vec3.ZERO, radius, 0, 0);
        SubCommon.sound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.6f);
        Tasks.later(8, () -> {
            if (guard.broken()) {
                return;
            }
            SubCommon.sound(level, center, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.8f);
            SubCommon.sound(level, center, SoundEvents.GLASS_BREAK, 1.5f, 0.6f);
            AABB column = new AABB(center.x - radius, center.y - 1, center.z - radius, center.x + radius, center.y + 12, center.z + radius);
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, column, e -> Combat.isTarget(e, caster))) {
                double dx = t.getX() - center.x;
                double dz = t.getZ() - center.z;
                if (dx * dx + dz * dz > (radius + 0.5) * (radius + 0.5)) {
                    continue;
                }
                Combat.hurtMagic(caster, t, 8);
                // the statue stands until 2.85 s from the cast
                freeze(caster, level, t, 49, t.position(), 0, 2.1f, 7, SubNet.ICE_TSUKI, 6);
            }
        });
        Tasks.later(37, () -> SubCommon.sound(level, center, SoundEvents.GLASS_BREAK, 1.6f, 0.7f));
        return true;
    }

    // ---------- X: Hakuren ----------

    private static boolean hakuren(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "sub.cravar");
        SubCommon.busy(caster, 16);
        Vec3 feet = caster.position();
        Vec3 dir = SubCommon.flat(caster.getLookAngle());
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        SubNet.send(level, SubNet.BL_HAKUREN, caster.getId(), feet, dir, 0, 0, 0);
        int[] stabs = {0, 3, 5, 8};
        for (int i = 0; i < 4; i++) {
            int k = i;
            Tasks.later(stabs[i], () -> SubCommon.sound(level, feet.add(dir.scale(1.6)), SoundEvents.POWDER_SNOW_BREAK, 1f, 0.8f + k * 0.15f));
        }
        Set<Integer> hit = new HashSet<>();
        Tasks.later(13, () -> {
            if (guard.broken()) {
                return;
            }
            SubCommon.sound(level, feet, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.8f);
            SubCommon.sound(level, feet, SoundEvents.ELDER_GUARDIAN_CURSE, 0.6f, 1.8f);
            Tasks.add(age -> {
                // the page: s from the avalanche's start, its front 0.6 + 18 * easeOut3(s / 0.55) ahead, as wide as 1.2 + 2.6 * reach
                float s = (age + 0.2f) / 20f;
                float reach = SubCommon.easeOutCubic(s / 0.55f);
                double front = 0.6 + 18 * reach;
                double half = 1.2 + 2.6 * reach;
                AABB box = new AABB(feet, feet).inflate(20, 5, 20);
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, e -> Combat.isTarget(e, caster))) {
                    Vec3 d = t.position().subtract(feet);
                    double along = d.dot(dir);
                    double across = Math.abs(d.dot(right));
                    if (along < 0.2 || along - 0.3 > front || across > half || Math.abs(d.y) > 4 || !hit.add(t.getId())) {
                        continue;
                    }
                    Combat.hurtMagic(caster, t, 11);
                    // everything shatters 2.25 s after the avalanche left (tick 13 + 45)
                    freeze(caster, level, t, Math.max(10, 45 - age), t.position(), 0, 2.1f, 8, SubNet.ICE_HAKUREN, 5);
                }
                return s > 0.68f;
            });
        });
        Tasks.later(58, () -> SubCommon.sound(level, feet.add(dir.scale(8)), SoundEvents.GLASS_BREAK, 1.6f, 0.6f));
        return true;
    }

    // ---------- C: Shirafune ----------

    static final float SHIRA_MAX = 17;

    private static boolean shirafune(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "sub.estocada");
        SubCommon.busy(caster, 12);
        Vec3 base = Byakuya.hand(caster).add(caster.getLookAngle().scale(0.1));
        Vec3 dir = caster.getLookAngle();
        Vec3 far = base.add(dir.scale(SHIRA_MAX));
        HitResult block = level.clip(new ClipContext(base, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double wall = block.getType() == HitResult.Type.MISS ? SHIRA_MAX : block.getLocation().distanceTo(base);
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : Combat.alongLine(level, base, base.add(dir.scale(wall)), 0.8, caster)) {
            double d = Combat.center(t).subtract(base).dot(dir);
            if (d > 0 && d < best) {
                best = d;
                first = t;
            }
        }
        int mode = first != null ? 0 : wall < SHIRA_MAX ? 1 : 2;
        double stop = first != null ? best : wall;
        SubNet.send(level, SubNet.BL_SHIRAFUNE, caster.getId(), base, dir, (float) stop, mode, 0);
        SubCommon.sound(level, base, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
        // the lance shoots at 0.35 s and reaches `stop` when easeOut3(s / 0.13) * 17 = stop
        float s = (float) (0.13 * (1 - Math.cbrt(Math.max(0, 1 - stop / SHIRA_MAX))));
        int at = Math.round((0.35f + s) * 20);
        Tasks.later(7, () -> {
            SubCommon.sound(level, base, SoundEvents.GLASS_BREAK, 1f, 1.8f);
            SubCommon.sound(level, base, SoundEvents.TRIDENT_THROW.value(), 1.2f, 1.4f);
        });
        if (first != null) {
            LivingEntity target = first;
            Vec3 wound = base.add(dir.scale(stop));
            Tasks.later(at, () -> {
                if (guard.broken() || !target.isAlive()) {
                    return;
                }
                Combat.hurtMagic(caster, target, 12);
                // frozen out from the wound; the statue shatters at 2.95 s
                freeze(caster, level, target, Math.max(10, 59 - at), wound, 1, 1.6f, 2.6f, SubNet.ICE_SHIRA, 4);
            });
        } else if (mode == 1) {
            Vec3 stake = block.getLocation();
            Set<Integer> caught = new HashSet<>();
            // the stake stands from 1.11 s (when the lance breaks) for 8 s: whoever touches it freezes
            Tasks.later(22, () -> {
                SubCommon.sound(level, stake, SoundEvents.GLASS_PLACE, 1.2f, 0.7f);
                Tasks.add(age -> {
                    for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(stake, stake).inflate(1.3, 2.5, 1.3),
                            e -> Combat.isTarget(e, caster))) {
                        double dx = t.getX() - stake.x, dz = t.getZ() - stake.z;
                        if (dx * dx + dz * dz < 1.3 * 1.3 && caught.add(t.getId())) {
                            Combat.hurtMagic(caster, t, 6);
                            freeze(caster, level, t, 40, t.position(), 0, 2.1f, 7, SubNet.ICE_TRAP, 4);
                        }
                    }
                    return age >= 160 || !caster.isAlive();
                });
            });
        }
        return true;
    }

    // ---------- B: Kido (tap Rikujokoro, hold Soren Sokatsui) ----------

    private static boolean kido(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        int bossHold = SubCommon.RANDOM.nextBoolean() ? 0 : 20;
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            boolean held = SubCommon.held(caster, age, bossHold);
            if (!held && age < 5) {
                rikujokoro(caster, level, guard);
                return true;
            }
            if (age >= 5) {
                soren(caster, level, guard);
                return true;
            }
            return false;
        });
        return true;
    }

    private static void rikujokoro(LivingEntity caster, ServerLevel level, SubCommon.Guard guard) {
        LivingEntity target = SubCommon.aimed(caster, 26);
        if (target == null) {
            SubCommon.warn(caster, "rukia.no_target");
            return;
        }
        SubCommon.pose(caster, "sub.palma");
        SubCommon.busy(caster, 8);
        SubNet.send(level, SubNet.BL_RIKU, target.getId(), target.position(), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, target.position(), SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.6f);
        // the bars slam shut at 0.5 s: held 3.5 s, no walking, no skills
        Tasks.later(10, () -> {
            if (guard.broken() || !target.isAlive()) {
                return;
            }
            SubCommon.sound(level, target.position(), SoundEvents.ANVIL_LAND, 0.8f, 1.8f);
            Combat.hurtMagic(caster, target, 4);
            Freeze.hold(target, 70);
            SubCommon.busy(target, 70);
        });
    }

    private static void soren(LivingEntity caster, ServerLevel level, SubCommon.Guard guard) {
        SubCommon.pose(caster, "sub.maos");
        SubCommon.busy(caster, 20);
        SubNet.send(level, SubNet.BL_SOREN, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.BLAZE_SHOOT, 0.8f, 0.6f);
        // the chant: 0.75 s
        Tasks.later(15, () -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.BL_SOREN, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, -1);
                return;
            }
            LivingEntity target = SubCommon.aimed(caster, 30);
            Vec3 at = target != null ? Combat.center(target) : Combat.raycastPoint(caster, 30);
            SubNet.send(level, SubNet.BL_SOREN, caster.getId(), at, Vec3.ZERO, 0, 0, 1);
            SubCommon.sound(level, caster.position(), SoundEvents.FIRECHARGE_USE, 1.4f, 0.6f);
            // the fire reaches it in 0.2 s
            Tasks.later(4, () -> {
                SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 1.3f);
                for (LivingEntity t : Combat.inRadius(level, at, 3, caster)) {
                    Combat.hurtMagic(caster, t, t == target ? 16 : 8);
                    Combat.pushFrom(t, caster.position(), 1.4, 0.55);
                }
            });
        });
    }

    // ---------- V: Bankai ----------

    private static boolean bankai(LivingEntity caster, ServerLevel level) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "sub.erguer");
        SubCommon.busy(caster, CLIMB + 4);
        SubCommon.setState(caster, "rukia_bankai", CLIMB + ON);
        Freeze.hold(caster, CLIMB);
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, CLIMB + ON, 1, false, false));
        SubNet.send(level, SubNet.BL_BANKAI, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.5f, 1.6f);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.5f, 0.6f);
        Set<Integer> flowers = new HashSet<>();
        Tasks.later(CLIMB, () -> {
            if (guard.broken()) {
                SubNet.send(level, SubNet.BL_BANKAI, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
                return;
            }
            SubCommon.sound(level, caster.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.7f);
            SubCommon.sound(level, caster.position(), SoundEvents.GLASS_BREAK, 1.6f, 0.5f);
            Tasks.add(age -> {
                if (guard.broken()) {
                    end(caster, level);
                    return true;
                }
                // the page: the wave's front 0.6 + 9.4 * easeOut3(s / 0.55) for 0.7 s; after that, whoever comes within 2 blocks
                float s = age / 20f;
                double reach = s < 0.7f ? Math.min(9.6, 0.6 + 9.4 * SubCommon.easeOutCubic(s / 0.55f) - 0.3) : 2.0;
                for (LivingEntity t : Combat.inRadius(level, caster.position(), reach, caster)) {
                    if (flowers.add(t.getId())) {
                        flower(caster, level, t);
                    }
                }
                if (age >= ON) {
                    end(caster, level);
                    return true;
                }
                return false;
            });
        });
        return true;
    }

    // Crystal, then a flower of ice, then it all shatters 2.15 s later.
    private static void flower(LivingEntity caster, ServerLevel level, LivingEntity t) {
        SubNet.send(level, SubNet.BL_FLOWER, t.getId(), t.position(), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, t.position(), SoundEvents.PLAYER_HURT_FREEZE, 1.2f, 0.7f);
        Freeze.hold(t, 43);
        t.setTicksFrozen(Math.max(t.getTicksFrozen(), 140));
        Combat.hurtMagic(caster, t, 4);
        Tasks.later(43, () -> {
            SubNet.send(level, SubNet.BL_ICE, t.getId(), t.position(), Vec3.ZERO, 0, SubNet.ICE_FLOWER, 1);
            SubCommon.sound(level, t.position(), SoundEvents.GLASS_BREAK, 1.6f, 0.6f);
            if (t.isAlive() && caster.isAlive()) {
                t.invulnerableTime = 0;
                Combat.hurtMagic(caster, t, 15);
            }
        });
    }

    // She thaws slowly and is defenseless for a moment (the page's end: 1.6 s thawing, kneeling).
    private static void end(LivingEntity caster, ServerLevel level) {
        SubCommon.clearState(caster, "rukia_bankai");
        SubNet.send(level, SubNet.BL_BANKAI, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 1);
        SubCommon.sound(level, caster.position(), SoundEvents.POWDER_SNOW_BREAK, 1.2f, 0.7f);
        caster.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        if (caster.isAlive()) {
            Freeze.hold(caster, 26);
            SubCommon.busy(caster, 26);
        }
    }
}
