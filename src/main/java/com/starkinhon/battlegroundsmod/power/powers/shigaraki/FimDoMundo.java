package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

// Shift+V, Fim do Mundo (preview v6). He kneels (0.9 s), raises the open hand ("Desapareça.") and at 1.5 s the five
// fingers touch the ground: impact frame, hitstop, and the Decadência runs out from the hand at 24 blocks a second up
// to 150, climbing whatever stands (12 blocks a second up), with a wall of dust riding the front
// (client/fx/shig/EndFx). Everything in it dries, cracks and turns to dust: the ground (two layers under his level),
// every building, the hills; whoever the front reaches decays (the body crumbles, 160 damage). The ground under him
// stays. He gets up at 9.6 s; 11.5 s in all.
// The blocks go column by column as the front gets there, through Decay's queue (a few thousand per tick, no neighbour
// updates), only in loaded chunks and only with the gamerules (BlockRules).
public final class FimDoMundo {
    static final float SPEED = 24, REACH = 150, CLIMB = 12;
    private static final int TOUCH = 30, LENGTH = 230, UP = 192;
    private static final float DAMAGE = 160, BODY_SPEED = 3f;
    private static final double SAFE_R = 2.2;

    // Every column offset within REACH, closest first (built once).
    private static int[] OFFSETS;
    private static float[] DISTS;

    private FimDoMundo() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = Shigaraki.data(caster);
        int interrupts = data.interrupts;
        Vec3 feet = caster.position();
        Vec3 face = Combat.flatLook(caster);
        SubCommon.busy(caster, UP);
        SubCommon.pose(caster, "shig.fim");
        SubNet.send(level, Shigaraki.END, caster.getId(), feet, face, 0, 0, 0);
        SubCommon.sound(level, feet, SoundEvents.WARDEN_SONIC_CHARGE, 1.2f, 0.6f);
        // not crushed while he kneels (the touch is short, but bosses hit hard)
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TOUCH + 4, 3, false, false, false));
        Runnable release = Freeze.holdLookFree(caster, UP);
        // the right hand flat on the ground, half a block ahead and a little to the side (the preview's armR)
        Vec3 side = new Vec3(-face.z, 0, face.x);
        Vec3 hand = SubCommon.ground(level, feet.add(face.scale(0.6)).add(side.scale(-0.3)).add(0, 1, 0));
        Set<Integer> hit = new HashSet<>();
        long[] touchTick = {0};
        int[] next = {0};
        int gy = Mth.floor(feet.y + 0.01);
        boolean breaks = BlockRules.canBreak(level);
        Tasks.add(age -> {
            if (age < TOUCH) {
                if (!caster.isAlive() || data.interrupts != interrupts) {
                    release.run();
                    SubNet.send(level, Shigaraki.END, caster.getId(), feet, face, 2, 0, 0);
                    return true;
                }
                return false;
            }
            if (age == TOUCH) {
                touchTick[0] = level.getGameTime();
                SubNet.send(level, Shigaraki.END, caster.getId(), hand, face, 1, SPEED, REACH);
                SubCommon.sound(level, hand, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.4f);
                SubCommon.sound(level, hand, SoundEvents.WARDEN_SONIC_BOOM, 2f, 0.5f);
                // the star of cracks under the hand
                Decay.seed(level, caster, Shigaraki.groundSeed(hand), 9f, 3f, Decay.GROUND, hand.y, SAFE_R);
            }
            float e = (age - TOUCH) / 20f;
            float R = e * SPEED;
            if (age % 10 == 0 && R < REACH) {
                SubCommon.sound(level, caster.position(), SoundEvents.GRAVEL_BREAK, 2f, 0.4f + SubCommon.RANDOM.nextFloat() * 0.2f);
            }
            // whoever the front reaches
            if (R > 0 && R < REACH + 4) {
                for (LivingEntity v : Combat.inRadius(level, new Vec3(hand.x, caster.getY(), hand.z), Math.min(R, REACH), caster)) {
                    double dh = Math.hypot(v.getX() - hand.x, v.getZ() - hand.z);
                    if (dh < R && hit.add(v.getId())) {
                        Decay.body(caster, v, v.position().add(0, 0.1, 0), BODY_SPEED, DAMAGE, 60);
                    }
                }
            }
            // the blocks: columns the front reaches within the next second are queued for their own time
            if (breaks) {
                float ahead = Math.min(REACH + 4, (e + 1) * SPEED * 1.15f);
                columns(level, hand, gy, caster, touchTick[0], ahead, next);
            }
            if (age == UP) {
                release.run();
            }
            return age >= LENGTH;
        });
        return true;
    }

    private static void columns(ServerLevel level, Vec3 c, int gy, LivingEntity caster, long t0, float upTo, int[] next) {
        build();
        int cx = Mth.floor(c.x), cz = Mth.floor(c.z);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        while (next[0] < DISTS.length && DISTS[next[0]] <= upTo) {
            int o = OFFSETS[next[0]++];
            int x = cx + (short) (o >> 16), z = cz + (short) o;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            float px = x + 0.5f, pz = z + 0.5f;
            float dh = (float) Math.hypot(px - c.x, pz - c.z);
            if (dh > REACH + DecayNoise.vn(px * 0.33f, gy * 0.33f, pz * 0.33f) * 4) {
                continue;
            }
            boolean safe = (px - caster.getX()) * (px - caster.getX()) + (pz - caster.getZ()) * (pz - caster.getZ()) < SAFE_R * SAFE_R;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            for (int y = gy - 2; y <= top; y++) {
                if (safe && y < gy) {
                    continue;
                }
                at.set(x, y, z);
                BlockState st = level.getBlockState(at);
                if (!Decay.decays(level, at, st)) {
                    continue;
                }
                float when = DecayNoise.wave(px, y + 0.5f, pz, dh, SPEED, y + 0.5f - gy, CLIMB) + Decay.CRUMBLE;
                Decay.schedule(level, at.immutable(), t0 + Math.round(when * 20));
            }
        }
    }

    private static synchronized void build() {
        if (OFFSETS != null) {
            return;
        }
        int r = (int) Math.ceil(REACH + 4);
        int n = 0;
        long[] all = new long[(2 * r + 1) * (2 * r + 1)];
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d > r) {
                    continue;
                }
                // sort key: the distance (high bits), then the packed offset
                all[n++] = ((long) Float.floatToIntBits(d) << 32) | (((dx & 0xFFFF) << 16) | (dz & 0xFFFF)) & 0xFFFFFFFFL;
            }
        }
        long[] used = Arrays.copyOf(all, n);
        Arrays.sort(used);
        int[] offs = new int[n];
        float[] dists = new float[n];
        for (int i = 0; i < n; i++) {
            offs[i] = (int) used[i];
            dists[i] = Float.intBitsToFloat((int) (used[i] >>> 32));
        }
        DISTS = dists;
        OFFSETS = offs;
    }
}
