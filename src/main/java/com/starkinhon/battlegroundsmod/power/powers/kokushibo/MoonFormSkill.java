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
// Every cut is a blow of its own (Stark, 2026-11-08), but a form has a TOTAL damage per target that its cuts share (the
// rebalance of the same day): the first cut on someone takes 30% of it and every next cut on the same one 30% less than
// the one before, so all of them together never pass the total; a form of a hundred cuts deals what one of five does,
// spread out, and the 8th (one huge cut) deals it all at once. The cuts in the middle only hold (stun); the target is
// thrown away from the last cut that hit it once no cut has touched it for FLING_AFTER ticks (or the form ends), so
// nobody bounces like a pinball between the cuts. The moons cut without throwing or stunning. The awakened forms (7th,
// 8th, 9th, 10th, 14th, 16th) go through here too.
public final class MoonFormSkill {
    private static final double VIEW_DISTANCE = 128;
    // The awakened forms reach much further (the 10th drills ~180 blocks).
    private static final double AWAKENED_VIEW = 224;
    // The 16th Form homes on everyone this close around him, nearest first, at most six.
    private static final double HOMING_RANGE = 50;
    private static final double HIT_THICK = 1.5;
    // How far from the line between him and a cut someone can stand and still be hit by it (blocks, past their width).
    private static final double FAN_REACH = 1.0;
    // Each moon left hanging in the air after a form cuts whoever touches it for this much (Tier 0 maximum, like every
    // damage of the mod): no throw and no stun (Stark, 2026-11-08), so whoever runs or flies at him only gets scratched.
    private static final float HANGING_DAMAGE = 2f;
    // Share of a form's total the first cut on a target takes, and what each next cut keeps of the one before.
    private static final float FIRST_CUT = 0.3f, NEXT_CUT = 0.7f;
    // Ticks with no cut on a target before it is thrown away from the last one.
    private static final int FLING_AFTER = 6;

    // Per form: the total damage of its cuts on one target (Tier 0 maximum), damage of a moon, knockback out and up, how fat the carving is around a cut (times its half
    // width), how deep it tears the ground under it, the blocks it may take in one tick, and whether one target can be hit
    // again by the same cut (the 5th form's vortex keeps cutting whoever stays in it).
    private record Tune(float cutDamage, float moonDamage, double fling, double flingUp, double fat, int groove, int budget, int rehit) {
    }

    private static Tune tune(int form) {
        return switch (form) {
            case 2 -> new Tune(64f, 4f, 1.6, 0.6, 1.2, 1, 350, 0);
            case 3 -> new Tune(72f, 4f, 2.0, 0.7, 1.4, 2, 650, 0);
            case 5 -> new Tune(72f, 0f, 1.8, 0.7, 1.25, 2, 750, 10);
            // the awakened forms: 2x the scale, stronger blows, deeper marks
            case 7 -> new Tune(80f, 4f, 2.4, 0.8, 1.3, 2, 1500, 0);
            case 8 -> new Tune(100f, 4f, 3.0, 0.9, 1.4, 3, 1800, 0);
            case 9 -> new Tune(72f, 4f, 2.0, 0.6, 1.3, 2, 1500, 0);
            case 10 -> new Tune(88f, 4f, 1.6, 0.5, 1.3, 3, 1800, 6);
            case 14 -> new Tune(120f, 4f, 2.4, 0.8, 1.3, 2, 1800, 0);
            case 16 -> new Tune(160f, 4f, 1.0, 1.2, 1.4, 3, 2000, 0);
            default -> new Tune(88f, 4f, 2.4, 0.5, 1.5, 2, 1100, 0);
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

    // The awakened forms (the stretched sword).
    public static boolean seventh(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 7);
    }

    public static boolean eighth(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 8);
    }

    public static boolean ninth(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 9);
    }

    public static boolean tenth(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 10);
    }

    public static boolean fourteenth(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 14);
    }

    public static boolean sixteenth(LivingEntity caster, ServerLevel level) {
        return use(caster, level, 16);
    }

    private static boolean use(LivingEntity caster, ServerLevel level, int form) {
        boolean awakened = MoonForms.awakened(form);
        // the sword's trails in the moon's gold during the move (lilac for the awakened forms)
        int trail = awakened ? 0xC9A2FF : YoruNoMiya.MOON_GOLD;
        ModPayloads.SwordFx gold = new ModPayloads.SwordFx(SwordArts.FX_COLOR | trail << 4, caster.getId(), caster.position(), Vec3.ZERO, 0, 0);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, gold);
        SubCommon.pose(caster, SwordArts.QUICK_DRAW);
        SubCommon.busy(caster, form == 5 ? 14 : 10);
        com.starkinhon.battlegroundsmod.boss.BossFalas.formName(caster, form);
        Vec3 feet = caster.position();
        Vec3 look = caster.getLookAngle();
        int seed = 1 + SubCommon.RANDOM.nextInt(999_999);
        // at the full moon more moons stay hanging after the form, bigger and for longer (told to the clients as form + 100)
        boolean full = OniBody.is(caster) && OniBody.phase(caster) == OniBody.FULL_MOON;
        MoonForms.Basis basis = MoonForms.basis(form, feet, look);
        double view = awakened ? AWAKENED_VIEW : VIEW_DISTANCE;
        List<LivingEntity> targets = form == 16 ? homingTargets(caster, level) : List.of();
        // (+ 1000: the Awakened Moon's new impact is off, MoonImpactRule)
        boolean impact = MoonImpactRule.on(level);
        ModPayloads.SwordFx payload = new ModPayloads.SwordFx(SwordArts.FX_MOON_FORM, caster.getId(), feet, look, seed,
                form + (full ? 100 : 0) + (impact ? 0 : 1000));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(feet) < view * view) {
                // the 16th's targets first, so the client's cuts follow the same ones
                for (int i = 0; i < targets.size(); i++) {
                    PacketDistributor.sendToPlayer(player, new ModPayloads.SwordFx(SwordArts.FX_MOON_TARGET, targets.get(i).getId(), feet, Vec3.ZERO, seed, i));
                }
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        SubCommon.sound(level, feet, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.55f);
        SubCommon.sound(level, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.7f);
        SubCommon.sound(level, feet, SoundEvents.TRIDENT_RIPTIDE_3.value(), awakened ? 2.0f : 1.2f, form == 6 ? 1.8f : awakened ? 0.9f : 1.4f);
        if (awakened) {
            SubCommon.sound(level, feet, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.3f);
        }
        if (form == 2) {
            // the page has him jump about 2.4 blocks and turn in the air while the loops close round him
            caster.setDeltaMovement(caster.getDeltaMovement().x, 0.62, caster.getDeltaMovement().z);
            caster.hurtMarked = true;
        }
        MoonForms.Linger shape;
        if (form == 16) {
            double[][] start = new double[targets.size()][];
            for (int i = 0; i < targets.size(); i++) {
                start[i] = basis.local(targets.get(i).position());
            }
            shape = MoonForms.create16(seed, full, start, i -> targets.get(i).isAlive() ? basis.local(targets.get(i).position()) : null);
        } else {
            shape = MoonForms.create(form, seed, full);
        }
        Tune tune = tune(form);
        // the awakened forms' freezes and slow motion (the clients play the same clock, so hits stay on the cuts)
        MoonTime clock = MoonForms.awakened(form) && MoonImpactRule.on(level) ? MoonTime.of(form, shape) : MoonTime.plain(shape.end);
        int ticks = (int) Math.ceil(clock.realEnd * 20);
        Run run = new Run(caster, level, form, shape, basis, tune, clock, ticks);
        Tasks.repeat(ticks, run::tick);
        return true;
    }

    // Everyone the 16th Form homes on: within 50 blocks around him (no aiming), nearest first, at most six.
    private static List<LivingEntity> homingTargets(LivingEntity caster, ServerLevel level) {
        List<LivingEntity> near = new java.util.ArrayList<>(Combat.inRadius(level, caster.position().add(0, 1, 0), HOMING_RANGE, caster));
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        return near.size() > 6 ? near.subList(0, 6) : near;
    }

    private static final class Run {
        final LivingEntity caster;
        final ServerLevel level;
        final int form;
        final MoonForms.Linger shape;
        final MoonForms.Basis basis;
        final Tune tune;
        final Set<Long> hit = new HashSet<>();
        final Map<Long, Long> lastHit = new HashMap<>();
        final Set<Integer> struck = new HashSet<>();
        // per target: cuts landed in this form, and the tick and point of its last cut (for the throw after it)
        final Map<Integer, Integer> cuts = new HashMap<>();
        final Map<Integer, Integer> lastCutAt = new HashMap<>();
        final Map<Integer, Vec3> lastCutFrom = new HashMap<>();
        final Map<Integer, LivingEntity> held = new HashMap<>();
        final int ticks;
        int now;
        final int[] debris = {0};
        final boolean carve;
        final MoonTime clock;

        Run(LivingEntity caster, ServerLevel level, int form, MoonForms.Linger shape, MoonForms.Basis basis, Tune tune, MoonTime clock, int ticks) {
            this.ticks = ticks;
            this.clock = clock;
            this.caster = caster;
            this.level = level;
            this.form = form;
            this.shape = shape;
            this.basis = basis;
            this.tune = tune;
            this.carve = BlockRules.canBreak(level);
        }

        void tick(int age) {
            now = age;
            flings(age >= ticks - 1);
            if (!caster.isAlive()) {
                return;
            }
            double t = clock.form(age / 20.0);
            MoonForms.Frame fr = shape.frame(t);
            // who could be touched: everyone around where the form plays out
            List<LivingEntity> near;
            if (MoonForms.awakened(form)) {
                near = around(fr);
            } else {
                Vec3 middle = form == 6 ? basis.world(new double[] {0, 2, 12}) : form == 3 ? basis.world(new double[] {0, 1, 6}) : basis.world(new double[] {0, 2, 0});
                double around = form == 6 ? 30 : form == 3 ? 18 : form == 5 ? 10 : 14;
                near = Combat.inRadius(level, middle, around, caster);
            }
            int[] budget = {tune.budget()};
            if (form == 16) {
                craters(t, budget);
            }
            // Every form but the 6th and the 16th (Stark, 2026-11-07/08): a cut also lands on anyone between him and it,
            // so someone close in still gets hit. The 6th stays as it was (Stark: it is fine as it is); the 16th's cuts
            // fall from the sky on the targets it homes on, they are not swung out from him.
            boolean fan = form != 6 && form != 16;
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
                            strike(target, key, age, p);
                        } else if (fan && inFan(target, chest, p)) {
                            strike(target, key, age, p);
                        }
                    }
                    if (carve && budget[0] > 0) {
                        Vec3 across = basis.dir(rb.a[i]);
                        carveAt(p, across, width, budget);
                    }
                }
            }
            {
                for (MoonForms.Moon m : fr.moons()) {
                    // the moons left hanging in the air cut whoever touches them, once each (any form, the 5th too)
                    boolean hanging = shape.hanging(m.idx(), t);
                    float damage = hanging ? HANGING_DAMAGE : tune.moonDamage();
                    if (m.diss() > 0.5 || damage <= 0) {
                        continue;
                    }
                    Vec3 p = basis.world(m.p());
                    for (LivingEntity target : near) {
                        double reach = m.r() * 1.3 + target.getBbWidth() * 0.5;
                        Vec3 c = Combat.center(target);
                        double dy = Math.max(0, Math.abs(c.y - p.y) - target.getBbHeight() * 0.5);
                        double dx = c.x - p.x, dz = c.z - p.z;
                        if (dx * dx + dz * dz + dy * dy < reach * reach) {
                            moon(target, (hanging ? 200000 : 100000) + m.idx(), damage, p);
                        }
                    }
                }
            }
        }

        // The awakened forms are huge and travel far: everyone inside the box round everything drawn this tick.
        private List<LivingEntity> around(MoonForms.Frame fr) {
            double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
            boolean any = false;
            for (MoonForms.Ribbon rb : fr.ribbons()) {
                for (int i = 0; i < rb.p.length; i += 6) {
                    any = grow(lo, hi, basis.world(rb.p[i]), rb.hw * 2.5);
                }
            }
            for (MoonForms.Moon m : fr.moons()) {
                any = grow(lo, hi, basis.world(m.p()), m.r() * 1.5);
            }
            if (form == 16) {
                for (MoonFormsAwakened.Form16.Cut c : ((MoonFormsAwakened.Form16) shape.base()).cuts()) {
                    any = grow(lo, hi, basis.world(c.g), CRATER_RADIUS + 2);
                }
            }
            if (!any) {
                return List.of();
            }
            if (form != 16) {
                // the area between him and the cuts is hit too (see tick), so whoever stands next to him counts
                grow(lo, hi, caster.position().add(0, caster.getBbHeight() * 0.55, 0), 3);
            }
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(lo[0] - 2, lo[1] - 2, lo[2] - 2, hi[0] + 2, hi[1] + 2, hi[2] + 2);
            return level.getEntitiesOfClass(LivingEntity.class, box, e -> Combat.isTarget(e, caster));
        }

        private static boolean grow(double[] lo, double[] hi, Vec3 p, double pad) {
            lo[0] = Math.min(lo[0], p.x - pad);
            lo[1] = Math.min(lo[1], p.y - pad);
            lo[2] = Math.min(lo[2], p.z - pad);
            hi[0] = Math.max(hi[0], p.x + pad);
            hi[1] = Math.max(hi[1], p.y + pad);
            hi[2] = Math.max(hi[2], p.z + pad);
            return true;
        }

        // 16th: each cut that lands is a blow on whoever stands where it falls (its target first), and a crater.
        static final double CRATER_RADIUS = 4.5;
        final Set<Integer> landed = new HashSet<>();

        private void craters(double t, int[] budget) {
            List<MoonFormsAwakened.Form16.Cut> cuts = ((MoonFormsAwakened.Form16) shape.base()).cuts();
            for (int i = 0; i < cuts.size(); i++) {
                MoonFormsAwakened.Form16.Cut c = cuts.get(i);
                if (t < c.hit || !landed.add(i)) {
                    continue;
                }
                Vec3 at = basis.world(c.g);
                SubCommon.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 2.2f, 0.7f);
                SubCommon.sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 1.6f, 1.4f);
                for (LivingEntity target : Combat.inRadius(level, at.add(0, 1, 0), CRATER_RADIUS, caster)) {
                    strike(target, 300000 + i, (int) Math.round(t * 20), at);
                }
                if (carve) {
                    // the bowl: everything within the radius below the landing point, a little above it too
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    Vec3 feet = caster.position();
                    int r = (int) Math.ceil(CRATER_RADIUS);
                    int[] crater = {900};
                    for (int dx = -r; dx <= r && crater[0] > 0; dx++) {
                        for (int dz = -r; dz <= r && crater[0] > 0; dz++) {
                            for (int dy = -r; dy <= 2 && crater[0] > 0; dy++) {
                                double d2 = dx * dx + dz * dz + dy * dy * 2.2;
                                if (d2 > CRATER_RADIUS * CRATER_RADIUS) {
                                    continue;
                                }
                                pos.set(Mth.floor(at.x) + dx, Mth.floor(at.y) + dy, Mth.floor(at.z) + dz);
                                take(pos, at, feet, crater);
                            }
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

        private void strike(LivingEntity target, int piece, int age, Vec3 from) {
            if (!firstTouch(target, piece, age)) {
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
            // this cut's share of the form's total: the 8th is one cut and deals it all
            int n = cuts.merge(target.getId(), 1, Integer::sum);
            float share = form == 8 ? 1f : FIRST_CUT * (float) Math.pow(NEXT_CUT, n - 1);
            Combat.hurt(caster, target, tune.cutDamage() * share);
            Stun.apply(target, 12);
            // held where it is until the cuts stop; thrown away from the last one after (flings)
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            lastCutAt.put(target.getId(), now);
            lastCutFrom.put(target.getId(), from);
            held.put(target.getId(), target);
        }

        // A moon that touches someone: its damage only, no throw and no stun (Stark, 2026-11-08).
        private void moon(LivingEntity target, int piece, float damage, Vec3 at) {
            if (!hit.add((long) target.getId() << 32 | (piece & 0xFFFFFFFFL))) {
                return;
            }
            SubCommon.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f + SubCommon.RANDOM.nextFloat() * 0.3f);
            Vec3 motion = target.getDeltaMovement();
            Combat.hurt(caster, target, damage);
            // the hurt's own vanilla knockback taken back
            target.setDeltaMovement(motion);
            target.hurtMarked = true;
        }

        // Whether this cut may land on this target now (once per cut, or again after rehit ticks for a grinding cut).
        private boolean firstTouch(LivingEntity target, int piece, int age) {
            long id = (long) target.getId() << 32 | (piece & 0xFFFFFFFFL);
            if (tune.rehit() > 0) {
                // a cut that keeps grinding (the 5th's vortex, the 10th's saws) hits again, each cut on its own
                Long last = lastHit.get(id);
                if (last != null && age - last < tune.rehit()) {
                    return false;
                }
                lastHit.put(id, (long) age);
                return true;
            }
            return hit.add(id);
        }

        // Throws everyone no cut has touched for FLING_AFTER ticks (everyone left, when the form ends) away from the
        // last cut that hit them (the 6th along its barrage), and up; set again next tick so nothing in between eats it.
        private void flings(boolean end) {
            var it = held.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                int id = e.getKey();
                if (!end && now - lastCutAt.get(id) < FLING_AFTER) {
                    continue;
                }
                it.remove();
                LivingEntity target = e.getValue();
                if (!target.isAlive()) {
                    continue;
                }
                Vec3 from = lastCutFrom.get(id);
                Vec3 away = form == 6 ? basis.fwd() : target.position().subtract(from);
                if (new Vec3(away.x, 0, away.z).lengthSqr() < 1.0E-3) {
                    away = target.position().subtract(basis.origin());
                }
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
