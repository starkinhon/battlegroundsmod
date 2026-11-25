// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.opm;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.boss.SoulBoss;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

// Saitama's normal mode, remade 2026-11-25 (forja/saitama-jogavel, "os socos normais estão aprovadíssimos"): a bored face,
// but the blows of a divine soul. Every move steps up to the one in front (6 blocks), and each has its own air (the client):
// Z Socos Consecutivos: 30 punches in 1.35 s, every arm that shows lands (a hit every second punch: the Escudo de tier takes a
// flat part off each blow, so they come in pairs of 8), the air of the punches goes on 8 blocks past the target and takes
// whoever is behind it; the last throws the target 25+ blocks. X Gancho: a spiral column of air 26 high, a crater of 6 and the
// target 30 up, cutting whatever it was charging (whoever is within 4 goes up too). C Chute: a flat crescent of air runs 16
// blocks over the ground cutting a groove, the target dragged along, guard broken. B Counter: the blow caught with one hand
// ("ok."), a flick that opens a crater of 8 where they land. Shift+Z Soco Normal: a fist of wind that flies 25 blocks.
// Shift+C Pulo + Soco no Chao: a jump of up to 24 blocks, the punch in the ground, cracks running 15 out and the cracked
// ground blowing up. Numbers from the approved card: ~120 / 50 / 45 / 80, 40, 55; stuns never under 25 ticks.
public final class OpmNormal {
    private static final double REACH = 4.4, CONE = 0.3, STEP_MAX = 6.0;

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

    // A quick step toward the one in front, flat, up to STEP_MAX blocks (only when there is room to close).
    static void step(LivingEntity caster, LivingEntity target) {
        if (target == null || !caster.onGround()) {
            return;
        }
        Vec3 d = target.position().subtract(caster.position());
        d = new Vec3(d.x, 0, d.z);
        double gap = d.length() - (caster.getBbWidth() + target.getBbWidth()) * 0.5 - 0.9;
        if (gap <= 0.2) {
            return;
        }
        // (the ground's friction leaves about 2.2x the speed as distance)
        double v = Math.min(2.6, Math.min(STEP_MAX, gap) * 0.46);
        caster.setDeltaMovement(d.normalize().scale(v).add(0, caster.getDeltaMovement().y, 0));
        caster.hurtMarked = true;
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

    // The air of a blow goes on behind the target, through what is soft, about len blocks.
    static void punchThrough(ServerLevel level, Vec3 from, Vec3 dir, int len) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int budget = 90;
        for (int i = 1; i <= len && budget > 0; i++) {
            budget -= smash(level, from.add(dir.scale(i)), 1.0 + i * 0.08, 1.0 + i * 0.05, budget);
        }
    }

    // A small bowl in the ground where the air of a blow dug (only near the ground, never under someone flying).
    static void dent(ServerLevel level, Vec3 at, double rr, double deep) {
        int x = Mth.floor(at.x), z = Mth.floor(at.z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(top - at.y) > 3) {
            return;
        }
        OpmSerious.dent(level, new Vec3(at.x, top, at.z), rr, deep);
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

    private static boolean boss(LivingEntity e) {
        return e instanceof SoulBoss b && b.isBoss();
    }

    // ---------- Z: Socos Consecutivos ----------

    // The page's numbers: the first punch 0.3 s in, one every 0.045 s, 30 of them (the client draws the same clock).
    public static final double NS0 = 0.3, NSD = 0.045;
    public static final int NSN = 30;
    private static final float SOCO_DMG = 8f, SOCO_LAST = 14f;

    static boolean socos(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        LivingEntity first = front(caster, REACH + STEP_MAX);
        step(caster, first);
        SubCommon.pose(caster, "opm.socos");
        int end = (int) Math.ceil((NS0 + NSN * NSD) * 20) + 6;
        SubCommon.busy(caster, end);
        SubNet.send(level, SubNet.OPM_SOCOS, caster.getId(), caster.position(), caster.getLookAngle(), first == null ? -1 : first.getId(), 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 2, false, false));
            double t0 = (age - 1) / 20.0, t1 = age / 20.0;
            Vec3 dir = SubCommon.flat(caster.getLookAngle());
            for (int i = 0; i < NSN; i++) {
                double hitT = NS0 + i * NSD + NSD * 0.4;
                if (hitT <= t0 || hitT > t1) {
                    continue;
                }
                boolean last = i == NSN - 1, lands = last || i % 2 == 1;
                Vec3 eye = caster.getEyePosition();
                // the one in front, and the air of the punches 8 blocks past it
                Set<LivingEntity> hit = new LinkedHashSet<>(Combat.inCone(caster, REACH, 0.35));
                hit.addAll(Combat.alongLine(level, eye, eye.add(dir.scale(REACH + 8)), last ? 3.2 : 2.6, caster));
                for (LivingEntity target : hit) {
                    if (lands) {
                        Combat.hurt(caster, target, last ? SOCO_LAST : SOCO_DMG);
                    }
                    if (last) {
                        launch(level, caster, target, flatFrom(caster, target).scale(boss(target) ? 0.7 : 1.6).add(0, 0.4, 0));
                        SubNet.send(level, SubNet.OPM_FLASH, target.getId(), Combat.center(target), Vec3.ZERO, 1.1f, 1, 0);
                        Stun.apply(target, 25);
                        plow(level, target, 24, false);
                    } else {
                        // held in front of the fists, pushed back a little each punch (they don't fall out of the barrage)
                        target.setDeltaMovement(dir.x * 0.12, Math.min(0, target.getDeltaMovement().y), dir.z * 0.12);
                        target.hurtMarked = true;
                        Stun.apply(target, 12);
                    }
                }
                if (!last) {
                    // he keeps up with the one he pushes
                    caster.setDeltaMovement(dir.x * 0.12, caster.getDeltaMovement().y, dir.z * 0.12);
                    caster.hurtMarked = true;
                    if (i % 3 == 0 && BlockRules.canBreak(level)) {
                        Vec3 at = caster.position().add(dir.scale(3 + SubCommon.RANDOM.nextDouble() * 5))
                                .add(new Vec3(dir.z, 0, -dir.x).scale((SubCommon.RANDOM.nextDouble() - 0.5) * 2.4));
                        dent(level, at, 0.9, 1);
                    }
                } else {
                    // the air of the last punch goes on past the target: soft blocks, a line of holes in the ground
                    punchThrough(level, eye.add(dir.scale(REACH * 0.6)), dir, 12);
                    if (BlockRules.canBreak(level)) {
                        for (int j = 0; j < 6; j++) {
                            dent(level, caster.position().add(dir.scale(3 + j * 2)), 1.2, 1);
                        }
                    }
                    SubNet.send(level, SubNet.OPM_RING, 0, caster.position().add(dir.scale(3)), Vec3.ZERO, 7, 0.9f, 0);
                }
                SubCommon.sound(level, eye.add(dir.scale(2)), last ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                        last ? 1.3f : 0.6f, last ? 0.8f : 1.2f + 0.02f * i);
                if (last) {
                    SubCommon.sound(level, eye.add(dir.scale(3)), SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.5f);
                }
            }
            return t1 > NS0 + NSN * NSD + 0.1;
        });
        return true;
    }

    // ---------- X: Gancho ----------

    static boolean gancho(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        step(caster, front(caster, REACH + STEP_MAX));
        SubCommon.pose(caster, "opm.gancho");
        SubCommon.busy(caster, 12);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < 7) {
                return false;
            }
            LivingEntity target = front(caster, REACH + 0.6);
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.9f);
            if (target == null) {
                return true;
            }
            // The hook cuts whatever the target was charging (Stark's rule).
            PowerData data = target.hasData(ModAttachments.POWER) ? target.getData(ModAttachments.POWER) : null;
            if (data != null && (data.busyTicks > 0 || Stamina.holding(target))) {
                PowerLogic.interrupt(target);
            }
            Vec3 c = target.position();
            Combat.hurt(caster, target, 50f);
            Stun.apply(target, 30);
            // a crater of 6 under it and up about 30 blocks, through leaves and wooden ceilings on the way
            if (target.onGround() && BlockRules.canBreak(level)) {
                dent(level, c, 3.2, 2);
            }
            launch(level, caster, target, flatFrom(caster, target).scale(0.03).add(0, boss(target) ? 1.2 : 2.2, 0));
            plow(level, target, 30, false);
            // whoever is within 4 of it goes up too
            for (LivingEntity e : Combat.inRadius(level, c, 4, caster)) {
                if (e != target) {
                    Combat.hurt(caster, e, 25f);
                    Stun.apply(e, 25);
                    Vec3 out = SubCommon.flat(e.position().subtract(c));
                    launch(level, caster, e, out.scale(0.25).add(0, 1.7, 0));
                }
            }
            Vec3 p = c.add(0, 1.6, 0).subtract(flatFrom(caster, target).scale(0.35));
            SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), c, flatFrom(caster, target), 1, target.getId(), 0);
            SubNet.send(level, SubNet.OPM_FLASH, target.getId(), p, Vec3.ZERO, 1.0f, 1, 0);
            SubNet.send(level, SubNet.OPM_RING, 0, c, Vec3.ZERO, 9, 1.0f, 0);
            SubCommon.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.4f);
            return true;
        });
        return true;
    }

    // ---------- C: Chute ----------

    static boolean chute(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        step(caster, front(caster, REACH + STEP_MAX));
        SubCommon.pose(caster, "opm.chute");
        SubCommon.busy(caster, 10);
        Tasks.add(age -> {
            if (guard.broken()) {
                return true;
            }
            if (age < 6) {
                return false;
            }
            LivingEntity target = front(caster, REACH + 1.2);
            Vec3 dir = target != null ? flatFrom(caster, target) : SubCommon.flat(caster.getLookAngle());
            SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
            SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), caster.position(), dir, 2, target == null ? -1 : target.getId(), 0);
            if (target != null) {
                Defense.breakGuard(target, caster);
                Combat.hurt(caster, target, 45f);
                Stun.apply(target, 30);
                launch(level, caster, target, dir.scale(boss(target) ? 0.8 : 1.9).add(0, 0.25, 0));
                plow(level, target, 26, true);
                Vec3 c = target.position().add(0, 1.0, 0).subtract(dir.scale(0.45));
                SubNet.send(level, SubNet.OPM_FLASH, target.getId(), c, dir, 1.0f, 1, 0);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
            }
            // the crescent of air runs 16 blocks over the ground: a groove, and whoever is in the line is taken along
            groove(level, caster, caster.position().add(dir.scale(1.5)), dir, target);
            return true;
        });
        return true;
    }

    private static void groove(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir, LivingEntity target) {
        Set<LivingEntity> done = new HashSet<>();
        if (target != null) {
            done.add(target);
        }
        int[] dug = {0};
        Tasks.add(age -> {
            int front = (int) Math.round(16 * OpmShape.smooth(age / 8.0));
            for (; dug[0] < front; dug[0]++) {
                Vec3 at = from.add(dir.scale(dug[0]));
                if (BlockRules.canBreak(level)) {
                    dent(level, at, 0.85, 1);
                }
                for (LivingEntity e : Combat.inRadius(level, at.add(0, 0.8, 0), 2.2, caster)) {
                    if (done.add(e)) {
                        Combat.hurt(caster, e, 22f);
                        Stun.apply(e, 25);
                        launch(level, caster, e, dir.scale(1.4).add(0, 0.3, 0));
                    }
                }
            }
            return age >= 8;
        });
    }

    // ---------- B: Counter ----------

    static final int COUNTER_WINDOW = 16;

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
        SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), fist, dir, 3, enemy.getId(), 0);
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
            Combat.hurt(caster, enemy, 80f);
            // Against a boss the flick only pushes (and a real stun).
            launch(level, caster, enemy, boss(enemy) ? dir.scale(0.6).add(0, 0.2, 0) : dir.scale(1.9).add(0, 0.5, 0));
            Stun.apply(enemy, 50);
            landing(level, caster, enemy);
            SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), head, dir, 4, enemy.getId(), 0);
            SubNet.send(level, SubNet.OPM_FLASH, enemy.getId(), head, dir, 1.0f, 2, 0);
            SubCommon.sound(level, head, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 1.6f);
            SubCommon.sound(level, head, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.7f);
            return true;
        });
    }

    // B: where the flicked one comes down, a crater of 8 opens and whoever is near flies.
    static void landing(ServerLevel level, LivingEntity caster, LivingEntity enemy) {
        plow(level, enemy, 30, false);
        Tasks.add(age -> {
            if (!enemy.isAlive() || enemy.isRemoved() || age > 60) {
                return true;
            }
            if (age < 4 || !enemy.onGround()) {
                return false;
            }
            Vec3 at = enemy.position();
            if (BlockRules.canBreak(level)) {
                OpmSerious.dent(level, at, 4.2, 2.6);
                OpmSerious.crack(level, at.add(0, -1, 0), 6);
            }
            for (LivingEntity e : Combat.inRadius(level, at, 8, caster)) {
                if (e != enemy) {
                    Combat.hurt(caster, e, 20f);
                    Stun.apply(e, 25);
                    Vec3 out = SubCommon.flat(e.position().subtract(at));
                    launch(level, caster, e, out.scale(1.1).add(0, 0.6, 0));
                }
            }
            SubNet.send(level, SubNet.OPM_RING, 0, at, Vec3.ZERO, 12, 1.1f, 0);
            SubNet.send(level, SubNet.OPM_FLASH, enemy.getId(), at.add(0, 0.5, 0), Vec3.ZERO, 1.2f, 1, 0);
            SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.1f);
            return true;
        });
    }

    // ---------- Shift+Z: Soco Normal (the fist of wind) ----------

    // The punch 0.24 s in; the fist of wind flies 40 blocks a second (2 a tick) for 25 blocks.
    public static final int SN_PUNCH = 5;
    public static final double SN_LEN = 25, SN_SPEED = 2.0;

    static boolean socoNormal(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "opm.soco_normal");
        SubCommon.busy(caster, SN_PUNCH + 6);
        Set<LivingEntity> done = new HashSet<>();
        Vec3[] start = {null}, dir = {null};
        double[] dug = {0};
        Tasks.add(age -> {
            if (start[0] == null && guard.broken()) {
                return true;
            }
            if (age < SN_PUNCH) {
                return false;
            }
            if (start[0] == null) {
                // where it goes: the one aimed at (up to 30), else the crosshair, pitch included (it reaches whoever flies)
                LivingEntity aimed = SubCommon.aimed(caster, 30);
                Vec3 eye = caster.getEyePosition().add(0, -0.25, 0);
                dir[0] = aimed != null ? Combat.center(aimed).subtract(eye).normalize() : caster.getLookAngle();
                start[0] = eye.add(dir[0].scale(1.0));
                SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), start[0], dir[0], 5, 0, 0);
                SubCommon.sound(level, start[0], SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.7f);
                SubCommon.sound(level, start[0], SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2f, 0.7f);
            }
            double front = Math.min(SN_LEN, (age - SN_PUNCH + 1) * SN_SPEED);
            Vec3 a = start[0].add(dir[0].scale(Math.max(0, front - SN_SPEED))), b = start[0].add(dir[0].scale(front));
            for (LivingEntity e : Combat.alongLine(level, a, b, 2.2, caster)) {
                if (done.add(e)) {
                    Combat.hurt(caster, e, 40f);
                    Stun.apply(e, 25);
                    Vec3 push = SubCommon.flat(dir[0]);
                    launch(level, caster, e, push.scale(boss(e) ? 0.6 : 1.4).add(0, 0.3, 0));
                    SubNet.send(level, SubNet.OPM_FLASH, e.getId(), Combat.center(e), dir[0], 0.9f, 1, 0);
                }
            }
            // the ground under it torn up (only where it runs low)
            if (BlockRules.canBreak(level)) {
                for (; dug[0] < front; dug[0] += 3) {
                    dent(level, start[0].add(dir[0].scale(dug[0] + 1.5)).add(0, -1.1, 0), 0.75, 1);
                }
                smash(level, b, 1.6, 1.6, 20);
            }
            return front >= SN_LEN;
        });
        return true;
    }

    // ---------- Shift+C: Pulo + Soco no Chao ----------

    // Crouch until 0.28 s, in the air until 1.1 s (10 high at the top), the punch in the ground; the cracks run 15 out in half
    // a second and at 1.65 s the cracked ground blows up.
    public static final int PJ_UP = 6, PJ_LAND = 22, PJ_BLOW = 33;
    public static final double PJ_DIST = 24, PJ_HIGH = 10, PJ_R = 15;

    static boolean pulo(LivingEntity caster, ServerLevel level) {
        Opm.track(caster);
        Vec3 from = caster.position();
        Vec3 dir = SubCommon.flat(caster.getLookAngle());
        // how far: the one aimed at (up to 24), else the crosshair's point, else the whole 24; never onto a cliff or into a hole
        LivingEntity aimed = SubCommon.aimed(caster, PJ_DIST + 4);
        double dist = PJ_DIST;
        if (aimed != null) {
            dist = Math.min(PJ_DIST, Math.max(2, SubCommon.flat(aimed.position().subtract(from)).dot(dir) - 1.1));
        } else {
            Vec3 p = Combat.raycastPoint(caster, PJ_DIST + 8);
            double d = new Vec3(p.x - from.x, 0, p.z - from.z).length();
            if (d > 2 && d < PJ_DIST) {
                dist = d;
            }
        }
        Vec3 to = null;
        for (double d = dist; d >= 1; d -= 1) {
            Vec3 at = from.add(dir.scale(d));
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
            if (Math.abs(top - from.y) <= 9) {
                to = new Vec3(at.x, top, at.z);
                break;
            }
        }
        if (to == null) {
            to = from;
        }
        Vec3 land = to;
        SubCommon.Guard guard = SubCommon.guard(caster);
        SubCommon.pose(caster, "opm.pulo");
        SubCommon.busy(caster, PJ_BLOW + 4);
        caster.getData(ModAttachments.POWER).noFallTicks = Math.max(caster.getData(ModAttachments.POWER).noFallTicks, PJ_LAND + 30);
        Runnable release = Freeze.holdLookFree(caster, PJ_LAND + 4, age -> {
            if (age < PJ_UP) {
                return from;
            }
            double k = Math.min(1, (age - PJ_UP) / (double) (PJ_LAND - PJ_UP));
            return from.lerp(land, k).add(0, PJ_HIGH * 4 * k * (1 - k), 0);
        });
        SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), land, land.subtract(from), 6, 0, 0);
        Vec3 c = land.add(dir.scale(1.1));
        Tasks.add(age -> {
            if (guard.broken() && age < PJ_LAND) {
                release.run();
                return true;
            }
            if (age == PJ_UP) {
                // the push off: a small dent where he stood
                if (BlockRules.canBreak(level)) {
                    OpmSerious.dent(level, from, 1.4, 1);
                }
                SubCommon.sound(level, from, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.6f);
            }
            if (age == PJ_LAND) {
                SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), c, dir, 7, 0, 0);
                SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
                if (BlockRules.canBreak(level)) {
                    OpmSerious.dent(level, c, 1.8, 1.5);
                }
                for (LivingEntity e : Combat.inRadius(level, c, 3, caster)) {
                    Combat.hurt(caster, e, 10f);
                    Stun.apply(e, 12);
                }
            }
            // the cracks run out (the top block along jagged lines)
            if (age > PJ_LAND && age <= PJ_LAND + 10 && (age - PJ_LAND) % 3 == 1 && BlockRules.canBreak(level)) {
                OpmSerious.crack(level, c, PJ_R * Math.min(1, (age - PJ_LAND) / 10.0) + 1);
            }
            if (age == PJ_BLOW) {
                SubNet.send(level, SubNet.OPM_NORMAL, caster.getId(), c, dir, 8, 0, 0);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.7f);
                SubCommon.sound(level, c, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.2f, 0.6f);
                if (BlockRules.canBreak(level)) {
                    OpmSerious.sink(level, c, new double[] {PJ_R, 1.8});
                }
                for (LivingEntity e : Combat.inRadius(level, c, PJ_R, caster)) {
                    double d = SubCommon.flat(e.position().subtract(c)).length();
                    if (Math.abs(e.getY() - c.y) > 6) {
                        continue;
                    }
                    Combat.hurt(caster, e, 55f);
                    Stun.apply(e, 25);
                    Vec3 out = SubCommon.flat(e.position().subtract(c));
                    launch(level, caster, e, out.scale(0.15 + 0.25 * d / PJ_R).add(0, boss(e) ? 0.5 : 0.95, 0));
                }
                return true;
            }
            return false;
        });
        return true;
    }
}
