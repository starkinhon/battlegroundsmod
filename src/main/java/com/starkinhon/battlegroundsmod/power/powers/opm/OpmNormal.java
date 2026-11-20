// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.opm;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Defense;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Stamina;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import com.starkinhon.battlegroundsmod.combat.BlockRules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

// Saitama's normal mode (the approved page's second row): weak on purpose next to the serious moves, a bored face, no
// silence, no destruction, but his normal blows hit harder than most (Stark's port rules, 2026-11-11).
// Z Socos Consecutivos: a short step and 14 punches in 1.1 s; the ghosts of the arms the eye missed fill the air; the last
// one throws the target 8 blocks. X Gancho: low, then the fist straight up: the target goes about 20 blocks up, and the
// hook CUTS whatever the target was charging. C Chute: a step and the leg straight out: the target slides 12+ blocks
// back, guard broken, stunned. B Counter: he waits; the blow that comes is caught with one hand ("ok."), and a flick on
// the forehead sends them spinning away.
// Easier to land (Stark 2026-11-20, "deixar os golpes mais pegaveis"): more reach, a wider cone, a short step toward the
// one in front on Z/X/C (no homing: Z still goes where he looks), and the scenery answers each blow: the last punch of Z
// goes through leaves, glass and wood behind the target, X cracks the ground and sends the target up through leaves and
// wooden ceilings, C drags a groove, B leaves a crater where the target lands.
public final class OpmNormal {
    private static final double REACH = 4.4, CONE = 0.3, STEP_MAX = 3.0;

    private OpmNormal() {
    }

    // The one in front: the aimed target, or the nearest in a cone ahead.
    static LivingEntity front(LivingEntity caster, double range) {
        LivingEntity aimed = SubCommon.aimed(caster, range + 1);
        if (aimed != null) {
            return aimed;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
        for (LivingEntity e : Combat.inRadius((ServerLevel) caster.level(), eye, range + 1, caster)) {
            Vec3 to = Combat.center(e).subtract(eye);
            double d = to.length();
            if (d < bestD && to.normalize().dot(look) > CONE) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    private static Vec3 flatFrom(LivingEntity caster, LivingEntity target) {
        Vec3 d = target.position().subtract(caster.position());
        d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 1.0E-4 ? SubCommon.flat(caster.getLookAngle()) : d.normalize();
    }

    // A short step toward the one in front, flat (only when there is room to close; his own motion, so Pedra Viva keeps it).
    static void step(LivingEntity caster, LivingEntity target, double speed) {
        if (target == null || !caster.onGround()) {
            return;
        }
        Vec3 d = target.position().subtract(caster.position());
        d = new Vec3(d.x, 0, d.z);
        double gap = d.length() - (caster.getBbWidth() + target.getBbWidth()) * 0.5 - 0.8;
        if (gap <= 0.2) {
            return;
        }
        double v = Math.min(speed, Math.min(STEP_MAX, gap) * 0.42);
        caster.setDeltaMovement(d.normalize().scale(v).add(0, caster.getDeltaMovement().y, 0));
        caster.hurtMarked = true;
        Opm.own(caster);
    }

    // ---------- the scenery ----------

    // What his normal blows go through: leaves, glass, wood and plants (stone and the rest stay).
    static boolean soft(BlockState st) {
        return st.is(BlockTags.LEAVES) || st.is(Tags.Blocks.GLASS_BLOCKS) || st.is(Tags.Blocks.GLASS_PANES) || st.is(BlockTags.PLANKS)
                || st.is(BlockTags.WOODEN_SLABS) || st.is(BlockTags.WOODEN_STAIRS) || st.is(BlockTags.WOODEN_FENCES) || st.is(BlockTags.WOODEN_DOORS)
                || st.is(BlockTags.WOODEN_TRAPDOORS) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.REPLACEABLE);
    }

    // Breaks the soft blocks in a box round c (half sizes h, v), with the break's own particles and sound, no drops.
    static int smash(ServerLevel level, Vec3 c, double h, double v, int budget) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int n = 0;
        for (int x = Mth.floor(c.x - h); x <= Mth.floor(c.x + h) && n < budget; x++) {
            for (int y = Mth.floor(c.y - v); y <= Mth.floor(c.y + v) && n < budget; y++) {
                for (int z = Mth.floor(c.z - h); z <= Mth.floor(c.z + h) && n < budget; z++) {
                    p.set(x, y, z);
                    BlockState st = level.getBlockState(p);
                    if (!st.isAir() && soft(st) && st.getFluidState().isEmpty() && !BlockRules.keeps(st)) {
                        level.destroyBlock(p, false);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // Z's last punch: the air goes on behind the target, through what is soft, about 10 blocks.
    static void punchThrough(ServerLevel level, Vec3 from, Vec3 dir) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int budget = 90;
        for (int i = 1; i <= 10 && budget > 0; i++) {
            budget -= smash(level, from.add(dir.scale(i)), 1.0 + i * 0.08, 1.0 + i * 0.05, budget);
        }
    }

    // While a launched target flies: the soft blocks in its way go (a wooden roof, the treetops), for some ticks.
    static void plow(ServerLevel level, LivingEntity target, int ticks, boolean groove) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        Tasks.add(age -> {
            if (!target.isAlive() || target.isRemoved() || age > ticks) {
                return true;
            }
            Vec3 v = target.getDeltaMovement();
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0).add(v.scale(1.2));
            smash(level, c, target.getBbWidth() * 0.5 + 0.6, target.getBbHeight() * 0.5 + 0.6, 24);
            if (groove && target.onGround() && v.horizontalDistanceSqr() > 0.09) {
                // C: the heels cut the ground (the top block under the feet, only earth, sand and gravel)
                BlockPos under = target.blockPosition().below();
                BlockState st = level.getBlockState(under);
                if ((st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(Blocks.GRAVEL) || st.is(Blocks.SNOW_BLOCK)) && !BlockRules.keeps(st)) {
                    level.destroyBlock(under, false);
                }
            }
            return false;
        });
    }

    static void launch(ServerLevel level, LivingEntity caster, LivingEntity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        // (again next tick: the first can be eaten by the hit's own knockback)
        Tasks.later(1, () -> {
            if (target.isAlive()) {
                target.setDeltaMovement(velocity);
                target.hurtMarked = true;
            }
        });
        SubNet.send(level, SubNet.OPM_LAUNCH, target.getId(), caster.position(), velocity, 0, 0, 0);
    }

    // ---------- Z: Socos Consecutivos ----------

    static final double NS0 = 0.25, NSD = 0.075;
    static final int NSN = 14;

    static boolean socos(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity first = front(caster, REACH + 1);
        step(caster, first, 0.75);
        SubCommon.pose(caster, "opm.socos");
        SubCommon.busy(caster, 28);
        SubNet.send(level, SubNet.OPM_SOCOS, caster.getId(), caster.position(), caster.getLookAngle(), first == null ? -1 : first.getId(), 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        // The small punches' damage lands every 4th punch and with the last (the Escudo de tier takes a flat part off every
        // blow: thirteen 3s did nothing against a higher tier).
        java.util.Map<LivingEntity, Float> owed = new java.util.HashMap<>();
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
            double t0 = (age - 1) / 20.0, t1 = age / 20.0;
            for (int i = 0; i < NSN; i++) {
                double hitT = NS0 + i * NSD + NSD * 0.4;
                if (hitT <= t0 || hitT > t1) {
                    continue;
                }
                boolean last = i == NSN - 1;
                if (last) {
                    // the air of the last punch goes on past the target
                    Vec3 dir = SubCommon.flat(caster.getLookAngle());
                    punchThrough(level, caster.getEyePosition().add(dir.scale(REACH * 0.6)), dir);
                }
                for (LivingEntity target : Combat.inCone(caster, REACH, 0.35)) {
                    float due = owed.getOrDefault(target, 0f) + (last ? 10f : 3.5f);
                    if (last || i % 4 == 3) {
                        owed.remove(target);
                        Combat.hurt(caster, target, due);
                    } else {
                        owed.put(target, due);
                    }
                    if (last) {
                        launch(level, caster, target, flatFrom(caster, target).scale(0.9).add(0, 0.25, 0));
                        SubNet.send(level, SubNet.OPM_FLASH, target.getId(), Combat.center(target), Vec3.ZERO, 0.95f, 1, 0);
                        SubNet.send(level, SubNet.OPM_RING, 0, target.position(), Vec3.ZERO, 4, 0.8f, 0);
                        Stun.apply(target, 12);
                    } else {
                        // held in front of the fists (no knockback: they don't fall out of the barrage)
                        target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                        target.hurtMarked = true;
                        Stun.apply(target, 8);
                    }
                }
                SubCommon.sound(level, caster.getEyePosition().add(caster.getLookAngle().scale(2)), last ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                        last ? 1.2f : 0.6f, last ? 0.8f : 1.2f + 0.05f * i);
            }
            return t1 > NS0 + NSN * NSD + 0.1;
        });
        return true;
    }

    // ---------- X: Gancho ----------

    static boolean gancho(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        step(caster, front(caster, REACH + 1), 0.7);
        SubCommon.pose(caster, "opm.gancho");
        SubCommon.busy(caster, 10);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < 7) {
                return false;
            }
            LivingEntity target = front(caster, REACH);
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.9f);
            if (target == null) {
                return true;
            }
            // The hook cuts whatever the target was charging (Stark's rule).
            PowerData data = target.hasData(ModAttachments.POWER) ? target.getData(ModAttachments.POWER) : null;
            if (data != null && (data.busyTicks > 0 || Stamina.holding(target))) {
                PowerLogic.interrupt(target);
            }
            Combat.hurt(caster, target, 14f);
            Stun.apply(target, 30);
            // up about 25 blocks; it goes through leaves and wooden ceilings on the way, and the ground under it cracks
            if (target.onGround() && BlockRules.canBreak(level)) {
                OpmSerious.crack(level, target.position(), 4);
            }
            launch(level, caster, target, flatFrom(caster, target).scale(0.03).add(0, 1.9, 0));
            plow(level, target, 26, false);
            Vec3 c = target.position().add(0, 1.6, 0).subtract(flatFrom(caster, target).scale(0.35));
            SubNet.send(level, SubNet.OPM_FLASH, target.getId(), c, Vec3.ZERO, 0.85f, 1, 0);
            SubNet.send(level, SubNet.OPM_RING, 0, target.position(), Vec3.ZERO, 3.5f, 0.8f, 0);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.6f);
            return true;
        });
        return true;
    }

    // ---------- C: Chute ----------

    static boolean chute(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        step(caster, front(caster, REACH + 1.6), 0.8);
        SubCommon.pose(caster, "opm.chute");
        SubCommon.busy(caster, 9);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < 6) {
                return false;
            }
            LivingEntity target = front(caster, REACH + 0.6);
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
            if (target == null) {
                return true;
            }
            Defense.breakGuard(target, caster);
            Combat.hurt(caster, target, 12f);
            // (bosses take half: 25 left, still a real stun)
            Stun.apply(target, 50);
            Vec3 dir = flatFrom(caster, target);
            launch(level, caster, target, dir.scale(1.3).add(0, 0.22, 0));
            plow(level, target, 22, true);
            Vec3 c = target.position().add(0, 1.0, 0).subtract(dir.scale(0.45));
            SubNet.send(level, SubNet.OPM_FLASH, target.getId(), c, dir, 0.85f, 1, 0);
            SubNet.send(level, SubNet.OPM_RING, 0, caster.position().add(dir), Vec3.ZERO, 3f, 0.7f, 0);
            SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.5f);
            return true;
        });
        return true;
    }

    // ---------- B: Counter ----------

    static final int COUNTER_WINDOW = 12;

    static boolean counter(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.setState(caster, Opm.COUNTER, COUNTER_WINDOW);
        SubCommon.pose(caster, "opm.counter_espera");
        SubCommon.busy(caster, COUNTER_WINDOW);
        return true;
    }

    // The blow came: he holds the fist ("ok."), then flicks the forehead.
    static void caught(LivingEntity caster, ServerLevel level, LivingEntity enemy) {
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "opm.counter");
        SubCommon.busy(caster, 22);
        Freeze.hold(enemy, 20);
        Vec3 dir = flatFrom(caster, enemy);
        Vec3 fist = caster.position().add(0, 1.45, 0).add(dir.scale(0.95)).add(new Vec3(dir.z, 0, -dir.x).scale(-0.32));
        SubNet.send(level, SubNet.OPM_FLASH, caster.getId(), fist, Vec3.ZERO, 0.4f, 0, 0);
        SubNet.send(level, SubNet.OPM_SAY, caster.getId(), caster.position(), Vec3.ZERO, 0, 0, 0);
        SubCommon.sound(level, fist, SoundEvents.SHIELD_BLOCK, 1f, 1.4f);
        PowerData data = caster.getData(ModAttachments.POWER);
        data.ult = Math.min(PowerData.MAX_ULT, data.ult + 15);
        Tasks.add(age -> {
            if (guard.broken() || !enemy.isAlive()) {
                return true;
            }
            if (age < 19) {
                return false;
            }
            Vec3 head = enemy.position().add(0, enemy.getBbHeight() * 0.92, 0).subtract(dir.scale(0.3));
            Combat.hurt(caster, enemy, 18f);
            boolean boss = enemy instanceof SoulBoss b && b.isBoss();
            if (boss) {
                // Against a boss the flick only pushes (and a real stun).
                launch(level, caster, enemy, dir.scale(0.6).add(0, 0.2, 0));
            } else {
                launch(level, caster, enemy, dir.scale(1.7).add(0, 0.45, 0));
            }
            Stun.apply(enemy, 50);
            landing(level, enemy);
            SubNet.send(level, SubNet.OPM_FLASH, enemy.getId(), head, dir, 0.8f, 2, 0);
            SubCommon.sound(level, head, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 1.6f);
            SubCommon.sound(level, head, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.7f);
            return true;
        });
    }

    // B: where the flicked one comes down, the ground gives (a late pop: cracks round a small crater).
    static void landing(ServerLevel level, LivingEntity enemy) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        plow(level, enemy, 30, false);
        Tasks.add(age -> {
            if (!enemy.isAlive() || enemy.isRemoved() || age > 60) {
                return true;
            }
            if (age < 4 || !enemy.onGround()) {
                return false;
            }
            Vec3 at = enemy.position();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int top = Mth.floor(at.y) - 1;
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    int depth = r < 1.2 ? 2 : r < 2.3 ? 1 : 0;
                    for (int d = 0; d < depth; d++) {
                        p.set(Mth.floor(at.x) + x, top - d, Mth.floor(at.z) + z);
                        BlockState st = level.getBlockState(p);
                        if (OpmSerious.breakable(level, p, st)) {
                            level.destroyBlock(p, false);
                        }
                    }
                }
            }
            OpmSerious.crack(level, at.add(0, -1, 0), 4.5);
            SubNet.send(level, SubNet.OPM_RING, 0, at, Vec3.ZERO, 3.5f, 0.8f, 0);
            SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.3f);
            return true;
        });
    }
}
