package com.starkinhon.battlegroundsmod.power.powers.shigaraki;

import java.util.HashSet;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubNet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// X, Onda Sônica (preview v3, "MUITO FODA", 2026-11-11). He breathes in (0.3 s) and screams: from the mouth a cone of
// half-angle 0.5 rad runs out at 34 blocks per second up to 46 blocks. Where the front passes the ground heaves (drawn on
// the clients), whoever is within 26 blocks is thrown (harder the closer) and falls lying down; closer than 14, dizzy
// (stunned). The glass in the cone bursts into shards (the blocks really go, with the gamerules).
public final class Onda {
    static final double SPEED = 34, RANGE = 46, PUSH_R = 26, HALF = 0.5, STUN_R = 14;
    private static final int BREATH = 6, LENGTH = 52;
    private static final float DAMAGE = 30;

    private Onda() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        PowerData data = Shigaraki.data(caster);
        int interrupts = data.interrupts;
        SubCommon.busy(caster, BREATH + 30);
        SubCommon.pose(caster, "shig.grito");
        Vec3 dir = Combat.flatLook(caster);
        SubNet.send(level, Shigaraki.SCREAM, caster.getId(), caster.getEyePosition(), dir, 0, 0, 0);
        SubCommon.sound(level, caster.position(), SoundEvents.PLAYER_BREATH, 1.2f, 0.6f);
        Set<Integer> pushed = new HashSet<>();
        Set<Long> glass = new HashSet<>();
        Vec3[] origin = new Vec3[1];
        Tasks.add(age -> {
            if (age < BREATH) {
                if (!caster.isAlive() || data.interrupts != interrupts) {
                    return true;
                }
                return false;
            }
            if (age == BREATH) {
                origin[0] = caster.position().add(0, caster.getEyeHeight() * 0.94, 0).add(dir.scale(0.3));
                SubNet.send(level, Shigaraki.SCREAM, caster.getId(), origin[0], dir, 1, 0, 0);
                SubCommon.sound(level, origin[0], SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.55f);
                SubCommon.sound(level, origin[0], SoundEvents.RAVAGER_ROAR, 2.5f, 0.5f);
            }
            double e = (age - BREATH) / 20.0, front = e * SPEED;
            Vec3 o = origin[0];
            // whoever the front reaches this tick
            for (LivingEntity t : Combat.inRadius(level, o, Math.min(front, PUSH_R) + 1, caster)) {
                if (pushed.contains(t.getId())) {
                    continue;
                }
                Vec3 to = t.position().subtract(o);
                double dist = Math.sqrt(to.x * to.x + to.z * to.z);
                if (dist > front || dist > PUSH_R || angle(to, dir) > HALF + 0.1) {
                    continue;
                }
                pushed.add(t.getId());
                double k = 1 - dist / PUSH_R;
                Combat.hurt(caster, t, (float) (DAMAGE * Shigaraki.DAMAGE_MULT * (0.4 + 0.6 * k)));
                Combat.push(t, to, 0.5 + 1.6 * k, 0.45 + 0.5 * k);
                Stun.apply(t, dist < STUN_R ? 50 : 25);
            }
            // the glass bursts when the front gets to it (once)
            if (BlockRules.canBreak(level)) {
                shatter(level, o, dir, front - SPEED / 20, front, glass);
            }
            return age >= BREATH + (int) Math.ceil(RANGE / SPEED * 20) + 2 || age >= LENGTH;
        });
        return true;
    }

    static double angle(Vec3 to, Vec3 dir) {
        double len = Math.sqrt(to.x * to.x + to.z * to.z);
        if (len < 1.0E-3) {
            return 0;
        }
        return Math.acos(Mth.clamp((to.x * dir.x + to.z * dir.z) / len, -1, 1));
    }

    // Glass in the cone between radius r0 and r1 (flat distance) goes (the clients throw its shards).
    private static void shatter(ServerLevel level, Vec3 o, Vec3 dir, double r0, double r1, Set<Long> done) {
        if (r1 <= 0.8) {
            return;
        }
        int R = (int) Math.ceil(Math.min(r1, RANGE));
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int ox = Mth.floor(o.x), oz = Mth.floor(o.z), oy = Mth.floor(o.y);
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double cx = ox + dx + 0.5 - o.x, cz = oz + dz + 0.5 - o.z;
                double d = Math.sqrt(cx * cx + cz * cz);
                if (d < Math.max(0.8, r0) || d > r1 || d > RANGE || angle(new Vec3(cx, 0, cz), dir) > HALF + 0.25) {
                    continue;
                }
                for (int dy = -6; dy <= 30; dy++) {
                    at.set(ox + dx, oy + dy, oz + dz);
                    BlockState st = level.getBlockState(at);
                    if (isGlass(st) && done.add(at.asLong())) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16);
                    }
                }
            }
        }
    }

    static boolean isGlass(BlockState st) {
        return st.is(BlockTags.IMPERMEABLE) || st.is(Blocks.GLASS_PANE) || st.is(Blocks.TINTED_GLASS)
                || st.getBlock().getDescriptionId().contains("glass_pane");
    }
}
