// Souls / Battlegrounds Mod - Copyright (c) 2026 Stark (starkinhon). All Rights Reserved. Creditos: Stark e Claude.
// Proibido copiar, descompilar, extrair ou usar para treinar/alimentar IA. Ver LICENSE.
package com.starkinhon.battlegroundsmod.power.powers.toji;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.starkinhon.battlegroundsmod.ModItems;
import com.starkinhon.battlegroundsmod.boss.TojiEntity;
import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.Ruin;
import com.starkinhon.battlegroundsmod.combat.RuinNet;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.Ability;
import com.starkinhon.battlegroundsmod.power.Power;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.power.powers.DomainSpace;
import com.starkinhon.battlegroundsmod.power.powers.freezaboss.FzNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;
import com.starkinhon.battlegroundsmod.power.powers.sub.jj.JjCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.nr.NrCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Toji Fushiguro, the Jujutsu end-game boss (Stark 2026-11-22: "agora sim ficou de respeito", "cara de chefe final
// MESMO, pode portar"). Ported from the approved page (forja/toji-boss, https://claude.ai/artifact/GEdgJDyAi3AWKgVeUKarta):
// the page's seconds are 20 ticks, its units are blocks, Shibuya's street is his feet. No cursed energy at all: fast,
// strong, the mod's own weapons in his hands (the Lança Invertida, the Nuvem Brincalhona), and the city coming down.
//   Z  Sumiço: he crouches and is gone. Six hops round the target over the roofs, each one a crater of 7 in the roof and
//      a cloud of dust (count them: the 7th is at your back), then the kick from behind that throws the target and splits
//      every building in a line of 120 down to the street.
//   X  Nuvem Brincalhona (the mod's three-section staff, far stronger in his arms), a combo of three: the spin cuts the
//      top off everything round him to 64 in a slant (3 blocks up next to him, higher far out: don't jump), the leap
//      and the slam open a furrow of 100 through the buildings, and the typhoon pulls everything in and lets go: a
//      crater of 30.
//   C  Corrente de Mil Milhas (TjChain): he turns the spear on its endless chain round himself like a fan, paying chain
//      out to 60; everything the chain passes is cut at its height (the block turns to stumps). Hop over it, stay glued
//      to him inside the spin, or run out of its reach.
//   V  Caçador de Feiticeiros (the ult, only under 25%): he backs off to the end of the street, crouches, runs it in
//      0.7 s tearing the asphalt, leaps 70 and comes down spear first: a crater of 32, cracks to 100, and the buildings
//      round it topple whole, outward. His shadow on the ground marks where; the leap is his weak moment.
//   B  Lança Invertida do Céu: the spear that cancels whatever technique it touches. An expansion he is caught in shatters
//      into shards; the thrust cuts the target's techniques (sealed 2.5 s) and opens a corridor of 140.
public final class Toji {
    public static final String ID = "toji";
    public static final int COLOR = 0xB8B4BE;
    static final RandomSource RNG = RandomSource.createThreadSafe();
    static final int DUST = 0x5E5A62;
    static final int DUST_LIGHT = 0xB5B1BA;
    private static final double VIEW = 320;

    private Toji() {
    }

    public static Power create() {
        return new Power(ID, COLOR, 1.4f, List.of(
                new Ability(220, 0, Toji::sumico),
                new Ability(380, 0, Toji::nuvem),
                new Ability(560, 0, TjChain::corrente),
                new Ability(1000, 0, Toji::cacador),
                new Ability(320, 0, Toji::lanca)),
                Arrays.asList());
    }

    // ---------- shared bits ----------

    // His own effects reach as far as the destruction does (RuinNet's 320, not SubNet's 160).
    static void send(ServerLevel level, int kind, int id, Vec3 pos, Vec3 dir, float a, float b, float c) {
        ModPayloads.SubFx payload = new ModPayloads.SubFx(kind, id, pos, dir, a, b, c);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < VIEW * VIEW) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    // The street under his feet: the block he stands on.
    static int base(LivingEntity e) {
        return Mth.floor(e.getY() - 0.5);
    }

    static Vec3 flatDir(Vec3 from, Vec3 to, LivingEntity fallback) {
        Vec3 d = SubCommon.flat(to.subtract(from));
        if (d.lengthSqr() < 1.0E-6) {
            d = SubCommon.flat(fallback.getLookAngle());
        }
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    static void face(LivingEntity e, Vec3 dir) {
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90;
        e.setYRot(yaw);
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
    }

    // Stands still for this tick (gravity on).
    static void still(LivingEntity caster) {
        caster.setDeltaMovement(0, Math.min(0, caster.getDeltaMovement().y), 0);
        if (caster instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        SubCommon.busy(caster, 3);
    }

    // Held in the air at p this tick.
    static void hold(LivingEntity caster, Vec3 p) {
        caster.setNoGravity(true);
        caster.setDeltaMovement(Vec3.ZERO);
        caster.setPos(p.x, p.y, p.z);
        caster.fallDistance = 0;
        if (caster instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        SubCommon.busy(caster, 3);
    }

    static void weapon(LivingEntity caster, ItemStack stack) {
        caster.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    static ItemStack spear() {
        return new ItemStack(ModItems.INVERTED_SPEAR.get());
    }

    static void armor(LivingEntity caster, int ticks) {
        if (caster instanceof TojiEntity t) {
            t.armor(ticks);
        }
    }

    static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    // Who stands in an upright cylinder at base (radius r, from below under it to h over it), the caster left out.
    static List<LivingEntity> near(ServerLevel level, Vec3 base, double r, double below, double h, LivingEntity caster) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(base.x - r - 1, base.y - below, base.z - r - 1, base.x + r + 1, base.y + h, base.z + r + 1),
                e -> Combat.isTarget(e, caster))) {
            double dx = e.getX() - base.x, dz = e.getZ() - base.z;
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() * 0.5 <= r) {
                out.add(e);
            }
        }
        return out;
    }

    // Who stands within w (flat) of the segment A-B, between y0 and y1.
    static List<LivingEntity> line(ServerLevel level, Vec3 A, Vec3 B, double w, double y0, double y1, LivingEntity caster) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(Math.min(A.x, B.x) - w - 1, y0, Math.min(A.z, B.z) - w - 1, Math.max(A.x, B.x) + w + 1, y1, Math.max(A.z, B.z) + w + 1);
        double vx = B.x - A.x, vz = B.z - A.z, L2 = Math.max(1.0E-6, vx * vx + vz * vz);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> Combat.isTarget(e, caster))) {
            double k = Mth.clamp(((e.getX() - A.x) * vx + (e.getZ() - A.z) * vz) / L2, 0, 1);
            double d = Math.hypot(e.getX() - A.x - vx * k, e.getZ() - A.z - vz * k);
            if (d - e.getBbWidth() * 0.5 <= w) {
                out.add(e);
            }
        }
        return out;
    }

    static void hurt(LivingEntity caster, LivingEntity t, float dmg) {
        Combat.hurtMagic(caster, t, dmg);
    }

    static void stun(LivingEntity caster, LivingEntity t, int ticks) {
        int n = JjCommon.control(caster, t, ticks);
        // bosses cut stuns in half: never under 25 there
        n = Math.max(n, JjCommon.isBoss(t) ? 50 : 25);
        Stun.apply(t, n);
    }

    static void dust(ServerLevel level, Vec3 at, double R, double H, int life) {
        RuinNet.dust(level, at, R, H, life, Math.max(4, life / 8), DUST);
    }

    static void hitFx(ServerLevel level, LivingEntity e) {
        dust(level, e.position(), 5, 4, 32);
        SubCommon.sound(level, e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
        SubCommon.sound(level, e.position(), SoundEvents.ANVIL_LAND, 0.7f, 1.6f);
    }

    // ---------- the buildings ----------

    // The buildings round (cx, cz) between r0 and r1: upright boxes standing at least minH over the street, found column
    // by column from the heightmap (a flood over the tall columns, at most 20 x 20 wide each). Tallest first; each is
    // {x0, z0, x1, z1, top}.
    static List<int[]> buildings(ServerLevel level, double cx, double cz, double r0, double r1, int street, int minH, int max) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int rr = Mth.ceil(r1), x0 = Mth.floor(cx), z0 = Mth.floor(cz);
        for (int dz = -rr; dz <= rr; dz += 3) {
            for (int dx = -rr; dx <= rr; dx += 3) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int sx = x0 + dx, sz = z0 + dz;
                if (r < r0 || r > r1 || seen.contains(BlockPos.asLong(sx, 0, sz)) || !level.hasChunk(sx >> 4, sz >> 4)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz) - 1;
                if (top - street < minH) {
                    continue;
                }
                int[] box = {sx, sz, sx, sz, top};
                java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
                q.add(new int[] {sx, sz});
                seen.add(BlockPos.asLong(sx, 0, sz));
                while (!q.isEmpty()) {
                    int[] c = q.poll();
                    for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int x = c[0] + n[0], z = c[1] + n[1];
                        if (Math.abs(x - sx) > 10 || Math.abs(z - sz) > 10 || !seen.add(BlockPos.asLong(x, 0, z)) || !level.hasChunk(x >> 4, z >> 4)) {
                            continue;
                        }
                        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                        if (h - street < 6) {
                            continue;
                        }
                        box[0] = Math.min(box[0], x);
                        box[1] = Math.min(box[1], z);
                        box[2] = Math.max(box[2], x);
                        box[3] = Math.max(box[3], z);
                        box[4] = Math.max(box[4], h);
                        q.add(new int[] {x, z});
                    }
                }
                if (box[2] - box[0] >= 3 && box[3] - box[1] >= 3) {
                    out.add(box);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b[4], a[4]));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // A building falls over whole the way dirCode says (0 +x, 1 -x, 2 +z, 3 -z): the client draws it from a copy of its
    // blocks, the server takes them away as it starts and crushes whatever it lands on.
    static void topple(ServerLevel level, LivingEntity caster, int[] b, int street, int dirCode, int delay, int seed) {
        if (!BlockRules.canBreak(level)) {
            return;
        }
        int h = b[4] - street;
        if (h < 6) {
            return;
        }
        send(level, TjNet.TOPPLE, seed, new Vec3(b[0], street + 1, b[1]), new Vec3(b[2] - b[0] + 1, h, b[3] - b[1] + 1), dirCode, delay, seed);
        Ruin.clearBox(level, b[0], b[1], b[2], b[3], street, delay + 2);
        // it takes about 1.3 s to come down (the client's fall), then lies along the ground
        int fall = delay + 26;
        int lx0, lz0, lx1, lz1;
        switch (dirCode) {
            case 0 -> { lx0 = b[2] + 1; lx1 = b[2] + h; lz0 = b[1]; lz1 = b[3]; }
            case 1 -> { lx0 = b[0] - h; lx1 = b[0] - 1; lz0 = b[1]; lz1 = b[3]; }
            case 2 -> { lx0 = b[0]; lx1 = b[2]; lz0 = b[3] + 1; lz1 = b[3] + h; }
            default -> { lx0 = b[0]; lx1 = b[2]; lz0 = b[1] - h; lz1 = b[1] - 1; }
        }
        int fx0 = lx0, fz0 = lz0, fx1 = lx1, fz1 = lz1;
        Ruin.clearBox(level, fx0, fz0, fx1, fz1, street + 2, fall);
        Tasks.later(fall, () -> {
            Vec3 mid = new Vec3((fx0 + fx1) * 0.5 + 0.5, street + 1, (fz0 + fz1) * 0.5 + 0.5);
            Vec3 far = new Vec3(dirCode == 0 ? fx1 : dirCode == 1 ? fx0 : mid.x, street + 1, dirCode == 2 ? fz1 : dirCode == 3 ? fz0 : mid.z);
            RuinNet.dust(level, mid, Math.max(10, h * 0.45), 8, 90, 12, DUST);
            RuinNet.tremor(level, mid, 0.9, 30, 50);
            RuinNet.quake(level, mid, 1.1, false, 0xFFFFFF);
            SubCommon.sound(level, mid, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.45f);
            SubCommon.sound(level, far, SoundEvents.ANVIL_LAND, 3f, 0.4f);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(fx0, street, fz0, fx1 + 1, street + 8, fz1 + 1), e -> Combat.isTarget(e, caster))) {
                hurt(caster, e, 40);
                stun(caster, e, 25);
            }
        });
    }

    private static int dirCode(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 0 : 1) : (dz > 0 ? 2 : 3);
    }

    // ================= Z: Sumiço =================

    static boolean sumico(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 60);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 start = caster.position();
        int street = base(caster);
        armor(caster, 60);
        SubCommon.pose(caster, "nr.salto");
        Vec3[] hops = new Vec3[6];
        double a0 = RNG.nextDouble() * Mth.TWO_PI, turn = RNG.nextBoolean() ? 1 : -1;
        int[] hi = {0};
        Vec3[] kickDir = {null};
        Vec3[] behind = {null};
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive()) {
                caster.setInvisible(false);
                return true;
            }
            SubCommon.busy(caster, 3);
            if (age < 5) {
                still(caster);
                return false;
            }
            if (age == 5) {
                // gone: a little crater where he pushed off
                caster.setInvisible(true);
                Intangible.hold(caster, 40);
                Ruin.bowl(level, start.x, start.z, 5, 2, 4, 0, Ruin.opts().rim(0.1));
                RuinNet.ring(level, start, 32, 2.5, false, 20, DUST_LIGHT);
                dust(level, start, 7, 4, 40);
                SubCommon.sound(level, start, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 1.4f);
                Vec3 tc = target.position();
                for (int i = 0; i < 6; i++) {
                    double a = a0 + turn * i * Mth.TWO_PI / 6 + (RNG.nextDouble() - 0.5) * 0.5, r = 18 + RNG.nextDouble() * 20;
                    int x = Mth.floor(tc.x + Math.cos(a) * r), z = Mth.floor(tc.z + Math.sin(a) * r);
                    int g = level.hasChunk(x >> 4, z >> 4) ? Ruin.ground(level, x, z) : street;
                    hops[i] = new Vec3(x + 0.5, g + 1, z + 0.5);
                }
                send(level, TjNet.GHOST, caster.getId(), start, Vec3.ZERO, caster.getYRot(), 0, 0);
                return false;
            }
            int t = age - 5;
            if (hi[0] < 6) {
                if (t % 4 == 0) {
                    Vec3 p = hops[hi[0]];
                    caster.teleportTo(p.x, p.y, p.z);
                    caster.setDeltaMovement(Vec3.ZERO);
                    // the roof gives way where he lands
                    Ruin.bowl(level, p.x, p.z, 7, 7, 5, 0, Ruin.opts().rim(0.1).seed(hi[0] + 3));
                    RuinNet.ring(level, p, 26, 2, false, 18, DUST_LIGHT);
                    dust(level, p, 8, 5, 48);
                    RuinNet.tremor(level, p, 0.4, 30, 30);
                    RuinNet.quake(level, p, 0.6, false, 0xFFFFFF);
                    SubCommon.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 2.2f, 1.2f);
                    SubCommon.sound(level, p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2f, 0.6f);
                    Vec3 next = hi[0] < 5 ? hops[hi[0] + 1] : target.position();
                    send(level, TjNet.GHOST, caster.getId(), p, Vec3.ZERO, yaw(next.subtract(p)), hi[0] % 2, 0);
                    hi[0]++;
                }
                return false;
            }
            if (behind[0] == null) {
                // the 7th: at the target's back
                Vec3 look = SubCommon.flat(target.getLookAngle());
                look = look.lengthSqr() < 1.0E-4 ? flatDir(caster.position(), target.position(), caster) : look.normalize();
                behind[0] = target.position().subtract(look.scale(1.8));
                kickDir[0] = look;
                caster.teleportTo(behind[0].x, target.getY(), behind[0].z);
                caster.setDeltaMovement(Vec3.ZERO);
                caster.setInvisible(false);
                face(caster, look);
                SubCommon.pose(caster, "nr.chute_giro");
                SubCommon.sound(level, behind[0], SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.5f);
                return false;
            }
            int k = t - 24;
            still(caster);
            face(caster, kickDir[0]);
            if (k < 3) {
                return false;
            }
            Vec3 from = caster.position().add(kickDir[0].scale(1.2));
            Vec3 to = from.add(kickDir[0].scale(120));
            boolean hitTarget = false;
            for (LivingEntity e : line(level, caster.position(), caster.position().add(kickDir[0].scale(4.5)), 1.6, caster.getY() - 1, caster.getY() + 3.5, caster)) {
                hurt(caster, e, 40);
                stun(caster, e, 25);
                Combat.push(e, kickDir[0], 3.4, 0.55);
                hitFx(level, e);
                hitTarget |= e == target;
            }
            // the kick's wind splits every building in a line down to the street
            Ruin.trench(level, from, to, 3.2, 1, 17, 0, Ruin.opts().base(base(caster)).lip(0).seed(7));
            RuinNet.debrisLine(level, from, to, 70, 1.2);
            RuinNet.ring(level, from, 30, 2, false, 16, DUST_LIGHT);
            dust(level, from, 6, 4, 32);
            RuinNet.quake(level, from, 1.4, true, 0xFFFFFF);
            SubCommon.sound(level, from, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 3f, 0.4f);
            SubCommon.sound(level, from, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.7f);
            for (int i = 1; i <= 6; i++) {
                Vec3 q = from.add(kickDir[0].scale(i * 20));
                Tasks.later(i * 3, () -> dust(level, q, 7, 5, 46));
            }
            // whoever stood further down the line (the target flies into it, the rest are caught by the wind)
            for (LivingEntity e : line(level, from.add(kickDir[0].scale(4)), from.add(kickDir[0].scale(40)), 2.5, caster.getY() - 2, caster.getY() + 6, caster)) {
                if (e != target || !hitTarget) {
                    hurt(caster, e, 22);
                    Combat.push(e, kickDir[0], 2.0, 0.4);
                }
            }
            Intangible.free(caster);
            return true;
        }, () -> caster.setInvisible(false));
        return true;
    }

    static float yaw(Vec3 d) {
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
    }

    // ================= X: Nuvem Brincalhona =================

    static boolean nuvem(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 64);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        Vec3 c = caster.position();
        int street = base(caster);
        weapon(caster, new ItemStack(ModItems.PLAYFUL_CLOUD.get()));
        armor(caster, 110);
        SubCommon.pose(caster, "nuvem.forte_carga");
        SubCommon.sound(level, c, SoundEvents.CHAIN_PLACE, 2f, 0.6f);
        Vec3 dir0 = flatDir(c, target.position(), caster);
        face(caster, dir0);
        Vec3[] slamDir = {dir0};
        Vec3[] slamAt = {c};
        float[] spin = {yaw(dir0)};
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive()) {
                caster.setNoGravity(false);
                weapon(caster, spear());
                return true;
            }
            // ---- 1. the spin (0.35 s back, a full turn in 0.5 s) ----
            if (age < 30) {
                still(caster);
                if (age == 7) {
                    SubCommon.pose(caster, "nuvem.combo3");
                    double a0 = Math.atan2(dir0.z, dir0.x);
                    Ruin.shear(level, c.x, c.z, 12, 44, a0, -Mth.TWO_PI, street + 3, 0.16, 10, 0);
                    RuinNet.ring(level, c.add(0, 4, 0), 70, 3, true, 14, 0xD8D4DC);
                    RuinNet.ring(level, c, 50, 3, false, 28, DUST_LIGHT);
                    RuinNet.tremor(level, c, 0.5, 40, 50);
                    RuinNet.quake(level, c, 1.4, false, 0xFFFFFF);
                    SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 3f, 0.5f);
                    SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 1.6f);
                }
                if (age >= 7 && age <= 17) {
                    spin[0] -= 36;
                    caster.setYRot(spin[0]);
                    caster.setYBodyRot(spin[0]);
                    caster.setYHeadRot(spin[0]);
                }
                if (age == 9) {
                    for (LivingEntity e : near(level, c, 64, 4, 70, caster)) {
                        double r = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                        double cut = street + 1 + 3 + 0.16 * r;
                        if (r <= 6) {
                            hurt(caster, e, 30);
                            Combat.pushFrom(e, c, 1.6, 0.5);
                            hitFx(level, e);
                        } else if (e.getY() < cut + 1.5 && e.getY() + e.getBbHeight() > cut - 0.5) {
                            // caught in the cut's plane (up on a roof, or jumping): sliced with the buildings
                            hurt(caster, e, 26);
                            stun(caster, e, 25);
                            Combat.pushFrom(e, c, 1.2, 0.4);
                            hitFx(level, e);
                        }
                    }
                }
                if (age == 29) {
                    slamDir[0] = flatDir(c, target.position(), caster);
                    face(caster, slamDir[0]);
                    SubCommon.pose(caster, "nuvem.forte_golpe");
                    SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_STRONG, 2f, 0.5f);
                }
                return false;
            }
            // ---- 2. the leap and the slam ----
            if (age < 56) {
                int t = age - 30;
                face(caster, slamDir[0]);
                if (t < 9) {
                    hold(caster, c.add(0, 12 * Math.sin(t / 9.0 * Math.PI / 2), 0));
                    return false;
                }
                if (t < 12) {
                    hold(caster, c.add(0, 12 * (1 - (t - 9) / 3.0), 0));
                    return false;
                }
                caster.setNoGravity(false);
                still(caster);
                if (t == 12) {
                    caster.setPos(c.x, c.y, c.z);
                    Vec3 A = c.add(slamDir[0].scale(1.5));
                    Vec3 B = c.add(slamDir[0].scale(100));
                    slamAt[0] = A;
                    Ruin.trench(level, A, B, 6, 7, 11, 0, Ruin.opts().base(street).seed(3));
                    Ruin.fissures(level, A.x, A.z, 6, 40, 3, 0, 4, Ruin.opts().width(0.07).seed(8));
                    RuinNet.dust(level, A, 16, 10, 60, 10, DUST);
                    RuinNet.ring(level, A, 80, 5, false, 36, DUST_LIGHT);
                    RuinNet.tremor(level, A, 1.4, 40, 70);
                    RuinNet.quake(level, A, 2.2, true, 0xFFFFFF);
                    RuinNet.debrisLine(level, A, B, 80, 1.1);
                    SubCommon.sound(level, A, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
                    SubCommon.sound(level, A, SoundEvents.ANVIL_LAND, 3f, 0.5f);
                    for (LivingEntity e : line(level, A, B, 3.5, street - 4, street + 14, caster)) {
                        hurt(caster, e, 42);
                        stun(caster, e, 25);
                        Combat.push(e, slamDir[0], 0.6, 1.0);
                        hitFx(level, e);
                    }
                }
                if (t == 25) {
                    SubCommon.pose(caster, "db.mao_alto");
                }
                return false;
            }
            // ---- 3. the typhoon: everything is pulled in, then let go ----
            if (age < 92) {
                still(caster);
                spin[0] += 40;
                caster.setYBodyRot(spin[0]);
                if (age % 4 == 0) {
                    SubCommon.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 2.5f, 0.4f + (age - 56) / 60f);
                }
                if (age % 3 == 0) {
                    double a = RNG.nextDouble() * Mth.TWO_PI, r = 10 + RNG.nextDouble() * 34;
                    int x = Mth.floor(c.x + Math.cos(a) * r), z = Mth.floor(c.z + Math.sin(a) * r);
                    int g = level.hasChunk(x >> 4, z >> 4) ? Ruin.ground(level, x, z) : street;
                    RuinNet.dust(level, new Vec3(x + 0.5, g + 1, z + 0.5), 6 + RNG.nextDouble() * 4, 6, 36, 6, DUST);
                }
                if (age % 10 == 0) {
                    RuinNet.quake(level, c, 0.6, false, 0xFFFFFF);
                    RuinNet.debris(level, c, 30, 14, 0, 18, 1.0);
                }
                for (LivingEntity e : near(level, c, 44, 6, 40, caster)) {
                    Vec3 in = c.subtract(e.position());
                    Vec3 flat = SubCommon.flat(in);
                    double r = flat.length();
                    if (r < 2) {
                        continue;
                    }
                    Vec3 n = flat.scale(1 / r), tan = new Vec3(-n.z, 0, n.x);
                    Vec3 v = n.scale(0.32).add(tan.scale(0.22)).add(0, 0.06, 0);
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(v));
                    e.hurtMarked = true;
                }
                return false;
            }
            if (age == 92) {
                still(caster);
                Ruin.bowl(level, c.x, c.z, 24, 4, 12, 0, Ruin.opts().rim(0.4).base(street).seed(5));
                RuinNet.dust(level, c, 40, 16, 100, 12, DUST);
                RuinNet.ring(level, c, 120, 6, false, 44, DUST_LIGHT);
                RuinNet.ring(level, c.add(0, 6, 0), 90, 4, true, 24, 0xFFFFFF);
                RuinNet.tremor(level, c, 2, 40, 90);
                Tasks.later(8, () -> RuinNet.tremor(level, c, 1.2, 26, 60));
                RuinNet.quake(level, c, 2.8, false, 0xFFFFFF);
                RuinNet.debris(level, c, 30, 34, 0, 120, 1.3);
                SubCommon.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 5f, 0.4f);
                SubCommon.sound(level, c, SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.6f);
                for (LivingEntity e : near(level, c, 30, 6, 40, caster)) {
                    double r = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                    hurt(caster, e, (float) Math.max(25, 50 - r * 0.85));
                    stun(caster, e, 25);
                    Combat.pushFrom(e, c, 2.2, 1.0);
                    hitFx(level, e);
                }
                return false;
            }
            still(caster);
            if (age < 100) {
                return false;
            }
            weapon(caster, spear());
            return true;
        }, () -> {
            caster.setNoGravity(false);
            weapon(caster, spear());
        });
        return true;
    }

    // ================= B: Lança Invertida do Céu =================

    static boolean lanca(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 60);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        weapon(caster, spear());
        armor(caster, 30);
        // An expansion (his or anyone's he is caught in) does not hold him: the spear tears it and it shatters.
        boolean torn = DomainSpace.inside(target) || DomainSpace.inside(caster);
        if (torn) {
            DomainSpace.release(target, true);
            DomainSpace.release(caster, true);
            Vec3 at = target.position().add(0, 6, 0);
            send(level, TjNet.SHARDS, 0, at, Vec3.ZERO, 22, RNG.nextInt(1000), 0);
            SubCommon.sound(level, at, SoundEvents.GLASS_BREAK, 4f, 0.5f);
            SubCommon.sound(level, at, SoundEvents.GLASS_BREAK, 4f, 0.8f);
            SubCommon.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 2f, 1.4f);
            RuinNet.ring(level, at, 110, 5, true, 28, 0xC9D2F0);
        }
        Vec3 start = caster.position();
        Vec3[] dir = {flatDir(start, target.position(), caster)};
        double close = Math.max(0, caster.distanceTo(target) - 3.2);
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive()) {
                return true;
            }
            // a lunge to arm's reach, then the thrust
            if (age < 5) {
                Vec3 p = start.add(dir[0].scale(close * smooth((age + 1) / 5.0)));
                caster.setPos(p.x, caster.getY(), p.z);
                still(caster);
                face(caster, dir[0]);
                if (age == 3) {
                    SubCommon.pose(caster, "sub.estocada");
                    SubCommon.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 2f, 0.7f);
                }
                return false;
            }
            still(caster);
            face(caster, dir[0]);
            if (age != 6) {
                return age >= 18;
            }
            int street = base(caster);
            Vec3 A = caster.position().add(dir[0].scale(1.2));
            Vec3 B = A.add(dir[0].scale(140));
            for (LivingEntity e : line(level, A, A.add(dir[0].scale(60)), 3.0, caster.getY() - 2, caster.getY() + 5, caster)) {
                boolean close1 = e.distanceTo(caster) < 8;
                hurt(caster, e, close1 ? 46 : 24);
                if (close1) {
                    // the technique on them is cut: sealed, interrupted, the good effects stripped
                    PowerLogic.seal(e, 50);
                    List<MobEffectInstance> good = new ArrayList<>();
                    for (MobEffectInstance fx : e.getActiveEffects()) {
                        if (fx.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                            good.add(fx);
                        }
                    }
                    good.forEach(fx -> e.removeEffect(fx.getEffect()));
                }
                stun(caster, e, 25);
                Combat.push(e, dir[0], 2.6, 0.4);
                hitFx(level, e);
            }
            Ruin.trench(level, A, B, 7, 2, 14, 0, Ruin.opts().base(street).lip(1).seed(12));
            RuinNet.debrisLine(level, A, B, 70, 1.3);
            RuinNet.tremor(level, A, 0.9, 50, 60);
            RuinNet.quake(level, A, 1.8, true, 0xFFFFFF);
            RuinNet.ring(level, A.add(0, 2, 0), 40, 2, true, 14, 0xEEF0F4);
            SubCommon.sound(level, A, SoundEvents.TRIDENT_THROW.value(), 3f, 0.5f);
            SubCommon.sound(level, A, SoundEvents.WARDEN_SONIC_BOOM, 2.5f, 1.2f);
            for (int i = 0; i < 9; i++) {
                Vec3 q = A.add(dir[0].scale(140 * i / 9.0));
                double R = 7 + i * 0.8;
                Tasks.later(1 + i * 2, () -> dust(level, q, R, 6, 52));
            }
            return false;
        });
        return true;
    }

    // ================= V: Caçador de Feiticeiros (ult) =================

    static boolean cacador(LivingEntity caster, ServerLevel level) {
        LivingEntity target = NrCommon.target(caster, 90);
        if (target == null) {
            SubCommon.warn(caster, "no_target");
            return false;
        }
        SubCommon.Guard guard = SubCommon.guard(caster);
        weapon(caster, spear());
        armor(caster, 90);
        // back off to the end of the street: about 48 blocks from the target, on the far side from it
        Vec3 away = flatDir(target.position(), caster.position(), caster);
        Vec3 from = caster.position();
        Vec3 start = from;
        for (double turn : new double[] {0, 25, -25, 50, -50}) {
            Vec3 d = away.yRot((float) Math.toRadians(turn));
            Vec3 spot = target.position().add(d.scale(48));
            int x = Mth.floor(spot.x), z = Mth.floor(spot.z);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int g = Ruin.ground(level, x, z);
            if (Math.abs(g - target.getY()) < 24 && level.getFluidState(new BlockPos(x, g, z)).isEmpty()) {
                start = new Vec3(x + 0.5, g + 1, z + 0.5);
                break;
            }
        }
        Vec3 S = start;
        caster.teleportTo(S.x, S.y, S.z);
        caster.setDeltaMovement(Vec3.ZERO);
        SubCommon.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.5f);
        send(level, TjNet.GHOST, caster.getId(), from, Vec3.ZERO, caster.getYRot(), 0, 0);
        SubCommon.pose(caster, "jj.dash_charge");
        Vec3[] jump = {null};
        Vec3[] land = {null};
        Vec3[] runDir = {null};
        Set<Integer> runHit = new HashSet<>();
        Tasks.add(age -> {
            if (guard.broken() || !caster.isAlive()) {
                caster.setNoGravity(false);
                return true;
            }
            // ---- aim: crouched at the end of the street (0.8 s, the ground shakes) ----
            if (age < 16) {
                still(caster);
                face(caster, flatDir(S, target.position(), caster));
                if (age % 4 == 0) {
                    dust(level, S, 4, 2, 24);
                    RuinNet.quake(level, S, 0.4, false, 0xFFFFFF);
                }
                if (age == 1) {
                    RuinNet.ring(level, S, 40, 3, false, 24, DUST_LIGHT);
                    SubCommon.sound(level, S, SoundEvents.WARDEN_HEARTBEAT, 3f, 0.6f);
                }
                return false;
            }
            // ---- the run: the street in 0.7 s ----
            if (age == 16) {
                Vec3 tg = target.position();
                runDir[0] = flatDir(S, tg, caster);
                double L = Math.max(10, Math.hypot(tg.x - S.x, tg.z - S.z) - 8);
                jump[0] = S.add(runDir[0].scale(L));
                Ruin.bowl(level, S.x, S.z, 6, 3, 4, 0, Ruin.opts().rim(0.15));
                Ruin.trench(level, S.add(runDir[0].scale(2)), jump[0], 6, 3, 14, 0, Ruin.opts().base(Math.min(S.y, tg.y) - 1).lip(1.5).seed(21));
                RuinNet.debrisLine(level, S, jump[0], 90, 1.2);
                SubCommon.pose(caster, "jj.dash_run");
                SubCommon.sound(level, S, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.8f);
            }
            if (age <= 30) {
                double k = (age - 16) / 14.0;
                Vec3 prev = caster.position();
                Vec3 p = S.lerp(jump[0], k);
                int x = Mth.floor(p.x), z = Mth.floor(p.z);
                double y = level.hasChunk(x >> 4, z >> 4) ? Math.min(Ruin.ground(level, x, z) + 1, Math.max(S.y, jump[0].y) + 2) : p.y;
                caster.setPos(p.x, y, p.z);
                caster.setDeltaMovement(Vec3.ZERO);
                SubCommon.busy(caster, 3);
                face(caster, runDir[0]);
                for (LivingEntity e : line(level, prev, caster.position(), 2.8, y - 2, y + 4, caster)) {
                    if (runHit.add(e.getId())) {
                        hurt(caster, e, 34);
                        stun(caster, e, 25);
                        Vec3 side = new Vec3(-runDir[0].z, 0, runDir[0].x);
                        Combat.push(e, side.scale(Math.signum((e.getX() - p.x) * side.x + (e.getZ() - p.z) * side.z + 1.0E-4)), 2.0, 0.8);
                        hitFx(level, e);
                    }
                }
                if (age % 2 == 0) {
                    dust(level, caster.position().subtract(runDir[0].scale(4)), 7, 5, 48);
                    RuinNet.quake(level, caster.position(), 0.8, false, 0xFFFFFF);
                }
                return false;
            }
            // ---- the leap: 70 up, over to where the target is ----
            if (age == 31) {
                Vec3 J = caster.position();
                land[0] = target.position();
                Ruin.bowl(level, J.x, J.z, 7, 4, 4, 0, Ruin.opts().rim(0.2));
                RuinNet.ring(level, J, 36, 3, false, 20, DUST_LIGHT);
                RuinNet.tremor(level, J, 0.8, 40, 40);
                SubCommon.pose(caster, "jj.slam_leap");
                SubCommon.sound(level, J, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.9f);
                // his shadow on the ground: where he comes down
                SubNet.send(level, FzNet.MARK, 0, SubCommon.ground(level, land[0]), Vec3.ZERO, 12, 28, 3);
                jump[0] = J;
            }
            Vec3 J = jump[0], G = land[0];
            if (age < 47) {
                double k = smooth((age - 31) / 16.0);
                Vec3 p = J.lerp(G, k).add(0, 70 * Math.sin(k * Math.PI / 2), 0);
                hold(caster, p);
                return false;
            }
            if (age < 52) {
                hold(caster, G.add(0, 70, 0));
                if (age == 50) {
                    SubCommon.pose(caster, "sub.cravar");
                }
                return false;
            }
            if (age < 59) {
                double k = Math.pow((age - 51) / 7.0, 2);
                hold(caster, G.add(0, 70 * (1 - k), 0));
                return false;
            }
            if (age == 59) {
                caster.setNoGravity(false);
                caster.setPos(G.x, G.y, G.z);
                still(caster);
                impact(caster, level, G);
                return false;
            }
            still(caster);
            return age >= 76;
        }, () -> caster.setNoGravity(false));
        return true;
    }

    private static void impact(LivingEntity caster, ServerLevel level, Vec3 G) {
        int street = Mth.floor(G.y - 0.5);
        // the buildings round it, found before the crater opens
        List<int[]> blds = buildings(level, G.x, G.z, 32, 62, street, 14, 6);
        Ruin.bowl(level, G.x, G.z, 26, 12, 15, 0, Ruin.opts().rim(0.32).base(street).seed(21));
        Ruin.fissures(level, G.x, G.z, 24, 80, 3.5, 3, 7, Ruin.opts().width(0.085).seed(21));
        int seed = RNG.nextInt(1 << 20);
        for (int[] b : blds) {
            double cx = (b[0] + b[2]) * 0.5, cz = (b[1] + b[3]) * 0.5;
            double d = Math.hypot(cx - G.x, cz - G.z);
            topple(level, caster, b, street, dirCode(cx - G.x, cz - G.z), 4 + (int) Math.round((d - 32) * 0.5), seed++);
        }
        RuinNet.dust(level, G, 46, 24, 130, 14, 0x6A666E);
        RuinNet.boom(level, G.add(0, 2, 0), 22, 46, 140, 40, RuinNet.MODE_DUST, RuinNet.SHAPE_MUSHROOM, 0, 0x57535B);
        RuinNet.ring(level, G, 160, 8, false, 56, DUST_LIGHT);
        RuinNet.ring(level, G.add(0, 24, 0), 120, 4, true, 32, 0xDCD8E4);
        RuinNet.debris(level, G, 32, 36, 0, 220, 1.6);
        RuinNet.tremor(level, G, 2.8, 44, 120);
        Tasks.later(8, () -> RuinNet.tremor(level, G, 1.7, 30, 90));
        Tasks.later(16, () -> RuinNet.tremor(level, G, 1.0, 20, 70));
        RuinNet.quake(level, G, 3.4, true, 0xFFFFFF);
        SubCommon.sound(level, G, SoundEvents.GENERIC_EXPLODE.value(), 6f, 0.3f);
        SubCommon.sound(level, G, SoundEvents.WARDEN_SONIC_BOOM, 5f, 0.5f);
        SubCommon.sound(level, G, SoundEvents.ANVIL_LAND, 4f, 0.3f);
        for (LivingEntity e : near(level, G, 32, 6, 40, caster)) {
            double r = Math.hypot(e.getX() - G.x, e.getZ() - G.z);
            float dmg = (float) (r < 8 ? 110 : Math.max(40, 110 - (r - 8) * 2.9));
            hurt(caster, e, dmg);
            stun(caster, e, 30);
            Combat.pushFrom(e, G, 1.8, 1.2);
            hitFx(level, e);
        }
    }
}
