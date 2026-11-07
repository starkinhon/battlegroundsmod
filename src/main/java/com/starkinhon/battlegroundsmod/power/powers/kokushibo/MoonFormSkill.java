package com.starkinhon.battlegroundsmod.power.powers.kokushibo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.starkinhon.battlegroundsmod.combat.BlockRules;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.SwordArts;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.powers.sub.SubCommon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// Moon Breathing 2nd, 3rd, 5th and 6th Forms on the server. The shape is MoonForms (the approved 3D pages' script, the
// same the client draws), run every tick for the whole move: whatever a drawn cut or moon touches is cut (the hitbox is
// the effect itself, 1.5x as thick, as the 1st form), and the cuts carve the blocks they pass through and tear the ground
// under them, so the destruction always follows the direction of the cuts (Stark).
// Stark's order of destruction: 6th > 5th > 3rd and 2nd > 1st.
public final class MoonFormSkill {
    private static final double VIEW_DISTANCE = 128;
    private static final double HIT_THICK = 1.5;
    // How far from the line between him and a cut someone can stand and still be hit by it (blocks, past their width).
    private static final double FAN_REACH = 1.0;

    // Per form: damage of a cut and of a moon, knockback out and up, how fat the carving is around a cut (times its half
    // width), how deep it tears the ground under it, the blocks it may take in one tick, and whether one target can be hit
    // again by the same cut (the 5th form's vortex keeps cutting whoever stays in it).
    private record Tune(float cutDamage, float moonDamage, double fling, double flingUp, double fat, int groove, int budget, int rehit) {
    }

    private static Tune tune(int form) {
        return switch (form) {
            case 2 -> new Tune(9f, 3f, 1.6, 0.6, 1.2, 1, 350, 0);
            case 3 -> new Tune(16f, 3f, 2.0, 0.7, 1.4, 2, 650, 0);
            case 5 -> new Tune(7f, 0f, 1.8, 0.7, 1.25, 2, 750, 10);
            default -> new Tune(5f, 2f, 2.4, 0.5, 1.5, 2, 1100, 0);
        };
    }

    private MoonFormSkill() {
    }

    public static boolean second(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 2);
    }

    public static boolean third(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 3);
    }

    public static boolean fifth(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 5);
    }

    public static boolean sixth(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 6);
    }

    private static boolean use(LivingEntity caster, ServerLevel level, int form) {
        // the sword's trails in the moon's gold during the move
        ModPayloads.SwordFx gold = new ModPayloads.SwordFx(SwordArts.FX_COLOR | YoruNoMiya.MOON_GOLD << 4, caster.getId(), caster.position(), Vec3.ZERO, 0, 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, gold);
        SubCommon.pose(caster, SwordArts.QUICK_DRAW);
        SubCommon.busy(caster, form == 5 ? 14 : 10);
        Vec3 feet = caster.position();
        Vec3 look = caster.getLookAngle();
        int seed = 1 + SubCommon.RANDOM.nextInt(999_999);
        ModPayloads.SwordFx payload = new ModPayloads.SwordFx(SwordArts.FX_MOON_FORM, caster.getId(), feet, look, seed, form);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(feet) < VIEW_DISTANCE * VIEW_DISTANCE) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        SubCommon.sound(level, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.55f);
        SubCommon.sound(level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.7f);
        SubCommon.sound(level, feet, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.2f, form == 6 ? 1.8f : 1.4f);
        if (form == 2) {
            // the page has him jump about 2.4 blocks and turn in the air while the loops close round him
            caster.setDeltaMovement(caster.getDeltaMovement().x, 0.62, caster.getDeltaMovement().z);
            caster.hurtMarked = true;
        }
        MoonForms.Form shape = MoonForms.create(form, seed);
        MoonForms.Basis basis = MoonForms.basis(form, feet, look);
        Tune tune = tune(form);
        Run run = new Run(caster, level, form, shape, basis, tune);
        int ticks = (int) Math.ceil(shape.end * 20);
        Tasks.repeat(ticks, run::tick);
        return true;
    }

    private static final class Run {
        final LivingEntity caster;
        final ServerLevel level;
        final int form;
        final MoonForms.Form shape;
        final MoonForms.Basis basis;
        final Tune tune;
        final Set<Long> hit = new HashSet<>();
        final Map<Integer, Integer> lastHit = new HashMap<>();
        final Set<Integer> struck = new HashSet<>();
        final int[] debris = {0};
        final boolean carve;

        Run(LivingEntity caster, ServerLevel level, int form, MoonForms.Form shape, MoonForms.Basis basis, Tune tune) {
            this.caster = caster;
            this.level = level;
            this.form = form;
            this.shape = shape;
            this.basis = basis;
            this.tune = tune;
            this.carve = BlockRules.canBreak(level);
        }

        void tick(int age) {
            if (!caster.isAlive()) {
                return;
            }
            double t = age / 20.0;
            MoonForms.Frame fr = shape.frame(t);
            // who could be touched: everyone around where the form plays out
            Vec3 middle = form == 6 ? basis.world(new double[] {0, 2, 12}) : form == 3 ? basis.world(new double[] {0, 1, 6}) : basis.world(new double[] {0, 2, 0});
            double around = form == 6 ? 30 : form == 3 ? 18 : form == 5 ? 10 : 14;
            List<LivingEntity> near = Combat.inRadius(level, middle, around, caster);
            int[] budget = {tune.budget()};
            // 3rd (Stark): the cut also lands on anyone between him and it, so someone close in still gets hit
            boolean fan = form == 3;
            Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.55, 0);
            for (int k = 0; k < fr.ribbons().size(); k++) {
                MoonForms.Ribbon rb = fr.ribbons().get(k);
                if (rb.shape == 2) {
                    continue;
                }
                int n = rb.p.length - 1;
                int key = ribbonKey(rb);
                for (int i = 0; i <= n; i += 2) {
                    double s = i / (double) n;
                    if (!rb.drawn(s)) {
                        continue;
                    }
                    Vec3 p = basis.world(rb.p[i]);
                    double width = rb.hw * (rb.shape == 1 ? Math.pow(Math.sin(Math.PI * s), 0.75) : 1);
                    for (LivingEntity target : near) {
                        double reach = width * 2.2 * HIT_THICK * 0.5 + target.getBbWidth() * 0.5 + 0.35;
                        Vec3 c = Combat.center(target);
                        double dy = Math.max(0, Math.abs(c.y - p.y) - target.getBbHeight() * 0.5);
                        double dx = c.x - p.x, dz = c.z - p.z;
                        if (dx * dx + dz * dz + dy * dy < reach * reach) {
                            strike(target, key, tune.cutDamage(), age, p);
                        } else if (fan && inFan(target, chest, p)) {
                            strike(target, key, tune.cutDamage(), age, p);
                        }
                    }
                    if (carve && budget[0] > 0) {
                        Vec3 across = basis.dir(rb.a[i]);
                        carveAt(p, across, width, budget);
                    }
                }
            }
            if (tune.moonDamage() > 0) {
                for (MoonForms.Moon m : fr.moons()) {
                    if (m.diss() > 0.5) {
                        continue;
                    }
                    Vec3 p = basis.world(m.p());
                    for (LivingEntity target : near) {
                        double reach = m.r() * 1.3 + target.getBbWidth() * 0.5;
                        Vec3 c = Combat.center(target);
                        double dy = Math.max(0, Math.abs(c.y - p.y) - target.getBbHeight() * 0.5);
                        double dx = c.x - p.x, dz = c.z - p.z;
                        if (dx * dx + dz * dz + dy * dy < reach * reach) {
                            strike(target, 100000 + m.idx(), tune.moonDamage(), age, p);
                        }
                    }
                }
            }
        }

        // Whether the target stands on the line from his chest out to this point of a cut (the area the cut sweeps
        // through between him and where it is drawn).
        private static boolean inFan(LivingEntity target, Vec3 from, Vec3 to) {
            Vec3 c = Combat.center(target);
            Vec3 seg = to.subtract(from);
            double len2 = seg.lengthSqr();
            double k = len2 < 1.0E-6 ? 0 : Mth.clamp(c.subtract(from).dot(seg) / len2, 0, 1);
            Vec3 q = from.add(seg.scale(k));
            double reach = target.getBbWidth() * 0.5 + FAN_REACH;
            double dy = Math.max(0, Math.abs(c.y - q.y) - target.getBbHeight() * 0.5);
            double dx = c.x - q.x, dz = c.z - q.z;
            return dx * dx + dz * dz + dy * dy < reach * reach;
        }

        // A stable id per cut over the move (the ribbons come back in the same order each frame for a given form while
        // they live; the seed tells them apart).
        private static int ribbonKey(MoonForms.Ribbon rb) {
            return (int) Math.round(rb.seed * 1000);
        }

        private void strike(LivingEntity target, int piece, float damage, int age, Vec3 from) {
            long id = (long) target.getId() << 32 | (piece & 0xFFFFFFFFL);
            if (tune.rehit() > 0) {
                Integer last = lastHit.get(target.getId());
                if (last != null && age - last < tune.rehit()) {
                    return;
                }
                lastHit.put(target.getId(), age);
            } else if (!hit.add(id)) {
                return;
            }
            Vec3 at = Combat.center(target);
            if (struck.add(target.getId())) {
                // the first blow on each one: the impact frame in the moon's gold (as the 1st form's blow)
                ModPayloads.SwordFx fx = new ModPayloads.SwordFx(SwordArts.FX_MOON_HIT, target.getId(), at, Vec3.ZERO, 0, 0);
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(at) < VIEW_DISTANCE * VIEW_DISTANCE) {
                        PacketDistributor.sendToPlayer(player, fx);
                    }
                }
                SubCommon.sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9f, 1.6f);
            }
            SubCommon.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f + SubCommon.RANDOM.nextFloat() * 0.3f);
            Combat.hurt(caster, target, damage);
            Stun.apply(target, 12);
            // thrown out of the move: away from him (the 6th along its barrage), and up; set again next tick so nothing
            // in between eats it
            Vec3 away = form == 6 ? basis.fwd() : target.position().subtract(basis.origin());
            away = new Vec3(away.x, 0, away.z);
            away = away.lengthSqr() < 1.0E-4 ? basis.fwd() : away.normalize();
            Vec3 fling = away.scale(tune.fling()).add(0, tune.flingUp(), 0);
            target.setDeltaMovement(fling);
            target.hurtMarked = true;
            Tasks.later(1, () -> {
                if (target.isAlive()) {
                    target.setDeltaMovement(fling);
                    target.hurtMarked = true;
                }
            });
        }

        // The blocks a cut passes through (a flat slab along it, as wide as the band and a little thick), and the ground
        // under it torn in a trench along the cut.
        private void carveAt(Vec3 p, Vec3 across, double width, int[] budget) {
            double half = width * tune.fat() * 2.2 * 0.5 + 0.5;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            Vec3 feet = caster.position();
            for (double q = -half; q <= half && budget[0] > 0; q += 0.7) {
                Vec3 c = p.add(across.scale(q));
                for (int dy = -1; dy <= 1 && budget[0] > 0; dy++) {
                    pos.set(Mth.floor(c.x), Mth.floor(c.y) + dy, Mth.floor(c.z));
                    take(pos, p, feet, budget);
                }
                // the ground under the cut: the first solid block within 5 below, torn tune.groove deep
                int x = Mth.floor(c.x), z = Mth.floor(c.z), y0 = Mth.floor(c.y);
                for (int y = y0; y >= y0 - 5; y--) {
                    pos.set(x, y, z);
                    BlockState st = level.getBlockState(pos);
                    if (!st.isAir()) {
                        for (int g = 0; g < tune.groove() && budget[0] > 0; g++) {
                            pos.set(x, y - g, z);
                            take(pos, p, feet, budget);
                        }
                        break;
                    }
                }
            }
        }

        private void take(BlockPos.MutableBlockPos pos, Vec3 from, Vec3 feet, int[] budget) {
            if (level.getBlockState(pos).isAir()) {
                return;
            }
            budget[0]--;
            YoruNoMiya.remove(level, pos, from, feet, debris);
        }
    }
}
