package com.starkinhon.battlegroundsmod.power.powers.uchiha;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.starkinhon.battlegroundsmod.ModAttachments;
import com.starkinhon.battlegroundsmod.combat.Combat;
import com.starkinhon.battlegroundsmod.combat.Freeze;
import com.starkinhon.battlegroundsmod.combat.Fx;
import com.starkinhon.battlegroundsmod.combat.Intangible;
import com.starkinhon.battlegroundsmod.combat.SkillSounds;
import com.starkinhon.battlegroundsmod.combat.Stun;
import com.starkinhon.battlegroundsmod.combat.Tasks;
import com.starkinhon.battlegroundsmod.fx.ModParticles;
import com.starkinhon.battlegroundsmod.fx.SoulParticleOptions;
import com.starkinhon.battlegroundsmod.movement.Movement;
import com.starkinhon.battlegroundsmod.network.ModPayloads;
import com.starkinhon.battlegroundsmod.power.PowerData;
import com.starkinhon.battlegroundsmod.power.PowerLogic;
import com.starkinhon.battlegroundsmod.skill.AgarraoSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

// B: neck grab (madara.agarrao.json, made in the Forja). Madara vanishes into a dash much faster than the Dash Attack,
// bending towards whoever he aims at. If he reaches someone he grabs them by the neck, lifts them, holds them up and
// slams them into the ground, which breaks open like vanilla blocks do; they stay stuck in the hole for a moment.
// Only the two fighters get the cutscene. A dash that reaches nobody just ends where it stops.
public final class Agarrao {
    public static final String SKILL = AgarraoSpec.SKILL;
    // Space between the two bodies while he holds the target up.
    private static final double HOLD_GAP = 0.3;
    // Madara stays still a little after the slam, standing over the hole.
    private static final int AFTER_SLAM_TICKS = 8;
    // The hole opens from the middle out over this many ticks.
    private static final int CRATER_TICKS = 3;

    private Agarrao() {
    }

    public static boolean use(LivingEntity caster, ServerLevel level) {
        if (!Movement.free(caster)) {
            return false;
        }
        boolean awakened = awakened(caster);
        AgarraoSpec spec = AgarraoSpec.get(awakened);
        AgarraoSpec.Dash dash = spec.dash();
        PowerData data = caster.getData(ModAttachments.POWER);
        int interrupts = data.interrupts;
        int prep = Math.max(0, spec.tempo().preparoTicks());
        double speed = Math.max(0.1, dash.velocidade());
        int maxDash = Math.max(1, (int) Math.ceil(dash.distanciaMax() / speed));
        data.busyTicks = prep + maxDash + 2;
        LivingEntity locked = lock(caster, dash.distanciaMax());

        animation(caster, "dash#" + maxDash, awakened);
        SkillSounds.play(level, spec.sons(), "uso", 0, prep, onde -> caster.position(), screens(caster, null));
        Fx.effects(level, SKILL, awakened, "uso", caster, prep);

        Vec3[] dir = {locked != null ? flatTo(caster, locked) : Combat.flatLook(caster)};
        double[] traveled = {0};
        Vec3[] last = {caster.position()};
        Tasks.add(age -> {
            if (!caster.isAlive() || data.interrupts != interrupts || Freeze.isFrozen(caster)) {
                miss(caster, level, spec, awakened, data);
                return true;
            }
            if (age < prep) {
                return false;
            }
            int t = age - prep;
            if (t == 0) {
                vanish(caster, level, spec, awakened, maxDash);
            } else {
                Vec3 moved = caster.position().subtract(last[0]);
                traveled[0] += Math.sqrt(moved.x * moved.x + moved.z * moved.z);
            }
            last[0] = caster.position();
            if (dash.seguirAlvo() && locked != null && Combat.isTarget(locked, caster)) {
                dir[0] = flatTo(caster, locked);
            } else if (locked == null) {
                dir[0] = Combat.flatLook(caster);
            }
            double step = Math.min(speed, dash.distanciaMax() - traveled[0]);
            Vec3 from = Combat.center(caster);
            LivingEntity caught = caught(level, caster, from, from.add(dir[0].scale(Math.max(0, step) + dash.distanciaAgarrar())),
                    dash.raioPegar(), locked);
            if (caught != null) {
                grab(caster, caught, level, spec, awakened, data);
                return true;
            }
            if (step <= 0.05 || t > maxDash + 2) {
                miss(caster, level, spec, awakened, data);
                return true;
            }
            caster.setDeltaMovement(dir[0].x * step, Math.min(caster.getDeltaMovement().y, 0.1), dir[0].z * step);
            caster.hurtMarked = true;
            caster.resetFallDistance();
            trail(level, caster, from, dir[0], step, spec);
            return false;
        });
        return true;
    }

    // Whoever he is fighting, if close enough and in front; else whoever he aims at.
    private static LivingEntity lock(LivingEntity caster, double range) {
        PowerData data = caster.getData(ModAttachments.POWER);
        if (data.foughtId >= 0 && caster.level().getEntity(data.foughtId) instanceof LivingEntity fought && Combat.isTarget(fought, caster)
                && fought.distanceTo(caster) <= range && caster.hasLineOfSight(fought) && flatTo(caster, fought).dot(Combat.flatLook(caster)) > 0.5) {
            return fought;
        }
        return Combat.raycastTarget(caster, range);
    }

    // The first one along this stretch of the dash, the locked target first.
    private static LivingEntity caught(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 to, double radius, LivingEntity locked) {
        List<LivingEntity> hits = Combat.alongLine(level, from, to, radius, caster);
        if (locked != null && hits.contains(locked)) {
            return locked;
        }
        return hits.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(caster))).orElse(null);
    }

    private static void vanish(LivingEntity caster, ServerLevel level, AgarraoSpec spec, boolean awakened, int maxDash) {
        if (spec.dash().invisivel()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), maxDash + 4));
        }
        smoke(level, caster, spec);
        SkillSounds.play(level, spec.sons(), "dash", 0, maxDash, onde -> caster.position(), screens(caster, null));
        Fx.effects(level, SKILL, awakened, "dash", caster, maxDash);
    }

    private static void reappear(LivingEntity caster, ServerLevel level, AgarraoSpec spec) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.HideEntity(caster.getId(), 0));
        smoke(level, caster, spec);
    }

    private static void miss(LivingEntity caster, ServerLevel level, AgarraoSpec spec, boolean awakened, PowerData data) {
        data.busyTicks = 0;
        reappear(caster, level, spec);
        if (caster.isAlive()) {
            Vec3 now = caster.getDeltaMovement();
            caster.setDeltaMovement(now.x * 0.2, now.y, now.z * 0.2);
            caster.hurtMarked = true;
            SkillSounds.play(level, spec.sons(), "reaparecer", 0, 0, onde -> caster.position(), screens(caster, null));
            Fx.effects(level, SKILL, awakened, "reaparecer", caster, 0);
        }
    }

    private static void grab(LivingEntity caster, LivingEntity target, ServerLevel level, AgarraoSpec spec, boolean awakened, PowerData data) {
        reappear(caster, level, spec);
        AgarraoSpec.Agarrar agarrar = spec.agarrar();
        AgarraoSpec.Esmagar esmagar = spec.esmagar();
        int lift = Math.max(0, agarrar.levantarTicks());
        int held = lift + Math.max(0, agarrar.segurarTicks());
        int impact = spec.slamTick();
        data.busyTicks = impact + AFTER_SLAM_TICKS;

        // Madara stops dead, facing the target.
        Vec3 forward = flatTo(caster, target);
        float yRot = (float) (Mth.atan2(-forward.x, forward.z) * Mth.RAD_TO_DEG);
        caster.setDeltaMovement(Vec3.ZERO);
        if (caster instanceof ServerPlayer player) {
            player.connection.teleport(caster.getX(), caster.getY(), caster.getZ(), yRot, 0);
        } else {
            caster.moveTo(caster.getX(), caster.getY(), caster.getZ(), yRot, 0);
            caster.setYHeadRot(yRot);
        }
        caster.hurtMarked = true;
        Freeze.hold(caster, impact + AFTER_SLAM_TICKS);

        // Held by the neck right in front of him: up over the lift, kept up, then slammed straight down.
        Vec3 base = caster.position().add(forward.scale(caster.getBbWidth() / 2 + target.getBbWidth() / 2 + HOLD_GAP));
        Vec3 start = target.position();
        double height = agarrar.altura();
        Freeze.hold(target, impact, age -> {
            if (age < lift) {
                double k = smooth(age / (double) lift);
                return start.lerp(base, Math.min(1, age / 2.0)).add(0, height * k, 0);
            }
            // With no lift he comes out of the dash already holding them up.
            if (age < held) {
                return base.add(0, height, 0);
            }
            double k = (age - held) / (double) Math.max(1, impact - held);
            return base.add(0, height * (1 - k * k), 0);
        });
        Stun.apply(target, impact + Math.max(0, esmagar.presoTicks()));
        // Whatever the target was charging is cut off, and no skill (not even a counter) until they get out of the hole.
        PowerLogic.seal(target, impact + Math.max(0, esmagar.presoTicks()));
        if (spec.cutscene().intangivel()) {
            int ticks = impact + Math.max(0, spec.cutscene().depoisTicks());
            Intangible.hold(caster, ticks);
            Intangible.hold(target, ticks);
        }
        if (spec.cutscene().ativa()) {
            for (ServerPlayer player : screens(caster, target)) {
                PacketDistributor.sendToPlayer(player, new ModPayloads.GrabCutscene(caster.getId(), target.getId(), awakened, impact));
            }
        }

        animation(caster, "agarrar", awakened);
        Combat.hurt(caster, target, agarrar.dano());
        Vec3 neck = Combat.center(target).add(0, target.getBbHeight() * 0.3, 0);
        SkillSounds.play(level, spec.sons(), "agarrar", 0, held, onde -> "alvo".equals(onde) ? Combat.center(target) : caster.position(),
                screens(caster, target));
        Fx.effects(level, SKILL, awakened, "agarrar", neck, forward);
        for (int i = 0; i < 12; i++) {
            Vec3 out = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).normalize();
            Fx.moving(level, ModParticles.dust(0xFFFFFF, 0.8f, 4, 0.8f), neck.add(out.scale(0.15)), out.scale(0.18));
        }

        Tasks.later(impact, () -> {
            if (!target.isAlive()) {
                return;
            }
            Vec3 ground = base;
            Combat.hurt(caster, target, esmagar.dano());
            if (esmagar.quebrarBlocos()) {
                crater(level, ground, esmagar.raioCratera(), esmagar.profundidadeCratera());
            }
            // Pinned at the bottom of the hole while stuck there.
            Vec3 bottom = bottom(level, ground, esmagar.profundidadeCratera() + 2, caster);
            Freeze.hold(target, Math.max(1, esmagar.presoTicks()), age -> bottom);
            SkillSounds.play(level, spec.sons(), "esmagar", 0, 0, onde -> "alvo".equals(onde) ? Combat.center(target) : "usuario".equals(onde)
                    ? caster.position() : ground, screens(caster, target));
            Fx.effects(level, SKILL, awakened, "esmagar", ground, forward);
            dust(level, ground, spec);
        });
    }

    // A round bowl broken the vanilla way (block particles and sounds, no drops), from the middle out. Unbreakable
    // blocks and blocks holding things (chests, furnaces) stay.
    private static void crater(ServerLevel level, Vec3 pos, double radius, double depth) {
        if (radius <= 0 || depth <= 0) {
            return;
        }
        BlockPos center = BlockPos.containing(pos);
        int r = (int) Math.ceil(radius * 1.1);
        int d = (int) Math.ceil(depth * 1.1);
        List<List<BlockPos>> waves = new ArrayList<>();
        for (int i = 0; i < CRATER_TICKS; i++) {
            waves.add(new ArrayList<>());
        }
        RandomSource random = level.getRandom();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double across = (x * x + z * z) / (radius * radius);
                if (across > 1.21) {
                    continue;
                }
                for (int y = -d; y <= 0; y++) {
                    double shape = across + y * y / (depth * depth);
                    if (shape > 0.85 + random.nextDouble() * 0.3) {
                        continue;
                    }
                    int wave = (int) Math.min(CRATER_TICKS - 1, Math.sqrt(across) * CRATER_TICKS);
                    waves.get(wave).add(center.offset(x, y, z));
                }
            }
        }
        for (int i = 0; i < CRATER_TICKS; i++) {
            List<BlockPos> wave = waves.get(i);
            Runnable dig = () -> {
                for (BlockPos block : wave) {
                    BlockState state = level.getBlockState(block);
                    if (!state.isAir() && state.getDestroySpeed(level, block) >= 0 && !state.hasBlockEntity()) {
                        level.destroyBlock(block, false);
                    }
                }
            };
            if (i == 0) {
                dig.run();
            } else {
                Tasks.later(i, dig);
            }
        }
    }

    // The floor under a point, looking down at most this far.
    private static Vec3 bottom(ServerLevel level, Vec3 pos, double depth, LivingEntity owner) {
        Vec3 from = pos.add(0, 0.5, 0);
        var hit = level.clip(new ClipContext(from, from.add(0, -depth, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? pos : hit.getLocation();
    }

    // Dark puffs where he vanishes or comes back.
    private static void smoke(ServerLevel level, LivingEntity caster, AgarraoSpec spec) {
        AgarraoSpec.Rastro rastro = spec.rastro();
        if (!rastro.ativa()) {
            return;
        }
        int color = rgb(rastro.corFumaca(), 0x2A2633);
        RandomSource random = level.getRandom();
        for (int i = 0; i < rastro.fumacaSumir(); i++) {
            Vec3 at = caster.position().add(random.nextGaussian() * 0.3, 0.2 + random.nextDouble() * caster.getBbHeight(), random.nextGaussian() * 0.3);
            Vec3 velocity = new Vec3(random.nextGaussian() * 0.05, 0.02, random.nextGaussian() * 0.05);
            Fx.moving(level, ModParticles.puff(color, 0.25f + random.nextFloat() * 0.3f, 8 + random.nextInt(8), 0.9f), at, velocity);
        }
    }

    // A thin line of wind along the stretch just covered, and kicked-up dust at the feet.
    private static void trail(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 dir, double step, AgarraoSpec spec) {
        AgarraoSpec.Rastro rastro = spec.rastro();
        if (!rastro.ativa()) {
            return;
        }
        int color = rgb(rastro.cor(), 0xFFFFFF);
        RandomSource random = level.getRandom();
        int count = Math.max(1, (int) Math.round(rastro.densidade() * step));
        Vec3 back = dir.scale(-1);
        for (int i = 0; i < count; i++) {
            Vec3 at = from.add(dir.scale(step * i / count)).add(random.nextGaussian() * 0.12, (random.nextDouble() - 0.5) * 0.9, random.nextGaussian() * 0.12);
            Fx.moving(level, ModParticles.streak(color, 0.5f, 5 + random.nextInt(3)), at, back.scale(0.15));
        }
        Vec3 feet = caster.position().add(0, 0.1, 0);
        for (int i = 0; i < 2; i++) {
            Fx.moving(level, ModParticles.puff(0xD8D2C4, 0.3f + random.nextFloat() * 0.2f, 8, 0.85f), feet,
                    back.scale(0.1).add(random.nextGaussian() * 0.05, 0.03, random.nextGaussian() * 0.05));
        }
    }

    // The dust wave rolling out along the ground and small specks of dirt flying out of the hole.
    private static void dust(ServerLevel level, Vec3 pos, AgarraoSpec spec) {
        AgarraoSpec.Impacto impacto = spec.impacto();
        RandomSource random = level.getRandom();
        AgarraoSpec.Onda onda = impacto.onda();
        if (onda != null && onda.ativa()) {
            int color = rgb(onda.cor(), 0xB8A58A);
            int points = 48;
            for (double y : new double[] {0.15, 0.6}) {
                for (int i = 0; i < points; i++) {
                    double angle = i * Math.PI * 2 / points;
                    Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    Fx.moving(level, ModParticles.puff(color, 0.35f + random.nextFloat() * 0.2f, 10 + random.nextInt(6), 0.93f),
                            pos.add(out.scale(0.6)).add(0, y, 0), out.scale(onda.velocidade()));
                }
            }
        }
        double radius = spec.esmagar().raioCratera();
        for (int i = 0; i < impacto.poeira(); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * radius * 0.6;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            int color = random.nextBoolean() ? 0x6B4A2F : 0x8A7458;
            SoulParticleOptions speck = new SoulParticleOptions(ModParticles.DUST.get(), color, 0.08f + random.nextFloat() * 0.08f,
                    20 + random.nextInt(20), 0.97f, 0.9f);
            Fx.moving(level, speck, pos.add(out.scale(r)).add(0, 0.3, 0),
                    out.scale(0.15 + random.nextDouble() * 0.35).add(0, 0.3 + random.nextDouble() * 0.45, 0));
        }
    }

    private static void animation(LivingEntity caster, String part, boolean awakened) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new ModPayloads.PlaySkillAnimation(caster.getId(), SKILL + "#" + part, awakened));
    }

    // The players whose screen this is about ("tela" sounds).
    private static List<ServerPlayer> screens(LivingEntity caster, LivingEntity target) {
        List<ServerPlayer> out = new ArrayList<>();
        if (caster instanceof ServerPlayer player) {
            out.add(player);
        }
        if (target instanceof ServerPlayer player) {
            out.add(player);
        }
        return out;
    }

    private static Vec3 flatTo(LivingEntity from, LivingEntity to) {
        Vec3 d = to.position().subtract(from.position());
        Vec3 flat = new Vec3(d.x, 0, d.z);
        return flat.lengthSqr() < 1.0E-4 ? Combat.flatLook(from) : flat.normalize();
    }

    private static boolean awakened(LivingEntity caster) {
        return caster.getData(ModAttachments.POWER).awakened;
    }

    private static int rgb(String hex, int fallback) {
        try {
            return Integer.parseInt(hex.replace("#", ""), 16);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
